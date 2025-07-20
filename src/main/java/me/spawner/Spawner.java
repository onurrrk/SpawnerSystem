package me.spawner;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import me.spawner.utils.JsonLogger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class Spawner extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    private final NamespacedKey SPAWNER_PICKAXE_KEY = new NamespacedKey(this, "spawner_pickaxe");
    private final NamespacedKey USES_KEY = new NamespacedKey(this, "uses_left");
    private final NamespacedKey PLAYER_PLACED_KEY = new NamespacedKey(this, "player_placed_spawner");
    private String systemMode;
    private JsonLogger jsonLogger;

    @Override
    public void onEnable() {
        loadConfigValues();
        this.jsonLogger = new JsonLogger(this);
        if (Bukkit.getPluginManager().getPlugin("Towny") == null) {
            getLogger().severe("Towny bulunamadı! Plugin devre dışı bırakılıyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("spsystem").setExecutor(this);
        getCommand("spsystem").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MiemacraftSpawner plugini '" + systemMode + "' modunda başarıyla etkinleştirildi!");
    }

    private void loadConfigValues() {
        saveDefaultConfig();
        reloadConfig();
        systemMode = getConfig().getString("sistem", "gelismis").toLowerCase();
    }

    private String getMessage(String path) {
        String prefix = getConfig().getString("prefix", "&8[&aSpawner&8] &r");
        String message = getConfig().getString("mesajlar." + path, "&cMesaj bulunamadı: " + path);
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("spawner.admin")) {
            sender.sendMessage(getMessage("yetki-yok"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(getMessage("yanlis-alt-komut"));
            return true;
        }
        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("reload")) {
            loadConfigValues();
            sender.sendMessage(getMessage("reload-basarili"));
            return true;
        }
        if (subCommand.equals("kazmaver")) {
            if (!systemMode.equals("gelismis")) {
                sender.sendMessage(getMessage("komut-devre-disi"));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(getMessage("yanlis-kullanim-kazma"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(getMessage("oyuncu-bulunamadi").replace("%player%", args[1]));
                return true;
            }
            int uses;
            try {
                uses = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(getMessage("sayi-degil"));
                return true;
            }
            if (uses <= 0 || uses > 100) {
                sender.sendMessage(getMessage("maksimum-can-siniri"));
                return true;
            }
            target.getInventory().addItem(createSpawnerPickaxe(uses));
            sender.sendMessage(getMessage("kazma-verildi-gonderen").replace("%player%", target.getName()).replace("%uses%", String.valueOf(uses)));
            target.sendMessage(getMessage("kazma-verildi-alan"));
            return true;
        }
        sender.sendMessage(getMessage("yanlis-alt-komut"));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("spawner.admin")) return new ArrayList<>();
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Arrays.asList("reload", "kazmaver"), new ArrayList<>());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("kazmaver")) {
            return StringUtil.copyPartialMatches(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), new ArrayList<>());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("kazmaver")) {
            return StringUtil.copyPartialMatches(args[2], IntStream.rangeClosed(1, 100).mapToObj(String::valueOf).collect(Collectors.toList()), new ArrayList<>());
        }
        return new ArrayList<>();
    }

    @EventHandler
    public void onSpawnerPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SPAWNER) return;
        if (event.getBlockPlaced().getState() instanceof CreatureSpawner spawnerState) {
            jsonLogger.log(event.getPlayer(), event.getBlockPlaced(), "KOYDU");
            spawnerState.getPersistentDataContainer().set(PLAYER_PLACED_KEY, PersistentDataType.BOOLEAN, true);
            spawnerState.update();
        }
    }

    @EventHandler
    public void onSpawnerBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        Material blockType = event.getBlock().getType();

        if (isSpawnerPickaxe(itemInHand) && blockType != Material.SPAWNER) {
            player.sendMessage(getMessage("sp-kazma-sadece-spawner"));
            event.setCancelled(true);
            return;
        }
        if (blockType != Material.SPAWNER) return;
        if (!(event.getBlock().getState() instanceof CreatureSpawner spawnerState)) {
            event.setCancelled(true);
            return;
        }

        if (systemMode.equals("towny")) {
            if (!spawnerState.getPersistentDataContainer().has(PLAYER_PLACED_KEY)) {
                player.sendMessage(getMessage("dogal-spawner-kirilamaz"));
                event.setCancelled(true);
                return;
            }
        }

        Location loc = event.getBlock().getLocation();
        TownyAPI api = TownyAPI.getInstance();
        TownBlock townBlock = api.getTownBlock(loc);

        if (townBlock != null && townBlock.hasTown()) {
            try {
                Resident resident = api.getResident(player);
                if (resident == null || !townBlock.getTown().hasResident(resident)) {
                    player.sendMessage(getMessage("towny-yetki-yok").replace("%town%", townBlock.getTown().getName()));
                    event.setCancelled(true);
                    return;
                }
            } catch (Exception e) {
                event.setCancelled(true);
                return;
            }
        }

        switch (systemMode) {
            case "gelismis":
                if (!isSpawnerPickaxe(itemInHand)) {
                    player.sendMessage(getMessage("gelismis-yanlis-kazma"));
                    event.setCancelled(true);
                    return;
                }
                ItemMeta meta = itemInHand.getItemMeta();
                int usesLeft = meta.getPersistentDataContainer().getOrDefault(USES_KEY, PersistentDataType.INTEGER, 1) - 1;
                if (usesLeft > 0) {
                    meta.getPersistentDataContainer().set(USES_KEY, PersistentDataType.INTEGER, usesLeft);
                    List<String> newLore = new ArrayList<>();
                    getConfig().getStringList("spawner-kazma-item.aciklama").forEach(line ->
                            newLore.add(ChatColor.translateAlternateColorCodes('&', line.replace("%uses%", String.valueOf(usesLeft))))
                    );
                    meta.setLore(newLore);
                    itemInHand.setItemMeta(meta);
                } else {
                    player.getInventory().setItemInMainHand(null);
                    player.sendMessage(getMessage("kazma-kirildi"));
                }
                break;
            case "klasik":
            case "towny":
                if (!itemInHand.containsEnchantment(Enchantment.SILK_TOUCH)) {
                    player.sendMessage(getMessage("klasik-ipeksi-gerekli"));
                    event.setCancelled(true);
                    return;
                }
                break;
        }

        jsonLogger.log(player, event.getBlock(), "KIRDI");
        event.setDropItems(false);
        event.setExpToDrop(0);

        ItemStack spawnerItem = new ItemStack(Material.SPAWNER);
        if (spawnerItem.getItemMeta() instanceof BlockStateMeta bsm) {
            CreatureSpawner newSpawnerState = (CreatureSpawner) bsm.getBlockState();
            EntityType brokenType = spawnerState.getSpawnedType();

            // --- HATA DÜZELTMESİ BURADA ---
            if (brokenType != null) {
                newSpawnerState.setSpawnedType(brokenType);
            }
            // -----------------------------

            bsm.setBlockState(newSpawnerState);
            spawnerItem.setItemMeta(bsm);
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(spawnerItem);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(item -> loc.getWorld().dropItemNaturally(player.getLocation(), item));
            player.sendMessage(getMessage("envanter-dolu"));
        } else {
            player.sendMessage(getMessage("spawner-alindi"));
        }
    }

    private ItemStack createSpawnerPickaxe(int uses) {
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = pickaxe.getItemMeta();
        String name = ChatColor.translateAlternateColorCodes('&', getConfig().getString("spawner-kazma-item.isim"));
        List<String> coloredLore = new ArrayList<>();
        getConfig().getStringList("spawner-kazma-item.aciklama").forEach(line ->
                coloredLore.add(ChatColor.translateAlternateColorCodes('&', line.replace("%uses%", String.valueOf(uses))))
        );
        meta.setDisplayName(name);
        meta.setLore(coloredLore);
        meta.getPersistentDataContainer().set(SPAWNER_PICKAXE_KEY, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(USES_KEY, PersistentDataType.INTEGER, uses);
        meta.addEnchant(Enchantment.DIG_SPEED, 5, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        pickaxe.setItemMeta(meta);
        return pickaxe;
    }

    private boolean isSpawnerPickaxe(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(SPAWNER_PICKAXE_KEY);
    }
}