# Armor HUD and Alerts as HUD modules (2026-11-12)

Worktree branch of the equipment-HUD + alerts rework. Three changes, all one story: the Armor HUD and the alert warnings move from fixed-popcard rendering into the standard HUD module system, and the VANILLA HUD background becomes an option only where a vanilla resource actually backs the module.

## What changed

### Armor HUD shows worn armor, hands, and only occupied slots

`ArmorModule` no longer draws a fixed four-slot grid. Each tick it lists the equipped pieces (HEAD → FEET) and — behind two new switches, `armorHudShowMainHand` / `armorHudShowOffHand` (default off) — the main and off hands when they hold something. Empty slots are never drawn; the module collapses to nothing when nothing is worn or held, drawing no background at all.

With `armorHudBgMode = VANILLA` the piece icons sit on **one continuous Minecraft hotbar-strip graphic** (`hud/hotbar`, 182×22), the uku-style single strip: horizontal layout stretches one strip so every cell gets the vanilla 20-px pitch (items land exactly where vanilla draws hotbar items, icons at +3/+3, durability text beside each cell); vertical layout stacks complete 20×22 cells, reading as a vertical strip. Horizontal stretch is uniform and bounded by the durability text width, so long labels grow the strip rather than overlap it.

### VANILLA background restricted to Armor HUD + Potion HUD

Every HUD dropdown offered VANILLA, but only Armor HUD (hotbar strip) and Potion HUD (vanilla status-effect slots) have a vanilla texture behind them; every other module drew the same flat panel for VANILLA as for SOLID.

- `core/.../config/HudBackgroundPolicy.normalize(AuroraConfig)` — a reflective pass over every public `*BgMode` field: outside the `armorHudBgMode`/`potionBgMode` allowlist, VANILLA folds to SOLID. Idempotent, no one-shot marker; run at the end of `AuroraConfig.load()` and after every `ProfileManager.applyProfile`, so configs, profiles and hand-edited JSON all converge.
- The dropdowns for Sprint, Info HUD, CPS, Reach, Totem Pops and Keystrokes now offer only NONE / SOLID (`FLAT_BACKGROUNDS` in `FeatureRegistry`). A persisted VANILLA on those still loads and folds, so no config is rejected.

### Alerts render as two HUD modules instead of a popup card

The shared popup card (`AlertRenderer`'s timed card) no longer carries the two alert sources. Each is now a standard HUD module — draggable, resizable, hideable, profile-scoped — registered as `armor_alert` ("Armor Alert") and `potion_alert` ("Potion Alert") in the HUD editor; both map their feature-registry id to the existing `alerts` settings card, so X/shift+click there opens its settings.

- **Armor Alert** (`ArmorAlertHudModule`): one row per latched low-durability item — icon, "Helmet · 8% ×2" — fed per tick by `ArmorAlertFeature`. Rows stay up while the item remains in your possession (the scanner keeps seeing the fingerprint, wherever the item sits) and disappear when it leaves; the panel fades out after 300 ms, like the potion module. `AlertHudState` (new) carries the immutable per-tick row snapshots between the scanner and the modules.
- **Effect Alert** (`EffectExpiryHudModule`): one row per latched effect — vanilla status sprite, localized name, live seconds left — fed by `StatusAlertFeature`, same fade contract.
- Each module has its own Background (NONE/SOLID only, `armorAlertBgMode` / `effectExpiryBgMode`, default NONE), Background Color, Text Color and Theme Corner Radius, plus the module layout fields. All new fields are profile-scoped, ship in the release defaults with both alert modules off, and reset under the existing `armorAlert`/`effectExpiry` prefixes.

### Durability alerts latch on the item, not the slot

`ArmorAlertFeature`'s scan was a boolean per `EquipmentSlot`: unequip → re-equip re-fired, two identical items shared one flag, an item moved to a chest kept alerting. The alert now keys on an item **fingerprint** — (registry id, max damage, damage) — in a pure core class, `alerts/DurabilityLatch`:

- **Moving never re-fires.** The same item state is the same key wherever it sits; the inventory scan only *refreshes* (keeps alive), so an unequipped item cannot arm a new alert, and an already-latched one stays on screen while it is in your possession. Its warning row follows the item to the hand slots or inventory without a second fire.
- **Repair or replace re-arms naturally.** A repaired/replacement item is a different fingerprint; when it again drops below the threshold it is an unknown key and latches fresh. No un-latch bookkeeping.
- **Bounding.** Entries unseen for 200 ticks prune (dropped, stored), the map caps at 64 with least-recently-seen eviction, and world join/respawn resets the latch (`ClientPlayNetworkHandlerWorldJoinMixin`), alongside `AlertHudState.clearAll()` and the effect-alert inclusion latch reset.
- `AlertManager.fire(...)` is gone: the popup card's fire path had no remaining callers. The informational `notify` path (hotkey hints) and the shared `playAlertSound()` stay.

## Config fields added

`armorHudShowMainHand`, `armorHudShowOffHand`, `armorAlertHudEnabled`, `armorAlertBgMode`, `armorAlertBgColor`, `armorAlertTextColor`, `armorAlertThemeCorners`, `effectExpiryHudEnabled`, `effectExpiryBgMode`, `effectExpiryBgColor`, `effectExpiryTextColor`, `effectExpiryThemeCorners`. All in `AuroraConfig`, mirrored in `defaults/3.2.0.json` (both alert modules ship off; fresh installs keep alerts silent unless enabled). No behaviour-changing existing field needed a migrator beyond the `HudBackgroundPolicy` normalization pass.

## Verification

- `DurabilityLatchTest` (9 cases) and `HudBackgroundPolicyTest` (5 cases) in the core test set; pure, no Minecraft types.
- `FeatureCatalogTest` parity holds: the new modules register under the existing `alerts` card id, so no catalog entry was added.
- Compile/test matrix run on 26.3 first, then `tools/parity.py` across all lines (evidence to be attached before release).
- No dev-client boot was made in this session: rendering behavior of the hotbar strip (horizontal stretch and vertical cell stack, both layouts, all three background modes) needs a boot check on 26.3 OpenGL and one native backend before release, per the glass/rendering rules. The blitSprite overload used is the same one the pre-existing `HudBackgrounds` helpers call on all three lines, so the call shape is proven even where the new sprite path is not.

## Known notes

- The old shared popup code (`AlertRenderer`) remains for informational popups only; its WARNING/CRITICAL priority constants are now unused but kept.
- FAIR_PLAY notes for `alerts` and `armor_hud` were reworded in `FeatureCatalog` and regenerated alongside.
