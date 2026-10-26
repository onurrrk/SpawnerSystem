package me.spawner.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class ConfigMigrator {

    public enum Type {
        MOVE,
        REMOVE,
        OVERWRITE,
        APPEND,
        REPLACE,
        RUNNABLE
    }

    public static final class Change {
        private final Version version;
        private final Type type;
        private final String file;
        private final String path;
        private final String key;
        private final Object value;

        private Change(Version version, Type type, String file, String path, String key, Object value) {
            this.version = version;
            this.type = type;
            this.file = file;
            this.path = path;
            this.key = key;
            this.value = value;
        }

        private String valueAsString() {
            return value == null ? null : String.valueOf(value);
        }

        @Override
        public String toString() {
            return "Change{version=" + version + ", type=" + type + ", file=" + file + ", path=" + path + ", key=" + key + ", value=" + value + "}";
        }
    }

    private static final String MIGRATION_FILE = "config-migration.json";
    private static final String DEFAULT_FILE = "config.yml";
    private static final Map<String, Consumer<YamlConfiguration>> RUNNABLES = new HashMap<>();

    private ConfigMigrator() {
    }

    public static void registerRunnable(String name, Consumer<YamlConfiguration> migration) {
        RUNNABLES.put(name.toLowerCase(Locale.ROOT), migration);
    }

    public static List<Change> pendingChanges(JavaPlugin plugin, Version lastRunVersion) {
        List<Change> result = new ArrayList<>();
        try (InputStream in = plugin.getResource(MIGRATION_FILE)) {
            if (in == null) {
                plugin.getLogger().warning("Could not find " + MIGRATION_FILE + " in the jar.");
                return result;
            }

            JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            if (!root.isJsonArray()) return result;

            for (JsonElement element : root.getAsJsonArray()) {
                JsonObject migration = element.getAsJsonObject();
                Version version = Version.fromString(text(migration, "version"));
                if (lastRunVersion.compareTo(version) >= 0) continue;

                JsonElement changes = migration.get("changes");
                if (changes == null || !changes.isJsonArray()) continue;

                for (JsonElement changeElement : changes.getAsJsonArray()) {
                    Change change = readChange(plugin, version, changeElement.getAsJsonObject());
                    if (change != null) result.add(change);
                }
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Could not read " + MIGRATION_FILE + ": " + e.getMessage());
            result.clear();
        }
        return result;
    }

    public static int apply(JavaPlugin plugin, String resourceName, YamlConfiguration yaml, List<Change> changes) {
        int applied = 0;
        for (Change change : changes) {
            if (!fileMatches(change.file, resourceName)) continue;
            try {
                if (perform(plugin, yaml, change)) applied++;
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Migration failed for " + change + ": " + e.getMessage());
            }
        }
        return applied;
    }

    private static Change readChange(JavaPlugin plugin, Version version, JsonObject object) {
        String typeName = text(object, "type");
        Type type = parseType(typeName);
        if (type == null) {
            plugin.getLogger().warning("Unknown migration type '" + typeName + "' for version " + version + ", skipped.");
            return null;
        }
        String file = text(object, "file");
        return new Change(
                version,
                type,
                file == null || file.isBlank() ? DEFAULT_FILE : file,
                text(object, "path"),
                text(object, "key"),
                toObject(object.get("value"))
        );
    }

    private static Type parseType(String name) {
        if (name == null) return null;
        try {
            return Type.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String text(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static Object toObject(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;

        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                list.add(toObject(item));
            }
            return list;
        }

        if (!element.isJsonPrimitive()) return null;

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isNumber()) {
            String number = primitive.getAsString();
            try {
                if (number.matches("-?\\d+")) {
                    long parsed = Long.parseLong(number);
                    if (parsed >= Integer.MIN_VALUE && parsed <= Integer.MAX_VALUE) return (int) parsed;
                    return parsed;
                }
                return Double.parseDouble(number);
            } catch (NumberFormatException e) {
                return number;
            }
        }
        return primitive.getAsString();
    }

    private static boolean fileMatches(String pattern, String name) {
        if (!pattern.contains("*")) return pattern.equals(name);
        String regex = Pattern.quote(pattern).replace("*", "\\E.*\\Q");
        return name.matches(regex);
    }

    private static boolean perform(JavaPlugin plugin, YamlConfiguration yaml, Change change) {
        return switch (change.type) {
            case MOVE -> move(yaml, change);
            case REMOVE -> remove(yaml, change.path);
            case OVERWRITE -> overwrite(yaml, change);
            case APPEND -> append(yaml, change);
            case REPLACE -> replace(yaml, change);
            case RUNNABLE -> runnable(plugin, yaml, change);
        };
    }

    private static boolean move(YamlConfiguration yaml, Change change) {
        String target = change.valueAsString();
        if (change.path == null || target == null || change.path.equals(target)) return false;

        Object old = yaml.get(change.path);
        if (old == null) return false;

        Map<String, Object> leaves = null;
        if (old instanceof ConfigurationSection section) {
            leaves = new LinkedHashMap<>();
            for (String key : section.getKeys(true)) {
                if (!section.isConfigurationSection(key)) {
                    leaves.put(key, section.get(key));
                }
            }
        }

        yaml.set(change.path, null);
        prune(yaml, change.path);

        if (leaves == null) {
            yaml.set(target, old);
        } else {
            for (Map.Entry<String, Object> entry : leaves.entrySet()) {
                yaml.set(target + "." + entry.getKey(), entry.getValue());
            }
        }
        return true;
    }

    private static boolean remove(YamlConfiguration yaml, String path) {
        if (path == null || yaml.get(path) == null) return false;
        yaml.set(path, null);
        prune(yaml, path);
        return true;
    }

    private static boolean overwrite(YamlConfiguration yaml, Change change) {
        if (change.path == null || yaml.get(change.path) == null) return false;
        yaml.set(change.path, change.value);
        return true;
    }

    private static boolean append(YamlConfiguration yaml, Change change) {
        if (change.path == null || change.value == null) return false;

        Object current = yaml.get(change.path);
        if (current instanceof String text) {
            String addition = change.valueAsString();
            if (text.endsWith(addition)) return false;
            yaml.set(change.path, text + addition);
            return true;
        }

        if (current instanceof List<?> list) {
            Collection<?> additions = change.value instanceof List<?> items ? items : Collections.singletonList(change.value);
            List<Object> updated = new ArrayList<>(list);
            boolean changed = false;
            for (Object item : additions) {
                if (!updated.contains(item)) {
                    updated.add(item);
                    changed = true;
                }
            }
            if (!changed) return false;
            yaml.set(change.path, updated);
            return true;
        }
        return false;
    }

    private static boolean replace(YamlConfiguration yaml, Change change) {
        if (change.path == null || change.key == null) return false;

        Object current = yaml.get(change.path);
        if (!(current instanceof String text)) return false;

        String replacement = change.valueAsString() == null ? "" : change.valueAsString();
        String replaced = text.replaceAll(change.key, replacement);
        if (replaced.equals(text)) return false;
        yaml.set(change.path, replaced);
        return true;
    }

    private static boolean runnable(JavaPlugin plugin, YamlConfiguration yaml, Change change) {
        Consumer<YamlConfiguration> migration = change.key == null ? null : RUNNABLES.get(change.key.toLowerCase(Locale.ROOT));
        if (migration == null) {
            plugin.getLogger().warning("Config migrator could not find runnable migration with key " + change.key);
            return false;
        }
        migration.accept(yaml);
        return true;
    }

    private static void prune(YamlConfiguration yaml, String path) {
        String current = path;
        int dot;
        while ((dot = current.lastIndexOf('.')) > 0) {
            current = current.substring(0, dot);
            ConfigurationSection parent = yaml.getConfigurationSection(current);
            if (parent == null || !parent.getKeys(false).isEmpty()) return;
            yaml.set(current, null);
        }
    }
}
