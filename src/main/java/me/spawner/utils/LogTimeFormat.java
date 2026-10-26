package me.spawner.utils;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.Locale;

public final class LogTimeFormat {

    private static final String PATTERN_24H = "HH:mm:ss";
    private static final String PATTERN_12H = "hh:mm:ss a";

    private LogTimeFormat() {
    }

    public static String timePattern(FileConfiguration config) {
        String mode = config.getString("log-time-format", "auto");
        String normalized = mode == null ? "auto" : mode.trim().toLowerCase(Locale.ROOT);

        if (normalized.equals("12h")) {
            return PATTERN_12H;
        }
        if (normalized.equals("24h")) {
            return PATTERN_24H;
        }
        return systemUses12Hour() ? PATTERN_12H : PATTERN_24H;
    }

    private static boolean systemUses12Hour() {
        try {
            String pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                    null, FormatStyle.MEDIUM, IsoChronology.INSTANCE, Locale.getDefault());
            return pattern.replaceAll("'[^']*'", "").contains("a");
        } catch (Exception e) {
            return false;
        }
    }
}
