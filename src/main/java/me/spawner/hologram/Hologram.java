package me.spawner.hologram;

import me.spawner.Spawner;
import me.spawner.config.LanguageManager;
import me.spawner.utils.ColorUtils;
import me.spawner.utils.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class Hologram {
    public static final Set<Hologram> paperHolograms = ConcurrentHashMap.newKeySet();
    private static final String IDENTIFIER_PREFIX = "SpawnerSystem-Holo-";

    public static final class PlayerSnapshot {
        private final UUID[] worlds;
        private final double[] xs;
        private final double[] ys;
        private final double[] zs;
        private int size = 0;

        private PlayerSnapshot(int capacity) {
            worlds = new UUID[capacity];
            xs = new double[capacity];
            ys = new double[capacity];
            zs = new double[capacity];
        }

        public boolean anyWithin(UUID world, double x, double y, double z, double rangeSq) {
            for (int i = 0; i < size; i++) {
                if (!world.equals(worlds[i])) continue;
                double dx = xs[i] - x, dy = ys[i] - y, dz = zs[i] - z;
                if (dx * dx + dy * dy + dz * dz <= rangeSq) return true;
            }
            return false;
        }
    }

    public static PlayerSnapshot snapshotPlayers() {
        java.util.Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        PlayerSnapshot snap = new PlayerSnapshot(online.size());
        for (Player p : online) {
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            Location l = p.getLocation();
            if (l.getWorld() == null) continue;
            snap.worlds[snap.size] = l.getWorld().getUID();
            snap.xs[snap.size] = l.getX();
            snap.ys[snap.size] = l.getY();
            snap.zs[snap.size] = l.getZ();
            snap.size++;
        }
        return snap;
    }

    private final Spawner plugin;
    private CreatureSpawner spawner;
    private final Location location;
    private final double visibleDistance;
    private final FileConfiguration languageConfig;
    private boolean lastSpawnerState = true;

    private final AtomicReference<TextDisplay> displayEntity = new AtomicReference<>(null);
    private Object updaterTask;
    private volatile boolean removed = false;
    private volatile boolean blocked = false;
    private Runnable onSelfRemove;

    private final boolean isMobHologram;
    private final LivingEntity targetMob;
    private List<String> currentMobHologramText;
    private String lastSentText = null;

    private final boolean seeThrough;
    private final String uniqueIdentifier;

    private long lastSpawnTime;
    private volatile long maxDelayMs;
    private volatile double activationRangeSq;

    private String cachedTypeKey;
    private final NamespacedKey itemSpawnerKey;
    private boolean cachedTypeInitialized = false;
    private String cachedTranslatedTypeName;
    private String cachedModeTag;
    private List<String> cachedRawStatusLines;

    private Hologram(Spawner plugin, FileConfiguration languageConfig, CreatureSpawner spawner, LivingEntity targetMob,
                     Location location, double visibleDistance, boolean isMob, List<String> initialText, boolean seeThrough) {
        this.plugin = plugin;
        this.languageConfig = languageConfig;
        this.spawner = spawner;
        this.location = location;
        this.isMobHologram = isMob;
        this.targetMob = targetMob;
        this.visibleDistance = visibleDistance;
        this.currentMobHologramText = (initialText != null) ? initialText : new ArrayList<>();
        this.seeThrough = seeThrough;
        this.uniqueIdentifier = IDENTIFIER_PREFIX + UUID.randomUUID();
        this.itemSpawnerKey = new NamespacedKey(plugin, "item_spawner");

        int configDelay = plugin.getConfig().getInt("Spawners.DEFAULT.delay", 500);
        this.maxDelayMs = (configDelay * 50L) + 3000L;
        this.lastSpawnTime = System.currentTimeMillis();

        int configRange = plugin.getConfig().getInt("Spawners.DEFAULT.range", 16);
        this.activationRangeSq = (double) configRange * configRange;
        syncTimings(spawner);

        if (isMobHologram) {
            Scheduler.runEntityTask(plugin, targetMob, () -> {
                if (removed) return;
                spawnMobHolograms();
                startTracking();
            });
        } else {
            Scheduler.runLocationTask(plugin, location, () -> {
                if (removed) return;
                spawnSpawnerHolograms();
                startTracking();
            });
        }
    }

    public static Hologram createSpawnerHologram(Spawner plugin, FileConfiguration languageConfig, CreatureSpawner spawner, double visibleDistance, boolean seeThrough) {
        return new Hologram(plugin, languageConfig, spawner, null, spawner.getLocation(), visibleDistance, false, null, seeThrough);
    }

    public static Hologram createMobHologram(Spawner plugin, FileConfiguration languageConfig, LivingEntity mob, double visibleDistance, List<String> initialText, boolean seeThrough) {
        return new Hologram(plugin, languageConfig, null, mob, mob.getLocation(), visibleDistance, true, initialText, seeThrough);
    }

    public void setOnSelfRemove(Runnable onSelfRemove) {
        this.onSelfRemove = onSelfRemove;
    }

    public Location getLocation() {
        return isMobHologram ? (targetMob != null ? targetMob.getLocation() : location) : location;
    }

    public double getVisibleDistance() {
        return this.visibleDistance;
    }

    public boolean isMobHologram() {
        return this.isMobHologram;
    }

    public String getUniqueIdentifier() {
        return uniqueIdentifier;
    }

    public static String locationKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public static String chunkKey(String world, int chunkX, int chunkZ) {
        return world + "," + chunkX + "," + chunkZ;
    }

    public static String chunkKey(Location loc) {
        return chunkKey(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    private void spawnMobHolograms() {
        if (targetMob == null || !targetMob.isValid()) return;
        removeEntitiesOnly();

        final String text = String.join("\n", this.currentMobHologramText);
        TextDisplay display = targetMob.getWorld().spawn(targetMob.getLocation(), TextDisplay.class, d -> {
            setupDisplayAttributes(d);
            d.setText(text);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0.3f, 0),
                    new AxisAngle4f(),
                    new Vector3f(1, 1, 1),
                    new AxisAngle4f()
            ));
        });

        lastSentText = text;
        targetMob.addPassenger(display);
        displayEntity.set(display);
    }

    private void spawnSpawnerHolograms() {
        removeEntitiesOnly();

        Location loc = location.clone().add(0.5, 1.3, 0.5);
        final String text = String.join("\n", getHologramLines());
        TextDisplay display = location.getWorld().spawn(loc, TextDisplay.class, d -> {
            setupDisplayAttributes(d);
            d.setText(text);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(),
                    new Vector3f(1, 1, 1),
                    new AxisAngle4f()
            ));
        });

        lastSentText = text;
        displayEntity.set(display);
    }

    private void setupDisplayAttributes(TextDisplay display) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setDefaultBackground(false);
        display.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
        display.setSeeThrough(this.seeThrough);
        display.setShadowed(true);
        display.setPersistent(false);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setCustomName(uniqueIdentifier);
        display.setCustomNameVisible(false);

        try {
            display.setInterpolationDuration(0);
            display.setInterpolationDelay(0);
        } catch (Throwable ignored) {
        }

        try {
            display.getClass().getMethod("setTeleportDuration", int.class).invoke(display, 0);
        } catch (Throwable ignored) {
        }

        try {
            display.setViewRange((float) Math.max(0.05, visibleDistance / 64.0));
        } catch (Throwable ignored) {
        }
    }

    private void startTracking() {
        if (Scheduler.isFolia()) {
            if (isMobHologram) {
                this.updaterTask = Scheduler.runEntityTaskTimer(plugin, targetMob, this::updateMobLogic, this::removeSelf, 20L, 20L);
            } else {
                this.updaterTask = Scheduler.runLocationTaskTimer(plugin, location, () -> updateSpawnerLogic(playerNearbyFolia()), 20L, 20L);
            }
        } else {
            paperHolograms.add(this);
        }
    }

    private void pushText(String text) {
        if (text.equals(lastSentText)) return;

        TextDisplay display = displayEntity.get();
        if (display == null) return;

        lastSentText = text;
        Scheduler.runEntityTask(plugin, display, () -> {
            if (display.isValid()) {
                display.setText(text);
            }
        });
    }

    public void updateMobHologramText(List<String> textLines) {
        if (!isMobHologram) return;
        this.currentMobHologramText = textLines;
        pushText(String.join("\n", textLines));
    }

    public void removeHologram() {
        if (removed) return;
        removed = true;

        Scheduler.cancelTask(updaterTask);
        updaterTask = null;
        paperHolograms.remove(this);

        TextDisplay display = displayEntity.get();
        if (Scheduler.isFolia() && display != null && display.isValid()) {
            Scheduler.runEntityTask(plugin, display, this::removeEntitiesOnly);
        } else {
            Scheduler.runLocationTask(plugin, getLocation(), this::removeEntitiesOnly);
        }
    }

    private void removeSelf() {
        removeHologram();
        if (onSelfRemove != null) {
            onSelfRemove.run();
        }
    }

    private void removeEntitiesOnly() {
        TextDisplay display = displayEntity.getAndSet(null);
        if (display != null && display.isValid()) {
            try {
                display.remove();
            } catch (Throwable ignored) {
            }
        }
        lastSentText = null;
    }

    private void syncTimings(CreatureSpawner source) {
        if (source == null) return;
        int range = Math.max(1, plugin.spawnerSettings().effectiveRange(source));
        this.activationRangeSq = (double) range * range;
        this.maxDelayMs = Math.max(1, plugin.spawnerSettings().effectiveDelay(source)) * 50L + 3000L;
    }

    public void recordSuccessfulSpawn(CreatureSpawner updatedSpawner) {
        this.spawner = updatedSpawner;
        syncTimings(updatedSpawner);
        this.blocked = false;
        this.lastSpawnTime = System.currentTimeMillis();
        checkAndUpdateState(true);
    }

    public void recordBlocked(CreatureSpawner updatedSpawner) {
        this.spawner = updatedSpawner;
        syncTimings(updatedSpawner);
        this.blocked = true;
        checkAndUpdateState(false);
    }

    private void checkAndUpdateState(boolean isWorking) {
        if (this.lastSpawnerState != isWorking) {
            this.lastSpawnerState = isWorking;
            pushText(String.join("\n", getHologramLines()));
        }
    }

    public void updateSpawnerState(CreatureSpawner updatedSpawner) {
        this.spawner = updatedSpawner;
        syncTimings(updatedSpawner);
        invalidateEntityCache();
        pushText(String.join("\n", getHologramLines()));
    }

    public void tickPaper(PlayerSnapshot snapshot) {
        if (removed) return;
        if (isMobHologram) {
            updateMobLogic();
        } else {
            World world = location.getWorld();
            boolean nearby = world != null && snapshot.anyWithin(world.getUID(),
                    location.getBlockX(), location.getBlockY(), location.getBlockZ(), activationRangeSq);
            updateSpawnerLogic(nearby);
        }
    }

    private boolean playerNearbyFolia() {
        World world = location.getWorld();
        if (world == null) return false;
        try {
            double r = Math.sqrt(activationRangeSq);
            for (Entity entity : world.getNearbyEntities(location, r, r, r, e -> e instanceof Player)) {
                Player p = (Player) entity;
                if (!p.isDead() && p.getGameMode() != org.bukkit.GameMode.SPECTATOR
                        && p.getLocation().distanceSquared(location) <= activationRangeSq) {
                    return true;
                }
            }
            return false;
        } catch (IllegalStateException | UnsupportedOperationException e) {
            return playerNearbyFallback(world);
        }
    }

    private boolean playerNearbyFallback(World world) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                Location playerLocation = p.getLocation();
                if (world.equals(playerLocation.getWorld()) && playerLocation.distanceSquared(location) <= activationRangeSq) {
                    return true;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return false;
    }

    private void updateMobLogic() {
        if (removed) return;
        if (targetMob == null || targetMob.isDead() || !targetMob.isValid()) {
            removeSelf();
            return;
        }

        TextDisplay display = displayEntity.get();
        if (display != null && display.isValid() && !targetMob.getPassengers().contains(display)) {
            targetMob.addPassenger(display);
        }
    }

    private void updateSpawnerLogic(boolean playerNearby) {
        if (removed) return;
        World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;

        if (location.getBlock().getType() != Material.SPAWNER) {
            removeSelf();
            return;
        }

        if (!playerNearby) {
            lastSpawnTime += 1000;
        }

        boolean isWorking = !blocked && (System.currentTimeMillis() - lastSpawnTime) <= maxDelayMs;
        checkAndUpdateState(isWorking);
    }

    private void invalidateEntityCache() {
        cachedTypeInitialized = false;
    }

    private List<String> getHologramLines() {
        EntityType type = spawner != null ? spawner.getSpawnedType() : null;
        String itemType = spawner != null
                ? spawner.getPersistentDataContainer().get(itemSpawnerKey, PersistentDataType.STRING)
                : null;
        String typeKey = itemType != null ? "ITEM:" + itemType : (type != null ? type.name() : "EMPTY");

        if (!cachedTypeInitialized || !typeKey.equals(cachedTypeKey)) {
            cachedTypeInitialized = true;
            cachedTypeKey = typeKey;
            cachedTranslatedTypeName = itemType != null
                    ? LanguageManager.itemName(languageConfig, itemType)
                    : LanguageManager.entityName(languageConfig, type);
            cachedModeTag = getLangString("mode-tags." + plugin.settings().systemMode);
            cachedRawStatusLines = languageConfig.getStringList("spawnable-status");
        }

        String statusSymbol = lastSpawnerState ? getLangString("can_spawn") : getLangString("cannot_spawn");

        List<String> processedLines = new ArrayList<>(cachedRawStatusLines.size());
        for (String line : cachedRawStatusLines) {
            String processed = line.replace("%type%", cachedTranslatedTypeName)
                    .replace("%mode%", cachedModeTag)
                    .replace("%status%", statusSymbol);
            processedLines.add(ColorUtils.color(processed));
        }

        return processedLines;
    }

    private String getLangString(String path) {
        return ColorUtils.color(languageConfig.getString(path, path));
    }
}
