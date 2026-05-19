package com.example.vibecraftmod.toast;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class CompletionToast {

    public static void show(MinecraftClient client) {
        SystemToast.add(
                client.getToastManager(),
                SystemToast.Type.NARRATOR_TOGGLE,
                Text.literal("◆ VibeCraft").formatted(Formatting.AQUA),
                Text.literal("Task complete").formatted(Formatting.GRAY));
    }
}
