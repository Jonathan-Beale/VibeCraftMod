package com.example.vibecraftmod.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Definition of a single screen provided by a plugin.
 * Immutable data model for screen metadata and widgets.
 */
public final class ScreenDef {
    public final String id;           // "vibecraft:claude", "enchantforge:editor"
    public final String plugin;       // "vibecraft", "enchantforge"
    public final String title;
    public final int priority;        // higher = shown first in screen switcher
    public final SchemaConfig config; // keybinds, colors, settings for this screen
    public final JsonObject panel;
    public final JsonArray widgets;

    private ScreenDef(String id, String plugin, String title, int priority, SchemaConfig config, JsonObject panel, JsonArray widgets) {
        this.id = id;
        this.plugin = plugin;
        this.title = title;
        this.priority = priority;
        this.config = config;
        this.panel = panel;
        this.widgets = widgets;
    }

    /**
     * Parse a screen definition from JSON.
     * Expected structure:
     * {
     *   "id": "vibecraft:claude",
     *   "plugin": "vibecraft",
     *   "title": "Claude Chat",
     *   "priority": 100,
     *   "config": { ... },
     *   "widgets": [ ... ]
     * }
     */
    public static ScreenDef fromJson(JsonObject obj) {
        String id = obj.get("id").getAsString();
        String plugin = obj.get("plugin").getAsString();
        String title = obj.get("title").getAsString();
        int priority = obj.has("priority") ? obj.get("priority").getAsInt() : 0;
        
        SchemaConfig config = null;
        if (obj.has("config") && obj.get("config").isJsonObject()) {
            config = SchemaConfig.fromJson(obj.getAsJsonObject("config"));
        }
        
        JsonObject panel = obj.has("panel") && obj.get("panel").isJsonObject()
            ? obj.getAsJsonObject("panel")
            : new JsonObject();

        JsonArray widgets = obj.has("widgets") && obj.get("widgets").isJsonArray() 
            ? obj.getAsJsonArray("widgets") 
            : new JsonArray();

        return new ScreenDef(id, plugin, title, priority, config, panel, widgets);
    }

    @Override
    public String toString() {
        return "ScreenDef{" + id + " (" + plugin + ")}";
    }
}
