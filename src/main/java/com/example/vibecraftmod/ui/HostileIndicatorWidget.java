package com.example.vibecraftmod.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Renders a pulsing directional arrow at the left or right screen edge when a
 * hostile mob is behind the player.  Position is always screen-relative — the
 * overlay's configured x/y are ignored.
 *
 * Expected binding data: {"side":"left"|"right","timestamp":<epochMs>}
 * A null or stale value (older than TOTAL_MS) renders nothing.
 */
public class HostileIndicatorWidget implements OverlayWidget {

    private static final long SOLID_MS = 2500L;
    private static final long FADE_MS  =  500L;
    private static final long TOTAL_MS = SOLID_MS + FADE_MS;

    @Override
    public void render(DrawContext context, MinecraftClient client, OverlayDef def, Object data) {
        if (!(data instanceof JsonElement je) || !je.isJsonObject()) return;
        JsonObject obj = je.getAsJsonObject();
        if (!obj.has("side") || !obj.has("timestamp")) return;

        long age = System.currentTimeMillis() - obj.get("timestamp").getAsLong();
        if (age > TOTAL_MS || age < 0) return;

        float opacity = age > SOLID_MS ? 1f - (float)(age - SOLID_MS) / FADE_MS : 1f;
        int alpha = Math.max(0, Math.min(255, (int)(opacity * 255)));
        if (alpha <= 0) return;

        // Pulsing brightness: slow sine wave between 0xCC and 0xFF
        float pulse = (float)((Math.sin(System.currentTimeMillis() / 180.0) + 1.0) * 0.5);
        int r = (int)(0xCC + pulse * 0x33);
        int color = (alpha << 24) | (r << 16) | 0x1111;

        String side = obj.get("side").getAsString();
        int screenW  = context.getScaledWindowWidth();
        int screenH  = context.getScaledWindowHeight();
        int midY     = screenH / 2 - 4;

        if ("left".equals(side)) {
            context.drawText(client.textRenderer, "◄◄◄", 4, midY, color, true);
        } else if ("right".equals(side)) {
            String text = "►►►";
            int tw = client.textRenderer.getWidth(text);
            context.drawText(client.textRenderer, text, screenW - tw - 4, midY, color, true);
        }
    }
}
