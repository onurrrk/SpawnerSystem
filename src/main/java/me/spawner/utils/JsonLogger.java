package me.spawner.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import me.spawner.Spawner;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class JsonLogger {

    private final Spawner plugin;
    private final Gson gson;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final File logsFolder;
    private final boolean logsEnabled;

    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";

    public JsonLogger(Spawner plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.logsFolder = new File(plugin.getDataFolder(), "logs");

        plugin.getConfig().addDefault("logs-enabled", true);
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        
        this.logsEnabled = plugin.getConfig().getBoolean("logs-enabled");

        if (!logsFolder.exists()) {
            boolean created = logsFolder.mkdirs();
            if (!created) {
                plugin.getLogger().severe(RED + "Logs folder could not be created! Check permissions." + RESET);
            }
        }
    }

    public synchronized void log(Player player, Block block, String action) {
        if (!logsEnabled) {
            return;
        }

        if (!(block.getState() instanceof CreatureSpawner)) {
            return;
        }

        CreatureSpawner spawnerState = (CreatureSpawner) block.getState();
        EntityType type = spawnerState.getSpawnedType();
        String spawnerType = (type != null) ? type.name() : "UNKNOWN";

        String world = block.getWorld().getName();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        String platform = getClientType(player);

        List<LogEntry> logs = readLogsForToday();
        
        LogEntry entry = new LogEntry(
                player.getName(),
                dateFormat.format(new Date()),
                platform,
                action,
                spawnerType,
                world,
                x,
                y,
                z
        );
        
        logs.add(entry);
        writeLogsForToday(logs);
    }

    private String getClientType(Player player) {
        try {
            Class<?> floodgateApi = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object apiInstance = floodgateApi.getMethod("getInstance").invoke(null);
            boolean isBedrock = (boolean) floodgateApi.getMethod("isFloodgatePlayer", java.util.UUID.class).invoke(apiInstance, player.getUniqueId());
            if (isBedrock) {
                return "Bedrock";
            }
        } catch (Exception e) {
        }

        try {
            Class<?> geyserApi = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object apiInstance = geyserApi.getMethod("api").invoke(null);
            boolean isBedrock = (boolean) geyserApi.getMethod("isBedrockPlayer", java.util.UUID.class).invoke(apiInstance, player.getUniqueId());
            if (isBedrock) {
                return "Bedrock";
            }
        } catch (Exception e) {
        }

        return "Java";
    }

    private File getTodayLogFile() {
        String fileName = fileDateFormat.format(new Date()) + ".json";
        return new File(logsFolder, fileName);
    }

    private List<LogEntry> readLogsForToday() {
        File logFile = getTodayLogFile();
        
        if (!logFile.exists()) {
            return new ArrayList<>();
        }

        try (FileReader reader = new FileReader(logFile)) {
            Type listType = new TypeToken<ArrayList<LogEntry>>() {}.getType();
            List<LogEntry> logs = gson.fromJson(reader, listType);
            
            if (logs != null) {
                return logs;
            } else {
                return new ArrayList<>();
            }
        } catch (IOException e) {
            plugin.getLogger().severe(RED + "Could not read today's log file!" + RESET);
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private void writeLogsForToday(List<LogEntry> logs) {
        File logFile = getTodayLogFile();
        
        try (FileWriter writer = new FileWriter(logFile)) {
            gson.toJson(logs, writer);
        } catch (IOException e) {
            plugin.getLogger().severe(RED + "Could not write to today's log file!" + RESET);
            e.printStackTrace();
        }
    }

    private static class LogEntry {
        String player;
        String date;
        String platform;
        String action;
        String spawnerType;
        String world;
        int x;
        int y;
        int z;

        public LogEntry(String player, String date, String platform, String action, String spawnerType, String world, int x, int y, int z) {
            this.player = player;
            this.date = date;
            this.platform = platform;
            this.action = action;
            this.spawnerType = spawnerType;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}