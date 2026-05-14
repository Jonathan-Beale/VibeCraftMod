package com.example.vibecraftmod.screen;

import com.example.vibecraftmod.config.ClientConfig;
import com.example.vibecraftmod.hud.ClaudeHudState;
import com.example.vibecraftmod.network.ModPackets;
import com.example.vibecraftmod.settings.ClaudeSettings;
import com.example.vibecraftmod.settings.ColorScheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ClaudeInputScreen extends Screen {

    private static final int LINE_HEIGHT   = 11;
    private static final int PADDING       = 8;
    private static final int TITLE_HEIGHT  = 14;
    private static final int SCROLL_SPEED  = 3;
    private static final int BTN_H         = 16;
    private static final int BTN_GAP       = 2;
    private static final int BTN_BG        = 0xFF142033;
    private static final int BTN_BG_HOVER  = 0xFF1E3A5F;
    private static final int SCROLLBAR_W   = 3;
    private static final int MAX_INPUT_LEN = 500;

    private static final int COL_BG       = 0xDD0A0A0F;
    private static final int COL_TITLE_BG = 0xFF111118;
    private static final int COL_DIVIDER  = 0xFF2A2A3A;
    private static final int COL_LABEL    = 0xFFCCCCCC;
    private static final int COL_SB_TRACK = 0xFF1A1A28;
    private static final int COL_SB_THUMB = 0xFF4A4A6A;
    private static final int COL_SB_ACT   = 0xFF7070A0;

    private record DisplayLine(OrderedText text, int color) {}

    private boolean showThoughts;
    private String  noticeText   = null;
    private long    noticeExpiry = 0;
    private int     promptW      = 0;

    private String inputText = "";
    private int    cursor    = 0;
    private final List<String>           inputLines        = new ArrayList<>();
    private final List<List<String>>     stagedVisualLines = new ArrayList<>();
    private       List<String>           activeVisualLines = new ArrayList<>();
    private final List<DisplayLine>      displayLines      = new ArrayList<>();
    private int scrollOffset  = 0;
    private int lastLineCount = -1;
    private String lastIndicator = "";

    private boolean draggingScrollbar = false;
    private int dragStartMouseY;
    private int dragStartOffset;

    public ClaudeInputScreen() {
        super(Text.literal("Claude Chat"));
    }

    // -------------------------------------------------------------------------
    // Layout helpers

    private static final int PANEL_W_MAX = 520;
    private int panelW()     { return Math.min(PANEL_W_MAX, (int)(width * 0.70)); }
    private int panelX()     { return (width - panelW()) / 2; }
    private int historyTop() { return TITLE_HEIGHT; }
    private int historyBot() { return height - activeH() - PADDING * 2 - optionsH() - pendingH(); }
    private int pendingH() {
        int total = 0;
        for (List<String> vl : stagedVisualLines) total += vl.size();
        return total * LINE_HEIGHT;
    }
    private int activeH() {
        return (activeVisualLines.isEmpty() ? 1 : activeVisualLines.size()) * LINE_HEIGHT;
    }
    private int historyH()   { return historyBot() - historyTop(); }
    private int maxTextW()   { return panelW() - PADDING * 2 - SCROLLBAR_W - promptW; }

    private int sbX()      { return panelX() + panelW() - PADDING / 2 - SCROLLBAR_W; }
    private int sbTrackT() { return historyTop() + PADDING; }
    private int sbTrackB() { return historyBot() - PADDING; }

    private int optionsH() {
        ClaudeHudState.PendingQuestion q = ClaudeHudState.getQuestion();
        if (q == null || q.options().isEmpty()) return 0;
        return q.options().size() * (BTN_H + BTN_GAP) + 4;
    }

    private int optionsTop() { return historyBot() + 2; }

    // -------------------------------------------------------------------------

    @Override
    protected void init() {
        promptW = textRenderer.getWidth("◆ ");
        showThoughts = ClaudeSettings.getBool("ui.thoughts_visible");
        if (ClaudeHudState.isEmpty()) ModPackets.requestHistory();
        rebuildInputLines();
        rebuildLines();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        List<ClaudeHudState.HudLine> raw = ClaudeHudState.getLines();
        String indicator = currentIndicator();
        if (raw.size() != lastLineCount || !indicator.equals(lastIndicator)) {
            rebuildLines();
            lastIndicator = indicator;
        }

        int px = panelX(), pw = panelW();
        long nowMs = System.currentTimeMillis();

        ctx.fill(px, 0, px + pw, height, COL_BG);

        // Title bar
        ctx.fill(px, 0, px + pw, TITLE_HEIGHT, COL_TITLE_BG);
        ctx.drawText(textRenderer,
                Text.literal("◆ Claude Chat").formatted(Formatting.AQUA),
                px + PADDING, 3, COL_LABEL, false);
        if (ClaudeHudState.isStreaming()) {
            ctx.drawText(textRenderer,
                    Text.literal("  ⏳").formatted(Formatting.YELLOW),
                    px + PADDING + textRenderer.getWidth("◆ Claude Chat") + 2, 3, COL_LABEL, false);
        }

        // [x] close — rightmost
        String closeLabel = "[x]";
        int closeW = textRenderer.getWidth(closeLabel);
        int closeX = px + pw - PADDING - closeW;
        boolean closeHov = mouseX >= closeX - 3 && mouseX < closeX + closeW + 3
                        && mouseY >= 1 && mouseY < TITLE_HEIGHT - 1;
        ctx.fill(closeX - 3, 1, closeX + closeW + 3, TITLE_HEIGHT - 1,
                closeHov ? 0xFF3A1010 : BTN_BG);
        ctx.drawText(textRenderer,
                Text.literal(closeLabel).formatted(closeHov ? Formatting.RED : Formatting.GRAY),
                closeX, 3, 0xFFFFFFFF, false);

        // [options] button — left of [x]
        String optLabel = "[options]";
        int optW  = textRenderer.getWidth(optLabel);
        int optX  = closeX - 8 - optW;
        boolean optHov = mouseX >= optX - 3 && mouseX < optX + optW + 3
                      && mouseY >= 1 && mouseY < TITLE_HEIGHT - 1;
        ctx.fill(optX - 3, 1, optX + optW + 3, TITLE_HEIGHT - 1,
                optHov ? BTN_BG_HOVER : BTN_BG);
        ctx.drawText(textRenderer,
                Text.literal(optLabel).formatted(optHov ? Formatting.WHITE : Formatting.AQUA),
                optX, 3, 0xFFFFFFFF, false);

        // History
        int visibleLines = historyH() / LINE_HEIGHT;
        int total        = displayLines.size();
        int maxScroll    = Math.max(0, total - visibleLines);
        scrollOffset     = Math.min(scrollOffset, maxScroll);

        int startLine = Math.max(0, total - visibleLines - scrollOffset);
        int endLine   = Math.min(total, startLine + visibleLines);

        int ty = historyTop() + PADDING;
        for (int i = startLine; i < endLine; i++) {
            DisplayLine dl = displayLines.get(i);
            ctx.drawText(textRenderer, dl.text(), px + PADDING, ty, dl.color() | 0xFF000000, false);
            ty += LINE_HEIGHT;
        }

        // Scrollbar
        if (total > visibleLines) {
            int sbx    = sbX();
            int trackT = sbTrackT();
            int trackB = sbTrackB();
            int trackH = trackB - trackT;

            ctx.fill(sbx, trackT, sbx + SCROLLBAR_W, trackB, COL_SB_TRACK);

            int thumbH = Math.max(8, trackH * visibleLines / total);
            int thumbY = trackT + (maxScroll > 0
                    ? (trackH - thumbH) * startLine / maxScroll
                    : 0);

            boolean thumbHov = !draggingScrollbar
                    && mouseX >= sbx && mouseX < sbx + SCROLLBAR_W
                    && mouseY >= thumbY && mouseY < thumbY + thumbH;
            ctx.fill(sbx, thumbY, sbx + SCROLLBAR_W, thumbY + thumbH,
                    (draggingScrollbar || thumbHov) ? COL_SB_ACT : COL_SB_THUMB);
        }

        // Thoughts-toggle notice (fade-out toast)
        if (noticeText != null && nowMs < noticeExpiry) {
            long remaining = noticeExpiry - nowMs;
            int a  = remaining < 500L ? (int)(remaining * 255L / 500L) : 255;
            int nw = textRenderer.getWidth(noticeText) + 16;
            int nx = px + (pw - nw) / 2;
            int ny = historyTop() + PADDING + 4;
            ctx.fill(nx, ny, nx + nw, ny + 13, ((a * 0xCC / 255) << 24) | 0x00111118);
            ctx.drawText(textRenderer, Text.literal(noticeText),
                    nx + 8, ny + 3, (a << 24) | 0x00FFFF55, false);
        }

        // Option buttons
        ClaudeHudState.PendingQuestion q = ClaudeHudState.getQuestion();
        if (q != null && !q.options().isEmpty()) {
            int y0 = optionsTop();
            for (int i = 0; i < q.options().size(); i++) {
                int top = y0 + i * (BTN_H + BTN_GAP);
                boolean hover = mouseX >= px + PADDING && mouseX < px + pw - PADDING
                             && mouseY >= top && mouseY < top + BTN_H;
                ctx.fill(px + PADDING, top, px + pw - PADDING, top + BTN_H,
                        hover ? BTN_BG_HOVER : BTN_BG);
                ctx.drawText(textRenderer,
                        "[" + (char)('A' + i) + "] " + q.options().get(i),
                        px + PADDING + 6, top + 4, 0xFFFFFFFF, false);
            }
        }

        // Divider
        ctx.fill(px + PADDING, historyBot(), px + pw - PADDING, historyBot() + 1, COL_DIVIDER);

        // Input area — all lines (staged + active) share the same x and the same drawText path
        int inputX = px + PADDING + promptW;

        // Staged lines (wrapped)
        int userColor = ColorScheme.get().user() | 0xFF000000;
        int lineY = historyBot() + PADDING;
        for (List<String> vl : stagedVisualLines) {
            for (String seg : vl) {
                ctx.drawText(textRenderer, seg, inputX, lineY, userColor, false);
                lineY += LINE_HEIGHT;
            }
        }

        // Active line: ◆ prompt on the first visual sub-line, then wrapped text + cursor
        int activeY = lineY;
        ctx.drawText(textRenderer,
                Text.literal("◆").formatted(Formatting.AQUA),
                px + PADDING, activeY, COL_LABEL, false);

        if (activeVisualLines.isEmpty() || inputText.isEmpty()) {
            ctx.drawText(textRenderer,
                    Text.literal("Message Claude…").formatted(Formatting.DARK_GRAY),
                    inputX, activeY, 0xFF555555, false);
        } else {
            for (String seg : activeVisualLines) {
                ctx.drawText(textRenderer, seg, inputX, activeY, userColor, false);
                activeY += LINE_HEIGHT;
            }
            activeY = lineY; // reset to first visual line for cursor math
        }

        // Blinking cursor: find the visual sub-line containing cursor
        if ((nowMs / 500) % 2 == 0) {
            int charAccum = 0;
            int cursorLineY = lineY;
            List<String> segs = activeVisualLines.isEmpty()
                    ? java.util.List.of("") : activeVisualLines;
            for (int i = 0; i < segs.size(); i++) {
                String seg = segs.get(i);
                int segEnd = charAccum + seg.length();
                if (cursor <= segEnd || i == segs.size() - 1) {
                    int posInSeg = Math.min(cursor - charAccum, seg.length());
                    int cx = inputX + textRenderer.getWidth(seg.substring(0, posInSeg));
                    ctx.fill(cx, cursorLineY, cx + 1, cursorLineY + textRenderer.fontHeight, userColor);
                    break;
                }
                charAccum = segEnd;
                cursorLineY += LINE_HEIGHT;
            }
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    // -------------------------------------------------------------------------
    // Keyboard input

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (inputText.length() >= MAX_INPUT_LEN) return true;
        inputText = inputText.substring(0, cursor) + chr + inputText.substring(cursor);
        cursor++;
        rebuildInputLines();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                inputLines.add(inputText);
                inputText = "";
                cursor = 0;
                rebuildInputLines();
                return true;
            }
            String current = inputText.trim();
            if (!inputLines.isEmpty() || !current.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String line : inputLines) sb.append(line).append("\n");
                if (!current.isEmpty()) sb.append(current);
                String combined = sb.toString().trim();
                if (!combined.isEmpty()) {
                    ModPackets.sendInput(combined);
                    inputLines.clear();
                    inputText = "";
                    cursor = 0;
                    scrollOffset = 0;
                    rebuildInputLines();
                }
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { inputLines.clear(); close(); return true; }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (cursor > 0) {
                inputText = inputText.substring(0, cursor - 1) + inputText.substring(cursor);
                cursor--;
                rebuildInputLines();
            } else if (inputText.isEmpty() && !inputLines.isEmpty()) {
                inputText = inputLines.remove(inputLines.size() - 1);
                cursor = inputText.length();
                rebuildInputLines();
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (cursor < inputText.length()) {
                inputText = inputText.substring(0, cursor) + inputText.substring(cursor + 1);
                rebuildInputLines();
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_LEFT)  { if (cursor > 0) cursor--; return true; }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) { if (cursor < inputText.length()) cursor++; return true; }
        if (keyCode == GLFW.GLFW_KEY_HOME)  { cursor = 0; return true; }
        if (keyCode == GLFW.GLFW_KEY_END)   { cursor = inputText.length(); return true; }

        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            cursor = inputText.length();
            return true;
        }

        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            String clip = client.keyboard.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                String insert = clip.replace("\r\n", " ").replace("\r", " ").replace("\n", " ");
                int space = MAX_INPUT_LEN - inputText.length();
                if (space > 0) {
                    if (insert.length() > space) insert = insert.substring(0, space);
                    inputText = inputText.substring(0, cursor) + insert + inputText.substring(cursor);
                    cursor += insert.length();
                    rebuildInputLines();
                }
            }
            return true;
        }

        if (ClientConfig.matches("toggle_thoughts", keyCode, modifiers)) {
            showThoughts = !showThoughts;
            ClaudeSettings.set("ui.thoughts_visible", String.valueOf(showThoughts));
            ModPackets.sendSetSetting("ui.thoughts_visible", String.valueOf(showThoughts));
            noticeText   = showThoughts ? "Thoughts visible" : "Thoughts hidden";
            noticeExpiry = System.currentTimeMillis() + 1500;
            lastLineCount = -1;
            return true;
        }
        if (ClientConfig.matches("open_options", keyCode, modifiers)) {
            client.setScreen(new ClaudeSettingsScreen(this));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scroll(historyH() / LINE_HEIGHT - 1); return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll(-(historyH() / LINE_HEIGHT - 1)); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void scroll(int lineDelta) {
        int visibleLines = historyH() / LINE_HEIGHT;
        int maxScroll    = Math.max(0, displayLines.size() - visibleLines);
        scrollOffset     = Math.max(0, Math.min(maxScroll, scrollOffset + lineDelta));
    }

    // -------------------------------------------------------------------------
    // Mouse handling

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int px = panelX(), pw = panelW();

            // [x] close
            String closeLabel = "[x]";
            int closeW = textRenderer.getWidth(closeLabel);
            int closeX = px + pw - PADDING - closeW;
            if (mouseX >= closeX - 3 && mouseX < closeX + closeW + 3
                    && mouseY >= 1 && mouseY < TITLE_HEIGHT - 1) {
                close(); return true;
            }

            // [options]
            String optLabel = "[options]";
            int optW = textRenderer.getWidth(optLabel);
            int optX = closeX - 8 - optW;
            if (mouseX >= optX - 3 && mouseX < optX + optW + 3
                    && mouseY >= 1 && mouseY < TITLE_HEIGHT - 1) {
                client.setScreen(new ClaudeSettingsScreen(this));
                return true;
            }

            // Scrollbar track click → jump + start drag
            int total     = displayLines.size();
            int visLines  = historyH() / LINE_HEIGHT;
            int maxScroll = Math.max(0, total - visLines);
            if (total > visLines) {
                int sbx    = sbX();
                int trackT = sbTrackT();
                int trackB = sbTrackB();
                int trackH = trackB - trackT;
                if (mouseX >= sbx - 2 && mouseX < sbx + SCROLLBAR_W + 2
                        && mouseY >= trackT && mouseY < trackB) {
                    int thumbH = Math.max(8, trackH * visLines / total);
                    double frac = Math.max(0, Math.min(1,
                            (mouseY - trackT - thumbH / 2.0) / Math.max(1, trackH - thumbH)));
                    scrollOffset = Math.max(0, Math.min(maxScroll,
                            maxScroll - (int)(frac * maxScroll)));
                    draggingScrollbar = true;
                    dragStartMouseY   = (int)mouseY;
                    dragStartOffset   = scrollOffset;
                    return true;
                }
            }

            // Option buttons
            ClaudeHudState.PendingQuestion q = ClaudeHudState.getQuestion();
            if (q != null && !q.options().isEmpty()) {
                int y0 = optionsTop();
                for (int i = 0; i < q.options().size(); i++) {
                    int top = y0 + i * (BTN_H + BTN_GAP);
                    if (mouseX >= px + PADDING && mouseX < px + pw - PADDING
                            && mouseY >= top && mouseY < top + BTN_H) {
                        String chosen = q.options().get(i);
                        ClaudeHudState.addLine("> " + chosen, ColorScheme.get().user());
                        ModPackets.sendInput(chosen);
                        ClaudeHudState.clearQuestion();
                        scrollOffset = 0;
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY) {
        if (draggingScrollbar && button == 0) {
            int visLines  = historyH() / LINE_HEIGHT;
            int total     = displayLines.size();
            int maxScroll = Math.max(0, total - visLines);
            int trackH    = sbTrackB() - sbTrackT();
            if (maxScroll > 0 && trackH > 0) {
                int thumbH = Math.max(8, trackH * visLines / total);
                double dy  = mouseY - dragStartMouseY;
                int delta  = -(int)Math.round(dy * maxScroll / Math.max(1, trackH - thumbH));
                scrollOffset = Math.max(0, Math.min(maxScroll, dragStartOffset + delta));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        int visibleLines = historyH() / LINE_HEIGHT;
        int maxScroll    = Math.max(0, displayLines.size() - visibleLines);
        scrollOffset = (int)Math.max(0,
                Math.min(maxScroll, scrollOffset + verticalAmount * SCROLL_SPEED));
        return true;
    }

    @Override public boolean shouldPause()      { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }

    // -------------------------------------------------------------------------
    // Line building

    private void rebuildInputLines() {
        if (textRenderer == null) return;
        int maxW = maxTextW();
        stagedVisualLines.clear();
        for (String line : inputLines) stagedVisualLines.add(wrapTextSimple(line, maxW));
        activeVisualLines = wrapTextSimple(inputText, maxW);
    }

    private List<String> wrapTextSimple(String text, int maxW) {
        List<String> result = new ArrayList<>();
        String rem = text;
        while (!rem.isEmpty()) {
            String fits = textRenderer.trimToWidth(rem, maxW);
            if (fits.isEmpty()) fits = rem.substring(0, 1);
            result.add(fits);
            rem = rem.substring(fits.length());
        }
        if (result.isEmpty()) result.add("");
        return result;
    }

    private void rebuildLines() {
        displayLines.clear();
        List<ClaudeHudState.HudLine> raw = ClaudeHudState.getLines();
        lastLineCount = raw.size();
        int maxW = maxTextW();
        for (ClaudeHudState.HudLine line : raw) {
            if (line.thought() && !showThoughts && !ClaudeHudState.isStreaming()) continue;
            wrapLine(line.text(), line.color(), maxW, line.thought(), line.collapsed());
        }
        String indicator = currentIndicator();
        if (!indicator.isEmpty()) wrapLine(indicator, 0xFFAA00, maxW, false, true);
    }

    private String currentIndicator() {
        if (!ClaudeHudState.isStreaming()) return "";
        String tool   = ClaudeHudState.getCurrentTool();
        String detail = ClaudeHudState.getCurrentToolDetail();
        return tool != null
                ? "⏳ [" + tool + "] " + (detail != null ? detail : "")
                : "⏳ Processing…";
    }

    private void wrapLine(String text, int color, int maxW, boolean italic, boolean singleLine) {
        if (singleLine) {
            int avail = maxW - textRenderer.getWidth("…") - 1;
            String trimmed = textRenderer.trimToWidth(text, avail);
            if (trimmed.length() < text.length()) trimmed += "…";
            displayLines.add(new DisplayLine(Text.literal(trimmed).asOrderedText(), color));
            return;
        }
        MutableText mt = parseLegacy(text.isEmpty() ? " " : text);
        if (italic) mt = (MutableText) mt.copy().formatted(Formatting.ITALIC);
        for (OrderedText o : textRenderer.wrapLines(mt, maxW)) {
            displayLines.add(new DisplayLine(o, color));
        }
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
                if (f == Formatting.RESET)              { current = Style.EMPTY; }
                else if (f.isColor())                   { current = current.withColor(f); }
                else if (f == Formatting.BOLD)          { current = current.withBold(true); }
                else if (f == Formatting.ITALIC)        { current = current.withItalic(true); }
                else if (f == Formatting.UNDERLINE)     { current = current.withUnderline(true); }
                else if (f == Formatting.STRIKETHROUGH) { current = current.withStrikethrough(true); }
            } else {
                buf.append(c);
            }
        }
        if (!buf.isEmpty()) root.append(Text.literal(buf.toString()).setStyle(current));
        return root;
    }
}
