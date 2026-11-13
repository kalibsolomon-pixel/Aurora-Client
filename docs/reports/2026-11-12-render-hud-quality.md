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
Independent review and delegated HUD/Hit Color changes remain outstanding.
No release or owner configuration was changed.
