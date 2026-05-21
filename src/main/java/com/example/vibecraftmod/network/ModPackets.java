package com.example.vibecraftmod.network;

import com.example.vibecraftmod.EntityHighlightManager;
import com.example.vibecraftmod.hud.HudState;
import com.example.vibecraftmod.screen.SchemaScreen;
import com.example.vibecraftmod.settings.ModSettings;
import com.example.vibecraftmod.settings.ColorScheme;
import com.example.vibecraftmod.toast.CompletionToast;
import com.example.vibecraftmod.ui.OverlayDataBinding;
import com.example.vibecraftmod.ui.ScreenManager;
import com.example.vibecraftmod.ui.UiSchemaStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModPackets {
    // Sends a chat message as if typed by the user (for UI actions)
    public static void sendChat(String text) {
        sendInput(text);
    }

    // Sends a command as if typed by the user (for UI actions)
    public static void sendCommand(String command) {
        sendInput(command);
    }

    // Colors are resolved from ColorScheme at event-handling time so they reflect the active scheme.
    private static final Map<String, java.util.List<JsonObject>> historyBuffersByPlugin = new HashMap<>();
    private static final Map<String, Long> lastSequenceByPluginAndChannel = new HashMap<>();
    private static String lastOpenScreenId = "";
    private static long lastOpenScreenAtMs = 0L;

    public static void register() {
        PayloadTypeRegistry.playS2C().register(VibeCraftEventPayload.ID, VibeCraftEventPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VibeCraftInputPayload.ID, VibeCraftInputPayload.CODEC);

        // Request fresh schema+history every time we join a world.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
            client.execute(() -> requestHistory(resolveDefaultPlugin())));

        // Clear stale state on disconnect so reconnects always start clean.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            UiSchemaStore.clear();
            HudState.clear();
            ScreenManager.reset();
            EntityHighlightManager.clear();
            historyBuffersByPlugin.clear();
            lastSequenceByPluginAndChannel.clear();
        });

        ClientPlayNetworking.registerGlobalReceiver(VibeCraftEventPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (payload.protocolVersion() != VibeCraftEventPayload.PROTOCOL_VERSION) {
                        System.err.println("[VibeCraftMod] Protocol version mismatch: server=" + payload.protocolVersion() + ", client=" + VibeCraftEventPayload.PROTOCOL_VERSION);
                    }
                    handleEvent(payload.json(), context.client());
                }));
    }

    // Event handler registry
    private static final Map<String, EventHandler> EVENT_HANDLERS = new HashMap<>();

    public interface EventHandler {
        void handle(JsonObject obj, net.minecraft.client.MinecraftClient client);
    }

    static {
        // Register default event handlers
        EVENT_HANDLERS.put("user_message", (obj, client) -> {
            String text = obj.has("text") ? obj.get("text").getAsString() : "";
            if (!text.isEmpty()) HudState.addLine("> " + text, ColorScheme.get(pluginFromEvent(obj)).user());
        });
        EVENT_HANDLERS.put("stream_start", (obj, client) -> {
            HudState.setStreaming(true);
            HudState.setCurrentTool(null, null);
            HudState.clearQuestion();
        });
        EVENT_HANDLERS.put("thinking", (obj, client) -> {
            String text = obj.has("text") ? obj.get("text").getAsString() : "";
            for (String l : text.split("\n")) {
                if (!l.isBlank()) HudState.addThought(l);
            }
        });
        EVENT_HANDLERS.put("question", (obj, client) -> {
            ColorScheme cs = ColorScheme.get(pluginFromEvent(obj));
            String prompt = obj.has("prompt") ? obj.get("prompt").getAsString() : "";
            java.util.List<String> opts = new java.util.ArrayList<>();
            if (obj.has("options")) {
                for (var el : obj.getAsJsonArray("options")) opts.add(el.getAsString());
            }
            HudState.addLine("? " + prompt, cs.question());
            for (int i = 0; i < opts.size(); i++) {
                HudState.addLine("  [" + (char)('A' + i) + "] " + opts.get(i), cs.claude());
            }
            HudState.setQuestion(new HudState.PendingQuestion(prompt, opts));
        });
        EVENT_HANDLERS.put("tool_call", (obj, client) -> {
            String tool   = obj.has("tool")   ? obj.get("tool").getAsString()   : "";
            String detail = obj.has("detail") ? obj.get("detail").getAsString() : "";
            HudState.setCurrentTool(tool, detail);
            HudState.addCollapsedLine("[" + tool + "] " + detail, ColorScheme.get(pluginFromEvent(obj)).tool());
        });
        EVENT_HANDLERS.put("claude_text", (obj, client) -> {
            HudState.setCurrentTool(null, null);
            JsonArray lines = obj.getAsJsonArray("lines");
            int claudeColor = ColorScheme.get(pluginFromEvent(obj)).claude();
            for (var el : lines) {
                HudState.addLine(stripLegacyFormatting(el.getAsString()), claudeColor);
            }
        });
        EVENT_HANDLERS.put("bash_output", (obj, client) -> {
            JsonArray lines = obj.getAsJsonArray("lines");
            int outputColor = ColorScheme.get(pluginFromEvent(obj)).output();
            for (var el : lines) {
                HudState.addLine(el.getAsString(), outputColor);
            }
        });
        EVENT_HANDLERS.put("stream_end", (obj, client) -> {
            HudState.setStreaming(false);
            HudState.setCurrentTool(null, null);
            HudState.setStreamEndTime(System.currentTimeMillis());
            CompletionToast.show(client);
        });
        EVENT_HANDLERS.put("ui_schema", (obj, client) -> {
            if (obj.has("schema") && obj.get("schema").isJsonObject()) {
                UiSchemaStore.setSchema(obj.getAsJsonObject("schema"));
            }
        });
        EVENT_HANDLERS.put("ui_schema_patch", (obj, client) -> {
            if (obj.has("patch") && obj.get("patch").isJsonObject()) {
                UiSchemaStore.patchSchema(obj.getAsJsonObject("patch"));
            }
        });
        EVENT_HANDLERS.put("binding_update", (obj, client) -> {
            if (!obj.has("binding")) return;
            String binding = obj.get("binding").getAsString();
            if (obj.has("value")) {
                OverlayDataBinding.update(binding, obj.get("value"));
            }
        });
        EVENT_HANDLERS.put("binding_updates", (obj, client) -> {
            if (!obj.has("values") || !obj.get("values").isJsonObject()) return;
            JsonObject values = obj.getAsJsonObject("values");
            for (var entry : values.entrySet()) {
                OverlayDataBinding.update(entry.getKey(), entry.getValue());
            }
        });
        EVENT_HANDLERS.put("open_screen", (obj, client) -> {
            String screenId = obj.has("screenId") ? obj.get("screenId").getAsString() : "";
            if (screenId.isBlank() || client == null) return;

            String currentScreenId = ScreenManager.getActiveScreenId();
            boolean sameScreenId = screenId.equals(currentScreenId);
            boolean uiAlreadyOpen = client.currentScreen instanceof SchemaScreen;
            long nowMs = System.currentTimeMillis();
            boolean rapidDuplicate = screenId.equals(lastOpenScreenId) && (nowMs - lastOpenScreenAtMs) < 500;
            lastOpenScreenId = screenId;
            lastOpenScreenAtMs = nowMs;

            if (uiAlreadyOpen && (sameScreenId || rapidDuplicate)) {
                if (com.example.vibecraftmod.DebugConfig.DEBUG_EVENTS) {
                    System.out.println("[VibeCraftMod] handleEvent: ignored duplicate open_screen for screenId=" + screenId);
                }
                return;
            }

            if (ScreenManager.setActiveScreen(screenId)) {
                client.setScreen(new SchemaScreen());
            } else if (com.example.vibecraftmod.DebugConfig.DEBUG_EVENTS) {
                System.out.println("[VibeCraftMod] handleEvent: unknown screenId in open_screen=" + screenId);
            }
        });
        EVENT_HANDLERS.put("close_screen", (obj, client) -> {
            if (client != null) {
                client.setScreen(null);
            }
        });
        EVENT_HANDLERS.put("history_chunk", (obj, client) -> {
            String plugin = pluginFromEvent(obj);
            java.util.List<JsonObject> historyBuffer = historyBuffersByPlugin.computeIfAbsent(plugin, k -> new java.util.ArrayList<>());
            JsonArray chunkEntries = obj.has("entries") ? obj.getAsJsonArray("entries") : new JsonArray();
            for (var el : chunkEntries) {
                if (el.isJsonObject()) historyBuffer.add(el.getAsJsonObject());
            }
            boolean done = obj.has("done") && obj.get("done").getAsBoolean();
            if (done) {
                if (!acceptSequence(plugin, "history", obj)) {
                    historyBuffersByPlugin.remove(plugin);
                    return;
                }
                JsonArray merged = new JsonArray();
                for (JsonObject e : historyBuffer) merged.add(e);
                historyBuffersByPlugin.remove(plugin);
                handleHistory(merged, plugin);
            }
        });
        EVENT_HANDLERS.put("history", (obj, client) -> {
            String plugin = pluginFromEvent(obj);
            if (!acceptSequence(plugin, "history", obj)) return;
            handleHistory(obj.getAsJsonArray("entries"), plugin);
        });
        EVENT_HANDLERS.put("settings", (obj, client) -> {
            Map<String, String> vals = new HashMap<>();
            String plugin = pluginFromEvent(obj);
            if (!acceptSequence(plugin, "settings", obj)) return;
            JsonObject vs = obj.getAsJsonObject("values");
            for (var entry : vs.entrySet()) {
                vals.put(entry.getKey(), entry.getValue().getAsString());
            }
            ModSettings.applyAllForPlugin(plugin, vals);
        });
        EVENT_HANDLERS.put("ef_highlight_entities", (obj, client) -> {
            java.util.List<Integer> ids = new java.util.ArrayList<>();
            if (obj.has("entities") && obj.get("entities").isJsonArray()) {
                for (var el : obj.getAsJsonArray("entities")) {
                    try { ids.add(el.getAsInt()); } catch (Exception ignored) {}
                }
            }
            EntityHighlightManager.update(ids);
        });
        // ...register more default handlers as needed...
    }

    public static void registerEventHandler(String type, EventHandler handler) {
        EVENT_HANDLERS.put(type, handler);
    }

    private static void handleEvent(String json, net.minecraft.client.MinecraftClient client) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String type = obj.get("type").getAsString();

            if (com.example.vibecraftmod.DebugConfig.DEBUG_EVENTS) {
                if ("open_screen".equals(type)) {
                    String screenId = obj.has("screenId") ? obj.get("screenId").getAsString() : "";
                    String ns = obj.has("namespace") ? obj.get("namespace").getAsString() : "";
                    System.out.println("[VibeCraftMod] handleEvent: open_screen for screenId=" + screenId + (ns.isBlank() ? "" : " namespace=" + ns));
                }
                if ("close_screen".equals(type)) {
                    System.out.println("[VibeCraftMod] handleEvent: close_screen");
                }
            }

            EventHandler handler = EVENT_HANDLERS.get(type);
            if (handler != null) {
                handler.handle(obj, client);
            } else {
                String ns = obj.has("namespace") ? obj.get("namespace").getAsString() : "";
                System.err.println("[VibeCraftMod] Unknown event type: " + type + (ns.isBlank() ? "" : " namespace=" + ns));
            }
        } catch (Exception e) {
            System.err.println("[VibeCraftMod] handleEvent exception: " + e);
        }
    }

    private static void handleHistory(JsonArray entries, String plugin) {
        if (entries == null) return;
        ColorScheme cs = ColorScheme.get(plugin);
        java.util.List<HudState.HudLine> lines = new java.util.ArrayList<>();
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
                        HudState.HudLine last = lines.get(lines.size() - 1);
                        if (last.text().equals(lineText) && last.color() == cs.user()) break;
                    }
                    lines.add(new HudState.HudLine(lineText, cs.user()));
                }
                case "CLAUDE" -> {
                    for (String l : body.split("\n")) {
                        l = l.stripTrailing();
                        if (!l.isEmpty()) lines.addAll(parseMarkdownLine(l, cs));
                    }
                }
                case "THINKING" -> {
                    for (String l : body.split("\n")) {
                        l = l.stripTrailing();
                        if (!l.isEmpty()) lines.add(new HudState.HudLine(l, 0x888899, true, false));
                    }
                }
                case "TOOL" -> {
                    String[] parts = header.split("\\|", 2);
                    String tool   = parts.length > 0 ? parts[0] : "";
                    String detail = parts.length > 1 ? parts[1] : "";
                    lines.add(new HudState.HudLine("[" + tool + "] " + detail, cs.tool(), false, true));
                }
                case "BASH" -> {
                    for (String l : body.split("\n")) {
                        lines.add(new HudState.HudLine(l, cs.output()));
                    }
                }
                case "SYSTEM" -> lines.add(new HudState.HudLine(header, cs.system()));
            }
        }
        HudState.replaceWithHistory(lines);
    }

    public static void sendInput(String message) {
        sendInput(message, resolveDefaultPlugin());
    }

    public static void sendInput(String message, String plugin) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "message");
        obj.addProperty("plugin", plugin);
        obj.addProperty("namespace", plugin);
        obj.addProperty("message", message);
        ClientPlayNetworking.send(new VibeCraftInputPayload(obj.toString(), VibeCraftInputPayload.PROTOCOL_VERSION));
    }

    public static void requestHistory() {
        requestHistory(resolveDefaultPlugin());
    }

    public static void requestHistory(String plugin) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "request_history");
        obj.addProperty("plugin", plugin);
        obj.addProperty("namespace", plugin);
        ClientPlayNetworking.send(new VibeCraftInputPayload(obj.toString(), VibeCraftInputPayload.PROTOCOL_VERSION));
    }

    public static void sendSetSetting(String key, String value) {
        sendSetSetting(resolveDefaultPlugin(), key, value);
    }

    public static void sendSetSetting(String plugin, String key, String value) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "set_setting");
        obj.addProperty("plugin", plugin);
        obj.addProperty("namespace", plugin);
        obj.addProperty("key", key);
        obj.addProperty("value", value);
        ClientPlayNetworking.send(new VibeCraftInputPayload(obj.toString(), VibeCraftInputPayload.PROTOCOL_VERSION));
    }

    public static void clearHistory() {
        clearHistory(resolveDefaultPlugin());
    }

    private static String resolveDefaultPlugin() {
        String active = ScreenManager.getActivePlugin();
        if (active != null && !active.isBlank()) return active;
        
        // Use schema-defined default plugin instead of hardcoded "vibecraft"
        String schemaDefault = com.example.vibecraftmod.ui.SchemaConfig.getDefaultPlugin();
        if (schemaDefault != null && !schemaDefault.isBlank()) return schemaDefault;
        
        // Final fallback only if schema not available
        return "vibecraft";
    }

    private static String pluginFromEvent(JsonObject obj) {
        if (obj != null) {
            if (obj.has("plugin") && obj.get("plugin").isJsonPrimitive()) {
                String plugin = obj.get("plugin").getAsString();
                if (!plugin.isBlank()) return plugin;
            }
            if (obj.has("namespace") && obj.get("namespace").isJsonPrimitive()) {
                String namespace = obj.get("namespace").getAsString();
                if (!namespace.isBlank()) return namespace;
            }
        }
        return resolveDefaultPlugin();
    }

    private static boolean acceptSequence(String plugin, String channel, JsonObject obj) {
        Long seq = eventSequence(obj);
        if (seq == null) return true;
        String key = plugin + "|" + channel;
        Long last = lastSequenceByPluginAndChannel.get(key);
        if (last != null && seq <= last) {
            return false;
        }
        lastSequenceByPluginAndChannel.put(key, seq);
        return true;
    }

    private static Long eventSequence(JsonObject obj) {
        if (obj == null) return null;
        for (String key : new String[] {"seq", "sequence", "revision", "version"}) {
            if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                try {
                    return obj.get(key).getAsLong();
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public static void clearHistory(String plugin) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "clear_history");
        obj.addProperty("plugin", plugin);
        obj.addProperty("namespace", plugin);
        ClientPlayNetworking.send(new VibeCraftInputPayload(obj.toString(), VibeCraftInputPayload.PROTOCOL_VERSION));
    }

    private static java.util.List<HudState.HudLine> parseMarkdownLine(String line, ColorScheme cs) {
        if (line.startsWith("### ")) return List.of(new HudState.HudLine(line.substring(4), cs.question()));
        if (line.startsWith("## "))  return List.of(new HudState.HudLine(line.substring(3), cs.question()));
        if (line.startsWith("# "))   return List.of(new HudState.HudLine(line.substring(2), cs.question()));
        if (line.startsWith("> "))   return List.of(new HudState.HudLine("> " + line.substring(2), cs.user()));
        if (line.equals(">"))        return List.of(new HudState.HudLine(">", cs.user()));
        if (line.startsWith("- ") || line.startsWith("* "))
            return List.of(new HudState.HudLine("• " + line.substring(2), cs.claude()));
        if (line.equals("---") || line.equals("***") || line.equals("___"))
            return List.of(new HudState.HudLine("────────────────────", cs.system()));
        return List.of(new HudState.HudLine(line, cs.claude()));
    }

    private static String stripLegacyFormatting(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }
}

