package com.example.vibecraftmod;

public class DebugConfig {
    public static boolean DEBUG_SCHEMA = false;
    public static boolean DEBUG_EVENTS = false;
    public static boolean DEBUG_ACTIONS = false;

    public static void setAll(boolean value) {
        DEBUG_SCHEMA = value;
        DEBUG_EVENTS = value;
        DEBUG_ACTIONS = value;
    }
}