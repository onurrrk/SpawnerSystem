package me.spawner.config;

import me.spawner.utils.Compat;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class PluginSettings {
    public volatile String systemMode = "advanced";
    public volatile String menuType = "V2";
    public volatile boolean menuV2 = true;
    public volatile boolean menuEnabled;
    public volatile boolean dropStoredOnBreak;
    public volatile boolean storageEnabled;
    public volatile boolean autoSlayerEnabled;
    public volatile boolean xpVacuumEnabled;
    public volatile boolean wipeStoredItems;
    public volatile boolean wipeStoredXp;
    public volatile boolean ignoreSpawnConditions;
    public volatile int storageMaxPages;
    public volatile boolean naturalSpawnerBreak;
    public volatile boolean naturalBreakPermissionRequired;
    public volatile boolean boosterEnabled;
    public volatile boolean boosterReturnOnBreak;
    public volatile boolean perSpawnerBoost;
    public volatile boolean stackingEnabled;
    public volatile int maxStack;
    public volatile boolean perMobTypeLimit;
    public volatile boolean boosterAllowStacking;
    public volatile int boosterMaxStacked;
    public volatile int boosterMinDelaySeconds;
    public volatile boolean breakChanceEnabled;
    public volatile double breakChanceDefault;
    public volatile boolean allowEmptySpawnerBreak;
    public volatile int range;
    public volatile int delay;
    public volatile int amount;
    public volatile int maxMobsPerChunk;
    public volatile boolean perSpawnerLimit;
    public volatile int hRadius;
    public volatile int itemRange;
    public volatile int itemDelay;
    public volatile int itemHRadius;
    public volatile boolean itemGiveXp;
    public volatile boolean chunkLimitEnabled;
    public volatile int spawnerLimitPerChunk;
    public volatile boolean separateChunkLimits;
    public volatile int mobSpawnerLimitPerChunk;
    public volatile int itemSpawnerLimitPerChunk;
    public volatile boolean nerfMobs;
    public volatile boolean hologramsEnabled;
    public volatile double hologramVisibleDistance;
    public volatile boolean hologramSeeThrough;
    public volatile boolean cullingEnabled;
    public volatile int cullingWarnDuration;
    public volatile int cullingCheckInterval;
    public volatile boolean cullingIgnoreLeashed;
    public volatile boolean cullingIgnoreNamed;
    public volatile boolean cullingIgnoreTamed;
    public volatile Set<String> cullingValidMobTypes = new HashSet<>();
    public volatile boolean cullingAllMobs;

    public void load(JavaPlugin plugin) {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        systemMode = cfg.getString("system", "advanced").trim().toLowerCase(Locale.ROOT);
        String requestedMenuType = cfg.getString("spawner-menu-type", "V1").trim().toUpperCase(Locale.ROOT);
        if (!requestedMenuType.equals("V1") && !requestedMenuType.equals("V2")) {
            plugin.getLogger().warning("config.yml: 'spawner-menu-type' is \"" + requestedMenuType
                    + "\" but it must be \"V1\" or \"V2\". Using \"V2\".");
            requestedMenuType = "V2";
        }
        menuType = requestedMenuType;
        menuV2 = menuType.equals("V2");
        dropStoredOnBreak = cfg.getBoolean("spawner-drop-stored-on-break", false);
        menuEnabled = cfg.getBoolean("spawner-menu", true);
        if (!menuEnabled) {
            storageEnabled = false;
            autoSlayerEnabled = false;
            xpVacuumEnabled = false;
            wipeStoredItems = false;
            wipeStoredXp = false;
        } else if (menuV2) {
            storageEnabled = true;
            autoSlayerEnabled = true;
            xpVacuumEnabled = true;
            wipeStoredItems = false;
            wipeStoredXp = false;
        } else {
            storageEnabled = cfg.getBoolean("spawner-storage.enabled", true);
            autoSlayerEnabled = cfg.getBoolean("spawner-auto-slayer.enabled", true);
            xpVacuumEnabled = autoSlayerEnabled;
            wipeStoredItems = !storageEnabled;
            wipeStoredXp = !xpVacuumEnabled;
        }
        ignoreSpawnConditions = menuEnabled && menuV2;
        storageMaxPages = Math.max(1, Math.min(100, cfg.getInt("spawner-storage.max-pages", 1)));
        naturalSpawnerBreak = cfg.getBoolean("natural-spawner-break", false);
        naturalBreakPermissionRequired = cfg.getBoolean("natural-spawner-break-permission-required", false);
        boosterEnabled = cfg.getBoolean("spawner-booster.enabled", true);
        boosterAllowStacking = cfg.getBoolean("spawner-booster.allow-stacking", false);
        boosterMaxStacked = Math.max(1, cfg.getInt("spawner-booster.max-stacked-boosters", 5));
        boosterMinDelaySeconds = Math.max(0, cfg.getInt("spawner-booster.min-spawn-delay-seconds", 2));
        boosterReturnOnBreak = cfg.getBoolean("spawner-booster.return-on-break", true);
        perSpawnerBoost = cfg.getBoolean("spawner-booster.per-spawner-boost", true);
        stackingEnabled = cfg.getBoolean("spawner-stacking.enabled", true);
        int configuredMaxStack = cfg.getInt("spawner-stacking.max-stack", 5);
        maxStack = configuredMaxStack < 0 ? Integer.MAX_VALUE / 4 : Math.max(1, configuredMaxStack);
        perMobTypeLimit = cfg.getBoolean("chunk-limits.per-mob-type", false);
        breakChanceEnabled = cfg.getBoolean("spawner-break-chance.enabled", false);
        breakChanceDefault = cfg.getDouble("spawner-break-chance.default-chance", 0.0);
        allowEmptySpawnerBreak = cfg.getBoolean("allow-empty-spawner-break", true);
        range = cfg.getInt("Spawners.DEFAULT.range", 16);
        delay = cfg.getInt("Spawners.DEFAULT.delay", 500);
        amount = cfg.getInt("Spawners.DEFAULT.amount", 3);
        maxMobsPerChunk = cfg.getInt("Spawners.DEFAULT.max-mobs-per-chunk", 4);
        String limitMode = cfg.getString("Spawners.DEFAULT.limit-mode", "chunk").trim().toLowerCase(Locale.ROOT);
        perSpawnerLimit = limitMode.equals("spawner");
        if (!limitMode.equals("chunk") && !limitMode.equals("spawner")) {
            plugin.getLogger().warning("config.yml: 'limit-mode' must be \"chunk\" or \"spawner\" but was \"" + limitMode + "\". Using \"chunk\".");
        }
        hRadius = cfg.getInt("Spawners.DEFAULT.spawn-horizontal-radius", 3);
        itemRange = cfg.getInt("Spawners.ITEM.range", range);
        itemDelay = cfg.getInt("Spawners.ITEM.delay", delay);
        itemHRadius = cfg.getInt("Spawners.ITEM.spawn-horizontal-radius", hRadius);
        itemGiveXp = cfg.getBoolean("Spawners.ITEM.give-xp", true);
        chunkLimitEnabled = cfg.getBoolean("chunk-limits.enabled", true);
        spawnerLimitPerChunk = cfg.getInt("chunk-limits.spawner-limit", 5);
        separateChunkLimits = cfg.getBoolean("chunk-limits.separate-limits", false);
        mobSpawnerLimitPerChunk = cfg.getInt("chunk-limits.mob-spawner-limit", spawnerLimitPerChunk);
        itemSpawnerLimitPerChunk = cfg.getInt("chunk-limits.item-spawner-limit", spawnerLimitPerChunk);
        nerfMobs = cfg.getBoolean("nerf-spawner-mobs", true);

        if (maxMobsPerChunk < amount) {
            plugin.getLogger().warning("config.yml: 'max-mobs-per-chunk' (" + maxMobsPerChunk
                    + ") is smaller than 'amount' (" + amount + "). A spawner is set to spawn "
                    + amount + " mobs at once, but the chunk limit fills up earlier so some spawns will be cancelled. "
                    + "Set max-mobs-per-chunk to at least " + amount + " for 'amount' to work fully.");
        }

        hologramsEnabled = cfg.getBoolean("hologram-enabled", true);
        if (hologramsEnabled && !displaySupported()) {
            hologramsEnabled = false;
            plugin.getLogger().warning("Holograms were disabled because this server version has no text display entities (1.19.4 or newer is required).");
        }
        hologramVisibleDistance = cfg.getDouble("hologram-distance", 8.0);
        hologramSeeThrough = cfg.getBoolean("SeeThrough", true);

        cullingEnabled = cfg.getBoolean("chunk-mob-culling.enabled", true);
        cullingWarnDuration = cfg.getInt("chunk-mob-culling.warning-duration-seconds", 20);
        cullingCheckInterval = cfg.getInt("chunk-mob-culling.check-interval-ticks", 100);
        cullingIgnoreLeashed = cfg.getBoolean("chunk-mob-culling.ignore-leashed", true);
        cullingIgnoreNamed = cfg.getBoolean("chunk-mob-culling.ignore-named", true);
        cullingIgnoreTamed = cfg.getBoolean("chunk-mob-culling.ignore-tamed", true);
        Set<String> validMobs = new HashSet<>();
        for (String name : cfg.getStringList("chunk-mob-culling.valid-mobs")) {
            EntityType resolved = Compat.entityType(name);
            validMobs.add(resolved != null ? resolved.name() : name.trim().toUpperCase(Locale.ROOT));
        }
        cullingValidMobTypes = validMobs;
        cullingAllMobs = validMobs.contains("ALL");
    }

    private static boolean displaySupported() {
        try {
            Class.forName("org.bukkit.entity.TextDisplay");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
