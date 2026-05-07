package me.spawner;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventBus;
import me.spawner.utils.ColorUtils;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SpawnerMenu implements Listener {

    private final Spawner plugin;
    private final NamespacedKey AUTO_KILL_KEY;
    private final NamespacedKey XP_COLLECT_KEY;
    private final NamespacedKey STORED_XP_KEY;
    private final NamespacedKey MANAGER_KEY;
    private final NamespacedKey OWNER_KEY;
    private final Random random = new Random();
    private final Map<UUID, Boolean> luckPermsCache = new ConcurrentHashMap<>();

    private Method paperXpMethod = null;
    private Method teleportAsyncMethod = null;
    private boolean checkedForPaper = false;
    private boolean hasLuckPerms = false;

    public SpawnerMenu(Spawner plugin) {
        this.plugin = plugin;
        this.AUTO_KILL_KEY = new NamespacedKey(plugin, "auto_kill");
        this.XP_COLLECT_KEY = new NamespacedKey(plugin, "xp_collect");
        this.STORED_XP_KEY = new NamespacedKey(plugin, "stored_xp");
        this.MANAGER_KEY = new NamespacedKey(plugin, "spawner_manager");
        this.OWNER_KEY = new NamespacedKey(plugin, "spawner_owner");

        try {
            teleportAsyncMethod = Entity.class.getMethod("teleportAsync", Location.class);
        } catch (NoSuchMethodException ignored) {
        }

        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            this.hasLuckPerms = true;
            registerLuckPermsHook();
        }
    }

    private String getGuiText(String path) {
        if (plugin.getLanguageConfig() == null) return path;
        String val = plugin.getLanguageConfig().getString(path, path);
        return me.spawner.utils.ColorUtils.color(val);
    }

    private String getMessage(String path) {
        if (plugin.getLanguageConfig() == null) return path;
        String prefix = plugin.getLanguageConfig().getString("prefix", "&8[&aSpawner&8] &r");
        String val = plugin.getLanguageConfig().getString(path, path);
        return me.spawner.utils.ColorUtils.color(prefix + val);
    }

    private void registerLuckPermsHook() {
        LuckPerms api = LuckPermsProvider.get();
        EventBus eventBus = api.getEventBus();
        eventBus.subscribe(plugin, UserDataRecalculateEvent.class, event -> {
            UUID targetUUID = event.getUser().getUniqueId();
            luckPermsCache.remove(targetUUID);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == Material.SPAWNER) {
            CreatureSpawner spawner = (CreatureSpawner) event.getBlockPlaced().getState();
            spawner.getPersistentDataContainer().set(OWNER_KEY, PersistentDataType.STRING, event.getPlayer().getUniqueId().toString());
            spawner.update();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SPAWNER) return;
        if (!plugin.getConfig().getBoolean("spawner-menu", true)) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        event.setCancelled(true);
        openSpawnerMenu(player, block);
    }

    public void openSpawnerMenu(Player player, Block block) {
        String title = getGuiText("menu.title");
        Inventory inv = Bukkit.createInventory(null, 27, title);
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);
        updateMenuIcons(inv, block, player);
        player.openInventory(inv);
        player.setMetadata("opened_spawner_loc", new FixedMetadataValue(plugin, block.getLocation()));
    }

    private void updateMenuIcons(Inventory inv, Block block, Player player) {
        if (!(block.getState() instanceof CreatureSpawner spawner)) return;
        
        boolean viewerHasPerm = player.isOp() || player.hasPermission("spawner.menu") || player.hasPermission("spawner.admin");
        boolean autoKill = spawner.getPersistentDataContainer().getOrDefault(AUTO_KILL_KEY, PersistentDataType.BOOLEAN, false);
        boolean xpCollect = spawner.getPersistentDataContainer().getOrDefault(XP_COLLECT_KEY, PersistentDataType.BOOLEAN, false);
        int storedXp = spawner.getPersistentDataContainer().getOrDefault(STORED_XP_KEY, PersistentDataType.INTEGER, 0);

        inv.setItem(12, createMenuItem(Material.DIAMOND_SWORD, "auto-slayer", autoKill, -1, viewerHasPerm));
        inv.setItem(14, createMenuItem(Material.EXPERIENCE_BOTTLE, "xp-vacuum", xpCollect, storedXp, viewerHasPerm));
    }

    private ItemStack createMenuItem(Material material, String configKey, boolean enabled, int xpAmount, boolean hasPerm) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        
        meta.setAttributeModifiers(com.google.common.collect.ArrayListMultimap.create());
        for (org.bukkit.inventory.ItemFlag flag : org.bukkit.inventory.ItemFlag.values()) {
        meta.addItemFlags(flag);
        }

        String name = getGuiText("menu.items." + configKey + ".name");
        meta.setDisplayName(name);

        List<String> rawLore;
        if (hasPerm) {
            rawLore = plugin.getLanguageConfig().getStringList("menu.items." + configKey + ".lore");
        } else {
            rawLore = plugin.getLanguageConfig().getStringList("menu.items." + configKey + ".lore-no-perm");
            if (rawLore.isEmpty()) {
                rawLore = plugin.getLanguageConfig().getStringList("menu.items." + configKey + ".lore");
                rawLore.add("&c&lYETKİN YOK");
            }
        }

        String statusText = enabled ? getGuiText("menu.status.active") : getGuiText("menu.status.inactive");
        String xpText = (xpAmount == -1) ? "0" : String.valueOf(xpAmount);

        List<String> lore = rawLore.stream()
                .map(line -> me.spawner.utils.ColorUtils.color(line
                        .replace("%status%", statusText)
                        .replace("%xp%", xpText)))
                .collect(Collectors.toList());

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(getGuiText("menu.title"))) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        if (!player.hasMetadata("opened_spawner_loc")) return;
        
        Location loc = (Location) player.getMetadata("opened_spawner_loc").get(0).value();
        if (loc == null) return;
        Block block = loc.getBlock();
        if (block.getType() != Material.SPAWNER) return;
        CreatureSpawner spawner = (CreatureSpawner) block.getState();

        boolean viewerHasPerm = player.isOp() || player.hasPermission("spawner.menu") || player.hasPermission("spawner.admin");
        boolean autoKill = spawner.getPersistentDataContainer().getOrDefault(AUTO_KILL_KEY, PersistentDataType.BOOLEAN, false);
        boolean changed = false;

        if (event.getRawSlot() == 12) {
            if (viewerHasPerm) {
                boolean nextStatus = !autoKill;
                spawner.getPersistentDataContainer().set(AUTO_KILL_KEY, PersistentDataType.BOOLEAN, nextStatus);
                if (nextStatus) {
                    spawner.getPersistentDataContainer().set(MANAGER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
                    luckPermsCache.remove(player.getUniqueId());
                } else {
                    spawner.getPersistentDataContainer().set(XP_COLLECT_KEY, PersistentDataType.BOOLEAN, false);
                }
                changed = true;
                player.playSound(player.getLocation(), nextStatus ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
            } else {
                player.sendMessage(getMessage("menu.messages.no-permission"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                updateMenuIcons(event.getInventory(), block, player);
            }
        } 
        else if (event.getRawSlot() == 14) {
            int storedXp = spawner.getPersistentDataContainer().getOrDefault(STORED_XP_KEY, PersistentDataType.INTEGER, 0);

            if (event.isRightClick()) {
                if (storedXp > 0) {
                    player.giveExp(storedXp);
                    spawner.getPersistentDataContainer().set(STORED_XP_KEY, PersistentDataType.INTEGER, 0);
                    player.sendMessage(getMessage("menu.messages.xp-collected").replace("%xp%", String.valueOf(storedXp)));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                    changed = true;
                } else {
                    player.sendMessage(getMessage("menu.messages.xp-empty"));
                }
            } 
            else if (event.isLeftClick()) {
                if (viewerHasPerm) {
                    if (autoKill) {
                        boolean current = spawner.getPersistentDataContainer().getOrDefault(XP_COLLECT_KEY, PersistentDataType.BOOLEAN, false);
                        boolean nextStatus = !current;
                        spawner.getPersistentDataContainer().set(XP_COLLECT_KEY, PersistentDataType.BOOLEAN, nextStatus);
                        if (nextStatus) {
                            spawner.getPersistentDataContainer().set(MANAGER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
                        }
                        changed = true;
                        player.playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 1f, 1f);
                    } else {
                        player.sendMessage(getMessage("menu.messages.req-auto-slayer"));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                    }
                } else {
                    player.sendMessage(getMessage("menu.messages.feature-no-perm"));
                    updateMenuIcons(event.getInventory(), block, player);
                }
            }
        }

        if (changed) {
            spawner.update();
            updateMenuIcons(event.getInventory(), block, player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        CreatureSpawner spawner = event.getSpawner();
        boolean autoKill = spawner.getPersistentDataContainer().getOrDefault(AUTO_KILL_KEY, PersistentDataType.BOOLEAN, false);
        
        if (!autoKill) return;

        String managerUUIDString = spawner.getPersistentDataContainer().getOrDefault(MANAGER_KEY, PersistentDataType.STRING, null);
        
        if (managerUUIDString != null) {
            try {
                UUID managerUUID = UUID.fromString(managerUUIDString);
                
                if (!checkPermission(managerUUID, "spawner.menu")) {
                    spawner.getPersistentDataContainer().set(AUTO_KILL_KEY, PersistentDataType.BOOLEAN, false);
                    spawner.getPersistentDataContainer().set(XP_COLLECT_KEY, PersistentDataType.BOOLEAN, false);
                    spawner.update();
                    
                    Player p = Bukkit.getPlayer(managerUUID);
                    if (p != null) {
                        p.sendMessage(getMessage("menu.messages.perms-changed"));
                    }
                    return;
                }
            } catch (Exception e) {
                spawner.getPersistentDataContainer().set(AUTO_KILL_KEY, PersistentDataType.BOOLEAN, false);
                spawner.update();
                return;
            }
        }

        if (event.getEntity() instanceof LivingEntity entity) {
            Location dropLoc = spawner.getLocation().clone().add((random.nextDouble() * 2 - 1) * 1.3, 0.2, (random.nextDouble() * 2 - 1) * 1.3);
            
            if (teleportAsyncMethod != null) {
                try {
                    teleportAsyncMethod.invoke(entity, dropLoc);
                } catch (Exception ignored) {
                    entity.teleport(dropLoc);
                }
            } else {
                entity.teleport(dropLoc);
            }
            
            entity.setMetadata("spawner_mob", new FixedMetadataValue(plugin, spawner.getLocation()));
            entity.setHealth(0); 
        }
    }

    private boolean checkPermission(UUID uuid, String permission) {
        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            return onlinePlayer.isOp() || onlinePlayer.hasPermission(permission) || onlinePlayer.hasPermission("spawner.admin");
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.isOp()) {
            return true; 
        }

        if (luckPermsCache.containsKey(uuid)) {
            return luckPermsCache.get(uuid);
        }

        if (hasLuckPerms) {
            try {
                LuckPerms api = LuckPermsProvider.get();
                User user = api.getUserManager().getUser(uuid);
                
                if (user != null) {
                    boolean hasPerm = user.getCachedData().getPermissionData(QueryOptions.nonContextual()).checkPermission(permission).asBoolean() ||
                                      user.getCachedData().getPermissionData(QueryOptions.nonContextual()).checkPermission("spawner.admin").asBoolean();
                    luckPermsCache.put(uuid, hasPerm);
                    return hasPerm;
                } else {
                    api.getUserManager().loadUser(uuid).thenAccept(loadedUser -> {
                        if (loadedUser != null) {
                            boolean hasPerm = loadedUser.getCachedData().getPermissionData(QueryOptions.nonContextual()).checkPermission(permission).asBoolean() ||
                                              loadedUser.getCachedData().getPermissionData(QueryOptions.nonContextual()).checkPermission("spawner.admin").asBoolean();
                            luckPermsCache.put(uuid, hasPerm);
                        }
                    });
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.hasMetadata("spawner_mob")) return;
        Location spawnerLoc = (Location) entity.getMetadata("spawner_mob").get(0).value();
        if (spawnerLoc == null) return;
        Block block = spawnerLoc.getBlock();
        if (block.getType() != Material.SPAWNER) return;
        CreatureSpawner spawner = (CreatureSpawner) block.getState();

        int finalXp = event.getDroppedExp();
        if (finalXp == 0) finalXp = calculateRealXp(entity);

        boolean xpStoreMode = spawner.getPersistentDataContainer().getOrDefault(XP_COLLECT_KEY, PersistentDataType.BOOLEAN, false);
        if (xpStoreMode) {
            int currentStored = spawner.getPersistentDataContainer().getOrDefault(STORED_XP_KEY, PersistentDataType.INTEGER, 0);
            spawner.getPersistentDataContainer().set(STORED_XP_KEY, PersistentDataType.INTEGER, currentStored + finalXp);
            spawner.update();
            event.setDroppedExp(0);
        } else {
            event.setDroppedExp(finalXp);
        }
    }

    private int calculateRealXp(LivingEntity entity) {
        if (!checkedForPaper) {
            try { 
                paperXpMethod = LivingEntity.class.getMethod("getDroppedExperience"); 
                checkedForPaper = true; 
            } catch (NoSuchMethodException ignored) { 
                checkedForPaper = true; 
            }
        }
        if (paperXpMethod != null) {
            try { return (int) paperXpMethod.invoke(entity); } catch (Exception ignored) {}
        }
        if (entity instanceof Ageable && !((Ageable) entity).isAdult()) return 0;
        EntityType type = entity.getType();
        if (type == EntityType.BLAZE || type == EntityType.GUARDIAN) return 10;
        if (type == EntityType.WITHER) return 50;
        if (type == EntityType.ENDER_DRAGON) return 12000;
        if (entity instanceof Monster) return 5;
        if (entity instanceof Animals) return random.nextInt(3) + 1;
        if (entity instanceof Slime) return ((Slime) entity).getSize(); 
        return 0;
    }
}