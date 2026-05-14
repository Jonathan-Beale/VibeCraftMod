package com.example.vibecraftmod.ui;

import com.example.vibecraftmod.DebugConfig;
import com.google.gson.*;
import java.util.*;

/** Deserializes config section from schema JSON (global or per-screen) */
public final class SchemaConfig {
    private static SchemaConfig globalInstance;
    private static ScreenDef[] screens = new ScreenDef[0];

    public final KeybindDef[] keybinds;
    public final ColorConfig colors;
    public final SettingsConfig settings;
    public final HelpConfig help;

    private SchemaConfig(JsonObject configObj) {
        this.keybinds = parseKeybinds(configObj != null ? configObj.getAsJsonArray("keybinds") : null);
        this.colors = new ColorConfig(configObj != null ? configObj.getAsJsonObject("colors") : null);
        this.settings = new SettingsConfig(configObj != null ? configObj.getAsJsonObject("settings") : null);
        this.help = new HelpConfig(configObj != null ? configObj.getAsJsonObject("help") : null);
    }

    /** Load from schema JSON object */
    public static synchronized SchemaConfig get() {
        if (globalInstance == null) globalInstance = loadFromSchema();
        return globalInstance;
    }

    /** Reload config from current UiSchemaStore */
    public static synchronized void reload() {
        globalInstance = loadFromSchema();
        loadScreens();
        if (DebugConfig.DEBUG_SCHEMA) {
            // Debug logging: print number of screens and widgets per screen
            ScreenDef[] screens = getScreens();
            System.out.println("[VibeCraftMod] SchemaConfig.reload: " + screens.length + " screens loaded");
            for (ScreenDef screen : screens) {
                int widgetCount = screen.widgets != null ? screen.widgets.size() : 0;
                System.out.println("[VibeCraftMod]   Screen '" + screen.id + "' (plugin: " + screen.plugin + ") has " + widgetCount + " widgets");
            }
        }
    }

    /** Get all registered screens */
    public static synchronized ScreenDef[] getScreens() {
        if (screens.length == 0) loadScreens();
        return screens;
    }

    /** Get a specific screen by ID */
    public static synchronized ScreenDef getScreen(String screenId) {
        for (ScreenDef screen : getScreens()) {
            if (screen.id.equals(screenId)) return screen;
        }
        return null;
    }

    /** Create a SchemaConfig from a JSON object (for per-screen configs) */
    public static SchemaConfig fromJson(JsonObject configObj) {
        return new SchemaConfig(configObj);
    }

    private static SchemaConfig loadFromSchema() {
        JsonObject schema = UiSchemaStore.getSchema();
        if (schema.has("config") && schema.get("config").isJsonObject()) {
            return new SchemaConfig(schema.getAsJsonObject("config"));
        }

        if (schema.has("screens") && schema.get("screens").isJsonArray()) {
            for (JsonElement e : schema.getAsJsonArray("screens")) {
                if (!e.isJsonObject()) continue;
                JsonObject screen = e.getAsJsonObject();
                if (screen.has("config") && screen.get("config").isJsonObject()) {
                    return new SchemaConfig(screen.getAsJsonObject("config"));
                }
            }
        }

        return new SchemaConfig(new JsonObject());
    }

    private static void loadScreens() {
        JsonObject schema = UiSchemaStore.getSchema();
        List<ScreenDef> screenList = new ArrayList<>();

        if (schema.has("screens") && schema.get("screens").isJsonArray()) {
            for (JsonElement e : schema.getAsJsonArray("screens")) {
                if (e.isJsonObject()) {
                    try {
                        screenList.add(ScreenDef.fromJson(e.getAsJsonObject()));
                    } catch (Exception ex) {
                        // Skip malformed screen defs
                    }
                }
            }
        }

        // Sort by priority (higher first)
        screenList.sort((a, b) -> Integer.compare(b.priority, a.priority));
        screens = screenList.toArray(new ScreenDef[0]);
    }

    private static KeybindDef[] parseKeybinds(JsonArray arr) {
        if (arr == null) return new KeybindDef[0];
        List<KeybindDef> list = new ArrayList<>();
        for (JsonElement e : arr) {
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                list.add(new KeybindDef(
                    o.get("id").getAsString(),
                    o.get("label").getAsString(),
                    o.get("defaultKey").getAsString(),
                    o.get("defaultMods").getAsInt(),
                    o.get("useMods").getAsBoolean()
                ));
            }
        }
        return list.toArray(new KeybindDef[0]);
    }

    public static class KeybindDef {
        public final String id;
        public final String label;
        public final String defaultKey;
        public final int defaultMods;
        public final boolean useMods;

        public KeybindDef(String id, String label, String defaultKey, int defaultMods, boolean useMods) {
            this.id = id;
            this.label = label;
            this.defaultKey = defaultKey;
            this.defaultMods = defaultMods;
            this.useMods = useMods;
        }
    }

    public static class ColorConfig {
        public final ColorSchemeDef[] schemes;
        public final ColorRoleDef[] roles;
        public final int paletteRows;
        public final int paletteCols;

        public ColorConfig(JsonObject colorsObj) {
            if (colorsObj == null) {
                this.schemes = new ColorSchemeDef[0];
                this.roles = new ColorRoleDef[0];
                this.paletteRows = 6;
                this.paletteCols = 21;
            } else {
                this.schemes = parseColorSchemes(colorsObj.getAsJsonArray("schemes"));
                this.roles = parseColorRoles(colorsObj.getAsJsonArray("roles"));
                JsonObject palette = colorsObj.getAsJsonObject("palette");
                this.paletteRows = palette != null ? palette.get("rows").getAsInt() : 6;
                this.paletteCols = palette != null ? palette.get("cols").getAsInt() : 21;
            }
        }

        private static ColorSchemeDef[] parseColorSchemes(JsonArray arr) {
            if (arr == null) return new ColorSchemeDef[0];
            List<ColorSchemeDef> list = new ArrayList<>();
            for (JsonElement e : arr) {
                if (e.isJsonObject()) {
                    JsonObject o = e.getAsJsonObject();
                    list.add(new ColorSchemeDef(
                        o.get("name").getAsString(),
                        o.get("label").getAsString(),
                        hex(o, "user", 0x55FF55),
                        hex(o, "claude", 0x55FFFF),
                        hex(o, "tool", 0xFFAA00),
                        hex(o, "output", 0x888888),
                        hex(o, "system", 0xAAAAAA),
                        hex(o, "question", 0xFFFF55)
                    ));
                }
            }
            return list.toArray(new ColorSchemeDef[0]);
        }

        private static ColorRoleDef[] parseColorRoles(JsonArray arr) {
            if (arr == null) return new ColorRoleDef[0];
            List<ColorRoleDef> list = new ArrayList<>();
            for (JsonElement e : arr) {
                if (e.isJsonObject()) {
                    JsonObject o = e.getAsJsonObject();
                    list.add(new ColorRoleDef(
                        o.get("key").getAsString(),
                        o.get("label").getAsString()
                    ));
                }
            }
            return list.toArray(new ColorRoleDef[0]);
        }

        private static int hex(JsonObject o, String key, int defaultVal) {
            if (o.has(key) && o.get(key).isJsonPrimitive()) {
                try {
                    return (int) Long.parseLong(o.get(key).getAsString(), 16);
                } catch (NumberFormatException ignored) {}
            }
            return defaultVal;
        }
    }

    public static class ColorSchemeDef {
        public final String name;
        public final String label;
        public final int user;
        public final int claude;
        public final int tool;
        public final int output;
        public final int system;
        public final int question;

        public ColorSchemeDef(String name, String label, int user, int claude, int tool, int output, int system, int question) {
            this.name = name;
            this.label = label;
            this.user = user;
            this.claude = claude;
            this.tool = tool;
            this.output = output;
            this.system = system;
            this.question = question;
        }
    }

    public static class ColorRoleDef {
        public final String key;
        public final String label;

        public ColorRoleDef(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    public static class SettingsConfig {
        public final SettingsTab[] tabs;

        public SettingsConfig(JsonObject settingsObj) {
            if (settingsObj == null || !settingsObj.has("tabs")) {
                this.tabs = new SettingsTab[0];
            } else {
                this.tabs = parseTabs(settingsObj.getAsJsonArray("tabs"));
            }
        }

        private static SettingsTab[] parseTabs(JsonArray arr) {
            if (arr == null) return new SettingsTab[0];
            List<SettingsTab> list = new ArrayList<>();
            for (JsonElement e : arr) {
                if (e.isJsonObject()) {
                    list.add(new SettingsTab(e.getAsJsonObject()));
                }
            }
            return list.toArray(new SettingsTab[0]);
        }
    }

    public static class SettingsTab {
        public final String name;
        public final SettingsRow[] rows;

        public SettingsTab(JsonObject tabObj) {
            this.name = tabObj.get("name").getAsString();
            this.rows = parseRows(tabObj.getAsJsonArray("rows"));
        }

        private static SettingsRow[] parseRows(JsonArray arr) {
            if (arr == null) return new SettingsRow[0];
            List<SettingsRow> list = new ArrayList<>();
            for (JsonElement e : arr) {
                if (e.isJsonObject()) {
                    JsonObject o = e.getAsJsonObject();
                    String type = o.get("type").getAsString();
                    list.add(switch (type) {
                        case "section" -> new SectionRow(o.get("title").getAsString());
                        case "toggle" -> new ToggleRow(
                            o.get("key").getAsString(),
                            o.get("label").getAsString()
                        );
                        case "int_range" -> new IntRangeRow(
                            o.get("key").getAsString(),
                            o.get("label").getAsString(),
                            o.get("min").getAsInt(),
                            o.get("max").getAsInt()
                        );
                        case "keybind" -> new KeybindRow(
                            o.get("id").getAsString(),
                            o.get("label").getAsString(),
                            o.get("useMods").getAsBoolean()
                        );
                        default -> null;
                    });
                }
            }
            return list.stream().filter(Objects::nonNull).toArray(SettingsRow[]::new);
        }
    }

    public sealed interface SettingsRow permits SectionRow, ToggleRow, IntRangeRow, KeybindRow {}
    public record SectionRow(String title) implements SettingsRow {}
    public record ToggleRow(String key, String label) implements SettingsRow {}
    public record IntRangeRow(String key, String label, int min, int max) implements SettingsRow {}
    public record KeybindRow(String id, String label, boolean useMods) implements SettingsRow {}

    public static class HelpConfig {
        public final String title;
        public final HelpSection[] sections;

        public HelpConfig(JsonObject helpObj) {
            if (helpObj == null) {
                this.title = "Help";
                this.sections = new HelpSection[0];
            } else {
                this.title = helpObj.get("title").getAsString();
                this.sections = parseSections(helpObj.getAsJsonArray("sections"));
            }
        }

        private static HelpSection[] parseSections(JsonArray arr) {
            if (arr == null) return new HelpSection[0];
            List<HelpSection> list = new ArrayList<>();
            for (JsonElement e : arr) {
                if (e.isJsonObject()) {
                    list.add(new HelpSection(e.getAsJsonObject()));
                }
            }
            return list.toArray(new HelpSection[0]);
        }
    }

    public static class HelpSection {
        public final String title;
        public final HelpItem[] items;

        public HelpSection(JsonObject sectionObj) {
            this.title = sectionObj.get("title").getAsString();
            this.items = parseItems(sectionObj.getAsJsonArray("items"));
        }

        private static HelpItem[] parseItems(JsonArray arr) {
            if (arr == null) return new HelpItem[0];
            List<HelpItem> list = new ArrayList<>();
            for (JsonElement e : arr) {
                if (e.isJsonObject()) {
                    JsonObject o = e.getAsJsonObject();
                    String keybindId = null;
                    if (o.has("keybind") && !o.get("keybind").isJsonNull()) {
                        keybindId = o.get("keybind").getAsString();
                    }
                    list.add(new HelpItem(keybindId, o.get("text").getAsString()));
                }
            }
            return list.toArray(new HelpItem[0]);
        }
    }

    public record HelpItem(String keybindId, String text) {}
}
