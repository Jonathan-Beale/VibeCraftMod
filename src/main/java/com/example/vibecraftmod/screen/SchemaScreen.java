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
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        INTERNAL_ACTIONS.put("toggle_collapsible", (screen, action, optionValue, targetPlugin) -> {
            String id = strVal(action, "id", "");
            if (!id.isBlank()) {
                boolean current = screen.collapsibleOpen.getOrDefault(id, boolVal(action, "defaultOpen", false));
                screen.collapsibleOpen.put(id, !current);
            }
        });
        INTERNAL_ACTIONS.put("set_text_focus", (screen, action, optionValue, targetPlugin) -> {
            String id = strVal(action, "id", "");
            if (!id.isBlank()) {
                String value = screen.textFieldValue(id);
                screen.activeTextFieldId = id;
                screen.textFieldCursor.put(id, Math.min(screen.textFieldCursor.getOrDefault(id, value.length()), value.length()));
            }
        });
        INTERNAL_ACTIONS.put("clear_text_state", (screen, action, optionValue, targetPlugin) -> {
            String id = strVal(action, "id", "");
            if (!id.isBlank()) {
                screen.setTextFieldValue(id, "");
                screen.textFieldCursor.put(id, 0);
                screen.activeTextFieldId = id;
            }
        });
        INTERNAL_ACTIONS.put("toggle_query_token", (screen, action, optionValue, targetPlugin) -> {
            String stateId = strVal(action, "stateId", "");
            String token = strVal(action, "token", "").toLowerCase(Locale.ROOT).trim();
            if (stateId.isBlank() || token.isBlank()) return;

            screen.toggleFacetToken(stateId, token);
            screen.textFieldCursor.put(stateId, screen.textFieldValue(stateId).length());
            screen.activeTextFieldId = stateId;
        });
    }

    private record ClickTarget(int x, int y, int w, int h, JsonObject action, String optionValue, int z, long seq) {}
    private record ScrollRegion(int x, int y, int w, int h, String id) {}
    private record LayoutRow(int index, int z, JsonObject widget, String type, int y, int h) {}

    private static final class FilterState {
        private String textQuery = "";
        private final LinkedHashSet<String> selectedTags = new LinkedHashSet<>();
        private final LinkedHashSet<String> selectedItems = new LinkedHashSet<>();
    }

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
    private final Map<String, Boolean> collapsibleOpen = new HashMap<>();
    private final Map<String, Integer> scrollPanelOffsets = new HashMap<>();
    private final List<ScrollRegion> scrollRegions = new ArrayList<>();
    private final Map<String, String> textFieldValues = new HashMap<>();
    private final Map<String, Integer> textFieldCursor = new HashMap<>();
    private final Map<String, FilterState> filterStates = new HashMap<>();
    private final Map<String, WidgetRenderer> widgetRenderers = new HashMap<>();
    private String activeTextFieldId = null;
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
            renderHint(ctx, w, panelX, panelW, panelPadding, y));
        widgetRenderers.put("search_box", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            renderSearchBox(ctx, w, panelX, panelW, panelPadding, y, h, mouseX, mouseY));
        widgetRenderers.put("filter_chips", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            renderFilterChips(ctx, w, panelX, panelW, panelPadding, y, h, mouseX, mouseY));
        widgetRenderers.put("multi_select_dropdown", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            renderMultiSelectDropdown(ctx, w, panelX, panelW, panelPadding, y, h, mouseX, mouseY, colLabel));
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
        widgetRenderers.put("panel", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            renderPanel(ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY));
        widgetRenderers.put("collapsible", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            renderCollapsible(ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY));
        widgetRenderers.put("scroll_panel", (ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY) ->
            renderScrollPanel(ctx, w, panelX, panelW, panelPadding, titleHeight, colLabel, y, h, mouseX, mouseY));
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
        scrollRegions.clear();

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
            String rowGroup = strVal(w, "rowGroup", "");
            if (!rowGroup.isBlank()) {
                int maxH = 0;
                int j = i;
                for (; j < widgets.size(); j++) {
                    JsonObject gw = widgets.get(j).getAsJsonObject();
                    if ("modal".equals(strVal(gw, "type", ""))) continue;
                    if (!rowGroup.equals(strVal(gw, "rowGroup", ""))) break;
                    String gType = strVal(gw, "type", "");
                    if (boolVal(gw, "flex", false)) {
                        flexCount++;
                    } else {
                        maxH = Math.max(maxH, widgetHeight(gw, gType, panelW, panelPadding));
                    }
                }
                fixedHeight += maxH;
                i = Math.max(i, j - 1);
                continue;
            }
            if (boolVal(w, "flex", false)) {
                flexCount++;
                continue;
            }
            fixedHeight += widgetHeight(w, type, panelW, panelPadding);
        }
        int remaining = Math.max(0, height - titleHeight - fixedHeight - panelPadding);
        int flexHeight = flexCount == 0 ? 0 : remaining / flexCount;

        List<LayoutRow> rows = new ArrayList<>();
        for (int i = 0; i < widgets.size(); i++) {
            JsonObject w = widgets.get(i).getAsJsonObject();
            String type = strVal(w, "type", "");
            if ("modal".equals(type)) continue;
            String rowGroup = strVal(w, "rowGroup", "");
            if (!rowGroup.isBlank()) {
                int rowY = y;
                int rowH = 0;
                int j = i;
                for (; j < widgets.size(); j++) {
                    JsonObject gw = widgets.get(j).getAsJsonObject();
                    String gType = strVal(gw, "type", "");
                    if ("modal".equals(gType)) continue;
                    if (!rowGroup.equals(strVal(gw, "rowGroup", ""))) break;
                    int gH = boolVal(gw, "flex", false) ? flexHeight : widgetHeight(gw, gType, panelW, panelPadding);
                    rowH = Math.max(rowH, gH);
                    rows.add(new LayoutRow(j, widgetRenderZ(gType, gw), gw, gType, rowY, gH));
                }
                y += rowH;
                i = Math.max(i, j - 1);
                continue;
            }
            int wh = boolVal(w, "flex", false) ? flexHeight : widgetHeight(w, type, panelW, panelPadding);
            rows.add(new LayoutRow(i, widgetRenderZ(type, w), w, type, y, wh));
            y += wh;
        }
        renderRowsByZ(ctx, rows, panelX, panelW, panelPadding, titleHeight, colLabel, mouseX, mouseY);

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
            String rowGroup = strVal(w, "rowGroup", "");
            if (!rowGroup.isBlank()) {
                int maxH = 0;
                int j = i;
                for (; j < widgets.size(); j++) {
                    if (!widgets.get(j).isJsonObject()) continue;
                    JsonObject gw = widgets.get(j).getAsJsonObject();
                    String gType = strVal(gw, "type", "");
                    if ("modal".equals(gType)) continue;
                    if (!rowGroup.equals(strVal(gw, "rowGroup", ""))) break;
                    if (boolVal(gw, "flex", false)) {
                        flexCount++;
                    } else {
                        maxH = Math.max(maxH, widgetHeight(gw, gType, panelW, panelPadding));
                    }
                }
                fixedHeight += maxH;
                i = Math.max(i, j - 1);
                continue;
            }
            if (boolVal(w, "flex", false)) {
                flexCount++;
                continue;
            }
            if ("modal".equals(type)) continue;
            fixedHeight += widgetHeight(w, type, panelW, panelPadding);
        }
        int flexHeight = flexCount == 0 ? 0 : Math.max(0, (regionH - fixedHeight) / flexCount);

        int y = regionY;
        int regionTitleHeight = intVal(UiSchemaStore.getSchema(), "titleHeight", 14);
        List<LayoutRow> rows = new ArrayList<>();
        for (int i = 0; i < widgets.size(); i++) {
            if (!widgets.get(i).isJsonObject()) continue;
            JsonObject w = widgets.get(i).getAsJsonObject();
            String type = strVal(w, "type", "");
            if ("modal".equals(type)) continue;
            String rowGroup = strVal(w, "rowGroup", "");
            if (!rowGroup.isBlank()) {
                int rowY = y;
                int rowH = 0;
                int j = i;
                List<LayoutRow> groupRows = new ArrayList<>();
                for (; j < widgets.size(); j++) {
                    if (!widgets.get(j).isJsonObject()) continue;
                    JsonObject gw = widgets.get(j).getAsJsonObject();
                    String gType = strVal(gw, "type", "");
                    if ("modal".equals(gType)) continue;
                    if (!rowGroup.equals(strVal(gw, "rowGroup", ""))) break;
                    int gH = boolVal(gw, "flex", false) ? flexHeight : widgetHeight(gw, gType, panelW, panelPadding);
                    rowH = Math.max(rowH, gH);
                    groupRows.add(new LayoutRow(j, widgetRenderZ(gType, gw), gw, gType, rowY, gH));
                }
                if (y >= regionY + regionH) break;
                if (y + rowH > regionY) {
                    rows.addAll(groupRows);
                }
                y += rowH;
                i = Math.max(i, j - 1);
                continue;
            }
            int wh = boolVal(w, "flex", false) ? flexHeight : widgetHeight(w, type, panelW, panelPadding);
            if (y >= regionY + regionH) break;
            if (y + wh <= regionY) {
                y += wh;
                continue;
            }
            rows.add(new LayoutRow(i, widgetRenderZ(type, w), w, type, y, wh));
            y += wh;
        }
        renderRowsByZ(ctx, rows, panelX, panelW, panelPadding, regionTitleHeight, colLabel, mouseX, mouseY);
    }

    private void renderRowsByZ(DrawContext ctx, List<LayoutRow> rows,
                               int panelX, int panelW, int panelPadding, int titleHeight, int colLabel,
                               int mouseX, int mouseY) {
        rows.stream()
                .sorted(Comparator.comparingInt(LayoutRow::z).thenComparingInt(LayoutRow::index))
                .forEach(row -> renderWidgetByType(ctx, row.widget(), row.type(),
                        panelX, panelW, panelPadding, titleHeight, colLabel,
                        row.y(), row.h(), mouseX, mouseY));
    }

    private int widgetRenderZ(String type, JsonObject widget) {
        if (widget.has("z")) {
            return intVal(widget, "z", Z_BASE);
        }
        return switch (type) {
            case "dropdown", "multi_select_dropdown" -> Z_DROPDOWN;
            default -> Z_BASE;
        };
    }

    private void renderPanel(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                             int titleHeight, int colLabel, int y, int h, int mouseX, int mouseY) {
        int pad = intVal(widget, "padding", 0);

        // Optional background fill
        int bg = colorVal(widget, "background", 0);
        if (bg != 0) {
            ctx.fill(panelX + panelPadding, y, panelX + panelW - panelPadding, y + h, bg);
        }

        // Optional border outline
        if (widget.has("border")) {
            int borderColor = colorVal(widget, "border", 0xFF2A2A3A);
            int bw = intVal(widget, "borderWidth", 1);
            drawOutlinedBox(ctx, panelX + panelPadding, y, panelX + panelW - panelPadding, y + h,
                    0, borderColor, bw);
        }

        // Render children widgets inside the panel, offset by padding
        JsonArray children = arr(widget, "widgets");
        if (children.size() > 0) {
            renderWidgetsInRegion(ctx, children, panelX, panelW, panelPadding + pad,
                    y + pad, h - pad * 2, mouseX, mouseY, colLabel);
        }
    }

    private void renderCollapsible(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                                   int titleHeight, int colLabel, int y, int h, int mouseX, int mouseY) {
        String id = strVal(widget, "id", "");
        boolean defOpen = boolVal(widget, "open", false);
        boolean open = id.isBlank() ? defOpen : collapsibleOpen.getOrDefault(id, defOpen);
        int headerH = intVal(widget, "headerHeight", 16);
        String label = strVal(widget, "label", "");

        // Header background
        int headerBg = colorVal(widget, "headerBg", 0xFF111122);
        int headerHoverBg = colorVal(widget, "headerHoverBg", 0xFF1A1A33);
        int headerColor = colorVal(widget, "headerColor", 0xFFCCCCCC);
        boolean hover = inBox(mouseX, mouseY, panelX + panelPadding, y, panelW - panelPadding * 2, headerH);
        ctx.fill(panelX + panelPadding, y, panelX + panelW - panelPadding, y + headerH,
                hover ? headerHoverBg : headerBg);

        int headerLeft = panelX + panelPadding;
        int headerRight = panelX + panelW - panelPadding;

        // Tag pills on the right side (if present)
        int tagsStartX = headerRight;
        JsonArray tags = arr(widget, "tags");
        if (!tags.isEmpty()) {
            int chipGap = intVal(widget, "tagChipGap", 2);
            int chipPadX = intVal(widget, "tagChipPaddingX", 4);
            int chipH = intVal(widget, "tagChipHeight", Math.max(9, headerH - 8));
            int minLabelW = intVal(widget, "minLabelWidth", 170);
            int maxTagArea = Math.max(0, (headerRight - headerLeft) - minLabelW - 8);
            int cx = headerRight;
            int usedTagArea = 0;
            int hiddenCount = 0;
            for (int i = tags.size() - 1; i >= 0; i--) {
                if (!tags.get(i).isJsonPrimitive()) continue;
                String tag = tags.get(i).getAsString();
                if (tag == null || tag.isBlank()) continue;
                int chipW = textRenderer.getWidth(tag) + chipPadX * 2;
                int neededArea = chipW + (usedTagArea == 0 ? 0 : chipGap);
                if (usedTagArea + neededArea > maxTagArea) {
                    hiddenCount++;
                    continue;
                }
                usedTagArea += neededArea;
                cx -= chipW;
                int chipY = y + (headerH - chipH) / 2;
                int[] style = tagStyle(tag);
                drawOutlinedBox(ctx, cx, chipY, cx + chipW, chipY + chipH, style[0], style[1], 1);
                ctx.drawText(textRenderer, tag, cx + chipPadX, chipY + (chipH - 8) / 2, style[2], false);
                cx -= chipGap;
            }

            if (hiddenCount > 0) {
                String more = "+" + hiddenCount + " more";
                int moreW = textRenderer.getWidth(more) + chipPadX * 2;
                if (usedTagArea + moreW + (usedTagArea == 0 ? 0 : chipGap) <= maxTagArea) {
                    cx -= moreW;
                    int chipY = y + (headerH - chipH) / 2;
                    int[] style = tagStyle("default");
                    drawOutlinedBox(ctx, cx, chipY, cx + moreW, chipY + chipH, style[0], style[1], 1);
                    ctx.drawText(textRenderer, more, cx + chipPadX, chipY + (chipH - 8) / 2, style[2], false);
                    cx -= chipGap;
                }
            }

            tagsStartX = Math.max(headerLeft + 30, cx + chipGap);
        }

        // Triangle indicator + label
        String arrow = open ? "▼ " : "▶ ";
        int labelX = headerLeft + 4;
        int labelMaxW = Math.max(20, tagsStartX - labelX - 4);
        ctx.drawText(textRenderer, truncate(arrow + label, labelMaxW), labelX, y + (headerH - 8) / 2, headerColor, false);

        // Click target for toggle
        JsonObject toggleAction = new JsonObject();
        toggleAction.addProperty("type", "toggle_collapsible");
        toggleAction.addProperty("id", id);
        toggleAction.addProperty("defaultOpen", defOpen);
        addClickTarget(headerLeft, y, panelW - panelPadding * 2, headerH, toggleAction, null, Z_BASE + 1);

        // Render children if open
        if (open) {
            int childrenBg = colorVal(widget, "background", 0xFF0A0A1A);
            ctx.fill(panelX + panelPadding, y + headerH, panelX + panelW - panelPadding, y + h, childrenBg);

            int pad = intVal(widget, "padding", 2);
            renderWidgetsInRegion(ctx, arr(widget, "widgets"), panelX, panelW, panelPadding + pad,
                    y + headerH + pad, h - headerH - pad * 2, mouseX, mouseY, colLabel);
        }
    }

    private void renderScrollPanel(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                                   int titleHeight, int colLabel, int y, int h, int mouseX, int mouseY) {
        String id = strVal(widget, "id", "scroll_panel");
        int scrollbarW = intVal(widget, "scrollbarWidth", 3);
        int innerPad = intVal(widget, "padding", 0);
        JsonArray children = filterScrollChildren(widget);

        // Compute total children height
        int totalH = innerPad * 2;
        for (int ci = 0; ci < children.size(); ci++) {
            if (!children.get(ci).isJsonObject()) continue;
            JsonObject child = children.get(ci).getAsJsonObject();
            totalH += widgetHeight(child, strVal(child, "type", ""), panelW, panelPadding + scrollbarW);
        }

        int maxScroll = Math.max(0, totalH - h);
        int scroll = Math.min(scrollPanelOffsets.getOrDefault(id, 0), maxScroll);
        scrollPanelOffsets.put(id, scroll);

        // Background
        int bg = colorVal(widget, "background", 0);
        if (bg != 0) ctx.fill(panelX + panelPadding, y, panelX + panelW - panelPadding, y + h, bg);

        if (children.isEmpty()) {
            String emptyText = strVal(widget, "emptyText", "No results");
            int emptyColor = colorVal(widget, "emptyColor", 0xFF7482A3);
            ctx.drawText(textRenderer, emptyText, panelX + panelPadding + 4, y + 4, emptyColor, false);
            scrollRegions.add(new ScrollRegion(panelX + panelPadding, y, panelW - panelPadding * 2, h, id));
            return;
        }

        // Clip content to this region
        ctx.enableScissor(panelX + panelPadding, y, panelX + panelW - panelPadding - scrollbarW - 1, y + h);
        try {
            renderWidgetsInRegion(ctx, children, panelX, panelW - scrollbarW - 1, panelPadding + innerPad,
                    y - scroll + innerPad, h + scroll - innerPad, mouseX, mouseY, colLabel);
        } finally {
            ctx.disableScissor();
        }

        // Scrollbar
        if (maxScroll > 0) {
            int sbX = panelX + panelW - panelPadding - scrollbarW;
            int thumbH = Math.max(12, h * h / totalH);
            int thumbY = y + (int)((long)(h - thumbH) * scroll / maxScroll);
            int track = colorVal(widget, "scrollTrack", 0xFF1A1A28);
            int thumb = colorVal(widget, "scrollThumb", 0xFF4A4A6A);
            int activeColor = colorVal(widget, "scrollActive", 0xFF7070A0);
            boolean thumbHover = inBox(mouseX, mouseY, sbX, thumbY, scrollbarW, thumbH);
            ctx.fill(sbX, y, sbX + scrollbarW, y + h, track);
            ctx.fill(sbX, thumbY, sbX + scrollbarW, thumbY + thumbH, thumbHover ? activeColor : thumb);
        }

        // Register region for mouseScrolled
        scrollRegions.add(new ScrollRegion(panelX + panelPadding, y, panelW - panelPadding * 2, h, id));
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

    private void renderHint(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding, int y) {
        String text = strVal(widget, "text", "");
        int color = colorVal(widget, "color", 0xFF777788);
        if (!text.isEmpty()) {
            int maxW = Math.max(40, panelW - panelPadding * 2);
            if (boolVal(widget, "wrap", false)) {
                int lineHeight = Math.max(1, intVal(widget, "lineHeight", 10));
                List<OrderedText> lines = textRenderer.wrapLines(parseLegacy(text), maxW);
                int ty = y + 2;
                for (OrderedText line : lines) {
                    ctx.drawText(textRenderer, line, panelX + panelPadding, ty, color, false);
                    ty += lineHeight;
                }
            } else {
                ctx.drawText(textRenderer, text, panelX + panelPadding, y + 2, color, false);
            }
        }
    }

    private void renderSearchBox(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                                 int y, int h, int mouseX, int mouseY) {
        String id = strVal(widget, "id", "search");
        String value = textFieldValue(id);
        int cursor = Math.min(textFieldCursor.getOrDefault(id, value.length()), value.length());
        textFieldCursor.put(id, cursor);

        int x = panelX + panelPadding;
        int w = panelW - panelPadding * 2;
        boolean focused = id.equals(activeTextFieldId);
        boolean hover = inBox(mouseX, mouseY, x, y, w, h);

        int bg = colorVal(widget, focused ? "focusBackground" : "background", focused ? 0xFF121A2E : 0xFF101422);
        int outline = colorVal(widget, focused ? "focusOutline" : "outline", focused ? 0xFF6CA4D4 : 0x664A6A94);
        int textColor = colorVal(widget, "textColor", 0xFFE8EEF7);
        int placeholderColor = colorVal(widget, "placeholderColor", 0xFF7C8AA8);
        int iconColor = colorVal(widget, "iconColor", 0xFF86A8D1);
        int clearColor = colorVal(widget, "clearColor", 0xFF9BB3D4);

        drawOutlinedBox(ctx, x, y, x + w, y + h, bg, outline, 1);
        String prefix = strVal(widget, "prefix", "⌕");
        int prefixW = prefix.isEmpty() ? 0 : textRenderer.getWidth(prefix + " ");
        if (!prefix.isEmpty()) {
            ctx.drawText(textRenderer, prefix, x + 5, y + (h - 8) / 2, iconColor, false);
        }

        int textX = x + 5 + prefixW;
        int textY = y + (h - 8) / 2;
        int clearW = value.isEmpty() ? 0 : textRenderer.getWidth("[x]") + 8;
        int maxTextW = Math.max(30, w - (textX - x) - clearW - 6);

        if (value.isEmpty()) {
            String placeholder = strVal(widget, "placeholder", "Search...");
            ctx.drawText(textRenderer, truncate(placeholder, maxTextW), textX, textY, placeholderColor, false);
        } else {
            String shown = trimToRight(value, maxTextW);
            ctx.drawText(textRenderer, shown, textX, textY, textColor, false);
        }

        JsonObject focusAction = action("set_text_focus");
        focusAction.addProperty("id", id);
        addClickTarget(x, y, w, h, focusAction, null, Z_BASE + 1);

        if (!value.isEmpty()) {
            int cx = x + w - clearW + 3;
            int cy = y + (h - 8) / 2;
            boolean clearHover = inBox(mouseX, mouseY, cx - 3, y + 1, clearW - 2, h - 2);
            ctx.drawText(textRenderer, "[x]", cx, cy, clearHover ? 0xFFFFFFFF : clearColor, false);

            JsonObject clearAction = action("clear_text_state");
            clearAction.addProperty("id", id);
            addClickTarget(cx - 3, y + 1, clearW - 2, h - 2, clearAction, null, Z_BASE + 2);
        }

        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            String before = value.substring(0, cursor);
            String shownBefore = trimToRight(before, maxTextW);
            int caretX = textX + textRenderer.getWidth(shownBefore);
            ctx.fill(caretX, y + 3, caretX + 1, y + h - 3, textColor);
        }
    }

    private void renderFilterChips(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                                   int y, int h, int mouseX, int mouseY) {
        String stateId = strVal(widget, "stateId", "");
        JsonArray tokens = arr(widget, "tokens");
        if (tokens.isEmpty()) return;

        int chipH = intVal(widget, "chipHeight", 14);
        int gap = intVal(widget, "chipGap", 4);
        int padX = intVal(widget, "chipPaddingX", 6);
        int bg = colorVal(widget, "background", 0xFF0B0F1A);
        int outline = colorVal(widget, "outline", 0x55354A66);
        int activeBg = colorVal(widget, "activeBackground", 0xFF1E3958);
        int activeOutline = colorVal(widget, "activeOutline", 0xFF6CA4D4);
        int textColor = colorVal(widget, "textColor", 0xFFBFD2EA);
        int activeTextColor = colorVal(widget, "activeTextColor", 0xFFFFFFFF);

        int x = panelX + panelPadding;
        int right = panelX + panelW - panelPadding;
        int cx = x;
        int cy = y;

        for (int i = 0; i < tokens.size(); i++) {
            if (!tokens.get(i).isJsonObject()) continue;
            JsonObject tokenObj = tokens.get(i).getAsJsonObject();
            String label = strVal(tokenObj, "label", "");
            String token = strVal(tokenObj, "token", label);
            if (label.isBlank() || token.isBlank()) continue;

            int chipW = textRenderer.getWidth(label) + padX * 2;
            if (cx != x && cx + chipW > right) {
                cx = x;
                cy += chipH + gap;
            }

            boolean active = hasQueryToken(stateId, token);
            boolean hover = inBox(mouseX, mouseY, cx, cy, chipW, chipH);
            drawOutlinedBox(ctx, cx, cy, cx + chipW, cy + chipH,
                    active ? activeBg : bg,
                    active || hover ? activeOutline : outline,
                    1);
            ctx.drawText(textRenderer, label, cx + padX, cy + (chipH - 8) / 2,
                    active ? activeTextColor : textColor, false);

            JsonObject toggle = action("toggle_query_token");
            toggle.addProperty("stateId", stateId);
            toggle.addProperty("token", token);
            addClickTarget(cx, cy, chipW, chipH, toggle, null, Z_BASE + 1);

            cx += chipW + gap;
        }
    }

    private void renderMultiSelectDropdown(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                                           int y, int h, int mouseX, int mouseY, int colLabel) {
        String id = strVal(widget, "id", "multi_select");
        String stateId = strVal(widget, "stateId", id);
        int optionH = intVal(widget, "optionHeight", 14);
        int maxVisible = Math.max(1, intVal(widget, "maxVisible", 8));

        int bg = colorVal(widget, "buttonBg", 0xFF142033);
        int hoverBg = colorVal(widget, "buttonHover", 0xFF1E3A5F);
        int outline = colorVal(widget, "buttonOutline", 0x553A5A80);
        int hoverOutline = colorVal(widget, "buttonHoverOutline", 0xFF4A86C8);
        int outlineWidth = intVal(widget, "outlineWidth", 1);
        int textColor = colorVal(widget, "color", colLabel);
        int optionBg = colorVal(widget, "optionBg", 0xFF101828);
        int optionHover = colorVal(widget, "optionHover", 0xFF1C2F4A);
        int optionActive = colorVal(widget, "optionActive", 0xFF21456A);
        int optionOutline = colorVal(widget, "optionOutline", 0x663A5A80);

        JsonArray options = arr(widget, "options");
        double widthPct = doubleVal(widget, "widthPercent", 1.0);
        widthPct = Math.max(0.10, Math.min(1.0, widthPct));
        int contentW = panelW - panelPadding * 2;
        int w = Math.max(40, (int) Math.round(contentW * widthPct));
        String align = strVal(widget, "align", "left").toLowerCase(Locale.ROOT);
        int x = switch (align) {
            case "right" -> panelX + panelW - panelPadding - w;
            case "center" -> panelX + panelPadding + Math.max(0, (contentW - w) / 2);
            default -> panelX + panelPadding;
        };
        int selectedCount = 0;
        for (int i = 0; i < options.size(); i++) {
            if (!options.get(i).isJsonObject()) continue;
            JsonObject opt = options.get(i).getAsJsonObject();
            String token = strVal(opt, "token", strVal(opt, "label", ""));
            if (!token.isBlank() && hasQueryToken(stateId, token)) selectedCount++;
        }

        boolean open = id.equals(activeDropdownId);
        boolean hover = inBox(mouseX, mouseY, x, y, w, h);
        drawOutlinedBox(ctx, x, y, x + w, y + h, hover ? hoverBg : bg,
                hover ? hoverOutline : outline, outlineWidth);

        String baseLabel = strVal(widget, "label", "Filter Tags");
        String suffix = selectedCount == 0 ? "all" : selectedCount + " selected";
        String header = baseLabel + ": " + suffix + (open ? " ▲" : " ▼");
        ctx.drawText(textRenderer, truncate(header, Math.max(30, w - 12)), x + 6, y + (h - 8) / 2, textColor, false);

        JsonObject toggle = action("toggle_dropdown");
        toggle.addProperty("id", id);
        addClickTarget(x, y, w, h, toggle, null, intVal(widget, "z", Z_DROPDOWN));

        if (!open) return;

        int visible = Math.min(options.size(), maxVisible);
        int listH = visible * optionH;
        int listY = y + h + 2;
        if (listY + listH > height - 2) {
            listY = Math.max(2, y - listH - 2);
        }

        drawOutlinedBox(ctx, x, listY, x + w, listY + listH,
                colorVal(widget, "dropdownBg", 0xFF0C1220),
                colorVal(widget, "dropdownOutline", 0xAA4A6A94),
                1);

        for (int i = 0; i < visible; i++) {
            if (!options.get(i).isJsonObject()) continue;
            JsonObject opt = options.get(i).getAsJsonObject();
            String label = strVal(opt, "label", "Option");
            String token = strVal(opt, "token", label);
            int oy = listY + i * optionH;
            boolean selected = hasQueryToken(stateId, token);
            boolean optHover = inBox(mouseX, mouseY, x, oy, w, optionH);

            int fill = selected ? optionActive : optHover ? optionHover : optionBg;
            drawOutlinedBox(ctx, x, oy, x + w, oy + optionH, fill, optionOutline, 1);
            String rowText = (selected ? "[x] " : "[ ] ") + label;
            ctx.drawText(textRenderer, truncate(rowText, Math.max(24, w - 10)), x + 5, oy + (optionH - 8) / 2,
                    selected ? 0xFFFFFFFF : textColor, false);

            JsonObject action = action("toggle_query_token");
            action.addProperty("stateId", stateId);
            action.addProperty("token", token);
            addClickTarget(x, oy, w, optionH, action, null, intVal(widget, "z", Z_DROPDOWN + 1));
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
        // Check scroll panels first
        for (ScrollRegion region : scrollRegions) {
            if (mouseX >= region.x() && mouseX <= region.x() + region.w()
                    && mouseY >= region.y() && mouseY <= region.y() + region.h()) {
                int current = scrollPanelOffsets.getOrDefault(region.id(), 0);
                scrollPanelOffsets.put(region.id(), Math.max(0, (int)(current - verticalAmount * 12)));
                return true;
            }
        }
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

        if (activeTextFieldId != null) {
            activeTextFieldId = null;
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
        if (activeTextFieldId != null) {
            String id = activeTextFieldId;
            String value = textFieldValue(id);
            int cursorPos = Math.min(textFieldCursor.getOrDefault(id, value.length()), value.length());
            String next = value.substring(0, cursorPos) + chr + value.substring(cursorPos);
            setTextFieldValue(id, next);
            textFieldCursor.put(id, cursorPos + 1);
            return true;
        }

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

        if (handleTextFieldKeyPress(keyCode, modifiers)) {
            return true;
        }
        
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
            case "hint" -> hintWidgetHeight(w, panelW, panelPadding);
            case "search_box" -> intVal(w, "height", 18);
            case "filter_chips" -> filterChipsHeight(w, panelW, panelPadding);
            case "multi_select_dropdown" -> intVal(w, "height", 18);
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
            case "panel" -> {
                if (w.has("height")) yield w.get("height").getAsInt();
                JsonArray children = arr(w, "widgets");
                int pad = intVal(w, "padding", 0);
                int total = pad * 2;
                for (int ci = 0; ci < children.size(); ci++) {
                    if (!children.get(ci).isJsonObject()) continue;
                    JsonObject child = children.get(ci).getAsJsonObject();
                    total += widgetHeight(child, strVal(child, "type", ""), panelW, panelPadding);
                }
                yield total;
            }
            case "collapsible" -> {
                int headerH = intVal(w, "headerHeight", 16);
                String colId = strVal(w, "id", "");
                boolean defOpen = boolVal(w, "open", false);
                boolean open = colId.isBlank() ? defOpen : collapsibleOpen.getOrDefault(colId, defOpen);
                if (!open) yield headerH;
                JsonArray children = arr(w, "widgets");
                int pad = intVal(w, "padding", 2);
                int total = headerH + (pad * 2);
                for (int ci = 0; ci < children.size(); ci++) {
                    if (!children.get(ci).isJsonObject()) continue;
                    JsonObject child = children.get(ci).getAsJsonObject();
                    total += widgetHeight(child, strVal(child, "type", ""), panelW, panelPadding);
                }
                yield total;
            }
            case "scroll_panel" -> intVal(w, "height", 200);
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

    private int hintWidgetHeight(JsonObject w, int panelW, int panelPadding) {
        if (!boolVal(w, "wrap", false)) {
            return intVal(w, "height", 12);
        }
        String text = strVal(w, "text", "");
        if (text.isEmpty()) {
            return intVal(w, "height", 12);
        }
        int maxW = Math.max(40, panelW - panelPadding * 2);
        int lineHeight = Math.max(1, intVal(w, "lineHeight", 10));
        List<OrderedText> lines = textRenderer.wrapLines(parseLegacy(text), maxW);
        int rows = Math.max(1, lines.size());
        return rows * lineHeight + 4;
    }

    private int filterChipsHeight(JsonObject widget, int panelW, int panelPadding) {
        JsonArray tokens = arr(widget, "tokens");
        if (tokens.isEmpty()) return intVal(widget, "height", 0);

        int chipH = intVal(widget, "chipHeight", 14);
        int gap = intVal(widget, "chipGap", 4);
        int padX = intVal(widget, "chipPaddingX", 6);
        int availableW = Math.max(40, panelW - panelPadding * 2);

        int rowCount = 1;
        int rowW = 0;
        for (int i = 0; i < tokens.size(); i++) {
            if (!tokens.get(i).isJsonObject()) continue;
            JsonObject tok = tokens.get(i).getAsJsonObject();
            String label = strVal(tok, "label", "");
            if (label.isBlank()) continue;
            int chipW = textRenderer.getWidth(label) + padX * 2;
            int needed = rowW == 0 ? chipW : rowW + gap + chipW;
            if (needed > availableW && rowW > 0) {
                rowCount++;
                rowW = chipW;
            } else {
                rowW = needed;
            }
        }

        return rowCount * chipH + Math.max(0, rowCount - 1) * gap;
    }

    private JsonArray filterScrollChildren(JsonObject widget) {
        JsonArray source = arr(widget, "widgets");
        String searchState = strVal(widget, "searchState", "");
        FilterState state = searchState.isBlank() ? null : filterState(searchState);
        if (state == null || (state.textQuery.isBlank() && state.selectedTags.isEmpty() && state.selectedItems.isEmpty())) {
            return source;
        }

        List<String> terms = new ArrayList<>();
        if (state != null && !state.textQuery.isBlank()) {
            for (String token : state.textQuery.split("\\s+")) {
                String t = token.trim().toLowerCase(Locale.ROOT);
                if (!t.isEmpty()) terms.add(t);
            }
        }

        List<JsonObject> matched = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            if (!source.get(i).isJsonObject()) continue;
            JsonObject child = source.get(i).getAsJsonObject();
            String type = strVal(child, "type", "");
            if ("spacer".equals(type)) continue;
            if (matchesSearch(child, terms, state)) matched.add(child);
        }

        JsonArray filtered = new JsonArray();
        for (int i = 0; i < matched.size(); i++) {
            if (i > 0) {
                JsonObject spacer = new JsonObject();
                spacer.addProperty("type", "spacer");
                spacer.addProperty("height", 2);
                filtered.add(spacer);
            }
            filtered.add(matched.get(i));
        }
        return filtered;
    }

    private boolean matchesSearch(JsonObject widget, List<String> terms, FilterState state) {
        String fallback = strVal(widget, "label", "");
        JsonObject catalog = obj(widget, "catalog");
        JsonObject facets = obj(catalog, "facets");
        String searchText = strVal(catalog, "searchText", fallback).toLowerCase(Locale.ROOT);
        Set<String> tags = facetValues(facets, "tags");
        Set<String> items = facetValues(facets, "items");

        for (String term : terms) {
            if (!searchText.contains(term)) {
                return false;
            }
        }

        if (state != null) {
            if (!state.selectedTags.isEmpty() && !tags.containsAll(state.selectedTags)) return false;
            if (!state.selectedItems.isEmpty() && !items.containsAll(state.selectedItems)) return false;
        }
        return true;
    }

    private boolean hasQueryToken(String stateId, String token) {
        if (stateId == null || stateId.isBlank() || token == null || token.isBlank()) return false;
        String target = token.toLowerCase(Locale.ROOT).trim();
        FilterState state = filterState(stateId);
        if (target.startsWith("tag:")) return state.selectedTags.contains(target.substring(4));
        if (target.startsWith("item:")) return state.selectedItems.contains(target.substring(5));
        return false;
    }

    private boolean handleTextFieldKeyPress(int keyCode, int modifiers) {
        if (activeTextFieldId == null) return false;

        String id = activeTextFieldId;
        String value = textFieldValue(id);
        int cursorPos = Math.min(textFieldCursor.getOrDefault(id, value.length()), value.length());

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            activeTextFieldId = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
            String clip = client.keyboard.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                String insert = clip.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ');
                String next = value.substring(0, cursorPos) + insert + value.substring(cursorPos);
                setTextFieldValue(id, next);
                textFieldCursor.put(id, cursorPos + insert.length());
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (cursorPos > 0) {
                String next = value.substring(0, cursorPos - 1) + value.substring(cursorPos);
                setTextFieldValue(id, next);
                textFieldCursor.put(id, cursorPos - 1);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (cursorPos < value.length()) {
                String next = value.substring(0, cursorPos) + value.substring(cursorPos + 1);
                setTextFieldValue(id, next);
                textFieldCursor.put(id, cursorPos);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            textFieldCursor.put(id, Math.max(0, cursorPos - 1));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            textFieldCursor.put(id, Math.min(value.length(), cursorPos + 1));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            textFieldCursor.put(id, 0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            textFieldCursor.put(id, value.length());
            return true;
        }

        return false;
    }

    private FilterState filterState(String stateId) {
        return filterStates.computeIfAbsent(stateId, ignored -> new FilterState());
    }

    private String textFieldValue(String id) {
        if (filterStates.containsKey(id) || id.toLowerCase(Locale.ROOT).contains("search")) {
            return filterState(id).textQuery;
        }
        return textFieldValues.getOrDefault(id, "");
    }

    private void setTextFieldValue(String id, String value) {
        if (filterStates.containsKey(id) || id.toLowerCase(Locale.ROOT).contains("search")) {
            filterState(id).textQuery = value;
            return;
        }
        textFieldValues.put(id, value);
    }

    private void toggleFacetToken(String stateId, String token) {
        FilterState state = filterState(stateId);
        if (token.startsWith("tag:")) {
            toggleSetValue(state.selectedTags, token.substring(4));
            return;
        }
        if (token.startsWith("item:")) {
            toggleSetValue(state.selectedItems, token.substring(5));
        }
    }

    private void toggleSetValue(Set<String> values, String entry) {
        if (!values.remove(entry)) {
            values.add(entry);
        }
    }

    private Set<String> facetValues(JsonObject facets, String key) {
        Set<String> values = new HashSet<>();
        JsonArray entries = facets.has(key) && facets.get(key).isJsonArray() ? facets.getAsJsonArray(key) : new JsonArray();
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).isJsonPrimitive()) continue;
            values.add(entries.get(i).getAsString().toLowerCase(Locale.ROOT));
        }
        return values;
    }

    private String trimToRight(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (textRenderer.getWidth(text) <= maxWidth) return text;

        String out = text;
        while (!out.isEmpty() && textRenderer.getWidth(out) > maxWidth) {
            out = out.substring(1);
        }
        return out;
    }

    private int[] tagStyle(String tag) {
        String t = tag == null ? "" : tag.toLowerCase(Locale.ROOT);
        return switch (t) {
            case "offense", "execute" -> new int[] {0xFF2A1620, 0xFFB8557A, 0xFFFFC6D8};
            case "defense", "armor" -> new int[] {0xFF132033, 0xFF4F7FB9, 0xFFCAE3FF};
            case "utility", "mobility", "tool" -> new int[] {0xFF152A20, 0xFF57A884, 0xFFCFFFE8};
            case "reactive", "threshold" -> new int[] {0xFF2D2212, 0xFFD19A4A, 0xFFFFE4BF};
            case "active", "instant" -> new int[] {0xFF261B31, 0xFF9369C8, 0xFFEDD9FF};
            case "passive", "cooldown" -> new int[] {0xFF21242D, 0xFF8691A8, 0xFFE1E7F3};
            case "ranged" -> new int[] {0xFF1D2433, 0xFF6E8FD0, 0xFFD9E6FF};
            case "melee" -> new int[] {0xFF311D1D, 0xFFC37272, 0xFFFFDDDD};
            case "sustain" -> new int[] {0xFF1E2A1B, 0xFF82B16B, 0xFFE4FFD9};
            default -> new int[] {0xFF232733, 0xFF707B94, 0xFFD9DFEC};
        };
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


