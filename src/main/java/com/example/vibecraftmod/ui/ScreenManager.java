package com.example.vibecraftmod.ui;

import java.util.*;

/**
 * Manages the active screen and screen switching.
 * Each screen is provided by a plugin and has its own configuration,
 * widgets, and action handlers.
 */
public final class ScreenManager {
    private static String activeScreenId;

    private ScreenManager() {}

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
