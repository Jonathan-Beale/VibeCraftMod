package com.example.vibecraftmod.settings;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ModSettings {
    private ModSettings() {}

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
                if (isKnownSetting(entry.getKey()))
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
        // Chat display toggles
        DEFAULTS.put("chat.user_messages",  "true");
        DEFAULTS.put("chat.claude_text",    "true");
        DEFAULTS.put("chat.tools",          "false");
        DEFAULTS.put("chat.bash",           "false");
        DEFAULTS.put("chat.thinking",       "false");
        
        // HUD display settings
        DEFAULTS.put("hud.lines",           "3");
        DEFAULTS.put("ui.thoughts_visible", "true");
        DEFAULTS.put("ui.color_scheme",     "terminal");
        
        // Color role defaults (actual role definitions come from server schema)
        // The server provides color role definitions in the schema; these are fallback defaults only
        DEFAULTS.put("color.user",     "55FF55");
        DEFAULTS.put("color.claude",   "55FFFF");
        DEFAULTS.put("color.tool",     "FFAA00");
        DEFAULTS.put("color.output",   "888888");
        DEFAULTS.put("color.system",   "AAAAAA");
        DEFAULTS.put("color.question", "FFFF55");
    }

    /**
     * Apply settings from schema (server-driven config).
     */
    public static void applySchemaSettings(Map<String, String> schemaSettings) {
        for (var entry : schemaSettings.entrySet()) {
            values.put(entry.getKey(), entry.getValue());
        }
        saveToFile();
    }

    public static void applyAll(Map<String, String> incoming) {
        for (var entry : incoming.entrySet()) {
            if (isKnownSetting(entry.getKey())) {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        saveToFile();
    }

    public static void applyAllForPlugin(String plugin, Map<String, String> incoming) {
        if (plugin == null || plugin.isBlank()) {
            applyAll(incoming);
            return;
        }
        for (var entry : incoming.entrySet()) {
            String key = entry.getKey();
            if (!isKnownSetting(key)) continue;
            values.put(scopedKey(plugin, key), entry.getValue());
            if ("vibecraft".equalsIgnoreCase(plugin)) {
                values.put(key, entry.getValue());
            }
        }
        saveToFile();
    }

    public static void set(String key, String value) {
        if (!isKnownSetting(key)) return;
        values.put(key, value);
        saveToFile();
    }

    public static void setForPlugin(String plugin, String key, String value) {
        if (!isKnownSetting(key)) return;
        if (plugin == null || plugin.isBlank()) {
            set(key, value);
            return;
        }
        String scoped = scopedKey(plugin, key);
        values.put(scoped, value);
        // Keep global values for vibecraft compatibility with existing consumers.
        if ("vibecraft".equalsIgnoreCase(plugin)) {
            values.put(key, value);
        }
        saveToFile();
    }

    private static boolean isKnownSetting(String key) {
        if (key == null || key.isBlank()) return false;
        String baseKey = baseKey(key);
        if (DEFAULTS.containsKey(baseKey)) return true;
        // Accept server/schema-driven extensible namespaces.
        return baseKey.startsWith("ui.") || baseKey.startsWith("color.") || baseKey.startsWith("chat.") || baseKey.startsWith("hud.");
    }

    public static String get(String key) {
        return values.getOrDefault(key, DEFAULTS.getOrDefault(key, ""));
    }

    public static String getForPlugin(String plugin, String key) {
        if (plugin == null || plugin.isBlank()) return get(key);
        String scoped = scopedKey(plugin, key);
        if (values.containsKey(scoped)) return values.get(scoped);

        // For non-vibecraft plugins, do not bleed vibecraft UI style values into their UI.
        if (!"vibecraft".equalsIgnoreCase(plugin)
                && (key.startsWith("ui.") || key.startsWith("color.") || key.startsWith("chat.") || key.startsWith("hud."))) {
            return DEFAULTS.getOrDefault(key, "");
        }
        return get(key);
    }

    public static boolean getBool(String key) {
        return Boolean.parseBoolean(values.getOrDefault(key, DEFAULTS.getOrDefault(key, "false")));
    }

    public static boolean getBoolForPlugin(String plugin, String key) {
        return Boolean.parseBoolean(getForPlugin(plugin, key));
    }

    public static int getInt(String key) {
        try {
            return Integer.parseInt(values.getOrDefault(key, DEFAULTS.getOrDefault(key, "1")));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static int getIntForPlugin(String plugin, String key) {
        try {
            String value = getForPlugin(plugin, key);
            if (value == null || value.isBlank()) value = DEFAULTS.getOrDefault(key, "1");
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static int hudLines() {
        return Math.max(0, Math.min(10, getInt("hud.lines")));
    }

    /**
     * Get color role definitions from server schema.
     * Returns an array of [key, label] pairs for all defined color roles.
     * Example: [["color.user", "User Text"], ["color.claude", "Claude Text"], ...]
     */
    public static String[][] getColorRoles() {
        return com.example.vibecraftmod.settings.ColorScheme.getRoles();
    }

    private static String scopedKey(String plugin, String key) {
        return plugin + ":" + key;
    }

    private static String baseKey(String key) {
        int idx = key.indexOf(':');
        if (idx >= 0 && idx + 1 < key.length()) return key.substring(idx + 1);
        return key;
    }
}
