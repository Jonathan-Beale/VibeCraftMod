package com.example.vibecraftmod.ui;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Generic bar overlay widget (e.g., health, progress).
 */
public class BarOverlayWidget implements OverlayWidget {
    private final JsonObject config;
    public BarOverlayWidget(JsonObject config) {
        this.config = config;
    }
    @Override
    public void render(DrawContext context, MinecraftClient client, OverlayDef def, Object data) {
        int x = def.position.x;
        int y = def.position.y;
        int w = Math.max(1, def.size.width);
        int h = Math.max(1, def.size.height);
        int bg = parseColor("background", 0x66000000);
        int fg = parseColor("foreground", 0xFF55FF55);
        double pct = resolvePercent(data);
        int filled = (int) Math.round(w * Math.max(0.0, Math.min(1.0, pct)));
        context.fill(x, y, x + w, y + h, bg);
        context.fill(x, y, x + filled, y + h, fg);
    }

    private double resolvePercent(Object data) {
        if (data instanceof Number n) {
            double v = n.doubleValue();
            return v > 1.0 ? v / 100.0 : v;
        }
        if (config != null && config.has("percent")) {
            try {
                double v = config.get("percent").getAsDouble();
                return v > 1.0 ? v / 100.0 : v;
            } catch (Exception ignored) {}
        }
        return 0.0;
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
