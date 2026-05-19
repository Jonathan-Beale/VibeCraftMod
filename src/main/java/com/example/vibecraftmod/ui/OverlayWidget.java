package com.example.vibecraftmod.ui;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Base interface for all overlay widgets.
 */
public interface OverlayWidget {
    void render(DrawContext context, MinecraftClient client, OverlayDef def, Object data);

    /**
     * Registry for overlay widget types.
     */
    Map<String, OverlayWidgetFactory> REGISTRY = new HashMap<>();

    static void register(String type, OverlayWidgetFactory factory) {
        REGISTRY.put(type, factory);
    }

    static OverlayWidget create(String type, JsonObject config) {
        OverlayWidgetFactory factory = REGISTRY.get(type);
        if (factory != null) {
            return factory.create(config);
        }
        return new UnknownOverlayWidget(type);
    }

    interface OverlayWidgetFactory {
        OverlayWidget create(JsonObject config);
    }
}
