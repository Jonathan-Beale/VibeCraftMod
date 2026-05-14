package com.example.vibecraftmod.ui;

import com.google.gson.JsonObject;

/**
 * Generic slot-based overlay widget (e.g., armor, inventory, hotbar).
 */
public class SlotOverlayWidget implements OverlayWidget {
    private final JsonObject config;
    public SlotOverlayWidget(JsonObject config) {
        this.config = config;
    }
    @Override
    public void render(OverlayDef def, Object data /*, rendering context */) {
        // TODO: Render slots using config and data
    }
}
