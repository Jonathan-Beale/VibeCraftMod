package com.example.vibecraftmod.ui;

import com.example.vibecraftmod.DebugConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;

public final class UiSchemaStore {
    private UiSchemaStore() {}

    private static JsonObject schema;

    public static synchronized boolean hasSchema() {
      return schema != null;
    }

    /** Clear schema on disconnect so the next login fetches fresh data from the server. */
    public static synchronized void clear() {
      schema = null;
      SchemaConfig.reload();
      ScreenManager.reload();
    }


    public static synchronized void setSchema(JsonObject value) {
      schema = mergeSchemaByPlugin(schema, value);
      if (DebugConfig.DEBUG_SCHEMA) {
        System.out.println("[VibeCraftMod] UiSchemaStore.setSchema: schema set, triggering reload");
        // Print out the schema source (top-level keys and screen IDs)
        if (schema != null) {
          System.out.print("[VibeCraftMod] Schema top-level keys: ");
          for (String key : schema.keySet()) {
            System.out.print(key + " ");
          }
          System.out.println();
          if (schema.has("screens")) {
            try {
              var arr = schema.getAsJsonArray("screens");
              System.out.print("[VibeCraftMod] Schema screens: ");
              for (int i = 0; i < arr.size(); i++) {
                var s = arr.get(i).getAsJsonObject();
                if (s.has("id")) System.out.print(s.get("id").getAsString() + " ");
              }
              System.out.println();
            } catch (Exception e) {
              System.out.println("[VibeCraftMod] Error printing schema screens: " + e);
            }
          }
        }
      }
      SchemaConfig.reload();
      ScreenManager.reload();
      OverlayManager.reload();
    }

    /**
     * Merge full-schema updates by plugin so one plugin does not wipe all others.
     * Incoming screens replace existing screens for the same plugin(s), while other
     * plugins' screens remain intact.
     */
    private static JsonObject mergeSchemaByPlugin(JsonObject current, JsonObject incoming) {
      if (incoming == null) return current;
      if (current == null) return incoming;

      boolean incomingHasScreens = incoming.has("screens") && incoming.get("screens").isJsonArray();
      if (!incomingHasScreens) {
        return incoming;
      }

      JsonObject merged = current.deepCopy();

      JsonArray incomingScreens = incoming.getAsJsonArray("screens");
      Set<String> replacedPlugins = new HashSet<>();
      for (JsonElement el : incomingScreens) {
        if (!el.isJsonObject()) continue;
        JsonObject screen = el.getAsJsonObject();
        if (!screen.has("plugin") || !screen.get("plugin").isJsonPrimitive()) continue;
        String pluginId = screen.get("plugin").getAsString();
        if (pluginId != null && !pluginId.isBlank()) {
          replacedPlugins.add(pluginId);
        }
      }

      JsonArray existingScreens = (merged.has("screens") && merged.get("screens").isJsonArray())
              ? merged.getAsJsonArray("screens")
              : new JsonArray();

      JsonArray mergedScreens = new JsonArray();
      for (JsonElement el : existingScreens) {
        if (!el.isJsonObject()) {
          mergedScreens.add(el.deepCopy());
          continue;
        }
        JsonObject screen = el.getAsJsonObject();
        if (screen.has("plugin") && screen.get("plugin").isJsonPrimitive()) {
          String pluginId = screen.get("plugin").getAsString();
          if (replacedPlugins.contains(pluginId)) {
            continue;
          }
        }
        mergedScreens.add(screen.deepCopy());
      }

      for (JsonElement el : incomingScreens) {
        mergedScreens.add(el.deepCopy());
      }
      merged.add("screens", mergedScreens);

      // Merge other top-level keys from incoming (except screens, handled above).
      for (String key : incoming.keySet()) {
        if ("screens".equals(key)) continue;
        merged.add(key, incoming.get(key).deepCopy());
      }

      return merged;
    }

    /**
     * Patch the current schema with a partial update (diff/patch object).
     * Only changed parts are updated and reloaded.
     */
    public static synchronized void patchSchema(JsonObject patch) {
      if (schema == null) schema = new JsonObject();
      applyPatch(schema, patch);
      if (DebugConfig.DEBUG_SCHEMA) {
        System.out.println("[VibeCraftMod] UiSchemaStore.patchSchema: schema patched, triggering partial reload");
      }
      // For now, reload everything; future: reload only affected parts
      SchemaConfig.reload();
      ScreenManager.reload();
      OverlayManager.reload();
    }

    /**
     * Recursively apply a patch object to a target object.
     */
    private static void applyPatch(JsonObject target, JsonObject patch) {
      for (String key : patch.keySet()) {
        if (patch.get(key).isJsonObject() && target.has(key) && target.get(key).isJsonObject()) {
          applyPatch(target.getAsJsonObject(key), patch.getAsJsonObject(key));
        } else {
          target.add(key, patch.get(key));
        }
      }
    }

    public static synchronized JsonObject getSchema() {
        return schema;
    }

    public static synchronized JsonArray widgets() {
        JsonObject s = getSchema();
        if (s == null) return new JsonArray();
        if (s.has("widgets") && s.get("widgets").isJsonArray()) return s.getAsJsonArray("widgets");
        return new JsonArray();
    }
}
