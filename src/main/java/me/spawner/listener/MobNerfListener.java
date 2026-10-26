package me.spawner.listener;

import me.spawner.Spawner;
import me.spawner.spawner.MobNerf;
import me.spawner.utils.PDCUtils;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;

public final class MobNerfListener implements Listener {
    private final Spawner plugin;

    public MobNerfListener(Spawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityTransform(EntityTransformEvent event) {
        if (!plugin.settings().nerfMobs) return;
        if (!PDCUtils.hasBoolean(event.getEntity().getPersistentDataContainer(), plugin.keys().nerfedMob)) return;

        for (Entity transformed : event.getTransformedEntities()) {
            PDCUtils.setBoolean(transformed.getPersistentDataContainer(), plugin.keys().nerfedMob, true);

            if (transformed instanceof LivingEntity livingEntity) {
                MobNerf.apply(livingEntity, livingEntity.getType());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNerfedMobTarget(EntityTargetEvent event) {
        if (!plugin.settings().nerfMobs) return;
        if (!(event.getEntity() instanceof LivingEntity mob)) return;
        if (PDCUtils.hasBoolean(mob.getPersistentDataContainer(), plugin.keys().nerfedMob)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNerfedMobDamage(EntityDamageByEntityEvent event) {
        if (!plugin.settings().nerfMobs) return;
        if (!(event.getDamager() instanceof LivingEntity mob)) return;
        if (PDCUtils.hasBoolean(mob.getPersistentDataContainer(), plugin.keys().nerfedMob)) {
            event.setCancelled(true);
        }
    }
}
