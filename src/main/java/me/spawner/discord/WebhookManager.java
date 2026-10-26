package me.spawner.discord;

import me.spawner.Spawner;
import me.spawner.utils.LogTimeFormat;
import me.spawner.utils.PlatformUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WebhookManager {
    private final JavaPlugin plugin;
    private volatile FileConfiguration config;
    private volatile boolean enabled;
    private volatile String webhookUrl;
    private volatile String avatarApi;
    private volatile String webhookName;

    private static final int MAX_QUEUED_WEBHOOKS = 100;

    private volatile long lastDropWarning = 0L;

    private final ExecutorService executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_QUEUED_WEBHOOKS),
            r -> {
                Thread t = new Thread(r, "SpawnerSystem-Webhook");
                t.setDaemon(true);
                return t;
            },
            (task, pool) -> warnDropped());

    public WebhookManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "discord.yml");
        if (!file.exists()) {
            plugin.saveResource("discord.yml", false);
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        config = cfg;

        enabled = cfg.getBoolean("webhook.enabled", false);
        webhookUrl = cfg.getString("webhook.url", "");
        avatarApi = cfg.getString("webhook.avatar-api", "https://mc-heads.net/avatar/%uuid%/128");

        webhookName = cfg.getString("webhook.webhook-name", cfg.getString("webhook.server-name", "SpawnerSystem"));
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void warnDropped() {
        long now = System.currentTimeMillis();
        if (now - lastDropWarning > 60_000L) {
            lastDropWarning = now;
            plugin.getLogger().warning("Discord webhook queue is full (Discord unreachable or rate limited); dropping webhook messages.");
        }
    }

    public void sendPlaceWebhook(Player player, Location loc, String spawnerType) {
        sendWebhook("place", player, loc, spawnerType, null);
    }

    public void sendBreakWebhook(Player player, Location loc, String spawnerType, ItemStack itemUsed) {
        sendWebhook("break", player, loc, spawnerType, itemUsed);
    }

    private void sendWebhook(String eventType, Player player, Location loc, String spawnerType, ItemStack itemUsed) {
        final FileConfiguration cfg = this.config;
        final String url = this.webhookUrl;
        if (!enabled || url == null || url.isEmpty() || url.contains("WEBHOOK_URL") || !url.startsWith("https://")) {
            return;
        }

        final ConfigurationSection section = cfg.getConfigurationSection("events." + eventType);
        if (section == null || !section.getBoolean("enabled", false)) {
            return;
        }

        final Map<String, String> vars = new HashMap<>();
        vars.put("%player%", player.getName());
        vars.put("%uuid%", player.getUniqueId().toString());
        vars.put("%ping%", PlatformUtils.ping(player));
        vars.put("%tps%", PlatformUtils.tps());
        vars.put("%client%", PlatformUtils.clientType(player));
        vars.put("%world%", loc.getWorld() != null ? loc.getWorld().getName() : "unknown");
        vars.put("%x%", String.valueOf(loc.getBlockX()));
        vars.put("%y%", String.valueOf(loc.getBlockY()));
        vars.put("%z%", String.valueOf(loc.getBlockZ()));
        vars.put("%gamemode%", player.getGameMode().name());
        vars.put("%type%", getTranslatedType(spawnerType));

        String itemType = "null";
        String itemName = "null";
        if (itemUsed != null && itemUsed.getType() != Material.AIR) {
            itemType = itemUsed.getType().name();
            if (itemUsed.hasItemMeta() && itemUsed.getItemMeta().hasDisplayName()) {
                itemName = ChatColor.stripColor(itemUsed.getItemMeta().getDisplayName());
            } else {
                itemName = itemType;
            }
        }
        vars.put("%item%", itemType);
        vars.put("%item_name%", itemName);

        final String timePattern = LogTimeFormat.timePattern(plugin.getConfig());
        final String avatar = avatarApi;
        final String name = webhookName;

        try {
            executor.execute(() -> {
                try {
                    DiscordWebhook webhook = new DiscordWebhook(url);
                    webhook.setUsername(name);

                    DiscordWebhook.EmbedObject embed = new DiscordWebhook.EmbedObject()
                            .setTitle(replace(section.getString("title", ""), vars))
                            .setDescription(replace(section.getString("description", ""), vars))
                            .setColor(section.getInt("color", 0))
                            .setThumbnail(avatar.replace("%uuid%", vars.get("%uuid%")).replace("%player%", vars.get("%player%")))
                            .setFooter("Date: " + new SimpleDateFormat("dd/MM/yyyy " + timePattern).format(new Date()));

                    if (section.contains("fields")) {
                        for (Map<?, ?> field : section.getMapList("fields")) {
                            boolean inline = !(field.get("inline") instanceof Boolean) || (Boolean) field.get("inline");
                            embed.addField(replace(String.valueOf(field.get("name")), vars),
                                    replace(String.valueOf(field.get("value")), vars), inline);
                        }
                    }

                    webhook.addEmbed(embed);
                    webhook.execute();
                } catch (Exception e) {
                    plugin.getLogger().warning("Webhook could not be sent: " + e.getMessage());
                }
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    private String getTranslatedType(String type) {
        FileConfiguration lang = plugin instanceof Spawner ? ((Spawner) plugin).getLanguageConfig() : null;

        if (type != null && type.startsWith("ITEM:") && lang != null) {
            return me.spawner.config.LanguageManager.itemName(lang, type.substring(5));
        }

        if (type == null || type.equals("UNKNOWN")) {
            return lang != null ? lang.getString("entity-types.EMPTY", "UNKNOWN") : "UNKNOWN";
        }

        if (lang != null) {
            org.bukkit.entity.EntityType entityType = me.spawner.utils.Compat.entityType(type);
            if (entityType != null) {
                return me.spawner.config.LanguageManager.entityName(lang, entityType);
            }
            String translated = lang.getString("entity-types." + type.toUpperCase(Locale.ROOT));
            if (translated != null && !translated.isEmpty()) {
                return translated;
            }
        }

        String lower = type.toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }

    private static String replace(String text, Map<String, String> vars) {
        if (text == null || text.equals("null")) {
            return "";
        }
        String result = text;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }
}
