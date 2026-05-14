package com.example.vibecraftmod.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClaudeHudState {

    public record HudLine(String text, int color, boolean thought, boolean collapsed) {
        public HudLine(String text, int color)                  { this(text, color, false, false); }
        public HudLine(String text, int color, boolean thought) { this(text, color, thought, false); }
    }
    public record PendingQuestion(String prompt, List<String> options) {}

    private static final int MAX_LINES = 500;

    private static final List<HudLine> lines = new ArrayList<>();
    private static boolean streaming = false;
    private static String currentTool = null;
    private static String currentToolDetail = null;
    private static boolean visible = true;
    private static long streamEndTime = 0;
    private static PendingQuestion pendingQuestion = null;

    public static synchronized void addLine(String text, int color) {
        lines.add(new HudLine(text, color, false, false));
        if (lines.size() > MAX_LINES) lines.remove(0);
    }

    public static synchronized void addCollapsedLine(String text, int color) {
        lines.add(new HudLine(text, color, false, true));
        if (lines.size() > MAX_LINES) lines.remove(0);
    }

    public static synchronized void addThought(String text) {
        lines.add(new HudLine(text, 0x888899, true));
        if (lines.size() > MAX_LINES) lines.remove(0);
    }

    public static synchronized void setStreaming(boolean s) {
        streaming = s;
    }

    public static synchronized void setCurrentTool(String tool, String detail) {
        currentTool = tool;
        currentToolDetail = detail;
    }

    public static synchronized void setStreamEndTime(long t) {
        streamEndTime = t;
    }

    public static synchronized void toggleVisible() {
        visible = !visible;
    }

    public static synchronized void clear() {
        lines.clear();
        streaming = false;
        currentTool = null;
        currentToolDetail = null;
    }

    public static synchronized void replaceWithHistory(List<HudLine> historyLines) {
        lines.clear();
        for (HudLine hl : historyLines) {
            lines.add(hl);
            if (lines.size() > MAX_LINES) lines.remove(0);
        }
    }

    public static synchronized boolean isEmpty() {
        return lines.isEmpty();
    }

    public static synchronized void setQuestion(PendingQuestion q) { pendingQuestion = q; }
    public static synchronized PendingQuestion getQuestion()       { return pendingQuestion; }
    public static synchronized void clearQuestion()                { pendingQuestion = null; }

    public static synchronized List<HudLine> getLines() {
        return Collections.unmodifiableList(new ArrayList<>(lines));
    }

    public static synchronized boolean isStreaming()          { return streaming; }
    public static synchronized String  getCurrentTool()       { return currentTool; }
    public static synchronized String  getCurrentToolDetail() { return currentToolDetail; }
    public static synchronized boolean isVisible()            { return visible; }
    public static synchronized long    getStreamEndTime()     { return streamEndTime; }
}
