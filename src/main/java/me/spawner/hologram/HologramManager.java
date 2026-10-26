package me.spawner.hologram;

import me.spawner.Spawner;
import me.spawner.config.PluginSettings;
import me.spawner.utils.Scheduler;
import org.bukkit.Location;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class HologramManager {
    private final Spawner plugin;
    private final Map<String, Hologram> holograms = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> keysByChunk = new ConcurrentHashMap<>();
    private volatile boolean active = false;
    private Object tickerTask;

    public HologramManager(Spawner plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return active;
    }

    public void start() {
        Scheduler.cancelTask(tickerTask);
        tickerTask = null;
        active = plugin.settings().hologramsEnabled;
        if (active && !Scheduler.isFolia()) {
            tickerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (Hologram.paperHolograms.isEmpty()) return;
                Hologram.PlayerSnapshot snapshot = Hologram.snapshotPlayers();
                for (Hologram hologram : Hologram.paperHolograms) {
                    hologram.tickPaper(snapshot);
                }
            }, 20L, 20L);
        }
    }

    public void stop() {
        Scheduler.cancelTask(tickerTask);
        tickerTask = null;
        if (active) {
            for (Hologram hologram : new ArrayList<>(holograms.values())) {
                try {
                    hologram.removeHologram();
                } catch (Throwable ignored) {
                }
            }
            holograms.clear();
            keysByChunk.clear();
        }
    }

    public Hologram get(Location loc) {
        return active ? holograms.get(Hologram.locationKey(loc)) : null;
    }

    public boolean has(Location loc) {
        return active && holograms.containsKey(Hologram.locationKey(loc));
    }

    public void createForSpawner(CreatureSpawner spawnerState) {
        if (!active) return;

        Location loc = spawnerState.getLocation();
        String key = Hologram.locationKey(loc);
        if (holograms.containsKey(key)) return;

        PluginSettings settings = plugin.settings();
        Scheduler.runLocationTask(plugin, loc, () -> {
            if (holograms.containsKey(key)) return;
            Hologram hologram = Hologram.createSpawnerHologram(plugin, plugin.language().config(), spawnerState,
                    settings.hologramVisibleDistance, settings.hologramSeeThrough);
            holograms.put(key, hologram);
            keysByChunk.computeIfAbsent(Hologram.chunkKey(loc), k -> ConcurrentHashMap.newKeySet()).add(key);
            hologram.setOnSelfRemove(() -> unregister(key, loc, hologram));
        });
    }

    public void removeForSpawner(Location loc) {
        if (!active) return;

        String key = Hologram.locationKey(loc);
        Hologram hologram = holograms.get(key);
        if (hologram != null) {
            unregister(key, loc, hologram);
            Scheduler.runLocationTask(plugin, loc, hologram::removeHologram);
        }
    }

    public void removeChunk(String world, int chunkX, int chunkZ) {
        if (!active) return;

        Set<String> keys = keysByChunk.remove(Hologram.chunkKey(world, chunkX, chunkZ));
        if (keys == null) return;

        for (String key : keys) {
            Hologram hologram = holograms.remove(key);
            if (hologram != null) {
                hologram.removeHologram();
            }
        }
    }

    public Hologram createMobWarning(LivingEntity mob, List<String> lines) {
        if (!active) return null;
        PluginSettings settings = plugin.settings();
        return Hologram.createMobHologram(plugin, plugin.language().config(), mob,
                settings.hologramVisibleDistance, lines, settings.hologramSeeThrough);
    }

    private void unregister(String key, Location loc, Hologram hologram) {
        holograms.remove(key, hologram);
        String chunkKey = Hologram.chunkKey(loc);
        Set<String> keys = keysByChunk.get(chunkKey);
        if (keys != null) {
            keys.remove(key);
            if (keys.isEmpty()) {
                keysByChunk.remove(chunkKey, keys);
            }
        }
    }
}
