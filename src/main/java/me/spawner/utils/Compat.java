package me.spawner.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Compat {
    private static final Object NONE = new Object();
    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>();

    private Compat() {
    }

    private static Object lookup(String registryField, String... keys) {
        Object registry;
        try {
            registry = Registry.class.getField(registryField).get(null);
        } catch (Throwable t) {
            return null;
        }
        if (!(registry instanceof Registry<?> reg)) return null;
        for (String key : keys) {
            try {
                Object value = reg.get(NamespacedKey.minecraft(key));
                if (value != null) return value;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static Enchantment enchantment(String key) {
        Object cached = CACHE.get("ench:" + key);
        if (cached != null) return cached == NONE ? null : (Enchantment) cached;

        Enchantment result = null;
        Object o = lookup("ENCHANTMENT", key);
        if (o instanceof Enchantment e) {
            result = e;
        } else {
            try {
                result = Enchantment.getByKey(NamespacedKey.minecraft(key));
            } catch (Throwable ignored) {
            }
        }
        CACHE.put("ench:" + key, result == null ? NONE : result);
        return result;
    }

    public static Attribute attribute(String name) {
        Object cached = CACHE.get("attr:" + name);
        if (cached != null) return cached == NONE ? null : (Attribute) cached;

        Attribute result = null;
        Object o = lookup("ATTRIBUTE", name, "generic." + name);
        if (o instanceof Attribute a) {
            result = a;
        } else {
            try {
                result = Attribute.valueOf("GENERIC_" + name.toUpperCase(Locale.ROOT));
            } catch (Throwable ignored) {
            }
        }
        CACHE.put("attr:" + name, result == null ? NONE : result);
        return result;
    }

    public static void setBaseAttribute(LivingEntity entity, String name, double value) {
        try {
            Attribute attribute = attribute(name);
            if (attribute == null) return;
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) instance.setBaseValue(value);
        } catch (Throwable ignored) {
        }
    }

    private static final String[][] ENTITY_ALIASES = {
            {"MOOSHROOM", "MUSHROOM_COW"},
            {"SNOW_GOLEM", "SNOWMAN"},
            {"ITEM", "DROPPED_ITEM"}
    };

    public static EntityType entityType(String name) {
        if (name == null) return null;
        String upper = name.trim().toUpperCase(Locale.ROOT);
        EntityType direct = parseEntityType(upper);
        if (direct != null) return direct;

        String alias = entityAlias(upper);
        return alias != null ? parseEntityType(alias) : null;
    }

    public static String entityAlias(String name) {
        if (name == null) return null;
        String upper = name.toUpperCase(Locale.ROOT);
        for (String[] pair : ENTITY_ALIASES) {
            if (pair[0].equals(upper)) return pair[1];
            if (pair[1].equals(upper)) return pair[0];
        }
        return null;
    }

    private static EntityType parseEntityType(String upper) {
        try {
            return EntityType.valueOf(upper);
        } catch (Throwable t) {
            return null;
        }
    }

    public static PotionType potionType(String name) {
        if (name == null) return null;
        String upper = name.trim().toUpperCase(Locale.ROOT);
        Object o = lookup("POTION", upper.toLowerCase(Locale.ROOT));
        if (o instanceof PotionType p) return p;
        try {
            return PotionType.valueOf(upper);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void applyBasePotion(PotionMeta meta, PotionType type) {
        if (meta == null || type == null) return;
        try {
            Method m = PotionMeta.class.getMethod("setBasePotionType", PotionType.class);
            m.invoke(meta, type);
            return;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            return;
        }
        try {
            meta.setBasePotionData(new PotionData(type, false, false));
        } catch (Throwable ignored) {
        }
    }
}
