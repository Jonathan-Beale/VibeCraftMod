package com.example.vibecraftmod.ui;

import com.google.gson.JsonObject;

/**
 * Generic bar overlay widget (e.g., health, progress).
 */
public class BarOverlayWidget implements OverlayWidget {
    private final JsonObject config;
    public BarOverlayWidget(JsonObject config) {
        this.config = config;
    }
    @Override
    public void render(OverlayDef def, Object data /*, rendering context */) {
        // TODO: Render bar using config and data
    }
}
