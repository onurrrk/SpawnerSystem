package me.spawner.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

public final class PlatformUtils {
    private static boolean floodgateChecked = false;
    private static Object floodgateInstance;
    private static Method floodgateIsPlayer;

    private static boolean geyserChecked = false;
    private static Object geyserInstance;
    private static Method geyserIsPlayer;

    private static Method tpsMethod;
    private static boolean tpsChecked = false;
    private static boolean tpsUnsupported = false;

    private PlatformUtils() {
    }

    public static String clientType(Player player) {
        UUID uuid = player.getUniqueId();
        try {
            if (!floodgateChecked) {
                floodgateChecked = true;
                try {
                    Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                    floodgateInstance = api.getMethod("getInstance").invoke(null);
                    floodgateIsPlayer = api.getMethod("isFloodgatePlayer", UUID.class);
                } catch (Throwable ignored) {
                    floodgateInstance = null;
                }
            }
            if (floodgateInstance != null && (boolean) floodgateIsPlayer.invoke(floodgateInstance, uuid)) {
                return "Bedrock";
            }
        } catch (Throwable ignored) {
        }
        try {
            if (!geyserChecked) {
                geyserChecked = true;
                try {
                    Class<?> api = Class.forName("org.geysermc.geyser.api.GeyserApi");
                    geyserInstance = api.getMethod("api").invoke(null);
                    geyserIsPlayer = api.getMethod("isBedrockPlayer", UUID.class);
                } catch (Throwable ignored) {
                    geyserInstance = null;
                }
            }
            if (geyserInstance != null && (boolean) geyserIsPlayer.invoke(geyserInstance, uuid)) {
                return "Bedrock";
            }
        } catch (Throwable ignored) {
        }
        return "Java";
    }

    public static String tps() {
        if (tpsUnsupported) return "N/A";
        try {
            if (!tpsChecked) {
                tpsChecked = true;
                tpsMethod = Bukkit.getServer().getClass().getMethod("getTPS");
            }
            double[] tps = (double[]) tpsMethod.invoke(Bukkit.getServer());
            return String.format(Locale.US, "%.2f", Math.min(20.0, tps[0]));
        } catch (Throwable t) {
            tpsUnsupported = true;
            return "N/A";
        }
    }

    public static String ping(Player player) {
        try {
            return String.valueOf(player.getPing());
        } catch (Throwable t) {
            return "N/A";
        }
    }
}
