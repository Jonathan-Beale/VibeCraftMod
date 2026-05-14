package com.example.vibecraftmod.ui;

/**
 * Fallback widget for unknown overlay types.
 */
public class UnknownOverlayWidget implements OverlayWidget {
    private final String type;
    public UnknownOverlayWidget(String type) {
        this.type = type;
    }
    @Override
    public void render(OverlayDef def, Object data /*, rendering context */) {
        // Optionally render a warning or nothing
    }
}
