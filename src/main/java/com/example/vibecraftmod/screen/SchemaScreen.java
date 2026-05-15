package com.example.vibecraftmod.screen;

import com.example.vibecraftmod.DebugConfig;
import com.example.vibecraftmod.config.ClientConfig;
import com.example.vibecraftmod.config.PluginConfig;
import com.example.vibecraftmod.hud.HudState;
import com.example.vibecraftmod.network.ModPackets;
import com.example.vibecraftmod.settings.ModSettings;
import com.example.vibecraftmod.settings.ColorScheme;
import com.example.vibecraftmod.ui.SchemaConfig;
import com.example.vibecraftmod.ui.ScreenDef;
import com.example.vibecraftmod.ui.ScreenManager;
import com.example.vibecraftmod.ui.UiSchemaStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SchemaScreen extends Screen {

    private static final int Z_BASE = 100;
    private static final int Z_DROPDOWN = 200;
    private static final int Z_MODAL_BACKDROP = 900;
    private static final int Z_MODAL = 1000;

    @FunctionalInterface
    private interface InternalAction {
        void run(SchemaScreen screen, JsonObject action, String optionValue, String targetPlugin);
    }

    @FunctionalInterface
    private interface WidgetRenderer {
        void render(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                    int titleHeight, int colLabel, int y, int h, int mouseX, int mouseY);
    }

    private static final Map<String, InternalAction> INTERNAL_ACTIONS = new HashMap<>();

    static {
        // Standard UI control actions (plugin-agnostic)
        INTERNAL_ACTIONS.put("close_screen", (screen, action, optionValue, targetPlugin) -> screen.close());
        INTERNAL_ACTIONS.put("open_options", (screen, action, optionValue, targetPlugin) -> {
            if (screen.client != null) screen.client.setScreen(new SchemaSettingsScreen(screen));
        });
        INTERNAL_ACTIONS.put("open_help", (screen, action, optionValue, targetPlugin) -> screen.openHelpModal());
        INTERNAL_ACTIONS.put("open_modal", (screen, action, optionValue, targetPlugin) -> {
            String id = strVal(action, "id", "");
            if (!id.isBlank()) screen.activeModalId = id;
        });
        INTERNAL_ACTIONS.put("close_modal", (screen, action, optionValue, targetPlugin) -> screen.activeModalId = null);
        INTERNAL_ACTIONS.put("toggle_dropdown", (screen, action, optionValue, targetPlugin) -> {
            String id = strVal(action, "id", "");
            if (!id.isBlank()) {
                screen.activeDropdownId = id.equals(screen.activeDropdownId) ? null : id;
            }
        });
        
        // Server communication actions
        INTERNAL_ACTIONS.put("request_history", (screen, action, optionValue, targetPlugin) -> ModPackets.requestHistory(targetPlugin));
        INTERNAL_ACTIONS.put("refresh_schema", (screen, action, optionValue, targetPlugin) -> ModPackets.requestHistory(targetPlugin));
        
        // Rendering/display actions (plugin-specific: targets HudState and ColorScheme)
        // These assume a HUD rendering layer is present; other plugins may define alternatives
        INTERNAL_ACTIONS.put("clear_history", (screen, action, optionValue, targetPlugin) -> screen.clearHistoryViewAndServer(targetPlugin));
        INTERNAL_ACTIONS.put("clear_input", (screen, action, optionValue, targetPlugin) -> {
            screen.inputText = "";
            screen.cursor = 0;
            screen.inputScrollOffset = 0;
        });
        INTERNAL_ACTIONS.put("send_message", (screen, action, optionValue, targetPlugin) -> {
            String text = screen.resolveActionText(strVal(action, "text", "${option}"), optionValue);
            if (!text.isBlank()) {
                HudState.addLine("> " + text, ColorScheme.get(targetPlugin).user());
                ModPackets.sendInput(text, targetPlugin);
                if (optionValue != null) HudState.clearQuestion();
            }
            screen.activeDropdownId = null;
        });
        INTERNAL_ACTIONS.put("append_input", (screen, action, optionValue, targetPlugin) -> {
            String value = screen.resolveActionText(strVal(action, "text", ""), optionValue);
            if (!value.isEmpty()) {
                screen.inputText = screen.inputText.substring(0, screen.cursor) + value + screen.inputText.substring(screen.cursor);
                screen.cursor += value.length();
            }
        });
        INTERNAL_ACTIONS.put("replace_input", (screen, action, optionValue, targetPlugin) -> {
            screen.inputText = screen.resolveActionText(strVal(action, "text", ""), optionValue);
            screen.cursor = screen.inputText.length();
        });
        INTERNAL_ACTIONS.put("submit_input", (screen, action, optionValue, targetPlugin) -> {
            String text = screen.inputText.trim();
            if (!text.isEmpty()) {
                HudState.addLine("> " + text, ColorScheme.get(targetPlugin).user());
                ModPackets.sendInput(text, targetPlugin);
                screen.inputText = "";
                screen.cursor = 0;
                screen.inputScrollOffset = 0;
            }
        });
        INTERNAL_ACTIONS.put("set_setting", (screen, action, optionValue, targetPlugin) -> {
            String key = strVal(action, "key", "");
            String value = screen.resolveActionText(strVal(action, "value", ""), optionValue);
            if (!key.isBlank()) {
                ModSettings.setForPlugin(targetPlugin, key, value);
                ModPackets.sendSetSetting(targetPlugin, key, value);
            }
        });
        INTERNAL_ACTIONS.put("toggle_setting", (screen, action, optionValue, targetPlugin) -> {
            String key = strVal(action, "key", "");
            if (!key.isBlank()) {
                boolean current = ModSettings.getBoolForPlugin(targetPlugin, key);
                String value = current
                        ? strVal(action, "falseValue", "false")
                        : strVal(action, "trueValue", "true");
                ModSettings.setForPlugin(targetPlugin, key, value);
                ModPackets.sendSetSetting(targetPlugin, key, value);
            }
        });
        INTERNAL_ACTIONS.put("set_tab", (screen, action, optionValue, targetPlugin) -> {
            String container = strVal(action, "container", "tabs");
            int index = intVal(action, "tabIndex", 0);
            screen.selectedTabByContainer.put(container, Math.max(0, index));
        });
        INTERNAL_ACTIONS.put("toggle_schema_error", (screen, action, optionValue, targetPlugin) -> {
            screen.forceSchemaErrorView = !screen.forceSchemaErrorView;
            screen.activeDropdownId = null;
        });
    }

    private record ClickTarget(int x, int y, int w, int h, JsonObject action, String optionValue, int z, long seq) {}

    private String inputText = "";
    private int cursor = 0;
    private int scrollOffset = 0;
    private int historyTop = 0;
    private int historyBottom = 0;
    private int inputTop = 0;
    private int inputBottom = 0;
    private int inputScrollOffset = 0;
    private int historyLineHeight = 11;
    private final List<ClickTarget> clickTargets = new ArrayList<>();
    private long clickSeq = 0;
    private final Map<String, Integer> selectedTabByContainer = new HashMap<>();
        private final Map<String, WidgetRenderer> widgetRenderers = new HashMap<>();
    private String activeModalId = null;
    private String activeDropdownId = null;
    private boolean forceSchemaErrorView = false;

    public SchemaScreen() {
        super(Text.literal("Plugin Screen"));
        registerDefaultWidgetRenderers();
        // Preserve server-selected screen when opened via open_screen.
        ScreenManager.getActiveScreen();
    }

        private void registerDefaultWidgetRenderers() {
        widgetRenderers.put("toolbar", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            renderToolbar(ctx, w, panelX, panelW, panelPadding, y, titleHeight, mouseX, mouseY));
        widgetRenderers.put("history", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            renderHistory(ctx, w, panelX, panelW, panelPadding, y, h, mouseX, mouseY));
        widgetRenderers.put("question_options", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            renderQuestionOptions(ctx, w, panelX, panelW, panelPadding, y, mouseX, mouseY));
        widgetRenderers.put("input", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            renderInput(ctx, w, panelX, panelW, panelPadding, y, h, colLabel));
        widgetRenderers.put("hint", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            renderHint(ctx, w, panelX, panelPadding, y));
        widgetRenderers.put("text", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            renderText(ctx, w, panelX, panelW, panelPadding, y));
        widgetRenderers.put("action_row", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            renderActionRow(ctx, w, panelX, panelW, panelPadding, y, mouseX, mouseY));
        widgetRenderers.put("dropdown", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            renderDropdown(ctx, w, panelX, panelW, panelPadding, y, mouseX, mouseY, colLabel));
        widgetRenderers.put("setting_toggle", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            renderSettingToggle(ctx, w, panelX, panelW, panelPadding, y, mouseX, mouseY));
        widgetRenderers.put("state_badge", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            renderStateBadge(ctx, w, panelX, panelW, panelPadding, y));
        widgetRenderers.put("tab_container", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            renderTabContainer(ctx, w, panelX, panelW, panelPadding, y, h, mouseX, mouseY, colLabel));
        widgetRenderers.put("divider", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            ctx.fill(panelX + panelPadding, y, panelX + panelW - panelPadding, y + 1,
                colorVal(w, "color", 0xFF2A2A3A)));
        widgetRenderers.put("spacer", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) -> {
        });
        }

        private void renderWidgetByType(DrawContext ctx, JsonObject widget, String type,
                        int panelX, int panelW, int panelPadding, int titleHeight, int colLabel,
                        int y, int h, int mouseX, int mouseY) {
        WidgetRenderer renderer = widgetRenderers.get(type);
        if (renderer == null) return;
        renderer.render(ctx, widget, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY);
        }

    @Override
    protected void init() {
        ScreenDef activeScreen = ScreenManager.getActiveScreen();
        String activePlugin = activeScreen != null ? activeScreen.plugin : defaultPlugin();
        ModPackets.requestHistory(activePlugin);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        clickTargets.clear();
        clickSeq = 0;

        if (forceSchemaErrorView || !UiSchemaStore.hasSchema()) {
            renderSchemaMissing(ctx, mouseX, mouseY);
            super.render(ctx, mouseX, mouseY, delta);
            return;
        }

        // Get active screen and its plugin
        ScreenDef activeScreen = ScreenManager.getActiveScreen();
        if (activeScreen == null) {
            renderSchemaMissing(ctx, mouseX, mouseY);
            super.render(ctx, mouseX, mouseY, delta);
            return;
        }
        // title is set in constructor: super(Text.literal(activeScreen.title))

        JsonObject schema = UiSchemaStore.getSchema();
        JsonObject panel = (activeScreen.panel != null && !activeScreen.panel.entrySet().isEmpty())
            ? activeScreen.panel
            : obj(schema, "panel");
        JsonArray widgets = activeScreen.widgets != null && !activeScreen.widgets.isEmpty()
            ? activeScreen.widgets
            : UiSchemaStore.widgets();

        int panelW = Math.min(intVal(panel, "maxWidth", 520), (int) (width * doubleVal(panel, "widthPercent", 0.70)));
        int panelX = (width - panelW) / 2;
        int panelPadding = intVal(panel, "padding", 8);
        int titleHeight = intVal(panel, "titleHeight", 14);
        int colBg = colorVal(panel, "background", 0xDD0A0A0F);
        int colTitleBg = colorVal(panel, "titleBackground", 0xFF111118);
        int colDivider = colorVal(panel, "divider", 0xFF2A2A3A);
        int colLabel = colorVal(panel, "label", 0xFFCCCCCC);

        ctx.fill(panelX, 0, panelX + panelW, height, colBg);
        ctx.fill(panelX, 0, panelX + panelW, titleHeight, colTitleBg);

        String title = (activeScreen.title != null && !activeScreen.title.isBlank())
            ? activeScreen.title
            : strVal(schema, "title", "VibeCraft Chat");
        ctx.drawText(textRenderer, Text.literal("◆ " + title).formatted(Formatting.AQUA),
                panelX + panelPadding, 3, colLabel, false);

        int y = titleHeight;
        int fixedHeight = 0;
        int flexCount = 0;
        for (int i = 0; i < widgets.size(); i++) {
            JsonObject w = widgets.get(i).getAsJsonObject();
            String type = strVal(w, "type", "");
            if ("modal".equals(type)) continue;
            if ("history".equals(type) && boolVal(w, "flex", false)) {
                flexCount++;
                continue;
            }
            fixedHeight += widgetHeight(w, type, panelW, panelPadding);
        }
        int remaining = Math.max(120, height - titleHeight - fixedHeight - panelPadding);
        int flexHeight = flexCount == 0 ? 0 : remaining / flexCount;

        for (int i = 0; i < widgets.size(); i++) {
            JsonObject w = widgets.get(i).getAsJsonObject();
            String type = strVal(w, "type", "");
            if ("modal".equals(type)) continue;
            int wh = ("history".equals(type) && boolVal(w, "flex", false)) ? flexHeight : widgetHeight(w, type, panelW, panelPadding);
            renderWidgetByType(ctx, w, type, panelX, panelW, panelPadding, titleHeight, colLabel, y, wh, mouseX, mouseY);

            y += wh;
        }

        for (int i = 0; i < widgets.size(); i++) {
            JsonObject w = widgets.get(i).getAsJsonObject();
            if (!"modal".equals(strVal(w, "type", ""))) continue;
            String modalId = strVal(w, "id", "default-modal");
            boolean open = boolVal(w, "open", false) || modalId.equals(activeModalId);
            if (open) {
                renderModal(ctx, w, mouseX, mouseY, colLabel);
                break;
            }
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

        private void renderSchemaMissing(DrawContext ctx, int mouseX, int mouseY) {
        int panelW = Math.min(520, (int) (width * 0.70));
        int panelX = (width - panelW) / 2;
        int panelY = Math.max(20, (height - 130) / 2);
        int panelH = 130;

        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xDD0A0A0F);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 16, 0xFF111118);

        ctx.drawText(textRenderer, Text.literal("◆ UI Schema Missing").formatted(Formatting.RED),
            panelX + 10, panelY + 4, 0xFFFFFFFF, false);
        ctx.drawText(textRenderer, "No server UI schema is currently loaded.",
            panelX + 10, panelY + 28, 0xFFDDDDDD, false);
        ctx.drawText(textRenderer, "Reconnect or trigger a schema sync from server.",
            panelX + 10, panelY + 41, 0xFF9AA6C2, false);
        if (forceSchemaErrorView) {
            ctx.drawText(textRenderer, "Forced test mode is active.",
                panelX + 10, panelY + 62, 0xFFE3A55A, false);
            String label = "[Back]";
            int bw = textRenderer.getWidth(label) + 12;
            int bx = panelX + panelW - 10 - bw;
            int by = panelY + panelH - 24;
            boolean hov = inBox(mouseX, mouseY, bx, by, bw, 16);
            drawOutlinedBox(ctx, bx, by, bx + bw, by + 16, hov ? 0xFF1E3A5F : 0xFF142033,
                    hov ? 0xFF4A86C8 : 0x553A5A80, 1);
            ctx.drawText(textRenderer, label, bx + 6, by + 4, hov ? 0xFFFFFFFF : 0xFFCCCCCC, false);
            addClickTarget(bx, by, bw, 16, internalAction("toggle_schema_error"), null, Z_MODAL);
        } else {
            ctx.drawText(textRenderer, "Press Esc to close this panel.",
                panelX + 10, panelY + 62, 0xFF888899, false);
        }
        }

    private void renderToolbar(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                               int y, int titleHeight, int mouseX, int mouseY) {
        String closeLabel = strVal(widget, "closeLabel", "[x]");
        String optionsLabel = strVal(widget, "optionsLabel", "[options]");
        String helpLabel = strVal(widget, "helpLabel", "[help]");
        boolean showClose = boolVal(widget, "showClose", true);
        boolean showOptions = boolVal(widget, "showOptions", true);
        boolean showHelp = boolVal(widget, "showHelp", false);
        boolean showErrorTest = boolVal(widget, "showErrorTest", false);
        boolean showInlineStatus = boolVal(widget, "showInlineStatus", true);
        int btnBg = colorVal(widget, "buttonBg", 0xFF142033);
        int btnHover = colorVal(widget, "buttonHover", 0xFF1E3A5F);
        int btnDanger = colorVal(widget, "buttonDanger", 0xFF3A1010);
        int btnDangerHover = colorVal(widget, "buttonDangerHover", 0xFF5A1818);
        int btnPaddingX = intVal(widget, "buttonPaddingX", 5);
        int statusColor = colorVal(widget, "statusColor", 0xFF96A6C2);
        int textColor = colorVal(widget, "textColor", 0xFF9CCBFF);
        int textHoverColor = colorVal(widget, "textHoverColor", 0xFFFFFFFF);
        int dangerTextColor = colorVal(widget, "dangerTextColor", 0xFFAAAAAA);
        int dangerTextHoverColor = colorVal(widget, "dangerTextHoverColor", 0xFFFF7777);
        int btnOutline = colorVal(widget, "buttonOutline", 0x553A5A80);
        int btnHoverOutline = colorVal(widget, "buttonHoverOutline", 0xFF4A86C8);
        int btnDangerOutline = colorVal(widget, "buttonDangerOutline", 0x775A2020);
        int btnDangerHoverOutline = colorVal(widget, "buttonDangerHoverOutline", 0xFFAA4545);
        int outlineWidth = intVal(widget, "outlineWidth", 1);

        int xRight = panelX + panelW - panelPadding;
        if (showClose) {
            int w = textRenderer.getWidth(closeLabel);
            int x = xRight - w;
            boolean hover = inBox(mouseX, mouseY, x - btnPaddingX, y + 1, w + btnPaddingX * 2, titleHeight - 2);
            int x1 = x - btnPaddingX;
            int y1 = y + 1;
            int x2 = x + w + btnPaddingX;
            int y2 = y + titleHeight - 1;
            drawOutlinedBox(ctx, x1, y1, x2, y2, hover ? btnDangerHover : btnDanger,
                    hover ? btnDangerHoverOutline : btnDangerOutline, outlineWidth);
            ctx.drawText(textRenderer, closeLabel,
                    x, y + 3, hover ? dangerTextHoverColor : dangerTextColor, false);
            JsonObject closeAction = obj(widget, "closeAction");
            if (closeAction.entrySet().isEmpty()) closeAction = action("close_screen");
            addClickTarget(x - btnPaddingX, y + 1, w + btnPaddingX * 2, titleHeight - 2, closeAction, null, intVal(widget, "z", Z_BASE));
            xRight = x - 8;
        }
        if (showHelp) {
            int w = textRenderer.getWidth(helpLabel);
            int x = xRight - w;
            boolean hover = inBox(mouseX, mouseY, x - btnPaddingX, y + 1, w + btnPaddingX * 2, titleHeight - 2);
            int x1 = x - btnPaddingX;
            int y1 = y + 1;
            int x2 = x + w + btnPaddingX;
            int y2 = y + titleHeight - 1;
            drawOutlinedBox(ctx, x1, y1, x2, y2, hover ? btnHover : btnBg,
                    hover ? btnHoverOutline : btnOutline, outlineWidth);
            ctx.drawText(textRenderer, helpLabel,
                    x, y + 3, hover ? textHoverColor : textColor, false);
            JsonObject helpAction = obj(widget, "helpAction");
            if (helpAction.entrySet().isEmpty()) {
                helpAction = action("open_modal");
                helpAction.addProperty("id", "help-modal");
            }
            addClickTarget(x - btnPaddingX, y + 1, w + btnPaddingX * 2, titleHeight - 2, helpAction, null, intVal(widget, "z", Z_BASE));
            xRight = x - 8;
        }
        if (showOptions) {
            int w = textRenderer.getWidth(optionsLabel);
            int x = xRight - w;
            boolean hover = inBox(mouseX, mouseY, x - btnPaddingX, y + 1, w + btnPaddingX * 2, titleHeight - 2);
            int x1 = x - btnPaddingX;
            int y1 = y + 1;
            int x2 = x + w + btnPaddingX;
            int y2 = y + titleHeight - 1;
            drawOutlinedBox(ctx, x1, y1, x2, y2, hover ? btnHover : btnBg,
                    hover ? btnHoverOutline : btnOutline, outlineWidth);
            ctx.drawText(textRenderer, optionsLabel,
                    x, y + 3, hover ? textHoverColor : textColor, false);
            JsonObject optionsAction = obj(widget, "optionsAction");
            if (optionsAction.entrySet().isEmpty()) optionsAction = action("open_options");
            addClickTarget(x - btnPaddingX, y + 1, w + btnPaddingX * 2, titleHeight - 2, optionsAction, null, intVal(widget, "z", Z_BASE));
            xRight = x - 8;
        }
            if (showErrorTest) {
                String errorLabel = forceSchemaErrorView ? "[ui ok]" : "[ui err]";
                int w = textRenderer.getWidth(errorLabel);
                int x = xRight - w;
                boolean hover = inBox(mouseX, mouseY, x - btnPaddingX, y + 1, w + btnPaddingX * 2, titleHeight - 2);
                int x1 = x - btnPaddingX;
                int y1 = y + 1;
                int x2 = x + w + btnPaddingX;
                int y2 = y + titleHeight - 1;
                drawOutlinedBox(ctx, x1, y1, x2, y2, hover ? btnHover : btnBg,
                    hover ? btnHoverOutline : btnOutline, outlineWidth);
                ctx.drawText(textRenderer, errorLabel,
                    x, y + 3, hover ? textHoverColor : textColor, false);
                addClickTarget(x - btnPaddingX, y + 1, w + btnPaddingX * 2, titleHeight - 2,
                        internalAction("toggle_schema_error"), null, intVal(widget, "z", Z_BASE));
                xRight = x - 8;
            }

        if (showInlineStatus) {
            String statusTemplate = strVal(widget, "statusText", "").trim();
            String status;
            if (!statusTemplate.isEmpty()) {
                status = resolveTemplate(statusTemplate);
            } else {
                String tool = HudState.getCurrentTool();
                String idleLabel = strVal(widget, "idleLabel", "Idle");
                String toolLabel = tool == null ? idleLabel : "[" + tool + "]";
                status = toolLabel + "  History: " + HudState.getLines().size() + "  Streaming: " + HudState.isStreaming();
            }
            String clipped = truncate(status, Math.max(40, xRight - (panelX + panelPadding)));
            ctx.drawText(textRenderer, clipped, panelX + panelPadding, y + 3, statusColor, false);
        }
    }

    private void renderHistory(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                               int y, int h, int mouseX, int mouseY) {
        int lineHeight = intVal(widget, "lineHeight", 11);
        historyLineHeight = lineHeight;
        boolean showThoughts = boolVal(widget, "showThoughts", ModSettings.getBoolForPlugin(defaultPlugin(), "ui.thoughts_visible"));
        boolean truncateCollapsed = boolVal(widget, "truncateCollapsed", false);
        boolean truncateIndicator = boolVal(widget, "truncateIndicator", false);
        int scrollbarW = intVal(widget, "scrollbarWidth", 3);
        int textW = panelW - panelPadding * 2 - scrollbarW;
        int historyBg = colorVal(widget, "background", 0x00000000);
        int indicatorColor = colorVal(widget, "indicatorColor", ColorScheme.get(defaultPlugin()).tool());

        if ((historyBg >>> 24) != 0) {
            ctx.fill(panelX + panelPadding, y, panelX + panelW - panelPadding, y + h, historyBg);
        }

        List<HudState.HudLine> lines = HudState.getLines();
        List<HudState.HudLine> display = new ArrayList<>();
        int toolColor = ColorScheme.get(defaultPlugin()).tool();
        for (HudState.HudLine line : lines) {
            if (line.thought() && !showThoughts && !HudState.isStreaming()) continue;
            boolean isToolCallRow = line.color() == toolColor && line.text().startsWith("[");
            if (line.collapsed() && isToolCallRow && truncateCollapsed) {
                display.add(new HudState.HudLine(truncate(line.text(), textW), line.color(), line.thought(), true));
                continue;
            }
            List<String> wrapped = wrapTextRows(line.text(), textW);
            for (String row : wrapped) {
                display.add(new HudState.HudLine(row, line.color(), line.thought(), false));
            }
        }

        String indicator = currentIndicator();
        if (!indicator.isEmpty()) {
            if (truncateIndicator) {
                display.add(new HudState.HudLine(truncate(indicator, textW), indicatorColor, false, true));
            } else {
                List<String> wrapped = wrapTextRows(indicator, textW);
                for (String row : wrapped) {
                    display.add(new HudState.HudLine(row, indicatorColor, false, true));
                }
            }
        }

        int textX = panelX + panelPadding;
        int visible = Math.max(1, h / lineHeight);
        int maxScroll = Math.max(0, display.size() - visible);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        int start = Math.max(0, display.size() - visible - scrollOffset);
        int end = Math.min(display.size(), start + visible);

        int ty = y;
        for (int i = start; i < end; i++) {
            HudState.HudLine line = display.get(i);
            ctx.drawText(textRenderer, line.text(), textX, ty, 0xFF000000 | (line.color() & 0x00FFFFFF), false);
            ty += lineHeight;
        }

        if (display.size() > visible) {
            int sbX = panelX + panelW - panelPadding - scrollbarW;
            int trackTop = y;
            int trackBottom = y + h;
            int trackH = trackBottom - trackTop;
            int thumbH = Math.max(8, trackH * visible / display.size());
            int thumbY = trackTop + (maxScroll > 0 ? (trackH - thumbH) * start / maxScroll : 0);
            int track = colorVal(widget, "scrollTrack", 0xFF1A1A28);
            int thumb = colorVal(widget, "scrollThumb", 0xFF4A4A6A);
            int active = colorVal(widget, "scrollActive", 0xFF7070A0);
            boolean hover = inBox(mouseX, mouseY, sbX, thumbY, scrollbarW, thumbH);
            ctx.fill(sbX, trackTop, sbX + scrollbarW, trackBottom, track);
            ctx.fill(sbX, thumbY, sbX + scrollbarW, thumbY + thumbH, hover ? active : thumb);
        }

        historyTop = y;
        historyBottom = y + h;
    }

    private void renderQuestionOptions(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                                       int y, int mouseX, int mouseY) {
        HudState.PendingQuestion q = HudState.getQuestion();
        if (q == null || q.options().isEmpty()) return;

        int buttonH = intVal(widget, "buttonHeight", 16);
        int gap = intVal(widget, "buttonGap", 2);
        int btnBg = colorVal(widget, "buttonBg", 0xFF142033);
        int btnHover = colorVal(widget, "buttonHover", 0xFF1E3A5F);
        int textColor = colorVal(widget, "textColor", 0xFFFFFFFF);
        int outline = colorVal(widget, "buttonOutline", 0x553A5A80);
        int hoverOutline = colorVal(widget, "buttonHoverOutline", 0xFF4A86C8);
        int outlineWidth = intVal(widget, "outlineWidth", 1);

        for (int i = 0; i < q.options().size(); i++) {
            int top = y + i * (buttonH + gap);
            boolean hover = inBox(mouseX, mouseY, panelX + panelPadding, top, panelW - panelPadding * 2, buttonH);
            int x1 = panelX + panelPadding;
            int x2 = panelX + panelW - panelPadding;
            drawOutlinedBox(ctx, x1, top, x2, top + buttonH, hover ? btnHover : btnBg,
                    hover ? hoverOutline : outline, outlineWidth);
            String option = q.options().get(i);
            ctx.drawText(textRenderer, "[" + (char)('A' + i) + "] " + option,
                    panelX + panelPadding + 6, top + 4, textColor, false);
            JsonObject action = obj(widget, "action");
            if (action.entrySet().isEmpty()) action = action("send_message");
                addClickTarget(panelX + panelPadding, top, panelW - panelPadding * 2, buttonH,
                    action, option, intVal(widget, "z", Z_BASE));
        }
    }

    private void renderText(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding, int y) {
        String text = resolveTemplate(strVal(widget, "text", ""));
        int color = colorVal(widget, "color", 0xFFCCCCCC);
        boolean centered = boolVal(widget, "center", false);
        boolean wrap = boolVal(widget, "wrap", false);
        int x = centered ? panelX + (panelW - textRenderer.getWidth(text)) / 2 : panelX + panelPadding;
        if (!wrap) {
            ctx.drawText(textRenderer, text, x, y + 2, color, false);
            return;
        }

        int maxW = Math.max(40, panelW - panelPadding * 2);
        int ty = y + 2;
        for (String paragraph : text.split("\\n", -1)) {
            if (paragraph.isEmpty()) {
                ty += historyLineHeight;
                continue;
            }
            for (String row : wrapParagraphByWords(paragraph, maxW)) {
                int rowX = centered ? panelX + (panelW - textRenderer.getWidth(row)) / 2 : x;
                ctx.drawText(textRenderer, row, rowX, ty, color, false);
                ty += historyLineHeight;
            }
        }
    }

    private void renderStateBadge(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding, int y) {
        String bind = strVal(widget, "bind", "streaming");
        String text;
        if ("current_tool".equals(bind)) {
            String tool = HudState.getCurrentTool();
            String detail = HudState.getCurrentToolDetail();
            text = tool == null ? strVal(widget, "emptyText", "Idle") : "[" + tool + "] " + (detail == null ? "" : detail);
        } else {
            text = HudState.isStreaming()
                    ? strVal(widget, "trueText", "Streaming")
                    : strVal(widget, "falseText", "Idle");
        }
        int bg = colorVal(widget, "background", 0xFF142033);
        int fg = colorVal(widget, "color", 0xFFFFFFFF);
        int x = panelX + panelPadding;
        int w = textRenderer.getWidth(text) + 12;
        int h = intVal(widget, "height", 14);
        ctx.fill(x, y + 1, x + w, y + h - 1, bg);
        ctx.drawText(textRenderer, text, x + 6, y + (h - 8) / 2, fg, false);
    }

    private void renderActionRow(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                                 int y, int mouseX, int mouseY) {
        JsonArray buttons = arr(widget, "buttons");
        if (buttons.isEmpty()) return;

        int h = intVal(widget, "height", 20);
        int gap = intVal(widget, "gap", 4);
        int bg = colorVal(widget, "buttonBg", 0xFF142033);
        int hoverBg = colorVal(widget, "buttonHover", 0xFF1E3A5F);
        int fg = colorVal(widget, "color", 0xFFFFFFFF);
        int outline = colorVal(widget, "buttonOutline", 0x553A5A80);
        int hoverOutline = colorVal(widget, "buttonHoverOutline", 0xFF4A86C8);
        int outlineWidth = intVal(widget, "outlineWidth", 1);
        int totalGap = gap * (buttons.size() - 1);
        int avail = panelW - panelPadding * 2 - totalGap;
        int eachW = Math.max(24, avail / buttons.size());

        int x = panelX + panelPadding;
        for (int i = 0; i < buttons.size(); i++) {
            if (!buttons.get(i).isJsonObject()) continue;
            JsonObject b = buttons.get(i).getAsJsonObject();
            String label = strVal(b, "label", "Action");
            boolean hover = inBox(mouseX, mouseY, x, y, eachW, h);
            drawOutlinedBox(ctx, x, y, x + eachW, y + h, hover ? hoverBg : bg,
                    hover ? hoverOutline : outline, outlineWidth);
            int tx = x + Math.max(4, (eachW - textRenderer.getWidth(label)) / 2);
            ctx.drawText(textRenderer, label, tx, y + (h - 8) / 2, fg, false);
            JsonObject action = obj(b, "action");
            if (!action.entrySet().isEmpty()) {
                addClickTarget(x, y, eachW, h, action, null, intVal(widget, "z", Z_BASE));
            }
            x += eachW + gap;
        }
    }

    private void renderSettingToggle(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                                     int y, int mouseX, int mouseY) {
        String key = strVal(widget, "key", "");
        if (key.isBlank()) return;

        String label = strVal(widget, "label", key);
        String activePlugin = defaultPlugin();
        boolean current = ModSettings.getBoolForPlugin(activePlugin, key);
        String right = current ? strVal(widget, "trueLabel", "On") : strVal(widget, "falseLabel", "Off");
        int h = intVal(widget, "height", 18);
        int fg = colorVal(widget, "color", 0xFFCCCCCC);
        int bg = colorVal(widget, "background", 0xFF142033);
        int hoverBg = colorVal(widget, "hover", 0xFF1E3A5F);
        int stateOnColor = colorVal(widget, "stateOnColor", 0xFF55FF55);
        int stateOffColor = colorVal(widget, "stateOffColor", 0xFFAAAAAA);
        int outline = colorVal(widget, "outline", 0x553A5A80);
        int hoverOutline = colorVal(widget, "hoverOutline", 0xFF4A86C8);
        int outlineWidth = intVal(widget, "outlineWidth", 1);

        int x = panelX + panelPadding;
        int w = panelW - panelPadding * 2;
        boolean hover = inBox(mouseX, mouseY, x, y, w, h);
        drawOutlinedBox(ctx, x, y, x + w, y + h, hover ? hoverBg : bg,
                hover ? hoverOutline : outline, outlineWidth);
        ctx.drawText(textRenderer, label, x + 6, y + (h - 8) / 2, fg, false);
        ctx.drawText(textRenderer, right, x + w - 6 - textRenderer.getWidth(right), y + (h - 8) / 2,
                current ? stateOnColor : stateOffColor, false);

        JsonObject action = action("toggle_setting");
        action.addProperty("plugin", activePlugin);
        action.addProperty("key", key);
        action.addProperty("trueValue", strVal(widget, "trueValue", "true"));
        action.addProperty("falseValue", strVal(widget, "falseValue", "false"));
        addClickTarget(x, y, w, h, action, null, intVal(widget, "z", Z_BASE));
    }

    private void renderTabContainer(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                                    int y, int h, int mouseX, int mouseY, int colLabel) {
        String containerId = strVal(widget, "id", "tabs");
        JsonArray tabs = arr(widget, "tabs");
        if (tabs.isEmpty()) return;

        int tabH = intVal(widget, "tabHeight", 18);
        int gap = intVal(widget, "tabGap", 3);
        int bg = colorVal(widget, "tabBg", 0xFF142033);
        int activeBg = colorVal(widget, "tabActiveBg", 0xFF1E3A5F);
        int fg = colorVal(widget, "tabColor", 0xFFCCCCCC);
        int activeFg = colorVal(widget, "tabActiveColor", 0xFFFFFFFF);
        int outline = colorVal(widget, "tabOutline", 0x553A5A80);
        int activeOutline = colorVal(widget, "tabActiveOutline", 0xFF4A86C8);
        int outlineWidth = intVal(widget, "outlineWidth", 1);
        int contentDivider = colorVal(widget, "contentDivider", 0xFF2A2A3A);

        int selected = selectedTabByContainer.getOrDefault(containerId, 0);
        if (selected < 0 || selected >= tabs.size()) selected = 0;

        int x = panelX + panelPadding;
        for (int i = 0; i < tabs.size(); i++) {
            if (!tabs.get(i).isJsonObject()) continue;
            JsonObject tab = tabs.get(i).getAsJsonObject();
            String label = strVal(tab, "label", "Tab " + (i + 1));
            int tw = textRenderer.getWidth(label) + 12;
            boolean isActive = i == selected;
            boolean hover = inBox(mouseX, mouseY, x, y, tw, tabH);
            drawOutlinedBox(ctx, x, y, x + tw, y + tabH, isActive ? activeBg : hover ? activeBg : bg,
                    isActive || hover ? activeOutline : outline, outlineWidth);
            ctx.drawText(textRenderer, label, x + 6, y + (tabH - 8) / 2, isActive ? activeFg : fg, false);

            JsonObject action = action("set_tab");
            action.addProperty("container", containerId);
            action.addProperty("tabIndex", i);
            addClickTarget(x, y, tw, tabH, action, null, intVal(widget, "z", Z_BASE));

            x += tw + gap;
        }

        int contentY = y + tabH + 3;
        int contentH = Math.max(0, h - tabH - 3);
        ctx.fill(panelX + panelPadding, contentY - 1, panelX + panelW - panelPadding, contentY, contentDivider);

        JsonObject activeTab = tabs.get(selected).getAsJsonObject();
        JsonArray tabWidgets = arr(activeTab, "widgets");
        renderWidgetsInRegion(ctx, tabWidgets, panelX, panelW, panelPadding, contentY, contentH, mouseX, mouseY, colLabel);
    }

    private void renderWidgetsInRegion(DrawContext ctx, JsonArray widgets, int panelX, int panelW, int panelPadding,
                                       int regionY, int regionH, int mouseX, int mouseY, int colLabel) {
        int fixedHeight = 0;
        int flexCount = 0;
        for (int i = 0; i < widgets.size(); i++) {
            if (!widgets.get(i).isJsonObject()) continue;
            JsonObject w = widgets.get(i).getAsJsonObject();
            String type = strVal(w, "type", "");
            if ("history".equals(type) && boolVal(w, "flex", false)) {
                flexCount++;
                continue;
            }
            if ("modal".equals(type)) continue;
            fixedHeight += widgetHeight(w, type, panelW, panelPadding);
        }
        int flexHeight = flexCount == 0 ? 0 : Math.max(0, (regionH - fixedHeight) / flexCount);

        int y = regionY;
        int regionTitleHeight = intVal(UiSchemaStore.getSchema(), "titleHeight", 14);
        for (int i = 0; i < widgets.size(); i++) {
            if (!widgets.get(i).isJsonObject()) continue;
            JsonObject w = widgets.get(i).getAsJsonObject();
            String type = strVal(w, "type", "");
            if ("modal".equals(type)) continue;
            int wh = ("history".equals(type) && boolVal(w, "flex", false)) ? flexHeight : widgetHeight(w, type, panelW, panelPadding);
            if (y + wh > regionY + regionH) break;
            renderWidgetByType(ctx, w, type, panelX, panelW, panelPadding, regionTitleHeight, colLabel, y, wh, mouseX, mouseY);
            y += wh;
        }
    }

    private void renderModal(DrawContext ctx, JsonObject widget, int mouseX, int mouseY, int colLabel) {
        int bg = colorVal(widget, "backdrop", 0xAA000000);
        ctx.fill(0, 0, width, height, bg);

        int modalW = Math.min(intVal(widget, "maxWidth", 460), (int) (width * doubleVal(widget, "widthPercent", 0.72)));
        int modalH = intVal(widget, "height", 240);
        int x = (width - modalW) / 2;
        int y = (height - modalH) / 2;
        int pad = intVal(widget, "padding", 8);
        int titleH = intVal(widget, "titleHeight", 16);
        int bodyBg = colorVal(widget, "background", 0xFF0A0A0F);
        int titleBg = colorVal(widget, "titleBackground", 0xFF111118);
        int line = colorVal(widget, "divider", 0xFF2A2A3A);
        int closeBg = colorVal(widget, "closeButtonBg", 0xFF142033);
        int closeHoverBg = colorVal(widget, "closeButtonHover", 0xFF3A1010);
        int closeColor = colorVal(widget, "closeColor", 0xFFAAAAAA);
        int closeHoverColor = colorVal(widget, "closeHoverColor", 0xFFFF7777);
        int closeOutline = colorVal(widget, "closeOutline", 0x553A5A80);
        int closeHoverOutline = colorVal(widget, "closeHoverOutline", 0xFFAA4545);
        int closeOutlineWidth = intVal(widget, "closeOutlineWidth", 1);

        ctx.fill(x, y, x + modalW, y + modalH, bodyBg);
        ctx.fill(x, y, x + modalW, y + titleH, titleBg);
        ctx.fill(x + 1, y + titleH, x + modalW - 1, y + titleH + 1, line);

        String title = strVal(widget, "title", "Modal");
        ctx.drawText(textRenderer, title, x + pad, y + (titleH - 8) / 2, colLabel, false);

        String close = strVal(widget, "closeLabel", "[x]");
        int cw = textRenderer.getWidth(close);
        int cx = x + modalW - pad - cw;
        boolean hover = inBox(mouseX, mouseY, cx - 2, y + 1, cw + 4, titleH - 2);
        drawOutlinedBox(ctx, cx - 2, y + 1, cx + cw + 2, y + titleH - 1, hover ? closeHoverBg : closeBg,
                hover ? closeHoverOutline : closeOutline, closeOutlineWidth);
        ctx.drawText(textRenderer, close, cx, y + (titleH - 8) / 2, hover ? closeHoverColor : closeColor, false);
        addClickTarget(cx - 2, y + 1, cw + 4, titleH - 2, action("close_modal"), null, intVal(widget, "z", Z_MODAL));

        if (boolVal(widget, "closeOnBackdrop", true)) {
            JsonObject closeAction = action("close_modal");
            addClickTarget(0, 0, width, y, closeAction, null, intVal(widget, "backdropZ", Z_MODAL_BACKDROP));
            addClickTarget(0, y + modalH, width, height - (y + modalH), closeAction, null, intVal(widget, "backdropZ", Z_MODAL_BACKDROP));
            addClickTarget(0, y, x, modalH, closeAction, null, intVal(widget, "backdropZ", Z_MODAL_BACKDROP));
            addClickTarget(x + modalW, y, width - (x + modalW), modalH, closeAction, null, intVal(widget, "backdropZ", Z_MODAL_BACKDROP));
        }

        JsonArray children = arr(widget, "widgets");
        int bodyY = y + titleH + 3;
        int bodyH = Math.max(0, modalH - titleH - 4);
        renderWidgetsInRegion(ctx, children, x, modalW, pad, bodyY, bodyH, mouseX, mouseY, colLabel);
    }

    private void renderInput(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                             int y, int h, int colLabel) {
        int divider = colorVal(widget, "divider", 0xFF2A2A3A);
        int inputBg = colorVal(widget, "background", 0x00000000);
        int promptColor = colorVal(widget, "promptColor", colLabel);
        int placeholderColor = colorVal(widget, "placeholderColor", 0xFF555555);
        int textColor = colorVal(widget, "textColor", ColorScheme.get(defaultPlugin()).user() | 0xFF000000);
        int caretColor = colorVal(widget, "caretColor", textColor);
        int lineHeight = Math.max(1, intVal(widget, "lineHeight", historyLineHeight));
        int minRows = Math.max(1, intVal(widget, "minRows", 1));
        int inputOutline = colorVal(widget, "outline", 0x553A5A80);
        int inputOutlineWidth = intVal(widget, "outlineWidth", 1);
        String prompt = strVal(widget, "prompt", "◆");
        String placeholder = strVal(widget, "placeholder", "Type a message...");

        ctx.fill(panelX + panelPadding, y, panelX + panelW - panelPadding, y + 1, divider);

        if ((inputBg >>> 24) != 0) {
            drawOutlinedBox(ctx, panelX + panelPadding, y + 1, panelX + panelW - panelPadding, y + h,
                    inputBg, inputOutline, inputOutlineWidth);
        }

        int inputY = y + 6;
        int promptW = textRenderer.getWidth(prompt + " ");
        int inputX = panelX + panelPadding + promptW;
        int maxW = panelW - panelPadding * 2 - promptW;
        int visibleRows = Math.max(minRows, (h - 10) / lineHeight);

        ctx.drawText(textRenderer, prompt, panelX + panelPadding, inputY, promptColor, false);

        List<OrderedText> wrapped = textRenderer.wrapLines(parseLegacy(inputText.isEmpty() ? " " : inputText), maxW);
        int maxInputScroll = Math.max(0, wrapped.size() - visibleRows);
        inputScrollOffset = Math.max(0, Math.min(maxInputScroll, inputScrollOffset));
        if (inputText.isEmpty()) {
            ctx.drawText(textRenderer, placeholder, inputX, inputY, placeholderColor, false);
        } else {
            int startRow = Math.min(inputScrollOffset, Math.max(0, wrapped.size() - 1));
            int endRow = Math.min(wrapped.size(), startRow + visibleRows);
            int ty = inputY;
            for (int i = startRow; i < endRow; i++) {
                OrderedText ot = wrapped.get(i);
                ctx.drawText(textRenderer, ot, inputX, ty, textColor, false);
                ty += lineHeight;
            }
        }

        long nowMs = System.currentTimeMillis();
        if ((nowMs / 500) % 2 == 0) {
            int line = 0;
            int col = 0;
            if (!inputText.isEmpty()) {
                String before = inputText.substring(0, Math.min(cursor, inputText.length()));
                List<OrderedText> caretRows = textRenderer.wrapLines(parseLegacy(before.isEmpty() ? " " : before), maxW);
                line = Math.max(0, caretRows.size() - 1);
                col = textRenderer.getWidth(caretRows.get(line));
                if (before.endsWith("\n")) {
                    line++;
                    col = 0;
                }
                if (line < inputScrollOffset) {
                    inputScrollOffset = line;
                } else if (line >= inputScrollOffset + visibleRows) {
                    inputScrollOffset = line - visibleRows + 1;
                }
            }
            int caretY = line - inputScrollOffset;
            if (caretY >= 0 && caretY < visibleRows) {
                int cy = inputY + caretY * lineHeight;
                ctx.fill(inputX + col, cy, inputX + col + 1, cy + textRenderer.fontHeight, caretColor);
            }
        }

        inputTop = y + 1;
        inputBottom = y + h;
    }

    private void renderHint(DrawContext ctx, JsonObject widget, int panelX, int panelPadding, int y) {
        String text = strVal(widget, "text", "");
        int color = colorVal(widget, "color", 0xFF777788);
        if (!text.isEmpty()) {
            ctx.drawText(textRenderer, text, panelX + panelPadding, y + 2, color, false);
        }
    }

    private void renderDropdown(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                                int y, int mouseX, int mouseY, int colLabel) {
        String id = strVal(widget, "id", "dropdown");
        String label = strVal(widget, "label", "Select");
        int h = intVal(widget, "height", 18);
        int optionH = intVal(widget, "optionHeight", 16);
        int bg = colorVal(widget, "buttonBg", 0xFF142033);
        int hoverBg = colorVal(widget, "buttonHover", 0xFF1E3A5F);
        int textColor = colorVal(widget, "color", colLabel);
        int outline = colorVal(widget, "buttonOutline", 0x553A5A80);
        int hoverOutline = colorVal(widget, "buttonHoverOutline", 0xFF4A86C8);
        int outlineWidth = intVal(widget, "outlineWidth", 1);
        int x = panelX + panelPadding;
        int w = panelW - panelPadding * 2;
        boolean open = id.equals(activeDropdownId);

        boolean hover = inBox(mouseX, mouseY, x, y, w, h);
        drawOutlinedBox(ctx, x, y, x + w, y + h, hover ? hoverBg : bg,
                hover ? hoverOutline : outline, outlineWidth);
        String headerLabel = label + (open ? " ▲" : " ▼");
        ctx.drawText(textRenderer, headerLabel, x + 6, y + (h - 8) / 2, textColor, false);

        JsonObject toggle = action("toggle_dropdown");
        toggle.addProperty("id", id);
        addClickTarget(x, y, w, h, toggle, null, intVal(widget, "z", Z_DROPDOWN));

        if (!open) return;

        JsonArray options = arr(widget, "options");
        int oy = y + h;
        for (int i = 0; i < options.size(); i++) {
            if (!options.get(i).isJsonObject()) continue;
            JsonObject opt = options.get(i).getAsJsonObject();
            String optLabel = strVal(opt, "label", "Option");
            JsonObject optAction = obj(opt, "action");
            boolean optHover = inBox(mouseX, mouseY, x, oy, w, optionH);
            drawOutlinedBox(ctx, x, oy, x + w, oy + optionH, optHover ? hoverBg : bg,
                    optHover ? hoverOutline : outline, outlineWidth);
            ctx.drawText(textRenderer, optLabel, x + 6, oy + (optionH - 8) / 2, textColor, false);
            if (!optAction.entrySet().isEmpty()) {
                addClickTarget(x, oy, w, optionH, optAction, null, intVal(widget, "z", Z_DROPDOWN + 1));
            }
            oy += optionH;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY >= historyTop && mouseY <= historyBottom) {
            scrollOffset = Math.max(0, (int) (scrollOffset + verticalAmount * 3));
            return true;
        }
        if (mouseY >= inputTop && mouseY <= inputBottom) {
            inputScrollOffset = Math.max(0, (int) (inputScrollOffset + verticalAmount * 3));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        List<ClickTarget> ordered = new ArrayList<>(clickTargets);
        ordered.sort((a, b) -> {
            int byZ = Integer.compare(b.z(), a.z());
            if (byZ != 0) return byZ;
            return Long.compare(b.seq(), a.seq());
        });

        for (ClickTarget target : ordered) {
            if (!inBox(mouseX, mouseY, target.x(), target.y(), target.w(), target.h())) continue;
            runAction(target.action(), target.optionValue());
            return true;
        }

        if (activeDropdownId != null) {
            activeDropdownId = null;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void addClickTarget(int x, int y, int w, int h, JsonObject action, String optionValue, int z) {
        int resolvedZ = z;
        if (action != null && action.has("z")) {
            try {
                resolvedZ = action.get("z").getAsInt();
            } catch (Exception ignored) {
                resolvedZ = z;
            }
        }
        clickTargets.add(new ClickTarget(x, y, w, h, action, optionValue, resolvedZ, clickSeq++));
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        int max = inputMaxLength();
        if (inputText.length() >= max) return true;
        inputText = inputText.substring(0, cursor) + chr + inputText.substring(cursor);
        cursor++;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        ScreenDef activeScreen = ScreenManager.getActiveScreen();
        String activePlugin = activeScreen != null ? activeScreen.plugin : defaultPlugin();
        
        if (PluginConfig.matches(activePlugin, "clear_history", keyCode, modifiers)) {
            clearHistoryViewAndServer(activePlugin);
            return true;
        }
        if (PluginConfig.matches(activePlugin, "sync_history", keyCode, modifiers)) {
            ModPackets.requestHistory(activePlugin);
            return true;
        }
        if (PluginConfig.matches(activePlugin, "open_help", keyCode, modifiers)) {
            openHelpModal();
            return true;
        }
        if (PluginConfig.matches(activePlugin, "toggle_thoughts", keyCode, modifiers)) {
            boolean next = !ModSettings.getBoolForPlugin(activePlugin, "ui.thoughts_visible");
            String value = String.valueOf(next);
            ModSettings.setForPlugin(activePlugin, "ui.thoughts_visible", value);
            ModPackets.sendSetSetting(activePlugin, "ui.thoughts_visible", value);
            return true;
        }

        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0
                && keyCode == GLFW.GLFW_KEY_M
                && "vibecraft".equalsIgnoreCase(activePlugin)) {
            ModPackets.sendInput("__vc_mock_stream_test__", activePlugin);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                if (inputText.length() < inputMaxLength()) {
                    inputText = inputText.substring(0, cursor) + "\n" + inputText.substring(cursor);
                    cursor++;
                }
                return true;
            }
            String trimmed = inputText.trim();
            if (!trimmed.isEmpty()) {
                ModPackets.sendInput(trimmed, activePlugin);
                inputText = "";
                cursor = 0;
                scrollOffset = 0;
                inputScrollOffset = 0;
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (cursor > 0) {
                inputText = inputText.substring(0, cursor - 1) + inputText.substring(cursor);
                cursor--;
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (cursor < inputText.length()) {
                inputText = inputText.substring(0, cursor) + inputText.substring(cursor + 1);
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_LEFT) { if (cursor > 0) cursor--; return true; }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) { if (cursor < inputText.length()) cursor++; return true; }
        if (keyCode == GLFW.GLFW_KEY_HOME) { cursor = 0; return true; }
        if (keyCode == GLFW.GLFW_KEY_END) { cursor = inputText.length(); return true; }

        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
            String clip = client.keyboard.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                String insert = clip.replace("\r\n", "\n").replace('\r', '\n');
                int room = inputMaxLength() - inputText.length();
                if (room > 0) {
                    if (insert.length() > room) insert = insert.substring(0, room);
                    inputText = inputText.substring(0, cursor) + insert + inputText.substring(cursor);
                    cursor += insert.length();
                }
            }
            return true;
        }

        if (PluginConfig.matches(activePlugin, "open_options", keyCode, modifiers)) {
            client.setScreen(new SchemaSettingsScreen(this));
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(null);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Intentionally no-op: keep game view crisp behind the schema UI.
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private int inputMaxLength() {
        JsonArray widgets = UiSchemaStore.widgets();
        for (int i = 0; i < widgets.size(); i++) {
            JsonObject w = widgets.get(i).getAsJsonObject();
            if ("input".equals(strVal(w, "type", ""))) {
                return intVal(w, "maxLength", 1000);
            }
        }
        return 1000;
    }

    private String currentIndicator() {
        if (!HudState.isStreaming()) return "";
        String tool = HudState.getCurrentTool();
        String detail = HudState.getCurrentToolDetail();
        return tool != null ? "⏳ [" + tool + "] " + (detail != null ? detail : "") : "⏳ Processing...";
    }

    private int widgetHeight(JsonObject w, String type, int panelW, int panelPadding) {
        return switch (type) {
            case "toolbar" -> intVal(w, "height", 14);
            case "history" -> intVal(w, "height", 220);
            case "question_options" -> {
                HudState.PendingQuestion q = HudState.getQuestion();
                if (q == null || q.options().isEmpty()) yield 0;
                int buttonH = intVal(w, "buttonHeight", 16);
                int gap = intVal(w, "buttonGap", 2);
                yield q.options().size() * (buttonH + gap) + 4;
            }
            case "input" -> inputWidgetHeight(w, panelW, panelPadding);
            case "hint" -> intVal(w, "height", 12);
            case "text" -> intVal(w, "height", 14);
            case "action_row" -> intVal(w, "height", 20);
            case "dropdown" -> {
                int base = intVal(w, "height", 18);
                String id = strVal(w, "id", "dropdown");
                if (!id.equals(activeDropdownId)) yield base;
                int optionH = intVal(w, "optionHeight", 16);
                yield base + (arr(w, "options").size() * optionH);
            }
            case "setting_toggle" -> intVal(w, "height", 18);
            case "state_badge" -> intVal(w, "height", 14);
            case "tab_container" -> intVal(w, "height", 220);
            case "modal" -> 0;
            case "divider" -> 1;
            case "spacer" -> intVal(w, "height", 8);
            default -> intVal(w, "height", 0);
        };
    }

    private int inputWidgetHeight(JsonObject w, int panelW, int panelPadding) {
        int maxHeight = intVal(w, "height", 72);
        int promptW = textRenderer.getWidth(strVal(w, "prompt", "◆") + " ");
        int maxW = Math.max(40, panelW - panelPadding * 2 - promptW);
        int lineHeight = Math.max(1, intVal(w, "lineHeight", historyLineHeight));
        int minRows = Math.max(1, intVal(w, "minRows", 1));

        List<OrderedText> wrapped = textRenderer.wrapLines(parseLegacy(inputText.isEmpty() ? " " : inputText), maxW);
        int rows = Math.max(minRows, wrapped.size());
        int maxRowsByHeight = Math.max(minRows, (maxHeight - 10) / lineHeight);
        int visibleRows = Math.min(rows, maxRowsByHeight);

        return Math.max(lineHeight + 10, Math.min(maxHeight, visibleRows * lineHeight + 10));
    }

    private void runAction(JsonObject action, String optionValue) {
        if (action == null || action.entrySet().isEmpty()) return;
        String type = strVal(action, "type", "");
        ScreenDef activeScreen = ScreenManager.getActiveScreen();
        String defaultPlugin = activeScreen != null ? activeScreen.plugin : defaultPlugin();
        String targetPlugin = strVal(action, "plugin", defaultPlugin);
        if (DebugConfig.DEBUG_ACTIONS) {
            System.out.println("[VibeCraftMod] runAction: type=" + type
                    + ", targetPlugin=" + targetPlugin
                    + ", optionValue=" + (optionValue == null ? "<null>" : optionValue)
                    + ", modal=" + (activeModalId == null ? "<none>" : activeModalId));
        }
        if (runInternalAction(type, action, optionValue, targetPlugin)) {
            return;
        }
        if (handleLegacyAction(type, action, optionValue, targetPlugin)) return;
    }

    private void clearHistoryViewAndServer(String plugin) {
        HudState.clear();
        HudState.clearQuestion();
        ModPackets.clearHistory(plugin);
        scrollOffset = 0;
    }

    private void openHelpModal() {
        activeModalId = "help-modal";
    }

    private boolean runInternalAction(String type, JsonObject action, String optionValue, String targetPlugin) {
        String name = null;
        if ("invoke_internal".equals(type)) {
            name = strVal(action, "name", strVal(action, "id", ""));
        } else if (type.startsWith("internal:")) {
            name = type.substring("internal:".length());
        } else if (INTERNAL_ACTIONS.containsKey(type)) {
            name = type;
        }

        if (name == null || name.isBlank()) return false;
        InternalAction handler = INTERNAL_ACTIONS.get(name);
        if (handler == null) {
            if (DebugConfig.DEBUG_ACTIONS) {
                System.out.println("[VibeCraftMod] Unknown internal action: " + name);
            }
            return false;
        }

        handler.run(this, action, optionValue, targetPlugin);
        return true;
    }

    private boolean handleLegacyAction(String type, JsonObject action, String optionValue, String targetPlugin) {
        switch (type) {
            case "open_modal" -> {
                String id = strVal(action, "id", "");
                if (!id.isBlank()) activeModalId = id;
                return true;
            }
            case "close_modal" -> {
                activeModalId = null;
                return true;
            }
            case "toggle_dropdown" -> {
                String id = strVal(action, "id", "");
                if (!id.isBlank()) activeDropdownId = id.equals(activeDropdownId) ? null : id;
                return true;
            }
            case "request_history", "refresh_schema" -> {
                ModPackets.requestHistory(targetPlugin);
                return true;
            }
            case "clear_history" -> {
                clearHistoryViewAndServer(targetPlugin);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private List<String> wrapTextRows(String text, int maxW) {
        List<String> rows = new ArrayList<>();
        if (text.isEmpty()) {
            rows.add("");
            return rows;
        }
        for (String paragraph : text.split("\\n", -1)) {
            if (paragraph.isEmpty()) {
                rows.add("");
                continue;
            }
            rows.addAll(wrapParagraphByWords(paragraph, maxW));
        }
        if (rows.isEmpty()) rows.add("");
        return rows;
    }

    private List<String> wrapParagraphByWords(String paragraph, int maxW) {
        List<String> rows = new ArrayList<>();
        String rem = paragraph;
        while (!rem.isEmpty()) {
            String fits = textRenderer.trimToWidth(rem, maxW);
            if (fits.isEmpty()) {
                rows.add(rem.substring(0, 1));
                rem = rem.substring(1);
                continue;
            }

            int splitAt = fits.length();
            boolean clipped = splitAt < rem.length();
            if (clipped && splitAt > 0 && !Character.isWhitespace(fits.charAt(splitAt - 1))) {
                int ws = -1;
                for (int i = splitAt - 1; i >= 0; i--) {
                    if (Character.isWhitespace(fits.charAt(i))) {
                        ws = i;
                        break;
                    }
                }
                if (ws > 0) {
                    splitAt = ws;
                    fits = rem.substring(0, splitAt).stripTrailing();
                }
            }

            rows.add(fits);
            rem = rem.substring(splitAt);
            rem = rem.stripLeading();
        }
        return rows;
    }

    private String truncate(String text, int maxW) {
        if (textRenderer.getWidth(text) <= maxW) return text;
        String out = text;
        while (out.length() > 1 && textRenderer.getWidth(out + "…") > maxW) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }

    private String resolveTemplate(String text) {
        if (text == null) return "";
        String tool = HudState.getCurrentTool();
        String detail = HudState.getCurrentToolDetail();
        return text
                .replace("${streaming}", String.valueOf(HudState.isStreaming()))
                .replace("${historyCount}", String.valueOf(HudState.getLines().size()))
                .replace("${tool}", tool == null ? "" : tool)
            .replace("${detail}", detail == null ? "" : detail)
            .replace("${shortcut.open_menu}", ClientConfig.getKeybindName("open_menu"))
            .replace("${shortcut.toggle_thoughts}", ClientConfig.getKeybindName("toggle_thoughts"))
            .replace("${shortcut.open_options}", ClientConfig.getKeybindName("open_options"))
            .replace("${shortcut.open_help}", ClientConfig.getKeybindName("open_help"))
            .replace("${shortcut.sync_history}", ClientConfig.getKeybindName("sync_history"))
            .replace("${shortcut.clear_history}", ClientConfig.getKeybindName("clear_history"));
    }

    private String resolveActionText(String template, String optionValue) {
        String resolved = resolveTemplate(template == null ? "" : template);
        return resolved.replace("${option}", optionValue == null ? "" : optionValue);
    }

    private static MutableText parseLegacy(String s) {
        MutableText root = Text.empty();
        Style current = Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                if (!buf.isEmpty()) {
                    root.append(Text.literal(buf.toString()).setStyle(current));
                    buf.setLength(0);
                }
                char code = s.charAt(++i);
                Formatting f = Formatting.byCode(code);
                if (f == null) continue;
                if (f == Formatting.RESET) current = Style.EMPTY;
                else if (f.isColor()) current = current.withColor(f);
                else if (f == Formatting.BOLD) current = current.withBold(true);
                else if (f == Formatting.ITALIC) current = current.withItalic(true);
                else if (f == Formatting.UNDERLINE) current = current.withUnderline(true);
                else if (f == Formatting.STRIKETHROUGH) current = current.withStrikethrough(true);
            } else {
                buf.append(c);
            }
        }
        if (!buf.isEmpty()) root.append(Text.literal(buf.toString()).setStyle(current));
        return root;
    }

    private static JsonObject obj(JsonObject parent, String key) {
        if (parent != null && parent.has(key) && parent.get(key).isJsonObject()) return parent.getAsJsonObject(key);
        return new JsonObject();
    }

    private static JsonArray arr(JsonObject parent, String key) {
        if (parent != null && parent.has(key) && parent.get(key).isJsonArray()) return parent.getAsJsonArray(key);
        return new JsonArray();
    }

    private static JsonObject action(String type) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type);
        return obj;
    }

    private static JsonObject internalAction(String name) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "invoke_internal");
        obj.addProperty("name", name);
        return obj;
    }

    private static String strVal(JsonObject obj, String key, String fallback) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) return obj.get(key).getAsString();
        return fallback;
    }

    private static boolean boolVal(JsonObject obj, String key, boolean fallback) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) return obj.get(key).getAsBoolean();
        return fallback;
    }

    private static int intVal(JsonObject obj, String key, int fallback) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try { return obj.get(key).getAsInt(); } catch (Exception ignored) { }
        }
        return fallback;
    }

    private static double doubleVal(JsonObject obj, String key, double fallback) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try { return obj.get(key).getAsDouble(); } catch (Exception ignored) { }
        }
        return fallback;
    }

    private static int colorVal(JsonObject obj, String key, int fallback) {
        if (obj == null) return fallback;

        // Preferred: explicit setting key per color prop, e.g. "textColorSetting": "ui.input_text".
        String settingKey = strVal(obj, key + "Setting", null);
        if (settingKey != null && !settingKey.isBlank()) {
            int fromSetting = parseColor(ModSettings.getForPlugin(defaultPlugin(), settingKey), Integer.MIN_VALUE);
            if (fromSetting != Integer.MIN_VALUE) return fromSetting;
        }

        // Also allow inline references in color values: "setting:ui.input_text" or "$ui.input_text".
        String raw = strVal(obj, key, null);
        if (raw == null || raw.isBlank()) return fallback;
        if (raw.startsWith("setting:")) {
            int fromSetting = parseColor(ModSettings.getForPlugin(defaultPlugin(), raw.substring("setting:".length()).trim()), Integer.MIN_VALUE);
            return fromSetting != Integer.MIN_VALUE ? fromSetting : fallback;
        }
        if (raw.startsWith("$")) {
            int fromSetting = parseColor(ModSettings.getForPlugin(defaultPlugin(), raw.substring(1).trim()), Integer.MIN_VALUE);
            return fromSetting != Integer.MIN_VALUE ? fromSetting : fallback;
        }

        return parseColor(raw, fallback);
    }

    private static int parseColor(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            String hex = raw.replaceFirst("^#", "");
            long val = Long.parseLong(hex, 16);
            if (hex.length() <= 6) return (int) (0xFF000000L | val);
            return (int) val;
        } catch (Exception e) {
            return fallback;
        }
    }

    private void drawOutlinedBox(DrawContext ctx, int x1, int y1, int x2, int y2,
                                 int fill, int outline, int outlineWidth) {
        ctx.fill(x1, y1, x2, y2, fill);
        int ow = Math.max(0, outlineWidth);
        if (ow <= 0 || (outline >>> 24) == 0) return;

        for (int i = 0; i < ow; i++) {
            ctx.fill(x1, y1 + i, x2, y1 + i + 1, outline);
            ctx.fill(x1, y2 - i - 1, x2, y2 - i, outline);
            ctx.fill(x1 + i, y1, x1 + i + 1, y2, outline);
            ctx.fill(x2 - i - 1, y1, x2 - i, y2, outline);
        }
    }

    private static boolean inBox(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String defaultPlugin() {
        String active = ScreenManager.getActivePlugin();
        if (active != null && !active.isBlank()) return active;
        return "vibecraft";
    }
}


