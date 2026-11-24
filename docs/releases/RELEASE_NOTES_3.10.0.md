# Aurora Client 3.10.0

One JAR per Minecraft version: **26.1.2**, **26.2**, and **26.3**, built from the same
source commit. Install the one matching your Minecraft version with Fabric Loader
and Fabric API. Java 25 is required.

## UI & Layout

- **Reorganised Settings into six pages (PR #119, PR #120):** The Settings tab is now
  split into **Appearance**, **World & HUD**, **Performance**, **Audio & Keys**,
  **Privacy**, and **Tools**. The former Display Settings bucket is split into
  individual catalog features (**Decoupled Input**, **Tab List**, **Nametags**,
  **Per-Sound Volume**, **Streamer Mode**, and **Server List**) with their own
  fair-play classes, mixin health warnings, and per-feature resets.
- **Compact icon rail at high GUI scales (PR #118):** When the main Aurora window is
  shrunk on small viewports or high GUI scales (such as GUI scale 4 at 1080p), the
  sidebar collapses into a 22 px icon rail with tooltips and windows use a 6 px screen
  margin under 300 px height so all six Settings tabs and the content column fit
  without crowding.
- **Full-screen Resource Pack Manager (PR #117):** The folder-aware Resource Pack
  Manager now uses vanilla's full-screen pack layout with Aurora's controls inside it
  (centred title and search bar, two 200 px lists with breadcrumbs and folder counts,
  36 px rows, and a responsive footer) instead of a cramped bordered window, and fixes
  the empty-folder note overlapping the `..` row.
- **Smoother Aurora title-screen logos (PR #122):** Both the centre Aurora mark and
  the top-right brand icon button now render from power-of-two SVG rasters (`16`–`512`)
  with linear filtering, edge clamping, and RGB edge-bleed dilation, eliminating
  nearest-neighbor stair-stepping and dark fringes across GUI scales.

## Performance & Cleanup

- **Removed Aurora's frame pacer, frame rate limiters, low latency, and Reflex
  (PR #121):** Ephemera now owns frame pacing, frame rate limiting, and low-latency
  rendering outright. Removed Aurora's frame pacer (`FramePacer` / `FramePacing` /
  `ParkDiagnostics`), global/GUI/unfocused frame caps, VSync override, adaptive render
  sleeping, and automatic Reflex scheduler across all three Minecraft lines. Older
  configs and saved profiles carrying the retired fields load quietly without warnings.

## Fixes

- **2026-11-23 reliability & correctness pass (PR #116):**
  - **Profiles:** Atomic rename durability (old file is only deleted after the
    replacement lands durably); manual profile switches and renames now interact
    cleanly with per-server auto-switching; IPv6 server patterns parse bracketed,
    bare RFC 5952, and `host:port` forms accurately.
  - **Chat Suite:** Search no longer overwrites vanilla's display filter; chat history
    uses read-your-writes across fast reconnects, buffers incoming messages while
    loading, captures disconnect policy/limit per session, and keys singleplayer worlds
    by save directory so renames keep history.
  - **Global shortcut combos (26.2 / 26.3):** Fullscreen, screenshot, and friends
    shortcuts now share Aurora's modifier and combo-deferral handling.
  - **Screenshots & Resource Packs:** Concurrent screenshot crop saves write atomically
    without clobbering; pack icon refreshes use a generation counter to ignore stale
    background decodes; Resource Pack Manager gains full keyboard navigation and
    O(visible) viewport culling.
  - **Windows Media Controls:** Keeps the media helper warm across isolated keypresses
    independently of Now Playing HUD polling and coalesces close requests.
  - **Release tooling:** Tightened parity acceptance log/result binding and fixed
    `26.1.2` VulkanMod renderer detection (`not OpenGL (Vulkan or other)`).

## Verification

- Full 14-row parity GPU acceptance across Minecraft **26.1.2**, **26.2**, and
  **26.3** (OpenGL, VulkanMod, and native Vulkan), 56 judged runs:
  **0.038–0.109 ms** mean and **<= 0.202 ms** p95 Aurora CPU per frame (budget:
  0.25 ms mean / 0.40 ms p95), **0.0** UI fills, **0.0** glass captures, all native
  mixins healthy (`146` on 26.1.2, `149` on 26.2, `146` on 26.3), and zero Aurora
  warnings or errors across every row ([evidence](evidence/3.10.0-parity.json)).
- Visually verified the tiered linear-filtered Aurora title-screen logos on a live
  Minecraft `26.2` dev boot (GUI scale 3) with zero Aurora warnings or errors.
