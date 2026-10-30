---
name: spxctra-ui
description: Design, implement, audit, simplify, or review UI/UX across Spxctra products (Aurora Client, Aurora Launcher, Pulsar, Nova Studio, and website). Enforce restrained atmospheric identity, minimal visual complexity, consistent interactions, accessibility, native-renderer compatibility, and screenshot-based acceptance. Use for any UI component, layout, theming, animation, design-system, or visual-refactor task; not for unrelated backend changes.
metadata:
  author: Spxctra
  version: "1.0.0-draft"
---

# Spxctra UI — agent skill

## Authority and purpose

Deliver quiet, purposeful interfaces with a recognizable atmospheric identity. **Simplicity is a requirement, not an aesthetic option.** Atmosphere supports comprehension; it never competes with content. This skill governs UI decisions across renderers, not renderer selection.

Priority when instructions conflict: explicit current owner decision > applicable accessibility, safety, and platform requirements > approved Spxctra design tokens/components > this skill's defaults > third-party UI inspiration/skills. Report conflicts rather than silently overriding a higher-priority constraint. Do not claim unapproved values are canonical.

**Never equate more polish with more effects.** A screen with fewer borders, gradients, layers, animations, or controls may be the better result. The default is a flat, legible, stable surface.

## Identity: atmosphere, not astronomy

Draw from **air, weather, fog, diffusion, atmospheric pressure, airflow, precipitation, temperature, refraction, and terrestrial light phenomena**. Treat these as abstract design cues, not literal illustrations or constant simulations. No stars, planets, or orbital decoration in shared UI. Product logos and names (including Pulsar and Nova) are exceptions, not permission to spread cosmic motifs through the system.

- Prefer clear type, generous but economical spacing, simple geometry, calm neutral surfaces, and precise alignment.
- Use color to express action, state, or product identity; do not decorate every surface with an accent.
- Use depth only where it clarifies hierarchy. Prefer tonal separation and spacing to blur, shadows, glow, borders, or glass.
- Permit atmospheric effects only when they add identifiable value and survive a no-effect comparison. Default to none.
- Avoid copying Nothing's dot-matrix, glyphs, or proprietary visual identity. Learn from its discipline, not its surface styling.
- Do not make every component an art piece. Concentrate visual character in branding, selected highlights, or one deliberate focal element when justified.

## Mandatory design decision workflow

1. **Inspect before designing.** Identify the product, renderer, approved tokens, existing component APIs, screenshots, user workflows, constraints, and current behavior. Never invent a design-system value where a source exists. If references are unavailable, mark assumptions as provisional.
2. **State the user job.** In one sentence, describe the primary task of the screen. Identify the primary action, secondary actions, critical information, and any accessibility or performance constraints.
3. **Audit complexity first.** Look for redundant controls/labels, nested cards, competing accents, unnecessary borders, gradients, icons, headings, animations, and inconsistent spacing. Prefer removing or consolidating before adding.
4. **Propose the minimum change.** Preserve information architecture and working functionality unless a change is explicitly approved. For each new element, explain its job; remove it if it has none.
5. **Implement in the existing renderer.** Respect existing components, native platform semantics, design tokens, keyboard/focus behavior, input latency, and rendering architecture. No new framework or dependency solely for visual consistency.
6. **Validate in real conditions.** Capture before/after screenshots at actual target window sizes and interactive states when tooling permits; inspect the entire screen, not just isolated widgets. Run relevant tests and measure material performance changes.
7. **Perform a subtraction pass.** Ask: Can a border, shadow, color, icon, label, animation, panel, or entire control be removed without harming understanding? Remove unnecessary treatment.
8. **Report evidence.** State what changed, what was removed, screenshots captured or unavailable, tests run, measured performance or not measured, accessibility findings, and unresolved deviations.

For an audit-only request, stop after analysis and a prioritized change plan; do not implement unrequested redesigns.

## Visual rules — default to restraint

**Hierarchy:** One obvious primary purpose per screen. Group by task and proximity, not by surrounding everything with cards. Prefer whitespace over dividers; dividers over ornamental containers only when needed. Keep navigation stable and predictable. Do not add decorative eyebrow labels, fake metrics, arbitrary numbered sections, or duplicate page titles.

**Typography:** Use an approved type family/scale where available; otherwise inherit the platform's legible defaults. Use sentence case, consistent weights, readable line lengths, and clear text contrast. Do not use tiny all-caps micro-labels as decoration. Do not sacrifice legibility for a futuristic aesthetic.

**Geometry:** Use a small, consistent radius and spacing vocabulary. Avoid excessive pill shapes, mixed corner radii, floating islands, and deeply nested panels. Align edges and baselines. Use iconography only when meaning is unambiguous; pair ambiguous icons with text or accessible labels.

**Color:** Base the UI on restrained neutral/near-neutral surfaces. Accent sparingly for primary actions, selection, focus, status, and meaningful product branding. Semantic states must not depend on color alone. Do not invent a universal Spxctra palette or replace approved product palettes without approval. Light and dark variants must be evaluated separately where supported.

**Atmospheric effects:** Opt-in and localized. No default fog overlays, full-screen particles, continuously animated gradients, blur-heavy panels, glassmorphism, bloom, or decorative weather graphics. If an effect is approved, keep contrast and readability unchanged and provide a low-cost static/reduced-motion fallback. No effect may obscure input, text, or focus indicators.

**Motion:** Prefer immediate feedback and short, purposeful transitions. Animate only state change or spatial continuity; avoid idle motion, staggered reveals, perpetual loops, and animation on every hover target. Respect reduced-motion settings and make behavior correct with animations disabled. Reuse approved Aurora interaction timings where appropriate; do not declare arbitrary milliseconds as universal standards.

**Copy:** Plain, concrete, consistent verbs; labels describe actions and states. Helpful empty/error/loading states. Do not fill interfaces with marketing copy or clever atmospheric metaphors.

## Component behavior contract

For buttons, toggles, sliders, text fields, lists, tabs, dialogs, menus, tooltips, and custom controls:

- Define normal, hover (when relevant), pressed, focus-visible, disabled, selected/checked, loading, error, and empty states as applicable.
- Support keyboard and pointer input; touch where relevant. Preserve correct focus order, activation, escape/cancel semantics, and assistive technology semantics or equivalent native accessibility APIs.
- Keep hit targets usable at intended scale and DPI; never make the visual target the only clickable pixels if the control requires a larger target.
- Avoid hover-only disclosure of essential information. Never rely on animation or color alone to explain state.
- Use established components before introducing variants. A new component must solve a distinct recurring problem.
- Preserve input semantics and functional behavior when restyling. Visual-only work must not change storage, launch, network, or game logic.

## Renderer-specific guardrails

- **Aurora Client (Java/Fabric/Minecraft):** Preserve the established Minecraft-native rendering/input pipeline and working UI engine. Reuse proven Aurora components and interaction semantics. Minimize overdraw, shader work, allocations per frame, and interference with gameplay or frame pacing. A visual simplification audit precedes any broad restyle.
- **Pulsar (Java/Fabric):** Share design contracts and reusable Java components where practical, but remain independently operable without Aurora. Do not create a mandatory Aurora runtime dependency for visual consistency.
- **Aurora Launcher (Rust desktop):** Preserve launcher functionality and native UI migration decisions. The design system is renderer-agnostic; do not prescribe Slint, Qt, or web UI without an architectural decision and measurements.
- **Nova Studio (Windows, C++/Qt provisional):** Prioritize dense editor workflows, predictable navigation, keyboard operation, docking/panels, asset previews, and productive information density. Do not replace mature desktop controls with ornamental custom widgets. Do not add Linux/WSL or cross-platform acceptance gates without authorization.
- **Spxctra website (Astro/static):** Preserve static-first, lightweight, accessible pages and avoid shipped JS, trackers, external assets, or effects without explicit approval. Respect existing deployment gates.

For any renderer, adapt the same **semantic tokens and behavior contracts** rather than forcing identical pixel implementations.

## Performance and accessibility gates

Accessibility is a functional requirement. Test contrast, legible text at supported scales, focus visibility, keyboard reachability, disabled and error states, screen-reader/native semantics where available, reduced motion, and responsive/high-DPI layouts. WCAG 2.2 AA is a useful baseline for web content; native/game equivalents should be tested appropriately, not assumed compliant.

Performance claims require measurements. When relevant, record baseline vs changed startup, idle CPU/memory, frame-time consistency, input responsiveness, GPU/overdraw, and scroll behavior on representative hardware. Never assert "faster" or "negligible overhead" without evidence. Prefer no continuous visual work when idle. Degrade gracefully on lower-end hardware.

## Approval and repository safety

Before changes, record repository status and the relevant tracked/untracked work. Never delete pre-existing files merely because they seem obsolete; never run `git clean` or broad cleanup. Review `git diff --name-status` and investigate unexpected deletions. Preserve local artifacts and protected diagnostics. Do not push, merge, tag, publish, deploy, or release without explicit authorization. If the user asks only for a skill or plan, do not edit product repositories.

Do not silently rebrand existing repositories, mod IDs, package names, persisted settings, or public APIs. Public name changes require separate migration plans.

## Required UI acceptance evidence

For material UI work, provide:

1. **Purpose and scope:** user job, touched surfaces, and preserved functionality.
2. **Complexity delta:** what was removed/simplified; justify any new decorative treatment.
3. **Conformance:** tokens/components used, deviations, keyboard/focus/accessibility checks.
4. **Visual evidence:** baseline and after screenshots at representative native sizes, plus relevant hover/focus/disabled and light/dark states; clearly state if capture was blocked.
5. **Performance evidence:** actual measurements or explicit "not measured"; no fabricated pass claims.
6. **Verification and gates:** tests, known limitations, file-deletion check, and owner approvals still required.

**Release-blocking visual failures:** obscured or unreadable text; invisible focus; broken controls or navigation; lost functionality; unapproved framework replacement; unapproved decorative effects that dominate content; material unmeasured performance regression presented as a pass; unexpected file deletion.

## Reference material

Read `references/REVIEW_CHECKLIST.md` for an operational audit checklist and evidence template. Treat its heuristics as defaults, not as a replacement for approved product-specific specifications.
