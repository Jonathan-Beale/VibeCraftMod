package com.example.vibecraftmod.settings;

import com.example.vibecraftmod.ui.SchemaConfig;
import com.example.vibecraftmod.ui.ScreenManager;

public record ColorScheme(int user, int claude, int tool, int output, int system, int question) {

    public static ColorScheme get() {
        String active = ScreenManager.getActivePlugin();
        return get((active == null || active.isBlank()) ? "vibecraft" : active);
    }

    public static ColorScheme get(String plugin) {
        String name = ModSettings.getForPlugin(plugin, "ui.color_scheme");
        SchemaConfig config = SchemaConfig.get();
        
        // Try to find in schema schemes
        for (SchemaConfig.ColorSchemeDef def : config.colors.schemes) {
            if (def.name.equals(name)) {
                return new ColorScheme(def.user, def.claude, def.tool, def.output, def.system, def.question);
            }
        }
        
        // Check if custom
        if ("custom".equals(name)) {
            return new ColorScheme(
                    hex(ModSettings.getForPlugin(plugin, "color.user"),     0x55FF55),
                    hex(ModSettings.getForPlugin(plugin, "color.claude"),   0x55FFFF),
                    hex(ModSettings.getForPlugin(plugin, "color.tool"),     0xFFAA00),
                    hex(ModSettings.getForPlugin(plugin, "color.output"),   0x888888),
                    hex(ModSettings.getForPlugin(plugin, "color.system"),   0xAAAAAA),
                    hex(ModSettings.getForPlugin(plugin, "color.question"), 0xFFFF55));
        }
        
        // Default to first scheme from schema, or hardcoded terminal
        if (config.colors.schemes.length > 0) {
            SchemaConfig.ColorSchemeDef def = config.colors.schemes[0];
            return new ColorScheme(def.user, def.claude, def.tool, def.output, def.system, def.question);
        }
        
        return new ColorScheme(0x55FF55, 0x55FFFF, 0xFFAA00, 0x888888, 0xAAAAAA, 0xFFFF55);  // terminal
    }

    // Dynamic schemes and roles from schema
    public static ColorScheme[] getAll() {
        SchemaConfig config = SchemaConfig.get();
        ColorScheme[] schemes = new ColorScheme[config.colors.schemes.length];
        for (int i = 0; i < config.colors.schemes.length; i++) {
            SchemaConfig.ColorSchemeDef def = config.colors.schemes[i];
            schemes[i] = new ColorScheme(def.user, def.claude, def.tool, def.output, def.system, def.question);
        }
        return schemes;
    }

    public static String[] getNames() {
        SchemaConfig config = SchemaConfig.get();
        String[] names = new String[config.colors.schemes.length + 1];  // +1 for custom
        for (int i = 0; i < config.colors.schemes.length; i++) {
            names[i] = config.colors.schemes[i].name;
        }
        names[config.colors.schemes.length] = "custom";
        return names;
    }

    public static String[] getLabels() {
        SchemaConfig config = SchemaConfig.get();
        String[] labels = new String[config.colors.schemes.length + 1];  // +1 for custom
        for (int i = 0; i < config.colors.schemes.length; i++) {
            labels[i] = config.colors.schemes[i].label;
        }
        labels[config.colors.schemes.length] = "Custom";
        return labels;
    }

    public static String[][] getRoles() {
        SchemaConfig config = SchemaConfig.get();
        if (config.colors.roles.length == 0) {
            return new String[][] {
                    {"color.user", "User Text"},
                    {"color.claude", "Claude Text"},
                    {"color.tool", "Tool Calls"},
                    {"color.output", "Command Output"},
                    {"color.system", "System Messages"},
                    {"color.question", "Questions"}
            };
        }
        String[][] roles = new String[config.colors.roles.length][2];
        for (int i = 0; i < config.colors.roles.length; i++) {
            roles[i][0] = config.colors.roles[i].key;
            roles[i][1] = config.colors.roles[i].label;
        }
        return roles;
    }

    // Legacy static accessors for compatibility
    @Deprecated public static final int PALETTE_COLS = 21;
    @Deprecated public static final int PALETTE_ROWS = 6;
    @Deprecated public static final int[] PALETTE = buildPalette();
    
    public static int getPaletetteCols() {
        return SchemaConfig.get().colors.paletteCols;
    }
    
    public static int getPaletteRows() {
        return SchemaConfig.get().colors.paletteRows;
    }

    public static int hex(String s, int fallback) {
        if (s == null || s.isBlank()) return fallback;
        try { return (int)(Long.parseLong(s.replaceFirst("^#", ""), 16)) & 0xFFFFFF; }
        catch (NumberFormatException e) { return fallback; }
    }

    public static int hex(com.google.gson.JsonObject obj, String key, int fallback) {
        if (obj == null || !obj.has(key)) return fallback;
        com.google.gson.JsonElement elem = obj.get(key);
        if (elem.isJsonPrimitive()) {
            return hex(elem.getAsString(), fallback);
        }
        return fallback;
    }

    private static int[] buildPalette() {
        int cols = SchemaConfig.get().colors.paletteCols;
        int rows = SchemaConfig.get().colors.paletteRows;
        // Six rows from bright to muted/deep, with extra neutral families.
        float[] sats = { 1.00f, 0.82f, 0.62f, 1.00f, 0.78f, 0.55f };
        float[] vals = { 1.00f, 1.00f, 1.00f, 0.72f, 0.55f, 0.38f };
        int[] p = new int[cols * rows];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 18; col++) {
                p[row * cols + col] = hsv(col / 18.0f, sats[row], vals[row]);
            }

            float t = row / (float)(rows - 1);
            float grayV = 1.0f - t * 0.85f;
            p[row * cols + 18] = gray(grayV);

            // Warm neutral family (brown/sand range).
            p[row * cols + 19] = hsv(0.10f, 0.35f + t * 0.20f, 0.95f - t * 0.60f);
            // Cool neutral family (slate/steel range).
            p[row * cols + 20] = hsv(0.60f, 0.20f + t * 0.18f, 0.95f - t * 0.60f);
        }
        return p;
    }

    private static int hsv(float h, float s, float v) {
        int i = (int)(h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0: r=v; g=t; b=p; break; case 1: r=q; g=v; b=p; break;
            case 2: r=p; g=v; b=t; break; case 3: r=p; g=q; b=v; break;
            case 4: r=t; g=p; b=v; break; default: r=v; g=p; b=q; break;
        }
        return ((int)(r*255+.5f) << 16) | ((int)(g*255+.5f) << 8) | (int)(b*255+.5f);
    }

    private static int gray(float v) {
        int c = Math.min(255, (int)(v * 255 + .5f));
        return (c << 16) | (c << 8) | c;
    }
}

