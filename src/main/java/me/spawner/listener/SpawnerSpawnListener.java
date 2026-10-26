package me.spawner.listener;

import me.spawner.Spawner;
import me.spawner.booster.BoosterManager;
import me.spawner.hologram.Hologram;
import me.spawner.spawner.ChunkMobCounter;
import me.spawner.spawner.MobNerf;
import me.spawner.utils.PDCUtils;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.persistence.PersistentDataType;

public final class SpawnerSpawnListener implements Listener {
    private final Spawner plugin;

    public SpawnerSpawnListener(Spawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        CreatureSpawner spawner = event.getSpawner();
        if (spawner == null) return;
        if (plugin.spawnerSettings().ignoresConditions(spawner)) return;

        if (plugin.items().itemSpawnerType(spawner) != null) {
            Hologram itemHologram = plugin.holograms().get(spawner.getLocation());
            if (itemHologram != null) {
                itemHologram.recordSuccessfulSpawn(spawner);
            }
            return;
        }

        EntityType type = spawner.getSpawnedType();
        if (type == null) return;

        if (plugin.settings().autoSlayerEnabled
                && (plugin.settings().menuV2
                || PDCUtils.getBoolean(spawner.getPersistentDataContainer(), plugin.keys().autoKill, false))) {
            Hologram autoHologram = plugin.holograms().get(spawner.getLocation());
            if (autoHologram != null) {
                autoHologram.recordSuccessfulSpawn(spawner);
            }
            return;
        }

        boolean perSpawner = plugin.settings().perSpawnerLimit;
        String ownerId = perSpawner ? ownerId(spawner) : null;
        ChunkMobCounter.ChunkCount chunkCount = perSpawner
                ? plugin.mobCounter().get(spawner.getChunk(), type, plugin.keys().spawnerOwner, ownerId)
                : plugin.mobCounter().get(spawner.getChunk(), type);
        if (chunkCount.count.get() >= plugin.settings().maxMobsPerChunk) {
            event.setCancelled(true);
            return;
        }
        chunkCount.count.incrementAndGet();

        PDCUtils.setBoolean(event.getEntity().getPersistentDataContainer(), plugin.keys().spawnerBorn, true);

        if (perSpawner) {
            event.getEntity().getPersistentDataContainer()
                    .set(plugin.keys().spawnerOwner, PersistentDataType.STRING, ownerId);
        }

        Hologram hologram = plugin.holograms().get(spawner.getLocation());
        if (hologram != null) {
            hologram.recordSuccessfulSpawn(spawner);
        }

        BoosterManager.Booster boost = plugin.boosters().activeIfEnabled(spawner);
        if (boost != null && boost.xpPercent() > 0 && event.getEntity() instanceof LivingEntity boostedMob) {
            boostedMob.getPersistentDataContainer()
                    .set(plugin.keys().mobXpBoost, PersistentDataType.INTEGER, boost.xpPercent());
        }

        if (plugin.settings().nerfMobs && event.getEntity() instanceof LivingEntity livingEntity) {
            PDCUtils.setBoolean(livingEntity.getPersistentDataContainer(), plugin.keys().nerfedMob, true);
            MobNerf.apply(livingEntity, type);
        }
    }

    private static String ownerId(CreatureSpawner spawner) {
        return spawner.getWorld().getName() + ":" + spawner.getX() + ":" + spawner.getY() + ":" + spawner.getZ();
    }
}
