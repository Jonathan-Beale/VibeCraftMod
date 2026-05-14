package com.example.vibecraftmod.ui;

import com.google.gson.JsonObject;

/**
 * Generic icon overlay widget (e.g., status, effect icons).
 */
public class IconOverlayWidget implements OverlayWidget {
    private final JsonObject config;
    public IconOverlayWidget(JsonObject config) {
        this.config = config;
    }
    @Override
    public void render(OverlayDef def, Object data /*, rendering context */) {
        // TODO: Render icon(s) using config and data
    }
}
