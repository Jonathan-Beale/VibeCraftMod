package com.example.vibecraftmod.settings;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaudeSettings {
    private ClaudeSettings() {}

    private static final Map<String, String> values = new ConcurrentHashMap<>();
    private static Path settingsFile;

    public static void init(Path configDir) {
        settingsFile = configDir.resolve("vibecraftmod-settings.json");
        loadFromFile();
    }

    private static void loadFromFile() {
        if (settingsFile == null || !Files.exists(settingsFile)) return;
        try {
            JsonObject obj = JsonParser.parseString(Files.readString(settingsFile)).getAsJsonObject();
            for (var entry : obj.entrySet()) {
                if (DEFAULTS.containsKey(entry.getKey()))
                    values.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception ignored) {}
    }

    private static void saveToFile() {
        if (settingsFile == null) return;
        try {
            JsonObject obj = new JsonObject();
            for (var entry : values.entrySet()) obj.addProperty(entry.getKey(), entry.getValue());
            Files.writeString(settingsFile, obj.toString());
        } catch (Exception ignored) {}
    }

    public static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("chat.user_messages",  "true");
        DEFAULTS.put("chat.claude_text",    "true");
        DEFAULTS.put("chat.tools",          "false");
        DEFAULTS.put("chat.bash",           "false");
        DEFAULTS.put("chat.thinking",       "false");
        DEFAULTS.put("hud.lines",           "1");
        DEFAULTS.put("ui.thoughts_visible", "false");
        DEFAULTS.put("ui.color_scheme",     "terminal");
        DEFAULTS.put("color.user",     "55FF55");
        DEFAULTS.put("color.claude",   "55FFFF");
        DEFAULTS.put("color.tool",     "FFAA00");
        DEFAULTS.put("color.output",   "888888");
        DEFAULTS.put("color.system",   "AAAAAA");
        DEFAULTS.put("color.question", "FFFF55");
    }

    public static void applyAll(Map<String, String> incoming) {
        for (var entry : incoming.entrySet()) {
            if (DEFAULTS.containsKey(entry.getKey())) {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        saveToFile();
    }

    public static void set(String key, String value) {
        if (!DEFAULTS.containsKey(key)) return;
        values.put(key, value);
        saveToFile();
    }

    public static String get(String key) {
        return values.getOrDefault(key, DEFAULTS.getOrDefault(key, ""));
    }

    public static boolean getBool(String key) {
        return Boolean.parseBoolean(values.getOrDefault(key, DEFAULTS.getOrDefault(key, "false")));
    }

    public static int getInt(String key) {
        try {
            return Integer.parseInt(values.getOrDefault(key, DEFAULTS.getOrDefault(key, "1")));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static int hudLines() {
        return Math.max(0, Math.min(10, getInt("hud.lines")));
    }
}
