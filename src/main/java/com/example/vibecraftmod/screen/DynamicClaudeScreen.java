package com.example.vibecraftmod.screen;

import com.example.vibecraftmod.DebugConfig;
import com.example.vibecraftmod.config.ClientConfig;
import com.example.vibecraftmod.config.PluginConfig;
import com.example.vibecraftmod.hud.ClaudeHudState;
import com.example.vibecraftmod.network.ModPackets;
import com.example.vibecraftmod.settings.ClaudeSettings;
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

public class DynamicClaudeScreen extends Screen {

    private record ClickTarget(int x, int y, int w, int h, JsonObject action, String optionValue) {}

    private String inputText = "";
    private int cursor = 0;
    private int scrollOffset = 0;
    private int historyTop = 0;
    private int historyBottom = 0;
    private int historyLineHeight = 11;
    private final List<ClickTarget> clickTargets = new ArrayList<>();
    private final Map<String, Integer> selectedTabByContainer = new HashMap<>();
    private String activeModalId = null;
    private String activeDropdownId = null;

    public DynamicClaudeScreen() {
        super(Text.literal("Plugin Screen"));
        // Preserve server-selected screen when opened via open_screen.
        ScreenManager.getActiveScreen();
    }

    @Override
    protected void init() {
        ScreenDef activeScreen = ScreenManager.getActiveScreen();
        String activePlugin = activeScreen != null ? activeScreen.plugin : "vibecraft";
        ModPackets.requestHistory(activePlugin);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Get active screen and its plugin
        ScreenDef activeScreen = ScreenManager.getActiveScreen();
        if (activeScreen == null) return;
        // title is set in constructor: super(Text.literal(activeScreen.title))
        clickTargets.clear();

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
            : strVal(schema, "title", "Claude Chat");
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
            fixedHeight += widgetHeight(w, type);
        }
        int remaining = Math.max(120, height - titleHeight - fixedHeight - panelPadding);
        int flexHeight = flexCount == 0 ? 0 : remaining / flexCount;

        for (int i = 0; i < widgets.size(); i++) {
            JsonObject w = widgets.get(i).getAsJsonObject();
            String type = strVal(w, "type", "");
            if ("modal".equals(type)) continue;
            int wh = ("history".equals(type) && boolVal(w, "flex", false)) ? flexHeight : widgetHeight(w, type);

            switch (type) {
                case "toolbar" -> renderToolbar(ctx, w, panelX, panelW, panelPadding, y, titleHeight, mouseX, mouseY);
                case "history" -> renderHistory(ctx, w, panelX, panelW, panelPadding, y, wh, mouseX, mouseY);
                case "question_options" -> renderQuestionOptions(ctx, w, panelX, panelW, panelPadding, y, mouseX, mouseY);
                case "input" -> renderInput(ctx, w, panelX, panelW, panelPadding, y, wh, colLabel);
                case "hint" -> renderHint(ctx, w, panelX, panelPadding, y);
                case "text" -> renderText(ctx, w, panelX, panelW, panelPadding, y);
                case "action_row" -> renderActionRow(ctx, w, panelX, panelW, panelPadding, y, mouseX, mouseY);
                case "dropdown" -> renderDropdown(ctx, w, panelX, panelW, panelPadding, y, mouseX, mouseY, colLabel);
                case "setting_toggle" -> renderSettingToggle(ctx, w, panelX, panelW, panelPadding, y, mouseX, mouseY);
                case "state_badge" -> renderStateBadge(ctx, w, panelX, panelW, panelPadding, y);
                case "tab_container" -> renderTabContainer(ctx, w, panelX, panelW, panelPadding, y, wh, mouseX, mouseY, colLabel);
                case "divider" -> ctx.fill(panelX + panelPadding, y, panelX + panelW - panelPadding, y + 1, colDivider);
                case "spacer" -> { }
                default -> { }
            }

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

    private void renderToolbar(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                               int y, int titleHeight, int mouseX, int mouseY) {
        String closeLabel = strVal(widget, "closeLabel", "[x]");
        String optionsLabel = strVal(widget, "optionsLabel", "[options]");
        String helpLabel = strVal(widget, "helpLabel", "[help]");
        boolean showClose = boolVal(widget, "showClose", true);
        boolean showOptions = boolVal(widget, "showOptions", true);
        boolean showHelp = boolVal(widget, "showHelp", false);
        boolean showInlineStatus = boolVal(widget, "showInlineStatus", true);
        int btnBg = colorVal(widget, "buttonBg", 0xFF142033);
        int btnHover = colorVal(widget, "buttonHover", 0xFF1E3A5F);
        int btnDanger = colorVal(widget, "buttonDanger", 0xFF3A1010);
        int btnDangerHover = colorVal(widget, "buttonDangerHover", 0xFF5A1818);
        int btnPaddingX = intVal(widget, "buttonPaddingX", 5);
        int statusColor = colorVal(widget, "statusColor", 0xFF96A6C2);

        int xRight = panelX + panelW - panelPadding;
        if (showClose) {
            int w = textRenderer.getWidth(closeLabel);
            int x = xRight - w;
            boolean hover = inBox(mouseX, mouseY, x - btnPaddingX, y + 1, w + btnPaddingX * 2, titleHeight - 2);
            ctx.fill(x - btnPaddingX, y + 1, x + w + btnPaddingX, y + titleHeight - 1, hover ? btnDangerHover : btnDanger);
            ctx.drawText(textRenderer, Text.literal(closeLabel).formatted(hover ? Formatting.RED : Formatting.GRAY),
                    x, y + 3, 0xFFFFFFFF, false);
            JsonObject closeAction = obj(widget, "closeAction");
            if (closeAction.entrySet().isEmpty()) closeAction = action("close_screen");
            clickTargets.add(new ClickTarget(x - btnPaddingX, y + 1, w + btnPaddingX * 2, titleHeight - 2, closeAction, null));
            xRight = x - 8;
        }
        if (showHelp) {
            int w = textRenderer.getWidth(helpLabel);
            int x = xRight - w;
            boolean hover = inBox(mouseX, mouseY, x - btnPaddingX, y + 1, w + btnPaddingX * 2, titleHeight - 2);
            ctx.fill(x - btnPaddingX, y + 1, x + w + btnPaddingX, y + titleHeight - 1, hover ? btnHover : btnBg);
            ctx.drawText(textRenderer, Text.literal(helpLabel).formatted(hover ? Formatting.WHITE : Formatting.AQUA),
                    x, y + 3, 0xFFFFFFFF, false);
            JsonObject helpAction = obj(widget, "helpAction");
            if (helpAction.entrySet().isEmpty()) {
                helpAction = action("open_modal");
                helpAction.addProperty("id", "help-modal");
            }
            clickTargets.add(new ClickTarget(x - btnPaddingX, y + 1, w + btnPaddingX * 2, titleHeight - 2, helpAction, null));
            xRight = x - 8;
        }
        if (showOptions) {
            int w = textRenderer.getWidth(optionsLabel);
            int x = xRight - w;
            boolean hover = inBox(mouseX, mouseY, x - btnPaddingX, y + 1, w + btnPaddingX * 2, titleHeight - 2);
            ctx.fill(x - btnPaddingX, y + 1, x + w + btnPaddingX, y + titleHeight - 1, hover ? btnHover : btnBg);
            ctx.drawText(textRenderer, Text.literal(optionsLabel).formatted(hover ? Formatting.WHITE : Formatting.AQUA),
                    x, y + 3, 0xFFFFFFFF, false);
            JsonObject optionsAction = obj(widget, "optionsAction");
            if (optionsAction.entrySet().isEmpty()) optionsAction = action("open_options");
            clickTargets.add(new ClickTarget(x - btnPaddingX, y + 1, w + btnPaddingX * 2, titleHeight - 2, optionsAction, null));
            xRight = x - 8;
        }

        if (showInlineStatus) {
            String statusTemplate = strVal(widget, "statusText", "").trim();
            String status;
            if (!statusTemplate.isEmpty()) {
                status = resolveTemplate(statusTemplate);
            } else {
                String tool = ClaudeHudState.getCurrentTool();
                String idleLabel = strVal(widget, "idleLabel", "Idle");
                String toolLabel = tool == null ? idleLabel : "[" + tool + "]";
                status = toolLabel + "  History: " + ClaudeHudState.getLines().size() + "  Streaming: " + ClaudeHudState.isStreaming();
            }
            String clipped = truncate(status, Math.max(40, xRight - (panelX + panelPadding)));
            ctx.drawText(textRenderer, clipped, panelX + panelPadding, y + 3, statusColor, false);
        }
    }

    private void renderHistory(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                               int y, int h, int mouseX, int mouseY) {
        int lineHeight = intVal(widget, "lineHeight", 11);
        historyLineHeight = lineHeight;
        boolean showThoughts = boolVal(widget, "showThoughts", ClaudeSettings.getBool("ui.thoughts_visible"));
        int scrollbarW = intVal(widget, "scrollbarWidth", 3);

        List<ClaudeHudState.HudLine> lines = ClaudeHudState.getLines();
        List<ClaudeHudState.HudLine> display = new ArrayList<>();
        for (ClaudeHudState.HudLine line : lines) {
            if (line.thought() && !showThoughts && !ClaudeHudState.isStreaming()) continue;
            display.add(line);
        }

        String indicator = currentIndicator();
        if (!indicator.isEmpty()) {
            display.add(new ClaudeHudState.HudLine(indicator, ColorScheme.get().tool(), false, true));
        }

        int textX = panelX + panelPadding;
        int textW = panelW - panelPadding * 2 - scrollbarW;
        int visible = Math.max(1, h / lineHeight);
        int maxScroll = Math.max(0, display.size() - visible);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        int start = Math.max(0, display.size() - visible - scrollOffset);
        int end = Math.min(display.size(), start + visible);

        int ty = y;
        for (int i = start; i < end; i++) {
            ClaudeHudState.HudLine line = display.get(i);
            String text = truncate(line.text(), textW);
            ctx.drawText(textRenderer, text, textX, ty, 0xFF000000 | (line.color() & 0x00FFFFFF), false);
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
        ClaudeHudState.PendingQuestion q = ClaudeHudState.getQuestion();
        if (q == null || q.options().isEmpty()) return;

        int buttonH = intVal(widget, "buttonHeight", 16);
        int gap = intVal(widget, "buttonGap", 2);
        int btnBg = colorVal(widget, "buttonBg", 0xFF142033);
        int btnHover = colorVal(widget, "buttonHover", 0xFF1E3A5F);

        for (int i = 0; i < q.options().size(); i++) {
            int top = y + i * (buttonH + gap);
            boolean hover = inBox(mouseX, mouseY, panelX + panelPadding, top, panelW - panelPadding * 2, buttonH);
            ctx.fill(panelX + panelPadding, top, panelX + panelW - panelPadding, top + buttonH,
                    hover ? btnHover : btnBg);
            String option = q.options().get(i);
            ctx.drawText(textRenderer, "[" + (char)('A' + i) + "] " + option,
                    panelX + panelPadding + 6, top + 4, 0xFFFFFFFF, false);
            JsonObject action = obj(widget, "action");
            if (action.entrySet().isEmpty()) action = action("send_message");
            clickTargets.add(new ClickTarget(panelX + panelPadding, top, panelW - panelPadding * 2, buttonH,
                    action, option));
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
            String rem = paragraph;
            while (!rem.isEmpty()) {
                String fits = textRenderer.trimToWidth(rem, maxW);
                if (fits.isEmpty()) fits = rem.substring(0, 1);
                int rowX = centered ? panelX + (panelW - textRenderer.getWidth(fits)) / 2 : x;
                ctx.drawText(textRenderer, fits, rowX, ty, color, false);
                ty += historyLineHeight;
                rem = rem.substring(fits.length());
            }
        }
    }

    private void renderStateBadge(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding, int y) {
        String bind = strVal(widget, "bind", "streaming");
        String text;
        if ("current_tool".equals(bind)) {
            String tool = ClaudeHudState.getCurrentTool();
            String detail = ClaudeHudState.getCurrentToolDetail();
            text = tool == null ? strVal(widget, "emptyText", "Idle") : "[" + tool + "] " + (detail == null ? "" : detail);
        } else {
            text = ClaudeHudState.isStreaming()
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
        int totalGap = gap * (buttons.size() - 1);
        int avail = panelW - panelPadding * 2 - totalGap;
        int eachW = Math.max(24, avail / buttons.size());

        int x = panelX + panelPadding;
        for (int i = 0; i < buttons.size(); i++) {
            if (!buttons.get(i).isJsonObject()) continue;
            JsonObject b = buttons.get(i).getAsJsonObject();
            String label = strVal(b, "label", "Action");
            boolean hover = inBox(mouseX, mouseY, x, y, eachW, h);
            ctx.fill(x, y, x + eachW, y + h, hover ? hoverBg : bg);
            int tx = x + Math.max(4, (eachW - textRenderer.getWidth(label)) / 2);
            ctx.drawText(textRenderer, label, tx, y + (h - 8) / 2, fg, false);
            JsonObject action = obj(b, "action");
            if (!action.entrySet().isEmpty()) {
                clickTargets.add(new ClickTarget(x, y, eachW, h, action, null));
            }
            x += eachW + gap;
        }
    }

    private void renderSettingToggle(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                                     int y, int mouseX, int mouseY) {
        String key = strVal(widget, "key", "");
        if (key.isBlank()) return;

        String label = strVal(widget, "label", key);
        boolean current = ClaudeSettings.getBool(key);
        String right = current ? strVal(widget, "trueLabel", "On") : strVal(widget, "falseLabel", "Off");
        int h = intVal(widget, "height", 18);
        int fg = colorVal(widget, "color", 0xFFCCCCCC);
        int bg = colorVal(widget, "background", 0xFF142033);
        int hoverBg = colorVal(widget, "hover", 0xFF1E3A5F);

        int x = panelX + panelPadding;
        int w = panelW - panelPadding * 2;
        boolean hover = inBox(mouseX, mouseY, x, y, w, h);
        ctx.fill(x, y, x + w, y + h, hover ? hoverBg : bg);
        ctx.drawText(textRenderer, label, x + 6, y + (h - 8) / 2, fg, false);
        ctx.drawText(textRenderer, right, x + w - 6 - textRenderer.getWidth(right), y + (h - 8) / 2,
                current ? 0xFF55FF55 : 0xFFAAAAAA, false);

        JsonObject action = action("toggle_setting");
        action.addProperty("key", key);
        action.addProperty("trueValue", strVal(widget, "trueValue", "true"));
        action.addProperty("falseValue", strVal(widget, "falseValue", "false"));
        clickTargets.add(new ClickTarget(x, y, w, h, action, null));
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
            ctx.fill(x, y, x + tw, y + tabH, isActive ? activeBg : hover ? activeBg : bg);
            ctx.drawText(textRenderer, label, x + 6, y + (tabH - 8) / 2, isActive ? activeFg : fg, false);

            JsonObject action = action("set_tab");
            action.addProperty("container", containerId);
            action.addProperty("tabIndex", i);
            clickTargets.add(new ClickTarget(x, y, tw, tabH, action, null));

            x += tw + gap;
        }

        int contentY = y + tabH + 3;
        int contentH = Math.max(0, h - tabH - 3);
        ctx.fill(panelX + panelPadding, contentY - 1, panelX + panelW - panelPadding, contentY, 0xFF2A2A3A);

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
            fixedHeight += widgetHeight(w, type);
        }
        int flexHeight = flexCount == 0 ? 0 : Math.max(0, (regionH - fixedHeight) / flexCount);

        int y = regionY;
        for (int i = 0; i < widgets.size(); i++) {
            if (!widgets.get(i).isJsonObject()) continue;
            JsonObject w = widgets.get(i).getAsJsonObject();
            String type = strVal(w, "type", "");
            if ("modal".equals(type)) continue;
            int wh = ("history".equals(type) && boolVal(w, "flex", false)) ? flexHeight : widgetHeight(w, type);
            if (y + wh > regionY + regionH) break;

            switch (type) {
                case "history" -> renderHistory(ctx, w, panelX, panelW, panelPadding, y, wh, mouseX, mouseY);
                case "question_options" -> renderQuestionOptions(ctx, w, panelX, panelW, panelPadding, y, mouseX, mouseY);
                case "input" -> renderInput(ctx, w, panelX, panelW, panelPadding, y, wh, colLabel);
                case "hint" -> renderHint(ctx, w, panelX, panelPadding, y);
                case "text" -> renderText(ctx, w, panelX, panelW, panelPadding, y);
                case "action_row" -> renderActionRow(ctx, w, panelX, panelW, panelPadding, y, mouseX, mouseY);
                case "dropdown" -> renderDropdown(ctx, w, panelX, panelW, panelPadding, y, mouseX, mouseY, colLabel);
                case "setting_toggle" -> renderSettingToggle(ctx, w, panelX, panelW, panelPadding, y, mouseX, mouseY);
                case "state_badge" -> renderStateBadge(ctx, w, panelX, panelW, panelPadding, y);
                case "tab_container" -> renderTabContainer(ctx, w, panelX, panelW, panelPadding, y, wh, mouseX, mouseY, colLabel);
                case "divider" -> ctx.fill(panelX + panelPadding, y, panelX + panelW - panelPadding, y + 1, 0xFF2A2A3A);
                case "spacer" -> { }
                default -> { }
            }
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

        ctx.fill(x, y, x + modalW, y + modalH, bodyBg);
        ctx.fill(x, y, x + modalW, y + titleH, titleBg);
        ctx.fill(x + 1, y + titleH, x + modalW - 1, y + titleH + 1, line);

        String title = strVal(widget, "title", "Modal");
        ctx.drawText(textRenderer, title, x + pad, y + (titleH - 8) / 2, colLabel, false);

        String close = strVal(widget, "closeLabel", "[x]");
        int cw = textRenderer.getWidth(close);
        int cx = x + modalW - pad - cw;
        boolean hover = inBox(mouseX, mouseY, cx - 2, y + 1, cw + 4, titleH - 2);
        ctx.fill(cx - 2, y + 1, cx + cw + 2, y + titleH - 1, hover ? 0xFF3A1010 : 0xFF142033);
        ctx.drawText(textRenderer, close, cx, y + (titleH - 8) / 2, hover ? 0xFFFF7777 : 0xFFAAAAAA, false);
        clickTargets.add(new ClickTarget(cx - 2, y + 1, cw + 4, titleH - 2, action("close_modal"), null));

        if (boolVal(widget, "closeOnBackdrop", true)) {
            JsonObject closeAction = action("close_modal");
            clickTargets.add(new ClickTarget(0, 0, width, y, closeAction, null));
            clickTargets.add(new ClickTarget(0, y + modalH, width, height - (y + modalH), closeAction, null));
            clickTargets.add(new ClickTarget(0, y, x, modalH, closeAction, null));
            clickTargets.add(new ClickTarget(x + modalW, y, width - (x + modalW), modalH, closeAction, null));
        }

        JsonArray children = arr(widget, "widgets");
        int bodyY = y + titleH + 3;
        int bodyH = Math.max(0, modalH - titleH - 4);
        renderWidgetsInRegion(ctx, children, x, modalW, pad, bodyY, bodyH, mouseX, mouseY, colLabel);
    }

    private void renderInput(DrawContext ctx, JsonObject widget, int panelX, int panelW, int panelPadding,
                             int y, int h, int colLabel) {
        int divider = colorVal(widget, "divider", 0xFF2A2A3A);
        String prompt = strVal(widget, "prompt", "◆");
        String placeholder = strVal(widget, "placeholder", "Message Claude...");

        ctx.fill(panelX + panelPadding, y, panelX + panelW - panelPadding, y + 1, divider);

        int inputY = y + 6;
        int promptW = textRenderer.getWidth(prompt + " ");
        int inputX = panelX + panelPadding + promptW;
        int maxW = panelW - panelPadding * 2 - promptW;
        int userColor = ColorScheme.get().user() | 0xFF000000;

        ctx.drawText(textRenderer, Text.literal(prompt).formatted(Formatting.AQUA), panelX + panelPadding, inputY,
                colLabel, false);

        List<OrderedText> wrapped = textRenderer.wrapLines(parseLegacy(inputText.isEmpty() ? " " : inputText), maxW);
        if (inputText.isEmpty()) {
            ctx.drawText(textRenderer, Text.literal(placeholder).formatted(Formatting.DARK_GRAY), inputX, inputY,
                    0xFF555555, false);
        } else {
            int ty = inputY;
            for (OrderedText ot : wrapped) {
                ctx.drawText(textRenderer, ot, inputX, ty, userColor, false);
                ty += historyLineHeight;
            }
        }

        long nowMs = System.currentTimeMillis();
        if ((nowMs / 500) % 2 == 0) {
            int line = 0;
            int col = 0;
            if (!inputText.isEmpty()) {
                String before = inputText.substring(0, Math.min(cursor, inputText.length()));
                List<String> rows = wrapTextRows(before, maxW);
                line = Math.max(0, rows.size() - 1);
                col = textRenderer.getWidth(rows.get(rows.size() - 1));
            }
            int cy = inputY + line * historyLineHeight;
            ctx.fill(inputX + col, cy, inputX + col + 1, cy + textRenderer.fontHeight, userColor);
        }
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
        int x = panelX + panelPadding;
        int w = panelW - panelPadding * 2;
        boolean open = id.equals(activeDropdownId);

        boolean hover = inBox(mouseX, mouseY, x, y, w, h);
        ctx.fill(x, y, x + w, y + h, hover ? hoverBg : bg);
        String headerLabel = label + (open ? " ▲" : " ▼");
        ctx.drawText(textRenderer, headerLabel, x + 6, y + (h - 8) / 2, colLabel, false);

        JsonObject toggle = action("toggle_dropdown");
        toggle.addProperty("id", id);
        clickTargets.add(new ClickTarget(x, y, w, h, toggle, null));

        if (!open) return;

        JsonArray options = arr(widget, "options");
        int oy = y + h;
        for (int i = 0; i < options.size(); i++) {
            if (!options.get(i).isJsonObject()) continue;
            JsonObject opt = options.get(i).getAsJsonObject();
            String optLabel = strVal(opt, "label", "Option");
            JsonObject optAction = obj(opt, "action");
            boolean optHover = inBox(mouseX, mouseY, x, oy, w, optionH);
            ctx.fill(x, oy, x + w, oy + optionH, optHover ? hoverBg : bg);
            ctx.drawText(textRenderer, optLabel, x + 6, oy + (optionH - 8) / 2, colLabel, false);
            if (!optAction.entrySet().isEmpty()) {
                clickTargets.add(new ClickTarget(x, oy, w, optionH, optAction, null));
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
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        for (int i = clickTargets.size() - 1; i >= 0; i--) {
            ClickTarget target = clickTargets.get(i);
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
        String activePlugin = activeScreen != null ? activeScreen.plugin : "vibecraft";
        
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
            boolean next = !ClaudeSettings.getBool("ui.thoughts_visible");
            String value = String.valueOf(next);
            ClaudeSettings.set("ui.thoughts_visible", value);
            ModPackets.sendSetSetting(activePlugin, "ui.thoughts_visible", value);
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
            client.setScreen(new ClaudeSettingsScreen(this));
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
        if (!ClaudeHudState.isStreaming()) return "";
        String tool = ClaudeHudState.getCurrentTool();
        String detail = ClaudeHudState.getCurrentToolDetail();
        return tool != null ? "⏳ [" + tool + "] " + (detail != null ? detail : "") : "⏳ Processing...";
    }

    private int widgetHeight(JsonObject w, String type) {
        return switch (type) {
            case "toolbar" -> intVal(w, "height", 14);
            case "history" -> intVal(w, "height", 220);
            case "question_options" -> {
                ClaudeHudState.PendingQuestion q = ClaudeHudState.getQuestion();
                if (q == null || q.options().isEmpty()) yield 0;
                int buttonH = intVal(w, "buttonHeight", 16);
                int gap = intVal(w, "buttonGap", 2);
                yield q.options().size() * (buttonH + gap) + 4;
            }
            case "input" -> intVal(w, "height", 72);
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

    private void runAction(JsonObject action, String optionValue) {
        if (action == null || action.entrySet().isEmpty()) return;
        String type = strVal(action, "type", "");
        ScreenDef activeScreen = ScreenManager.getActiveScreen();
        String defaultPlugin = activeScreen != null ? activeScreen.plugin : "vibecraft";
        String targetPlugin = strVal(action, "plugin", defaultPlugin);
        if (DebugConfig.DEBUG_ACTIONS) {
            System.out.println("[VibeCraftMod] runAction: type=" + type
                    + ", targetPlugin=" + targetPlugin
                    + ", optionValue=" + (optionValue == null ? "<null>" : optionValue)
                    + ", modal=" + (activeModalId == null ? "<none>" : activeModalId));
        }
        switch (type) {
            case "close_screen" -> close();
            case "open_options" -> {
                if (client != null) client.setScreen(new ClaudeSettingsScreen(this));
            }
            case "open_help" -> openHelpModal();
            case "open_modal" -> {
                String id = strVal(action, "id", "");
                if (!id.isBlank()) activeModalId = id;
            }
            case "close_modal" -> activeModalId = null;
            case "toggle_dropdown" -> {
                String id = strVal(action, "id", "");
                if (!id.isBlank()) {
                    activeDropdownId = id.equals(activeDropdownId) ? null : id;
                }
            }
            case "request_history", "refresh_schema" -> ModPackets.requestHistory(targetPlugin);
            case "send_message" -> {
                String text = resolveActionText(strVal(action, "text", "${option}"), optionValue);
                if (!text.isBlank()) {
                    ClaudeHudState.addLine("> " + text, ColorScheme.get().user());
                    ModPackets.sendInput(text, targetPlugin);
                    if (optionValue != null) ClaudeHudState.clearQuestion();
                }
                activeDropdownId = null;
            }
            case "append_input" -> {
                String value = resolveActionText(strVal(action, "text", ""), optionValue);
                if (!value.isEmpty()) {
                    inputText = inputText.substring(0, cursor) + value + inputText.substring(cursor);
                    cursor += value.length();
                }
            }
            case "replace_input" -> {
                inputText = resolveActionText(strVal(action, "text", ""), optionValue);
                cursor = inputText.length();
            }
            case "clear_input" -> {
                inputText = "";
                cursor = 0;
            }
            case "clear_history" -> {
                clearHistoryViewAndServer(targetPlugin);
            }
            case "submit_input" -> {
                String text = inputText.trim();
                if (!text.isEmpty()) {
                    ClaudeHudState.addLine("> " + text, ColorScheme.get().user());
                    ModPackets.sendInput(text, targetPlugin);
                    inputText = "";
                    cursor = 0;
                }
            }
            case "set_setting" -> {
                String key = strVal(action, "key", "");
                String value = resolveActionText(strVal(action, "value", ""), optionValue);
                if (!key.isBlank()) {
                    ClaudeSettings.set(key, value);
                    ModPackets.sendSetSetting(targetPlugin, key, value);
                }
            }
            case "toggle_setting" -> {
                String key = strVal(action, "key", "");
                if (!key.isBlank()) {
                    boolean current = ClaudeSettings.getBool(key);
                    String value = current
                            ? strVal(action, "falseValue", "false")
                            : strVal(action, "trueValue", "true");
                    ClaudeSettings.set(key, value);
                    ModPackets.sendSetSetting(targetPlugin, key, value);
                }
            }
            case "set_tab" -> {
                String container = strVal(action, "container", "tabs");
                int index = intVal(action, "tabIndex", 0);
                selectedTabByContainer.put(container, Math.max(0, index));
            }
            default -> {}
        }
    }

    private void clearHistoryViewAndServer(String plugin) {
        ClaudeHudState.clear();
        ClaudeHudState.clearQuestion();
        ModPackets.clearHistory(plugin);
        scrollOffset = 0;
    }

    private void openHelpModal() {
        activeModalId = "help-modal";
    }

    private List<String> wrapTextRows(String text, int maxW) {
        List<String> rows = new ArrayList<>();
        if (text.isEmpty()) {
            rows.add("");
            return rows;
        }
        for (String paragraph : text.split("\\n", -1)) {
            String rem = paragraph;
            if (rem.isEmpty()) {
                rows.add("");
                continue;
            }
            while (!rem.isEmpty()) {
                String fits = textRenderer.trimToWidth(rem, maxW);
                if (fits.isEmpty()) fits = rem.substring(0, 1);
                rows.add(fits);
                rem = rem.substring(fits.length());
            }
        }
        if (rows.isEmpty()) rows.add("");
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
        String tool = ClaudeHudState.getCurrentTool();
        String detail = ClaudeHudState.getCurrentToolDetail();
        return text
                .replace("${streaming}", String.valueOf(ClaudeHudState.isStreaming()))
                .replace("${historyCount}", String.valueOf(ClaudeHudState.getLines().size()))
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
        String raw = strVal(obj, key, null);
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

    private static boolean inBox(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
