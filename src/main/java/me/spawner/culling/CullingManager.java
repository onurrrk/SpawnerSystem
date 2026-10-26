package me.spawner.culling;

import me.spawner.Spawner;
import me.spawner.config.PluginSettings;
import me.spawner.hologram.Hologram;
import me.spawner.utils.ChunkUtils;
import me.spawner.utils.ColorUtils;
import me.spawner.utils.PDCUtils;
import me.spawner.utils.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public final class CullingManager {

    private record ChunkRef(UUID world, int x, int z) {
    }

    private static final class Presence {
        final String chunkKey;
        final long since;
        volatile long lastSeen;

        Presence(String chunkKey, long since) {
            this.chunkKey = chunkKey;
            this.since = since;
            this.lastSeen = since;
        }
    }

    private record SpawnerCount(int value, long expires) {
    }

    private static final class WarnedMob {
        final Hologram hologram;
        final Object task;

        WarnedMob(Hologram hologram, Object task) {
            this.hologram = hologram;
            this.task = task;
        }
    }

    private static final long TRACKED_PERIOD_TICKS = 10L;
    private static final long FOLIA_RUN_PERIOD_TICKS = 2L;
    private static final long PRESENCE_TTL_MS = 300_000L;
    private static final long HOT_TTL_MS = 30_000L;
    private static final int HOT_POLL_EVERY_RUNS = 5;
    private static final long PRUNE_INTERVAL_MS = 60_000L;
    private static final long SPAWNER_COUNT_TTL_MS = 5_000L;

    private final Spawner plugin;
    private final Map<UUID, LivingEntity> tracked = new ConcurrentHashMap<>();
    private final Set<ChunkRef> dirtyChunks = ConcurrentHashMap.newKeySet();
    private final Queue<ChunkRef> sweepQueue = new ConcurrentLinkedQueue<>();
    private final Set<ChunkRef> loadedChunks = ConcurrentHashMap.newKeySet();
    private final Map<ChunkRef, Long> hotChunks = new ConcurrentHashMap<>();
    private final Map<UUID, WarnedMob> warnedMobs = new ConcurrentHashMap<>();
    private final Map<UUID, Presence> presence = new ConcurrentHashMap<>();
    private final Map<ChunkRef, SpawnerCount> spawnerCounts = new ConcurrentHashMap<>();
    private final Set<ChunkRef> refreshingCounts = ConcurrentHashMap.newKeySet();
    private volatile boolean running = false;
    private volatile long lastPrune = System.currentTimeMillis();
    private int runCounter = 0;
    private int sweepBudget = 1;
    private Object taskHandle;

    public CullingManager(Spawner plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.settings().cullingEnabled) return;
        Scheduler.cancelTask(taskHandle);
        taskHandle = null;
        running = true;

        if (Scheduler.isFolia()) {
            loadedChunks.clear();
            try {
                for (World world : Bukkit.getWorlds()) {
                    for (Chunk chunk : world.getLoadedChunks()) {
                        loadedChunks.add(new ChunkRef(world.getUID(), chunk.getX(), chunk.getZ()));
                    }
                }
            } catch (Throwable ignored) {
            }
            taskHandle = Scheduler.runAsyncTaskTimer(plugin, this::foliaTick, 20L, FOLIA_RUN_PERIOD_TICKS);
        } else {
            tracked.clear();
            for (World world : Bukkit.getWorlds()) {
                for (LivingEntity living : world.getLivingEntities()) {
                    track(living);
                }
            }
            taskHandle = Scheduler.runAsyncTaskTimer(plugin, this::trackedSweep, 20L, TRACKED_PERIOD_TICKS);
        }
    }

    public void stop() {
        running = false;
        Scheduler.cancelTask(taskHandle);
        taskHandle = null;
        tracked.clear();
        dirtyChunks.clear();
        sweepQueue.clear();
        loadedChunks.clear();
        spawnerCounts.clear();
        refreshingCounts.clear();
        hotChunks.clear();

        new ArrayList<>(warnedMobs.values()).forEach(info -> {
            Scheduler.cancelTask(info.task);
            if (info.hologram != null) {
                info.hologram.removeHologram();
            }
        });
        warnedMobs.clear();
    }

    public void onMobAdded(Entity entity) {
        if (!running || !isCullable(entity)) return;

        if (Scheduler.isFolia()) {
            Location loc = entity.getLocation();
            if (loc.getWorld() != null) {
                dirtyChunks.add(new ChunkRef(loc.getWorld().getUID(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4));
            }
        } else {
            track(entity);
        }
    }

    public void trackAll(Collection<? extends Entity> entities) {
        if (!running || Scheduler.isFolia()) return;
        for (Entity entity : entities) {
            track(entity);
        }
    }

    public void chunkLoaded(Chunk chunk) {
        if (!running || !Scheduler.isFolia()) return;
        ChunkRef ref = new ChunkRef(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        loadedChunks.add(ref);
        dirtyChunks.add(ref);
    }

    public void chunkUnloaded(World world, int chunkX, int chunkZ) {
        if (!Scheduler.isFolia()) return;
        loadedChunks.remove(new ChunkRef(world.getUID(), chunkX, chunkZ));
    }

    private void track(Entity entity) {
        if (entity instanceof LivingEntity living && isCullable(entity)) {
            tracked.put(entity.getUniqueId(), living);
        }
    }

    private boolean isCullable(Entity entity) {
        if (!(entity instanceof LivingEntity) || entity instanceof Player || entity instanceof ArmorStand) {
            return false;
        }
        PluginSettings settings = plugin.settings();
        return settings.cullingAllMobs || settings.cullingValidMobTypes.contains(entity.getType().name());
    }

    private boolean isExempt(LivingEntity mob) {
        PluginSettings settings = plugin.settings();
        if (settings.cullingIgnoreLeashed && mob.isLeashed()) return true;
        if (settings.cullingIgnoreNamed && mob.getCustomName() != null) return true;
        return settings.cullingIgnoreTamed && mob instanceof Tameable tameable && tameable.isTamed();
    }

    private int limitFor(ChunkRef ref, Chunk chunk) {
        int base = plugin.settings().maxMobsPerChunk;
        if (!plugin.settings().perSpawnerLimit) {
            return base;
        }

        long now = System.currentTimeMillis();
        SpawnerCount cached = spawnerCounts.get(ref);
        if (cached != null && cached.expires() >= now) {
            return base * Math.max(1, cached.value());
        }

        if (chunk != null) {
            int value = ChunkUtils.countStacked(chunk, null, plugin.stacks().stackKey(), null);
            spawnerCounts.put(ref, new SpawnerCount(value, now + SPAWNER_COUNT_TTL_MS));
            return base * Math.max(1, value);
        }

        refreshSpawnerCount(ref);
        return cached == null ? -1 : base * Math.max(1, cached.value());
    }

    private void refreshSpawnerCount(ChunkRef ref) {
        World world = Bukkit.getWorld(ref.world());
        if (world == null || !refreshingCounts.add(ref)) return;

        Location anchor = new Location(world, ref.x() * 16, 64, ref.z() * 16);
        Scheduler.runLocationTask(plugin, anchor, () -> {
            try {
                if (world.isChunkLoaded(ref.x(), ref.z())) {
                    int value = ChunkUtils.countStacked(world.getChunkAt(ref.x(), ref.z()), null, plugin.stacks().stackKey(), null);
                    spawnerCounts.put(ref, new SpawnerCount(value, System.currentTimeMillis() + SPAWNER_COUNT_TTL_MS));
                }
            } finally {
                refreshingCounts.remove(ref);
            }
        });
    }

    private void pruneIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastPrune < PRUNE_INTERVAL_MS) return;
        lastPrune = now;
        presence.values().removeIf(p -> now - p.lastSeen > PRESENCE_TTL_MS);
        spawnerCounts.values().removeIf(c -> now - c.expires() > PRESENCE_TTL_MS);
    }

    private void trackedSweep() {
        pruneIfDue();
        if (tracked.isEmpty()) return;

        long now = System.currentTimeMillis();
        Map<ChunkRef, Map<EntityType, List<LivingEntity>>> groups = new HashMap<>();

        Iterator<LivingEntity> iterator = tracked.values().iterator();
        while (iterator.hasNext()) {
            LivingEntity mob = iterator.next();
            if (!mob.isValid() || mob.isDead() || !isCullable(mob)) {
                iterator.remove();
                continue;
            }
            if (isExempt(mob)) continue;
            Location loc = mob.getLocation();
            if (loc.getWorld() == null) continue;

            ChunkRef ref = new ChunkRef(loc.getWorld().getUID(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
            groups.computeIfAbsent(ref, k -> new HashMap<>())
                    .computeIfAbsent(mob.getType(), k -> new ArrayList<>())
                    .add(mob);
        }

        for (Map.Entry<ChunkRef, Map<EntityType, List<LivingEntity>>> entry : groups.entrySet()) {
            ChunkRef ref = entry.getKey();
            int limit = limitFor(ref, null);
            if (limit < 0) continue;
            evaluate(ref.world() + "," + ref.x() + "," + ref.z(), entry.getValue(), now, limit);
        }
    }

    private void foliaTick() {
        pruneIfDue();

        runCounter++;
        if (runCounter % HOT_POLL_EVERY_RUNS == 0 && !hotChunks.isEmpty()) {
            long now = System.currentTimeMillis();
            for (Map.Entry<ChunkRef, Long> entry : new ArrayList<>(hotChunks.entrySet())) {
                if (entry.getValue() < now) {
                    hotChunks.remove(entry.getKey());
                } else {
                    scheduleCheck(entry.getKey());
                }
            }
        }

        if (!dirtyChunks.isEmpty()) {
            for (ChunkRef ref : new ArrayList<>(dirtyChunks)) {
                dirtyChunks.remove(ref);
                scheduleCheck(ref);
            }
        }

        if (sweepQueue.isEmpty()) {
            sweepQueue.addAll(loadedChunks);
            long runsPerCycle = Math.max(1L, plugin.settings().cullingCheckInterval / FOLIA_RUN_PERIOD_TICKS);
            sweepBudget = (int) Math.max(1L, (sweepQueue.size() + runsPerCycle - 1) / runsPerCycle);
        }

        for (int i = 0; i < sweepBudget; i++) {
            ChunkRef ref = sweepQueue.poll();
            if (ref == null) break;
            scheduleCheck(ref);
        }
    }

    private void scheduleCheck(ChunkRef ref) {
        World world = Bukkit.getWorld(ref.world());
        if (world == null) return;

        Location anchor = new Location(world, ref.x() * 16, 64, ref.z() * 16);
        Scheduler.runLocationTask(plugin, anchor, () -> {
            if (world.isChunkLoaded(ref.x(), ref.z())) {
                processChunk(world.getChunkAt(ref.x(), ref.z()));
            }
        });
    }

    private void processChunk(Chunk chunk) {
        try {
            if (!chunk.isEntitiesLoaded()) {
                dirtyChunks.add(new ChunkRef(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()));
                return;
            }
        } catch (Throwable ignored) {
        }

        String chunkKey = chunk.getWorld().getUID() + "," + chunk.getX() + "," + chunk.getZ();
        Map<EntityType, List<LivingEntity>> byType = new HashMap<>();
        for (Entity entity : chunk.getEntities()) {
            if (entity.isValid() && isCullable(entity) && !isExempt((LivingEntity) entity)) {
                byType.computeIfAbsent(entity.getType(), k -> new ArrayList<>()).add((LivingEntity) entity);
            }
        }

        int limit = limitFor(new ChunkRef(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()), chunk);
        if (evaluate(chunkKey, byType, System.currentTimeMillis(), limit)) {
            markHot(chunk);
        }
    }

    private boolean evaluate(String chunkKey, Map<EntityType, List<LivingEntity>> byType, long now, int limit) {
        boolean nearLimit = false;

        Comparator<LivingEntity> newestArrivalFirst = Comparator
                .comparingLong((LivingEntity mob) -> sinceOf(mob)).reversed()
                .thenComparing(mob -> mob.getUniqueId());

        for (List<LivingEntity> entities : byType.values()) {
            for (LivingEntity mob : entities) {
                Presence known = presence.get(mob.getUniqueId());
                if (known == null || !known.chunkKey.equals(chunkKey)) {
                    long arrival = known == null && bornAtSpawner(mob) ? 0L : now;
                    known = new Presence(chunkKey, arrival);
                    presence.put(mob.getUniqueId(), known);
                }
                known.lastSeen = now;
            }
        }

        for (List<LivingEntity> entities : byType.values()) {
            if (entities.size() >= Math.max(1, limit - 1)) {
                nearLimit = true;
            }
            if (entities.size() <= limit && !hasWarned(entities)) {
                continue;
            }

            entities.sort(newestArrivalFirst);
            int excess = Math.max(0, entities.size() - limit);

            for (int i = 0; i < entities.size(); i++) {
                LivingEntity mob = entities.get(i);
                boolean mustLeave = i < excess;
                boolean warned = warnedMobs.containsKey(mob.getUniqueId());

                if (mustLeave && !warned) {
                    warn(mob);
                } else if (!mustLeave && warned) {
                    cleanup(mob.getUniqueId());
                }
            }
        }
        return nearLimit;
    }

    private boolean hasWarned(List<LivingEntity> entities) {
        if (warnedMobs.isEmpty()) return false;
        for (LivingEntity mob : entities) {
            if (warnedMobs.containsKey(mob.getUniqueId())) return true;
        }
        return false;
    }

    private void markHot(Chunk chunk) {
        long expires = System.currentTimeMillis() + HOT_TTL_MS;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                hotChunks.put(new ChunkRef(chunk.getWorld().getUID(), chunk.getX() + dx, chunk.getZ() + dz), expires);
            }
        }
    }

    private boolean bornAtSpawner(LivingEntity mob) {
        try {
            return PDCUtils.getBoolean(mob.getPersistentDataContainer(), plugin.keys().spawnerBorn, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private long sinceOf(LivingEntity mob) {
        Presence p = presence.get(mob.getUniqueId());
        return p != null ? p.since : 0L;
    }

    private List<String> warningLines(int secondsLeft) {
        return plugin.language().config().getStringList("mob-remove-warning").stream()
                .map(line -> ColorUtils.color(line.replace("%time%", String.valueOf(secondsLeft))))
                .collect(Collectors.toList());
    }

    private void warn(LivingEntity mob) {
        final int warnDuration = plugin.settings().cullingWarnDuration;
        final Hologram hologram = plugin.holograms().createMobWarning(mob, warningLines(warnDuration));

        Runnable runner = new Runnable() {
            private int timeLeft = warnDuration;
            private final Chunk initialChunk = mob.getChunk();

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead()) {
                    cleanup(mob.getUniqueId());
                    return;
                }

                if (isExempt(mob)) {
                    cleanup(mob.getUniqueId());
                    return;
                }

                Chunk currentChunk = mob.getChunk();
                if (!currentChunk.equals(initialChunk)) {
                    cleanup(mob.getUniqueId());
                    return;
                }

                long currentCount = 0;
                for (Entity entity : initialChunk.getEntities()) {
                    if (entity.getType() == mob.getType() && !(entity instanceof LivingEntity other && isExempt(other))) {
                        currentCount++;
                    }
                }

                int limit = limitFor(new ChunkRef(initialChunk.getWorld().getUID(), initialChunk.getX(), initialChunk.getZ()), initialChunk);
                if (currentCount <= limit) {
                    cleanup(mob.getUniqueId());
                    return;
                }

                if (timeLeft <= 0) {
                    mob.remove();
                    cleanup(mob.getUniqueId());
                    return;
                }

                if (hologram != null) {
                    hologram.updateMobHologramText(warningLines(timeLeft));
                }
                timeLeft--;
            }
        };

        Object task = Scheduler.runEntityTaskTimer(plugin, mob, runner, () -> cleanup(mob.getUniqueId()), 1L, 20L);
        warnedMobs.put(mob.getUniqueId(), new WarnedMob(hologram, task));
    }

    private void cleanup(UUID mobId) {
        WarnedMob info = warnedMobs.remove(mobId);
        if (info != null) {
            Scheduler.cancelTask(info.task);
            if (info.hologram != null) {
                info.hologram.removeHologram();
            }
        }
    }
}
