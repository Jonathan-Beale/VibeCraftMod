package com.example.vibecraftmod.ui;

import com.google.gson.JsonObject;
import com.example.vibecraftmod.hud.HudState;
import com.example.vibecraftmod.network.ModPackets;
import com.example.vibecraftmod.ui.ScreenManager;
import net.minecraft.client.MinecraftClient;

/**
 * Factory for registering all standard action handlers.
 * Replaces the large switch statement in runAction() with registry-based dispatch.
 */
public class StandardActions {
    
    /**
     * Register all standard action handlers.
     * Call once at app startup.
     */
    public static void registerAll() {
        ActionHandler.register("send_message", sendMessageHandler());
        ActionHandler.register("set_setting", setSettingHandler());
        ActionHandler.register("clear_history", clearHistoryHandler());
        ActionHandler.register("request_history", requestHistoryHandler());
        ActionHandler.register("sync_history", syncHistoryHandler());
        ActionHandler.register("close_modal", closeModalHandler());
        ActionHandler.register("close_screen", closeScreenHandler());
        ActionHandler.register("toggle_thoughts", toggleThoughtsHandler());
        ActionHandler.register("open_modal", openModalHandler());
        ActionHandler.register("select_option", selectOptionHandler());
        ActionHandler.register("scroll", scrollHandler());
        ActionHandler.register("noop", noopHandler());
    }

    private static ActionHandler sendMessageHandler() {
        return (action, optionValue) -> {
            String text = action.has("text") ? action.get("text").getAsString() : "";
            if (!text.isBlank()) {
                ModPackets.sendChat(text);
            }
        };
    }

    private static ActionHandler setSettingHandler() {
        return (action, optionValue) -> {
            String key = action.has("key") ? action.get("key").getAsString() : null;
            String value = action.has("value") ? action.get("value").getAsString() : null;
            if (key != null && value != null) {
                ModPackets.sendSetSetting(key, value);
            }
        };
    }

    private static ActionHandler clearHistoryHandler() {
        return (action, optionValue) -> {
            ModPackets.sendCommand("/chistory clear");
        };
    }

    private static ActionHandler requestHistoryHandler() {
        return (action, optionValue) -> {
            ModPackets.requestHistory();
        };
    }

    private static ActionHandler syncHistoryHandler() {
        return (action, optionValue) -> {
            ModPackets.requestHistory();
        };
    }

    private static ActionHandler closeModalHandler() {
        return (action, optionValue) -> {
            ScreenManager.closeModal();
        };
    }

    private static ActionHandler closeScreenHandler() {
        return (action, optionValue) -> {
            if (MinecraftClient.getInstance().currentScreen != null) {
                MinecraftClient.getInstance().setScreen(null);
            }
        };
    }

    private static ActionHandler toggleThoughtsHandler() {
        return (action, optionValue) -> {
            HudState.toggleThoughts();
        };
    }

    private static ActionHandler openModalHandler() {
        return (action, optionValue) -> {
            String modalId = action.has("modalId") ? action.get("modalId").getAsString() : null;
            if (modalId != null) {
                ScreenManager.openModal(modalId);
            }
        };
    }

    private static ActionHandler selectOptionHandler() {
        return (action, optionValue) -> {
            if (optionValue != null) {
                // Send selected option value
                ModPackets.sendChat(optionValue);
            }
        };
    }

    private static ActionHandler scrollHandler() {
        return (action, optionValue) -> {
            int delta = action.has("delta") ? action.get("delta").getAsInt() : 0;
            if (delta != 0) {
                // Scroll event (depends on current screen context)
            }
        };
    }

    private static ActionHandler noopHandler() {
        return (action, optionValue) -> {
            // No operation
        };
    }
}

