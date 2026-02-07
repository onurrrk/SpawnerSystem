package me.spawner;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.joml.AxisAngle4f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Hologram {
    private final JavaPlugin plugin;
    private CreatureSpawner spawner;
    private final Location location;
    private double visibleDistance;
    private final Map<UUID, Boolean> viewingPlayers = new ConcurrentHashMap<>();
    private FileConfiguration languageConfig;
    private boolean lastSpawnerState = false;

    private final List<TextDisplay> displayEntities = new ArrayList<>();
    private BukkitTask updaterTask;

    private final boolean isMobHologram;
    private LivingEntity targetMob;
    private List<String> currentMobHologramText;

    private Hologram(JavaPlugin plugin, FileConfiguration languageConfig, CreatureSpawner spawner, LivingEntity targetMob, Location location, double visibleDistance, boolean isMob, List<String> initialText) {
        this.plugin = plugin;
        this.languageConfig = languageConfig;
        this.spawner = spawner;
        this.location = location;
        this.isMobHologram = isMob;
        this.targetMob = targetMob;
        this.visibleDistance = visibleDistance;
        this.currentMobHologramText = (initialText != null) ? initialText : new ArrayList<>();

        if (isMobHologram) {
            spawnMobHolograms();
            startMobVisibilityTask();
        } else {
            spawnSpawnerHolograms();
            startSpawnerUpdater();
        }
    }

    public static Hologram createSpawnerHologram(JavaPlugin plugin, FileConfiguration languageConfig, CreatureSpawner spawner, double visibleDistance) {
        return new Hologram(plugin, languageConfig, spawner, null, spawner.getLocation(), visibleDistance, false, null);
    }

    public static Hologram createMobHologram(JavaPlugin plugin, FileConfiguration languageConfig, LivingEntity mob, double visibleDistance, List<String> initialText) {
        return new Hologram(plugin, languageConfig, null, mob, mob.getLocation(), visibleDistance, true, initialText);
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

        int lineCount = 2;

        for (int i = 0; i < lineCount; i++) {
            TextDisplay display = (TextDisplay) targetMob.getWorld().spawnEntity(targetMob.getLocation(), EntityType.TEXT_DISPLAY);
            setupDisplayAttributes(display);

            targetMob.addPassenger(display);

            float baseHeight = 0.5f;
            float lineSpacing = 0.25f;

            float yOffset = baseHeight + ((lineCount - 1 - i) * lineSpacing);

            display.setTransformation(new Transformation(
                    new Vector3f(0, yOffset, 0),
                    new AxisAngle4f(),
                    new Vector3f(1, 1, 1),
                    new AxisAngle4f()
            ));

            display.setVisibleByDefault(false);
            displayEntities.add(display);
        }

        updateMobHologramText(this.currentMobHologramText);
    }

    private void spawnSpawnerHolograms() {
        String[] lines = getHologramLines();
        for (int i = 0; i < lines.length; i++) {
            Location loc = getSpawnerHologramLocation(i);
            TextDisplay display = (TextDisplay) location.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
            setupDisplayAttributes(display);

            display.setText(lines[i]);
            display.setVisibleByDefault(false);

            displayEntities.add(display);
        }
    }

    private void setupDisplayAttributes(TextDisplay display) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setBackgroundColor(org.bukkit.Color.fromARGB(60, 0, 0, 0));
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.setPersistent(false);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setBrightness(null);
    }

    public void showTo(Player player) {
        if (viewingPlayers.containsKey(player.getUniqueId())) return;

        for (TextDisplay display : displayEntities) {
            if (display.isValid()) {
                player.showEntity(plugin, display);
            }
        }
        viewingPlayers.put(player.getUniqueId(), true);
    }

    public void hideFrom(Player player) {
        if (viewingPlayers.remove(player.getUniqueId()) == null) return;

        for (TextDisplay display : displayEntities) {
            if (display.isValid()) {
                player.hideEntity(plugin, display);
            }
        }
    }

    public void updateMobHologramText(List<String> textLines) {
        if (!isMobHologram) return;
        this.currentMobHologramText = textLines;

        for (int i = 0; i < displayEntities.size(); i++) {
            if (i < textLines.size()) {
                displayEntities.get(i).setText(textLines.get(i));
            } else {
                displayEntities.get(i).setText("");
            }
        }
    }

    public void removeHologram() {
        if (this.updaterTask != null) {
            this.updaterTask.cancel();
            this.updaterTask = null;
        }

        for (TextDisplay display : displayEntities) {
            if (display.isValid()) {
                display.remove();
            }
        }
        displayEntities.clear();
        viewingPlayers.clear();
    }

    private void startMobVisibilityTask() {
        this.updaterTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (targetMob == null || targetMob.isDead() || !targetMob.isValid()) {
                removeHologram();
                return;
            }

            for (TextDisplay display : displayEntities) {
                if (display.isValid() && !targetMob.getPassengers().contains(display)) {
                    targetMob.addPassenger(display);
                }
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getWorld().equals(targetMob.getWorld())) {
                    if (viewingPlayers.containsKey(player.getUniqueId())) hideFrom(player);
                    continue;
                }

                double distSq = player.getLocation().distanceSquared(targetMob.getLocation());
                boolean canSee = distSq <= visibleDistance * visibleDistance;
                boolean isViewing = viewingPlayers.containsKey(player.getUniqueId());

                if (canSee && !isViewing) {
                    showTo(player);
                } else if (!canSee && isViewing) {
                    hideFrom(player);
                }
            }
        }, 10L, 10L);
    }

    private void startSpawnerUpdater() {
        this.lastSpawnerState = isSpawnerActive();
        this.updaterTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateSpawnerState, 20L, 20L);
    }

    private void updateSpawnerState() {
        if (location.getBlock().getType() != Material.SPAWNER) {
            removeHologram();
            return;
        }

        refreshSpawner();
        boolean currentState = isSpawnerActive();

        if (currentState != lastSpawnerState) {
            lastSpawnerState = currentState;
            String[] lines = getHologramLines();
            if (displayEntities.size() > 2) {
                displayEntities.get(2).setText(lines[2]);
            }
        }

        for (Player player : location.getWorld().getPlayers()) {
            boolean isVisible = viewingPlayers.containsKey(player.getUniqueId());
            if (player.getLocation().distanceSquared(location) <= visibleDistance * visibleDistance) {
                if (!isVisible) showTo(player);
            } else {
                if (isVisible) hideFrom(player);
            }
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        String lower = str.toLowerCase();
        return lower.substring(0, 1).toUpperCase() + lower.substring(1);
    }

    private String[] getHologramLines() {
        EntityType type = spawner.getSpawnedType();
        String translatedTypeName = (type != null) ? languageConfig.getString("entity-types." + type.name(), capitalize(type.name())) : "Empty";
        String modeTag = getLangString("mode-tags." + plugin.getConfig().getString("system", "advanced"));

        String[] lines = new String[3];
        lines[0] = getLangString("spawnable-status.mob_type").replace("%type%", translatedTypeName);
        lines[1] = getLangString("spawnable-status.break_with").replace("%mode%", modeTag);
        lines[2] = isSpawnerActive() ? getLangString("spawnable-status.can_spawn") : getLangString("spawnable-status.cannot_spawn");
        return lines;
    }

    private void refreshSpawner() {
        if (location.getBlock().getState() instanceof CreatureSpawner) {
            spawner = (CreatureSpawner) location.getBlock().getState();
        }
    }

    private boolean isSpawnerActive() {
        if (spawner == null) return false;
        return spawner.getDelay() > 20;
    }

    private String getLangString(String path) {
        return ChatColor.translateAlternateColorCodes('&', languageConfig.getString(path, path));
    }

    private Location getSpawnerHologramLocation(int lineIndex) {
        double gapAboveBlock = 0.3;
        double lineSpacing = 0.25;
        return location.clone().add(0.5, 1.0 + gapAboveBlock + ((2 - lineIndex) * lineSpacing), 0.5);
    }
}
