package com.example.vibecraftmod.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Fallback widget for unknown overlay types.
 */
public class UnknownOverlayWidget implements OverlayWidget {
    private final String type;
    public UnknownOverlayWidget(String type) {
        this.type = type;
    }
    @Override
    public void render(DrawContext context, MinecraftClient client, OverlayDef def, Object data) {
        // Optionally render a warning or nothing
    }
}
