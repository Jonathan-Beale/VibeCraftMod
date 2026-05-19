package com.example.vibecraftmod.ui;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/**
 * Generic slot-based overlay widget (e.g., armor, inventory, hotbar).
 */
public class SlotOverlayWidget implements OverlayWidget {
    private final JsonObject config;
    public SlotOverlayWidget(JsonObject config) {
        this.config = config;
    }
    @Override
    public void render(DrawContext context, MinecraftClient client, OverlayDef def, Object data) {
        int x = def.position.x;
        int y = def.position.y;
        int size = Math.max(16, Math.min(def.size.width, def.size.height));
        context.fill(x, y, x + size, y + size, 0x66000000);
        ItemStack stack = data instanceof ItemStack s ? s : ItemStack.EMPTY;
        if (!stack.isEmpty() && client != null) {
            context.drawItem(stack, x + 2, y + 2);
            context.drawStackOverlay(client.textRenderer, stack, x + 2, y + 2);
        }
    }
}
