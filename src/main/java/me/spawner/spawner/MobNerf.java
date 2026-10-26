package me.spawner.spawner;

import me.spawner.utils.Compat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

public final class MobNerf {
    private MobNerf() {
    }

    public static void apply(LivingEntity entity, EntityType type) {
        entity.setCanPickupItems(false);
        Compat.setBaseAttribute(entity, "movement_speed", 0.0);
        Compat.setBaseAttribute(entity, "flying_speed", 0.0);
        if (type == EntityType.ZOMBIE_VILLAGER) {
            Compat.setBaseAttribute(entity, "attack_damage", 0.0);
        } else if (type != EntityType.VILLAGER) {
            Compat.setBaseAttribute(entity, "follow_range", 0.0);
        }
    }
}
