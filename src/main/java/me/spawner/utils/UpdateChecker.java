package me.spawner.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class UpdateChecker implements Listener {

    private final JavaPlugin plugin;
    private final String currentVersion;
    private String latestVersion = null;
    private boolean updateAvailable = false;
    private final ScheduledExecutorService scheduler;

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });

        Bukkit.getPluginManager().registerEvents(this, plugin);

        this.scheduler.execute(() -> {
            if (fetchUpdate()) {
                notifyOpsAndConsole();
                this.scheduler.schedule(() -> {
                    if (updateAvailable) {
                        sendConsoleUpdate(Bukkit.getConsoleSender());
                    }
                }, 5, TimeUnit.MINUTES);
            }
        });

        this.scheduler.scheduleAtFixedRate(() -> {
            if (fetchUpdate()) {
                notifyOpsAndConsole();
            }
        }, 12, 12, TimeUnit.HOURS);
    }

    private boolean fetchUpdate() {
        try {
            URL url = new URL("https://api.modrinth.com/v2/project/spawnersystem/version");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "SpawnerSystem-UpdateChecker");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() == 200) {
                InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                JsonArray jsonArray = new JsonParser().parse(reader).getAsJsonArray();
                
                if (jsonArray.size() > 0) {
                    JsonObject latestRelease = jsonArray.get(0).getAsJsonObject();
                    latestVersion = latestRelease.get("version_number").getAsString();
                    
                    updateAvailable = isNewerVersion(currentVersion, latestVersion);
                    reader.close();
                    return updateAvailable;
                }
                reader.close();
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isNewerVersion(String current, String latest) {
        try {
            current = current.replaceAll("[^0-9.]", "");
            latest = latest.replaceAll("[^0-9.]", "");
            String[] currArr = current.split("\\.");
            String[] latArr = latest.split("\\.");
            int length = Math.max(currArr.length, latArr.length);
            for (int i = 0; i < length; i++) {
                int currPart = i < currArr.length ? Integer.parseInt(currArr[i]) : 0;
                int latPart = i < latArr.length ? Integer.parseInt(latArr[i]) : 0;
                if (latPart > currPart) return true;
                if (currPart > latPart) return false;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void notifyOpsAndConsole() {
        if (!updateAvailable) return;
        sendConsoleUpdate(Bukkit.getConsoleSender());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                sendPlayerUpdate(player);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (updateAvailable && event.getPlayer().isOp()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (event.getPlayer().isOnline()) {
                    sendPlayerUpdate(event.getPlayer());
                }
            }, 150L);
        }
    }

    private void sendConsoleUpdate(CommandSender sender) {
        String[] lines = {
            "&8&m--------------------------------------------------",
            "&b&lSPAWNER SYSTEM UPDATE",
            "&7A new version of the plugin is available!",
            "&cCurrent: " + currentVersion + " &8» &aNew: " + latestVersion,
            "&eDownload here: &a&nhttps://modrinth.com/plugin/spawnersystem",
            "&8&m--------------------------------------------------"
        };
        for (String line : lines) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }
    }

    private void sendPlayerUpdate(CommandSender sender) {
        String msg = "&8&m--------------------------------------------------\n" +
                     "&b&lSPAWNER SYSTEM UPDATE\n" +
                     "&7A new version of the plugin is available!\n" +
                     "&cCurrent: " + currentVersion + " &8» &aNew: " + latestVersion + "\n" +
                     "&eDownload here: &a&nhttps://modrinth.com/plugin/spawnersystem\n" +
                     "&8&m--------------------------------------------------";
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }
}