package com.example.vibecraftmod.config;

import com.example.vibecraftmod.ui.SchemaConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientConfig {
    private ClientConfig() {}

    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("vibecraftmod.json");

    private static JsonObject data = new JsonObject();

    static { load(); }

    // -------------------------------------------------------------------------
    // Defaults (read from schema)

    private static int defaultKey(String id) {
        // Try to get from schema config first
        SchemaConfig.KeybindDef def = findKeybindDef(id);
        if (def != null) {
            return glfwKeyFromString(def.defaultKey);
        }
        // Fallback to hardcoded defaults
        return switch (id) {
            case "open_menu"       -> GLFW.GLFW_KEY_GRAVE_ACCENT;
            case "toggle_thoughts" -> GLFW.GLFW_KEY_T;
            case "open_options"    -> GLFW.GLFW_KEY_O;
            case "open_help"       -> GLFW.GLFW_KEY_H;
            case "sync_history"    -> GLFW.GLFW_KEY_R;
            case "clear_history"   -> GLFW.GLFW_KEY_L;
            default                -> -1;
        };
    }

    private static int defaultMods(String id) {
        // Try to get from schema config first
        SchemaConfig.KeybindDef def = findKeybindDef(id);
        if (def != null) {
            return def.defaultMods;
        }
        // Fallback to hardcoded defaults
        return switch (id) {
            case "toggle_thoughts", "open_options", "open_help", "sync_history", "clear_history"
                    -> GLFW.GLFW_MOD_CONTROL;
            default                                 -> 0;
        };
    }

    private static SchemaConfig.KeybindDef findKeybindDef(String id) {
        SchemaConfig config = SchemaConfig.get();
        for (SchemaConfig.KeybindDef def : config.keybinds) {
            if (def.id.equals(id)) return def;
        }
        return null;
    }

    private static int glfwKeyFromString(String keyName) {
        return switch (keyName) {
            case "GRAVE_ACCENT" -> GLFW.GLFW_KEY_GRAVE_ACCENT;
            case "T" -> GLFW.GLFW_KEY_T;
            case "O" -> GLFW.GLFW_KEY_O;
            case "H" -> GLFW.GLFW_KEY_H;
            case "R" -> GLFW.GLFW_KEY_R;
            case "L" -> GLFW.GLFW_KEY_L;
            default -> -1;
        };
    }

    // -------------------------------------------------------------------------
    // Key-only binds (e.g. open_menu — polled in tick, no modifier needed)

    public static int getKey(String id) {
        String legacyKey = id;             // old format stored bare id
        String newKey    = id + "_key";
        if (data.has(newKey))    return data.get(newKey).getAsInt();
        if (data.has(legacyKey)) return data.get(legacyKey).getAsInt();
        return defaultKey(id);
    }

    public static void setKey(String id, int keyCode) {
        data.addProperty(id, keyCode);     // keep legacy format for open_menu
        save();
    }

    // -------------------------------------------------------------------------
    // Combo binds (key + modifier mask)

    public static int getMods(String id) {
        String key = id + "_mods";
        return data.has(key) ? data.get(key).getAsInt() : defaultMods(id);
    }

    public static void setKeybind(String id, int keyCode, int mods) {
        data.addProperty(id + "_key",  keyCode);
        data.addProperty(id + "_mods", mods);
        save();
    }

    /** Returns true when keyCode and mods exactly match the stored binding. */
    public static boolean matches(String id, int keyCode, int mods) {
        return keyCode == getKey(id) && mods == getMods(id);
    }

    // -------------------------------------------------------------------------
    // Display

    public static String getKeybindName(String id) {
        int key  = getKey(id);
        if (key <= 0) return "None";
        String keyName = InputUtil.Type.KEYSYM.createFromCode(key)
                .getLocalizedText().getString();
        int mods = getMods(id);
        StringBuilder sb = new StringBuilder();
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) sb.append("Ctrl+");
        if ((mods & GLFW.GLFW_MOD_SHIFT)   != 0) sb.append("Shift+");
        if ((mods & GLFW.GLFW_MOD_ALT)     != 0) sb.append("Alt+");
        sb.append(keyName);
        return sb.toString();
    }

    // -------------------------------------------------------------------------

    private static void load() {
        try {
            if (Files.exists(CONFIG_FILE))
                data = JsonParser.parseString(Files.readString(CONFIG_FILE)).getAsJsonObject();
        } catch (Exception ignored) {}
    }

    private static void save() {
        try { Files.writeString(CONFIG_FILE, new Gson().toJson(data)); }
        catch (Exception ignored) {}
    }
}
