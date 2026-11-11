---
status: accepted
date: 2026-11-10
---

# UI shapes are drawn by a shader through Minecraft's pipeline; no own GPU renderer

Aurora's anti-aliased rounded rectangles and outlines are computed on the CPU in `RenderUtil` and emitted as many thin `GuiGraphics.fill` strips, about a thousand fills for one list row. That cost is why row templates and layer caches exist. The decision is to draw those shapes with a shader, one quad per shape, submitted through Minecraft's own render pipeline, and to rule out a separate GPU renderer for the UI.

## Considered options

- **Keep strips and caches only.** Works today, but every new screen needs cache discipline to stay fast.
- **Own GPU renderer bypassing `GuiGraphics`.** Rejected: it would also have to own text, clipping and layering, and it is a second pipeline to keep working under Sodium, Iris and VulkanMod. The glass renderer, with its separate OpenGL and Vulkan paths, shows what that costs.

The blur and glint code already register custom pipelines and shaders this way, including on VulkanMod, which is why the middle option is low risk.

## Consequences

- The change goes in behind `RenderUtil`, so no screen code changes.
- It waits until after the port. The pipeline API is what changes between Minecraft versions, so building it on 1.21.11 would mean building it twice.
- Existing caches stay until a measurement shows a given cache is no longer needed. The reference case is the Waypoints screen with 40 rows.
- The gain has not been measured yet.
