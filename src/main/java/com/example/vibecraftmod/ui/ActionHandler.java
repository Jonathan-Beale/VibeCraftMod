package com.example.vibecraftmod.ui;

import com.google.gson.JsonObject;
import java.util.*;

/**
 * Generic interface for handling schema-defined actions.
 * Eliminates large switch statements by using a registry.
 */
public interface ActionHandler {
    /**
     * Execute this action with optional option value (for multi-choice actions).
     */
    void execute(JsonObject actionObj, String optionValue);

    /** Registry of action handlers by type */
    Map<String, ActionHandler> REGISTRY = new HashMap<>();

    static void register(String type, ActionHandler handler) {
        REGISTRY.put(type, handler);
    }

    static void execute(String type, JsonObject actionObj, String optionValue) {
        ActionHandler handler = REGISTRY.get(type);
        if (handler != null) {
            handler.execute(actionObj, optionValue);
        }
    }

    static void execute(String type, JsonObject actionObj) {
        execute(type, actionObj, null);
    }
}
