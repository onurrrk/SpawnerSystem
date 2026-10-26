package me.spawner.spawner;

import me.spawner.Spawner;
import me.spawner.hologram.Hologram;
import me.spawner.menu.SpawnerMenu;
import me.spawner.utils.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnerTicker {
    public static final int PERIOD = 10;
    public static final int FIRST_CYCLE_TICKS = 30;
    private static final int MAX_VANILLA_DELAY = 32767;

    private static final class Entry {
        private final String key;
        private final Location location;
        private volatile int delay;
        private volatile double rangeSq;
        private int remaining;
        private volatile Object task;

        Entry(String key, Location location, int delay, double rangeSq) {
            this.key = key;
            this.location = location;
            this.delay = delay;
            this.rangeSq = rangeSq;
            this.remaining = delay;
        }
    }

    private final Spawner plugin;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> keysByChunk = new ConcurrentHashMap<>();
    private volatile boolean active = false;
    private Object timer;

    public SpawnerTicker(Spawner plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return active;
    }

    public void start() {
        stop();
        active = plugin.settings().ignoreSpawnConditions
                || (plugin.settings().menuEnabled && plugin.settings().autoSlayerEnabled);
        if (active && !Scheduler.isFolia()) {
            timer = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, PERIOD, PERIOD);
        }
    }

    public void stop() {
        active = false;
        Scheduler.cancelTask(timer);
        timer = null;
        for (Entry entry : new ArrayList<>(entries.values())) {
            Scheduler.cancelTask(entry.task);
            entry.task = null;
        }
        entries.clear();
        keysByChunk.clear();
    }

    public void sync(CreatureSpawner spawner, int delay, int range) {
        if (!active) return;

        Location loc = spawner.getLocation();
        if (loc.getWorld() == null) return;

        String key = Hologram.locationKey(loc);
        int ticks = Math.max(1, delay);
        long safeRange = Math.max(1, range);
        double rangeSq = (double) safeRange * safeRange;

        Entry existing = entries.get(key);
        if (existing != null) {
            existing.delay = ticks;
            existing.rangeSq = rangeSq;
            if (existing.remaining > ticks) {
                existing.remaining = ticks;
            }
            return;
        }

        Entry entry = new Entry(key, loc, ticks, rangeSq);
        if (entries.putIfAbsent(key, entry) != null) return;
        keysByChunk.computeIfAbsent(Hologram.chunkKey(loc), k -> ConcurrentHashMap.newKeySet()).add(key);
        if (Scheduler.isFolia()) {
            entry.task = Scheduler.runLocationTaskTimer(plugin, loc, () -> stepFolia(entry), PERIOD, PERIOD);
        }
    }

    public void untrack(CreatureSpawner spawner) {
        if (!active) return;

        Location loc = spawner.getLocation();
        if (loc.getWorld() == null) return;

        Entry entry = entries.get(Hologram.locationKey(loc));
        if (entry != null) {
            remove(entry);
        }
    }

    public void untrackChunk(String world, int chunkX, int chunkZ) {
        if (!active) return;

        Set<String> keys = keysByChunk.remove(Hologram.chunkKey(world, chunkX, chunkZ));
        if (keys == null) return;
        for (String key : keys) {
            Entry entry = entries.remove(key);
            if (entry != null) {
                Scheduler.cancelTask(entry.task);
                entry.task = null;
            }
        }
    }

    private void tickAll() {
        if (!active || entries.isEmpty()) return;

        Hologram.PlayerSnapshot snapshot = Hologram.snapshotPlayers();
        for (Entry entry : entries.values()) {
            try {
                stepPaper(entry, snapshot);
            } catch (Throwable t) {
                entry.remaining = entry.delay;
                plugin.getLogger().warning("Spawner cycle failed at " + entry.key + ": " + t);
            }
        }
    }

    private void stepPaper(Entry entry, Hologram.PlayerSnapshot snapshot) {
        Location loc = entry.location;
        World world = loc.getWorld();
        if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            remove(entry);
            return;
        }
        if (!snapshot.anyWithin(world.getUID(), loc.getBlockX() + 0.5, loc.getBlockY() + 0.5,
                loc.getBlockZ() + 0.5, entry.rangeSq)) {
            return;
        }
        advance(entry);
    }

    private void stepFolia(Entry entry) {
        if (!active || entries.get(entry.key) != entry) {
            Scheduler.cancelTask(entry.task);
            return;
        }
        try {
            Location loc = entry.location;
            World world = loc.getWorld();
            if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;
            if (!playerNearby(entry, world)) return;
            advance(entry);
        } catch (Throwable t) {
            entry.remaining = entry.delay;
            plugin.getLogger().warning("Spawner cycle failed at " + entry.key + ": " + t);
        }
    }

    private boolean playerNearby(Entry entry, World world) {
        Location center = entry.location.clone().add(0.5, 0.5, 0.5);
        try {
            double radius = Math.sqrt(entry.rangeSq);
            for (Entity entity : world.getNearbyEntities(center, radius, radius, radius, e -> e instanceof Player)) {
                Player player = (Player) entity;
                if (!player.isDead() && player.getGameMode() != GameMode.SPECTATOR
                        && player.getLocation().distanceSquared(center) <= entry.rangeSq) {
                    return true;
                }
            }
            return false;
        } catch (IllegalStateException | UnsupportedOperationException e) {
            return playerNearbyFallback(entry, world, center);
        }
    }

    private boolean playerNearbyFallback(Entry entry, World world, Location center) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) continue;
                Location playerLocation = player.getLocation();
                if (world.equals(playerLocation.getWorld()) && playerLocation.distanceSquared(center) <= entry.rangeSq) {
                    return true;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return false;
    }

    private void advance(Entry entry) {
        entry.remaining -= PERIOD;
        if (entry.remaining > 0) return;
        fire(entry);
    }

    private void fire(Entry entry) {
        Location loc = entry.location;
        Block block = loc.getBlock();
        if (block.getType() != Material.SPAWNER || !(block.getState() instanceof CreatureSpawner spawner)) {
            remove(entry);
            return;
        }

        boolean itemSpawner = plugin.items().itemSpawnerType(spawner) != null;
        if (!itemSpawner && spawner.getSpawnedType() == null) {
            entry.remaining = entry.delay;
            return;
        }

        SpawnerMenu.CycleResult result = plugin.menu().runCycle(spawner);
        Hologram hologram = plugin.holograms().get(loc);
        if (hologram != null) {
            if (result.blocked()) {
                hologram.recordBlocked(spawner);
            } else {
                hologram.recordSuccessfulSpawn(spawner);
            }
        }

        int blockDelay = Math.max(1, result.delay());
        entry.delay = blockDelay;
        entry.remaining = Math.max(1, entry.remaining + blockDelay);

        spawner.setDelay(Math.min(entry.remaining, MAX_VANILLA_DELAY));
        spawner.update();
    }

    public void kickstart(CreatureSpawner spawner) {
        if (!active) return;
        Location loc = spawner.getLocation();
        if (loc.getWorld() == null) return;
        Entry entry = entries.get(Hologram.locationKey(loc));
        if (entry != null) {
            entry.remaining = Math.min(entry.remaining, FIRST_CYCLE_TICKS);
            spawner.setDelay(Math.min(entry.remaining, MAX_VANILLA_DELAY));
        }
    }

    public int remainingTicks(CreatureSpawner spawner, int fallback) {
        Location loc = spawner.getLocation();
        if (loc.getWorld() == null) return fallback;
        Entry entry = entries.get(Hologram.locationKey(loc));
        return entry == null ? fallback : Math.max(1, entry.remaining);
    }

    private void remove(Entry entry) {
        entries.remove(entry.key, entry);
        Scheduler.cancelTask(entry.task);
        entry.task = null;

        String chunkKey = Hologram.chunkKey(entry.location);
        Set<String> keys = keysByChunk.get(chunkKey);
        if (keys != null) {
            keys.remove(entry.key);
            if (keys.isEmpty()) {
                keysByChunk.remove(chunkKey, keys);
            }
        }
    }
}
