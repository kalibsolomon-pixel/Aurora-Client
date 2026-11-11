# BetterHitreg Integration Catalog

Investigation-only catalog of the vendored `you.jass.betterhitreg` tree, produced 2026-09-08
for the execution session that will fold it into Aurora (one entrypoint, one mixin config,
one config file, Aurora feature card + detail screen replacing its UI; attribution kept).
No code was changed in producing this document.

All paths relative to `src/client/` unless noted. Line numbers are current `master`.

---

## 1. The entrypoint — `you/jass/betterhitreg/BetterHitreg.java`

`public class BetterHitreg implements ClientModInitializer` (line 35). Everything it does,
in `onInitializeClient()` order — each item is a discrete fold-in unit:

| # | What | Where | Notes for the fold-in |
|---|---|---|---|
| 1 | `Hitreg.client = Minecraft.getInstance()` | :49 | **Ordering constraint**: every class in the package reads this static. Must be set before anything touches `Hitreg`/`Settings`/`Render`. In a merged `AuroraClient.onInitializeClient`, do this first or make the field lazy. |
| 2 | `Commands.initialize()` | :50 → `settings/Commands.java:17-51` | Registers `ClientCommandRegistrationCallback.EVENT`, builds the `/hitreg` client command tree (see §4b). |
| 3 | `Render.updateColors()` | :51 | First touch of `Settings` → its **static initializer** loads `<configDir>/hitreg.properties` (seed-all-defaults, save-if-missing), then calls `Render.updateColors()` again from inside the static block. Idempotent; runs once. |
| 4 | `ClientTickEvents.START_CLIENT_TICK` → `Hitreg.tick()` | :53 → `hitreg/Hitreg.java:83-185` | The 50 ms "brain": metronome click, muffle/sharpen clamp, `UIUtils.update()` (rainbow-gradient text animator — only consumed by the old UI, droppable), fight-state machine, sprint-reset edge detection, swing/hit counters, jump-reset detection, fight-end stats + chat alerts, knockback-delay alert, target-position tracking, first-run tutorial chat messages. |
| 5 | `WorldRenderEvents.END_MAIN` → `Render.render(camera)` | :63-65 → `utility/Render.java:73-135` | World-space overlays via the 1.21.11 `Gizmos` API: target hitbox, target cross, server hitbox, your/their reach rings (r=3) and jump-range rings (r=4), solid floor + floor grid (§4 settings). Aurora uses `AFTER_ENTITIES` ×2 and `BEFORE_BLOCK_OUTLINE` on the same event bus — different events, no interference. |
| 6 | `HudRenderCallback` → score HUD | :85-89 | Draws `"Score: L-R"` at (10,10) whenever in-world and either score ≠ 0. Part of the practice-scoreboard utility (keybinds below), not the hitreg core. |
| 7 | `KeyMapping.Category.register(betterhitreg:hitreg)` | :140 | Own Controls-screen category; display name from `assets/betterhitreg/lang/en_us.json` (`key.category.betterhitreg.hitreg` = "Hitreg"). Aurora registers `aurora:client` the same way (`util/AuroraKeybinds.java:54`). |
| 8 | Six keybinds via `MultiVersion.registerKey` → `KeyBindingHelper.registerKeyBinding` | :143-176 | Names are **literal strings, not translation keys**. Defaults: `uiKey` "Open Hitreg Menu" = **H (bound)**; `handKey` "Switch Hand" = unbound; `leftKey` "Increase Left Score" = **LEFT arrow (bound)**; `rightKey` "Increase Right Score" = **RIGHT (bound)**; `upKey` "Send Score to Chat" = **UP (bound)**; `downKey` "Reset Score" = **DOWN (bound)**. |
| 9 | `ClientTickEvents.END_CLIENT_TICK` — keybind dispatch | :178-202 | `uiKey` → open `UIScreen` (if no screen open). `handKey` → swap main hand (5-tick cooldown + `broadcastOptions`, no-screen guard). Score keys → increment / send `"L-R"` via `sendChat` / reset, throttled to every 5 ticks (`scoreCooldown`), no-screen guard. Cooldown decrements. |

Also on the class: public statics `uiKey/handKey/leftKey/rightKey/upKey/downKey`,
`handSwitchCooldown`, `scoreCooldown`, `leftScore`, `rightScore` (:36-45).

**Fold-in notes.** Aurora's keybind system (`AuroraKeybinds.register(translationKey, default,
getter, setter)`) persists bindings in `aurora.json` with two-way vanilla sync — hitreg's
keybinds are raw Fabric registrations persisted only in vanilla `options.txt`. Beware the
documented gotcha (AGENTS.md §3): an `aurora.json`-only binding of an unbound vanilla key
gets written back to −1 at boot. The four arrow-key **defaults** are aggressive (they grab
arrow keys in every context, including menus with the no-screen guard only stopping screen
contexts) — the execution session should decide defaults deliberately; `uiKey` presumably
retires in favor of Aurora's own settings flow.

The version/credit strings displayed by the old UI live at `ui/UIScreen.java:80`
(`"BetterHitreg v1.0.6"`) and `:87` (`"Made by Jass • Modrinth.com/mod/betterhitreg • 1.21.11"`)
— source material for the new detail screen's "Original project by Jass." subtitle.

---

## 2. The 14 mixins — `betterhitreg.mixins.json`

The registered list is exactly 14 entries (includes the accessor): ArmorMixin, AttackMixin,
ChunkMixin, DamageMixin, **EntityAccessor**, GlowMixin, NetworkMixin, ParticleMixin,
PlayerMixin, RenderMixin, ServerMixin, SoundMixin, SourceMixin, WorldMixin. Config:
`required=true`, `defaultRequire=1`, package `you.jass.betterhitreg.mixin`, **no `client`
key** (flat `mixins` array — all targets are client classes anyway; on merge they move into
`aurora.mixins.json`'s `client` array and are covered by its refmap).

**Overlap verdict: zero same-class-same-method collisions with `aurora.mixins.json`.** Three
classes are shared between the two configs with *different* methods (safe — Mixin merges
both into one synthetic subclass), listed per-mixin below. Details:

### Per-mixin table

| Mixin | Target | Injection | One-line summary | Aurora mixin on same class? |
|---|---|---|---|---|
| `AttackMixin` | `MultiPlayerGameMode` | `@Inject("attack", HEAD)` static | Captures ~20 pre-hit state booleans at the exact attack moment, classifies the hit (early/knockback/crit/sweep/pick), updates the global fight state, and calls `Hit.load()` which schedules the custom feedback (`AttackMixin.java:24-78`) | **None** (Aurora's `ClientPlayerAttackMixin` targets `Minecraft.startAttack` — different class, earlier in the chain) |
| `ServerMixin` | `ClientPacketListener` | 3× `@Inject(HEAD, cancellable)`: `handleDamageEvent(ClientboundDamageEventPacket)`, `handleAnimate(ClientboundAnimatePacket)`, `handleSoundEvent(ClientboundSoundPacket)` | Gates server-confirmed damage/animation/sound packets through `PacketProcessor` (returns false → cancel = suppress server feedback). `handleDamageEvent` deliberately runs on BOTH network and main thread (comment :22 — bundled-packet servers skip the network pass) | **Same class, different methods**: Aurora has `ClientPacketListenerEntityEventMixin` (`handleEntityEvent`) and `ClientPlayNetworkHandlerWorldJoinMixin` (`handleLogin`/`handleRespawn`). No collision. (A third same-class mixin, `TickSyncNetworkMixin` (`handleMoveEntity`/`handleTickingState`), existed until Tick Sync was removed 2026-09-13.) |
| `NetworkMixin` | `ClientPacketListener` | `@ModifyArg` on the `Entity.handleDamageEvent` invocation *inside* `handleDamageEvent(ClientboundDamageEventPacket)` | Wraps the damage source in a `DontAnimate` marker to suppress the server's hurt animation for hits the mod handles (or all, if HIDE_ANIMATIONS) | Same as above; coexists today with `ServerMixin`'s HEAD inject on the *same method* inside hitreg's own config — already-proven combination |
| `DamageMixin` | `LivingEntity` | `@ModifyVariable` on `handleDamageEvent` (at `INVOKE_ASSIGN getHurtSound`, ordinal 0) + `@Inject(handleDamageEvent, HEAD, cancellable)` | Variable-mix nulls your own hurt sound (SILENCE_THEM); HEAD inject recognizes the `DontAnimate`/`OnlyAnimate` marker sources, applies hurt state without animation / animation without damage, cancels vanilla | **None** on `LivingEntity` (Aurora's `LivingEntityRendererExtractMixin` is `LivingEntityRenderer`; `DamageTiltMixin` is `GameRenderer.bobHurt`) |
| `EntityAccessor` | `LivingEntity` | `@Invoker getHurtSound(DamageSource)` | Lets `HitType.getHurtSound()` play the target's correct hurt sound | None |
| `ArmorMixin` | `HumanoidArmorLayer` | `@Inject("submit(...)", HEAD, cancellable)` | Cancels armor rendering entirely when HIDE_ARMOR | None (Aurora's `EquipmentLayerRendererMixin`/`ShieldSpecialRendererMixin` are different classes) |
| `ChunkMixin` | `LevelRenderer` | `@Shadow visibleSections` + `@Inject("prepareChunkRenders", HEAD)` | Clears the visible-section list every frame when VOID_WORLD ("unrender world") | **None** on `LevelRenderer` |
| `WorldMixin` | `LevelRenderer` | **none active** — the one `@Inject` body is fully commented out | Dead weight: a parking spot for the 1.21.9 path where `WorldRenderEvents` didn't exist (entrypoint :55 comment). Registered but injects nothing. **Safe to drop in the merge.** | None |
| `GlowMixin` | `Entity` | `@Inject("getTeamColor", RETURN, cancellable)` + `@Inject("isCurrentlyGlowing", RETURN, cancellable)` | Makes the current fight target glow (perfect-hit green / jump-reset yellow) for 500 ms after those events, when no custom hitbox renderer is on | **Same class, different method**: Aurora's `EntityMixin` injects `turn(DD)V` (FreeLook). No collision |
| `ParticleMixin` | `ParticleEngine` | `@Inject("makeParticle(...)", HEAD, cancellable, returns null)` | Filters particle spawn: hides all / non-crit-sweep / far-away particles per toggles (FIREWORK exempted) | **Same class, different method** — see the composition note below |
| `PlayerMixin` | `LocalPlayer` | `@Inject("crit", HEAD, cancellable)` + `@Inject("magicCrit", HEAD, cancellable)` | Cancels the client's own crit-particle calls when the mod handles the hit | None |
| `RenderMixin` | `EntityRenderer` | `@Inject("shouldRender", HEAD, cancellable)` | Hides other players (and fight text displays) during a fight when HIDE_OTHER_FIGHTS and they're >5 blocks from either fighter | None (Aurora's `EntityRenderDispatcherMixin` actually targets `EntityHitboxDebugRenderer`) |
| `SoundMixin` | `SoundEngine` | 2× `@Inject` on `play(SoundInstance)`: HEAD-cancellable (silence non-hit sounds) + after the first channel-execute (arm the EFX filter counter) | Mutes non-hit player sounds; counts hit sounds so `SourceMixin` can attach an OpenAL low/high-pass filter (muffle/sharpen) | None |
| `SourceMixin` | `com.mojang.blaze3d.audio.Channel` | `@Inject("play", TAIL)` | Attaches the EFX lowpass/highpass filter to the OpenAL source for the Nth armed hit sound (muffle/sharpen) | None |

### The one semantic near-miss worth knowing: `ParticleEngine`

Aurora's `ParticleEngineMixin` gates `createParticle` (HEAD-cancel + RETURN-modify;
`com/aurora/client/mixin/ParticleEngineMixin.java:40,52`), whose vanilla body calls
`makeParticle(...)` (stated in its own javadoc :17-19) — the exact method hitreg's
`ParticleMixin` gates. Both are HEAD-cancellable visibility filters on the same spawn
pipeline at different depths. This is **composition, not conflict**: a particle suppressed
by either feature is suppressed; when Aurora's cancel fires, hitreg's inject never runs for
that spawn (it sits one call deeper). No injection-order or framework hazard; just don't be
surprised that the Particles feature and hitreg's hide-particle toggles both apply.

---

## 3. Config — `config/hitreg.properties`

### Storage mechanics (`settings/Settings.java`)

- Raw `java.util.Properties`, one flat text file at `FabricLoader.getConfigDir()/hitreg.properties`
  (:12). Loaded in `Settings`' **static initializer** (:17-38): seeds every default from the
  three enums, loads the file over it, back-fills any missing default keys, saves if the file
  didn't exist. Unknown keys are kept and re-saved.
- **Every `set()` writes the whole file synchronously on the calling thread** (:51-54, :84-112) —
  i.e. the main client thread. Contrast: Aurora saves `aurora.json` async on a daemon executor
  precisely because blocking saves caused real disconnects (AGENTS.md §3). The migration must
  route writes through AuroraConfig's async save, not keep this.
- Custom save format: `#Hitreg Settings` header, groups `#Configure / #Render / #UI / #Tracked /
  #Toggle`, alphabetical within groups. Cosmetic only — parsing is plain `Properties.load`.
- Getters "self-heal": on missing/unparseable values they write the default back into the
  properties and save (e.g. `getInt` :149-160). Types are strings in the file; parsing per call.
- Defaults come from exactly three enums: `Setting` (8 keys), `Toggle` (29 keys), `Color`
  (23 entries × 2 keys = 46). Total defined keys: **83**. The live dev file
  (`run/config/hitreg.properties`) has 85 `=` lines — everything at defaults except
  `toggle=true`, `tutorial=false`, `fight_playtime_(seconds)=579`, `total_fights=24`
  (plus group headers/blank lines accounting for the count).

### Full field list

**Configure (`Setting` enum, `settings/Setting.java`):**

| Key | Type in file | Default | Controls |
|---|---|---|---|
| `hitreg` | int (ms) | `0` | Delay before the mod plays its own hit feedback (`Scheduler.schedule(Settings.getHitreg(), Hit::run)`; 0 = next main-thread frame). `getHitreg()` :40-42. Slider range in UI: 0–300 |
| `muffle_amount` | float 0–1 stored as decimal string (`0.5`) | `0` | OpenAL lowpass strength on your hit sounds |
| `sharpen_amount` | float 0–1, same encoding | `0` | OpenAL highpass strength on your hit sounds |
| `metronome` | int ticks | `0` | Click sound every N ticks; **<10 = disabled** (UI slider 9–25, <10 writes `"0"`) |
| `floor_grid_size` | int blocks | `0` | Ground grid spacing (`0` = off); render radius 16, smoothstep fade (`Render.renderFloor`) |
| `tutorial` | boolean | `true` | First-run chat tips; consumed (set false) by opening UI or running `/hitreg` |
| `total_fights` | int | `0` | Lifetime tracked fights (10 s–10 min with ≥1 hit) |
| `fight_playtime_(seconds)` | long | `0` | Lifetime tracked fight seconds — **note the parentheses in the key** |

**Toggles (`Toggle` enum, 29 keys — `settings/Toggle.java`). Defaults: only `toggle` and
`safeRegsOnly` are `true`; everything else `false`. Labels shown are the UI/command display names:**

| Key (exact) | Label | What it gates |
|---|---|---|
| `toggle` | custom hitreg | Master switch (`Hitreg.isToggled()` first check) |
| `safeRegsOnly` | safe regs only | Skip custom hitreg on first-hit/new-target/ghosted/hit-by-another/far-from-previous hits |
| `ignoreShieldHolders` | ignore shield holders | Skip custom hitreg vs shield holders |
| `alertDelays` | alert delays | Chat message with measured server registration delay |
| `alertGhosts` | alert ghosts | Chat message when a hit didn't register |
| `alertInconsistencies` | alert inconsistencies | Chat message when server misclassified your hit (sound vs expectation) |
| `alertFights` | alert fights | Post-fight chat summary (duration, both accuracies) |
| `legacySounds` | 1.8 sounds | Replace modern attack sounds with `PLAYER_HURT` only |
| `hideAnimations` | hide animations | Suppress ALL hurt animations (your hits too — `NetworkMixin` wraps everything) |
| `hideArmor` | hide armor | `ArmorMixin` cancels armor layer |
| `hideAllParticles` | hide crit particles | Suppress all hit particles (`Hit.updateSettings` + `ParticleMixin`) |
| `hideOtherParticles` | only crit/sweep particles | Suppress non-crit/sweep hit particles (incl. sharpness sparkles unless `particlesEveryHit`) |
| `particlesEveryHit` | particles on every hit | Force sharpness-style particles on every hit |
| `silenceOtherFights` | silence other fights | Mute/hide sounds (and animations) from other players' fights |
| `silenceSelf` | silence your hits | No sounds for hits the mod handles |
| `silenceThem` | silence their hits | No hurt sounds from your perspective of their hits (incl. your own hurt sound via `DamageMixin`) |
| `silenceNonHits` | silence non-hits | `SoundMixin` mutes non-attack player-source sounds |
| `hideOtherFights` | hide other fights | `RenderMixin` hides distant players mid-fight |
| `renderHitbox` | render target hitbox | Interpolated client-side hitbox box |
| `renderCross` | render target cross | Screen-space cross at the closest point on the target's box |
| `RenderServerHitbox` | render server hitbox | Box at the *un-interpolated* server position |
| `RenderYourReach` | render your reach | r=3 ring at your ground position (green/red by in-range) |
| `RenderTheirReach` | render their reach | Same for the target |
| `RenderYourJump` | render your jump range | r=4 ring |
| `RenderTheirJump` | render their jump range | r=4 ring |
| `PerfectHitColor` | color first tick hits | Glow/hitbox flash green for 500 ms after a perfect (first-tick-in-range) hit |
| `JumpResetColor` | color jump resets | Same for jump-reset detections |
| `VoidWorld` | unrender world | `ChunkMixin` clears visible sections — **UI- and command-hidden except `/hitreg VoidWorld`; extremely invasive, no widget in the old screen** |
| `SolidFloor` | render solid floor | 512×512 black fill quad at ground level (`Render.renderFloor`) |

⚠️ **Key-casing inconsistency (migration must match exact strings):** original toggles are
camelCase (`safeRegsOnly`), later additions are PascalCase (`RenderServerHitbox`,
`RenderYourReach`, `RenderTheirReach`, `RenderYourJump`, `RenderTheirJump`, `PerfectHitColor`,
`JumpResetColor`, `VoidWorld`, `SolidFloor`). Match on the property key, never the enum name.

**Colors (`Color` enum × 2 keys each = 46 keys, `settings/Color.java`):** every entry writes
`<name>_color` (bare hex, no `#`, e.g. `FFFFFF`) and `<name>_opacity` (int 0–255).

- *Render colors (18, category "render")* — consumed by `Render.updateColors()` (:42-61) and
  the far/near variants switch by in/out of range: `cross_far/near`,
  `cross_far/near_with_hitbox` (⚠️ read via keys `cross_far_color_with_hitbox` /
  `cross_with_hitbox_far_opacity` — the *opacity* key reorders the words vs the color key;
  see `Render.java:47-48`), `hitbox_far/near`, `server_hitbox` (default alpha 125),
  `your_reach_far/near`, `their_reach_far/near`, `your_jump_far/near`, `their_jump_far/near`
  (007FFF), `jump_reset` (FFFF00), `perfect_hit` (00FF00), `grid` (FFFFFF), `floor` (000000).
- *UI colors (5, category "ui")* — `background` (alpha 230), `border`, `text`, `hovered`,
  `highlighted`: **only the old `UIScreen`'s look. Obsolete the moment the UI is replaced.**

### Migration feasibility & precedent

- A `ThemeMigrator`-style one-time Properties→AuroraConfig migration is straightforward: the
  file is flat, key-complete (missing keys already back-filled by the loader), and
  unambiguous in type (booleans, ints, floats-as-strings, hex colors). Follow the
  `ThemeMigrator` shape: run at `AuroraConfig` load, one-way, never throws, guarded by a
  "migrated" flag so it fires once; then optionally leave or delete `hitreg.properties`.
- Semantics to preserve exactly: `hitreg` 0 ≠ "off" (master switch is the `toggle` boolean);
  `metronome` <10 means off; muffle/sharpen are 0–1 floats (commands take 0–100 ints and
  divide by 100 — `Commands.setMuffle` :99); opacities are 0–255; colors bare hex.
- `ProfileManager` snapshots every public field except `activeProfile` + playtime telemetry.
  Decide whether `total_fights` / `fight_playtime_(seconds)` are excluded like playtime
  telemetry (recommended: they're per-machine lifetime stats) — otherwise switching profiles
  silently swaps fight history.
- Aurora's per-feature Reset uses `AuroraConfig.resetByPrefix(...)` — name the new fields with
  a shared prefix (e.g. `hitreg…`) so one reset card covers them.
- Pre-existing upstream bug, do **not** carry over: `Commands.setHitreg` writes
  `Settings.set("toggled", "false")` (`Commands.java:80`) — key `toggled` doesn't exist (the
  real key is `toggle`), so passing a negative value silently no-ops the intended off-switch.

---

## 4. The existing custom UI — exactly two surfaces

### a) The menu screen (`ui/` package, 894 lines total) — "custom interface"

`ui/UIScreen.java` (533 lines), opened by the **H** keybind. A vanilla `Screen` subclass that
hand-renders a fixed 350×260 centered panel via its own micro-toolkit (`UIElement` interface,
`UIPanel`, `UILabel`, `UICheckbox`, `UISlider`, `UITheme` record, `UIUtils` draw helpers +
rainbow-shift animator). No scrolling; everything is hardcoded coordinates. All of `ui/` is
throwaway once the Aurora card exists — except the attribution strings (§1) and two live-stat
labels below.

`init()` side effects beyond layout (`UIScreen.java:42-44`): consumes the `tutorial` flag,
re-runs `Settings.load()` + `Render.updateColors()` (defensive re-read).

**Complete widget inventory (what must find homes in the FeatureDetailScreen):**

- Header/footer labels: version + author credit (attribution source).
- `Hitreg` slider 0–300 ms (`hitreg`) + `Enable Hitreg` checkbox (`toggle`).
- *Utility*: `Safe Regs Only`, `Ignore Shield Holders`, `Alert Delays (Nms)`,
  `Alert Ghosts (N%)`, `Alert Misplaces (N%)`, `Alert Fight Statistics` — **the three alert
  checkboxes embed live rolling stats from `Hitreg.last100Regs`** (`getAverageDelay()`,
  `getGhostRatio()`, `getInconsistencyRatio()` — `RegQueue`, last 100 regs). A plain
  FeatureSetting can't render live stats; either a static info row or dropping them is a
  decision for the execution session.
- *Audio*: `Mute Other Fights`, `1.8 Hit Sounds`, `Mute Non-hit Sounds`, `Mute Your Hits`,
  `Mute Their Hits`, `Hit Muffling` 0–100%, `Hit Sharpening` 0–100%, `Metronome` 9–25t (<10=off).
- *Render*: `Hide Other Fights`, `Hide Animations`, `Hide Armor`, `Hide All Particles`,
  `Hide Other Particles`, `Always Hit Particles`, `Show Target Hitbox`, `Show Target Cross`,
  `Show Server Hitbox`, `Show Your Hit Range`, `Show Their Hit Range`, `Show Your Jump Range`,
  `Show Their Jump Range`, `Perfect Hit Color`, `Jump Reset Color`.
- **Not in the screen at all** (command/properties-only): `VoidWorld`, `SolidFloor`,
  `floor_grid_size`, all 46 color values, `metronome`'s file default, tracked stats.

### b) The chat interface (`settings/Commands.java` + `MultiVersion.message`)

- `/hitreg` client command tree (`ClientCommandRegistrationCallback`): one subcommand per
  `Toggle` (by property key — so `/hitreg RenderServerHitbox`, `/hitreg VoidWorld`, …), plus
  `setHitreg <int>`, `setMuffle <int 0-100>`, `setSharpen <int 0-100>`, `setGridSize <int>`;
  bare `/hitreg` prints the guide (all values + UI key hint) and consumes the tutorial flag.
- **All feedback is clickable chat** via `MultiVersion.message(msg, command)`
  (`utility/MultiVersion.java:242-264`): gold `Hitreg |` prefix, click-to-run (toggles) or
  click-to-suggest (set commands), hover text. This formatter is also used by the *runtime*
  alert system — `alertDelays/alertGhosts/alertInconsistencies/alertFights` messages, the
  knockback-delay alert, fight summaries, and tutorial tips (`Hitreg.tick`,
  `PacketProcessor.processDamage`, `HitTracker.process`) — dozens of call sites. **These
  messages embed `/hitreg …` command strings as their click actions.** If the command
  disappears, every alert's click action breaks; if `message()` disappears, so does all
  runtime feedback. Options for the execution session: keep `/hitreg` as a thin alias,
  re-route alerts (Aurora has `hud/AlertManager`), or keep chat messages minus click actions.
- `Toggle.toggle()` (`Toggle.java:62-75`) couples state-change to **chat output** (value line
  + per-toggle explanatory lines + the "colors can be edited via configs/Hitreg.properties"
  hint for render toggles). Every UI checkbox routes through it, so the old screen announces
  changes in chat too. The new detail screen's setters should decouple state from chat.

---

## 5. Everything else tied to the package

| Item | Location | Disposition |
|---|---|---|
| Second entrypoint registration | `src/client/resources/fabric.mod.json:20` (`entrypoints.client[1]`) | Remove; fold into `AuroraClient` |
| Second mixin config | `fabric.mod.json:25` + `src/client/resources/betterhitreg.mixins.json` | Remove both; move 13 entries (all except dead `WorldMixin`) into `aurora.mixins.json`'s `client` array |
| Lang file | `assets/betterhitreg/lang/en_us.json` — exactly one key: `key.category.betterhitreg.hitreg` = "Hitreg" | Keep only if the betterhitreg keybind category survives; else delete with it |
| Mod icon | `assets/betterhitreg/icon.png` | **Unreferenced** — `fabric.mod.json` points at `assets/aurora/textures/gui/logo.png`. Dead; delete |
| Keybind category | `KeyMapping.Category` `betterhitreg:hitreg` (BetterHitreg.java:140) | Merge keybinds into `aurora:client` or keep; drives the lang-key decision |
| Live dev config | `run/config/hitreg.properties` (real values incl. tracked stats) | The migration's input on this machine |
| Docs | `AGENTS.md` §1 (vendored-mod paragraph, build-facts entrypoint/mixin rows), §7, §9 hygiene | Update in the same change (repo rule: AGENTS.md tracks this) |
| Cross-references | None from `com/aurora/**`, `build.gradle`, or `gradle.properties` (verified by grep) | The fold is purely additive on Aurora's side |

Also note two helper classes that live outside `ui/` but belong to the old UI's world:
`utility/MultiVersion`'s drawing half (`drawRectangle`…`drawGradientText`, ~lines 290-494) and
`ui/UIUtils` exist only for the old screen; `MultiVersion`'s *other* half (version gates,
positions, `message()`, `hasSharpness()`, `playParticles`, `getAction`) is live logic. The
MultiVersion file is a deliberate porting shim full of commented version blocks — trimming it
is cosmetic cleanup, explicitly **out of scope** next to the timing logic (§6).

`utility/Animation` (7 lines) is a timestamp record, `DontAnimate`/`OnlyAnimate` are
`DamageSource` marker subclasses — all load-bearing (§6).

---

## 6. Gameplay-critical / timing-sensitive code — preserve byte-for-byte

The mod's product *is* its timing behavior. Anything on this list must be moved verbatim,
not refactored:

- **`mixin/AttackMixin`** — the HEAD capture of ~20 pre-hit state flags and the 475 ms
  early-hit threshold (`AttackMixin.java:29-31`); the comment documents why 475 and not 500.
  Order relative to vanilla `attack` body matters.
- **`utility/PacketProcessor`** — the whole class. Dual-thread design: `processDamage` runs
  on the network thread *and* the main thread by design (bundled-packet servers skip the
  network pass — `ServerMixin.java:22` comment); timestamps are taken on whichever runs first
  with a **−2 ms compensation** when only the main thread ran (`PacketProcessor.java:39,55`);
  `Sound`'s constructor subtracts 2 ms for the same reason (`Sound.java:31-32`); the
  delayed-knockback queue exists because "knockback sounds come before hit registration on
  most servers" (`:137-144`).
- **`utility/Sound`** — attribution windows `wasFromYou`/`wasFromThem` use a **15 ms** jitter
  bound when `silenceOtherFights` is on, **50 ms** otherwise, with the tuning comment at
  `Sound.java:70-71`. These constants are empirical.
- **`utility/Scheduler`** — delay-0 → `client.execute` (next frame), delayed → dedicated
  single-thread executor → hop to main thread. This IS the "register the hit early" mechanism
  (`hitreg/Hit.java:103`).
- **`hitreg/Hit.load()/run()`** — "decide once at hit time whether the mod replaces the
  server's feedback" (`Hit.java:94`): the handled-ness decision is frozen at swing time
  because the target's block state can change before packets arrive. The `DontAnimate` /
  `OnlyAnimate` `DamageSource` marker-wrapper trick (flow: `NetworkMixin` ModifyArg wraps →
  vanilla passes it down → `DamageMixin` HEAD recognizes and cancels/short-circuits) is
  non-obvious and load-bearing in three files.
- **`utility/HitTracker.process()`** — the 500 ms hit↔animation↔sound correlation window,
  ghost/misclassification detection feeding `RegQueue` (last-100 stats) and the
  `wasGhosted`/`lastNonGhost` state that `safeRegsOnly` depends on.
- **`hitreg/Hitreg.tick()` + `updateFightState` + `targetTakingKnockback`** — sprint-reset
  edge detection (only *new* forward input counts, comment :103-105), `invulnerableTime == 20`
  hit confirmation, jump-reset ±1-tick window, 30-block fight radius, 10 s–10 min fight
  tracking bounds, the 120°/1e-3 knockback-direction discriminator.
- **`hitreg/HitType`** — exact vanilla sound sequences per hit type, the
  blocked-hit→knockback-sound suppression (`PacketProcessor.java:127`), the 1.21.2+ sweep
  predicate via `MultiVersion.isMovingFast()` (2.5× movement speed — vanilla parity).
- **`mixin/ServerMixin`'s three cancellable HEAD injects and `SoundMixin`'s two-stage play
  interception** — cancellation *is* the feature (suppress server feedback); any behavioral
  drift here changes what the server-side sees or what the player hears mid-fight.
- **`utility/RegQueue`** — pure ring-buffer stats, trivially safe, but its outputs feed the
  safe-regs decision chain — keep semantics.
- `mixin/ChunkMixin`'s `@Shadow private ObjectArrayList<SectionRenderDispatcher.RenderSection>
  visibleSections` — a private-field shadow by exact name; fragile against mapping drift,
  leave as-is.

---

## Complexity / risk read (summary for the reviewer)

- **No real mixin conflicts.** All 14 registered entries verified against all 57 Aurora
  entries: zero shared class+method pairs. Three classes shared with different methods
  (`ClientPacketListener`, `Entity`, `ParticleEngine`) — safe merges. One deliberate
  composition note: both mods gate the particle-spawn pipeline at different depths
  (Aurora `createParticle`, hitreg `makeParticle`) — filters OR together, no hazard.
  `WorldMixin` is registered but injects nothing (dead — drop it).
- **Config migration is easy and worth doing** (ThemeMigrator precedent; flat 83-key file;
  watch the exact key casing, `metronome`<10=off, `hitreg`=0≠off, floats-as-strings,
  hex-without-#). Route saves through Aurora's async executor; the vendored code writes the
  file synchronously on the main thread today.
- **Biggest complication is UI coupling, not plumbing:** every toggle flips state through
  `Toggle.toggle()` which prints chat; every runtime alert is a clickable chat message whose
  click action targets `/hitreg …`; two checkbox labels embed live rolling stats; five
  settings (`VoidWorld`, `SolidFloor`, `floor_grid_size`, all colors) have no old-UI widget
  at all, so "nothing gets dropped silently" needs the full §4 inventory, not just the screen.
  The five old UI colors are obsolete with the screen.
- **Keybind defaults need a decision:** the vendored mod binds H + all four arrow keys by
  default and persists only in `options.txt`; folding into `AuroraKeybinds` changes
  persistence and should probably default the scoreboard keys to unbound.
- **Keep the timing core frozen** (§6 list) — this is plumbing-only integration around it.
