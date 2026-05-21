package com.example.vibecraftmod;

public class DebugConfig {
    public static boolean DEBUG_SCHEMA = true;
    public static boolean DEBUG_EVENTS = true;
    public static boolean DEBUG_ACTIONS = true;

    public static void setAll(boolean value) {
        DEBUG_SCHEMA = value;
        DEBUG_EVENTS = value;
        DEBUG_ACTIONS = value;
    }
}