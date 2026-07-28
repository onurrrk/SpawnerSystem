package me.spawner.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class ConfigUpdater {

    public static void update(JavaPlugin plugin) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) return;

        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) return;

        YamlConfiguration extConfig = YamlConfiguration.loadConfiguration(configFile);

        int intConfigVer = 1, intLangVer = 1, intDiscordVer = 1, intLootVer = 1;
        InputStream defConfigStream = plugin.getResource("config.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
            intConfigVer = defConfig.getInt("config-version", 1);
            intLangVer = defConfig.getInt("language-version", 1);
            intDiscordVer = defConfig.getInt("discord-version", 1);
            intLootVer = defConfig.getInt("loot-version", 1);
        }

        int extConfigVer = extConfig.getInt("config-version", -1);
        int extLangVer = extConfig.getInt("language-version", -1);
        int extDiscordVer = extConfig.getInt("discord-version", -1);
        int extLootVer = extConfig.getInt("loot-version", -1);

        if (extLangVer != intLangVer) {
            File langFolder = new File(dataFolder, "languages");
            if (langFolder.exists()) {
                File legacyLangFolder = new File(dataFolder, "Legacy languages");
                try {
                    if (legacyLangFolder.exists()) deleteDirectory(legacyLangFolder);
                    Files.move(langFolder.toPath(), legacyLangFolder.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
            writeVersionLine(configFile, "config-version", "language-version", intLangVer);
        }

        if (extDiscordVer != intDiscordVer) {
            File discordFile = new File(dataFolder, "discord.yml");
            if (discordFile.exists()) {
                File legacyDiscord = new File(dataFolder, "Legacydiscord.yml");
                try {
                    Files.move(discordFile.toPath(), legacyDiscord.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
            try {
                plugin.saveResource("discord.yml", false);
            } catch (Exception ignored) {}
            writeVersionLine(configFile, "language-version", "discord-version", intDiscordVer);
        }

        if (extLootVer != intLootVer) {
            File lootFile = new File(dataFolder, "loot.yml");
            if (lootFile.exists()) {
                File legacyLoot = new File(dataFolder, "Legacyloot.yml");
                try {
                    Files.move(lootFile.toPath(), legacyLoot.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
            try {
                plugin.saveResource("loot.yml", false);
            } catch (Exception ignored) {}
            writeVersionLine(configFile, "discord-version", "loot-version", intLootVer);
        }

        if (extConfigVer != intConfigVer) {
            File legacyConfig = new File(dataFolder, "Legacyconfig.yml");
            try {
                Files.move(configFile.toPath(), legacyConfig.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {}
            try {
                plugin.saveResource("config.yml", false);
            } catch (Exception ignored) {}
        }
    }

    private static void writeVersionLine(File configFile, String afterKey, String key, int value) {
        try {
            List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            String newLine = key + ": " + value;

            int existingIndex = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().startsWith(key + ":")) {
                    existingIndex = i;
                    break;
                }
            }

            if (existingIndex != -1) {
                lines.set(existingIndex, newLine);
            } else {
                int afterIndex = -1;
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).trim().startsWith(afterKey + ":")) {
                        afterIndex = i;
                        break;
                    }
                }
                if (afterIndex != -1) {
                    lines.add(afterIndex + 1, newLine);
                } else {
                    lines.add(newLine);
                }
            }

            Files.write(configFile.toPath(), lines, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }
}
