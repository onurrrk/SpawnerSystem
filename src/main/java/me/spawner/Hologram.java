package me.spawner;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import me.spawner.utils.ColorUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Hologram {
    public static final Set<Hologram> paperHolograms = ConcurrentHashMap.newKeySet();

    private final JavaPlugin plugin;
    private CreatureSpawner spawner;
    private final Location location;
    private double visibleDistance;
    private final Map<UUID, Boolean> viewingPlayers = new ConcurrentHashMap<>();
    private FileConfiguration languageConfig;
    private boolean lastSpawnerState = true;

    private TextDisplay displayEntity;
    private Object updaterTask;

    private final boolean isMobHologram;
    private LivingEntity targetMob;
    private List<String> currentMobHologramText;

    private boolean isFolia;
    private boolean seeThrough;

    private long lastSpawnTime;
    private final long maxDelayMs;
    private final double activationRangeSq;
    private long lastUpdateMillis = 0;

    private Hologram(JavaPlugin plugin, FileConfiguration languageConfig, CreatureSpawner spawner, LivingEntity targetMob, Location location, double visibleDistance, boolean isMob, List<String> initialText, boolean seeThrough) {
        this.plugin = plugin;
        this.languageConfig = languageConfig;
        this.spawner = spawner;
        this.location = location;
        this.isMobHologram = isMob;
        this.targetMob = targetMob;
        this.visibleDistance = visibleDistance;
        this.currentMobHologramText = (initialText != null) ? initialText : new ArrayList<>();
        this.seeThrough = seeThrough;

        int configDelay = plugin.getConfig().getInt("Spawners.DEFAULT.delay", 500);
        this.maxDelayMs = (configDelay * 50L) + 3000L;
        this.lastSpawnTime = System.currentTimeMillis();

        int configRange = plugin.getConfig().getInt("Spawners.DEFAULT.range", 16);
        this.activationRangeSq = configRange * configRange;

        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            this.isFolia = true;
        } catch (ClassNotFoundException e) {
            this.isFolia = false;
        }

        if (isMobHologram) {
            runOnEntity(targetMob, () -> {
                spawnMobHolograms();
                if (isFolia) {
                    startMobVisibilityTaskFolia();
                } else {
                    paperHolograms.add(this);
                }
            });
        } else {
            runOnLocation(location, () -> {
                spawnSpawnerHolograms();
                if (isFolia) {
                    startSpawnerVisibilityTaskFolia();
                } else {
                    paperHolograms.add(this);
                }
            });
        }
    }

    public static Hologram createSpawnerHologram(JavaPlugin plugin, FileConfiguration languageConfig, CreatureSpawner spawner, double visibleDistance, boolean seeThrough) {
        return new Hologram(plugin, languageConfig, spawner, null, spawner.getLocation(), visibleDistance, false, null, seeThrough);
    }

    public static Hologram createMobHologram(JavaPlugin plugin, FileConfiguration languageConfig, LivingEntity mob, double visibleDistance, List<String> initialText, boolean seeThrough) {
        return new Hologram(plugin, languageConfig, null, mob, mob.getLocation(), visibleDistance, true, initialText, seeThrough);
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

    private void spawnMobHolograms() {
        if (targetMob == null || !targetMob.isValid()) return;
        removeEntitiesOnly();

        displayEntity = (TextDisplay) targetMob.getWorld().spawnEntity(targetMob.getLocation(), EntityType.TEXT_DISPLAY);
        setupDisplayAttributes(displayEntity);
        targetMob.addPassenger(displayEntity);

        displayEntity.setTransformation(new Transformation(
                new Vector3f(0, 0.3f, 0),
                new AxisAngle4f(),
                new Vector3f(1, 1, 1),
                new AxisAngle4f()
        ));

        displayEntity.setVisibleByDefault(false);
        updateMobHologramText(this.currentMobHologramText);
    }

    private void spawnSpawnerHolograms() {
        removeEntitiesOnly();

        Location loc = location.clone().add(0.5, 1.3, 0.5);
        displayEntity = (TextDisplay) location.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        setupDisplayAttributes(displayEntity);

        List<String> lines = getHologramLines();
        displayEntity.setText(String.join("\n", lines));
        displayEntity.setVisibleByDefault(false);
    }

    private void setupDisplayAttributes(TextDisplay display) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setDefaultBackground(false);
        display.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
        display.setSeeThrough(this.seeThrough);
        display.setShadowed(true);
        display.setPersistent(false);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setBrightness(new Display.Brightness(15, 15));
    }

    public void showTo(Player player) {
        if (viewingPlayers.containsKey(player.getUniqueId())) return;
        viewingPlayers.put(player.getUniqueId(), true);

        if (displayEntity != null && displayEntity.isValid()) {
            if (isFolia) {
                displayEntity.getScheduler().run(plugin, (t) -> player.showEntity(plugin, displayEntity), null);
            } else {
                player.showEntity(plugin, displayEntity);
            }
        }
    }

    public void hideFrom(Player player) {
        if (viewingPlayers.remove(player.getUniqueId()) == null) return;

        if (displayEntity != null && displayEntity.isValid()) {
            if (isFolia) {
                displayEntity.getScheduler().run(plugin, (t) -> player.hideEntity(plugin, displayEntity), null);
            } else {
                player.hideEntity(plugin, displayEntity);
            }
        }
    }

    public void updateMobHologramText(List<String> textLines) {
        if (!isMobHologram) return;
        this.currentMobHologramText = textLines;

        if (displayEntity != null && displayEntity.isValid()) {
            displayEntity.setText(String.join("\n", textLines));
        }
    }

    public void removeHologram() {
        cancelTask();
        if (!isFolia) {
            paperHolograms.remove(this);
        }

        Runnable removeLogic = this::removeEntitiesOnly;

        if (isFolia) {
            if (isMobHologram && targetMob != null && targetMob.isValid()) {
                runOnEntity(targetMob, removeLogic);
            } else {
                runOnLocation(location, removeLogic);
            }
        } else {
            removeLogic.run();
        }
    }

    private void removeEntitiesOnly() {
        if (displayEntity != null && displayEntity.isValid()) {
            displayEntity.remove();
        }
        displayEntity = null;
        viewingPlayers.clear();
    }

    public void recordSuccessfulSpawn(CreatureSpawner updatedSpawner) {
        this.spawner = updatedSpawner;
        this.lastSpawnTime = System.currentTimeMillis();
        checkAndUpdateState(true);
    }

    private void checkAndUpdateState(boolean isWorking) {
        if (this.lastSpawnerState != isWorking) {
            this.lastSpawnerState = isWorking;
            if (displayEntity != null && displayEntity.isValid()) {
                List<String> lines = getHologramLines();
                displayEntity.setText(String.join("\n", lines));
            }
        }
    }
    
    public void updateSpawnerState(CreatureSpawner updatedSpawner) {
        this.spawner = updatedSpawner;
        if (displayEntity != null && displayEntity.isValid()) {
            List<String> lines = getHologramLines();
            displayEntity.setText(String.join("\n", lines));
        }
    }

    public void tickPaper() {
        if (isMobHologram) {
            updateMobLogic();
        } else {
            updateSpawnerLogic();
        }
    }

    private void updateMobLogic() {
        if (targetMob == null || targetMob.isDead() || !targetMob.isValid()) {
            removeHologram();
            return;
        }

        if (displayEntity != null && displayEntity.isValid() && !targetMob.getPassengers().contains(displayEntity)) {
            targetMob.addPassenger(displayEntity);
        }

        double distSq = visibleDistance * visibleDistance;

        for (UUID uuid : new ArrayList<>(viewingPlayers.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline() || p.getWorld() != targetMob.getWorld() || p.getLocation().distanceSquared(targetMob.getLocation()) > distSq) {
                if (p != null) hideFrom(p);
                else viewingPlayers.remove(uuid);
            }
        }

        for (Player player : targetMob.getWorld().getNearbyEntitiesByType(Player.class, targetMob.getLocation(), visibleDistance)) {
            if (player.getLocation().distanceSquared(targetMob.getLocation()) <= distSq) {
                showTo(player);
            }
        }
    }

    private void updateSpawnerLogic() {
        if (!location.isWorldLoaded() || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;

        if (location.getBlock().getType() != Material.SPAWNER) {
            removeHologram();
            return;
        }

        double maxRadius = Math.max(visibleDistance, Math.sqrt(activationRangeSq));
        boolean playerNearby = false;
        double distSq = visibleDistance * visibleDistance;

        for (UUID uuid : new ArrayList<>(viewingPlayers.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline() || p.getWorld() != location.getWorld() || p.getLocation().distanceSquared(location) > distSq) {
                if (p != null) hideFrom(p);
                else viewingPlayers.remove(uuid);
            }
        }

        for (Player player : location.getWorld().getNearbyEntitiesByType(Player.class, location, maxRadius)) {
            double dist = player.getLocation().distanceSquared(location);
            if (dist <= this.activationRangeSq) {
                playerNearby = true;
            }
            if (dist <= distSq) {
                showTo(player);
            }
        }

        if (!playerNearby) {
            lastSpawnTime += 1000;
        }

        boolean isWorking = (System.currentTimeMillis() - lastSpawnTime) <= maxDelayMs;
        checkAndUpdateState(isWorking);
    }

    private void startMobVisibilityTaskFolia() {
        this.updaterTask = targetMob.getScheduler().runAtFixedRate(plugin, (t) -> updateMobLogic(), null, 20L, 20L);
    }

    private void startSpawnerVisibilityTaskFolia() {
        this.updaterTask = Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, (t) -> updateSpawnerLogic(), 20L, 20L);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        String lower = str.toLowerCase();
        return lower.substring(0, 1).toUpperCase() + lower.substring(1);
    }

    private List<String> getHologramLines() {
        EntityType type = spawner != null ? spawner.getSpawnedType() : null;
        String translatedTypeName = (type != null) ? languageConfig.getString("entity-types." + type.name(), capitalize(type.name())) : "Empty";
        String modeTag = getLangString("mode-tags." + plugin.getConfig().getString("system", "advanced"));

        String statusSymbol = lastSpawnerState ? getLangString("can_spawn") : getLangString("cannot_spawn");

        List<String> rawLines = languageConfig.getStringList("spawnable-status");
        List<String> processedLines = new ArrayList<>();

        for (String line : rawLines) {
            String processed = line.replace("%type%", translatedTypeName)
                                   .replace("%mode%", modeTag)
                                   .replace("%status%", statusSymbol);
            processedLines.add(me.spawner.utils.ColorUtils.color(processed));
        }

        return processedLines;
    }

    private String getLangString(String path) {
        return me.spawner.utils.ColorUtils.color(languageConfig.getString(path, path));
    }

    private void runOnLocation(Location loc, Runnable runnable) {
        if (isFolia) {
            Bukkit.getRegionScheduler().execute(plugin, loc, runnable);
        } else {
            runnable.run();
        }
    }

    private void runOnEntity(LivingEntity entity, Runnable runnable) {
        if (isFolia) {
            entity.getScheduler().run(plugin, (t) -> runnable.run(), null);
        } else {
            runnable.run();
        }
    }

    private void cancelTask() {
        if (updaterTask == null) return;

        if (isFolia) {
            try {
                Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
                Method cancelMethod = scheduledTaskClass.getMethod("cancel");
                cancelMethod.invoke(updaterTask);
            } catch (Exception ignored) {}
        } else {
            if (updaterTask instanceof org.bukkit.scheduler.BukkitTask) {
                ((org.bukkit.scheduler.BukkitTask) updaterTask).cancel();
            }
        }
        updaterTask = null;
    }
}