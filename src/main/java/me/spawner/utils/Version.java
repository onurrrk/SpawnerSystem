package me.spawner.utils;

import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public final class Version implements Comparable<Version> {

    private final int[] parts;

    private Version(int[] parts) {
        this.parts = parts;
    }

    public static String pluginVersion(Plugin plugin) {
        try {
            String version = plugin.getDescription().getVersion();
            if (version != null) return version;
        } catch (Throwable ignored) {
        }
        try {
            Object meta = plugin.getClass().getMethod("getPluginMeta").invoke(plugin);
            Object version = Class.forName("io.papermc.paper.plugin.configuration.PluginMeta")
                    .getMethod("getVersion").invoke(meta);
            if (version != null) return version.toString();
        } catch (Throwable ignored) {
        }
        return "0.0.0.0";
    }

    public static Version fromString(String text) {
        if (text == null) return new Version(new int[]{0});

        String cleaned = text.trim();
        if (cleaned.startsWith("v") || cleaned.startsWith("V")) {
            cleaned = cleaned.substring(1);
        }

        int cut = cleaned.length();
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (!Character.isDigit(c) && c != '.') {
                cut = i;
                break;
            }
        }
        cleaned = cleaned.substring(0, cut);

        List<Integer> numbers = new ArrayList<>();
        for (String piece : cleaned.split("\\.")) {
            if (piece.isEmpty()) continue;
            try {
                numbers.add(Integer.parseInt(piece));
            } catch (NumberFormatException e) {
                numbers.add(0);
            }
        }
        if (numbers.isEmpty()) numbers.add(0);

        int[] result = new int[numbers.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = numbers.get(i);
        }
        return new Version(result);
    }

    @Override
    public int compareTo(Version other) {
        int length = Math.max(parts.length, other.parts.length);
        for (int i = 0; i < length; i++) {
            int a = i < parts.length ? parts[i] : 0;
            int b = i < other.parts.length ? other.parts[i] : 0;
            if (a != b) return Integer.compare(a, b);
        }
        return 0;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof Version other && compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        int end = parts.length;
        while (end > 1 && parts[end - 1] == 0) end--;
        int hash = 1;
        for (int i = 0; i < end; i++) {
            hash = 31 * hash + parts[i];
        }
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) builder.append('.');
            builder.append(parts[i]);
        }
        return builder.toString();
    }
}
