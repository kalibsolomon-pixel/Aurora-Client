package com.aurora.client.conformance;

import java.util.List;

/**
 * The Design Language §15.2 conformance family manifest — the finite catalog
 * of production interaction families and the contract dimensions each one
 * carries, as accepted at the Phase C closure baseline. This is the tracked
 * half of the §15.2 harness: {@code ConformanceManifestTest} audits the
 * production tree against it (completeness, hover vocabulary, sound
 * ownership, radius/ClipBand inventories) and {@code ButtonLikeConformanceTest}
 * runs the shared button-like contract over real production controls, so a
 * NEW family cannot ship unclassified — the completeness sweep fails until it
 * is added here with explicit contract dimensions.
 *
 * <p>Dimensions record the ACCEPTED contract, not an aspiration: a family
 * entry is a claim about production that the tests verify from source or
 * behavior. Values follow DESIGN_LANGUAGE §8 (interaction states), §9 (motion:
 * canonical hover 140 ms symmetric; press ≈90 ms + ~180 ms recovery), §10
 * (sound ownership), §11 (per-component specs) and the Phase B/C closure
 * records in ARCHITECTURE.md.
 *
 * <p>The runtime half of the harness is the untracked DevPilot
 * {@code phasecclose} mode (a representative cross-family acceptance matrix
 * in dark/light ROUND and dark SQUARE). By convention DevPilot stays untracked;
 * every durable assertion it depends on lives here or in the per-family suites.
 */
public final class SemanticFamilyManifest {

    private SemanticFamilyManifest() {}

    /** §8.3/§9 hover policy of the family's primary interactive surface. */
    public enum Hover {
        /** Pointer-only {@code HoverAnim.symmetric(140)} — the canonical vocabulary. */
        CANONICAL_140,
        /** Sanctioned immediate hover with a recorded rationale (scanning/selection lists). */
        IMMEDIATE_SANCTIONED,
        /** Direct manipulation — interpolation is meaningless while dragging. */
        DIRECT_MANIPULATION,
        /** No hover animation by contract (fields, passive surfaces). */
        NOT_ANIMATED
    }

    /** §8.4 press/manipulation acknowledgment. */
    public enum Press {
        /** Shared 90 ms compression toward 0.96 + ~180 ms recovery (Button/mixin timeline). */
        SCALE_90_180,
        /** ToggleSwitch's one-shot thumb-compression pulse. */
        THUMB_PULSE,
        /** Slider's instant drag grip; value follows the pointer directly. */
        DRAG_GRIP,
        /** No press animation by recorded decision — the state change is the feedback. */
        NONE_BY_DESIGN,
        /** Surface is not an interactive control. */
        N_A
    }

    /** §10/§14 sound ownership — exactly one owner per family. */
    public enum Sound {
        /** {@code SemanticAction.button(...)}: the action owns one ACTIVATION via the feedback adapter. */
        SEMANTIC_ACTION,
        /** Control carries {@code SemanticSound.NONE}; the behavior plays exactly one ACTIVATION on genuine change. */
        BEHAVIOR_PLAY_ON_CHANGE,
        /** Vanilla widget keeps vanilla's own click (Aurora carries NONE). */
        VANILLA_OWNED,
        /** No sound by contract (sliders: no full click per value; hover always silent). */
        NONE_BY_CONTRACT
    }

    /** Keyboard activation policy. */
    public enum Keyboard {
        /** Enter/Space converge on the one activation path. */
        ENTER_SPACE,
        /** Enter/Space arm a capture; a later event completes it (§11.8). */
        ENTER_SPACE_CAPTURE,
        /** Arrow keys step the value when focused; no Enter/Space activation. */
        ARROW_STEPS,
        /** Vanilla owns the keyboard path entirely. */
        VANILLA_OWNED,
        /** Pointer-only by contract (direct manipulation). */
        POINTER_ONLY,
        /** No keyboard surface (passive/non-control family). */
        N_A
    }

    /**
     * One production conformance family. {@code files} are repo-root-relative
     * sources that implement it (the completeness sweep maps EVERY production
     * {@code new SemanticActionControl(} site onto a family through them).
     */
    public record Family(String id,
                         Hover hover,
                         Press press,
                         Sound sound,
                         Keyboard keyboard,
                         boolean focusHairline,
                         boolean narrated,
                         boolean disabledGate,
                         boolean clipCoupled,
                         List<String> files,
                         String notes) {}

    /** Root-relative source helper. */
    public static final String SRC = "src/client/java/com/aurora/client/";

    /** The production conformance families (§7 of the closure task — actual families only). */
    public static final List<Family> FAMILIES = List.of(
            new Family("button-action", Hover.CANONICAL_140, Press.SCALE_90_180,
                    Sound.SEMANTIC_ACTION, Keyboard.ENTER_SPACE, true, true, true, true,
                    List.of(SRC + "ui/component/Button.java",
                            SRC + "ui/component/ButtonWidget.java",
                            SRC + "screen/setting/ButtonSetting.java",
                            SRC + "screen/setting/PixelCanvasSetting.java",
                            SRC + "screen/ManagerListScreen.java",
                            SRC + "screen/ProfileManagerScreen.java",
                            SRC + "screen/WaypointManagerScreen.java"),
                    "Ordinary discrete actions. Custom rows play the semantic click; "
                            + "vanilla-backed ButtonWidgets carry NONE (vanilla's own funnel)."),
            new Family("toggle", Hover.CANONICAL_140, Press.THUMB_PULSE,
                    Sound.SEMANTIC_ACTION, Keyboard.ENTER_SPACE, true, true, true, true,
                    List.of(SRC + "ui/component/ToggleSwitch.java",
                            SRC + "screen/setting/BooleanSetting.java"),
                    "On/off state persistent and independent of hover; whole-row adapter; "
                            + "narration carries On/Off."),
            new Family("slider", Hover.CANONICAL_140, Press.DRAG_GRIP,
                    Sound.NONE_BY_CONTRACT, Keyboard.ARROW_STEPS, true, true, true, true,
                    List.of(SRC + "ui/component/Slider.java",
                            SRC + "screen/setting/SliderSetting.java",
                            SRC + "screen/setting/ThemeOpacitySetting.java"),
                    "No full click per value (§11.5); drag survives leaving the knob bounds; "
                            + "disabled bit is a cache-fingerprint input."),
            new Family("enum", Hover.CANONICAL_140, Press.NONE_BY_DESIGN,
                    Sound.SEMANTIC_ACTION, Keyboard.ENTER_SPACE, true, true, true, true,
                    List.of(SRC + "screen/setting/EnumSetting.java"),
                    "Expanded ≠ hover (pointer-only target); popup rows are the sanctioned "
                            + "immediate-hover scanning family; placement derives from the host band."),
            new Family("color-swatch", Hover.CANONICAL_140, Press.NONE_BY_DESIGN,
                    Sound.SEMANTIC_ACTION, Keyboard.ENTER_SPACE, true, true, true, true,
                    List.of(SRC + "ui/component/ColorSwatch.java",
                            SRC + "screen/setting/ColorSetting.java"),
                    "The represented color sample is DATA — no state modifies its pixels; "
                            + "focus hairline deliberately distinct from the selection ring."),
            new Family("keybind", Hover.CANONICAL_140, Press.NONE_BY_DESIGN,
                    Sound.SEMANTIC_ACTION, Keyboard.ENTER_SPACE_CAPTURE, true, true, true, true,
                    List.of(SRC + "screen/setting/KeybindSetting.java"),
                    "Focus ≠ listening; exactly-once setter+save; Escape/Backspace clear to "
                            + "UNBOUND; disabled cancels capture without mutation."),
            new Family("keylist", Hover.CANONICAL_140, Press.NONE_BY_DESIGN,
                    Sound.SEMANTIC_ACTION, Keyboard.ENTER_SPACE_CAPTURE, true, true, true, true,
                    List.of(SRC + "screen/setting/KeyListSetting.java"),
                    "Multi-value capture owned at ADD level; entry chips keep the sanctioned "
                            + "immediate-hover scanning painter but carry full semantic accessibility."),
            new Family("tooltip", Hover.NOT_ANIMATED, Press.N_A,
                    Sound.NONE_BY_CONTRACT, Keyboard.N_A, false, true, false, false,
                    List.of(SRC + "screen/setting/FeatureSetting.java"),
                    "The ONE production tooltip: 1500 ms dwell + the deliberate supplemental "
                            + "symmetric 200 ms fade (not the 140 ms control-hover duration)."),
            new Family("navigation-tabs", Hover.CANONICAL_140, Press.NONE_BY_DESIGN,
                    Sound.BEHAVIOR_PLAY_ON_CHANGE, Keyboard.ENTER_SPACE, true, true, false, true,
                    List.of(SRC + "screen/ResourcePackBrowserScreen.java",
                            SRC + "screen/AuroraScreen.java"),
                    "Persistent selection never aliases hover; already-selected reactivation is "
                            + "a silent consumed no-op; arrows rove focus-only through the group ring."),
            new Family("peer-selection", Hover.CANONICAL_140, Press.NONE_BY_DESIGN,
                    Sound.BEHAVIOR_PLAY_ON_CHANGE, Keyboard.ENTER_SPACE, true, true, true, true,
                    List.of(SRC + "screen/setting/AccentSetting.java"),
                    "Selection derives from the literal value (no stored index); conditional "
                            + "click only on genuine change; grid roving ring declared by the component."),
            new Family("segmented", Hover.CANONICAL_140, Press.NONE_BY_DESIGN,
                    Sound.BEHAVIOR_PLAY_ON_CHANGE, Keyboard.ENTER_SPACE, true, true, true, true,
                    List.of(SRC + "ui/component/SegmentedControl.java",
                            SRC + "screen/setting/SegmentedSetting.java"),
                    "Live-derived selection; hover composes with (never suppressed by) selected; "
                            + "one persistence path (the setter), never a second config save."),
            new Family("icon-action", Hover.CANONICAL_140, Press.NONE_BY_DESIGN,
                    Sound.SEMANTIC_ACTION, Keyboard.ENTER_SPACE, true, true, true, true,
                    List.of(SRC + "ui/component/IconAction.java",
                            SRC + "screen/setting/ItemScaleSetting.java",
                            SRC + "screen/setting/EffectRowSetting.java",
                            SRC + "screen/setting/EffectExpiryListSetting.java",
                            SRC + "screen/setting/ParticleRowSetting.java",
                            SRC + "screen/setting/SearchListSetting.java",
                            SRC + "screen/HudEditorScreen.java"),
                    "Glyph is presentation only — the accessible label is textual; disclosure "
                            + "actions flip glyph+narration from one state supplier while focus survives."),
            new Family("vanilla-button-plain", Hover.CANONICAL_140, Press.SCALE_90_180,
                    Sound.VANILLA_OWNED, Keyboard.VANILLA_OWNED, true, true, true, false,
                    List.of(SRC + "mixin/AbstractButtonMixin.java"),
                    "C-6 boundary: Aurora owns pixels through the shared painter/timeline; "
                            + "vanilla owns interaction, narration, enabled authority and sound."),
            new Family("text-field", Hover.NOT_ANIMATED, Press.N_A,
                    Sound.NONE_BY_CONTRACT, Keyboard.VANILLA_OWNED, true, true, true, true,
                    List.of(SRC + "mixin/EditBoxMixin.java"),
                    "Glass input chrome; focus = caret + accent hairline; the C-8 Escape rule "
                            + "(first Escape unfocuses, second closes) on every production field "
                            + "except ColorPicker's hex field (intentional screen-level Cancel)."),
            new Family("scroll-host", Hover.DIRECT_MANIPULATION, Press.N_A,
                    Sound.NONE_BY_CONTRACT, Keyboard.N_A, false, false, false, true,
                    List.of(SRC + "ui/util/ClipBand.java",
                            SRC + "screen/AuroraScreen.java",
                            SRC + "screen/ManagerListScreen.java",
                            SRC + "screen/ResourcePackBrowserScreen.java",
                            SRC + "screen/FeatureDetailScreen.java"),
                    "One band truth per scrolling host drives scissor, cull, click walk, hover "
                            + "test and availability sweep — render truth and input truth agree."),
            new Family("popup-surface", Hover.IMMEDIATE_SANCTIONED, Press.NONE_BY_DESIGN,
                    Sound.SEMANTIC_ACTION, Keyboard.ENTER_SPACE, true, true, true, true,
                    List.of(SRC + "screen/setting/EnumSetting.java"),
                    "Above-dim surface; placement resolves deterministically (below, flip-up, "
                            + "roomier-clamped) from the SAME resolver in render and hit-testing."),
            new Family("modal-surface", Hover.NOT_ANIMATED, Press.SCALE_90_180,
                    Sound.SEMANTIC_ACTION, Keyboard.ENTER_SPACE, true, true, true, false,
                    List.of(SRC + "screen/ResourcePackBrowserScreen.java",
                            SRC + "screen/setting/PixelCanvasSetting.java"),
                    "Background pointer AND focus blocked; animated geometry is the hit "
                            + "geometry through every frame; PixelCanvas warning stays a "
                            + "classified TEXT_ACTION inline panel."));

    /**
     * Production surfaces that are deliberately NOT conformance families —
     * the finite exemption catalog (DESIGN_LANGUAGE §16: every exception is
     * intentional, narrowly scoped, explainable). A surface may not join this
     * list without a classification matching one of the recorded rulings.
     */
    public record NonFamily(String file, String classification) {}

    public static final List<NonFamily> NON_FAMILIES = List.of(
            new NonFamily(SRC + "screen/setting/ThemePreviewSetting.java",
                    "PREVIEW/DATA-ONLY — mock slider (legacy ctor by design), previewMode() "
                            + "toggle, non-interactive painter buttons"),
            new NonFamily(SRC + "screen/WaypointManagerScreen.java#chips",
                    "DATA-ONLY — ColorSwatch.dataOnly() display chips (no interaction state)"),
            new NonFamily(SRC + "screen/setting/CrosshairPreviewSetting.java",
                    "PREVIEW — the real-shape crosshair preview; never consumes"),
            new NonFamily(SRC + "screen/setting/SectionFooterSetting.java",
                    "PASSIVE TEXT — group footer; never consumes"),
            new NonFamily(SRC + "screen/setting/SectionHeaderSetting.java",
                    "PASSIVE TEXT — group caption; no button behavior"),
            new NonFamily(SRC + "screen/ColorPickerScreen.java#surfaces",
                    "DIRECT-MANIPULATION DATA — pad/hue/alpha editing surfaces; Square-exempt "
                            + "data-driven frames; hex field's Escape is the screen's Cancel"),
            new NonFamily(SRC + "screen/HudEditorScreen.java#canvas",
                    "DIRECT-MANIPULATION — drag/resize handles + ±6 grab halo (ruled "
                            + "affordance, X badge tested before corner zones)"),
            new NonFamily(SRC + "worldmap/screen/WorldMapScreen.java#map",
                    "DATA SURFACE — pan/zoom map content; chrome buttons are ButtonWidgets"),
            new NonFamily(SRC + "screen/ManagerListScreen.java#thumbs",
                    "DIRECT-MANIPULATION — scrollbar capsules (mechanical, Square-exempt)"),
            new NonFamily(SRC + "hud/module/KeystrokesModule.java#keycaps",
                    "REPRESENTATIONAL HUD — drawn keycaps, Square-exempt radius"),
            new NonFamily(SRC + "util/HudBackgrounds.java",
                    "SANCTIONED DEVIATION — documented 'boxier' HUD radius"),
            new NonFamily(SRC + "ui/render/blur/BlurTestScreen.java",
                    "DEV HARNESS — material inspection surface"),
            new NonFamily(SRC + "DevPilot.java",
                    "DEV HARNESS — untracked development automation"));

    /** Families whose hover animator is a single-file declaration the sweep can pin. */
    public static final java.util.Map<String, String> CANONICAL_HOVER_ANCHORS = java.util.Map.ofEntries(
            java.util.Map.entry(SRC + "ui/component/Button.java", "HoverAnim.symmetric(HOVER_MS"),
            java.util.Map.entry(SRC + "ui/component/ToggleSwitch.java", "HoverAnim.symmetric(HOVER_MS"),
            java.util.Map.entry(SRC + "ui/component/Slider.java", "HoverAnim.symmetric(140"),
            java.util.Map.entry(SRC + "ui/component/ColorSwatch.java", "HoverAnim.symmetric(140"),
            java.util.Map.entry(SRC + "ui/component/SegmentedControl.java", "HoverAnim.symmetric(140"),
            java.util.Map.entry(SRC + "ui/component/IconAction.java", "HoverAnim.symmetric(HOVER_MS"),
            java.util.Map.entry(SRC + "mixin/AbstractButtonMixin.java", "HoverAnim.symmetric(140"),
            java.util.Map.entry(SRC + "screen/setting/EnumSetting.java", "HoverAnim.symmetric(140"),
            java.util.Map.entry(SRC + "screen/setting/KeybindSetting.java", "HoverAnim.symmetric(140"),
            java.util.Map.entry(SRC + "screen/setting/KeyListSetting.java", "HoverAnim.symmetric(140"),
            java.util.Map.entry(SRC + "screen/setting/AccentSetting.java", "HoverAnim.symmetric(HOVER_MS"),
            java.util.Map.entry(SRC + "screen/ResourcePackBrowserScreen.java", "HoverAnim.symmetric(140"),
            java.util.Map.entry(SRC + "screen/AuroraScreen.java", "HoverAnim.symmetric(140"));
}
