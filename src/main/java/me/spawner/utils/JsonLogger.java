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
    private final File logFile;
    private final Gson gson;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public JsonLogger(Spawner plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "sp.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        if (!logFile.exists()) {
            try {
                logFile.getParentFile().mkdirs();
                logFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("sp.json dosyası oluşturulamadı!");
                e.printStackTrace();
            }
        }
    }

    public synchronized void log(Player player, Block block, String action) {
        List<LogEntry> logs = readLogs();
        if (logs == null) logs = new ArrayList<>();

        if (block.getState() instanceof CreatureSpawner spawnerState) {

            EntityType type = spawnerState.getSpawnedType();
            String spawnerType = (type != null) ? type.name() : "UNKNOWN";

            String world = block.getWorld().getName();
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();

            logs.add(new LogEntry(player.getName(), dateFormat.format(new Date()), action, spawnerType, world, x, y, z));
            writeLogs(logs);
        }
    }

    private List<LogEntry> readLogs() {
        try (FileReader reader = new FileReader(logFile)) {
            Type listType = new TypeToken<ArrayList<LogEntry>>() {}.getType();
            List<LogEntry> logs = gson.fromJson(reader, listType);
            return (logs != null) ? logs : new ArrayList<>();
        } catch (IOException e) {
            plugin.getLogger().severe("sp.json dosyası okunamadı!");
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private void writeLogs(List<LogEntry> logs) {
        try (FileWriter writer = new FileWriter(logFile)) {
            gson.toJson(logs, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("sp.json dosyasına yazılamadı!");
            e.printStackTrace();
        }
    }

    private static class LogEntry {
        String oyuncu;
        String tarih;
        String eylem;
        String spawnerTuru;
        String dunya;
        int x, y, z;

        public LogEntry(String oyuncu, String tarih, String eylem, String spawnerTuru, String dunya, int x, int y, int z) {
            this.oyuncu = oyuncu;
            this.tarih = tarih;
            this.eylem = eylem;
            this.spawnerTuru = spawnerTuru;
            this.dunya = dunya;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}