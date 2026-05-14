package com.example.vibecraftmod.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

public class ArmorHudOverlay {

    // Match vanilla hotbar: 20px outer slot, 16px item inset by 2px each side
    private static final int SLOT_SIZE  = 20;
    private static final int ITEM_INSET = 2;
    private static final int MARGIN     = 5;

    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final int COLOR_BORDER   = 0xFF373737;
    private static final int COLOR_BG       = 0xB0141414;
    private static final int COLOR_COOLDOWN = 0xA0333333;

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
        int x2 = x + SLOT_SIZE;
        int y2 = y + SLOT_SIZE;

        // 1px border
        context.fill(x,      y,      x2,     y + 1,   COLOR_BORDER);
        context.fill(x,      y2 - 1, x2,     y2,      COLOR_BORDER);
        context.fill(x,      y + 1,  x + 1,  y2 - 1,  COLOR_BORDER);
        context.fill(x2 - 1, y + 1,  x2,     y2 - 1,  COLOR_BORDER);

        // Background
        context.fill(x + 1, y + 1, x2 - 1, y2 - 1, COLOR_BG);

        if (!stack.isEmpty()) {
            // Item render (16x16 centered in the slot)
            context.drawItem(stack, x + ITEM_INSET, y + ITEM_INSET);
            context.drawStackOverlay(client.textRenderer, stack, x + ITEM_INSET, y + ITEM_INSET);

            // Cooldown sweep: dark overlay from top that shrinks as cooldown expires
            float progress = client.player.getItemCooldownManager().getCooldownProgress(stack, tickDelta);
            if (progress < 1.0f) {
                int sweepHeight = Math.round((1.0f - progress) * (SLOT_SIZE - 2));
                if (sweepHeight > 0) {
                    context.fill(x + 1, y + 1, x2 - 1, y + 1 + sweepHeight, COLOR_COOLDOWN);
                }
            }
        }
    }
}
