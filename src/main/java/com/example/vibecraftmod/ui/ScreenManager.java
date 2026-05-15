package com.example.vibecraftmod.ui;

import java.util.*;

/**
 * Manages the active screen and screen switching.
 * Each screen is provided by a plugin and has its own configuration,
 * widgets, and action handlers.
 */
public final class ScreenManager {
        // Closes the current modal (for UI actions)
        public static void closeModal() {
            // In this mod, closing a modal is equivalent to setting the screen to null
            net.minecraft.client.MinecraftClient.getInstance().setScreen(null);
        }

        // Opens a modal by ID (for UI actions)
        public static void openModal(String modalId) {
            // This is a stub. Implement modal logic as needed.
            // For now, just print to console for debug.
            System.out.println("[VibeCraftMod] openModal called with modalId=" + modalId);
        }
    private static String activeScreenId;

    private ScreenManager() {}

    /** Reset active screen ID so next access re-initialises from schema. */
    public static synchronized void reset() {
        activeScreenId = null;
    }

    /** Initialize with the first available screen */
    public static synchronized void init() {
        ScreenDef[] screens = SchemaConfig.getScreens();
        if (screens.length > 0) {
            activeScreenId = screens[0].id;
        }
    }

    /** Get the currently active screen */
    public static synchronized ScreenDef getActiveScreen() {
        if (activeScreenId == null) init();
        return SchemaConfig.getScreen(activeScreenId);
    }

    /** Get the ID of the active screen */
    public static synchronized String getActiveScreenId() {
        if (activeScreenId == null) init();
        return activeScreenId;
    }

    /** Switch to a different screen */
    public static synchronized boolean setActiveScreen(String screenId) {
        ScreenDef screen = SchemaConfig.getScreen(screenId);
        if (screen != null) {
            activeScreenId = screenId;
            return true;
        }
        return false;
    }

    /** Switch to the highest-priority screen owned by a plugin. */
    public static synchronized boolean setActiveScreenForPlugin(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) return false;
        for (ScreenDef screen : SchemaConfig.getScreens()) {
            if (pluginId.equalsIgnoreCase(screen.plugin)) {
                activeScreenId = screen.id;
                return true;
            }
        }
        return false;
    }

    /** Reload screens and reset to first if active screen no longer exists */
    public static synchronized void reload() {
        SchemaConfig.reload();
        ScreenDef active = SchemaConfig.getScreen(activeScreenId);
        if (active == null) {
            init();
        }
    }

    /** Get all available screens, sorted by priority */
    public static synchronized ScreenDef[] getAllScreens() {
        return SchemaConfig.getScreens();
    }

    /** Get plugin ID for the active screen */
    public static synchronized String getActivePlugin() {
        ScreenDef screen = getActiveScreen();
        return screen != null ? screen.plugin : null;
    }
}
