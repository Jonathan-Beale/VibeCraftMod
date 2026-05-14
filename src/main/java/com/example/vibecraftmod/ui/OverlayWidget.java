package com.example.vibecraftmod.ui;

import com.google.gson.JsonObject;

/**
 * Base interface for all overlay widgets.
 */
public interface OverlayWidget {
    void render(OverlayDef def, Object data /*, rendering context */);

    /**
     * Factory for creating overlay widgets by type.
     */
    static OverlayWidget create(String type, JsonObject config) {
        switch (type) {
            case "armor_slots":
            case "slots":
                return new SlotOverlayWidget(config);
            case "bar":
                return new BarOverlayWidget(config);
            case "icon":
                return new IconOverlayWidget(config);
            case "text":
                return new TextOverlayWidget(config);
            default:
                return new UnknownOverlayWidget(type);
        }
    }
}
