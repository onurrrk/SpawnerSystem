package me.spawner.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DiscordWebhook {
    private final String url;
    private String content;
    private String username;
    private String avatarUrl;
    private final List<EmbedObject> embeds = new ArrayList<>();

    public DiscordWebhook(String url) {
        this.url = url;
    }

    public void setContent(String content) { this.content = content; }
    public void setUsername(String username) { this.username = username; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public void addEmbed(EmbedObject embed) { this.embeds.add(embed); }

    private JsonObject buildPayload() {
        JsonObject root = new JsonObject();
        if (content != null) root.addProperty("content", content);
        if (username != null) root.addProperty("username", username);
        if (avatarUrl != null) root.addProperty("avatar_url", avatarUrl);

        JsonObject allowedMentions = new JsonObject();
        allowedMentions.add("parse", new JsonArray());
        root.add("allowed_mentions", allowedMentions);

        if (!embeds.isEmpty()) {
            JsonArray array = new JsonArray();
            for (EmbedObject embed : embeds) {
                JsonObject o = new JsonObject();
                if (embed.getTitle() != null) o.addProperty("title", embed.getTitle());
                if (embed.getDescription() != null) o.addProperty("description", embed.getDescription());
                if (embed.getColor() != null) o.addProperty("color", embed.getColor());
                if (embed.getThumbnail() != null) {
                    JsonObject t = new JsonObject();
                    t.addProperty("url", embed.getThumbnail());
                    o.add("thumbnail", t);
                }
                if (embed.getFooter() != null) {
                    JsonObject f = new JsonObject();
                    f.addProperty("text", embed.getFooter());
                    o.add("footer", f);
                }
                if (!embed.getFields().isEmpty()) {
                    JsonArray fields = new JsonArray();
                    for (EmbedObject.Field field : embed.getFields()) {
                        JsonObject fo = new JsonObject();
                        fo.addProperty("name", field.getName());
                        fo.addProperty("value", field.getValue());
                        fo.addProperty("inline", field.isInline());
                        fields.add(fo);
                    }
                    o.add("fields", fields);
                }
                array.add(o);
            }
            root.add("embeds", array);
        }
        return root;
    }

    public void execute() throws Exception {
        if (this.content == null && this.embeds.isEmpty()) {
            throw new IllegalArgumentException("Set content or add at least one EmbedObject");
        }

        byte[] body = buildPayload().toString().getBytes(StandardCharsets.UTF_8);

        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(this.url).openConnection();
            try {
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "SpawnerSystem-Webhook");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(8000);
                connection.setDoOutput(true);

                try (OutputStream stream = connection.getOutputStream()) {
                    stream.write(body);
                    stream.flush();
                }

                int code = connection.getResponseCode();
                if (code >= 200 && code < 300) {
                    try (InputStream in = connection.getInputStream()) {
                        in.readAllBytes();
                    }
                    return;
                }

                String error = readError(connection);
                if (code == 429 && attempt == 0) {
                    long waitMs = 1000;
                    try {
                        double retry = JsonParser.parseString(error).getAsJsonObject().get("retry_after").getAsDouble();
                        waitMs = (long) Math.min(5000, Math.max(250, retry * 1000));
                    } catch (Exception ignored) {
                    }
                    Thread.sleep(waitMs);
                    continue;
                }
                throw new IOException("Discord returned HTTP " + code + (error.isEmpty() ? "" : ": " + error));
            } finally {
                connection.disconnect();
            }
        }
    }

    private String readError(HttpURLConnection connection) {
        try (InputStream err = connection.getErrorStream()) {
            if (err == null) return "";
            return new String(err.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public static class EmbedObject {
        private String title;
        private String description;
        private Integer color;
        private String thumbnail;
        private String footer;
        private final List<Field> fields = new ArrayList<>();

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public Integer getColor() { return color; }
        public String getThumbnail() { return thumbnail; }
        public String getFooter() { return footer; }
        public List<Field> getFields() { return fields; }

        public EmbedObject setTitle(String title) { this.title = title; return this; }
        public EmbedObject setDescription(String description) { this.description = description; return this; }
        public EmbedObject setColor(Integer color) { this.color = color; return this; }
        public EmbedObject setThumbnail(String thumbnail) { this.thumbnail = thumbnail; return this; }
        public EmbedObject setFooter(String footer) { this.footer = footer; return this; }
        public EmbedObject addField(String name, String value, boolean inline) {
            this.fields.add(new Field(name, value, inline));
            return this;
        }

        private static class Field {
            private final String name;
            private final String value;
            private final boolean inline;

            private Field(String name, String value, boolean inline) {
                this.name = name;
                this.value = value;
                this.inline = inline;
            }
            public String getName() { return name; }
            public String getValue() { return value; }
            public boolean isInline() { return inline; }
        }
    }
}
