package com.example.vibecraftmod.ui;

import com.google.gson.JsonObject;
import com.example.vibecraftmod.settings.ColorScheme;

/**
 * Immutable color scheme for a widget. Extracted once from schema,
 * then passed to renderers to avoid repeated color extraction.
 * 
 * Supports: background, text, hover states, borders/outlines, and variants.
 */
public record WidgetColors(
    // Base colors
    int bg,
    int text,
    
    // Hover/active states
    int hoverBg,
    int hoverText,
    int activeBg,
    int activeText,
    
    // Borders/outlines
    int border,
    int hoverBorder,
    int activeBorder,
    int borderWidth,
    
    // Variants (danger, disabled, etc.)
    int variantBg,
    int variantText,
    int variantBorder,
    int variantHoverBg,
    int variantHoverText,
    int variantHoverBorder
) {
    private static final int DEFAULT_BG = 0xFF1A1A2E;
    private static final int DEFAULT_TEXT = 0xFFEEEEEE;
    private static final int DEFAULT_HOVER_BG = 0xFF2A2A4E;
    private static final int DEFAULT_HOVER_TEXT = 0xFFFFFFFF;
    private static final int DEFAULT_BORDER = 0xFF555566;
    private static final int DEFAULT_BORDER_WIDTH = 1;

    /**
     * Extract WidgetColors from schema widget definition.
     * Uses sensible defaults for missing keys.
     */
    public static WidgetColors from(JsonObject widget, String prefix) {
        return new WidgetColors(
            ColorScheme.hex(widget, prefix + "Bg", DEFAULT_BG),
            ColorScheme.hex(widget, prefix + "Text", DEFAULT_TEXT),
            ColorScheme.hex(widget, prefix + "HoverBg", DEFAULT_HOVER_BG),
            ColorScheme.hex(widget, prefix + "HoverText", DEFAULT_HOVER_TEXT),
            ColorScheme.hex(widget, prefix + "ActiveBg", DEFAULT_HOVER_BG),
            ColorScheme.hex(widget, prefix + "ActiveText", DEFAULT_HOVER_TEXT),
            ColorScheme.hex(widget, prefix + "Border", DEFAULT_BORDER),
            ColorScheme.hex(widget, prefix + "HoverBorder", DEFAULT_BORDER),
            ColorScheme.hex(widget, prefix + "ActiveBorder", DEFAULT_BORDER),
            widget.has(prefix + "BorderWidth") ? widget.get(prefix + "BorderWidth").getAsInt() : DEFAULT_BORDER_WIDTH,
            ColorScheme.hex(widget, prefix + "DangerBg", 0xFFAA3333),
            ColorScheme.hex(widget, prefix + "DangerText", 0xFFFFEEEE),
            ColorScheme.hex(widget, prefix + "DangerBorder", 0xFFDD5555),
            ColorScheme.hex(widget, prefix + "DangerHoverBg", 0xFFBB4444),
            ColorScheme.hex(widget, prefix + "DangerHoverText", 0xFFFFFFFF),
            ColorScheme.hex(widget, prefix + "DangerHoverBorder", 0xFFEE6666)
        );
    }

    /**
     * Extract multiple prefixed color sets (for widgets with multiple element types).
     */
    public static WidgetColors[] fromMultiple(JsonObject widget, String... prefixes) {
        WidgetColors[] colors = new WidgetColors[prefixes.length];
        for (int i = 0; i < prefixes.length; i++) {
            colors[i] = from(widget, prefixes[i]);
        }
        return colors;
    }

    /**
     * Get color for current interaction state.
     */
    public int bgForState(boolean hover, boolean active) {
        if (active) return activeBg;
        if (hover) return hoverBg;
        return bg;
    }

    public int textForState(boolean hover, boolean active) {
        if (active) return activeText;
        if (hover) return hoverText;
        return text;
    }

    public int borderForState(boolean hover, boolean active) {
        if (active) return activeBorder;
        if (hover) return hoverBorder;
        return border;
    }

    /**
     * Danger variant colors for current state.
     */
    public int dangerBgForState(boolean hover) {
        return hover ? variantHoverBg : variantBg;
    }

    public int dangerTextForState(boolean hover) {
        return hover ? variantHoverText : variantText;
    }

    public int dangerBorderForState(boolean hover) {
        return hover ? variantHoverBorder : variantBorder;
    }
}
