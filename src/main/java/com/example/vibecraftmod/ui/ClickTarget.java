package com.example.vibecraftmod.ui;

import com.google.gson.JsonObject;

/**
 * Represents a clickable region on screen.
 */
public class ClickTarget {
    public final int x, y, w, h;
    public final JsonObject action;
    public final String optionValue;  // for option-based actions (question, dropdown)

    public ClickTarget(int x, int y, int w, int h, JsonObject action, String optionValue) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.action = action;
        this.optionValue = optionValue;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
