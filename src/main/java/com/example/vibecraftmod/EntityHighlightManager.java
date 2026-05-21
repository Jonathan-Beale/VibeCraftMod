package com.example.vibecraftmod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages client-side entity glow highlights sent from the server.
 *
 * Glow visibility is driven by a Mixin into Entity.isGlowing() so server metadata
 * packets cannot override it.  Color is applied via the client scoreboard so the
 * highlight renders red without needing NMS on the server.
 */
public final class EntityHighlightManager {

    private static final String TEAM_NAME = "ef_hostile";

    /** Entity network IDs that should glow. Read by EntityGlowMixin on every render call. */
    static final Set<Integer> highlighted = new HashSet<>();

    /** UUID strings currently in the red scoreboard team (for cleanup). */
    private static final Set<String> teamEntries = new HashSet<>();

    private EntityHighlightManager() {}

    public static void register() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!highlighted.contains(entity.getId())) return;
            addToTeam(entity, world.getScoreboard());
        });
    }

    /** Called by the ef_highlight_entities event handler. */
    public static void update(List<Integer> entityIds) {
        highlighted.clear();
        highlighted.addAll(entityIds);
        rebuildTeam();
    }

    /** Called on disconnect to reset all state. */
    public static void clear() {
        highlighted.clear();
        rebuildTeam();
    }

    /** Used by EntityGlowMixin — must be fast (called on every entity render). */
    public static boolean isHighlighted(int entityId) {
        return highlighted.contains(entityId);
    }

    /** Used by WorldRendererOutlineMixin to activate the outline pass. */
    public static boolean hasHighlights() {
        return !highlighted.isEmpty();
    }

    // ---- Team management ----

    private static void rebuildTeam() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            teamEntries.clear();
            return;
        }
        ClientWorld world = client.world;
        Scoreboard sb = world.getScoreboard();

        for (String uuid : new HashSet<>(teamEntries)) {
            sb.clearTeam(uuid);
        }
        teamEntries.clear();

        if (highlighted.isEmpty()) return;

        Team team = ensureTeam(sb);
        for (int id : highlighted) {
            Entity e = world.getEntityById(id);
            if (e != null) addToTeam(e, sb, team);
        }
    }

    private static void addToTeam(Entity entity, Scoreboard sb) {
        addToTeam(entity, sb, ensureTeam(sb));
    }

    private static void addToTeam(Entity entity, Scoreboard sb, Team team) {
        String uuidStr = entity.getUuid().toString();
        if (teamEntries.add(uuidStr)) {
            sb.addScoreHolderToTeam(uuidStr, team);
        }
    }

    private static Team ensureTeam(Scoreboard sb) {
        Team team = sb.getTeam(TEAM_NAME);
        if (team != null) return team;
        team = sb.addTeam(TEAM_NAME);
        team.setColor(Formatting.RED);
        return team;
    }
}
