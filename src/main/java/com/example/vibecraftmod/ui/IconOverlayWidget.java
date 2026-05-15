package com.example.vibecraftmod.ui;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Generic icon overlay widget (e.g., status, effect icons).
 */
public class IconOverlayWidget implements OverlayWidget {
    private final JsonObject config;
    public IconOverlayWidget(JsonObject config) {
        this.config = config;
    }
    @Override
    public void render(DrawContext context, MinecraftClient client, OverlayDef def, Object data) {
        if (client == null || client.textRenderer == null) return;
        String icon = config != null && config.has("icon") ? config.get("icon").getAsString() : "◆";
        if (data != null && !String.valueOf(data).isBlank()) {
            icon = String.valueOf(data);
        }
        int color = parseColor("color", 0xFFFFFFFF);
        context.drawText(client.textRenderer, Text.literal(icon), def.position.x, def.position.y, color, false);
    }

    private int parseColor(String key, int fallback) {
        if (config == null || !config.has(key)) return fallback;
        try {
            String raw = config.get(key).getAsString().replaceFirst("^#", "");
            return (int) (0xFF000000L | Long.parseLong(raw, 16));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
