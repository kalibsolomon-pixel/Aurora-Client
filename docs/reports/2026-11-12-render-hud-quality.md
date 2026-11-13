# Rendering, HUD and loading follow-up

Work in progress on `feat/render-hud-quality`, based on `7b01a13` and the
existing ADR 0009 optimization series. This is test work, not a release.

## Rendering reproduction

The workstation has a 2560 x 1440 landscape primary display and a 1440 x 2560
portrait secondary display, both at 125% desktop scaling. A disposable 26.3
client uses the native parity probe and desktop window capture; Minecraft's
F2 capture is avoided because it deliberately suppresses glass temporarily.

`tools/render_stability.py` compares a fixed region of successive native
window captures. Its exact-pixel verdict is suitable for a stationary
synthetic backdrop. Moving-world captures need a separate interpretation:
moving scenery and particles are valid pixel changes, not proof of flicker.

- OpenGL, static striped backdrop, device blur radius 25: all 16 captures
  identical in the sampled panel body on each display.
- OpenGL, the same radius with 32 extra glass panels: all 16 body captures
  identical on the portrait display.
- Native Vulkan, static stripes, radius 25: all 16 body captures identical
  on the portrait display. Captures contain a rendered, blurred panel.
- Native Vulkan, Aurora grid at GUI scale 3: both theme radius 8 and radius 9
  showed at most one intensity level of change in the sampled sidebar over
  16 captures with a live world. This does not reproduce the reported flicker.

Evidence is retained locally below `build/render-hud-quality/`. The original
live developer configuration and launcher instance were not modified.
The owner clarified that the trigger is **motion behind a panel**, with an odd
blur radius. The stationary tests above did not exercise that trigger.

### Motion regression and fix

`python tools/performance/blur_phase_compare.py <fixture> <window> <output>
--radius 9 --box 440,500,840,610` drives the real renderer at GUI scale 2 with
a four-device-pixel periodic pattern, translated through four integer phases.
Its area average is constant: a changing panel is unwanted aliasing, not valid
background motion. The tool asserts no more than two intensity levels of
change in the panel body and retains every native window capture.

Before the fix, radius 8 passes (maximum change 1/255), while radius 9 fails
(maximum 55/255, mean about 30.7/255). The padded 498-pixel capture becomes
249 and then 125 texels. A single bilinear sample only implements a two-texel
box at **exactly** 2:1. Rounding the size sweeps its sampling phase across an
entire texel, folding moving fine detail into broad visible bands. This also
explains dependence on panel dimensions and GUI scale, not only radius parity.

Uneven downsampling now averages taps half a source texel to either side of
the sample, on each affected axis. Exact 2:1 and non-minifying passes retain
their single tap. The requested radius, padding, panel UV mapping and opacity
are preserved. The 26.1 OpenGL path clamps to its own used sub-rectangle in
grow-only textures; portable OpenGL/Vulkan use the same filter policy.

The radius-9 test now passes with maximum change 1/255 on 26.1.2, 26.2 and
26.3 OpenGL, and on 26.3 native Vulkan. The 26.3 OpenGL result is the same
on both landscape and portrait displays. This is a reproduced software
cause; it does not diagnose every possible hardware/scanout artifact.

[Motion comparison](evidence/render-hud-quality/odd-radius-motion.gif) and
[raw measurements](evidence/render-hud-quality/measurements.json) accompany
the report. `BoxDownsampleTest` locks down the real 249-to-125 case, including
the 26.1 grow-only allocation, and checks that its control detects the old alias.

## Remove Reloading Screen compatibility

The launcher's `Aurora 26.3` instance contains Remove Reloading Screen
5.2.8+mc.26.3 (`rrls`). Its saved log repeatedly reports a null
`ActiveTextCollector` while Aurora's `LoadingOverlayMixin` draws the current
screen inside RRLS's miniature overlay render.

A disposable 26.3 OpenGL client with Aurora, Fabric and that RRLS jar reproduced:

1. Startup failure in `PlayerSkinWidget`: `No model for layer
   minecraft:player#main`. RRLS exposes Aurora's title before initial model
   resources are ready. Disabling the custom title allows the same fixture
   to reach the world.
2. Pause screen followed by a resource reload: 242 `Failed to draw overlay`
   errors in the recorded log. The check asserting zero such messages fails.

Aurora now waits for `isGameLoadFinished()` before creating its title player
preview and rebuilds the title when resources become ready. Its reload-overlay
optimization yields to `rrls` under ADR 0009, leaving one owner of that render
path. The real catalog stand-down rule has a regression test.

After the compatibility fix, the same fixture starts with the custom title
enabled. Pause/reload completes in 4,849 ms with zero overlay errors, and
diagnostics explicitly report the reload overlay stood down for RRLS.

## Font preparation

All 132 Aurora font IDs previously prepared their fallback-codepoint selection
on every reload, including unused families. `perfDeferAuroraFonts` now prepares
the selected family during loading and leaves unused families pending until
their first glyph/width/white-glyph/obfuscation lookup. Vanilla does the actual
preparation; the glyph providers, metrics and fallback behavior are retained.
Pending sets track font-option changes and use replacement providers after a
GUI-scale change. Closing/reloading discards pending state.

The two mixins yield together to ModernFix. The switch is a machine setting,
excluded from profiles and portable defaults under ADR 0009.

Same-session OFF/ON/ON/OFF/OFF/ON runs, three samples of each state:

| Client/backend | Eager median | Deferred median | Reduction |
|---|---:|---:|---:|
| 26.1.2 OpenGL | 2,505 ms | 949 ms | 62% |
| 26.2 OpenGL | 2,339 ms | 795 ms | 66% |
| 26.3 OpenGL | 2,506 ms | 1,003 ms | 60% |
| 26.3 Vulkan | 2,449 ms | 950 ms | 61% |

Every run checks identical widths for all 132 IDs with ASCII, accented,
Greek/Cyrillic/CJK and bold/italic samples. The selected-family first lookup is
0.24–0.58 ms in the enabled runs that record it. An unused-family first lookup
costs about 15–19 ms once: work is deferred, not eliminated. This optimization
does not make every texture or mod resource faster, and it deliberately stands
down in the owner's ModernFix-equipped instance.

Real clients also covered GUI scale 2/3 changes and a rendered specimen with
fallback glyphs, bold/italic, underline/strikethrough and obfuscated text.

## UI cleanup

Shared setting rows now have a four-pixel gap; feature details use the shared
six-pixel content inset below the header. Stable cached panels and popups gain
a restrained +3 to -3 RGB vertical shade, preserving the original alpha.
Controls keep one treatment throughout hover, and selected/focused outlines
remain legible. The gradient creates no per-frame strips or extra draw calls.

Screenshot review covered the main grid/list and Performance detail screen at
854x480 and 1280x960, GUI scale 2/3, the live square/19% opacity and factory
rounded/33% opacity. Whole-word centered labels and scroll clipping remain
intact. Screenshots are retained under the disposable fixture directories.

## Launcher-instance FPS observation

The native-preview launcher's `Aurora 26.3` instance was copied into disposable
fixtures. Its installed Aurora jar has SHA-256
`05ef7a34bed7769b0e498faf7901a42fd64f307ef1fe80e53a1348196dcd2887`.
All other mod jars are identical across the comparison, including Sodium,
Iris, ImmediatelyFast, ModernFix, RRLS, Fast IP Ping and Krypton. Fabric API is
the selected adapter's development pin. No account configuration was copied.

The standalone `fps-observer.jar` has no Aurora dependency; Aurora's source
outputs are removed from the classpath in all three rows. Only the installed
or candidate row adds its Aurora jar. Each run has the same copied world,
1280x960 OpenGL window on the portrait monitor, GUI scale 3, view distance 16,
simulation distance 12, VSync off, Unlimited FPS and MINIMIZED inactivity
policy. After 20 seconds warming up in-world it reads the game's FPS counter
once a second for 20 seconds. No builds or second Minecraft process run during
sampling. Existing live Aurora feature settings, including hitboxes, are used
for the two Aurora rows.

| Row | Median observed FPS |
|---|---:|
| Other launcher mods, Aurora absent | 2,251.5 |
| Installed Aurora jar | 1,835 |
| Candidate | 2,125 |

The candidate is about 16% above the installed jar and 6% below Aurora absent
in this short stationary spawn scene on an RX 9070 XT. This does not establish
performance in a busy server, GPU-bound world, shader pack, combat or traversal.
It also does not isolate the new font/blur fixes: the candidate includes the
previously merged optimization series. The blur is closed during this sample,
and font deferral stands down for ModernFix. There is still a measurable cost
to Aurora's active features. No universal FPS improvement is claimed.

The saved live options also enable VSync and the AFK inactivity limit; Aurora's
GUI limit is 120 FPS. These are distinct caps from actual rendering cost and
were controlled in the experiment, without changing the owner's settings.

## Hit Color flash styles

`hitColorMode` ("Flash Style") selects how the hurt overlay rows are written.
Every style writes the 16x8 hurt strip of vanilla's overlay texture, so the
body, armor and (optionally) trims flash together and no shader or extra draw
call is added. Flat is the default, so existing installs look unchanged.

| Style | What it writes | What you see | Extra cost / notes |
|---|---|---|---|
| Vanilla flash | Vanilla's own red pixels | The game's normal red flash; picker color ignored | None: the rows are restored once |
| Flat tint (default) | The Flash Color, every row | One constant color for every hurt entity; picker alpha is the strength | One upload when the color changes |
| Pulse | Flash Color with alpha on a cosine between 25% and the picker alpha | The flash breathes in and out; every hurt entity pulses in sync. Pulse Rate 0.5-4 cycles/s | 16 palette steps per cycle, so a few dozen uploads a second, and only while something is hurt |
| Health gradient | Eight rows, red (0%) through yellow to green (100%), picker alpha | With **Gradient Shows Health** on, each hurt entity flashes its own health bucket; armor uses the same row. With it off, it behaves like Flat | Information advantage (ADR 0007): the switch ships off. Rows written once; row selection is a per-entity integer remap |
| Rainbow | Hue cycling over 4 s, picker alpha | The flash color drifts around the color wheel; all hurt entities share the hue | 256 palette steps per cycle, about 64 uploads a second at most, only while something is hurt |

The animated styles originally put the millisecond clock into the change
signature and refreshed for every rendered living entity, so a crowded scene
could re-upload the overlay several times per frame, hurt or not. They now run
on fixed palettes (`HitColorModes.pulseStep`/`rainbowStep`): the overlay is
re-uploaded only when the palette step changes, and only when an entity being
extracted actually carries the hurt overlay. `HitColorModesTest` covers the
palette sizes, the step boundaries and pulse's alpha range.

The first dev-client boot showed the refresh/gradient mixin failing to apply
on 26.3: its gradient hook was a `@ModifyArg` on `OverlayTexture.pack` whose
handler also captured the enclosing method's render state, which Mixin does
not allow. Fail-soft (ADR 0004) kept the game running and switched the feature
part off, so unit tests could not see it. The hook is now a `RETURN` inject
that swaps the v coordinate of the returned packed value and keeps vanilla's u.

## Scoreboard HUD

`scoreboard` (off by default) replaces the vanilla sidebar with a HUD module:
move, resize, lock and hide it in the HUD editor like any other module; scale,
background (Vanilla stripes, Solid or None) and background color live on its
feature card. The adapter mixin resolves the sidebar objective exactly as
vanilla does and cancels vanilla's draw only when the feature is on; rows are
rebuilt at most once per tick into preallocated arrays, keeping vanilla's sort
order, number formats, team prefix/suffix and 15-row cap.

A review against the 26.x bytecode confirmed the sort, hidden-entry filter,
number format, team formatting, stripe colors and 15-row cap match vanilla,
and found two bugs, both fixed: with F3 open the module layer stands down but
vanilla keeps its sidebar, so cancelling vanilla there left no scoreboard (the
mixin now leaves vanilla alone under F3); and a Solid background drew an empty
panel when there was no objective (the background now declines with no rows
outside the HUD editor). Rows are also dropped when the level changes.

The scoreboard keeps a **Vanilla** background, the reproduction of vanilla's
own two translucent stripes. That is outside the "Vanilla only for Armor HUD
and Potion HUD" rule, which was applied to the existing modules; the owner
should confirm whether the scoreboard should follow it too.

## Entity interpolation and server ping

Entity movement smoothing adapts its time constant to servers that bundle
movement updates, using `MovementCadence`. Its only sampler was called from
the hitbox renderer, which returns early unless hitboxes are on, so the
adaptation silently required hitboxes and froze at its last value otherwise.
Changes:

- `ThrottleDetector.tick` now samples on the client tick (through `Timed`),
  gated by `smoothAnimationsEnabled && entityMovementSmoothingEnabled`, and
  nothing in the hitbox renderers calls it. Per-tick is also the right rate:
  cadence is measured in game ticks, and per-frame easing made convergence
  frame-rate dependent. The per-frame `WorldScope` string check became a
  level identity compare.
- The cadence map evicts the least recently observed entity instead of the
  first one ever seen.
- Smoothing skips vehicles with riders (the documented intent; only riders
  were skipped, so a boat or horse could drift from its rider) and the
  spectated camera entity, besides the local player.
- An entity unrendered for more than 0.25 s snaps to its position instead of
  sliding in from where it was last seen. `EntityMovementSmootherTest` covers
  easing, the stale snap and teleport snaps.
- `smoothAnimationsEnabled` is now listed among the extract mixin's gates.

Known remaining: Aurora's hitboxes use vanilla interpolation, so with both
smoothing and hitboxes on, the hitbox follows the unsmoothed position while
the body is eased. That is a product decision (smoothed hitboxes were removed
earlier for desyncing in the other direction) and is unchanged here.

Server ping and connection: Aurora does nothing on the connect or ping path.
It only reads the latency vanilla already measured (server list bars, Ping
HUD with a 200 ms cache, nametag latency). There is no DNS lookup on the render
thread and no extra ping. ADR 0007 rules out custom packets or extra sends, so
there is nothing for Aurora to make faster there; routing is the network's and
the server's.

## HUD dev-client boots

A disposable 26.3 fixture (`build/hud-boot/`, space-free world, a datapack
that creates a six-row sidebar objective, low-durability helmet/boots/sword,
empty leggings, a shield, a short Speed effect, and a high-health NoAI zombie
damaged every 12 ticks) was booted seven times on the portrait monitor with
no input, varying only `config/aurora.json`:

| Boot | Backend | Settings | Result |
|---|---|---|---|
| A | OpenGL | Armor HUD + hands, Vanilla strip; scoreboard Vanilla | Sidebar replaced, same rows/order as vanilla (D). Five cells on one hotbar strip, empty leggings omitted. Alert rows present; Effect row expires on time |
| B | OpenGL | Solid backgrounds, scoreboard 1.5x, armor vertical | Scaled solid sidebar; vertical solid armor column |
| C | Vulkan | As A | Identical to A |
| D | OpenGL | Scoreboard off | Vanilla sidebar renders unchanged |
| E | OpenGL | Health gradient on, blue Flash Color | Zombie at ~40% flashes yellow, not blue or red |
| F | OpenGL | Rainbow | Hue advances between hits (purple → red → orange → olive → green → teal → blue) |
| G | Vulkan | Flat, blue | Blue flash |

A-D exposed the failed Hit Color mixin (fixed above) and the alert modules
sitting under the sidebar (moved middle-left). After both fixes E-G log
`All 97 Aurora mixins applied.` The factory Armor HUD position (bottom right,
241 px in) overlaps the hotbar when the scaled screen is narrow (1300 px
window at GUI scale 3); that owner-tuned default is unchanged. Not verified:
the alert sound plays once per item (no audio capture), and the Scoreboard
F3/empty-panel fixes, which landed after these boots and are covered by
build/test only.

## Verification still in progress

The three supported build/test suites pass with the rendering and font changes.
The copied launcher mod set boots, with 95 mixins applied or stood down (seven
stand-downs, including RRLS, ModernFix and ImmediatelyFast).

Three 26.3 factory-default boots passed `tools/overhead_check.py`, each with two
warm-up and four judged runs. No parity probe or FPS observer was installed.
The fixtures used the portrait monitor and an open world with no screen:

| Fixture | CPU mean range | CPU p95 range | UI fills / glass captures |
|---|---:|---:|---:|
| OpenGL, Aurora/Fabric | 0.080–0.097 ms | 0.103–0.125 ms | 0 / 0 |
| Native Vulkan, Aurora/Fabric | 0.098–0.107 ms | 0.124–0.139 ms | 0 / 0 |
| OpenGL, copied launcher mods | 0.079–0.090 ms | 0.102–0.126 ms | 0 / 0 |

These are targeted checks of the rendering/loading candidate, not the full
fourteen-row release matrix or evidence for features that remain pending.
The optional acceptance tooling also compiles on all three supported lines.
The HUD, alert, Hit Color and interpolation changes pass the three supported
build/test suites; their dev-client boot checks are recorded below.
No release or owner configuration was changed.
