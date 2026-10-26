package me.spawner.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class UpdateChecker implements Listener {

    private static final String VERSIONS_URL = "https://api.modrinth.com/v2/project/spawnersystem/version";
    private static final String DOWNLOAD_URL = "https://modrinth.com/plugin/spawnersystem";
    private static final int TIMEOUT_MS = 5000;
    private static final long INITIAL_DELAY_SECONDS = 15L;
    private static final long CHECK_INTERVAL_HOURS = 12L;

    private final JavaPlugin plugin;
    private final String currentVersion;
    private final ScheduledExecutorService executor;
    private volatile String latestVersion;
    private volatile String announcedVersion;
    private volatile boolean updateAvailable = false;
    private volatile boolean closed = false;

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = Version.pluginVersion(plugin);

        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "SpawnerSystem-UpdateChecker");
            thread.setDaemon(true);
            return thread;
        });

        Bukkit.getPluginManager().registerEvents(this, plugin);
        executor.scheduleWithFixedDelay(this::check, INITIAL_DELAY_SECONDS, CHECK_INTERVAL_HOURS * 3600L, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (closed) return;
        closed = true;
        executor.shutdownNow();
        HandlerList.unregisterAll(this);
    }

    private void check() {
        if (closed) return;
        try {
            if (fetchUpdate() && !latestVersion.equals(announcedVersion)) {
                announcedVersion = latestVersion;
                announce();
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean fetchUpdate() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(VERSIONS_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "SpawnerSystem-UpdateChecker/" + currentVersion);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            if (connection.getResponseCode() != 200) {
                return false;
            }

            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                JsonArray versions = JsonParser.parseReader(reader).getAsJsonArray();
                if (versions.size() == 0) {
                    return false;
                }
                JsonObject newest = versions.get(0).getAsJsonObject();
                String latest = newest.get("version_number").getAsString();
                latestVersion = latest;
                updateAvailable = isNewerVersion(currentVersion, latest);
                return updateAvailable;
            }
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isNewerVersion(String current, String latest) {
        try {
            String[] currentParts = current.replaceAll("[^0-9.]", "").split("\\.");
            String[] latestParts = latest.replaceAll("[^0-9.]", "").split("\\.");
            int length = Math.max(currentParts.length, latestParts.length);
            for (int i = 0; i < length; i++) {
                int currentPart = i < currentParts.length && !currentParts[i].isEmpty() ? Integer.parseInt(currentParts[i]) : 0;
                int latestPart = i < latestParts.length && !latestParts[i].isEmpty() ? Integer.parseInt(latestParts[i]) : 0;
                if (latestPart > currentPart) return true;
                if (currentPart > latestPart) return false;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void announce() {
        sendUpdateMessage(Bukkit.getConsoleSender());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                sendUpdateMessage(player);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!updateAvailable || !event.getPlayer().isOp()) return;
        final Player player = event.getPlayer();
        Scheduler.runEntityTaskLater(plugin, player, () -> {
            if (player.isOnline()) {
                sendUpdateMessage(player);
            }
        }, 150L);
    }

    private void sendUpdateMessage(CommandSender sender) {
        String[] lines = {
                "&8&m--------------------------------------------------",
                "&b&lSPAWNER SYSTEM UPDATE",
                "&7A new version of the plugin is available!",
                "&cCurrent: " + currentVersion + " &8» &aNew: " + latestVersion,
                "&eDownload here: &a&n" + DOWNLOAD_URL,
                "&8&m--------------------------------------------------"
        };
        for (String line : lines) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }
    }
}
