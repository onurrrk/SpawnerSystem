package me.spawner.config;

import me.spawner.utils.ColorUtils;
import me.spawner.utils.Compat;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class LanguageManager {

    private static final String[] BUNDLED_LANGUAGES = {
            "en", "tr", "de", "es", "ru", "zh", "ja", "az", "fr", "ar", "nl", "id", "hy", "it",
            "gd", "sv", "ky", "ko", "hu", "cs", "el", "fa", "pl", "ro", "vi", "pt", "th", "uk"
    };

    private static final String RESET = "\u00a7r";

    private final JavaPlugin plugin;
    private volatile FileConfiguration config;

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        saveBundledLanguages();
        loadSelectedLanguage();
    }

    public FileConfiguration config() {
        return config;
    }

    private void saveBundledLanguages() {
        File folder = new File(plugin.getDataFolder(), "languages");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        for (String lang : BUNDLED_LANGUAGES) {
            File file = new File(folder, lang + ".yml");
            if (!file.exists() && plugin.getResource("languages/" + lang + ".yml") != null) {
                plugin.saveResource("languages/" + lang + ".yml", false);
            }
        }
    }

    private void loadSelectedLanguage() {
        String lang = plugin.getConfig().getString("language", "en");
        File folder = new File(plugin.getDataFolder(), "languages");
        File file = new File(folder, lang + ".yml");
        if (!file.exists()) {
            file = new File(folder, "en.yml");
        }

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("languages/en.yml")) {
            if (in != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
        }
        config = loaded;
    }

    public String withPrefix(String body) {
        String prefix = ColorUtils.color(config.getString("prefix", "&8[&#38BDF8Spawner&#7DD3FCSystem&8] &r"));
        String trimmed = prefix.replaceAll("(?:\\s|\\u00a7r)+$", "");
        return trimmed + " " + RESET + body;
    }

    public String message(String path) {
        String raw = config.getString("messages." + path);
        return withPrefix(ColorUtils.color(raw != null ? raw : "&cMessage not found: " + path));
    }

    public List<String> messageLines(String path) {
        String key = "messages." + path;
        List<String> raw = config.isList(key) ? config.getStringList(key) : null;
        if (raw == null || raw.isEmpty()) {
            return Collections.singletonList(message(path));
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String line = ColorUtils.color(raw.get(i));
            lines.add(i == 0 ? withPrefix(line) : line);
        }
        return lines;
    }

    public List<String> list(String path) {
        return config.getStringList(path).stream()
                .map(ColorUtils::color)
                .collect(Collectors.toList());
    }

    public String entityName(EntityType type) {
        return entityName(config, type);
    }

    public String itemName(String material) {
        return itemName(config, material);
    }

    public static String entityName(FileConfiguration cfg, EntityType type) {
        if (type == null) {
            return cfg.getString("entity-types.EMPTY", "Empty");
        }
        String translated = cfg.getString("entity-types." + type.name());
        if (translated == null) {
            String alias = Compat.entityAlias(type.name());
            if (alias != null) {
                translated = cfg.getString("entity-types." + alias);
            }
        }
        return translated != null ? translated : prettyName(type.name());
    }

    public static String itemName(FileConfiguration cfg, String material) {
        String translated = cfg.getString("item-types." + material);
        return translated != null ? translated : prettyName(material);
    }

    public static String prettyName(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        StringBuilder builder = new StringBuilder();
        for (String word : raw.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }
}
