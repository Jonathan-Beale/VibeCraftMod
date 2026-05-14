package com.example.vibecraftmod.ui;

import com.google.gson.JsonObject;

/**
 * Generic text overlay widget.
 */
public class TextOverlayWidget implements OverlayWidget {
    private final JsonObject config;
    public TextOverlayWidget(JsonObject config) {
        this.config = config;
    }
    @Override
    public void render(OverlayDef def, Object data /*, rendering context */) {
        // TODO: Render text using config and data
    }
}
