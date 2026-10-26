package me.spawner.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class PDCUtils {
    private static final byte TRUE = (byte) 1;
    private static final byte FALSE = (byte) 0;

    private PDCUtils() {}

    public static void setBoolean(PersistentDataContainer pdc, NamespacedKey key, boolean value) {
        pdc.set(key, PersistentDataType.BYTE, value ? TRUE : FALSE);
    }

    public static boolean getBoolean(PersistentDataContainer pdc, NamespacedKey key, boolean defaultValue) {
        return pdc.getOrDefault(key, PersistentDataType.BYTE, defaultValue ? TRUE : FALSE) != FALSE;
    }

    public static boolean hasBoolean(PersistentDataContainer pdc, NamespacedKey key) {
        return pdc.has(key, PersistentDataType.BYTE);
    }
}
