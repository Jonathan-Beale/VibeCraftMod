package com.example.vibecraftmod.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;

/**
 * Manages HUD overlays loaded from schema, supports hot-reload.
 */
public final class OverlayManager {
    private static final List<OverlayDef> overlays = new ArrayList<>();
    private static final List<OverlayWidget> widgets = new ArrayList<>();

    public static void loadFromSchema(JsonObject schema) {
        overlays.clear();
        widgets.clear();
        if (schema.has("overlays") && schema.get("overlays").isJsonArray()) {
            JsonArray arr = schema.getAsJsonArray("overlays");
            for (JsonElement e : arr) {
                if (e.isJsonObject()) {
                    try {
                        OverlayDef def = OverlayDef.fromJson(e.getAsJsonObject());
                        overlays.add(def);
                        // Pass the full overlay object as config for widget instantiation
                        widgets.add(OverlayWidget.create(def.type, e.getAsJsonObject()));
                    } catch (Exception ex) {
                        // skip malformed overlay
                    }
                }
            }
        }
    }

    public static List<OverlayDef> getOverlays() {
        return overlays;
    }

    // Call this from the HUD render pass
    public static void renderAll(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.options.hudHidden) return;
        String activePlugin = ScreenManager.getActivePlugin();
        for (int i = 0; i < overlays.size(); i++) {
            OverlayDef def = overlays.get(i);
            if (def.plugin != null && !def.plugin.isBlank()
                    && activePlugin != null && !activePlugin.isBlank()
                    && !def.plugin.equalsIgnoreCase(activePlugin)) {
                continue;
            }
            OverlayWidget widget = widgets.get(i);
            Object data = OverlayDataBinding.resolve(def.dataBinding);
            widget.render(context, client, def, data);
        }
    }

    // Call this on schema reload
    public static void reload() {
        loadFromSchema(UiSchemaStore.getSchema());
    }
}
