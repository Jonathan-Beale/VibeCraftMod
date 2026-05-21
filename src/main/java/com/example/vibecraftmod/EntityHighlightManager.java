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
 * Manages client-side entity glow highlights sent from the server.
 *
 * Glow visibility is driven by a Mixin into Entity.isGlowing().
 * Color is applied via client-side scoreboard teams (one per color).
 */
public final class EntityHighlightManager {

    /** Entity network ID → color name ("red", "aqua", …). */
    static final Map<Integer, String> highlighted = new HashMap<>();

    /** color name → UUID strings currently in that team (for cleanup). */
    private static final Map<String, Set<String>> teamEntries = new HashMap<>();

    private EntityHighlightManager() {}

    public static void register() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            String color = highlighted.get(entity.getId());
            if (color != null) addToTeam(entity, world.getScoreboard(), color);
        });
    }

    /** Called by the ef_highlight_entities event handler. */
    public static void update(List<Integer> hostile, List<Integer> neutral) {
        highlighted.clear();
        for (int id : hostile) highlighted.put(id, "red");
        for (int id : neutral) highlighted.put(id, "aqua");
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

    /** Read-only view of the current highlighted map for HUD overlays. */
    public static Map<Integer, String> getHighlighted() {
        return highlighted;
    }

    // ---- Team management ----

    private static void rebuildTeams() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            teamEntries.clear();
            return;
        }
        ClientWorld world = client.world;
        Scoreboard sb = world.getScoreboard();

        for (Set<String> uuids : teamEntries.values()) {
            for (String uuid : uuids) sb.clearTeam(uuid);
        }
        teamEntries.clear();

        if (highlighted.isEmpty()) return;

        for (var entry : highlighted.entrySet()) {
            Entity e = world.getEntityById(entry.getKey());
            if (e != null) addToTeam(e, sb, entry.getValue());
        }
    }

    private static void addToTeam(Entity entity, Scoreboard sb, String color) {
        Team team = ensureTeam(sb, color);
        String uuidStr = entity.getUuid().toString();
        if (teamEntries.computeIfAbsent(color, k -> new HashSet<>()).add(uuidStr)) {
            sb.addScoreHolderToTeam(uuidStr, team);
        }
    }

    private static Team ensureTeam(Scoreboard sb, String color) {
        String name = "ef_" + color;
        Team team = sb.getTeam(name);
        if (team != null) return team;
        team = sb.addTeam(name);
        team.setColor(color.equals("aqua") ? Formatting.AQUA : Formatting.RED);
        return team;
    }
}
