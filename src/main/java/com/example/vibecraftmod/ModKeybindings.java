package com.example.vibecraftmod;

import com.example.vibecraftmod.config.PluginConfig;
import com.example.vibecraftmod.hud.HudState;
import com.example.vibecraftmod.screen.SchemaScreen;
import com.example.vibecraftmod.ui.SchemaConfig;
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
                
                // Get the default plugin from schema (plugin-agnostic)
                String defaultPlugin = SchemaConfig.getDefaultPlugin();
                if (defaultPlugin == null || defaultPlugin.isBlank()) {
                    defaultPlugin = "vibecraft"; // Final fallback
                }
                
                int keyCode = PluginConfig.getKey(defaultPlugin, "open_menu");
                int requiredMods = PluginConfig.getMods(defaultPlugin, "open_menu");
                int activeMods = currentMods(handle);
                boolean isDown = keyCode > 0
                        && InputUtil.isKeyPressed(handle, keyCode)
                        && activeMods == requiredMods;
                if (isDown && !wasDown) {
                    // Key combo comes from server-provided schema keybind defaults.
                    // The plugin is now dynamic, not hardcoded.
                    if (!ScreenManager.setActiveScreenForPlugin(defaultPlugin)) {
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

