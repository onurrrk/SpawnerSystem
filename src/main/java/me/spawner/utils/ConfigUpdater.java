package me.spawner.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ConfigUpdater {

    private static final String VERSION_KEY = "version";
    private static final String LEGACY_VERSION_KEY = "last-run-version";
    private static final String DEFAULT_VERSION = "0.0.0.0";
    private static boolean versionLocked;

    private ConfigUpdater() {
    }

    public static void update(JavaPlugin plugin) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) return;

        versionLocked = false;
        deleteLegacyVersionFile(dataFolder);

        File configFile = new File(dataFolder, "config.yml");
        String currentVersion = bundledVersion(plugin);
        String lastRunVersion = readLastRunVersion(configFile);

        boolean versionChanged = configFile.exists()
                && !Version.fromString(lastRunVersion).equals(Version.fromString(currentVersion));
        List<ConfigMigrator.Change> changes = Collections.emptyList();
        if (versionChanged) {
            if (BackupManager.backup(plugin, lastRunVersion)) {
                changes = ConfigMigrator.pendingChanges(plugin, Version.fromString(lastRunVersion));
            } else {
                versionLocked = true;
            }
        }

        updateFile(plugin, "config.yml", configFile, changes);
        updateFile(plugin, "discord.yml", new File(dataFolder, "discord.yml"), changes);
        if (versionChanged) {
            updateFile(plugin, "loot.yml", new File(dataFolder, "loot.yml"), changes);
        }
        updateFile(plugin, "spawner_items.yml", new File(dataFolder, "spawner_items.yml"), changes);

        File[] languageFiles = new File(dataFolder, "languages").listFiles((dir, name) -> name.endsWith(".yml"));
        if (languageFiles != null) {
            for (File languageFile : languageFiles) {
                updateFile(plugin, "languages/" + languageFile.getName(), languageFile, changes);
            }
        }
    }

    public static void markCurrentVersion(JavaPlugin plugin) {
        if (versionLocked) return;

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return;

        String replacement = VERSION_KEY + ": \"" + bundledVersion(plugin) + "\"";
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8));
            boolean changed = lines.removeIf(line -> line.startsWith(LEGACY_VERSION_KEY + ":"));
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(VERSION_KEY + ":")) {
                    found = true;
                    if (!lines.get(i).equals(replacement)) {
                        lines.set(i, replacement);
                        changed = true;
                    }
                    break;
                }
            }
            if (!found) {
                lines.add(0, replacement);
                changed = true;
            }
            if (changed) {
                Files.write(configFile.toPath(), lines, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not update " + VERSION_KEY + ": " + e.getMessage());
        }
    }

    public static String bundledVersion(JavaPlugin plugin) {
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in != null) {
                YamlConfiguration template = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                String version = template.getString(VERSION_KEY);
                if (version != null && !version.isBlank()) {
                    return version.trim();
                }
            }
        } catch (IOException ignored) {
        }
        return Version.pluginVersion(plugin);
    }

    private static String readLastRunVersion(File configFile) {
        if (!configFile.exists()) return DEFAULT_VERSION;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        String version = yaml.getString(VERSION_KEY);
        if (version == null || version.isBlank()) {
            version = yaml.getString(LEGACY_VERSION_KEY);
        }
        return version == null || version.isBlank() ? DEFAULT_VERSION : version.trim();
    }

    private static void deleteLegacyVersionFile(File dataFolder) {
        try {
            Files.deleteIfExists(new File(dataFolder, ".version").toPath());
        } catch (IOException ignored) {
        }
    }

    private static void updateFile(JavaPlugin plugin, String resourceName, File target, List<ConfigMigrator.Change> changes) {
        if (!target.exists()) return;

        try {
            List<String> templateLines;
            try (InputStream in = plugin.getResource(resourceName)) {
                if (in == null) return;
                templateLines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .lines().collect(Collectors.toList());
            }
            List<String> userLines = Files.readAllLines(target.toPath(), StandardCharsets.UTF_8);

            YamlConfiguration expectedYaml = new YamlConfiguration();
            YamlConfiguration templateYaml = new YamlConfiguration();
            try {
                expectedYaml.loadFromString(String.join("\n", userLines));
                templateYaml.loadFromString(String.join("\n", templateLines));
            } catch (Exception e) {
                plugin.getLogger().warning(target.getName() + " could not be checked and was left untouched: " + e.getMessage());
                return;
            }

            int migrated = ConfigMigrator.apply(plugin, resourceName, expectedYaml, changes);
            List<String> sourceLines = userLines;
            if (migrated > 0) {
                sourceLines = expectedYaml.saveToString().lines().collect(Collectors.toList());
            }

            List<String> merged = YamlTemplateMerger.merge(templateLines, sourceLines);
            if (merged.equals(userLines)) return;

            YamlConfiguration mergedYaml = new YamlConfiguration();
            try {
                mergedYaml.loadFromString(String.join("\n", merged));
            } catch (Exception e) {
                plugin.getLogger().warning(target.getName() + " could not be checked and was left untouched: " + e.getMessage());
                return;
            }

            for (String path : expectedYaml.getKeys(true)) {
                if (expectedYaml.isConfigurationSection(path) || expectedYaml.get(path) == null) continue;
                if (!Objects.equals(expectedYaml.get(path), mergedYaml.get(path))) {
                    plugin.getLogger().warning(target.getName() + " was left untouched because updating it would change \"" + path + "\".");
                    return;
                }
            }

            Set<String> userKeys = new HashSet<>(expectedYaml.getKeys(true));
            int added = 0;
            for (String path : templateYaml.getKeys(true)) {
                if (!templateYaml.isConfigurationSection(path) && !userKeys.contains(path)) {
                    added++;
                }
            }

            Files.write(target.toPath(), merged, StandardCharsets.UTF_8);

            List<String> details = new ArrayList<>();
            if (added > 0) details.add(added + " new key(s) added");
            if (migrated > 0) details.add(migrated + " migration(s) applied");
            plugin.getLogger().info("Updated " + target.getName() + (details.isEmpty() ? "" : " (" + String.join(", ", details) + ")"));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not update " + target.getName() + ": " + e.getMessage());
        }
    }
}
