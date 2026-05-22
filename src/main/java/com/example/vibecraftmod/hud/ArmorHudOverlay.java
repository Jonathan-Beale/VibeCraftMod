package com.example.vibecraftmod.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

// TODO: replace hardcoded layout with schema-driven overlay — violates YAML-first / schema-driven design principle
public class ArmorHudOverlay {

    // The vanilla hotbar sprite is 182×22 px: 1px border top/bottom/left, then
    // 9 adjacent slots each 20px wide, then 1px right border.
    // Drawing it at (slotX - 1, slotY - 1) and scissor-clipping to 20×20 reveals
    // exactly one slot's interior — giving the same appearance as a hotbar slot.
    private static final Identifier HOTBAR_TEXTURE = Identifier.ofVanilla("hud/hotbar");
    private static final int HOTBAR_W = 182;
    private static final int HOTBAR_H = 22;

    // Vanilla hotbar: 20×20 slot, item drawn at +3 px from top-left
    private static final int SLOT_SIZE  = 20;
    private static final int ITEM_INSET = 3;
    private static final int MARGIN     = 5;

    private static final int COLOR_COOLDOWN = 0xA0333333;

    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public static void register() {
        HudRenderCallback.EVENT.register(ArmorHudOverlay::render);
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        float tickDelta = tickCounter.getTickDelta(true);
        int screenWidth  = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        // Right side, vertically centered
        int x = screenWidth - SLOT_SIZE - MARGIN;
        int y = (screenHeight - 4 * SLOT_SIZE) / 2;

        for (int i = 0; i < 4; i++) {
            drawSlot(context, x, y + i * SLOT_SIZE,
                    client.player.getEquippedStack(SLOTS[i]), client, tickDelta);
        }
    }

    private static void drawSlot(DrawContext context, int x, int y,
                                  ItemStack stack, MinecraftClient client, float tickDelta) {
        // Clip to this slot's area, then draw the hotbar sprite anchored so that
        // its slot-0 interior (past the 1px top/left border) falls exactly at (x, y).
        context.enableScissor(x, y, x + SLOT_SIZE, y + SLOT_SIZE);
        context.drawGuiTexture(RenderLayer::getGuiTextured,
            HOTBAR_TEXTURE, x - 1, y - 1, HOTBAR_W, HOTBAR_H);
        context.disableScissor();

        if (!stack.isEmpty()) {
            context.drawItem(stack, x + ITEM_INSET, y + ITEM_INSET);
            context.drawStackOverlay(client.textRenderer, stack, x + ITEM_INSET, y + ITEM_INSET);

            // Cooldown sweep from top (same as vanilla hotbar cooldown display)
            float progress = client.player.getItemCooldownManager().getCooldownProgress(stack, tickDelta);
            if (progress < 1.0f) {
                int sweepHeight = Math.round((1.0f - progress) * SLOT_SIZE);
                if (sweepHeight > 0) {
                    context.fill(x, y, x + SLOT_SIZE, y + sweepHeight, COLOR_COOLDOWN);
                }
            }
        }
    }
}
