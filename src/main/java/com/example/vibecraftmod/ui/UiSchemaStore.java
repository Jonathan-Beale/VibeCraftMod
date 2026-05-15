package com.example.vibecraftmod.ui;

import com.example.vibecraftmod.DebugConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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
      schema = value;
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
