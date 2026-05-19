package com.example.vibecraftmod.screen;

import com.example.vibecraftmod.config.ClientConfig;
import com.example.vibecraftmod.network.ModPackets;
import com.example.vibecraftmod.settings.ModSettings;
import com.example.vibecraftmod.settings.ColorScheme;
import com.example.vibecraftmod.ui.SchemaConfig;
import com.example.vibecraftmod.ui.ScreenManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SchemaSettingsScreen extends Screen {

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
    private static final int PAL_GAP      = 1;
    private static final int PAL_BLOCK_H  = 0;
    private static final int PAL_MIN_COLS = 24;
    private static final int PAL_MIN_ROWS = 8;
    private static final int PAL_SIDE_W   = 126;
    private static final int PAL_SIDE_H   = 52;
    private static final int PAL_SIDE_PAD = 3;

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

    // Click layering in colors tab: higher z handles clicks first.
    private static final int Z_PICKER_SWATCH = 300;
    private static final int Z_PICKER_PANEL  = 250;
    private static final int Z_SCHEME_ROW    = 100;
    private static final int Z_ROLE_ROW      = 90;

    private final Screen parent;
    private static int activeTab    = 0;
    private int    scrollOffset     = 0;
    private String capturingId      = null;
    private boolean capturingUseMods = false;
    private String editingRoleKey   = null;

    public SchemaSettingsScreen(Screen parent) {
        super(Text.literal("Options"));
        this.parent = parent;
    }

    private int panelW() { return Math.min(PANEL_W_MAX, (int)(width * 0.70)); }
    private int panelX() { return (width - panelW()) / 2; }

    private boolean customActive() {
        return "custom".equals(ModSettings.getForPlugin(activePlugin(), "ui.color_scheme"));
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
            int roleCount = getDisplayRoles(getCurrentTabRows()).length;
            int h = ROW_H + SECTION_GAP;
            h += ColorScheme.getNames().length * ROW_H;
            if (customActive()) {
                h += ROW_H + SECTION_GAP;
                h += roleCount * ROW_H;
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
        else                renderColorsTab  (ctx, mouseX, mouseY, px, pw, ry, clipTop, clipBot);
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
                    boolean val = ModSettings.getBoolForPlugin(activePlugin(), t.key());
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
                    int current = ModSettings.getIntForPlugin(activePlugin(), ir.key());
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
                default -> {}  // Other row types (ColorSchemeRow, ColorRow) handled in Colors tab
            }
        }
    }

    // -------------------------------------------------------------------------
    // Tab 1 — Colors

    private void renderColorsTab(DrawContext ctx, int mouseX, int mouseY, int px, int pw, int ry, int clipTop, int clipBot) {
        SchemaConfig.SettingsRow[] rows = getCurrentTabRows();
        String[][] displayRoles = getDisplayRoles(rows);
        boolean renderedRoles = false;
        boolean hasSchemaColorRows = false;
        for (SchemaConfig.SettingsRow row : rows) {
            if (row instanceof SchemaConfig.ColorRow) {
                hasSchemaColorRows = true;
                break;
            }
        }

        for (SchemaConfig.SettingsRow row : rows) {
            if (row instanceof SchemaConfig.SectionRow sec) {
                ctx.drawText(textRenderer, sec.title(), px + PADDING, ry, COL_SECTION, false);
                ry += ROW_H + SECTION_GAP;
            } else if (row instanceof SchemaConfig.ColorSchemeRow) {
                // Color scheme selector
                String current = ModSettings.getForPlugin(activePlugin(), "ui.color_scheme");
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
                    int[] cols = previewColorsForScheme(names[i]);
                    int sx = swatchesX;
                    int sy = ry + (ROW_H - SWATCH_H) / 2;
                    for (int c : cols) {
                        ctx.fill(sx, sy, sx + SWATCH_W, sy + SWATCH_H, 0xFF000000 | c);
                        sx += SWATCH_W + SWATCH_GAP;
                    }
                    ry += ROW_H;
                }
            } else if (row instanceof SchemaConfig.ColorRow) {
                if (!renderedRoles) {
                    ry = renderRoleRows(ctx, mouseX, mouseY, px, pw, ry, displayRoles, clipTop, clipBot);
                    renderedRoles = true;
                }
            }
        }

        if (!renderedRoles && !hasSchemaColorRows && displayRoles.length > 0) {
            ry = renderRoleRows(ctx, mouseX, mouseY, px, pw, ry, displayRoles, clipTop, clipBot);
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
            : handleColorsClick  (mouseX, mouseY, px, pw, ry, clipTop, clipBot);
    }

    private boolean handleSettingsClick(double mouseX, double mouseY, int px, int pw, int ry) {
        for (SchemaConfig.SettingsRow row : getCurrentTabRows()) {
            switch (row) {
                case SchemaConfig.SectionRow s -> ry += ROW_H + SECTION_GAP;
                case SchemaConfig.ToggleRow t -> {
                    if (inBox(mouseX, mouseY, px + PADDING, ry - 1, pw - PADDING * 2, ROW_H)) {
                        boolean v = !ModSettings.getBoolForPlugin(activePlugin(), t.key());
                        ModSettings.setForPlugin(activePlugin(), t.key(), String.valueOf(v));
                        ModPackets.sendSetSetting(activePlugin(), t.key(), String.valueOf(v));
                        return true;
                    }
                    ry += ROW_H;
                }
                case SchemaConfig.IntRangeRow ir -> {
                    int current = ModSettings.getIntForPlugin(activePlugin(), ir.key());
                    int labelW = textRenderer.getWidth(ir.label());
                    int bx = px + PADDING + 6 + labelW + 10;
                    if (inBox(mouseX, mouseY, bx, ry - 1, BTN_W, ROW_H) && current > ir.min()) {
                        String v = String.valueOf(current - 1);
                        ModSettings.setForPlugin(activePlugin(), ir.key(), v); ModPackets.sendSetSetting(activePlugin(), ir.key(), v);
                        return true;
                    }
                    bx += BTN_W + 3 + VAL_W + 3;
                    if (inBox(mouseX, mouseY, bx, ry - 1, BTN_W, ROW_H) && current < ir.max()) {
                        String v = String.valueOf(current + 1);
                        ModSettings.setForPlugin(activePlugin(), ir.key(), v); ModPackets.sendSetSetting(activePlugin(), ir.key(), v);
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
                default -> {}  // Other row types (ColorSchemeRow, ColorRow) handled in Colors tab
            }
        }
        return false;
    }

    private boolean handleColorsClick(double mouseX, double mouseY, int px, int pw, int ry, int clipTop, int clipBot) {
        SchemaConfig.SettingsRow[] rows = getCurrentTabRows();
        String[][] roles = getDisplayRoles(rows);
        List<ClickZone> zones = new ArrayList<>();

        // Register swatch targets (high z) for active color picker.
        if (editingRoleKey != null) {
            PaletteLayout pal = paletteLayout(px, pw, clipTop, clipBot);
            String roleKey = editingRoleKey;

            // Block click-through anywhere inside the picker panel, including gaps between swatches.
            zones.add(new ClickZone(pal.panelX(), pal.panelY(), pal.panelW(), pal.panelH(), Z_PICKER_PANEL, () -> {
            }));

            for (int row = 0; row < pal.rows(); row++) {
                for (int col = 0; col < pal.cols(); col++) {
                    int sx = pal.x() + col * (pal.cellW() + PAL_GAP);
                    int sy = pal.y() + row * (pal.cellH() + PAL_GAP);
                    int picked = densePaletteColor(col, row, pal.cols(), pal.rows());
                    zones.add(new ClickZone(sx, sy, pal.cellW(), pal.cellH(), Z_PICKER_SWATCH, () -> {
                        String hexVal = String.format("%06X", picked);
                        ModSettings.setForPlugin(activePlugin(), roleKey, hexVal);
                        ModPackets.sendSetSetting(activePlugin(), roleKey, hexVal);
                        ModPackets.requestHistory(activePlugin());
                        editingRoleKey = null;
                    }));
                }
            }
        }

        boolean handledRoles = false;
        boolean hasSchemaColorRows = false;

        for (SchemaConfig.SettingsRow row : rows) {
            if (row instanceof SchemaConfig.ColorRow) {
                hasSchemaColorRows = true;
                break;
            }
        }

        // Register normal UI row targets (lower z).
        for (SchemaConfig.SettingsRow row : rows) {
            if (row instanceof SchemaConfig.SectionRow) {
                ry += ROW_H + SECTION_GAP;
            } else if (row instanceof SchemaConfig.ColorSchemeRow) {
                String current = ModSettings.getForPlugin(activePlugin(), "ui.color_scheme");
                String[] names = ColorScheme.getNames();
                for (String scheme : names) {
                    int rowY = ry;
                    zones.add(new ClickZone(px + PADDING, rowY - 1, pw - PADDING * 2, ROW_H, Z_SCHEME_ROW, () -> {
                        if (!scheme.equals(current)) {
                            ModSettings.setForPlugin(activePlugin(), "ui.color_scheme", scheme);
                            ModPackets.sendSetSetting(activePlugin(), "ui.color_scheme", scheme);
                            ModPackets.requestHistory(activePlugin());
                        }
                        if (!"custom".equals(scheme)) editingRoleKey = null;
                    }));
                    ry += ROW_H;
                }
            } else if (row instanceof SchemaConfig.ColorRow) {
                if (!handledRoles) {
                    for (String[] role : roles) {
                        String roleKey = role[0];
                        int rowY = ry;
                        zones.add(new ClickZone(px + PADDING, rowY - 1, pw - PADDING * 2, ROW_H, Z_ROLE_ROW, () -> {
                            if (!customActive()) return;
                            boolean editing = roleKey.equals(editingRoleKey);
                            editingRoleKey = editing ? null : roleKey;
                        }));
                        ry += ROW_H;
                    }
                    handledRoles = true;
                }
            }
        }

        if (!handledRoles && !hasSchemaColorRows && roles.length > 0) {
            for (String[] role : roles) {
                String roleKey = role[0];
                int rowY = ry;
                zones.add(new ClickZone(px + PADDING, rowY - 1, pw - PADDING * 2, ROW_H, Z_ROLE_ROW, () -> {
                    if (!customActive()) return;
                    boolean editing = roleKey.equals(editingRoleKey);
                    editingRoleKey = editing ? null : roleKey;
                }));
                ry += ROW_H;
            }
        }

        return dispatchZones(mouseX, mouseY, zones);
    }

    private boolean dispatchZones(double mouseX, double mouseY, List<ClickZone> zones) {
        zones.sort(Comparator.comparingInt(ClickZone::z).reversed());
        for (ClickZone zone : zones) {
            if (!inBox(mouseX, mouseY, zone.x(), zone.y(), zone.w(), zone.h())) continue;
            if (zone.onClick() != null) zone.onClick().run();
            return true;
        }
        return false;
    }

    private int renderRoleRows(DrawContext ctx, int mouseX, int mouseY, int px, int pw, int ry, String[][] roles, int clipTop, int clipBot) {
        int rowRight = colorsRowRight(px, pw);
        int rowWidth = Math.max(24, rowRight - (px + PADDING));
        for (String[] role : roles) {
            String colorKey = role[0];
            String label = role[1];
            boolean editing = colorKey.equals(editingRoleKey);
            boolean hov = !editing && inBox(mouseX, mouseY, px + PADDING, ry - 1, rowWidth, ROW_H);

            if (editing) ctx.fill(px + PADDING, ry - 1, rowRight, ry + ROW_H - 2, 0xFF1A2A3A);
            else if (hov) ctx.fill(px + PADDING, ry - 1, rowRight, ry + ROW_H - 2, COL_BTN_HOV);

            int colorVal = ColorScheme.hex(ModSettings.getForPlugin(activePlugin(), colorKey), 0x888888);
            int swY = ry + (ROW_H - SWATCH_H) / 2;
            ctx.fill(px + PADDING + 6, swY, px + PADDING + 6 + SWATCH_W + 4, swY + SWATCH_H + 2, 0xFF333344);
            ctx.fill(px + PADDING + 7, swY + 1, px + PADDING + 6 + SWATCH_W + 3, swY + SWATCH_H + 1,
                    0xFF000000 | colorVal);

            ctx.drawText(textRenderer, label,
                    px + PADDING + 6 + SWATCH_W + 8, ry,
                    editing ? 0xFFFFFFFF : hov ? 0xFFDDDDDD : COL_LABEL, false);

            String hexStr = "#" + String.format("%06X", colorVal);
            ctx.drawText(textRenderer, hexStr,
                    rowRight - textRenderer.getWidth(hexStr), ry,
                    editing ? 0xFFFFFF55 : 0xFF666677, false);

            ry += ROW_H;

            if (editing) {
                PaletteLayout pal = paletteLayout(px, pw, clipTop, clipBot);
                ctx.fill(pal.panelX(), pal.panelY(), pal.panelX() + pal.panelW(), pal.panelY() + pal.panelH(), 0xEE111824);
                ctx.fill(pal.panelX(), pal.panelY(), pal.panelX() + pal.panelW(), pal.panelY() + 1, 0xFF3D4D66);
                ctx.fill(pal.panelX(), pal.panelY() + pal.panelH() - 1, pal.panelX() + pal.panelW(), pal.panelY() + pal.panelH(), 0xFF3D4D66);
                ctx.fill(pal.panelX(), pal.panelY(), pal.panelX() + 1, pal.panelY() + pal.panelH(), 0xFF3D4D66);
                ctx.fill(pal.panelX() + pal.panelW() - 1, pal.panelY(), pal.panelX() + pal.panelW(), pal.panelY() + pal.panelH(), 0xFF3D4D66);
                for (int row = 0; row < pal.rows(); row++) {
                    for (int col = 0; col < pal.cols(); col++) {
                        int sx = pal.x() + col * (pal.cellW() + PAL_GAP);
                        int sy = pal.y() + row * (pal.cellH() + PAL_GAP);
                        int color = densePaletteColor(col, row, pal.cols(), pal.rows());
                        boolean palHov = inBox(mouseX, mouseY, sx, sy, pal.cellW(), pal.cellH());
                        if (palHov) ctx.fill(sx - 1, sy - 1, sx + pal.cellW() + 1, sy + pal.cellH() + 1, 0xFFFFFFFF);
                        ctx.fill(sx, sy, sx + pal.cellW(), sy + pal.cellH(), 0xFF000000 | color);
                    }
                }
            }
        }
        return ry;
    }

    private boolean handleRoleRowsClick(double mouseX, double mouseY, int px, int pw, int ry, String[][] roles, int clipTop, int clipBot) {
        // Topmost behavior: if picker is open, it captures its panel area and blocks click-through.
        if (editingRoleKey != null) {
            PaletteLayout pal = paletteLayout(px, pw, clipTop, clipBot);
            if (inBox(mouseX, mouseY, pal.panelX(), pal.panelY(), pal.panelW(), pal.panelH())) {
                for (String[] role : roles) {
                    String roleKey = role[0];
                    if (!roleKey.equals(editingRoleKey)) continue;
                    for (int row = 0; row < pal.rows(); row++) {
                        for (int col = 0; col < pal.cols(); col++) {
                            int sx = pal.x() + col * (pal.cellW() + PAL_GAP);
                            int sy = pal.y() + row * (pal.cellH() + PAL_GAP);
                            if (inBox(mouseX, mouseY, sx, sy, pal.cellW(), pal.cellH())) {
                                String hexVal = String.format("%06X", densePaletteColor(col, row, pal.cols(), pal.rows()));
                                ModSettings.setForPlugin(activePlugin(), roleKey, hexVal);
                                ModPackets.sendSetSetting(activePlugin(), roleKey, hexVal);
                                ModPackets.requestHistory(activePlugin());
                                editingRoleKey = null;
                                return true;
                            }
                        }
                    }
                    break;
                }
                // Consume panel click even if between swatches to prevent interacting with covered rows.
                return true;
            }
        }

        // Row interaction when click is not within the picker panel overlay.
        int rowY = ry;
        int rowWidth = Math.max(24, pw - PADDING * 2);
        for (String[] role : roles) {
            String roleKey = role[0];
            boolean editing = roleKey.equals(editingRoleKey);
            if (inBox(mouseX, mouseY, px + PADDING, rowY - 1, rowWidth, ROW_H)) {
                if (!customActive()) return true;
                editingRoleKey = editing ? null : roleKey;
                return true;
            }
            rowY += ROW_H;
        }
        return false;
    }

    private String[][] getDisplayRoles(SchemaConfig.SettingsRow[] rows) {
        int count = 0;
        for (SchemaConfig.SettingsRow row : rows) {
            if (row instanceof SchemaConfig.ColorRow) count++;
        }
        if (count > 0) {
            String[][] out = new String[count][2];
            int i = 0;
            for (SchemaConfig.SettingsRow row : rows) {
                if (row instanceof SchemaConfig.ColorRow c) {
                    out[i][0] = c.key();
                    out[i][1] = c.label();
                    i++;
                }
            }
            return out;
        }
        return ColorScheme.getRoles();
    }

    private int colorsRowRight(int px, int pw) {
        if (activeTab == 1 && customActive() && editingRoleKey != null) {
            return px + pw - PADDING - PAL_SIDE_W - 4;
        }
        return px + pw - PADDING;
    }

    private PaletteLayout paletteLayout(int px, int pw, int clipTop, int clipBot) {
        int panelW = PAL_SIDE_W;
        int panelH = PAL_SIDE_H;
        int panelX = px + pw - PADDING - panelW;
        int panelY = clipTop + 4;
        int maxPanelY = clipBot - panelH - 2;
        if (panelY > maxPanelY) panelY = Math.max(clipTop + 2, maxPanelY);

        int x = panelX + PAL_SIDE_PAD;
        int y = panelY + PAL_SIDE_PAD;
        int availableW = panelW - PAL_SIDE_PAD * 2;
        int availableH = panelH - PAL_SIDE_PAD * 2;

        int colsFromSchema = Math.max(1, SchemaConfig.get().colors.paletteCols);
        int rowsFromSchema = Math.max(1, SchemaConfig.get().colors.paletteRows);
        int cols = Math.max(PAL_MIN_COLS, colsFromSchema + 4);
        int rows = Math.max(PAL_MIN_ROWS, rowsFromSchema + 2);

        int cellW = Math.max(3, (availableW - ((cols - 1) * PAL_GAP)) / cols);
        int cellH = Math.max(3, (availableH - ((rows - 1) * PAL_GAP)) / rows);
        return new PaletteLayout(x, y, cols, rows, cellW, cellH, panelX, panelY, panelW, panelH);
    }

    private static int densePaletteColor(int col, int row, int cols, int rows) {
        int neutralCols = Math.min(3, Math.max(1, cols / 10));
        int hueCols = Math.max(1, cols - neutralCols);

        if (col >= hueCols) {
            float t = rows <= 1 ? 0.0f : (float) row / (rows - 1);
            int neutralIndex = col - hueCols;
            return switch (neutralIndex) {
                case 0 -> gray(1.0f - t * 0.9f);
                case 1 -> hsv(0.10f, 0.28f + t * 0.28f, 0.95f - t * 0.62f);
                default -> hsv(0.60f, 0.18f + t * 0.22f, 0.95f - t * 0.62f);
            };
        }

        float h = (float) col / (float) hueCols;
        float rowNorm = rows <= 1 ? 0.0f : (float) row / (rows - 1);
        float sat = Math.max(0.18f, 1.0f - rowNorm * 0.72f);
        float val = Math.max(0.22f, 1.0f - rowNorm * 0.52f);
        return hsv(h, sat, val);
    }

    private static int hsv(float h, float s, float v) {
        int i = (int) (h * 6.0f);
        float f = h * 6.0f - i;
        float p = v * (1.0f - s);
        float q = v * (1.0f - f * s);
        float t = v * (1.0f - (1.0f - f) * s);
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        return ((int) (r * 255.0f + 0.5f) << 16)
                | ((int) (g * 255.0f + 0.5f) << 8)
                | (int) (b * 255.0f + 0.5f);
    }

    private static int gray(float v) {
        int c = Math.min(255, Math.max(0, (int) (v * 255.0f + 0.5f)));
        return (c << 16) | (c << 8) | c;
    }

        private record PaletteLayout(
            int x,
            int y,
            int cols,
            int rows,
            int cellW,
            int cellH,
            int panelX,
            int panelY,
            int panelW,
            int panelH
        ) {}

            private record ClickZone(int x, int y, int w, int h, int z, Runnable onClick) {}

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

    private String activePlugin() {
        String p = ScreenManager.getActivePlugin();
        return (p == null || p.isBlank()) ? "vibecraft" : p;
    }

    private int[] previewColorsForScheme(String schemeName) {
        if ("custom".equals(schemeName)) {
            return new int[] {
                    ColorScheme.hex(ModSettings.getForPlugin(activePlugin(), "color.user"), 0x55FF55),
                    ColorScheme.hex(ModSettings.getForPlugin(activePlugin(), "color.claude"), 0x55FFFF),
                    ColorScheme.hex(ModSettings.getForPlugin(activePlugin(), "color.tool"), 0xFFAA00),
                    ColorScheme.hex(ModSettings.getForPlugin(activePlugin(), "color.output"), 0x888888),
                    ColorScheme.hex(ModSettings.getForPlugin(activePlugin(), "color.system"), 0xAAAAAA),
                    ColorScheme.hex(ModSettings.getForPlugin(activePlugin(), "color.question"), 0xFFFF55)
            };
        }
        for (SchemaConfig.ColorSchemeDef def : SchemaConfig.get().colors.schemes) {
            if (def.name.equals(schemeName)) {
                return new int[] { def.user, def.claude, def.tool, def.output, def.system, def.question };
            }
        }
        ColorScheme cs = ColorScheme.get(activePlugin());
        return new int[] { cs.user(), cs.claude(), cs.tool(), cs.output(), cs.system(), cs.question() };
    }

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

