package me.spawner.utils;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ChunkUtils {

    private static Method liveTileEntities;
    private static boolean liveChecked = false;

    private ChunkUtils() {
    }

    public static List<CreatureSpawner> spawners(Chunk chunk) {
        List<CreatureSpawner> result = new ArrayList<>();
        for (BlockState live : tileEntitiesWithoutSnapshot(chunk)) {
            if (live instanceof CreatureSpawner) {
                BlockState snapshot = live.getBlock().getState();
                if (snapshot instanceof CreatureSpawner spawner) {
                    result.add(spawner);
                }
            }
        }
        return result;
    }

    public static int countStacked(Chunk chunk, Block excluded, NamespacedKey stackKey,
                                   java.util.function.Predicate<CreatureSpawner> filter) {
        long total = 0;
        for (BlockState live : tileEntitiesWithoutSnapshot(chunk)) {
            if (!(live instanceof CreatureSpawner spawner)) continue;
            if (excluded != null && live.getX() == excluded.getX() && live.getY() == excluded.getY()
                    && live.getZ() == excluded.getZ()) continue;
            if (filter != null && !filter.test(spawner)) continue;
            Integer size = spawner.getPersistentDataContainer().get(stackKey, PersistentDataType.INTEGER);
            total += size == null ? 1 : Math.max(1, size);
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static BlockState[] tileEntitiesWithoutSnapshot(Chunk chunk) {
        if (!liveChecked) {
            liveChecked = true;
            try {
                liveTileEntities = Chunk.class.getMethod("getTileEntities", boolean.class);
            } catch (Throwable ignored) {
                liveTileEntities = null;
            }
        }
        if (liveTileEntities != null) {
            try {
                return (BlockState[]) liveTileEntities.invoke(chunk, false);
            } catch (Throwable ignored) {
                liveTileEntities = null;
            }
        }
        return chunk.getTileEntities();
    }
}
