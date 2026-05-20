package com.example.vibecraftmod.ui;

import net.minecraft.client.gui.DrawContext;
import com.google.gson.JsonObject;
import java.util.*;

/**
 * Factory for registering all standard widget renderers.
 * Each widget type gets a registry entry that delegates to schema screen render helpers.
 * This keeps widget behavior schema-driven and registry-based.
 */
public class StandardWidgets {
    
    /**
     * Register all standard widget renderers and overlay widget types.
     * Call once at app startup.
     */
    public static void registerAll() {
        WidgetRenderer.register("toolbar", toolbarRenderer());
        WidgetRenderer.register("history", historyRenderer());
        WidgetRenderer.register("question_options", questionOptionsRenderer());
        WidgetRenderer.register("input", inputRenderer());
        WidgetRenderer.register("modal", modalRenderer());
        WidgetRenderer.register("action_row", actionRowRenderer());
        WidgetRenderer.register("dropdown", dropdownRenderer());
        WidgetRenderer.register("tabs", tabsRenderer());
        WidgetRenderer.register("text", textRenderer());
        WidgetRenderer.register("spacer", spacerRenderer());
        WidgetRenderer.register("panel", panelRenderer());
        WidgetRenderer.register("overlay", overlayRenderer());

        // Overlay widget types
        OverlayWidget.register("armor_slots", config -> new SlotOverlayWidget(config));
        OverlayWidget.register("slots", config -> new SlotOverlayWidget(config));
        OverlayWidget.register("bar", config -> new BarOverlayWidget(config));
        OverlayWidget.register("icon", config -> new IconOverlayWidget(config));
        OverlayWidget.register("text", config -> new TextOverlayWidget(config));
        OverlayWidget.register("hostile_indicator", config -> new HostileIndicatorWidget());
    }

    private static WidgetRenderer toolbarRenderer() {
        return new WidgetRenderer() {
            @Override
            public int render(DrawContext ctx, JsonObject widget, int x, int y, int maxW, int maxH,
                             int mouseX, int mouseY, List<ClickTarget> clickTargets) {
                // Rendered by SchemaScreen.renderToolbar()
                // Configuration driven by server-side schema
                return 20;
            }

            @Override
            public int getHeight(JsonObject widget, int maxW) {
                return 20;
            }
        };
    }

    private static WidgetRenderer historyRenderer() {
        return new WidgetRenderer() {
            @Override
            public int render(DrawContext ctx, JsonObject widget, int x, int y, int maxW, int maxH,
                             int mouseX, int mouseY, List<ClickTarget> clickTargets) {
                return maxH; // flex widget
            }

            @Override
            public int getHeight(JsonObject widget, int maxW) {
                return -1; // flex (takes remaining space)
            }
        };
    }

    private static WidgetRenderer questionOptionsRenderer() {
        return new WidgetRenderer() {
            @Override
            public int render(DrawContext ctx, JsonObject widget, int x, int y, int maxW, int maxH,
                             int mouseX, int mouseY, List<ClickTarget> clickTargets) {
                return widget.has("height") ? widget.get("height").getAsInt() : 20;
            }

            @Override
            public int getHeight(JsonObject widget, int maxW) {
                return widget.has("height") ? widget.get("height").getAsInt() : 20;
            }
        };
    }

    private static WidgetRenderer inputRenderer() {
        return new WidgetRenderer() {
            @Override
            public int render(DrawContext ctx, JsonObject widget, int x, int y, int maxW, int maxH,
                             int mouseX, int mouseY, List<ClickTarget> clickTargets) {
                return widget.has("height") ? widget.get("height").getAsInt() : 72;
            }

            @Override
            public int getHeight(JsonObject widget, int maxW) {
                return widget.has("height") ? widget.get("height").getAsInt() : 72;
            }
        };
    }

    private static WidgetRenderer modalRenderer() {
        return new WidgetRenderer() {
            @Override
            public int render(DrawContext ctx, JsonObject widget, int x, int y, int maxW, int maxH,
                             int mouseX, int mouseY, List<ClickTarget> clickTargets) {
                return widget.has("height") ? widget.get("height").getAsInt() : 200;
            }

            @Override
            public int getHeight(JsonObject widget, int maxW) {
                return widget.has("height") ? widget.get("height").getAsInt() : 200;
            }
        };
    }

    private static WidgetRenderer actionRowRenderer() {
        return new WidgetRenderer() {
            @Override
            public int render(DrawContext ctx, JsonObject widget, int x, int y, int maxW, int maxH,
                             int mouseX, int mouseY, List<ClickTarget> clickTargets) {
                return widget.has("height") ? widget.get("height").getAsInt() : 18;
            }

            @Override
            public int getHeight(JsonObject widget, int maxW) {
                return widget.has("height") ? widget.get("height").getAsInt() : 18;
            }
        };
    }

    private static WidgetRenderer dropdownRenderer() {
        return new WidgetRenderer() {
            @Override
            public int render(DrawContext ctx, JsonObject widget, int x, int y, int maxW, int maxH,
                             int mouseX, int mouseY, List<ClickTarget> clickTargets) {
                return 18;
            }

            @Override
            public int getHeight(JsonObject widget, int maxW) {
                return 18;
            }
        };
    }

    private static WidgetRenderer tabsRenderer() {
        return new WidgetRenderer() {
            @Override
            public int render(DrawContext ctx, JsonObject widget, int x, int y, int maxW, int maxH,
                             int mouseX, int mouseY, List<ClickTarget> clickTargets) {
                return 18;
            }

            @Override
            public int getHeight(JsonObject widget, int maxW) {
                return 18;
            }
        };
    }

    private static WidgetRenderer textRenderer() {
        return new WidgetRenderer() {
            @Override
            public int render(DrawContext ctx, JsonObject widget, int x, int y, int maxW, int maxH,
                             int mouseX, int mouseY, List<ClickTarget> clickTargets) {
                return widget.has("height") ? widget.get("height").getAsInt() : 12;
            }

            @Override
            public int getHeight(JsonObject widget, int maxW) {
                return widget.has("height") ? widget.get("height").getAsInt() : 12;
            }
        };
    }

    private static WidgetRenderer spacerRenderer() {
        return new WidgetRenderer() {
            @Override
            public int render(DrawContext ctx, JsonObject widget, int x, int y, int maxW, int maxH,
                             int mouseX, int mouseY, List<ClickTarget> clickTargets) {
                return widget.has("height") ? widget.get("height").getAsInt() : 6;
            }

            @Override
            public int getHeight(JsonObject widget, int maxW) {
                return widget.has("height") ? widget.get("height").getAsInt() : 6;
            }
        };
    }

    private static WidgetRenderer panelRenderer() {
        return new WidgetRenderer() {
            @Override
            public int render(DrawContext ctx, JsonObject widget, int x, int y, int maxW, int maxH,
                             int mouseX, int mouseY, List<ClickTarget> clickTargets) {
                return maxH;
            }

            @Override
            public int getHeight(JsonObject widget, int maxW) {
                return -1; // flex
            }
        };
    }

    private static WidgetRenderer overlayRenderer() {
        return new WidgetRenderer() {
            @Override
            public int render(DrawContext ctx, JsonObject widget, int x, int y, int maxW, int maxH,
                             int mouseX, int mouseY, List<ClickTarget> clickTargets) {
                return 0;
            }

            @Override
            public int getHeight(JsonObject widget, int maxW) {
                return 0;
            }
        };
    }
}
