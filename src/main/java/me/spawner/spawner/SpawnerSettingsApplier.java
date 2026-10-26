package me.spawner.spawner;

import me.spawner.Spawner;
import me.spawner.booster.BoosterManager;
import me.spawner.config.PluginSettings;
import me.spawner.utils.ChunkUtils;
import me.spawner.utils.PDCUtils;
import me.spawner.utils.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnerSettingsApplier {
    private final Spawner plugin;

    public SpawnerSettingsApplier(Spawner plugin) {
        this.plugin = plugin;
    }

    private boolean isItemSpawner(CreatureSpawner spawner) {
        return plugin.items().itemSpawnerType(spawner) != null;
    }

    public int effectiveDelay(CreatureSpawner spawner) {
        return blockDelay(plugin.boosters().groups(spawner, plugin.stacks().size(spawner)), isItemSpawner(spawner));
    }

    public boolean ignoresConditions(CreatureSpawner spawner) {
        PluginSettings s = plugin.settings();
        if (s.ignoreSpawnConditions) return true;
        return s.menuEnabled && s.autoSlayerEnabled && !isItemSpawner(spawner)
                && PDCUtils.getBoolean(spawner.getPersistentDataContainer(), plugin.keys().autoKill, false);
    }

    private int minimumDelay() {
        return plugin.ticker().isActive() ? SpawnerTicker.PERIOD : 1;
    }

    public int blockDelay(List<BoosterManager.Group> groups, boolean itemSpawner) {
        int best = Integer.MAX_VALUE;
        for (BoosterManager.Group group : groups) {
            best = Math.min(best, effectiveDelay(group.boost(), itemSpawner));
        }
        if (best == Integer.MAX_VALUE) {
            PluginSettings s = plugin.settings();
            return Math.max(minimumDelay(), itemSpawner ? s.itemDelay : s.delay);
        }
        return best;
    }

    public int effectiveDelay(BoosterManager.Booster booster) {
        return effectiveDelay(booster, false);
    }

    public int effectiveDelay(BoosterManager.Booster booster, boolean itemSpawner) {
        PluginSettings s = plugin.settings();
        int base = itemSpawner ? s.itemDelay : s.delay;
        int floor = minimumDelay();
        if (booster == null || booster.delaySeconds() <= 0) {
            return Math.max(floor, base);
        }

        long minTicks = s.boosterMinDelaySeconds * 20L;
        if (base <= minTicks) {
            return Math.max(floor, base);
        }
        long reduced = base - booster.delaySeconds() * 20L;
        return (int) Math.max(floor, Math.max(minTicks, reduced));
    }

    public int maxDelayReductionSeconds(boolean itemSpawner) {
        PluginSettings s = plugin.settings();
        long base = itemSpawner ? s.itemDelay : s.delay;
        long minTicks = s.boosterMinDelaySeconds * 20L;
        return (int) Math.max(0L, (base - minTicks) / 20L);
    }

    public int maxTicketDelaySeconds() {
        return Math.max(maxDelayReductionSeconds(false), maxDelayReductionSeconds(true));
    }

    public int effectiveRange(CreatureSpawner spawner) {
        PluginSettings s = plugin.settings();
        int base = isItemSpawner(spawner) ? s.itemRange : s.range;
        long bonus = 0;
        for (BoosterManager.Group group : plugin.boosters().groups(spawner, plugin.stacks().size(spawner))) {
            if (group.boost() != null) {
                bonus = Math.max(bonus, Math.max(0, group.boost().range()));
            }
        }
        return (int) Math.min(Integer.MAX_VALUE / 2, (long) base + bonus);
    }

    public void apply(CreatureSpawner spawner) {
        PluginSettings s = plugin.settings();
        boolean carrierChanged = plugin.items().ensureItemSpawnerType(spawner);
        boolean boostCleared = plugin.boosters().clearIfExpired(spawner);
        boolean wiped = plugin.storage().wipeDisabled(spawner);
        boolean itemSpawner = isItemSpawner(spawner);
        int delay = effectiveDelay(spawner);
        int range = effectiveRange(spawner);
        boolean ignoring = ignoresConditions(spawner);
        if (ignoring) {
            plugin.ticker().sync(spawner, delay, range);
        } else {
            plugin.ticker().untrack(spawner);
        }
        int amount = itemSpawner ? 1 : s.amount;
        boolean autoSlaying = s.autoSlayerEnabled
                && (s.menuV2 || PDCUtils.getBoolean(spawner.getPersistentDataContainer(), plugin.keys().autoKill, false));
        if (!itemSpawner && !autoSlaying) {
            amount = (int) Math.min(100L, (long) s.amount * plugin.stacks().size(spawner));
        }
        if (ignoring) {
            amount = 0;
        }
        int radius = itemSpawner ? s.itemHRadius : s.hRadius;

        boolean delayDrift = false;
        if (ignoring) {
            int remaining = Math.min(plugin.ticker().remainingTicks(spawner, delay), 32767);
            if (Math.abs(spawner.getDelay() - remaining) > SpawnerTicker.PERIOD) {
                spawner.setDelay(remaining);
                delayDrift = true;
            }
        }

        if (!carrierChanged && !boostCleared && !wiped && !delayDrift && spawner.getMinSpawnDelay() == delay && spawner.getMaxSpawnDelay() == delay
                && spawner.getSpawnCount() == amount && spawner.getMaxNearbyEntities() == 999
                && spawner.getRequiredPlayerRange() == range && spawner.getSpawnRange() == radius) {
            return;
        }

        if (delay > spawner.getMaxSpawnDelay()) {
            spawner.setMaxSpawnDelay(delay);
            spawner.setMinSpawnDelay(delay);
        } else {
            spawner.setMinSpawnDelay(delay);
            spawner.setMaxSpawnDelay(delay);
        }

        spawner.setSpawnCount(amount);
        spawner.setMaxNearbyEntities(999);
        spawner.setRequiredPlayerRange(range);
        spawner.setSpawnRange(radius);
        spawner.update();
    }

    public void processChunk(Chunk chunk) {
        if (!chunk.isLoaded()) return;
        for (CreatureSpawner spawnerState : ChunkUtils.spawners(chunk)) {
            apply(spawnerState);
            plugin.boosters().scheduleExpiry(spawnerState);
            plugin.holograms().createForSpawner(spawnerState);
        }
    }

    public void loadAllLoadedChunks() {
        boolean fallback = false;
        for (World world : Bukkit.getWorlds()) {
            Chunk[] chunks;
            try {
                chunks = world.getLoadedChunks();
            } catch (UnsupportedOperationException | IllegalStateException e) {
                fallback = true;
                continue;
            }
            for (Chunk chunk : chunks) {
                Scheduler.runLocationTask(plugin, chunk.getBlock(0, 0, 0).getLocation(), () -> processChunk(chunk));
            }
        }
        if (fallback) {
            loadChunksAroundPlayers();
        }
    }

    private void loadChunksAroundPlayers() {
        int radius = Math.min(32, Math.max(Bukkit.getViewDistance(), Bukkit.getSimulationDistance()) + 1);
        Set<String> seen = ConcurrentHashMap.newKeySet();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scheduler.runEntityTask(plugin, player, () -> {
                World world = player.getWorld();
                Location base = player.getLocation();
                int centerX = base.getBlockX() >> 4;
                int centerZ = base.getBlockZ() >> 4;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int cx = centerX + dx;
                        int cz = centerZ + dz;
                        if (!seen.add(world.getUID() + ":" + cx + ":" + cz)) continue;
                        Location chunkLocation = new Location(world, cx * 16.0, 64.0, cz * 16.0);
                        Scheduler.runLocationTask(plugin, chunkLocation, () -> {
                            if (world.isChunkLoaded(cx, cz)) {
                                processChunk(world.getChunkAt(cx, cz));
                            }
                        });
                    }
                }
            });
        }
    }
}
