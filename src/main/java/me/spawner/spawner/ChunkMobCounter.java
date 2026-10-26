package me.spawner.spawner;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChunkMobCounter {
    public static final class ChunkCount {
        public final AtomicInteger count;
        final long expires;

        ChunkCount(int count, long expires) {
            this.count = new AtomicInteger(count);
            this.expires = expires;
        }
    }

    private final Map<String, ChunkCount> cache = new ConcurrentHashMap<>();

    public ChunkCount get(Chunk chunk, EntityType type) {
        long now = System.currentTimeMillis();
        String key = chunk.getWorld().getName() + "," + chunk.getX() + "," + chunk.getZ() + "," + type.name();
        ChunkCount cached = cache.get(key);
        if (cached != null && cached.expires >= now) {
            return cached;
        }

        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity.getType() == type) {
                count++;
            }
        }
        return store(key, count, now);
    }

    public ChunkCount get(Chunk chunk, EntityType type, NamespacedKey ownerKey, String ownerId) {
        long now = System.currentTimeMillis();
        String key = chunk.getWorld().getName() + "," + chunk.getX() + "," + chunk.getZ() + "," + type.name() + ",owner:" + ownerId;
        ChunkCount cached = cache.get(key);
        if (cached != null && cached.expires >= now) {
            return cached;
        }

        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity.getType() == type
                    && ownerId.equals(entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING))) {
                count++;
            }
        }
        return store(key, count, now);
    }

    private ChunkCount store(String key, int count, long now) {
        if (cache.size() > 4096) {
            cache.clear();
        }
        ChunkCount fresh = new ChunkCount(count, now + 1000L);
        cache.put(key, fresh);
        return fresh;
    }

    public void clear() {
        cache.clear();
    }
}
