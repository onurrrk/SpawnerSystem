package me.spawner.spawner;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class BreakChance {

    private static final String PREFIX = "spawnersystem.chance.";
    private static final String BYPASS_PERMISSION = "spawner.admin";

    private BreakChance() {
    }

    public static double resolve(Player player, double defaultChance) {
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return 100.0;
        }

        double best = -1.0;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;

            String node = info.getPermission().toLowerCase(Locale.ROOT);
            if (!node.startsWith(PREFIX)) continue;

            String suffix = node.substring(PREFIX.length());
            double value;
            if (suffix.equals("*")) {
                value = 100.0;
            } else {
                try {
                    value = Double.parseDouble(suffix);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (Double.isNaN(value)) continue;
            }
            best = Math.max(best, clamp(value));
        }
        return best >= 0 ? best : clamp(defaultChance);
    }

    public static boolean roll(double chance) {
        if (chance >= 100.0) return true;
        if (chance <= 0.0) return false;
        return ThreadLocalRandom.current().nextDouble() * 100.0 < chance;
    }

    public static String format(double chance) {
        if (chance == Math.floor(chance)) {
            return String.valueOf((int) chance);
        }
        return String.format(Locale.US, "%.1f", chance);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }
}
