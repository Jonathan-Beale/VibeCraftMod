package com.example.vibecraftmod.ui;

import com.google.gson.JsonObject;

/**
 * Data model for a HUD overlay, parsed from schema overlays[].
 */
public final class OverlayDef {
    public final String id;
    public final String plugin;
    public final String type;
    public final Position position;
    public final Size size;
    public final Style style;
    public final String dataBinding;

    public OverlayDef(String id, String plugin, String type, Position position, Size size, Style style, String dataBinding) {
        this.id = id;
        this.plugin = plugin;
        this.type = type;
        this.position = position;
        this.size = size;
        this.style = style;
        this.dataBinding = dataBinding;
    }

    public static OverlayDef fromJson(JsonObject obj) {
        String id = obj.has("id") ? obj.get("id").getAsString() : "overlay";
        String plugin = obj.has("plugin") ? obj.get("plugin").getAsString() : "vibecraft";
        String type = obj.has("type") ? obj.get("type").getAsString() : "text";
        Position position = Position.fromJson(obj.has("position") && obj.get("position").isJsonObject()
            ? obj.getAsJsonObject("position") : new JsonObject());
        Size size = Size.fromJson(obj.has("size") && obj.get("size").isJsonObject()
            ? obj.getAsJsonObject("size") : new JsonObject());
        Style style = Style.fromJson(obj.has("style") && obj.get("style").isJsonObject()
            ? obj.getAsJsonObject("style") : new JsonObject());
        String dataBinding = obj.has("dataBinding") ? obj.get("dataBinding").getAsString() : null;
        return new OverlayDef(id, plugin, type, position, size, style, dataBinding);
    }

    public static class Position {
        public final int x, y;
        public final String anchor;
        public Position(int x, int y, String anchor) {
            this.x = x; this.y = y; this.anchor = anchor;
        }
        public static Position fromJson(JsonObject obj) {
            int x = obj.has("x") ? obj.get("x").getAsInt() : 0;
            int y = obj.has("y") ? obj.get("y").getAsInt() : 0;
            String anchor = obj.has("anchor") ? obj.get("anchor").getAsString() : "left";
            return new Position(x, y, anchor);
        }
    }
    public static class Size {
        public final int width, height;
        public Size(int width, int height) {
            this.width = width; this.height = height;
        }
        public static Size fromJson(JsonObject obj) {
            int width = obj.has("width") ? obj.get("width").getAsInt() : 0;
            int height = obj.has("height") ? obj.get("height").getAsInt() : 0;
            return new Size(width, height);
        }
    }
    public static class Style {
        public final String background, border;
        public Style(String background, String border) {
            this.background = background; this.border = border;
        }
        public static Style fromJson(JsonObject obj) {
            String background = obj.has("background") ? obj.get("background").getAsString() : null;
            String border = obj.has("border") ? obj.get("border").getAsString() : null;
            return new Style(background, border);
        }
    }
}
