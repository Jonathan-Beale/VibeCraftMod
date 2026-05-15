package com.example.vibecraftmod.ui;

import net.minecraft.client.gui.DrawContext;
import com.google.gson.JsonObject;
import java.util.*;

/**
 * Generic interface for rendering a schema-defined widget.
 * All widget rendering logic delegates through this interface.
 */
public interface WidgetRenderer {
    /**
     * Render the widget at the given position.
     * @param ctx Draw context
     * @param widget Schema JsonObject for this widget
     * @param x Screen x coordinate
     * @param y Screen y coordinate
     * @param maxW Maximum width available
     * @param maxH Maximum height available
     * @param mouseX Current mouse X (-1 if not tracking)
     * @param mouseY Current mouse Y (-1 if not tracking)
     * @param clickTargets List to append clickable regions to
     * @return Actual height used
     */
    int render(DrawContext ctx, JsonObject widget, int x, int y, int maxW, int maxH,
               int mouseX, int mouseY, List<ClickTarget> clickTargets);

    /**
     * Get the height this widget would use given schema and available width.
     * Called before render to calculate layout.
     */
    int getHeight(JsonObject widget, int maxW);

    /** Registry of widget renderers by type */
    Map<String, WidgetRenderer> REGISTRY = new HashMap<>();

    static void register(String type, WidgetRenderer renderer) {
        REGISTRY.put(type, renderer);
    }

    static WidgetRenderer get(String type) {
        return REGISTRY.get(type);
    }
}
