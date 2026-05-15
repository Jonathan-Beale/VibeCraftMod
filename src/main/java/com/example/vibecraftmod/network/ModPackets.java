package com.example.vibecraftmod.network;

import com.example.vibecraftmod.hud.ClaudeHudState;
import com.example.vibecraftmod.screen.DynamicClaudeScreen;
import com.example.vibecraftmod.settings.ClaudeSettings;
import com.example.vibecraftmod.settings.ColorScheme;
import com.example.vibecraftmod.toast.ClaudeCompletionToast;
import com.example.vibecraftmod.ui.ScreenManager;
import com.example.vibecraftmod.ui.UiSchemaStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModPackets {

    // Colors are resolved from ColorScheme at event-handling time so they reflect the active scheme.
    private static final java.util.List<JsonObject> historyBuffer = new java.util.ArrayList<>();
    private static String lastOpenScreenId = "";
    private static long lastOpenScreenAtMs = 0L;

    public static void register() {
        PayloadTypeRegistry.playS2C().register(VibeCraftEventPayload.ID, VibeCraftEventPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VibeCraftInputPayload.ID, VibeCraftInputPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(VibeCraftEventPayload.ID, (payload, context) ->
                context.client().execute(() -> handleEvent(payload.json(), context.client())));
    }

    private static void handleEvent(String json, net.minecraft.client.MinecraftClient client) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String type = obj.get("type").getAsString();

            if (com.example.vibecraftmod.DebugConfig.DEBUG_EVENTS) {
                if ("open_screen".equals(type)) {
                    String screenId = obj.has("screenId") ? obj.get("screenId").getAsString() : "";
                    System.out.println("[VibeCraftMod] handleEvent: open_screen for screenId=" + screenId);
                }
                if ("close_screen".equals(type)) {
                    System.out.println("[VibeCraftMod] handleEvent: close_screen");
                }
            }

            switch (type) {
                case "user_message" -> {
                    String text = obj.has("text") ? obj.get("text").getAsString() : "";
                    if (!text.isEmpty()) ClaudeHudState.addLine("> " + text, ColorScheme.get().user());
                }
                case "stream_start" -> {
                    ClaudeHudState.setStreaming(true);
                    ClaudeHudState.setCurrentTool(null, null);
                    ClaudeHudState.clearQuestion();
                }
                case "thinking" -> {
                    String text = obj.has("text") ? obj.get("text").getAsString() : "";
                    for (String l : text.split("\n")) {
                        if (!l.isBlank()) ClaudeHudState.addThought(l);
                    }
                }
                case "question" -> {
                    ColorScheme cs = ColorScheme.get();
                    String prompt = obj.has("prompt") ? obj.get("prompt").getAsString() : "";
                    java.util.List<String> opts = new java.util.ArrayList<>();
                    if (obj.has("options")) {
                        for (var el : obj.getAsJsonArray("options")) opts.add(el.getAsString());
                    }
                    ClaudeHudState.addLine("? " + prompt, cs.question());
                    for (int i = 0; i < opts.size(); i++) {
                        ClaudeHudState.addLine("  [" + (char)('A' + i) + "] " + opts.get(i), cs.claude());
                    }
                    ClaudeHudState.setQuestion(new ClaudeHudState.PendingQuestion(prompt, opts));
                }
                case "tool_call" -> {
                    String tool   = obj.has("tool")   ? obj.get("tool").getAsString()   : "";
                    String detail = obj.has("detail") ? obj.get("detail").getAsString() : "";
                    ClaudeHudState.setCurrentTool(tool, detail);
                    ClaudeHudState.addCollapsedLine("[" + tool + "] " + detail, ColorScheme.get().tool());
                }
                case "claude_text" -> {
                    ClaudeHudState.setCurrentTool(null, null);
                    JsonArray lines = obj.getAsJsonArray("lines");
                    int claudeColor = ColorScheme.get().claude();
                    for (var el : lines) {
                        ClaudeHudState.addLine(stripLegacyFormatting(el.getAsString()), claudeColor);
                    }
                }
                case "bash_output" -> {
                    JsonArray lines = obj.getAsJsonArray("lines");
                    int outputColor = ColorScheme.get().output();
                    for (var el : lines) {
                        ClaudeHudState.addLine(el.getAsString(), outputColor);
                    }
                }
                case "stream_end" -> {
                    ClaudeHudState.setStreaming(false);
                    ClaudeHudState.setCurrentTool(null, null);
                    ClaudeHudState.setStreamEndTime(System.currentTimeMillis());
                    ClaudeCompletionToast.show(client);
                }
                case "ui_schema" -> {
                    if (obj.has("schema") && obj.get("schema").isJsonObject()) {
                        UiSchemaStore.setSchema(obj.getAsJsonObject("schema"));
                    }
                }
                case "open_screen" -> {
                    String screenId = obj.has("screenId") ? obj.get("screenId").getAsString() : "";
                    if (screenId.isBlank() || client == null) break;

                    String currentScreenId = ScreenManager.getActiveScreenId();
                    boolean sameScreenId = screenId.equals(currentScreenId);
                    boolean uiAlreadyOpen = client.currentScreen instanceof DynamicClaudeScreen;
                    long nowMs = System.currentTimeMillis();
                    boolean rapidDuplicate = screenId.equals(lastOpenScreenId) && (nowMs - lastOpenScreenAtMs) < 500;
                    lastOpenScreenId = screenId;
                    lastOpenScreenAtMs = nowMs;

                    if (uiAlreadyOpen && (sameScreenId || rapidDuplicate)) {
                        if (com.example.vibecraftmod.DebugConfig.DEBUG_EVENTS) {
                            System.out.println("[VibeCraftMod] handleEvent: ignored duplicate open_screen for screenId=" + screenId);
                        }
                        break;
                    }

                    if (ScreenManager.setActiveScreen(screenId)) {
                        client.setScreen(new DynamicClaudeScreen());
                    } else if (com.example.vibecraftmod.DebugConfig.DEBUG_EVENTS) {
                        System.out.println("[VibeCraftMod] handleEvent: unknown screenId in open_screen=" + screenId);
                    }
                }
                case "close_screen" -> {
                    if (client != null) {
                        client.setScreen(null);
                    }
                }
                case "history_chunk" -> {
                    JsonArray chunkEntries = obj.has("entries") ? obj.getAsJsonArray("entries") : new JsonArray();
                    for (var el : chunkEntries) {
                        if (el.isJsonObject()) historyBuffer.add(el.getAsJsonObject());
                    }
                    boolean done = obj.has("done") && obj.get("done").getAsBoolean();
                    if (done) {
                        JsonArray merged = new JsonArray();
                        for (JsonObject e : historyBuffer) merged.add(e);
                        historyBuffer.clear();
                        handleHistory(merged);
                    }
                }
                case "history"  -> handleHistory(obj.getAsJsonArray("entries"));
                case "settings" -> {
                    Map<String, String> vals = new HashMap<>();
                    JsonObject vs = obj.getAsJsonObject("values");
                    for (var entry : vs.entrySet()) {
                        vals.put(entry.getKey(), entry.getValue().getAsString());
                    }
                    ClaudeSettings.applyAll(vals);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void handleHistory(JsonArray entries) {
        if (entries == null) return;
        ColorScheme cs = ColorScheme.get();
        java.util.List<ClaudeHudState.HudLine> lines = new java.util.ArrayList<>();
        for (var el : entries) {
            JsonObject e = el.getAsJsonObject();
            String type   = e.has("type")   ? e.get("type").getAsString()   : "";
            String header = e.has("header") ? e.get("header").getAsString() : "";
            String body   = e.has("body")   ? e.get("body").getAsString()   : "";
            switch (type) {
                case "USER" -> {
                    String text = body.isEmpty() ? header : body;
                    String lineText = "> " + text;
                    // Skip consecutive duplicate USER lines (artifact of old double-addUserMessage bug)
                    if (!lines.isEmpty()) {
                        ClaudeHudState.HudLine last = lines.get(lines.size() - 1);
                        if (last.text().equals(lineText) && last.color() == cs.user()) break;
                    }
                    lines.add(new ClaudeHudState.HudLine(lineText, cs.user()));
                }
                case "CLAUDE" -> {
                    for (String l : body.split("\n")) {
                        l = l.stripTrailing();
                        if (!l.isEmpty()) lines.addAll(parseMarkdownLine(l, cs));
                    }
                }
                case "TOOL" -> {
                    String[] parts = header.split("\\|", 2);
                    String tool   = parts.length > 0 ? parts[0] : "";
                    String detail = parts.length > 1 ? parts[1] : "";
                    lines.add(new ClaudeHudState.HudLine("[" + tool + "] " + detail, cs.tool(), false, true));
                }
                case "BASH" -> {
                    for (String l : body.split("\n")) {
                        lines.add(new ClaudeHudState.HudLine(l, cs.output()));
                    }
                }
                case "SYSTEM" -> lines.add(new ClaudeHudState.HudLine(header, cs.system()));
            }
        }
        ClaudeHudState.replaceWithHistory(lines);
    }

    public static void sendInput(String message) {
        sendInput(message, "vibecraft");  // Default to vibecraft for backward compat
    }

    public static void sendInput(String message, String plugin) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "message");
        obj.addProperty("plugin", plugin);
        obj.addProperty("message", message);
        ClientPlayNetworking.send(new VibeCraftInputPayload(obj.toString()));
    }

    public static void requestHistory() {
        requestHistory("vibecraft");
    }

    public static void requestHistory(String plugin) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "request_history");
        obj.addProperty("plugin", plugin);
        ClientPlayNetworking.send(new VibeCraftInputPayload(obj.toString()));
    }

    public static void sendSetSetting(String key, String value) {
        sendSetSetting("vibecraft", key, value);  // Default to vibecraft for backward compat
    }

    public static void sendSetSetting(String plugin, String key, String value) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "set_setting");
        obj.addProperty("plugin", plugin);
        obj.addProperty("key", key);
        obj.addProperty("value", value);
        ClientPlayNetworking.send(new VibeCraftInputPayload(obj.toString()));
    }

    public static void clearHistory() {
        clearHistory("vibecraft");
    }

    public static void clearHistory(String plugin) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "clear_history");
        obj.addProperty("plugin", plugin);
        ClientPlayNetworking.send(new VibeCraftInputPayload(obj.toString()));
    }

    private static java.util.List<ClaudeHudState.HudLine> parseMarkdownLine(String line, ColorScheme cs) {
        if (line.startsWith("### ")) return List.of(new ClaudeHudState.HudLine(line.substring(4), cs.question()));
        if (line.startsWith("## "))  return List.of(new ClaudeHudState.HudLine(line.substring(3), cs.question()));
        if (line.startsWith("# "))   return List.of(new ClaudeHudState.HudLine(line.substring(2), cs.question()));
        if (line.startsWith("> "))   return List.of(new ClaudeHudState.HudLine("> " + line.substring(2), cs.user()));
        if (line.equals(">"))        return List.of(new ClaudeHudState.HudLine(">", cs.user()));
        if (line.startsWith("- ") || line.startsWith("* "))
            return List.of(new ClaudeHudState.HudLine("• " + line.substring(2), cs.claude()));
        if (line.equals("---") || line.equals("***") || line.equals("___"))
            return List.of(new ClaudeHudState.HudLine("────────────────────", cs.system()));
        return List.of(new ClaudeHudState.HudLine(line, cs.claude()));
    }

    private static String stripLegacyFormatting(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }
}
