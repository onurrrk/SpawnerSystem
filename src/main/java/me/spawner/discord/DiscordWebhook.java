package me.spawner.discord;

import javax.net.ssl.HttpsURLConnection;
import java.io.OutputStream;
import java.net.URL;
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

    public void execute() throws Exception {
        if (this.content == null && this.embeds.isEmpty()) {
            throw new IllegalArgumentException("Set content or add at least one EmbedObject");
        }

        StringBuilder json = new StringBuilder();
        json.append("{");

        if (this.content != null) json.append("\"content\":\"").append(quote(this.content)).append("\",");
        if (this.username != null) json.append("\"username\":\"").append(quote(this.username)).append("\",");
        if (this.avatarUrl != null) json.append("\"avatar_url\":\"").append(quote(this.avatarUrl)).append("\",");

        if (!this.embeds.isEmpty()) {
            json.append("\"embeds\":[");
            for (EmbedObject embed : this.embeds) {
                json.append("{");
                if (embed.getTitle() != null) json.append("\"title\":\"").append(quote(embed.getTitle())).append("\",");
                if (embed.getDescription() != null) json.append("\"description\":\"").append(quote(embed.getDescription())).append("\",");
                if (embed.getColor() != null) json.append("\"color\":").append(embed.getColor()).append(",");
                if (embed.getThumbnail() != null) json.append("\"thumbnail\":{\"url\":\"").append(quote(embed.getThumbnail())).append("\"},");
                if (embed.getFooter() != null) json.append("\"footer\":{\"text\":\"").append(quote(embed.getFooter())).append("\"},");

                if (!embed.getFields().isEmpty()) {
                    json.append("\"fields\":[");
                    for (EmbedObject.Field field : embed.getFields()) {
                        json.append("{")
                            .append("\"name\":\"").append(quote(field.getName())).append("\",")
                            .append("\"value\":\"").append(quote(field.getValue())).append("\",")
                            .append("\"inline\":").append(field.isInline())
                            .append("},");
                    }
                    json.setLength(json.length() - 1);
                    json.append("]");
                } else {
                    if (json.charAt(json.length() - 1) == ',') json.setLength(json.length() - 1);
                }
                json.append("},");
            }
            json.setLength(json.length() - 1);
            json.append("]");
        } else {
            if (json.charAt(json.length() - 1) == ',') json.setLength(json.length() - 1);
        }
        json.append("}");

        URL url = new URL(this.url);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.addRequestProperty("Content-Type", "application/json");
        connection.addRequestProperty("User-Agent", "Java-DiscordWebhook");
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");

        try (OutputStream stream = connection.getOutputStream()) {
            stream.write(json.toString().getBytes("UTF-8"));
            stream.flush();
        }
        connection.getInputStream().close();
        connection.disconnect();
    }

    private String quote(String string) {
        return string.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
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