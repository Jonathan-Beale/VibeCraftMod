package com.example.vibecraftmod.ui;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Generic text overlay widget.
 */
public class TextOverlayWidget implements OverlayWidget {
    private final JsonObject config;
    public TextOverlayWidget(JsonObject config) {
        this.config = config;
    }
    @Override
    public void render(DrawContext context, MinecraftClient client, OverlayDef def, Object data) {
        if (client == null || client.textRenderer == null) return;
        int x = def.position.x;
        int y = def.position.y;
        String text = resolveText(def, data);
        int color = parseColor(config, "color", 0xFFFFFFFF);
        context.drawText(client.textRenderer, Text.literal(text), x, y, color, false);
    }

    private String resolveText(OverlayDef def, Object data) {
        if (config != null && config.has("text")) return config.get("text").getAsString();
        if (data == null) return def.id;
        return String.valueOf(data);
    }

    private int parseColor(JsonObject obj, String key, int fallback) {
        if (obj == null || !obj.has(key)) return fallback;
        try {
            String raw = obj.get(key).getAsString().replaceFirst("^#", "");
            return (int) (0xFF000000L | Long.parseLong(raw, 16));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
