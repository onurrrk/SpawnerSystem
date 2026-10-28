package me.spawner.menu;

import me.spawner.Spawner;
import me.spawner.booster.BoosterManager;
import me.spawner.hologram.Hologram;
import me.spawner.storage.SpawnerStorage;
import me.spawner.utils.ColorUtils;
import me.spawner.utils.LootConfig;
import me.spawner.utils.LuckPermsHook;
import me.spawner.utils.PDCUtils;
import me.spawner.utils.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SpawnerMenu implements Listener {
    public static final class SpawnerMenuHolder implements org.bukkit.inventory.InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    private static final int SLOT_STORAGE = 10;
    private static final int SLOT_AUTO_SLAYER = 12;
    private static final int SLOT_XP = 14;
    private static final int SLOT_BOOSTER = 16;
    private static final int MAX_ROLLS = 500;

    private final Spawner plugin;
    private final LootConfig lootConfig;
    private final LootConfig itemLootConfig;
    private final NamespacedKey STORED_XP_KEY;
    private final NamespacedKey AUTO_KILL_KEY;
    private final NamespacedKey MANAGER_KEY;
    private final Map<UUID, Boolean> luckPermsCache = new ConcurrentHashMap<>();
    private boolean hasLuckPerms = false;
    private boolean luckPermsRegistered = false;
    private final Random random = new Random();

    private Method paperGiveExpMendingMethod = null;

    public SpawnerMenu(Spawner plugin) {
        this.plugin = plugin;
        this.lootConfig = LootConfig.forMobs(plugin);
        itemLootConfig = LootConfig.forItems(plugin);
        this.STORED_XP_KEY = new NamespacedKey(plugin, "stored_xp");
        this.AUTO_KILL_KEY = new NamespacedKey(plugin, "auto_kill");
        this.MANAGER_KEY = new NamespacedKey(plugin, "spawner_manager");

        try {
            paperGiveExpMendingMethod = Player.class.getMethod("giveExp", int.class, boolean.class);
        } catch (NoSuchMethodException ignored) {
        }

        setupLuckPerms();
    }

    private void setupLuckPerms() {
        if (!plugin.settings().menuEnabled || plugin.settings().menuV2 || luckPermsRegistered) return;
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return;
        luckPermsRegistered = true;
        hasLuckPerms = true;
        LuckPermsHook.register(plugin, luckPermsCache::remove);
    }

    private String getGuiText(String path) {
        if (plugin.getLanguageConfig() == null) return path;
        String val = plugin.getLanguageConfig().getString(path, path);
        return ColorUtils.color(val);
    }

    private String getMessage(String path) {
        if (plugin.getLanguageConfig() == null) return path;
        String val = plugin.getLanguageConfig().getString(path, path);
        return plugin.language().withPrefix(ColorUtils.color(val));
    }

    public record CycleResult(int delay, boolean blocked) {
    }

    public void reload() {
        luckPermsCache.clear();
        setupLuckPerms();
        lootConfig.reload();
        itemLootConfig.reload();
    }

    private void giveExpToPlayer(Player player, int amount) {
        if (paperGiveExpMendingMethod != null) {
            try {
                paperGiveExpMendingMethod.invoke(player, amount, true);
                return;
            } catch (Exception ignored) {
            }
        }
        player.giveExp(amount);
    }

    private void runOnRegion(Location location, Runnable task) {
        Scheduler.runLocationTask(plugin, location, task);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();
        ItemStack held = event.getItem();

        if (plugin.stacks().enabled() && held != null && held.getType() == Material.SPAWNER) {
            event.setCancelled(true);
            plugin.stacks().merge(player, block, player.isSneaking());
            return;
        }

        if (!plugin.settings().menuEnabled) return;
        if (!player.isSneaking()) return;

        event.setCancelled(true);
        openSpawnerMenu(player, block);
    }

    public void openSpawnerMenu(Player player, Block block) {
        Inventory inv = buildMenuInventory(player, block);
        showMenu(player, inv, block.getLocation());
    }

    public void reopenMenu(Player player, Location loc) {
        runOnRegion(loc, () -> {
            Block block = loc.getBlock();
            if (block.getType() != Material.SPAWNER) return;
            Inventory inv = buildMenuInventory(player, block);
            Scheduler.runEntityTask(plugin, player, () -> showMenu(player, inv, loc));
        });
    }

    private void showMenu(Player player, Inventory inv, Location loc) {
        MenuLocks locks = plugin.menuLocks();
        if (!locks.tryAcquire(loc, player)) {
            player.sendMessage(getMessage("menu.messages.in-use"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        UUID id = player.getUniqueId();
        locks.beginTransfer(id);
        InventoryView view;
        try {
            view = player.openInventory(inv);
        } catch (RuntimeException e) {
            locks.release(loc, player);
            throw e;
        } finally {
            locks.endTransfer(id);
        }
        if (view == null) {
            if (!MenuLocks.isPluginMenu(player.getOpenInventory().getTopInventory())) {
                locks.release(loc, player);
            }
            return;
        }
        player.setMetadata("opened_spawner_loc", new FixedMetadataValue(plugin, loc));
    }

    private Location openedLocation(Player player) {
        List<org.bukkit.metadata.MetadataValue> values = player.getMetadata("opened_spawner_loc");
        if (values.isEmpty()) return null;
        Object value = values.get(0).value();
        return value instanceof Location location ? location : null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.menuLocks().releaseAll(event.getPlayer().getUniqueId());
    }

    private Inventory buildMenuInventory(Player player, Block block) {
        String title = getGuiText("menu.title");
        if (plugin.stacks().enabled() && block.getState() instanceof CreatureSpawner stacked) {
            title = title + " (" + plugin.stacks().size(stacked) + ")";
        }

        SpawnerMenuHolder holder = new SpawnerMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.setInventory(inv);
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);
        if (block.getState() instanceof CreatureSpawner spawner) {
            renderIcons(inv, readState(spawner), hasMenuPermission(player));
        }
        return inv;
    }

    public boolean hasItemSpawner(String material) {
        return itemLootConfig.hasConfig(material);
    }

    public Set<String> itemSpawnerKeys() {
        return itemLootConfig.keys();
    }

    private record MenuState(boolean autoKill, int storedXp, List<BoosterManager.EntryInfo> boosts, int storageUsed, int storageMax) {
    }

    private MenuState readState(CreatureSpawner spawner) {
        return new MenuState(
                isAutoKill(spawner),
                spawner.getPersistentDataContainer().getOrDefault(STORED_XP_KEY, PersistentDataType.INTEGER, 0),
                plugin.settings().boosterEnabled ? plugin.boosters().describe(spawner) : null,
                plugin.storage().enabled() ? plugin.storage().usedSlots(plugin.storage().read(spawner)) : -1,
                plugin.storage().maxSlots(spawner));
    }

    private boolean isAutoKill(CreatureSpawner spawner) {
        return plugin.settings().menuV2
                || plugin.items().itemSpawnerType(spawner) != null
                || PDCUtils.getBoolean(spawner.getPersistentDataContainer(), AUTO_KILL_KEY, false);
    }

    private boolean hasMenuPermission(Player player) {
        return plugin.settings().menuV2
                || player.isOp() || player.hasPermission("spawner.menu") || player.hasPermission("spawner.admin");
    }

    private void renderIcons(Inventory inv, MenuState state, boolean viewerHasPerm) {
        if (plugin.settings().autoSlayerEnabled) {
            inv.setItem(SLOT_AUTO_SLAYER, createMenuItem(Material.DIAMOND_SWORD, "auto-slayer", state.autoKill(), -1, viewerHasPerm));
        }
        if (state.storageUsed() >= 0) {
            inv.setItem(SLOT_STORAGE, createStorageItem(state));
        }
        if (plugin.settings().xpVacuumEnabled) {
            inv.setItem(SLOT_XP, createXpItem(state));
        }
        if (state.boosts() != null) {
            inv.setItem(SLOT_BOOSTER, plugin.boosters().createInfoItem(state.boosts()));
        }
    }

    private ItemStack createStorageItem(MenuState state) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        for (ItemFlag flag : ItemFlag.values()) {
            meta.addItemFlags(flag);
        }
        meta.setDisplayName(getGuiText("menu.items.storage.name"));
        List<String> lore = plugin.getLanguageConfig().getStringList("menu.items.storage.lore").stream()
                .map(line -> ColorUtils.color(line
                        .replace("%used%", String.valueOf(state.storageUsed()))
                        .replace("%max%", String.valueOf(state.storageMax()))))
                .collect(Collectors.toList());
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
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
        if (plugin.settings().menuV2) {
            rawLore = new ArrayList<>(plugin.getLanguageConfig().getStringList("menu.items." + configKey + ".lore-auto"));
        } else if (hasPerm) {
            rawLore = plugin.getLanguageConfig().getStringList("menu.items." + configKey + ".lore");
        } else {
            rawLore = plugin.getLanguageConfig().getStringList("menu.items." + configKey + ".lore-no-perm");
            if (rawLore.isEmpty()) {
                rawLore = plugin.getLanguageConfig().getStringList("menu.items." + configKey + ".lore");
                rawLore.add("&c&lNO PERMISSION");
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

    private ItemStack createXpItem(MenuState state) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        for (ItemFlag flag : ItemFlag.values()) {
            meta.addItemFlags(flag);
        }
        meta.setDisplayName(getGuiText("menu.items.xp-vacuum.name"));

        List<String> template = plugin.getLanguageConfig().getStringList("menu.items.xp-vacuum.lore");
        List<String> lore = new ArrayList<>();
        int keep = Math.max(0, template.size() - 2);
        for (int i = 0; i < keep; i++) {
            String line = template.get(i);
            if (line.contains("%status%")) continue;
            lore.add(ColorUtils.color(line.replace("%xp%", String.valueOf(state.storedXp()))));
        }
        lore.add(getGuiText("menu.items.xp-vacuum.click"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SpawnerMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof SpawnerMenuHolder && event.getPlayer() instanceof Player player) {
            Location loc = openedLocation(player);
            player.removeMetadata("opened_spawner_loc", plugin);
            if (loc != null && !plugin.menuLocks().isTransferring(player.getUniqueId())) {
                plugin.menuLocks().release(loc, player);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SpawnerMenuHolder)) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        if (!plugin.settings().menuEnabled) {
            Scheduler.runEntityTaskLater(plugin, player, player::closeInventory, 1L);
            return;
        }
        Location loc = openedLocation(player);
        if (loc == null) return;

        runOnRegion(loc, () -> handleMenuClick(event, loc, player));
    }

    private void handleMenuClick(InventoryClickEvent event, Location loc, Player player) {
        Block block = loc.getBlock();
        if (block.getType() != Material.SPAWNER) return;
        CreatureSpawner spawner = (CreatureSpawner) block.getState();

        boolean viewerHasPerm = hasMenuPermission(player);
        boolean autoKill = isAutoKill(spawner);
        boolean itemSpawner = plugin.items().itemSpawnerType(spawner) != null;
        boolean changed = false;
        Inventory inventory = event.getInventory();
        List<Runnable> playerActions = new ArrayList<>();
        int slot = event.getRawSlot();

        if (slot == SLOT_AUTO_SLAYER && plugin.settings().autoSlayerEnabled && !plugin.settings().menuV2) {
            if (itemSpawner) {
                playerActions.add(() -> player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f));
            } else if (viewerHasPerm) {
                boolean nextStatus = !autoKill;
                PDCUtils.setBoolean(spawner.getPersistentDataContainer(), AUTO_KILL_KEY, nextStatus);
                if (nextStatus) {
                    spawner.getPersistentDataContainer().set(MANAGER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
                    luckPermsCache.remove(player.getUniqueId());
                }
                changed = true;
                playerActions.add(() -> player.playSound(player.getLocation(), nextStatus ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f));
            } else {
                MenuState state = readState(spawner);
                playerActions.add(() -> {
                    player.sendMessage(getMessage("menu.messages.no-permission"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    renderIcons(inventory, state, false);
                });
            }
        } else if (slot == SLOT_STORAGE) {
            if (plugin.storage().enabled()) {
                SpawnerStorage.Snapshot snapshot = plugin.storage().snapshot(spawner);
                playerActions.add(() -> plugin.storageMenu().open(player, loc, snapshot));
            }
        } else if (slot == SLOT_BOOSTER) {
            MenuState state = readState(spawner);
            playerActions.add(() -> renderIcons(inventory, state, hasMenuPermission(player)));
        } else if (slot == SLOT_XP && plugin.settings().xpVacuumEnabled) {
            if (event.isLeftClick() || event.isRightClick()) {
                int storedXp = spawner.getPersistentDataContainer().getOrDefault(STORED_XP_KEY, PersistentDataType.INTEGER, 0);
                if (storedXp > 0) {
                    spawner.getPersistentDataContainer().set(STORED_XP_KEY, PersistentDataType.INTEGER, 0);
                    changed = true;
                    playerActions.add(() -> {
                        giveExpToPlayer(player, storedXp);
                        player.sendMessage(getMessage("menu.messages.xp-collected").replace("%xp%", String.valueOf(storedXp)));
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                    });
                } else {
                    playerActions.add(() -> player.sendMessage(getMessage("menu.messages.xp-empty")));
                }
            }
        }

        if (changed) {
            spawner.update();
            plugin.spawnerSettings().apply(spawner);
            MenuState state = readState(spawner);
            playerActions.add(() -> renderIcons(inventory, state, hasMenuPermission(player)));
        }

        if (!playerActions.isEmpty()) {
            Scheduler.runEntityTask(plugin, player, () -> playerActions.forEach(Runnable::run));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        CreatureSpawner spawner = event.getSpawner();
        if (spawner == null) return;

        if (plugin.spawnerSettings().ignoresConditions(spawner)) {
            event.setCancelled(true);
            resetVanillaDelay(spawner);
            return;
        }

        String itemType = plugin.items().itemSpawnerType(spawner);
        boolean itemSpawner = itemType != null;
        if (!itemSpawner) return;

        event.setCancelled(true);

        int stack = plugin.stacks().size(spawner);
        List<BoosterManager.Group> groups = plugin.boosters().groups(spawner, stack);
        int blockDelay = plugin.spawnerSettings().blockDelay(groups, itemSpawner);
        if (spawner.getDelay() != blockDelay) {
            spawner.setDelay(blockDelay);
            spawner.update();
        }

        boolean produced = produce(spawner, itemType, groups, blockDelay);

        Hologram hologram = plugin.holograms().get(spawner.getLocation());
        if (hologram != null) {
            if (produced) {
                hologram.recordSuccessfulSpawn(spawner);
            } else {
                hologram.recordBlocked(spawner);
            }
        }
    }

    private void resetVanillaDelay(CreatureSpawner spawner) {
        int blockDelay = plugin.spawnerSettings().effectiveDelay(spawner);
        if (spawner.getDelay() != blockDelay) {
            spawner.setDelay(blockDelay);
            spawner.update();
        }
    }

    private boolean managerAllowed(CreatureSpawner spawner) {
        String raw = spawner.getPersistentDataContainer().get(MANAGER_KEY, PersistentDataType.STRING);
        if (raw == null) return true;
        UUID manager;
        try {
            manager = UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            revokeAutoKill(spawner, null);
            return false;
        }
        if (checkPermission(manager, "spawner.menu")) return true;
        revokeAutoKill(spawner, manager);
        return false;
    }

    private void revokeAutoKill(CreatureSpawner spawner, UUID manager) {
        PDCUtils.setBoolean(spawner.getPersistentDataContainer(), AUTO_KILL_KEY, false);
        spawner.update();
        plugin.spawnerSettings().apply(spawner);
        if (manager != null) {
            Player player = Bukkit.getPlayer(manager);
            if (player != null) {
                player.sendMessage(getMessage("menu.messages.perms-changed"));
            }
        }
    }

    public CycleResult runCycle(CreatureSpawner spawner) {
        String itemType = plugin.items().itemSpawnerType(spawner);
        if (itemType == null && !plugin.settings().menuV2 && !managerAllowed(spawner)) {
            return new CycleResult(plugin.spawnerSettings().effectiveDelay(spawner), true);
        }
        List<BoosterManager.Group> groups = plugin.boosters().groups(spawner, plugin.stacks().size(spawner));
        int blockDelay = plugin.spawnerSettings().blockDelay(groups, itemType != null);
        boolean produced = produce(spawner, itemType, groups, blockDelay);
        return new CycleResult(blockDelay, !produced);
    }

    private boolean produce(CreatureSpawner spawner, String itemType, List<BoosterManager.Group> groups, int blockDelay) {
        boolean itemSpawner = itemType != null;

        LootConfig source;
        String lootKey;
        if (itemSpawner) {
            source = itemLootConfig;
            lootKey = itemType;
        } else {
            EntityType mobType = spawner.getSpawnedType();
            if (mobType == null) return true;
            source = lootConfig;
            lootKey = mobType.name();
        }

        if (!source.hasConfig(lootKey)) {
            return true;
        }

        World world = spawner.getWorld();
        Location dropLoc = spawner.getLocation().clone().add(
                0.5 + (random.nextDouble() * 2 - 1) * 1.3,
                0.2,
                0.5 + (random.nextDouble() * 2 - 1) * 1.3
        );

        SpawnerStorage storage = plugin.storage();
        boolean useStorage = storage.enabled() && plugin.settings().menuEnabled;
        List<SpawnerStorage.Entry> storedEntries = null;
        int storageMax = useStorage ? storage.maxSlots(spawner) : 0;
        if (useStorage) {
            storedEntries = storage.read(spawner);
            if (storage.isLocked(storedEntries) || storage.isFull(storedEntries, storageMax)) {
                return false;
            }
        }

        int baseExp = itemSpawner && !plugin.settings().itemGiveXp ? 0 : source.getExperience(lootKey);
        double rate = 0.0;
        double xpRate = 0.0;
        for (BoosterManager.Group group : groups) {
            int groupDelay = Math.max(1, plugin.spawnerSettings().effectiveDelay(group.boost(), itemSpawner));
            double speed = (double) blockDelay / groupDelay;
            int xpPercent = group.boost() == null ? 0 : group.boost().xpPercent();
            rate += speed * group.count();
            xpRate += speed * group.count() * BoosterManager.boostXp(baseExp, xpPercent);
        }

        PersistentDataContainer pdc = spawner.getPersistentDataContainer();
        NamespacedKey carryKey = plugin.stacks().carryKey();
        boolean hadCarry = pdc.has(carryKey, PersistentDataType.DOUBLE);
        double total = rate + pdc.getOrDefault(carryKey, PersistentDataType.DOUBLE, 0.0);
        int whole = (int) Math.min(Integer.MAX_VALUE, Math.floor(total + 1.0E-9));
        int rolls = Math.min(MAX_ROLLS, whole);
        double carry = whole > MAX_ROLLS ? 0.0 : Math.max(0.0, total - whole);

        boolean dirty = false;
        if (carry > 1.0E-6) {
            pdc.set(carryKey, PersistentDataType.DOUBLE, carry);
            dirty = true;
        } else if (hadCarry) {
            pdc.remove(carryKey);
            dirty = true;
        }
        if (rolls <= 0) {
            if (dirty) spawner.update();
            return true;
        }

        boolean storageChanged = false;
        for (int i = 0; i < rolls; i++) {
            for (ItemStack drop : source.rollDrops(lootKey)) {
                if (useStorage) {
                    int before = drop.getAmount();
                    int leftover = storage.add(storedEntries, drop, storageMax);
                    if (leftover < before) {
                        storageChanged = true;
                    }
                } else {
                    world.dropItemNaturally(dropLoc, drop);
                }
            }
        }

        long totalExp = rate > 0 ? Math.round(xpRate / rate * rolls) : 0L;
        if (totalExp > 0) {
            if (plugin.settings().xpVacuumEnabled && plugin.settings().menuEnabled) {
                int currentStored = pdc.getOrDefault(STORED_XP_KEY, PersistentDataType.INTEGER, 0);
                int newStored = (int) Math.min(Integer.MAX_VALUE, (long) currentStored + totalExp);
                pdc.set(STORED_XP_KEY, PersistentDataType.INTEGER, newStored);
                dirty = true;
            } else {
                int orbExp = (int) Math.min(Integer.MAX_VALUE, totalExp);
                world.spawn(dropLoc, ExperienceOrb.class, orb -> orb.setExperience(orbExp));
            }
        }

        if (storageChanged) {
            storage.write(spawner, storedEntries);
            dirty = true;
        }
        if (dirty) {
            spawner.update();
        }
        if (storageChanged && plugin.storageMenu().hasViewers(spawner.getLocation())) {
            plugin.storageMenu().notifyViewers(spawner.getLocation(), storage.snapshotEntries(storedEntries, storageMax));
        }
        return true;
    }

    private boolean checkPermission(UUID uuid, String permission) {
        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            return onlinePlayer.isOp() || onlinePlayer.hasPermission(permission) || onlinePlayer.hasPermission("spawner.admin");
        }

        if (Bukkit.getOfflinePlayer(uuid).isOp()) {
            return true;
        }

        Boolean cached = luckPermsCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        if (hasLuckPerms && !plugin.settings().menuV2) {
            LuckPermsHook.check(uuid, permission, luckPermsCache);
            Boolean resolved = luckPermsCache.get(uuid);
            return resolved == null || resolved;
        }
        return true;
    }
}
