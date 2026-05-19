package com.example.vibecraftmod;

import com.example.vibecraftmod.config.PluginConfig;
import com.example.vibecraftmod.hud.HudState;
import com.example.vibecraftmod.screen.SchemaScreen;
import com.example.vibecraftmod.ui.SchemaConfig;
import com.example.vibecraftmod.ui.ScreenDef;
import com.example.vibecraftmod.ui.ScreenManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModKeybindings {

    private static final Map<String, Boolean> bindingWasDown = new HashMap<>();

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen == null) {
                long handle = client.getWindow().getHandle();
                int activeMods = currentMods(handle);

                List<OpenMenuBinding> bindings = resolveOpenMenuBindings();
                Set<String> liveBindingIds = new HashSet<>();
                OpenMenuBinding triggered = null;

                for (OpenMenuBinding binding : bindings) {
                    liveBindingIds.add(binding.id());

                    boolean isDown = InputUtil.isKeyPressed(handle, binding.keyCode)
                            && activeMods == binding.requiredMods;
                    boolean wasDown = bindingWasDown.getOrDefault(binding.id(), false);

                    if (triggered == null && isDown && !wasDown) {
                        triggered = binding;
                    }

                    bindingWasDown.put(binding.id(), isDown);
                }

                // Prevent unbounded growth when schema/plugins change.
                bindingWasDown.keySet().removeIf(id -> !liveBindingIds.contains(id));

                if (triggered != null) {
                    if (!ScreenManager.setActiveScreenForPlugin(triggered.pluginId)) {
                        ScreenManager.init();
                    }
                    HudState.clearQuestion();
                    client.setScreen(new SchemaScreen());
                }
            } else {
                // Reset edge-trigger state while any UI is open.
                bindingWasDown.clear();
            }
        });
    }

    private static List<OpenMenuBinding> resolveOpenMenuBindings() {
        List<OpenMenuBinding> bindings = new ArrayList<>();
        Set<String> seenPlugins = new HashSet<>();

        // Reserve terminal opener precedence when keybind combos collide.
        int vibecraftKey = PluginConfig.getKey("vibecraft", "open_menu");
        if (vibecraftKey > 0) {
            int vibecraftMods = PluginConfig.getMods("vibecraft", "open_menu");
            bindings.add(new OpenMenuBinding("vibecraft", vibecraftKey, vibecraftMods));
            seenPlugins.add("vibecraft");
        }

        // Screens are sorted by priority; this order is used to break keybind ties.
        for (ScreenDef screen : SchemaConfig.getScreens()) {
            String pluginId = screen.plugin;
            if (pluginId == null || pluginId.isBlank() || !seenPlugins.add(pluginId)) {
                continue;
            }
            int keyCode = PluginConfig.getKey(pluginId, "open_menu");
            if (keyCode <= 0) {
                continue;
            }
            int requiredMods = PluginConfig.getMods(pluginId, "open_menu");
            bindings.add(new OpenMenuBinding(pluginId, keyCode, requiredMods));
        }

        return bindings;
    }

    private static final class OpenMenuBinding {
        private final String pluginId;
        private final int keyCode;
        private final int requiredMods;

        private OpenMenuBinding(String pluginId, int keyCode, int requiredMods) {
            this.pluginId = pluginId;
            this.keyCode = keyCode;
            this.requiredMods = requiredMods;
        }

        private String id() {
            return pluginId + "|" + keyCode + "|" + requiredMods;
        }
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

