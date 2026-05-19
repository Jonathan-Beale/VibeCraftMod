# VibeCraftMod — EnchantForge Visualization Plan

## Overview

EnchantForge tracks significant per-player state (energy, active effects, cooldowns, combat status,
absorption regen) that has no HUD representation beyond vanilla indicators or an ugly BossBar.
VibeCraftMod already has all the infrastructure to visualize this state — `binding_update` events
populate existing `bar`, `icon`, and `text` overlay widgets. The main work is:

- **Server-side (EnchantForge):** push state changes via `binding_update` events at the right moments
- **Client-side (VibeCraftMod):** define overlay layouts in `ui/main.json`; one feature (glint colors)
  requires a new `ItemRenderer` mixin

---

## Checklist

**Phase 1 — Core HUD (highest gap, server-side plumbing only)**
- [ ] [§1 Energy bar](#1-energy-bar--replace-bossbar) — replace BossBar with a clean HUD widget
- [ ] [§2 Armor enchant cooldown strip](#2-armor-enchant-cooldown-strip) — show ready/cooling state for armor enchants
- [ ] [§3 Active timed effects](#3-active-timed-effects-with-countdowns) — labeled effect rows with draining duration bars

**Phase 2 — Feedback (event-driven notifications)**
- [ ] [§4 Trigger activation flash](#4-trigger-activation-flash) — brief overlay when an enchant fires
- [ ] [§5 Absorption regen progress](#5-absorption-regen-progress) — regen fill indicator during out-of-combat
- [ ] [§6 Combat state indicator](#6-combat-state-indicator) — subtle in/out-of-combat status

**Phase 3 — Rendering (requires mod mixin)**
- [ ] [§7 Per-enchant glint colors](#7-per-enchant-glint-colors) — `ItemRenderer` mixin reads PDC color, no resource pack needed

---

## 1. Energy Bar — Replace BossBar

**Current state:** `EnergyManager` notifies `SuitListener` via a callback; `SuitListener` maintains a
BossBar (blue/yellow/red based on threshold). BossBar is visually obtrusive, conflicts with actual
boss fights, and gives no charge-tier context.

**Proposed:** Remove the BossBar and push energy state to a VibeCraftMod `bar` overlay widget.
Include charge-tier state (idle / charging / charged) as a color or label alongside the bar.

**Server-side (EnchantForge)**

In `EnergyManager.onChanged` callback (wired at startup in `EnchantForge.onEnable()`), send:
```json
{
  "type": "binding_updates",
  "updates": {
    "enchantforge.energy":       75.4,
    "enchantforge.energy_max":   100.0,
    "enchantforge.charge_tier":  0
  }
}
```
`charge_tier` values: `0` = idle, `1`/`2`/`3` = thruster charge level, `-1` = fired/cooldown.

Also send on `SuitListener` charge-state transitions (currently only updates the BossBar title).

**Client-side (VibeCraftMod)**

Add to `ui/main.json` overlays:
```json
{
  "id": "enchantforge:energy",
  "plugin": "enchantforge",
  "type": "bar",
  "position": { "x": -10, "y": -30, "anchor": "bottom_right" },
  "size": { "width": 80, "height": 6 },
  "dataBinding": "enchantforge.energy",
  "maxBinding": "enchantforge.energy_max",
  "style": {
    "fillColor": "00B8A0FF",
    "backgroundColor": "222222AA",
    "chargeTierBinding": "enchantforge.charge_tier"
  }
}
```

**Scope**
- EnchantForge: edit `EnergyManager.java` (add `VibeCraftUiBridge` callback), `SuitListener.java`
  (remove BossBar, send charge-tier updates), `VibeCraftUiBridge.java` (add `sendBindingUpdate`)
- VibeCraftMod: update `ui/main.json`; extend `BarOverlayWidget` to support `chargeTierBinding`
  for color shift if desired

---

## 2. Armor Enchant Cooldown Strip

**Current state:** Item-triggered enchants (held weapon) show cooldown via the item cooldown API.
Armor enchants (`on_damage_taken`, `stat_threshold`, `on_suit_jump`) have no visual when on cooldown
— players cannot tell when `last_stand` or `berserker` is ready again.

**Proposed:** A compact horizontal or vertical strip of icons — one per active armor enchant — that
dims and shows a fill animation while on cooldown, then brightens when ready.

**Server-side (EnchantForge)**

Send on every cooldown set and on each tick where cooldown expires, keyed by enchant:
```json
{
  "type": "binding_updates",
  "updates": {
    "enchantforge.cooldown.berserker":   0.0,
    "enchantforge.cooldown.last_stand":  47.2,
    "enchantforge.cooldown.bulwark":     0.0
  }
}
```
Value is remaining seconds (`0.0` = ready). Derive from `CooldownManager` expiry timestamps.

Push on: `CooldownManager.setCooldown()` (start), and via a lightweight scheduler that sends
updates while any cooldown is active (every 20 ticks is sufficient — visual precision only).

**Client-side (VibeCraftMod)**

Add a new `cooldown_strip` widget type (or compose from existing `icon` + `bar`) that renders a
row of enchant icons. Each icon reads `enchantforge.cooldown.<key>` and applies a radial or linear
fill overlay.

Alternatively, use existing `bar` widgets in a vertical stack — one per equipped armor enchant —
with server-sent `enchantforge.cooldown.*` bindings. The server already knows which enchants are
equipped via `PlayerEnchantIndex`.

**Scope**
- EnchantForge: edit `CooldownManager.java` (fire callback on set/clear), `VibeCraftUiBridge.java`
  (add `sendCooldownUpdate`), `EnchantForge.java` (wire callback at startup)
- VibeCraftMod: update `ui/main.json`; possibly add `cooldown_strip` widget type

---

## 3. Active Timed Effects with Countdowns

**Current state:** When a triggered enchant applies a timed effect (e.g. Berserker → Strength for
15s), the only indicator is the vanilla potion icon in the inventory — no label identifying which
enchant caused it, no remaining-time bar.

**Proposed:** A compact active-effects row: each active tracked effect shows its enchant display
name, level, and a draining duration bar. Clears when the effect ends.

**Server-side (EnchantForge)**

Send when `ActiveEffectTracker` entries change (on apply, on end-condition met, on remove):
```json
{
  "type": "binding_updates",
  "updates": {
    "enchantforge.effect.berserker.active":    true,
    "enchantforge.effect.berserker.label":     "Berserker II",
    "enchantforge.effect.berserker.remaining": 14.6,
    "enchantforge.effect.berserker.duration":  15.0,
    "enchantforge.effect.last_stand.active":   false
  }
}
```
`remaining` counts down via a 20-tick scheduler while effects are active (same scheduler as §2).

**Client-side (VibeCraftMod)**

A `binding_list` overlay widget (new) or a fixed-size stack of labeled bars driven by
`enchantforge.effect.*` bindings. Each row: name label on the left, draining bar on the right.
Rows with `active: false` collapse to zero height.

**Scope**
- EnchantForge: edit `ActiveEffectTracker.java` (add change callback), `VibeCraftUiBridge.java`,
  scheduler in `EnchantForge.java`
- VibeCraftMod: update `ui/main.json`; add collapsing list behavior to overlay manager if needed

---

## 4. Trigger Activation Flash

**Current state:** No HUD feedback when an enchant fires beyond particle/sound effects (which are
configurable per-enchant in YAML but are not always set).

**Proposed:** A brief (1–2 second) notification in a consistent corner position:
`"Berserker ▲ II"` with the enchant's display color. Non-blocking, fades out automatically.

**Server-side (EnchantForge)**

Send immediately after a trigger dispatch succeeds (in `StackingDispatcher.dispatch()` or the
`EnchantEventRouter` apply lambda):
```json
{
  "type": "binding_updates",
  "updates": {
    "enchantforge.flash.label":   "Berserker II",
    "enchantforge.flash.ts":      1716076234521
  }
}
```
`ts` is a millisecond timestamp — the client uses it to detect new flashes regardless of label
content, so two activations of the same enchant in quick succession both register.

**Client-side (VibeCraftMod)**

A `flash` overlay widget type (new, or extend `text` widget): renders the label for a fixed
duration after `ts` changes, then fades. No server round-trip needed to dismiss it.

**Scope**
- EnchantForge: edit `StackingDispatcher.java` (or dispatch lambdas in `EnchantEventRouter.java`),
  `VibeCraftUiBridge.java`
- VibeCraftMod: add `flash` widget type to `StandardWidgets`, `WidgetRenderer`; update `ui/main.json`

---

## 5. Absorption Regen Progress

**Current state:** Out-of-combat absorption regen (`outOfCombatRefreshTicks` / `outOfCombatRegenPerTick`)
runs silently on a 20-tick ticker in `EquipmentEnchantListener`. Players have no visual cue that
regen is in progress or how far along it is.

**Proposed:** While regen is active, push the current absorption value and max so a thin progress
indicator can show fill state. Clear when regen completes or combat resumes.

**Server-side (EnchantForge)**

Send in the `refreshOutOfCombat` ticker while regen is running, and once more on completion:
```json
{
  "type": "binding_updates",
  "updates": {
    "enchantforge.absorption":        6.4,
    "enchantforge.absorption_max":    8.0,
    "enchantforge.absorption_regen":  true
  }
}
```

**Client-side (VibeCraftMod)**

Augment the existing `enchantforge:armor_sidebar` overlay or add a dedicated absorption widget.
Show a pulsing or filling bar only when `absorption_regen` is `true`; hide otherwise.

**Scope**
- EnchantForge: edit `EquipmentEnchantListener.java` (send updates in regen ticker),
  `VibeCraftUiBridge.java`
- VibeCraftMod: update `ui/main.json`; minor `BarOverlayWidget` extension for pulse style

---

## 6. Combat State Indicator

**Current state:** `CombatTracker` records a last-hit timestamp; the out-of-combat threshold is
checked in the absorption regen ticker. Players have no way to know the server's combat timer status,
which matters for when absorption regen will begin.

**Proposed:** A subtle icon (sword = in combat, shield = out of combat) that reflects the server's
combat state. Low visual weight — not a prominent element.

**Server-side (EnchantForge)**

Send on `CombatTracker.recordHit()` (combat start) and when the regen ticker detects the player has
been out of combat long enough to begin regen (combat cleared):
```json
{ "type": "binding_update", "key": "enchantforge.in_combat", "value": true }
```

**Client-side (VibeCraftMod)**

A small `icon` overlay widget bound to `enchantforge.in_combat`, swapping between two icon states.
Position near the absorption bar (§5) so related information is grouped.

**Scope**
- EnchantForge: edit `CombatTracker.java` (add callback), `EquipmentEnchantListener.java`
  (fire clear event), `VibeCraftUiBridge.java`
- VibeCraftMod: update `ui/main.json`; `IconOverlayWidget` likely already handles boolean bindings

---

## 7. Per-Enchant Glint Colors

**Current state:** The enchantment shimmer on all enchanted items is the same vanilla purple,
hardcoded in the `rendertype_glint_direct` shader. There is no per-item color differentiation.

**Proposed:** When an enchant is applied to an item, store a glint color in the item's PDC
(`enchantforge:glint_color` as an ARGB integer). VibeCraftMod reads this from the item's
`minecraft:custom_data` component (which is synced to the client) and applies it during
glint rendering via an `ItemRenderer` mixin.

Color assignment is YAML-driven — each enchant definition gains an optional `glintColor` field:
```yaml
glintColor: "#00B8A0"   # teal — default for absorption enchants
glintColor: "#FF4444"   # red — for offensive enchants
glintColor: "#4488FF"   # blue — for defensive enchants
```
Enchants without `glintColor` fall back to vanilla purple.

**Server-side (EnchantForge)**

In `CustomEnchant.fromYaml()`, parse `glintColor` as an ARGB int and store on the model.
In `EnchantCommand.applyEnchant()`, after writing the PDC enchant key/level, also write:
```java
meta.getPersistentDataContainer().set(
    new NamespacedKey(plugin, "glint_color"),
    PersistentDataType.INTEGER,
    enchant.getGlintColor()  // 0 = use vanilla default
);
```

**Client-side (VibeCraftMod)**

Add a Fabric mixin on `ItemRenderer` targeting the method that submits glint render geometry.
Read `DataComponents.CUSTOM_DATA` from the `ItemStack`, extract `enchantforge:glint_color`,
and apply it as a color multiplier to the glint render layer:

```java
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Inject(method = "renderGlint", at = @At("HEAD"), cancellable = true)
    private void enchantForgeGlintColor(
            MatrixStack matrices, VertexConsumerProvider vcp,
            ItemStack stack, int light, CallbackInfo ci) {

        NbtCompound data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains("enchantforge:glint_color")) return;

        int argb = data.getInt("enchantforge:glint_color");
        if (argb == 0) return; // 0 = use vanilla

        // Render glint with custom ARGB color and cancel vanilla path
        renderGlintWithColor(matrices, vcp, stack, light, argb);
        ci.cancel();
    }
}
```

This requires no resource pack changes and works independently of the pack delivery system.
Only VibeCraftMod users see colored glints; other players see vanilla purple.

**Scope**
- EnchantForge: edit `CustomEnchant.java` (parse `glintColor`), `EnchantCommand.java` (write PDC);
  add `glintColor` field to any enchant YAMLs that should have custom colors
- VibeCraftMod: add `mixins/ItemRendererMixin.java`; register in `vibecraftmod.mixins.json`

---

## Implementation Order

**Phase 1 — Core HUD (§1–3)**
1. Add `sendBindingUpdate()` / `sendBindingUpdates()` helpers to `VibeCraftUiBridge.java`
2. Implement energy bar (§1): hook `EnergyManager` callback, remove BossBar from `SuitListener`
3. Implement cooldown strip (§2): hook `CooldownManager`, add 20-tick update scheduler
4. Implement active effects (§3): hook `ActiveEffectTracker`, fold into same scheduler
5. Define overlays in `ui/main.json` for §1–3

**Phase 2 — Feedback (§4–6)**
6. Implement trigger flash (§4): hook dispatch in `StackingDispatcher`
7. Implement absorption regen (§5): hook `EquipmentEnchantListener` regen ticker
8. Implement combat state (§6): hook `CombatTracker`
9. Add `flash` widget type to VibeCraftMod; update `ui/main.json` for §4–6

**Phase 3 — Rendering (§7)**
10. Parse `glintColor` in `CustomEnchant.fromYaml()`; write PDC in `EnchantCommand`
11. Write `ItemRendererMixin`; register in `vibecraftmod.mixins.json`
12. Add `glintColor` to enchant YAMLs

---

## Files Touched Summary

| File | Repo | Action | Phase |
|------|------|--------|-------|
| `VibeCraftUiBridge.java` | EnchantForge | Add `sendBindingUpdate` / `sendBindingUpdates` | 1 |
| `EnergyManager.java` | EnchantForge | Add binding-update callback | 1 |
| `SuitListener.java` | EnchantForge | Remove BossBar; send charge-tier updates | 1 |
| `CooldownManager.java` | EnchantForge | Add set/clear callback | 1 |
| `ActiveEffectTracker.java` | EnchantForge | Add change callback | 1 |
| `EnchantForge.java` | EnchantForge | Wire callbacks; add update scheduler | 1 |
| `ui/main.json` | VibeCraftMod | Add energy, cooldown, active-effect overlays | 1 |
| `StackingDispatcher.java` | EnchantForge | Fire flash event on dispatch | 2 |
| `EquipmentEnchantListener.java` | EnchantForge | Send absorption regen updates | 2 |
| `CombatTracker.java` | EnchantForge | Add hit/clear callbacks | 2 |
| `BarOverlayWidget.java` | VibeCraftMod | Pulse style for absorption regen | 2 |
| `StandardWidgets.java` | VibeCraftMod | Register `flash` widget type | 2 |
| `WidgetRenderer.java` | VibeCraftMod | Render `flash` widget | 2 |
| `ui/main.json` | VibeCraftMod | Add flash, absorption, combat overlays | 2 |
| `CustomEnchant.java` | EnchantForge | Parse `glintColor` from YAML | 3 |
| `EnchantCommand.java` | EnchantForge | Write `enchantforge:glint_color` PDC on apply | 3 |
| `enchants/*.yml` | EnchantForge | Add `glintColor` fields | 3 |
| `mixins/ItemRendererMixin.java` | VibeCraftMod | Create; read PDC color, apply to glint | 3 |
| `vibecraftmod.mixins.json` | VibeCraftMod | Register `ItemRendererMixin` | 3 |
