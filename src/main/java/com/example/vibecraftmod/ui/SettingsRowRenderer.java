package com.example.vibecraftmod.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;

/**
 * Generic interface for rendering a settings row.
 * Each row type (toggle, int_range, keybind, color_scheme, color) implements this
 * to encapsulate its own render logic and click handling.
 */
public interface SettingsRowRenderer {
    /**
     * Render this row at the given position.
     * @return Actual height used
     */
    int render(DrawContext ctx, TextRenderer textRenderer, int x, int y, int width, int mouseX, int mouseY);

    /**
     * Handle click on this row.
     * @return true if click was handled
     */
    boolean handleClick(double mouseX, double mouseY, int x, int y, int width);

    /**
     * Get the height this row should occupy.
     */
    int getHeight();
}
