package com.example.vibecraftmod.config;

import com.example.vibecraftmod.ui.SchemaConfig;
import com.example.vibecraftmod.ui.ScreenDef;
import com.example.vibecraftmod.ui.ScreenManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Plugin-scoped configuration for keybinds and settings.
 * Each plugin can have its own keybind namespace (e.g., "vibecraft:open_menu", "enchantforge:open_editor").
 */
public final class PluginConfig {
    private PluginConfig() {}

    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("vibecraft");

    private static Map<String, JsonObject> perPluginData = new HashMap<>();

    static { loadAll(); }

    // -------------------------------------------------------------------------
    // Plugin-Scoped Keybinds (e.g., "vibecraft", "enchantforge")

    /** Get keybind for a specific plugin's action */
    public static int getKey(String pluginId, String actionId) {
        String fullId = pluginId + ":" + actionId;
        String storageKey = actionId + "_key";
        
        JsonObject pluginData = getPluginData(pluginId);
        if (pluginData.has(storageKey)) {
            return pluginData.get(storageKey).getAsInt();
        }
        
        return defaultKey(pluginId, actionId);
    }

    /** Get modifier mask for keybind */
    public static int getMods(String pluginId, String actionId) {
        String storageKey = actionId + "_mods";
        
        JsonObject pluginData = getPluginData(pluginId);
        if (pluginData.has(storageKey)) {
            return pluginData.get(storageKey).getAsInt();
        }
        
        return defaultMods(pluginId, actionId);
    }

    /** Store a keybind */
    public static void setKeybind(String pluginId, String actionId, int keyCode, int mods) {
        JsonObject pluginData = getPluginData(pluginId);
        pluginData.addProperty(actionId + "_key", keyCode);
        pluginData.addProperty(actionId + "_mods", mods);
        save(pluginId);
    }

    /** Check if a key combo matches a keybind */
    public static boolean matches(String pluginId, String actionId, int keyCode, int mods) {
        return keyCode == getKey(pluginId, actionId) && mods == getMods(pluginId, actionId);
    }

    /** Get friendly keybind name */
    public static String getKeybindName(String pluginId, String actionId) {
        int key = getKey(pluginId, actionId);
        if (key <= 0) return "None";
        String keyName = InputUtil.Type.KEYSYM.createFromCode(key)
                .getLocalizedText().getString();
        int mods = getMods(pluginId, actionId);
        StringBuilder sb = new StringBuilder();
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) sb.append("Ctrl+");
        if ((mods & GLFW.GLFW_MOD_SHIFT)   != 0) sb.append("Shift+");
        if ((mods & GLFW.GLFW_MOD_ALT)     != 0) sb.append("Alt+");
        sb.append(keyName);
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Defaults from Schema

    private static int defaultKey(String pluginId, String actionId) {
        SchemaConfig config = getScreenConfig(pluginId);
        if (config != null) {
            for (SchemaConfig.KeybindDef def : config.keybinds) {
                if (def.id.equals(actionId)) {
                    return glfwKeyFromString(def.defaultKey);
                }
            }
        }
        return -1;
    }

    private static int defaultMods(String pluginId, String actionId) {
        SchemaConfig config = getScreenConfig(pluginId);
        if (config != null) {
            for (SchemaConfig.KeybindDef def : config.keybinds) {
                if (def.id.equals(actionId)) {
                    return def.defaultMods;
                }
            }
        }
        return 0;
    }

    /** Get the SchemaConfig for a plugin's current screen */
    private static SchemaConfig getScreenConfig(String pluginId) {
        ScreenDef[] screens = SchemaConfig.getScreens();
        for (ScreenDef screen : screens) {
            if (screen.plugin.equals(pluginId)) {
                return screen.config;
            }
        }
        return null;
    }

    private static int glfwKeyFromString(String keyName) {
        return switch (keyName) {
            case "GRAVE_ACCENT" -> GLFW.GLFW_KEY_GRAVE_ACCENT;
            case "0" -> GLFW.GLFW_KEY_0;
            case "1" -> GLFW.GLFW_KEY_1;
            case "2" -> GLFW.GLFW_KEY_2;
            case "3" -> GLFW.GLFW_KEY_3;
            case "4" -> GLFW.GLFW_KEY_4;
            case "5" -> GLFW.GLFW_KEY_5;
            case "6" -> GLFW.GLFW_KEY_6;
            case "7" -> GLFW.GLFW_KEY_7;
            case "8" -> GLFW.GLFW_KEY_8;
            case "9" -> GLFW.GLFW_KEY_9;
            case "A" -> GLFW.GLFW_KEY_A;
            case "B" -> GLFW.GLFW_KEY_B;
            case "C" -> GLFW.GLFW_KEY_C;
            case "D" -> GLFW.GLFW_KEY_D;
            case "E" -> GLFW.GLFW_KEY_E;
            case "F" -> GLFW.GLFW_KEY_F;
            case "G" -> GLFW.GLFW_KEY_G;
            case "H" -> GLFW.GLFW_KEY_H;
            case "I" -> GLFW.GLFW_KEY_I;
            case "J" -> GLFW.GLFW_KEY_J;
            case "K" -> GLFW.GLFW_KEY_K;
            case "L" -> GLFW.GLFW_KEY_L;
            case "M" -> GLFW.GLFW_KEY_M;
            case "N" -> GLFW.GLFW_KEY_N;
            case "O" -> GLFW.GLFW_KEY_O;
            case "P" -> GLFW.GLFW_KEY_P;
            case "Q" -> GLFW.GLFW_KEY_Q;
            case "R" -> GLFW.GLFW_KEY_R;
            case "S" -> GLFW.GLFW_KEY_S;
            case "T" -> GLFW.GLFW_KEY_T;
            case "U" -> GLFW.GLFW_KEY_U;
            case "V" -> GLFW.GLFW_KEY_V;
            case "W" -> GLFW.GLFW_KEY_W;
            case "X" -> GLFW.GLFW_KEY_X;
            case "Y" -> GLFW.GLFW_KEY_Y;
            case "Z" -> GLFW.GLFW_KEY_Z;
            case "F1" -> GLFW.GLFW_KEY_F1;
            case "F2" -> GLFW.GLFW_KEY_F2;
            case "F3" -> GLFW.GLFW_KEY_F3;
            case "F4" -> GLFW.GLFW_KEY_F4;
            case "F5" -> GLFW.GLFW_KEY_F5;
            case "F6" -> GLFW.GLFW_KEY_F6;
            case "F7" -> GLFW.GLFW_KEY_F7;
            case "F8" -> GLFW.GLFW_KEY_F8;
            case "F9" -> GLFW.GLFW_KEY_F9;
            case "F10" -> GLFW.GLFW_KEY_F10;
            case "F11" -> GLFW.GLFW_KEY_F11;
            case "F12" -> GLFW.GLFW_KEY_F12;
            case "ESCAPE" -> GLFW.GLFW_KEY_ESCAPE;
            case "ENTER" -> GLFW.GLFW_KEY_ENTER;
            case "TAB" -> GLFW.GLFW_KEY_TAB;
            case "SPACE" -> GLFW.GLFW_KEY_SPACE;
            default -> -1;
        };
    }

    // -------------------------------------------------------------------------
    // Persistence

    private static JsonObject getPluginData(String pluginId) {
        return perPluginData.computeIfAbsent(pluginId, k -> new JsonObject());
    }

    private static void loadAll() {
        perPluginData.clear();
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
        } catch (Exception ignored) {}
    }

    private static void save(String pluginId) {
        try {
            Path file = CONFIG_DIR.resolve(pluginId + "-keybinds.json");
            JsonObject data = getPluginData(pluginId);
            Files.writeString(file, new Gson().toJson(data));
        } catch (Exception ignored) {}
    }
}
