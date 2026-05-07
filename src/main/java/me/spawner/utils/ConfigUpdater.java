package me.spawner.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ConfigUpdater {

    public static void update(JavaPlugin plugin) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) return;

        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) return;

        YamlConfiguration extConfig = YamlConfiguration.loadConfiguration(configFile);
        
        int intConfigVer = 1, intLangVer = 1, intDiscordVer = 1;
        InputStream defConfigStream = plugin.getResource("config.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
            intConfigVer = defConfig.getInt("config-version", 1);
            intLangVer = defConfig.getInt("language-version", 1);
            intDiscordVer = defConfig.getInt("discord-version", 1);
        }

        int extConfigVer = extConfig.getInt("config-version", -1);
        int extLangVer = extConfig.getInt("language-version", -1);
        int extDiscordVer = extConfig.getInt("discord-version", -1);

        boolean configNeedsSave = false;

        if (extLangVer != intLangVer) {
            File langFolder = new File(dataFolder, "languages");
            if (langFolder.exists()) {
                File legacyLangFolder = new File(dataFolder, "Legacy languages");
                try {
                    if (legacyLangFolder.exists()) deleteDirectory(legacyLangFolder);
                    Files.move(langFolder.toPath(), legacyLangFolder.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
            extConfig.set("language-version", intLangVer);
            configNeedsSave = true;
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
            extConfig.set("discord-version", intDiscordVer);
            configNeedsSave = true;
        }

        if (extConfigVer != intConfigVer) {
            File legacyConfig = new File(dataFolder, "Legacyconfig.yml");
            try {
                Files.move(configFile.toPath(), legacyConfig.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {}
            try {
                plugin.saveResource("config.yml", false);
            } catch (Exception ignored) {}
            configNeedsSave = false;
        }

        if (configNeedsSave) {
            try {
                extConfig.save(configFile);
            } catch (Exception ignored) {}
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