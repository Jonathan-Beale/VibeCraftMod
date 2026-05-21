package com.example.vibecraftmod.hud;

import com.example.vibecraftmod.EntityHighlightManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Aerial radar disc in the bottom-right corner.
 * Rotates with the player (forward = up). Only visible when hostile entities are nearby.
 */
public class HostileRadarOverlay {

    private static final float MAX_RANGE    = 32.0f;
    private static final float RADIUS       = 48.0f;   // disc radius in scaled pixels
    private static final float MARGIN       = 10.0f;   // gap from screen edge
    private static final int   DISC_SEGMENTS = 48;

    public static void register() {
        HudRenderCallback.EVENT.register(HostileRadarOverlay::render);
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return;

        // Collect visible hostile entities within range
        var highlighted = EntityHighlightManager.getHighlighted();
        if (highlighted.isEmpty()) return;

        Vec3d playerPos = client.player.getPos();
        float yawRad = (float) Math.toRadians(client.player.getYaw());

        boolean anyInRange = false;
        for (var entry : highlighted.entrySet()) {
            if (!"red".equals(entry.getValue())) continue;
            Entity e = client.world.getEntityById(entry.getKey());
            if (e != null && client.player.distanceTo(e) <= MAX_RANGE) { anyInRange = true; break; }
        }
        if (!anyInRange) return;

        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();

        // Disc center: bottom-right corner with margin
        float cx = screenW - MARGIN - RADIUS;
        float cy = screenH - MARGIN - RADIUS;

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        // --- Background disc (TRIANGLE_STRIP: interleave perimeter+center; degenerate center-center pairs are no-ops) ---
        var buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= DISC_SEGMENTS; i++) {
            float angle = (float) (2 * Math.PI * i / DISC_SEGMENTS);
            buf.vertex(matrix, cx + (float) Math.cos(angle) * RADIUS, cy + (float) Math.sin(angle) * RADIUS, 0).color(0, 0, 0, 160);
            buf.vertex(matrix, cx, cy, 0).color(0, 0, 0, 160);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());

        // --- Disc border ---
        buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        float borderW = 1.5f;
        for (int i = 0; i <= DISC_SEGMENTS; i++) {
            float angle = (float) (2 * Math.PI * i / DISC_SEGMENTS);
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            buf.vertex(matrix, cx + cos * RADIUS,              cy + sin * RADIUS,              0).color(255, 255, 255, 60);
            buf.vertex(matrix, cx + cos * (RADIUS - borderW),  cy + sin * (RADIUS - borderW),  0).color(255, 255, 255, 60);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());

        // --- Forward direction tick (small notch at top of disc = player's facing) ---
        // In the rotated frame, "up" on the disc is always the player's forward direction.
        // Draw a bright tick on the disc rim at the top (angle = -PI/2 in screen space).
        float tickAngle = -(float) Math.PI / 2.0f; // straight up
        float tickOuter = RADIUS - 1.0f;
        float tickInner = RADIUS - 7.0f;
        float tickHalf  = 2.5f;
        float tcos = (float) Math.cos(tickAngle), tsin = (float) Math.sin(tickAngle);
        float tpx = -tsin, tpy = tcos; // perpendicular
        buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        buf.vertex(matrix, cx + tcos * tickOuter - tpx * tickHalf, cy + tsin * tickOuter - tpy * tickHalf, 0).color(255, 255, 255, 200);
        buf.vertex(matrix, cx + tcos * tickOuter + tpx * tickHalf, cy + tsin * tickOuter + tpy * tickHalf, 0).color(255, 255, 255, 200);
        buf.vertex(matrix, cx + tcos * tickInner,                  cy + tsin * tickInner,                  0).color(255, 255, 255, 200);
        BufferRenderer.drawWithGlobalProgram(buf.end());

        // --- Player dot (center) ---
        drawDot(matrix, cx, cy, 3.0f, 255, 255, 255, 230);

        // --- Enemy dots ---
        for (var entry : highlighted.entrySet()) {
            if (!"red".equals(entry.getValue())) continue;
            Entity entity = client.world.getEntityById(entry.getKey());
            if (entity == null) continue;

            float dist = client.player.distanceTo(entity);
            if (dist > MAX_RANGE) continue;

            Vec3d ePos = entity.getPos();
            double worldDx = ePos.x - playerPos.x;
            double worldDz = ePos.z - playerPos.z;

            // Rotate world offset into player-forward-up frame.
            // forward = (-sin(yaw), cos(yaw)) in (X,Z); right = (cos(yaw), sin(yaw)) in (X,Z)
            // mapX = dot(delta, right)    = worldDx*cos + worldDz*sin  (positive = screen-right)
            // mapY = dot(delta, forward)  = -worldDx*sin + worldDz*cos (positive = screen-up → negate for screen-Y)
            float sinYaw = (float) Math.sin(yawRad);
            float cosYaw = (float) Math.cos(yawRad);
            float mapX = (float) (worldDx * cosYaw + worldDz * sinYaw);
            float mapY = (float) (worldDx * sinYaw - worldDz * cosYaw);

            // Scale: MAX_RANGE world units → RADIUS screen pixels, clamp inside disc
            float scale = RADIUS / MAX_RANGE;
            float dotX = cx + mapX * scale;
            float dotY = cy + mapY * scale;

            // Clamp dot to disc boundary
            float offX = dotX - cx, offY = dotY - cy;
            float offLen = (float) Math.sqrt(offX * offX + offY * offY);
            float maxOff = RADIUS - 4.0f;
            if (offLen > maxOff) {
                dotX = cx + offX / offLen * maxOff;
                dotY = cy + offY / offLen * maxOff;
            }

            // Larger dot for closer enemies
            float closeness = 1.0f - dist / MAX_RANGE;
            float dotR = 2.5f + closeness * 2.0f;
            drawDot(matrix, dotX, dotY, dotR, 255, 40, 40, 230);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawDot(Matrix4f matrix, float x, float y, float r, int red, int green, int blue, int alpha) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        int segments = 12;
        var buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float a = (float) (2 * Math.PI * i / segments);
            buf.vertex(matrix, x + (float) Math.cos(a) * r, y + (float) Math.sin(a) * r, 0).color(red, green, blue, alpha);
            buf.vertex(matrix, x, y, 0).color(red, green, blue, alpha);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
    }
}
