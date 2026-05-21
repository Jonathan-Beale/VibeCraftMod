package com.example.vibecraftmod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages per-player client-side entity glow highlights driven by ef_highlight_entities
 * server messages.
 *
 * Glow visibility: injected via EntityGlowMixin into Entity.isGlowing().
 * Glow color:      applied via client-side scoreboard teams (one per color group).
 *
 * Supported colors: "red", "aqua" (more can be added via colorToFormatting).
 */
public final class EntityHighlightManager {

    /** Map from entity network ID → team name ("red", "aqua", …). */
    static final Map<Integer, String> highlighted = new HashMap<>();

    /** UUID strings added to each team, for cleanup. */
    private static final Map<String, Set<String>> teamEntries = new HashMap<>();

    private EntityHighlightManager() {}

    public static void register() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            String color = highlighted.get(entity.getId());
            if (color != null) addToTeam(entity, world.getScoreboard(), color);
        });
    }

    /** Called by the ef_highlight_entities event handler. */
    public static void update(Map<String, List<Integer>> groups) {
        highlighted.clear();
        for (var entry : groups.entrySet()) {
            for (int id : entry.getValue()) highlighted.put(id, entry.getKey());
        }
        rebuildTeams();
    }

    /** Called on disconnect. */
    public static void clear() {
        highlighted.clear();
        rebuildTeams();
    }

    /** Used by EntityGlowMixin — called on every entity render, must be fast. */
    public static boolean isHighlighted(int entityId) {
        return highlighted.containsKey(entityId);
    }

    // ---- Team management ----

    private static void rebuildTeams() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            teamEntries.clear();
            return;
        }
        Scoreboard sb = client.world.getScoreboard();

        // Remove all stale entries from every managed team.
        for (var entry : teamEntries.entrySet()) {
            Team team = sb.getTeam(teamName(entry.getKey()));
            for (String uuid : entry.getValue()) {
                sb.clearTeam(uuid);
                if (team != null) {} // clearTeam already removes from team
            }
        }
        teamEntries.clear();

        if (highlighted.isEmpty()) return;

        // Re-add current set.
        for (var entry : highlighted.entrySet()) {
            int id = entry.getKey();
            String color = entry.getValue();
            Entity e = client.world.getEntityById(id);
            if (e != null) addToTeam(e, sb, color);
        }
    }

    private static void addToTeam(Entity entity, Scoreboard sb, String color) {
        Team team = ensureTeam(sb, color);
        String uuidStr = entity.getUuid().toString();
        // Remove from any existing team first — addScoreHolderToTeam throws if already in one.
        sb.clearTeam(uuidStr);
        teamEntries.computeIfAbsent(color, k -> new HashSet<>()).add(uuidStr);
        sb.addScoreHolderToTeam(uuidStr, team);
    }

    private static Team ensureTeam(Scoreboard sb, String color) {
        String name = teamName(color);
        Team team = sb.getTeam(name);
        if (team != null) return team;
        team = sb.addTeam(name);
        team.setColor(colorToFormatting(color));
        return team;
    }

    private static String teamName(String color) {
        return "ef_" + color;
    }

    private static Formatting colorToFormatting(String color) {
        return switch (color) {
            case "red"    -> Formatting.RED;
            case "aqua"   -> Formatting.AQUA;
            case "blue"   -> Formatting.BLUE;
            case "green"  -> Formatting.GREEN;
            case "yellow" -> Formatting.YELLOW;
            case "white"  -> Formatting.WHITE;
            default       -> Formatting.RED;
        };
    }
}
