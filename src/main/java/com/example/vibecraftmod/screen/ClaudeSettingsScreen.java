package com.example.vibecraftmod.screen;

import com.example.vibecraftmod.config.ClientConfig;
import com.example.vibecraftmod.network.ModPackets;
import com.example.vibecraftmod.settings.ClaudeSettings;
import com.example.vibecraftmod.settings.ColorScheme;
import com.example.vibecraftmod.ui.SchemaConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class ClaudeSettingsScreen extends Screen {

    private static final int PANEL_W_MAX  = 520;
    private static final int ROW_H        = 14;
    private static final int PADDING      = 10;
    private static final int TITLE_H      = 14;
    private static final int TAB_BAR_H    = 14;
    private static final int SECTION_GAP  = 4;
    private static final int BTN_W        = 12;
    private static final int VAL_W        = 16;
    private static final int SWATCH_W     = 9;
    private static final int SWATCH_H     = 7;
    private static final int SWATCH_GAP   = 2;
    private static final int PAL_SW       = 16;
    private static final int PAL_SH       = 12;
    private static final int PAL_GAP      = 2;
    private static final int PAL_BLOCK_H  = 76; // 6 rows * 12px + gaps + padding

    private static final int COL_BG       = 0xDD0A0A0F;
    private static final int COL_TITLE_BG = 0xFF111118;
    private static final int COL_DIVIDER  = 0xFF2A2A3A;
    private static final int COL_SECTION  = 0xFF888899;
    private static final int COL_LABEL    = 0xFFCCCCCC;
    private static final int COL_BTN      = 0xFF142033;
    private static final int COL_BTN_SEL  = 0xFF1A5C30;
    private static final int COL_BTN_HOV  = 0xFF1E3A5F;
    private static final int COL_TAB_SEL  = 0xFF1A3A5F;
    private static final int COL_DIM      = 0xFF444455;
    private static final int COL_CAPTURE  = 0xFF1A3A5F;

    private final Screen parent;
    private static int activeTab    = 0;
    private int    scrollOffset     = 0;
    private String capturingId      = null;
    private boolean capturingUseMods = false;
    private String editingRoleKey   = null;

    public ClaudeSettingsScreen(Screen parent) {
        super(Text.literal("Options"));
        this.parent = parent;
    }

    private int panelW() { return Math.min(PANEL_W_MAX, (int)(width * 0.70)); }
    private int panelX() { return (width - panelW()) / 2; }

    private boolean customActive() {
        return "custom".equals(ClaudeSettings.get("ui.color_scheme"));
    }

    private SchemaConfig.SettingsTab[] getTabs() {
        return SchemaConfig.get().settings.tabs;
    }

    private SchemaConfig.SettingsRow[] getCurrentTabRows() {
        SchemaConfig.SettingsTab[] tabs = getTabs();
        if (activeTab < 0 || activeTab >= tabs.length) return new SchemaConfig.SettingsRow[0];
        return tabs[activeTab].rows;
    }

    private int contentH() {
        if (activeTab == 1) {
            int h = ROW_H + SECTION_GAP;
            h += ColorScheme.getNames().length * ROW_H;
            if (customActive()) {
                h += ROW_H + SECTION_GAP;
                h += ColorScheme.getRoles().length * ROW_H;
                if (editingRoleKey != null) h += PAL_BLOCK_H;
            }
            return h + PADDING;
        }
        int h = 0;
        for (SchemaConfig.SettingsRow row : getCurrentTabRows()) {
            h += ROW_H;
            if (row instanceof SchemaConfig.SectionRow) h += SECTION_GAP;
        }
        return h + PADDING;
    }

    private int fullPanelH()       { return TITLE_H + TAB_BAR_H + PADDING + contentH(); }
    private int panelH()           { return Math.min(fullPanelH(), height - 8); }
    private int visibleContentH()  { return panelH() - TITLE_H - TAB_BAR_H - PADDING; }
    private int maxScroll()        { return Math.max(0, contentH() - visibleContentH()); }
    private int panelY()           { return Math.max(0, (height - panelH()) / 2); }
    private int contentY(int py)   { return py + TITLE_H + TAB_BAR_H + PADDING; }

    @Override protected void init() {}
    @Override public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {}
    @Override public boolean shouldPause()      { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset));
        int px = panelX(), pw = panelW(), py = panelY();
        int ph = panelH();
        ctx.fill(px, py, px + pw, py + ph, COL_BG);
        ctx.fill(px, py, px + pw, py + TITLE_H, COL_TITLE_BG);
        ctx.drawText(textRenderer, Text.literal("◆ Options").formatted(Formatting.AQUA),
                px + PADDING, py + 3, COL_LABEL, false);

        String backLabel = "[back]";
        int backW = textRenderer.getWidth(backLabel);
        int backX = px + pw - PADDING - backW;
        boolean backHov = inBox(mouseX, mouseY, backX - 3, py + 1, backW + 6, TITLE_H - 2);
        ctx.fill(backX - 3, py + 1, backX + backW + 3, py + TITLE_H - 1,
                backHov ? COL_BTN_HOV : COL_BTN);
        ctx.drawText(textRenderer,
                Text.literal(backLabel).formatted(backHov ? Formatting.WHITE : Formatting.GRAY),
                backX, py + 3, 0xFFFFFFFF, false);

        // Tab bar from schema
        SchemaConfig.SettingsTab[] tabs = getTabs();
        int tabY = py + TITLE_H;
        int tx = px + PADDING;
        for (int i = 0; i < tabs.length; i++) {
            int tw = textRenderer.getWidth(tabs[i].name) + 12;
            boolean sel = (i == activeTab);
            boolean hov = !sel && inBox(mouseX, mouseY, tx, tabY, tw, TAB_BAR_H);
            ctx.fill(tx, tabY, tx + tw, tabY + TAB_BAR_H,
                    sel ? COL_TAB_SEL : hov ? COL_BTN_HOV : COL_BTN);
            ctx.drawText(textRenderer, tabs[i].name,
                    tx + 6, tabY + (TAB_BAR_H - 8) / 2,
                    sel ? 0xFFFFFFFF : hov ? 0xFFDDDDDD : COL_SECTION, false);
            tx += tw + 2;
        }
        ctx.fill(px + PADDING, tabY + TAB_BAR_H, px + pw - PADDING, tabY + TAB_BAR_H + 1, COL_DIVIDER);

        if (capturingId != null)
            ctx.fill(px, tabY + TAB_BAR_H + 1, px + pw, py + ph, 0x88000000);

        int clipTop = contentY(py);
        int clipBot = py + ph;
        ctx.enableScissor(px, clipTop, px + pw, clipBot);
        int ry = clipTop - scrollOffset;
        if (activeTab == 0) renderSettingsTab(ctx, mouseX, mouseY, px, pw, ry);
        else                renderColorsTab  (ctx, mouseX, mouseY, px, pw, ry);
        ctx.disableScissor();

        // Scrollbar
        int maxSc = maxScroll();
        if (maxSc > 0) {
            int sbx    = px + pw - 4;
            int trackH = clipBot - clipTop;
            ctx.fill(sbx, clipTop, sbx + 3, clipBot, 0xFF1A1A28);
            int thumbH = Math.max(8, trackH * visibleContentH() / contentH());
            int thumbY = clipTop + (trackH - thumbH) * scrollOffset / maxSc;
            ctx.fill(sbx, thumbY, sbx + 3, thumbY + thumbH, 0xFF4A4A6A);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scrollOffset = Math.max(0, Math.min(maxScroll(), (int)(scrollOffset - v * ROW_H * 3)));
        return true;
    }

    // -------------------------------------------------------------------------
    // Tab 0 — Settings

    private void renderSettingsTab(DrawContext ctx, int mouseX, int mouseY, int px, int pw, int ry) {
        for (SchemaConfig.SettingsRow row : getCurrentTabRows()) {
            switch (row) {
                case SchemaConfig.SectionRow s -> {
                    ctx.drawText(textRenderer, s.title(), px + PADDING, ry, COL_SECTION, false);
                    ry += ROW_H + SECTION_GAP;
                }
                case SchemaConfig.ToggleRow t -> {
                    boolean val = ClaudeSettings.getBool(t.key());
                    boolean rowHov = capturingId == null
                            && inBox(mouseX, mouseY, px + PADDING, ry - 1, pw - PADDING * 2, ROW_H);
                    String box = val ? "[x]" : "[ ]";
                    int checkCol = val ? 0xFF55FF55 : 0xFF555566;
                    int boxW = textRenderer.getWidth(box);
                    ctx.drawText(textRenderer, box, px + PADDING + 6, ry, checkCol, false);
                    ctx.drawText(textRenderer, t.label(),
                            px + PADDING + 6 + boxW + 3, ry,
                            rowHov ? 0xFFFFFFFF : COL_LABEL, false);
                    ry += ROW_H;
                }
                case SchemaConfig.IntRangeRow ir -> {
                    int current = ClaudeSettings.getInt(ir.key());
                    ctx.drawText(textRenderer, ir.label(), px + PADDING + 6, ry, COL_LABEL, false);
                    int labelW = textRenderer.getWidth(ir.label());
                    int bx = px + PADDING + 6 + labelW + 10;
                    boolean canDec = current > ir.min();
                    boolean decHov = canDec && capturingId == null
                            && inBox(mouseX, mouseY, bx, ry - 1, BTN_W, ROW_H);
                    ctx.fill(bx, ry - 1, bx + BTN_W, ry + ROW_H - 2, decHov ? COL_BTN_HOV : COL_BTN);
                    ctx.drawText(textRenderer, "-",
                            bx + (BTN_W - textRenderer.getWidth("-")) / 2, ry,
                            canDec ? 0xFFFF6666 : COL_DIM, false);
                    bx += BTN_W + 3;
                    String valStr = current == 0 ? "off" : String.valueOf(current);
                    ctx.drawText(textRenderer, valStr,
                            bx + (VAL_W - textRenderer.getWidth(valStr)) / 2, ry,
                            current == 0 ? 0xFF666677 : 0xFFFFFFFF, false);
                    bx += VAL_W + 3;
                    boolean canInc = current < ir.max();
                    boolean incHov = canInc && capturingId == null
                            && inBox(mouseX, mouseY, bx, ry - 1, BTN_W, ROW_H);
                    ctx.fill(bx, ry - 1, bx + BTN_W, ry + ROW_H - 2, incHov ? COL_BTN_HOV : COL_BTN);
                    ctx.drawText(textRenderer, "+",
                            bx + (BTN_W - textRenderer.getWidth("+")) / 2, ry,
                            canInc ? 0xFF55FF55 : COL_DIM, false);
                    ry += ROW_H;
                }
                case SchemaConfig.KeybindRow kb -> {
                    boolean capturing = kb.id().equals(capturingId);
                    ctx.drawText(textRenderer, kb.label(), px + PADDING + 6, ry, COL_LABEL, false);
                    int labelW = textRenderer.getWidth(kb.label());
                    int bx = px + PADDING + 6 + labelW + 10;
                    String keyStr = capturing
                            ? "Press a key…  (Esc to cancel)"
                            : "[ " + ClientConfig.getKeybindName(kb.id()) + " ]";
                    int kw = textRenderer.getWidth(keyStr) + 6;
                    boolean kHov = !capturing && capturingId == null
                            && inBox(mouseX, mouseY, bx, ry - 1, kw, ROW_H);
                    ctx.fill(bx, ry - 1, bx + kw, ry + ROW_H - 2,
                            capturing ? COL_CAPTURE : kHov ? COL_BTN_HOV : COL_BTN);
                    ctx.drawText(textRenderer, keyStr, bx + 3, ry,
                            capturing ? 0xFFFFFF55 : kHov ? 0xFFFFFFFF : COL_LABEL, false);
                    ry += ROW_H;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Tab 1 — Colors

    private void renderColorsTab(DrawContext ctx, int mouseX, int mouseY, int px, int pw, int ry) {
        ctx.drawText(textRenderer, "COLOR SCHEME", px + PADDING, ry, COL_SECTION, false);
        ry += ROW_H + SECTION_GAP;

        String current = ClaudeSettings.get("ui.color_scheme");
        String[] names = ColorScheme.getNames();
        String[] labels = ColorScheme.getLabels();
        int swatchCount = 6;
        int swatchGroupW = swatchCount * SWATCH_W + (swatchCount - 1) * SWATCH_GAP;
        int swatchesX = px + pw - PADDING - swatchGroupW;

        for (int i = 0; i < names.length; i++) {
            boolean sel = names[i].equals(current);
            boolean hov = inBox(mouseX, mouseY, px + PADDING, ry - 1, pw - PADDING * 2, ROW_H);
            if (hov && !sel) ctx.fill(px + PADDING, ry - 1, px + pw - PADDING, ry + ROW_H - 2, COL_BTN_HOV);
            if (sel)         ctx.fill(px + PADDING, ry - 1, px + pw - PADDING, ry + ROW_H - 2, COL_BTN_SEL);

            String box = sel ? "[x]" : "[ ]";
            int boxW = textRenderer.getWidth(box);
            ctx.drawText(textRenderer, box, px + PADDING + 6, ry, sel ? 0xFF55FF55 : 0xFF555566, false);
            ctx.drawText(textRenderer, labels[i],
                    px + PADDING + 6 + boxW + 4, ry,
                    sel ? 0xFFFFFFFF : hov ? 0xFFDDDDDD : COL_LABEL, false);

            // Swatches
            ColorScheme cs = ColorScheme.get();
            int[] cols = { cs.user(), cs.claude(), cs.tool(), cs.output(), cs.system(), cs.question() };
            int sx = swatchesX;
            int sy = ry + (ROW_H - SWATCH_H) / 2;
            for (int c : cols) {
                ctx.fill(sx, sy, sx + SWATCH_W, sy + SWATCH_H, 0xFF000000 | c);
                sx += SWATCH_W + SWATCH_GAP;
            }
            ry += ROW_H;
        }

        // Custom color editor
        if (customActive()) {
            ry += 2;
            ctx.fill(px + PADDING, ry, px + pw - PADDING, ry + 1, COL_DIVIDER);
            ry += 4;
            ctx.drawText(textRenderer, "CUSTOM COLORS", px + PADDING, ry, COL_SECTION, false);
            ry += ROW_H + SECTION_GAP;

            String[][] roles = ColorScheme.getRoles();
            for (String[] role : roles) {
                String roleKey   = role[0];
                String roleLabel = role[1];
                boolean editing  = roleKey.equals(editingRoleKey);
                boolean hov      = !editing && inBox(mouseX, mouseY, px + PADDING, ry - 1, pw - PADDING * 2, ROW_H);

                if (editing) ctx.fill(px + PADDING, ry - 1, px + pw - PADDING, ry + ROW_H - 2, 0xFF1A2A3A);
                else if (hov) ctx.fill(px + PADDING, ry - 1, px + pw - PADDING, ry + ROW_H - 2, COL_BTN_HOV);

                int roleColor = ColorScheme.hex(ClaudeSettings.get(roleKey), 0x888888);
                int swY = ry + (ROW_H - SWATCH_H) / 2;
                ctx.fill(px + PADDING + 6, swY, px + PADDING + 6 + SWATCH_W + 4, swY + SWATCH_H + 2, 0xFF333344);
                ctx.fill(px + PADDING + 7, swY + 1, px + PADDING + 6 + SWATCH_W + 3, swY + SWATCH_H + 1,
                        0xFF000000 | roleColor);

                ctx.drawText(textRenderer, roleLabel,
                        px + PADDING + 6 + SWATCH_W + 8, ry,
                        editing ? 0xFFFFFFFF : hov ? 0xFFDDDDDD : COL_LABEL, false);

                String hexStr = "#" + String.format("%06X", roleColor);
                ctx.drawText(textRenderer, hexStr,
                        px + pw - PADDING - textRenderer.getWidth(hexStr), ry,
                        editing ? 0xFFFFFF55 : 0xFF666677, false);

                ry += ROW_H;

                if (editing) {
                    int palX   = px + PADDING + 6;
                    int palTop = ry + 4;
                    int palCols = SchemaConfig.get().colors.paletteCols;
                    int palRows = SchemaConfig.get().colors.paletteRows;
                    int[] palette = ColorScheme.PALETTE;
                    for (int row = 0; row < palRows; row++) {
                        for (int col = 0; col < palCols; col++) {
                            int ci = row * palCols + col;
                            int c  = palette[ci];
                            int sx = palX + col * (PAL_SW + PAL_GAP);
                            int sy = palTop + row * (PAL_SH + PAL_GAP);
                            boolean palHov = inBox(mouseX, mouseY, sx, sy, PAL_SW, PAL_SH);
                            if (palHov) ctx.fill(sx-1, sy-1, sx+PAL_SW+1, sy+PAL_SH+1, 0xFFFFFFFF);
                            ctx.fill(sx, sy, sx + PAL_SW, sy + PAL_SH, 0xFF000000 | c);
                        }
                    }
                    ry += PAL_BLOCK_H;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Mouse

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (capturingId != null) { capturingId = null; return true; }

        int px = panelX(), pw = panelW(), py = panelY();

        // [back]
        String backLabel = "[back]";
        int backW = textRenderer.getWidth(backLabel);
        int backX = px + pw - PADDING - backW;
        if (inBox(mouseX, mouseY, backX - 3, py + 1, backW + 6, TITLE_H - 2)) {
            close(); return true;
        }

        // Tabs
        SchemaConfig.SettingsTab[] tabs = getTabs();
        int tabY = py + TITLE_H;
        int tx = px + PADDING;
        for (int i = 0; i < tabs.length; i++) {
            int tw = textRenderer.getWidth(tabs[i].name) + 12;
            if (inBox(mouseX, mouseY, tx, tabY, tw, TAB_BAR_H)) {
                if (i != activeTab) { activeTab = i; scrollOffset = 0; }
                return true;
            }
            tx += tw + 2;
        }

        // Ignore clicks outside the content clip area
        int clipTop = contentY(py), clipBot = py + panelH();
        if (mouseY < clipTop || mouseY >= clipBot) return false;

        int ry = clipTop - scrollOffset;
        return activeTab == 0
                ? handleSettingsClick(mouseX, mouseY, px, pw, ry)
                : handleColorsClick  (mouseX, mouseY, px, pw, ry);
    }

    private boolean handleSettingsClick(double mouseX, double mouseY, int px, int pw, int ry) {
        for (SchemaConfig.SettingsRow row : getCurrentTabRows()) {
            switch (row) {
                case SchemaConfig.SectionRow s -> ry += ROW_H + SECTION_GAP;
                case SchemaConfig.ToggleRow t -> {
                    if (inBox(mouseX, mouseY, px + PADDING, ry - 1, pw - PADDING * 2, ROW_H)) {
                        boolean v = !ClaudeSettings.getBool(t.key());
                        ClaudeSettings.set(t.key(), String.valueOf(v));
                        ModPackets.sendSetSetting(t.key(), String.valueOf(v));
                        return true;
                    }
                    ry += ROW_H;
                }
                case SchemaConfig.IntRangeRow ir -> {
                    int current = ClaudeSettings.getInt(ir.key());
                    int labelW = textRenderer.getWidth(ir.label());
                    int bx = px + PADDING + 6 + labelW + 10;
                    if (inBox(mouseX, mouseY, bx, ry - 1, BTN_W, ROW_H) && current > ir.min()) {
                        String v = String.valueOf(current - 1);
                        ClaudeSettings.set(ir.key(), v); ModPackets.sendSetSetting(ir.key(), v);
                        return true;
                    }
                    bx += BTN_W + 3 + VAL_W + 3;
                    if (inBox(mouseX, mouseY, bx, ry - 1, BTN_W, ROW_H) && current < ir.max()) {
                        String v = String.valueOf(current + 1);
                        ClaudeSettings.set(ir.key(), v); ModPackets.sendSetSetting(ir.key(), v);
                        return true;
                    }
                    ry += ROW_H;
                }
                case SchemaConfig.KeybindRow kb -> {
                    int labelW = textRenderer.getWidth(kb.label());
                    int bx = px + PADDING + 6 + labelW + 10;
                    String keyStr = "[ " + ClientConfig.getKeybindName(kb.id()) + " ]";
                    int kw = textRenderer.getWidth(keyStr) + 6;
                    if (inBox(mouseX, mouseY, bx, ry - 1, kw, ROW_H)) {
                        capturingId = kb.id(); capturingUseMods = kb.useMods();
                        return true;
                    }
                    ry += ROW_H;
                }
            }
        }
        return false;
    }

    private boolean handleColorsClick(double mouseX, double mouseY, int px, int pw, int ry) {
        ry += ROW_H + SECTION_GAP; // skip "COLOR SCHEME" header

        String current = ClaudeSettings.get("ui.color_scheme");
        String[] names = ColorScheme.getNames();
        for (int i = 0; i < names.length; i++) {
            if (inBox(mouseX, mouseY, px + PADDING, ry - 1, pw - PADDING * 2, ROW_H)) {
                String scheme = names[i];
                if (!scheme.equals(current)) {
                    ClaudeSettings.set("ui.color_scheme", scheme);
                    ModPackets.sendSetSetting("ui.color_scheme", scheme);
                    ModPackets.requestHistory();
                }
                if (!"custom".equals(scheme)) editingRoleKey = null;
                return true;
            }
            ry += ROW_H;
        }

        // Custom color editor
        if (customActive()) {
            ry += 2 + 1 + 4; // gap + divider + gap
            ry += ROW_H + SECTION_GAP; // "CUSTOM COLORS" header

            String[][] roles = ColorScheme.getRoles();
            for (String[] role : roles) {
                String roleKey = role[0];
                boolean editing = roleKey.equals(editingRoleKey);

                // Role row click — toggle palette
                if (inBox(mouseX, mouseY, px + PADDING, ry - 1, pw - PADDING * 2, ROW_H)) {
                    editingRoleKey = editing ? null : roleKey;
                    return true;
                }
                ry += ROW_H;

                // Palette click — HSV grid
                if (editing) {
                    int palX   = px + PADDING + 6;
                    int palTop = ry + 4;
                    int palCols = SchemaConfig.get().colors.paletteCols;
                    int palRows = SchemaConfig.get().colors.paletteRows;
                    int[] palette = ColorScheme.PALETTE;
                    outer:
                    for (int row = 0; row < palRows; row++) {
                        for (int col = 0; col < palCols; col++) {
                            int sx = palX + col * (PAL_SW + PAL_GAP);
                            int sy = palTop + row * (PAL_SH + PAL_GAP);
                            if (inBox(mouseX, mouseY, sx, sy, PAL_SW, PAL_SH)) {
                                int ci = row * palCols + col;
                                String hexVal = String.format("%06X", palette[ci]);
                                ClaudeSettings.set(roleKey, hexVal);
                                ModPackets.sendSetSetting(roleKey, hexVal);
                                ModPackets.requestHistory();
                                editingRoleKey = null;
                                return true;
                            }
                        }
                    }
                    ry += PAL_BLOCK_H;
                }
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Keyboard

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (capturingId != null) {
            if (isModifierKey(keyCode)) return true;
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                if (capturingUseMods) ClientConfig.setKeybind(capturingId, keyCode, modifiers);
                else                  ClientConfig.setKey(capturingId, keyCode);
            }
            capturingId = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (editingRoleKey != null) { editingRoleKey = null; return true; }
            close(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() { client.setScreen(parent); }

    private static boolean inBox(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static boolean isModifierKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_LEFT_CONTROL  || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
            || keyCode == GLFW.GLFW_KEY_LEFT_SHIFT    || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
            || keyCode == GLFW.GLFW_KEY_LEFT_ALT      || keyCode == GLFW.GLFW_KEY_RIGHT_ALT
            || keyCode == GLFW.GLFW_KEY_LEFT_SUPER    || keyCode == GLFW.GLFW_KEY_RIGHT_SUPER;
    }
}
