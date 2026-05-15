package com.example.vibecraftmod;

import com.example.vibecraftmod.config.PluginConfig;
import com.example.vibecraftmod.hud.HudState;
import com.example.vibecraftmod.screen.SchemaScreen;
import com.example.vibecraftmod.ui.ScreenManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ModKeybindings {

    private static boolean wasDown = false;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen == null) {
                long handle = client.getWindow().getHandle();
                int keyCode = PluginConfig.getKey("vibecraft", "open_menu");
                int requiredMods = PluginConfig.getMods("vibecraft", "open_menu");
                int activeMods = currentMods(handle);
                boolean isDown = keyCode > 0
                        && InputUtil.isKeyPressed(handle, keyCode)
                        && activeMods == requiredMods;
                if (isDown && !wasDown) {
                    // Backtick is VibeCraft terminal hotkey; key combo comes from server-provided schema keybind defaults.
                    if (!ScreenManager.setActiveScreenForPlugin("vibecraft")) {
                        ScreenManager.init();
                    }
                    HudState.clearQuestion();
                    client.setScreen(new SchemaScreen());
                }
                wasDown = isDown;
            } else {
                wasDown = false;
            }
        });
    }

    private static int currentMods(long windowHandle) {
        int mods = 0;
        if (InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
            mods |= GLFW.GLFW_MOD_CONTROL;
        }
        if (InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            mods |= GLFW.GLFW_MOD_SHIFT;
        }
        if (InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_LEFT_ALT)
                || InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_RIGHT_ALT)) {
            mods |= GLFW.GLFW_MOD_ALT;
        }
        return mods;
    }
}

