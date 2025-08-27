package me.spawner;

import me.spawner.utils.JsonLogger;
import org.bukkit.*;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class Spawner extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    private final NamespacedKey SPAWNER_PICKAXE_KEY = new NamespacedKey(this, "spawner_pickaxe");
    private final NamespacedKey USES_KEY = new NamespacedKey(this, "uses_left");
    private final NamespacedKey PLAYER_PLACED_KEY = new NamespacedKey(this, "player_placed_spawner");

    private String systemMode;
    private boolean naturalSpawnerBreak;
    private boolean allowEmptySpawnerBreak;
    private JsonLogger jsonLogger;

    private File languageFile;
    private FileConfiguration languageConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadLanguages();
        loadLanguage();
        loadConfigValues();
        this.jsonLogger = new JsonLogger(this);
        getCommand("spsystem").setExecutor(this);
        getCommand("spsystem").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("SpawnerSystem plugin enabled in '" + systemMode + "' mode!");
    }

private void loadLanguages() {
    File langFolder = new File(getDataFolder(), "languages");
    if (!langFolder.exists()) langFolder.mkdirs();

    String[] defaultLangs = {"en","tr","de","es","ru","zh","ja","az","fr","ar","nl","id","hy","it","gd","sv","ky","ko","hu","cs","el"};

    for (String lang : defaultLangs) {
        File langFile = new File(langFolder, lang + ".yml");
        if (!langFile.exists()) {
            try {
                saveResource("languages/" + lang + ".yml", false);
            } catch (Exception e) {
                getLogger().warning("Failed to save language file " + lang + ".yml: " + e.getMessage());
            }
        }
    }
}

    private void loadLanguage() {
        String lang = getConfig().getString("language", "en");
        File langFolder = new File(getDataFolder(), "languages");
        languageFile = new File(langFolder, lang + ".yml");
        if (!languageFile.exists()) {
            getLogger().warning(lang + ".yml not found! Defaulting to English.");
            languageFile = new File(langFolder, "en.yml");
        }
        languageConfig = YamlConfiguration.loadConfiguration(languageFile);
    }

    private void loadConfigValues() {
        reloadConfig();
        systemMode = getConfig().getString("system", "advanced").toLowerCase();
        naturalSpawnerBreak = getConfig().getBoolean("natural-spawner-break", false);
        allowEmptySpawnerBreak = getConfig().getBoolean("allow-empty-spawner-break", true);
    }

    private String getMessage(String path) {
        String prefix = ChatColor.translateAlternateColorCodes('&', languageConfig.getString("prefix", "&8[&aSpawner&8] &r"));
        String message = ChatColor.translateAlternateColorCodes('&', languageConfig.getString("messages." + path, "&cMessage not found: " + path));
        return prefix + message;
    }

    private String getLangString(String path) {
        return ChatColor.translateAlternateColorCodes('&', languageConfig.getString(path, path));
    }

    private List<String> getLangList(String path) {
        return languageConfig.getStringList(path).stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("spawner.admin")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(getMessage("wrong-subcommand"));
            return true;
        }
        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("reload")) {
            reloadConfig();
            loadLanguages();
            loadLanguage();
            loadConfigValues();
            sender.sendMessage(getMessage("reload-success"));
            return true;
        }
        if (subCommand.equals("pickaxegive")) {
            if (!systemMode.equals("advanced")) {
                sender.sendMessage(getMessage("command-disabled"));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(getMessage("wrong-pickaxe-usage"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(getMessage("player-not-found").replace("%player%", args[1]));
                return true;
            }
            int uses;
            try {
                uses = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(getMessage("not-a-number"));
                return true;
            }
            if (uses <= 0 || uses > 100) {
                sender.sendMessage(getMessage("max-uses-limit"));
                return true;
            }
            target.getInventory().addItem(createSpawnerPickaxe(uses));
            sender.sendMessage(getMessage("pickaxe-given-sender")
                    .replace("%player%", target.getName())
                    .replace("%uses%", String.valueOf(uses)));
            target.sendMessage(getMessage("pickaxe-given-recipient"));
            return true;
        }
        if (subCommand.equals("givespawner")) {
            if (args.length < 3) {
                sender.sendMessage(getMessage("wrong-subcommand"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(getMessage("player-not-found").replace("%player%", args[1]));
                return true;
            }
            EntityType type;
            try {
                type = EntityType.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + "Invalid mob type: " + args[2]);
                return true;
            }
            ItemStack spawnerItem = createSpawnerItem(type);
            target.getInventory().addItem(spawnerItem);
            sender.sendMessage(getMessage("spawner-given-sender")
                    .replace("%player%", target.getName())
                    .replace("%type%", type.name()));
            target.sendMessage(getMessage("spawner-given-recipient")
                    .replace("%type%", type.name()));
            return true;
        }
        sender.sendMessage(getMessage("wrong-subcommand"));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("spawner.admin")) return new ArrayList<>();
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Arrays.asList("reload", "pickaxegive", "givespawner"), new ArrayList<>());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("pickaxegive") || args[0].equalsIgnoreCase("givespawner"))) {
            return StringUtil.copyPartialMatches(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), new ArrayList<>());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("pickaxegive")) {
            return StringUtil.copyPartialMatches(args[2], IntStream.rangeClosed(1, 100).mapToObj(String::valueOf).collect(Collectors.toList()), new ArrayList<>());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("givespawner")) {
            return StringUtil.copyPartialMatches(args[2], Arrays.stream(EntityType.values()).map(Enum::name).collect(Collectors.toList()), new ArrayList<>());
        }
        return new ArrayList<>();
    }

    @EventHandler
    public void onSpawnerPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SPAWNER) return;
        if (event.getBlockPlaced().getState() instanceof CreatureSpawner spawnerState) {
            jsonLogger.log(event.getPlayer(), event.getBlockPlaced(), "PLACED");
            spawnerState.getPersistentDataContainer().set(PLAYER_PLACED_KEY, PersistentDataType.BOOLEAN, true);
            if (event.getItemInHand().getItemMeta() instanceof BlockStateMeta bsm) {
                CreatureSpawner state = (CreatureSpawner) bsm.getBlockState();
                spawnerState.setSpawnedType(state.getSpawnedType());
            }
            spawnerState.update();
        }
    }

    @EventHandler
    public void onSpawnerBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        Material blockType = event.getBlock().getType();
        if (isSpawnerPickaxe(itemInHand) && blockType != Material.SPAWNER) {
            player.sendMessage(getMessage("sp-pickaxe-only"));
            event.setCancelled(true);
            return;
        }
        if (blockType != Material.SPAWNER) return;
        if (!(event.getBlock().getState() instanceof CreatureSpawner spawnerState)) {
            event.setCancelled(true);
            return;
        }
        boolean isNaturalSpawner = !spawnerState.getPersistentDataContainer().has(PLAYER_PLACED_KEY);
        if (isNaturalSpawner && !naturalSpawnerBreak) {
            player.sendMessage(getMessage("natural-spawner-break-denied"));
            event.setCancelled(true);
            return;
        }
        EntityType brokenType = spawnerState.getSpawnedType();
        if (brokenType == null && !allowEmptySpawnerBreak) {
            player.sendMessage(getMessage("empty-spawner-break-denied"));
            event.setCancelled(true);
            return;
        }
        switch (systemMode) {
            case "advanced":
                if (!isSpawnerPickaxe(itemInHand)) {
                    player.sendMessage(getMessage("advanced-pickaxe-required"));
                    event.setCancelled(true);
                    return;
                }
                ItemMeta meta = itemInHand.getItemMeta();
                int usesLeft = meta.getPersistentDataContainer().getOrDefault(USES_KEY, PersistentDataType.INTEGER, 1) - 1;
                if (usesLeft > 0) {
                    meta.getPersistentDataContainer().set(USES_KEY, PersistentDataType.INTEGER, usesLeft);
                    List<String> newLore = new ArrayList<>();
                    for (String line : getLangList("spawner-pickaxe-item.lore")) {
                        newLore.add(line.replace("%uses%", String.valueOf(usesLeft)));
                    }
                    meta.setLore(newLore);
                    itemInHand.setItemMeta(meta);
                } else {
                    player.getInventory().setItemInMainHand(null);
                    player.sendMessage(getMessage("pickaxe-broken"));
                }
                break;
            case "classic":
                if (!itemInHand.containsEnchantment(Enchantment.SILK_TOUCH)) {
                    player.sendMessage(getMessage("classic-silk-required"));
                    event.setCancelled(true);
                    return;
                }
                break;
        }
        jsonLogger.log(player, event.getBlock(), "BROKE");
        event.setDropItems(false);
        event.setExpToDrop(0);
        ItemStack spawnerItem = (brokenType != null) ? createSpawnerItem(brokenType) : createEmptySpawner();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(spawnerItem);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            player.sendMessage(getMessage("inventory-full"));
        } else {
            player.sendMessage(getMessage("spawner-collected"));
        }
    }

    private ItemStack createSpawnerItem(EntityType type) {
        ItemStack spawner = new ItemStack(Material.SPAWNER);
        ItemMeta meta = spawner.getItemMeta();
        String modeTag = getLangString("mode-tags." + systemMode);
        String name = getLangString("spawner-item.name").replace("%type%", type.name());
        List<String> lore = getLangList("spawner-item.lore").stream()
                .map(line -> line.replace("%type%", type.name()).replace("%mode%", modeTag))
                .collect(Collectors.toList());
        meta.setDisplayName(name);
        meta.setLore(lore);
        if (meta instanceof BlockStateMeta) {
            BlockStateMeta bsm = (BlockStateMeta) meta;
            CreatureSpawner state = (CreatureSpawner) bsm.getBlockState();
            state.setSpawnedType(type);
            bsm.setBlockState(state);
        }
        spawner.setItemMeta(meta);
        return spawner;
    }

    private ItemStack createEmptySpawner() {
        ItemStack spawner = new ItemStack(Material.SPAWNER);
        ItemMeta meta = spawner.getItemMeta();
        String modeTag = getLangString("mode-tags." + systemMode);
        String name = getLangString("spawner-item.name").replace("%type%", "Empty");
        List<String> lore = getLangList("spawner-item.lore").stream()
                .map(line -> line.replace("%type%", "Empty").replace("%mode%", modeTag))
                .collect(Collectors.toList());
        meta.setDisplayName(name);
        meta.setLore(lore);
        spawner.setItemMeta(meta);
        return spawner;
    }

    private ItemStack createSpawnerPickaxe(int uses) {
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = pickaxe.getItemMeta();
        String name = getLangString("spawner-pickaxe-item.name");
        List<String> lore = getLangList("spawner-pickaxe-item.lore").stream()
                .map(line -> line.replace("%uses%", String.valueOf(uses)))
                .collect(Collectors.toList());
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(SPAWNER_PICKAXE_KEY, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(USES_KEY, PersistentDataType.INTEGER, uses);
        meta.addEnchant(Enchantment.DIG_SPEED, 5, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        pickaxe.setItemMeta(meta);
        return pickaxe;
    }

    private boolean isSpawnerPickaxe(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(SPAWNER_PICKAXE_KEY);
    }
}
