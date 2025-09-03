package me.spawner;

import org.bukkit.Bukkit;

public class ServerVersion {

    public static final int CURRENT_VERSION;
    public static final boolean IS_POST_1_20_R2;
    public static final boolean IS_PDC_BOOLEAN_AVAILABLE;
    public static final boolean IS_1_17_OR_HIGHER; 

    static {
        String[] parts = Bukkit.getBukkitVersion().split("-")[0].split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

        CURRENT_VERSION = major * 10000 + minor * 100 + patch;

        IS_POST_1_20_R2 = CURRENT_VERSION >= 12002;
        IS_PDC_BOOLEAN_AVAILABLE = CURRENT_VERSION >= 12005;
        IS_1_17_OR_HIGHER = CURRENT_VERSION >= 11700;
    }
}