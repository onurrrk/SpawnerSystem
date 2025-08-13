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

    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";

    private final File logsFolder;
    private final boolean logsEnabled;

    public JsonLogger(Spawner plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.logsFolder = new File(plugin.getDataFolder(), "logs");

       
        plugin.getConfig().addDefault("logs-enabled", true);
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        this.logsEnabled = plugin.getConfig().getBoolean("logs-enabled");

        if (!logsFolder.exists()) {
            if (!logsFolder.mkdirs()) {
                plugin.getLogger().severe(RED + "Logs folder could not be created! Please restart the server and try again. If the issue persists, contact the developer." + RESET);
            }
        }
    }

    public synchronized void log(Player player, Block block, String action) {
        if (!logsEnabled) return; 

        if (!(block.getState() instanceof CreatureSpawner spawnerState)) return;

        EntityType type = spawnerState.getSpawnedType();
        String spawnerType = (type != null) ? type.name() : "UNKNOWN";

        String world = block.getWorld().getName();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        List<LogEntry> logs = readLogsForToday();
        logs.add(new LogEntry(player.getName(), dateFormat.format(new Date()), action, spawnerType, world, x, y, z));
        writeLogsForToday(logs);
    }

    private File getTodayLogFile() {
        String fileName = fileDateFormat.format(new Date()) + ".json";
        return new File(logsFolder, fileName);
    }

    private List<LogEntry> readLogsForToday() {
        File logFile = getTodayLogFile();
        if (!logFile.exists()) return new ArrayList<>();

        try (FileReader reader = new FileReader(logFile)) {
            Type listType = new TypeToken<ArrayList<LogEntry>>() {}.getType();
            List<LogEntry> logs = gson.fromJson(reader, listType);
            return (logs != null) ? logs : new ArrayList<>();
        } catch (IOException e) {
            plugin.getLogger().severe(RED + "Could not read today's log file! Please restart the server and try again. If the issue persists, contact the developer." + RESET);
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private void writeLogsForToday(List<LogEntry> logs) {
        File logFile = getTodayLogFile();
        try (FileWriter writer = new FileWriter(logFile)) {
            gson.toJson(logs, writer);
        } catch (IOException e) {
            plugin.getLogger().severe(RED + "Could not write to today's log file! Please restart the server and try again. If the issue persists, contact the developer." + RESET);
            e.printStackTrace();
        }
    }

    private static class LogEntry {
        String player;
        String date;
        String action;
        String spawnerType;
        String world;
        int x, y, z;

        public LogEntry(String player, String date, String action, String spawnerType, String world, int x, int y, int z) {
            this.player = player;
            this.date = date;
            this.action = action;
            this.spawnerType = spawnerType;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
