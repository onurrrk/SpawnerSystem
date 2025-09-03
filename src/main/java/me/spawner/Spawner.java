package me.spawner;

import me.spawner.utils.JsonLogger;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class Spawner extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private final NamespacedKey SPAWNER_PICKAXE_KEY = new NamespacedKey(this, "spawner_pickaxe");
    private final NamespacedKey USES_KEY = new NamespacedKey(this, "uses_left");
    private final NamespacedKey PLAYER_PLACED_KEY = new NamespacedKey(this, "player_placed_spawner");
    
    private final Map<Location, Hologram> holograms = new HashMap<>();
    private final Map<UUID, MobCullingInfo> warnedMobs = new ConcurrentHashMap<>();
    
    private String systemMode;
    private boolean naturalSpawnerBreak;
    private boolean allowEmptySpawnerBreak;
    private JsonLogger jsonLogger;
    private FileConfiguration languageConfig;
    private int range, delay, amount, maxMobsPerChunk, hRadius;
    private boolean chunkLimitEnabled, nerfMobs;
    private int spawnerLimitPerChunk;

    private boolean hologramsEnabledByConfig = false;
    private boolean canUseHolograms = false;
    private boolean cullingEnabled = false;
    private int cullingWarnDuration;
    private int cullingCheckInterval;
    private double hologramVisibleDistance;
    private Set<String> cullingValidMobTypes;

    private static class MobCullingInfo {
        final Hologram hologram;
        final BukkitTask task;
        MobCullingInfo(Hologram hologram, BukkitTask task) {
            this.hologram = hologram;
            this.task = task;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadLanguages();
        loadLanguage();
        loadConfigValues();

        boolean needsProtocolLib = hologramsEnabledByConfig || cullingEnabled;
        if (needsProtocolLib) {
            if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
                canUseHolograms = true;
                getLogger().info("ProtocolLib found. Hologram features have been successfully enabled!");
            } else {
                canUseHolograms = false;
                getLogger().warning("ProtocolLib plugin not found! Hologram features have been disabled.");
                getLogger().warning("To use holograms, please install ProtocolLib on your server.");
            }
        }

        this.jsonLogger = new JsonLogger(this);
        getCommand("spsystem").setExecutor(this);
        getCommand("spsystem").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        
        loadAllSpawnersInLoadedChunks();
        
        if (cullingEnabled) {
            startMobCullingTask();
        }
        
        getLogger().info("SpawnerSystem plugin enabled in '" + systemMode + "' mode!");
    }
    
    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (canUseHolograms) {
            new ArrayList<>(holograms.values()).forEach(Hologram::removeHologram);
            holograms.clear();
            new ArrayList<>(warnedMobs.values()).forEach(info -> {
                if (info.hologram != null) info.hologram.removeHologram();
            });
            warnedMobs.clear();
        }
        getLogger().info("SpawnerSystem plugin disabled!");
    }
    
    private void reloadPlugin() {
        if (canUseHolograms) {
            new ArrayList<>(holograms.values()).forEach(Hologram::removeHologram);
            holograms.clear();
            new ArrayList<>(warnedMobs.values()).forEach(info -> {
                info.task.cancel();
                if (info.hologram != null) info.hologram.removeHologram();
            });
            warnedMobs.clear();
        }
        
        reloadConfig();
        loadLanguages();
        loadLanguage();
        loadConfigValues();

        boolean needsProtocolLib = hologramsEnabledByConfig || cullingEnabled;
        if (needsProtocolLib) {
            if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
                canUseHolograms = true;
            } else {
                canUseHolograms = false;
                getLogger().warning("ProtocolLib not found on reload! Disabling hologram feature.");
            }
        }
        loadAllSpawnersInLoadedChunks();
        if (cullingEnabled) {
            startMobCullingTask();
        }
    }

    private void loadConfigValues() {
        reloadConfig();
        FileConfiguration cfg = getConfig();
        systemMode = cfg.getString("system", "advanced").toLowerCase();
        naturalSpawnerBreak = cfg.getBoolean("natural-spawner-break", false);
        allowEmptySpawnerBreak = cfg.getBoolean("allow-empty-spawner-break", true);
        range = cfg.getInt("Spawners.DEFAULT.range", 16);
        delay = cfg.getInt("Spawners.DEFAULT.delay", 500);
        amount = cfg.getInt("Spawners.DEFAULT.amount", 3);
        maxMobsPerChunk = cfg.getInt("Spawners.DEFAULT.max-mobs-per-chunk", 4);
        hRadius = cfg.getInt("Spawners.DEFAULT.spawn-horizontal-radius", 3);
        chunkLimitEnabled = cfg.getBoolean("chunk-limits.enabled", true);
        spawnerLimitPerChunk = cfg.getInt("chunk-limits.spawner-limit", 5);
        nerfMobs = cfg.getBoolean("nerf-spawner-mobs", true);
        
        hologramsEnabledByConfig = cfg.getBoolean("hologram-enabled", true);
        hologramVisibleDistance = cfg.getDouble("hologram-distance", 8.0);
        
        cullingEnabled = cfg.getBoolean("chunk-mob-culling.enabled", true);
        cullingWarnDuration = cfg.getInt("chunk-mob-culling.warning-duration-seconds", 20);
        cullingCheckInterval = cfg.getInt("chunk-mob-culling.check-interval-ticks", 100);
        cullingValidMobTypes = new HashSet<>(cfg.getStringList("chunk-mob-culling.valid-mobs"));
    }
    
    private void loadLanguages() {
        File langFolder = new File(getDataFolder(), "languages");
        if (!langFolder.exists()) langFolder.mkdirs();
        String[] defaultLangs = {"en","tr","de","es","ru","zh","ja","az","fr","ar","nl","id","hy","it","gd","sv","ky","ko","hu","cs","el","fa","pl","ro","vi","pt","th","uk"};
        for (String lang : defaultLangs) {
            File langFile = new File(langFolder, lang + ".yml");
            if (!langFile.exists()) {
                saveResource("languages/" + lang + ".yml", false);
            }
        }
    }
    
    private void loadLanguage() {
        String lang = getConfig().getString("language", "en");
        File langFolder = new File(getDataFolder(), "languages");
        File languageFile = new File(langFolder, lang + ".yml");
        if (!languageFile.exists()) {
            languageFile = new File(langFolder, "en.yml");
        }
        languageConfig = YamlConfiguration.loadConfiguration(languageFile);
    }
    
    private void createHologramForSpawner(CreatureSpawner spawnerState) {
        if (!canUseHolograms || !hologramsEnabledByConfig) return;
        
        Location loc = spawnerState.getLocation();
        if (holograms.containsKey(loc)) return;
        
        Runnable task = () -> {
            Hologram hologram = Hologram.createSpawnerHologram(this, languageConfig, spawnerState, hologramVisibleDistance);
            holograms.put(loc, hologram);
        };

        if (isFolia()) {
            Bukkit.getRegionScheduler().execute(this, loc, task);
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }
    
    private void removeHologramForSpawner(Location loc) {
        if (!canUseHolograms || !hologramsEnabledByConfig) return;

        Hologram hologram = holograms.remove(loc);
        if (hologram != null) {
            Runnable task = hologram::removeHologram;
            
            if (isFolia()) {
                Bukkit.getRegionScheduler().execute(this, loc, task);
            } else {
                Bukkit.getScheduler().runTask(this, task);
            }
        }
    }
    
    private void loadAllSpawnersInLoadedChunks() {
        if (!canUseHolograms || !hologramsEnabledByConfig) return;

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState tileEntity : chunk.getTileEntities()) {
                    if (tileEntity instanceof CreatureSpawner spawnerState) {
                        applySettingsToSpawner(spawnerState);
                        createHologramForSpawner(spawnerState);
                    }
                }
            }
        }
    }
    
    private void applySettingsToSpawner(CreatureSpawner spawner) {
        spawner.setMinSpawnDelay(delay);
        spawner.setMaxSpawnDelay(delay);
        spawner.setSpawnCount(amount);
        spawner.setMaxNearbyEntities(999);
        spawner.setRequiredPlayerRange(range);
        spawner.setSpawnRange(hRadius);
        spawner.update();
    }
    
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!hologramsEnabledByConfig) return;
        for (BlockState tileEntity : event.getChunk().getTileEntities()) {
            if (tileEntity instanceof CreatureSpawner spawnerState) {
                applySettingsToSpawner(spawnerState);
                createHologramForSpawner(spawnerState);
            }
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!canUseHolograms || !hologramsEnabledByConfig) return;

        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        
        if (from.getBlockX() == to.getBlockX() && 
            from.getBlockY() == to.getBlockY() && 
            from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        
        updateHologramsForPlayer(player);
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!canUseHolograms || !hologramsEnabledByConfig) return;
        updateHologramsForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!canUseHolograms || !hologramsEnabledByConfig) return;

        Player player = event.getPlayer();
        for (Hologram hologram : holograms.values()) {
            hologram.hideFrom(player);
        }
    }
    
    private void updateHologramsForPlayer(Player player) {
        for (Hologram hologram : holograms.values()) {
            if (hologram.isMobHologram()) continue;
            if (player.getWorld().equals(hologram.getLocation().getWorld())) {
                double distance = player.getLocation().distance(hologram.getLocation());
                
                if (distance <= hologram.getVisibleDistance()) {
                    hologram.showTo(player);
                } else {
                    hologram.hideFrom(player);
                }
            } else {
                hologram.hideFrom(player);
            }
        }
    }
    
    @EventHandler
    public void onSpawnerPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SPAWNER) return;
        
        if (chunkLimitEnabled) {
            int spawnerCount = 0;
            for (BlockState blockState : event.getBlock().getChunk().getTileEntities()) {
                if (blockState instanceof CreatureSpawner) {
                    if (!blockState.getLocation().equals(event.getBlockPlaced().getLocation())) {
                        spawnerCount++;
                    }
                }
            }
            if (spawnerCount >= spawnerLimitPerChunk) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(getMessage("chunk-limit-exceeded"));
                return;
            }
        }

        if (event.getBlockPlaced().getState() instanceof CreatureSpawner spawnerState) {
            jsonLogger.log(event.getPlayer(), event.getBlockPlaced(), "PLACED");
            spawnerState.getPersistentDataContainer().set(PLAYER_PLACED_KEY, PersistentDataType.BOOLEAN, true);
            if (event.getItemInHand().getItemMeta() instanceof BlockStateMeta bsm) {
                if (bsm.getBlockState() instanceof CreatureSpawner itemSpawnerState) {
                    spawnerState.setSpawnedType(itemSpawnerState.getSpawnedType());
                }
            }
            applySettingsToSpawner(spawnerState);
            createHologramForSpawner(spawnerState);
        }
    }
    
    @EventHandler
    public void onSpawnerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) {
            if (isSpawnerPickaxe(event.getPlayer().getInventory().getItemInMainHand())) {
                event.getPlayer().sendMessage(getMessage("sp-pickaxe-only"));
                event.setCancelled(true);
            }
            return;
        }
        if (!(block.getState() instanceof CreatureSpawner spawnerState)) return;
        
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        
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
        
        boolean canBreak = false;
        if ("advanced".equals(systemMode)) {
            if (isSpawnerPickaxe(itemInHand)) {
                canBreak = true;
                ItemMeta meta = itemInHand.getItemMeta();
                int usesLeft = meta.getPersistentDataContainer().getOrDefault(USES_KEY, PersistentDataType.INTEGER, 1) - 1;
                if (usesLeft > 0) {
                    meta.getPersistentDataContainer().set(USES_KEY, PersistentDataType.INTEGER, usesLeft);
                    List<String> newLore = getLangList("spawner-pickaxe-item.lore").stream()
                            .map(line -> line.replace("%uses%", String.valueOf(usesLeft)))
                            .collect(Collectors.toList());
                    meta.setLore(newLore);
                    itemInHand.setItemMeta(meta);
                } else {
                    player.getInventory().setItemInMainHand(null);
                    player.sendMessage(getMessage("pickaxe-broken"));
                }
            } else {
                player.sendMessage(getMessage("advanced-pickaxe-required"));
            }
        } else if ("classic".equals(systemMode)) {
            if (itemInHand.containsEnchantment(Enchantment.SILK_TOUCH)) {
                canBreak = true;
            } else {
                player.sendMessage(getMessage("classic-silk-required"));
            }
        }
        
        if (!canBreak) {
            event.setCancelled(true);
            return;
        }
        
        removeHologramForSpawner(block.getLocation());
        
        jsonLogger.log(player, block, "BROKE");
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
    
    @EventHandler
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        CreatureSpawner spawner = event.getSpawner();
        EntityType type = spawner.getSpawnedType();
        if (type == null) return;
        int currentMobsInChunk = 0;
        for (Entity entity : spawner.getChunk().getEntities()) {
            if (entity.getType() == type) {
                currentMobsInChunk++;
            }
        }
        if (currentMobsInChunk >= maxMobsPerChunk) {
            event.setCancelled(true);
            return;
        }
        if (nerfMobs && event.getEntity() instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) event.getEntity();
            if (livingEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                livingEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.0);
            }
             if (livingEntity.getEquipment() != null) {
                 livingEntity.getEquipment().clear();
            }
        }
    }
    
    @EventHandler
    public void onPlayerUseSpawnEggOnBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null || event.getClickedBlock() == null) return;
        
        handleSpawnEgg(event.getPlayer(), event.getItem(), event.getClickedBlock().getChunk(), event);
    }
    
    @EventHandler
    public void onPlayerUseSpawnEggOnEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (event.getRightClicked() instanceof Ageable) {
            handleSpawnEgg(player, item, event.getRightClicked().getChunk(), event);
        }
    }

    private void handleSpawnEgg(Player player, ItemStack item, Chunk chunk, org.bukkit.event.Cancellable event) {
        if (item.getType() == null || !item.getType().name().endsWith("_SPAWN_EGG")) return;
        
        EntityType spawnType;
        try {
            String typeName = item.getType().name().replace("_SPAWN_EGG", "");
            spawnType = EntityType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return;
        }

        int currentMobsInChunk = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity.getType() == spawnType) {
                currentMobsInChunk++;
            }
        }
        if (currentMobsInChunk >= maxMobsPerChunk) {
            event.setCancelled(true);
            String translatedTypeName = getTranslatedEntityName(spawnType);
            player.sendMessage(getMessage("egg-chunk-limit-exceeded")
                    .replace("%type%", translatedTypeName)
                    .replace("%limit%", String.valueOf(maxMobsPerChunk))
            );
        }
    }
    
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> block.getType() == Material.SPAWNER);
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
        switch (subCommand) {
            case "reload":
                reloadPlugin();
                sender.sendMessage(getMessage("reload-success"));
                return true;
            case "pickaxegive":
                if (!"advanced".equals(systemMode)) {
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
            case "givespawner":
                if (args.length < 3) {
                    sender.sendMessage(getMessage("wrong-subcommand"));
                    return true;
                }
                Player targetSpawner = Bukkit.getPlayer(args[1]);
                if (targetSpawner == null) {
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
                
                String translatedTypeName = getTranslatedEntityName(type);
                targetSpawner.getInventory().addItem(createSpawnerItem(type));
                
                sender.sendMessage(getMessage("spawner-given-sender")
                        .replace("%player%", targetSpawner.getName())
                        .replace("%type%", translatedTypeName));
                targetSpawner.sendMessage(getMessage("spawner-given-recipient")
                        .replace("%type%", translatedTypeName));
                return true;
            default:
                sender.sendMessage(getMessage("wrong-subcommand"));
                return true;
        }
    }
    
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("spawner.admin")) return Collections.emptyList();
        
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Arrays.asList("reload", "pickaxegive", "givespawner"), completions);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("pickaxegive") || args[0].equalsIgnoreCase("givespawner"))) {
            StringUtil.copyPartialMatches(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("pickaxegive")) {
            StringUtil.copyPartialMatches(args[2], IntStream.rangeClosed(1, 100).mapToObj(String::valueOf).collect(Collectors.toList()), completions);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("givespawner")) {
            StringUtil.copyPartialMatches(args[2], Arrays.stream(EntityType.values()).filter(EntityType::isAlive).map(Enum::name).collect(Collectors.toList()), completions);
        }
        return completions;
    }
    
    private String getMessage(String path) {
        String prefix = ChatColor.translateAlternateColorCodes('&', languageConfig.getString("prefix", "&8[&aSpawner&8] &r"));
        String message = ChatColor.translateAlternateColorCodes('&', languageConfig.getString("messages." + path, "&cMessage not found: " + path));
        return prefix + " " + message;
    }
    
    private List<String> getLangList(String path) {
        return languageConfig.getStringList(path).stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList());
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        String lower = str.toLowerCase();
        return lower.substring(0, 1).toUpperCase() + lower.substring(1);
    }

    private String getTranslatedEntityName(EntityType type) {
        if (type == null) {
            return languageConfig.getString("entity-types.EMPTY", "Empty");
        }
        return languageConfig.getString("entity-types." + type.name(), capitalize(type.name()));
    }
    
    private ItemStack createSpawnerItem(EntityType type) {
        ItemStack spawner = new ItemStack(Material.SPAWNER);
        ItemMeta meta = spawner.getItemMeta();
        if (meta instanceof BlockStateMeta bsm) {
            String translatedTypeName = getTranslatedEntityName(type);

            String name = languageConfig.getString("spawner-item.name", "&a%type% Spawner")
                                         .replace("%type%", translatedTypeName);
            bsm.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            
            CreatureSpawner state = (CreatureSpawner) bsm.getBlockState();
            state.setSpawnedType(type);
            bsm.setBlockState(state);

            String modeTag = languageConfig.getString("mode-tags." + systemMode, "");
            List<String> lore = getLangList("spawner-item.lore").stream()
                    .map(line -> line.replace("%type%", translatedTypeName)
                                     .replace("%mode%", modeTag))
                    .collect(Collectors.toList());
            bsm.setLore(lore);
            
            try {
                bsm.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
            } catch (IllegalArgumentException ignored) {
            }
            
            spawner.setItemMeta(bsm);
        }
        return spawner;
    }
    
    private ItemStack createEmptySpawner() {
        ItemStack spawner = new ItemStack(Material.SPAWNER);
        ItemMeta meta = spawner.getItemMeta();
        
        String translatedTypeName = getTranslatedEntityName(null);
        
        String name = languageConfig.getString("spawner-item.name", "&a%type% Spawner")
                                     .replace("%type%", translatedTypeName);
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

        String modeTag = languageConfig.getString("mode-tags." + systemMode, "");
        List<String> lore = getLangList("spawner-item.lore").stream()
                .map(line -> line.replace("%type%", translatedTypeName)
                                 .replace("%mode%", modeTag))
                .collect(Collectors.toList());
        meta.setLore(lore);
        
        try {
            meta.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
        } catch (IllegalArgumentException ignored) {
        }
        
        spawner.setItemMeta(meta);
        return spawner;
    }
    
    private ItemStack createSpawnerPickaxe(int uses) {
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = pickaxe.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', languageConfig.getString("spawner-pickaxe-item.name")));
        meta.setLore(getLangList("spawner-pickaxe-item.lore").stream()
                .map(line -> line.replace("%uses%", String.valueOf(uses)))
                .collect(Collectors.toList()));
        
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
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(SPAWNER_PICKAXE_KEY);
    }
    
    private boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void startMobCullingTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    processChunkForCulling(chunk);
                }
            }
        }, cullingCheckInterval, cullingCheckInterval);
    }

    private void processChunkForCulling(Chunk chunk) {
        Map<EntityType, List<LivingEntity>> entitiesInChunk = new HashMap<>();
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity && entity.isValid() && cullingValidMobTypes.contains(entity.getType().name().toUpperCase())) {
                entitiesInChunk.computeIfAbsent(entity.getType(), k -> new ArrayList<>()).add((LivingEntity) entity);
            }
        }

        entitiesInChunk.forEach((type, entities) -> {
            if (entities.size() > maxMobsPerChunk) {
                entities.sort(Comparator.comparingInt(Entity::getTicksLived).reversed());
                for (int i = 0; i < entities.size() - maxMobsPerChunk; i++) {
                    LivingEntity mobToWarn = entities.get(i);
                    if (!warnedMobs.containsKey(mobToWarn.getUniqueId())) {
                        warnMob(mobToWarn);
                    }
                }
            }
        });
    }

    private void warnMob(LivingEntity mob) {
        Hologram hologram = null;
        if (canUseHolograms && hologramsEnabledByConfig) {
            List<String> initialWarningLines = languageConfig.getStringList("mob-remove-warning").stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line.replace("%time%", String.valueOf(cullingWarnDuration))))
                .collect(Collectors.toList());
            hologram = Hologram.createMobHologram(this, languageConfig, mob, hologramVisibleDistance, initialWarningLines);
        }
        
        final Hologram finalHologram = hologram;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            private int timeLeft = cullingWarnDuration;
            private final Chunk initialChunk = mob.getChunk();

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead()) {
                    cleanup(mob.getUniqueId());
                    return;
                }
                
                long currentCountInChunk = 0;
                for(Entity entity : initialChunk.getEntities()){
                    if(entity.getType() == mob.getType()){
                        currentCountInChunk++;
                    }
                }

                if (!mob.getChunk().equals(initialChunk) || currentCountInChunk <= maxMobsPerChunk) {
                    cleanup(mob.getUniqueId());
                    return;
                }

                if (timeLeft <= 0) {
                    mob.remove();
                    cleanup(mob.getUniqueId());
                    return;
                }
                
                if (finalHologram != null) {
                    List<String> warningLines = languageConfig.getStringList("mob-remove-warning").stream()
                            .map(line -> ChatColor.translateAlternateColorCodes('&', line.replace("%time%", String.valueOf(timeLeft))))
                            .collect(Collectors.toList());
                    finalHologram.updateMobHologramText(warningLines);
                }
                timeLeft--;
            }
        }, 0L, 20L);
        
        warnedMobs.put(mob.getUniqueId(), new MobCullingInfo(hologram, task));
    }

    private void cleanup(UUID mobId) {
        MobCullingInfo info = warnedMobs.remove(mobId);
        if (info != null) {
            info.task.cancel();
            if (info.hologram != null) {
                info.hologram.removeHologram();
            }
        }
    }
                                 }
