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
import net.minecraft.util.math.RotationAxis;
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

            // Horizontal angle from player-forward to entity (0 = ahead, π/2 = right, ±π = behind)
            double relYaw = Math.atan2(-dx, dz) - yawRad;

            // Screen-space direction from center toward entity
            float sdx = (float) Math.sin(relYaw);
            float sdy = -(float) Math.cos(relYaw);

            // Intersect (sdx,sdy) ray with the screen boundary rectangle minus margin
            float halfW = cx - EDGE_MARGIN;
            float halfH = cy - EDGE_MARGIN;
            float tx = Math.abs(sdx) > 0.001f ? halfW / Math.abs(sdx) : Float.MAX_VALUE;
            float ty = Math.abs(sdy) > 0.001f ? halfH / Math.abs(sdy) : Float.MAX_VALUE;
            float t = Math.min(tx, ty);

            float arrowX = cx + sdx * t;
            float arrowY = cy + sdy * t;

            // Larger arrow for closer enemies
            float closeness = 1.0f - dist / MAX_RANGE;
            float size = MIN_SIZE + closeness * (MAX_SIZE - MIN_SIZE);

            // Rotate so tip points outward (away from center, toward the off-screen enemy)
            float rotation = (float) Math.atan2(sdx, -sdy);

            drawArrow(context, arrowX, arrowY, size, rotation);
        }
    }

    private static void drawArrow(DrawContext context, float x, float y, float size, float rotation) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0f);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation(rotation));

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        // Arrowhead: tip at top, wide base below center
        float tipY  = -size;
        float baseY =  size * 0.30f;
        float halfW =  size * 0.55f;
        // Shaft: narrow rectangle below the base
        float shaftW =  size * 0.20f;
        float shaftB =  size * 0.75f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        var buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        // Arrowhead triangle
        buffer.vertex(matrix,      0, tipY,  0).color(255, 30, 30, 230);
        buffer.vertex(matrix, -halfW, baseY, 0).color(255, 30, 30, 230);
        buffer.vertex(matrix,  halfW, baseY, 0).color(255, 30, 30, 230);

        // Shaft (two triangles = rectangle)
        buffer.vertex(matrix, -shaftW, baseY,  0).color(220, 30, 30, 200);
        buffer.vertex(matrix, -shaftW, shaftB, 0).color(220, 30, 30, 200);
        buffer.vertex(matrix,  shaftW, baseY,  0).color(220, 30, 30, 200);
        buffer.vertex(matrix,  shaftW, baseY,  0).color(220, 30, 30, 200);
        buffer.vertex(matrix, -shaftW, shaftB, 0).color(220, 30, 30, 200);
        buffer.vertex(matrix,  shaftW, shaftB, 0).color(220, 30, 30, 200);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.disableBlend();
        context.getMatrices().pop();
    }
}
