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
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class JsonLogger {
    private final Spawner plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final SimpleDateFormat dateFormat;
    private final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final File logsFolder;
    private final boolean logsEnabled;

    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SpawnerSystem-Logger");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger pending = new AtomicInteger();

    private List<LogEntry> cache = null;
    private String cacheDay = null;

    public JsonLogger(Spawner plugin) {
        this.plugin = plugin;
        this.logsFolder = new File(plugin.getDataFolder(), "logs");
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd " + LogTimeFormat.timePattern(plugin.getConfig()));
        this.logsEnabled = plugin.getConfig().getBoolean("logs-enabled", true);

        if (logsEnabled && !logsFolder.exists() && !logsFolder.mkdirs()) {
            plugin.getLogger().severe("Logs folder could not be created! Check permissions.");
        }
    }

    public void log(Player player, Block block, String action) {
        if (!logsEnabled) return;
        if (!(block.getState() instanceof CreatureSpawner spawnerState)) return;

        EntityType type = spawnerState.getSpawnedType();
        String itemType = plugin.items().itemSpawnerType(spawnerState);
        final String spawnerType = itemType != null ? "ITEM:" + itemType : (type != null ? type.name() : "UNKNOWN");
        final String name = player.getName();
        final String platform = PlatformUtils.clientType(player);
        final String world = block.getWorld().getName();
        final int x = block.getX(), y = block.getY(), z = block.getZ();
        final Date now = new Date();

        pending.incrementAndGet();
        try {
            io.execute(() -> append(new LogEntry(name, null, platform, action, spawnerType, world, x, y, z), now));
        } catch (RejectedExecutionException e) {
            pending.decrementAndGet();
        }
    }

    private void append(LogEntry entry, Date now) {
        try {
            String day = fileDateFormat.format(now);
            entry.date = dateFormat.format(now);
            if (cache == null || !day.equals(cacheDay)) {
                cache = readLogs(day);
                cacheDay = day;
            }
            cache.add(entry);
        } catch (Exception e) {
            plugin.getLogger().warning("Log entry failed: " + e.getMessage());
        } finally {
            if (pending.decrementAndGet() == 0) {
                writeLogs();
            }
        }
    }

    private File fileFor(String day) {
        return new File(logsFolder, day + ".json");
    }

    private List<LogEntry> readLogs(String day) {
        File file = fileFor(day);
        if (!file.exists()) return new ArrayList<>();

        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<ArrayList<LogEntry>>() {}.getType();
            List<LogEntry> logs = gson.fromJson(reader, listType);
            return logs != null ? logs : new ArrayList<>();
        } catch (Exception e) {
            plugin.getLogger().severe("Could not read today's log file, backing it up: " + e.getMessage());
            File backup = new File(logsFolder, day + ".corrupt-" + System.currentTimeMillis() + ".json");

            file.renameTo(backup);
            return new ArrayList<>();
        }
    }

    private void writeLogs() {
        if (cache == null || cacheDay == null) return;
        Path target = fileFor(cacheDay).toPath();
        Path tmp = new File(logsFolder, cacheDay + ".json.tmp").toPath();
        try {
            if (!logsFolder.exists())
                logsFolder.mkdirs();
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                gson.toJson(cache, writer);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            plugin.getLogger().severe("Could not write to today's log file: " + e.getMessage());
        }
    }

    public void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(3, TimeUnit.SECONDS)) {
                io.shutdownNow();
            }
        } catch (InterruptedException e) {
            io.shutdownNow();
            Thread.currentThread().interrupt();
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

        LogEntry(String player, String date, String platform, String action, String spawnerType, String world, int x, int y, int z) {
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
