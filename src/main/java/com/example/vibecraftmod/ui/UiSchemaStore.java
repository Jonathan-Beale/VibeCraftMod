package com.example.vibecraftmod.ui;

import com.example.vibecraftmod.DebugConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class UiSchemaStore {
    private UiSchemaStore() {}

    private static JsonObject schema;

    public static synchronized boolean hasSchema() {
      return schema != null;
    }

    public static synchronized void setSchema(JsonObject value) {
        schema = value;
      if (DebugConfig.DEBUG_SCHEMA) {
        System.out.println("[VibeCraftMod] UiSchemaStore.setSchema: schema set, triggering reload");
      }
        SchemaConfig.reload();  // Reload config when schema changes
        ScreenManager.reload();
        OverlayManager.reload();
    }

    public static synchronized JsonObject getSchema() {
        if (schema != null) return schema;
        return JsonParser.parseString(defaultSchemaJson()).getAsJsonObject();
    }

    private static String defaultSchemaJson() {
        return """
            {
              "id": "claude-main",
              "title": "Claude Chat",
              "panel": {
                "widthPercent": 0.7,
                "maxWidth": 520,
                "padding": 8,
                "titleHeight": 14,
                "background": "DD0A0A0F",
                "titleBackground": "FF111118",
                "divider": "FF2A2A3A",
                "label": "CCCCCC"
              },
              "widgets": [
                {"type": "toolbar", "showOptions": true, "showHelp": true, "showClose": true,
                 "optionsLabel": "Options", "helpLabel": "Help", "closeLabel": "X", "buttonPaddingX": 6,
                 "buttonBg": "FF18304D", "buttonHover": "FF24507A",
                 "buttonDanger": "FF4A1A1A", "buttonDangerHover": "FF6A2222",
                 "showInlineStatus": true, "idleLabel": "Idle", "statusColor": "FF96A6C2"},
                {"type": "history", "flex": true, "lineHeight": 11, "showThoughts": false, "scrollbarWidth": 3},
                {"type": "question_options", "buttonHeight": 16, "buttonGap": 2, "buttonBg": "FF142033", "buttonHover": "FF1E3A5F"},
                {"type": "input", "height": 72, "placeholder": "Message Claude...", "maxLength": 1000, "prompt": "◆"},
                {"type": "modal", "id": "help-modal", "title": "Help", "height": 280,
                 "widgets": [
                   {"type": "text", "height": 22, "wrap": true, "text": "Shortcuts", "color": "FFDDDDDD"},
                   {"type": "text", "height": 110, "wrap": true,
                    "text": "Open panel: ${shortcut.open_menu}\\nOpen options: ${shortcut.open_options}\\nOpen help: ${shortcut.open_help}\\nSync history: ${shortcut.sync_history}\\nClear history: ${shortcut.clear_history}\\nToggle thoughts: ${shortcut.toggle_thoughts}\\n\\nEditing keys: Enter=Send, Shift+Enter=New line, Ctrl+V=Paste",
                    "color": "FF96A6C2"},
                   {"type": "action_row", "height": 18, "buttons": [
                     {"label": "Clear History", "action": {"type": "clear_history"}},
                     {"label": "Sync", "action": {"type": "request_history"}},
                     {"label": "Close", "action": {"type": "close_modal"}}
                   ]}
                 ]}
              ]
            }
            """;
    }

    public static synchronized JsonArray widgets() {
        JsonObject s = getSchema();
        if (s.has("widgets") && s.get("widgets").isJsonArray()) return s.getAsJsonArray("widgets");
        return new JsonArray();
    }
}
