package com.example.vibecraftmod;

import com.example.vibecraftmod.config.ClientConfig;
import com.example.vibecraftmod.hud.ClaudeHudState;
import com.example.vibecraftmod.screen.DynamicClaudeScreen;
import com.example.vibecraftmod.ui.ScreenManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.InputUtil;

public class ModKeybindings {

    private static boolean wasDown = false;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen == null) {
                int keyCode = ClientConfig.getKey("open_menu");
                boolean isDown = keyCode > 0 &&
                        InputUtil.isKeyPressed(client.getWindow().getHandle(), keyCode);
                if (isDown && !wasDown) {
                    // Backtick is the terminal hotkey: always target the primary VibeCraft screen.
                    if (!ScreenManager.setActiveScreen("vibecraft:claude")) {
                        ScreenManager.init();
                    }
                    ClaudeHudState.clearQuestion();
                    client.setScreen(new DynamicClaudeScreen());
                }
                wasDown = isDown;
            } else {
                wasDown = false;
            }
        });
    }
}
