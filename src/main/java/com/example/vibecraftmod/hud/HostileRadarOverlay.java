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
 * Draws screen-edge arrows pointing toward nearby hostile (red-highlighted) entities.
 * Arrow size scales with proximity; entities beyond MAX_RANGE are suppressed.
 */
public class HostileRadarOverlay {

    private static final float MAX_RANGE   = 16.0f;
    private static final float EDGE_MARGIN = 26.0f;
    private static final float MIN_SIZE    =  8.0f;
    private static final float MAX_SIZE    = 22.0f;

    public static void register() {
        HudRenderCallback.EVENT.register(HostileRadarOverlay::render);
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return;
        if (EntityHighlightManager.getHighlighted().isEmpty()) return;

        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();
        float cx = screenW / 2.0f;
        float cy = screenH / 2.0f;
        float yawRad = (float) Math.toRadians(client.player.getYaw());
        Vec3d playerPos = client.player.getPos();

        for (var entry : EntityHighlightManager.getHighlighted().entrySet()) {
            if (!"red".equals(entry.getValue())) continue;

            Entity entity = client.world.getEntityById(entry.getKey());
            if (entity == null) continue;

            float dist = client.player.distanceTo(entity);
            if (dist > MAX_RANGE) continue;

            Vec3d ePos = entity.getPos();
            double dx = ePos.x - playerPos.x;
            double dz = ePos.z - playerPos.z;

            // Horizontal angle from player-forward to entity; normalise to (-π, π)
            double relYaw = Math.atan2(-dx, dz) - yawRad;
            relYaw = ((relYaw + Math.PI) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI) - Math.PI;

            // Suppress indicator when the entity is inside the camera's horizontal FOV
            double halfFovH = Math.toRadians(client.options.getFov().getValue())
                              * screenW / screenH / 2.0;
            if (Math.abs(relYaw) < halfFovH) continue;

            // Screen-space direction from center toward entity (outward direction)
            float sdx = (float)  Math.sin(relYaw);
            float sdy = -(float) Math.cos(relYaw);

            // Intersect (sdx,sdy) ray with the screen boundary rectangle minus margin
            float halfW = cx - EDGE_MARGIN;
            float halfH = cy - EDGE_MARGIN;
            float tx = Math.abs(sdx) > 0.001f ? halfW / Math.abs(sdx) : Float.MAX_VALUE;
            float ty = Math.abs(sdy) > 0.001f ? halfH / Math.abs(sdy) : Float.MAX_VALUE;
            float t = Math.min(tx, ty);

            float arrowX = cx + sdx * t;
            float arrowY = cy + sdy * t;

            float closeness = 1.0f - dist / MAX_RANGE;
            float size = MIN_SIZE + closeness * (MAX_SIZE - MIN_SIZE);

            drawArrow(context, arrowX, arrowY, sdx, sdy, size);
        }
    }

    /**
     * Draws an arrow at (x, y) with its tip pointing in the (fx, fy) direction.
     * All vertices computed directly in screen space — no rotation matrix needed.
     */
    private static void drawArrow(DrawContext context, float x, float y,
                                  float fx, float fy, float size) {
        // Perpendicular vector (rotated 90° CCW in screen space)
        float px = -fy;
        float py =  fx;

        // Tip extends outward from the anchor point
        float tipX = x + fx * size;
        float tipY = y + fy * size;

        // Arrowhead base: slightly inward from anchor, spanning full width
        float baseInset = size * 0.30f;
        float halfWidth = size * 0.55f;
        float bLX = x - fx * baseInset - px * halfWidth;
        float bLY = y - fy * baseInset - py * halfWidth;
        float bRX = x - fx * baseInset + px * halfWidth;
        float bRY = y - fy * baseInset + py * halfWidth;

        // Shaft: narrow rectangle trailing inward from the arrowhead base
        float shaftHalf = size * 0.20f;
        float shaftLen  = size * 0.75f;
        float s1LX = x - fx * baseInset - px * shaftHalf;
        float s1LY = y - fy * baseInset - py * shaftHalf;
        float s1RX = x - fx * baseInset + px * shaftHalf;
        float s1RY = y - fy * baseInset + py * shaftHalf;
        float s2LX = x - fx * shaftLen  - px * shaftHalf;
        float s2LY = y - fy * shaftLen  - py * shaftHalf;
        float s2RX = x - fx * shaftLen  + px * shaftHalf;
        float s2RY = y - fy * shaftLen  + py * shaftHalf;

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        var buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        // Arrowhead triangle
        buffer.vertex(matrix, tipX, tipY, 0).color(255, 30, 30, 230);
        buffer.vertex(matrix, bLX,  bLY,  0).color(255, 30, 30, 230);
        buffer.vertex(matrix, bRX,  bRY,  0).color(255, 30, 30, 230);

        // Shaft (two triangles = rectangle)
        buffer.vertex(matrix, s1LX, s1LY, 0).color(220, 30, 30, 200);
        buffer.vertex(matrix, s2LX, s2LY, 0).color(220, 30, 30, 200);
        buffer.vertex(matrix, s1RX, s1RY, 0).color(220, 30, 30, 200);
        buffer.vertex(matrix, s1RX, s1RY, 0).color(220, 30, 30, 200);
        buffer.vertex(matrix, s2LX, s2LY, 0).color(220, 30, 30, 200);
        buffer.vertex(matrix, s2RX, s2RY, 0).color(220, 30, 30, 200);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }
}
