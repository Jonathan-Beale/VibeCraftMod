package com.example.vibecraftmod.hud;

import com.example.vibecraftmod.settings.ClaudeSettings;
import com.example.vibecraftmod.settings.ColorScheme;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;

public class ClaudeHudOverlay {

    private static final int PANEL_WIDTH    = 280;
    private static final int LINE_HEIGHT    = 10;
    private static final int PADDING        = 5;
    // MAX_VISIBLE is now dynamic — see ClaudeSettings.hudLines()
    private static final int TOP_MARGIN     = 4;
    private static final long FADE_DURATION = 5000; // ms after stream ends before fading
    private static final long FADE_TIME     = 2000; // ms fade duration

    public static void register() {
        HudRenderCallback.EVENT.register(ClaudeHudOverlay::render);
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!ClaudeHudState.isVisible()) return;

        boolean streaming = ClaudeHudState.isStreaming();
        long endTime = ClaudeHudState.getStreamEndTime();
        long now = System.currentTimeMillis();

        // Determine opacity: full while streaming or recently active, fade out after
        int alpha;
        if (streaming || endTime == 0) {
            alpha = 0xCC; // always visible while streaming or never shown yet
        } else {
            long elapsed = now - endTime;
            if (elapsed > FADE_DURATION + FADE_TIME) return; // fully faded
            if (elapsed > FADE_DURATION) {
                float t = (float)(elapsed - FADE_DURATION) / FADE_TIME;
                alpha = (int)(0xCC * (1f - t));
            } else {
                alpha = 0xCC;
            }
        }
        if (alpha <= 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        List<ClaudeHudState.HudLine> lines = ClaudeHudState.getLines();

        // Show last N lines per settings (0 = HUD fully disabled)
        int maxVisible = ClaudeSettings.hudLines();
        if (maxVisible == 0) return;
        int start = Math.max(0, lines.size() - maxVisible);
        List<ClaudeHudState.HudLine> visible = lines.subList(start, lines.size());

        // Add streaming indicator if active
        String toolIndicator = null;
        if (streaming) {
            String tool   = ClaudeHudState.getCurrentTool();
            String detail = ClaudeHudState.getCurrentToolDetail();
            toolIndicator = tool != null
                    ? "⏳ [" + tool + "] " + (detail != null ? detail : "")
                    : "⏳ Processing…";
        }

        int lineCount = visible.size() + (toolIndicator != null ? 1 : 0);
        if (lineCount == 0) return;

        int panelHeight = lineCount * LINE_HEIGHT + PADDING * 2;
        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();

        int x = screenW - PANEL_WIDTH - 4;
        int y = TOP_MARGIN;

        // Background
        int bg = (alpha << 24) | 0x000000;
        context.fill(x - PADDING, y - PADDING, x + PANEL_WIDTH + PADDING, y + panelHeight, bg);

        // Render lines
        int ty = y;
        for (ClaudeHudState.HudLine line : visible) {
            int color = (alpha << 24) | (line.color() & 0x00FFFFFF);
            String text = truncate(line.text(), PANEL_WIDTH, client);
            context.drawText(client.textRenderer, text, x, ty, color, false);
            ty += LINE_HEIGHT;
        }

        // Streaming indicator
        if (toolIndicator != null) {
            int toolColor = (alpha << 24) | (ColorScheme.get().tool() & 0x00FFFFFF);
            context.drawText(client.textRenderer,
                    truncate(toolIndicator, PANEL_WIDTH, client), x, ty, toolColor, false);
        }
    }

    private static String truncate(String text, int maxWidth, MinecraftClient client) {
        if (client.textRenderer.getWidth(text) <= maxWidth) return text;
        while (text.length() > 1 && client.textRenderer.getWidth(text + "…") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }
}
