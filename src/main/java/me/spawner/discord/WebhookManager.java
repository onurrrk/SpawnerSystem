package me.spawner.discord;

import me.spawner.Spawner;
import org.bukkit.Bukkit;
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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class WebhookManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private boolean enabled;
    private String webhookUrl;
    private String avatarApi;
    private String serverName;

    public WebhookManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "discord.yml");
        
        if (!file.exists()) {
            plugin.saveResource("discord.yml", false);
        }
        
        config = YamlConfiguration.loadConfiguration(file);
        
        enabled = config.getBoolean("webhook.enabled", false);
        webhookUrl = config.getString("webhook.url", "");
        avatarApi = config.getString("webhook.avatar-api", "https://mc-heads.net/avatar/%uuid%/128");
        serverName = config.getString("webhook.server-name", "SpawnerSystem");
    }

    public void sendPlaceWebhook(Player player, Location loc, String spawnerType) {
        sendWebhook("place", player, loc, spawnerType, null);
    }

    public void sendBreakWebhook(Player player, Location loc, String spawnerType, ItemStack itemUsed) {
        sendWebhook("break", player, loc, spawnerType, itemUsed);
    }

    private void sendWebhook(String eventType, Player player, Location loc, String spawnerType, ItemStack itemUsed) {
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.contains("WEBHOOK_URL")) {
            return;
        }

        ConfigurationSection section = config.getConfigurationSection("events." + eventType);
        if (section == null || !section.getBoolean("enabled", false)) {
            return;
        }

        final String pName = player.getName();
        final String pUuid = player.getUniqueId().toString();
        final String pPing = String.valueOf(player.getPing());
        final String pTps = String.format(Locale.US, "%.2f", Bukkit.getServer().getTPS()[0]);
        final String pClient = getClientType(player);
        final String pWorld = loc.getWorld().getName();
        final String pX = String.valueOf(loc.getBlockX());
        final String pY = String.valueOf(loc.getBlockY());
        final String pZ = String.valueOf(loc.getBlockZ());
        final String pGm = player.getGameMode().name();
        
        final String sType = getTranslatedType(spawnerType);

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
        
        final String finalIType = itemType;
        final String finalIName = itemName;

        CompletableFuture.runAsync(() -> {
            try {
                DiscordWebhook webhook = new DiscordWebhook(webhookUrl);
                webhook.setUsername(serverName);

                String titleText = section.getString("title", "");
                String descText = section.getString("description", "");
                
                String title = replacePlaceholders(titleText, pName, pUuid, pPing, pTps, pClient, pWorld, pX, pY, pZ, pGm, sType, finalIType, finalIName);
                String desc = replacePlaceholders(descText, pName, pUuid, pPing, pTps, pClient, pWorld, pX, pY, pZ, pGm, sType, finalIType, finalIName);
                
                DiscordWebhook.EmbedObject embed = new DiscordWebhook.EmbedObject()
                        .setTitle(title)
                        .setDescription(desc)
                        .setColor(section.getInt("color", 0))
                        .setThumbnail(avatarApi.replace("%uuid%", pUuid).replace("%player%", pName))
                        .setFooter("Tarih: " + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));

                if (section.contains("fields")) {
                    for (Map<?, ?> field : section.getMapList("fields")) {
                        String name = replacePlaceholders(String.valueOf(field.get("name")), pName, pUuid, pPing, pTps, pClient, pWorld, pX, pY, pZ, pGm, sType, finalIType, finalIName);
                        String value = replacePlaceholders(String.valueOf(field.get("value")), pName, pUuid, pPing, pTps, pClient, pWorld, pX, pY, pZ, pGm, sType, finalIType, finalIName);
                        
                        boolean inline = true;
                        if (field.get("inline") instanceof Boolean) {
                            inline = (Boolean) field.get("inline");
                        }
                        
                        embed.addField(name, value, inline);
                    }
                }

                webhook.addEmbed(embed);
                webhook.execute();

            } catch (Exception e) {
                plugin.getLogger().warning("Webhook could not be sent: " + e.getMessage());
            }
        });
    }

    private String getTranslatedType(String type) {
        if (type == null || type.equals("UNKNOWN") || type.equals("BOŞ")) {
            if (plugin instanceof Spawner) {
                return ((Spawner) plugin).getLanguageConfig().getString("entity-types.EMPTY", "UNKNOWN");
            }
            return "UNKNOWN";
        }

        if (plugin instanceof Spawner) {
            String translated = ((Spawner) plugin).getLanguageConfig().getString("entity-types." + type.toUpperCase());
            if (translated != null && !translated.isEmpty()) {
                return translated;
            }
        }

        String lower = type.toLowerCase();
        return lower.substring(0, 1).toUpperCase() + lower.substring(1);
    }

    private String getClientType(Player player) {
        try {
            Class<?> floodgateApi = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object apiInstance = floodgateApi.getMethod("getInstance").invoke(null);
            boolean isBedrock = (boolean) floodgateApi.getMethod("isFloodgatePlayer", java.util.UUID.class).invoke(apiInstance, player.getUniqueId());
            if (isBedrock) {
                return "Bedrock";
            }
        } catch (Exception ignored) {}

        try {
            Class<?> geyserApi = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object apiInstance = geyserApi.getMethod("api").invoke(null);
            boolean isBedrock = (boolean) geyserApi.getMethod("isBedrockPlayer", java.util.UUID.class).invoke(apiInstance, player.getUniqueId());
            if (isBedrock) {
                return "Bedrock";
            }
        } catch (Exception ignored) {}

        return "Java";
    }

    private String replacePlaceholders(String text, String pName, String pUuid, String pPing, String pTps, String pClient, String pWorld, String pX, String pY, String pZ, String pGm, String sType, String iType, String iName) {
        if (text == null || text.equals("null")) {
            return "";
        }
        
        String result = text;
        
        result = result.replace("%player%", pName);
        result = result.replace("%uuid%", pUuid);
        result = result.replace("%x%", pX);
        result = result.replace("%y%", pY);
        result = result.replace("%z%", pZ);
        result = result.replace("%world%", pWorld);
        result = result.replace("%type%", sType);
        result = result.replace("%item%", iType);
        result = result.replace("%item_name%", iName);
        result = result.replace("%ping%", pPing);
        result = result.replace("%tps%", pTps);
        result = result.replace("%client%", pClient);
        result = result.replace("%gamemode%", pGm);
        
        return result;
    }
}