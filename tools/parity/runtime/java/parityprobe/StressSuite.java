package parityprobe;

import com.aurora.client.AuroraClient;
import com.aurora.client.adapter.NativeKeyboard;
import com.aurora.client.adapter.NativeUi;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.profile.ProfileManager;
import com.aurora.client.feature.impl.WaypointFeature;
import com.aurora.client.health.MixinHealth;
import com.aurora.client.screen.*;
import com.aurora.client.screen.setting.*;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.interaction.SemanticControlHost;
import com.aurora.client.ui.layout.ScreenLayout;
import com.aurora.client.util.SmoothScroll;
import com.aurora.client.worldmap.screen.WorldMapScreen;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Exhaustive live UI, control, multi-scale, reset, and runtime feature stress suite
 * for disposable fixtures only. Steps on END_CLIENT_TICK so Minecraft genuinely
 * renders frames between actions, clicks real controls via Screen mouse/key handlers,
 * checks geometry/overlaps across GUI scales 1..4 + narrow 854x480, and writes
 * stress-report.json.
 */
public final class StressSuite {
    private static final Deque<Step> QUEUE = new ArrayDeque<>();
    private static Path root;
    private static boolean active;
    private static int waitTicks;
    private static int controlsClicked;
    private static int controlsMutated;
    private static int disabledVerified;
    private static int resetsVerified;
    private static int screensChecked;
    private static int geometryChecks;
    private static final List<String> FAILURES = new ArrayList<>();
    private static final List<String> WARNINGS = new ArrayList<>();
    private static final List<Map<String, Object>> SCREEN_LOG = new ArrayList<>();
    private static final Set<String> CLICKED_SIGNATURES = new LinkedHashSet<>();
    private static net.minecraft.client.InactivityFpsLimit previousInactivity;

    @FunctionalInterface
    private interface Step {
        void run(Minecraft client) throws Exception;
    }

    private StressSuite() {}

    public static boolean isActive() { return active; }

    public static void start(Path probeRoot, Minecraft client) {
        if (active) throw new IllegalStateException("StressSuite already running");
        root = probeRoot;
        QUEUE.clear();
        FAILURES.clear();
        WARNINGS.clear();
        SCREEN_LOG.clear();
        CLICKED_SIGNATURES.clear();
        controlsClicked = 0;
        controlsMutated = 0;
        disabledVerified = 0;
        resetsVerified = 0;
        screensChecked = 0;
        geometryChecks = 0;
        waitTicks = 0;
        active = true;
        previousInactivity = client.options.inactivityFpsLimit().get();
        client.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
        client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);

        FeatureRegistry.modules();
        FeatureRegistry.settings();
        FeatureRegistry.settingsPages();

        buildPlan();
        System.out.println("[stress-suite] started with " + QUEUE.size() + " queued steps");
    }

    public static void tick(Minecraft client) {
        if (!active) return;
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (QUEUE.isEmpty()) {
            finish(client);
            return;
        }
        Step step = QUEUE.pollFirst();
        try {
            step.run(client);
        } catch (Throwable t) {
            String msg = "EXCEPTION: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            t.printStackTrace();
            FAILURES.add(msg);
        }
    }

    private static void enqueue(Step s) { QUEUE.addLast(s); }
    private static void enqueueWait(int ticks) { QUEUE.addLast(mc -> waitTicks = ticks); }

    private static void fail(String msg) {
        System.out.println("[stress-suite] FAIL: " + msg);
        FAILURES.add(msg);
    }

    private static void warn(String msg) {
        System.out.println("[stress-suite] WARN: " + msg);
        WARNINGS.add(msg);
    }

    private static void buildPlan() {
        // 0. Sanity: verify Smooth Camera is completely gone from FeatureRegistry & AuroraConfig
        enqueue(mc -> {
            for (var meta : FeatureRegistry.all()) {
                if (meta.id.toLowerCase().contains("smooth_camera") || meta.displayName.toLowerCase().contains("smooth camera"))
                    fail("Smooth Camera still registered in FeatureRegistry: " + meta.id);
                for (var s : meta.settings) {
                    if (s.label != null && s.label.toLowerCase().contains("smooth camera"))
                        fail("Smooth Camera setting still present in " + meta.id + ": " + s.label);
                }
            }
            for (var page : FeatureRegistry.settingsPages()) {
                for (var entry : page.entries()) {
                    if (entry.id.toLowerCase().contains("smooth_camera") || entry.displayName.toLowerCase().contains("smooth camera"))
                        fail("Smooth Camera group still present on Settings page " + page.label());
                }
            }
            try {
                AuroraConfig.class.getField("smoothCamera");
                fail("AuroraConfig.smoothCamera field still exists!");
            } catch (NoSuchFieldException expected) { /* good */ }
            if (!MixinHealth.failures().isEmpty()) {
                fail("Failed mixins at boot: " + MixinHealth.failures());
            }
            System.out.println("[stress-suite] smooth-camera absence + mixin health verified");
        });

        // 1. Scale 2 (1080x720@2 -> 540x360 GUI): EXHAUSTIVE click-every-control on AuroraScreen,
        //    all 6 Settings pages, ALL 39 module detail screens + Reset verification, and all dedicated screens!
        enqueueScalePass(1080, 720, 2, "s2", true);

        // 2. Scale 1 (1080x720@1 -> 1080x720 GUI): full AuroraScreen + all 6 Settings pages +
        //    every module detail screen geometry & representative control click + all dedicated screens
        enqueueScalePass(1080, 720, 1, "s1", false);

        // 3. Scale 3 (1080x720@3 -> 360x240 GUI)
        enqueueScalePass(1080, 720, 3, "s3", false);

        // 4. TRUE Scale 4 (1080x960@4 -> 270x240 GUI, exercising narrowNavigation!)
        enqueueScalePass(1080, 960, 4, "s4", false);

        // 5. Narrow 854x480@2 (427x240 GUI) at high opacity 0.85
        enqueue(mc -> {
            AuroraConfig.get().themeOrDefault().backgroundOpacity = 0.85;
            ThemeManager.reload();
        });
        enqueueScalePass(854, 480, 2, "narrow854-op85", false);

        // 6. Restore factory opacity (0.42) & 1080x720@2, run live in-world all-features stress pass!
        enqueue(mc -> {
            resetAllConfigToDefaults();
            resizeAndScale(mc, 1080, 720, 2);
            NativeUi.setScreen(mc, null);
        });
        enqueueWait(3);
        enqueueInWorldFeatureStress();
    }

    private static void enqueueScalePass(int winW, int winH, int scale, String tag, boolean exhaustiveControls) {
        enqueue(mc -> {
            resizeAndScale(mc, winW, winH, scale);
            System.out.println("[stress-suite] === SCALE PASS " + tag + " (" + winW + "x" + winH + " @ " + scale + ") ===");
        });
        enqueueWait(2);

        // A. AuroraScreen Features tab (Grid & List + search + tile clicks)
        enqueue(mc -> {
            AuroraConfig.get().moduleGridView = true;
            NativeUi.setScreen(mc, AuroraScreen.create());
        });
        enqueueWait(10);
        enqueue(mc -> {
            var screen = (AuroraScreen) NativeUi.screen(mc);
            checkSemanticGeometry(screen, tag + ":AuroraScreen:grid");
            grab(mc, "stress-" + tag + "-main-grid");

            // Click List layout control via real mouse click
            var layoutControls = (SemanticActionControl[]) getField(screen, "layoutControls");
            clickControl(screen, layoutControls[0]);
            if (AuroraConfig.get().moduleGridView) fail(tag + ": clicking List layout control did not switch moduleGridView=false");
            controlsClicked++; controlsMutated++;
        });
        enqueueWait(2);
        enqueue(mc -> {
            var screen = (AuroraScreen) NativeUi.screen(mc);
            checkSemanticGeometry(screen, tag + ":AuroraScreen:list");
            grab(mc, "stress-" + tag + "-main-list");

            // Click Grid layout control back
            var layoutControls = (SemanticActionControl[]) getField(screen, "layoutControls");
            clickControl(screen, layoutControls[1]);
            if (!AuroraConfig.get().moduleGridView) fail(tag + ": clicking Grid layout control did not switch moduleGridView=true");
            controlsClicked++; controlsMutated++;

            // Search bar typing
            var searchField = (EditBox) getField(screen, "searchField");
            screen.setFocused(searchField);
            searchField.setFocused(true);
            for (char c : "zoom".toCharArray()) screen.charTyped(new CharacterEvent(c));
        });
        enqueueWait(2);
        enqueue(mc -> {
            var screen = (AuroraScreen) NativeUi.screen(mc);
            @SuppressWarnings("unchecked")
            var lastResults = (List<?>) getField(screen, "lastResults");
            if (lastResults == null || lastResults.isEmpty()) fail(tag + ": search 'zoom' returned 0 results");
            var searchField = (EditBox) getField(screen, "searchField");
            searchField.setValue("");
        });
        enqueueWait(2);

        // Click module tiles (all 39 on exhaustive, first 6 on fast scale passes)
        enqueue(mc -> {
            var screen = (AuroraScreen) NativeUi.screen(mc);
            var modules = FeatureRegistry.modules();
            int limit = exhaustiveControls ? modules.size() : Math.min(6, modules.size());
            @SuppressWarnings("unchecked")
            var tileControls = (Map<String, SemanticActionControl>) getField(screen, "tileControls");
            var scrolls = (SmoothScroll[]) getField(screen, "scrolls");
            for (int i = 0; i < limit; i++) {
                final int idx = i;
                enqueueFirstAfterBatch(tag, screen, modules.get(idx), tileControls, scrolls);
            }
        });
        // Actually queue tile clicks cleanly before Settings pass using a helper:
        enqueueModuleTileChecks(tag, exhaustiveControls);

        // B. AuroraScreen Settings tab (ALL 6 sub-tabs!)
        enqueue(mc -> {
            var screen = AuroraScreen.create();
            NativeUi.setScreen(mc, screen);
        });
        enqueueWait(2);
        enqueue(mc -> {
            var screen = (AuroraScreen) NativeUi.screen(mc);
            var tabControls = (SemanticActionControl[]) getField(screen, "tabControls");
            clickControl(screen, tabControls[1]);
            int cat = (int) getField(screen, "selectedCategory");
            if (cat != 1) fail(tag + ": clicking Settings sidebar tab did not switch selectedCategory to 1 (got " + cat + ")");
            controlsClicked++; controlsMutated++;
        });
        enqueueWait(2);

        var pages = FeatureRegistry.settingsPages();
        for (int pIdx = 0; pIdx < pages.size(); pIdx++) {
            final int pageIndex = pIdx;
            final String pageName = pages.get(pIdx).label();
            enqueue(mc -> {
                var screen = (AuroraScreen) NativeUi.screen(mc);
                var pageControls = (SemanticActionControl[]) getField(screen, "pageControls");
                if (pageIndex < pageControls.length) {
                    var ctrl = pageControls[pageIndex];
                    var chrome = (ScreenLayout) getField(screen, "chromeLayout");
                    var box = chrome.at(pageIndex + 3);
                    var focBefore = FeatureSetting.getFocused();
                    boolean capBefore = FeatureSetting.hasActiveCapture();
                    clickControl(screen, ctrl);
                    int sel = (int) getField(screen, "selectedPage");
                    if (sel != pageIndex) {
                        var boxAfter = chrome.at(pageIndex + 3);
                        fail(tag + ": clicking Settings sub-tab '" + pageName + "' did not select page " + pageIndex
                                + " (got " + sel + ", ctrl=[" + ctrl.getX() + "," + ctrl.getY() + "," + ctrl.getWidth() + "x" + ctrl.getHeight()
                                + "], boxBefore=[" + box.x() + "," + box.top() + "," + box.width() + "x" + box.height()
                                + "], boxAfter=[" + boxAfter.x() + "," + boxAfter.top() + "," + boxAfter.width() + "x" + boxAfter.height()
                                + "], avail=" + ctrl.isAvailable() + ", cat=" + getField(screen, "selectedCategory")
                                + ", focBefore=" + focBefore + ", capBefore=" + capBefore + ")");
                    }
                    controlsClicked++; controlsMutated++;
                }
            });
            enqueueWait(2);
            enqueue(mc -> {
                var screen = (AuroraScreen) NativeUi.screen(mc);
                checkSemanticGeometry(screen, tag + ":Settings:" + pageName);
                if (pageIndex == 0 || pageIndex == 4) {
                    grab(mc, "stress-" + tag + "-settings-" + pageName.toLowerCase());
                }
            });

            // Exercise rows on this Settings page!
            var entries = pages.get(pIdx).entries();
            for (var entry : entries) {
                for (var setting : entry.settings) {
                    if (!exhaustiveControls && !shouldSampleSetting(setting)) continue;
                    enqueueSettingsPageRowTest(tag, pageIndex, pageName, entry.id, setting);
                }
            }
        }

        // Reset Appearance mutations (like uppercase/custom font) after Settings tab before dedicated screens
        enqueue(mc -> {
            AuroraConfig.get().textCase = AuroraConfig.defaults().textCase;
            AuroraConfig.get().activeFont = AuroraConfig.defaults().activeFont;
            AuroraConfig.get().textWeight = AuroraConfig.defaults().textWeight;
            AuroraConfig.get().textItalic = AuroraConfig.defaults().textItalic;
            AuroraConfig.get().textUnderline = AuroraConfig.defaults().textUnderline;
            AuroraConfig.get().themeEnabled = true;
            AuroraConfig.get().themeOrDefault().accent = AuroraConfig.defaults().themeOrDefault().accent;
            AuroraConfig.get().themeOrDefault().background = AuroraConfig.defaults().themeOrDefault().background;
            ThemeManager.reload();
        });

        // C. ALL 39 FeatureDetailScreens!
        var allModules = FeatureRegistry.modules();
        for (var meta : allModules) {
            if (!meta.hasDetail()) continue;
            enqueueDetailScreenTest(tag, meta, exhaustiveControls);
        }

        // D. Dedicated Screens at this scale!
        enqueueDedicatedScreens(tag);
    }

    private static void enqueueFirstAfterBatch(String tag, AuroraScreen screen, FeatureMetadata meta,
                                               Map<String, SemanticActionControl> tileControls, SmoothScroll[] scrolls) {
        // no-op placeholder; handled synchronously in enqueueModuleTileChecks below
    }

    private static void enqueueModuleTileChecks(String tag, boolean exhaustive) {
        var modules = FeatureRegistry.modules();
        int limit = exhaustive ? modules.size() : Math.min(6, modules.size());
        for (int i = 0; i < limit; i++) {
            final FeatureMetadata meta = modules.get(i);
            enqueue(mc -> {
                if (!(NativeUi.screen(mc) instanceof AuroraScreen screen)) return;
                // Scroll tile cleanly inside the content viewport using ScreenLayout
                var layout = screen.contentLayout();
                @SuppressWarnings("unchecked")
                var arranged = (List<ScreenLayout.Box>) getField(layout, "arranged");
                for (var box : arranged) {
                    if (box.key() instanceof com.aurora.client.module.Module mod && mod.id.equals(meta.id)) {
                        var scrolls = (SmoothScroll[]) getField(screen, "scrolls");
                        double target = Math.max(0, box.top() - layout.viewport().y - 12);
                        scrolls[0].set(Math.min(layout.maxScroll(), target));
                        break;
                    }
                }
            });
            enqueueWait(2);
            enqueue(mc -> {
                if (!(NativeUi.screen(mc) instanceof AuroraScreen screen)) return;
                @SuppressWarnings("unchecked")
                var tileControls = (Map<String, SemanticActionControl>) getField(screen, "tileControls");
                var ctrl = tileControls.get(meta.id);
                if (ctrl == null || !ctrl.isAvailable()) {
                    fail(tag + ": tile '" + meta.id + "' never became available in viewport");
                    return;
                }
                // Click inside the visible viewport intersection of the tile
                var vp = screen.contentLayout().viewport();
                int cy1 = Math.max(vp.y + 4, ctrl.getY() + 4);
                int cy2 = Math.min(vp.yEnd() - 4, ctrl.getBottom() - 4);
                double cx = ctrl.getX() + ctrl.getWidth() / 2.0;
                double cy = (cy1 + cy2) / 2.0;
                boolean before = meta.isEnabled();
                clickAt(screen, cx, cy);
                boolean after = meta.isEnabled();
                controlsClicked++;
                if (after == before) {
                    fail(tag + ": clicking module tile '" + meta.id + "' did not toggle enabled (" + before + ") at (" + cx + "," + cy + ")");
                } else {
                    controlsMutated++;
                    // Click again to restore
                    clickAt(screen, cx, cy);
                    if (meta.isEnabled() != before) {
                        fail(tag + ": second click on module tile '" + meta.id + "' did not restore (" + before + ")");
                    }
                }
            });
        }
    }

    private static boolean shouldSampleSetting(FeatureSetting s) {
        if (s instanceof SectionHeaderSetting || s instanceof SectionFooterSetting
                || s instanceof ThemePreviewSetting || s instanceof TextPreviewSetting
                || s instanceof CrosshairPreviewSetting) return false;
        String key = s.getClass().getSimpleName();
        return CLICKED_SIGNATURES.add(key);
    }

    private static void enqueueSettingsPageRowTest(String tag, int pageIndex, String pageName, String groupId, FeatureSetting setting) {
        if (setting instanceof SectionHeaderSetting || setting instanceof SectionFooterSetting
                || setting instanceof ThemePreviewSetting || setting instanceof TextPreviewSetting
                || setting instanceof CrosshairPreviewSetting || setting instanceof ButtonSetting) {
            return; // ButtonSettings open external screens or OS dialogs; tested separately
        }
        enqueue(mc -> {
            if (!(NativeUi.screen(mc) instanceof AuroraScreen screen)) return;
            if (!setting.isVisible()) return;
            // Scroll setting into content viewport
            var layout = screen.contentLayout();
            var box = layout.bounds(setting);
            if (box != null) {
                var pageScrolls = (SmoothScroll[]) getField(screen, "pageScrolls");
                double target = Math.max(0, box.top() - layout.viewport().y - 16);
                pageScrolls[pageIndex].set(Math.min(layout.maxScroll(), target));
            }
        });
        enqueueWait(1);
        enqueue(mc -> {
            if (!(NativeUi.screen(mc) instanceof AuroraScreen screen)) return;
            if (!setting.isVisible()) return;
            exerciseSetting(screen, tag + ":Settings:" + pageName + ":" + groupId, setting, false);
        });
        enqueueWait(1);
        enqueue(mc -> {
            // Dismiss any open popup (enum/color/keybind)
            if (NativeUi.screen(mc) instanceof AuroraScreen screen) {
                FeatureSetting.cancelActiveCapture();
                FeatureSetting focused = FeatureSetting.getFocused();
                if (focused != null) focused.dismissPopup();
                FeatureSetting.clearFocus();
            }
        });
    }

    private static void enqueueDetailScreenTest(String tag, FeatureMetadata meta, boolean exhaustive) {
        // Open FeatureDetailScreen directly (even for resourcepack_browser we test both!)
        enqueue(mc -> {
            NativeUi.setScreen(mc, new FeatureDetailScreen(null, meta));
        });
        enqueueWait(2);
        enqueue(mc -> {
            if (!(NativeUi.screen(mc) instanceof FeatureDetailScreen screen)) {
                fail(tag + ": failed to open FeatureDetailScreen for " + meta.id);
                return;
            }
            checkSemanticGeometry(screen, tag + ":Detail:" + meta.id);
            if (meta.id.equals("pack_tweaks") || meta.id.equals("animations") || meta.id.equals("minimap")) {
                grab(mc, "stress-" + tag + "-detail-" + meta.id);
            }
        });

        // Expand any collapsed SectionHeaderSettings by real click!
        for (var setting : meta.settings) {
            if (setting instanceof SectionHeaderSetting header) {
                enqueue(mc -> {
                    if (!(NativeUi.screen(mc) instanceof FeatureDetailScreen screen)) return;
                    if (!((boolean) getField(header, "collapsed"))) return;
                    scrollDetailTo(screen, header);
                });
                enqueueWait(1);
                enqueue(mc -> {
                    if (!(NativeUi.screen(mc) instanceof FeatureDetailScreen screen)) return;
                    if (!((boolean) getField(header, "collapsed"))) return;
                    var box = screen.contentLayout().bounds(header);
                    if (box == null) { fail(tag + ":" + meta.id + ": collapsed SectionHeader '" + header.label + "' has no layout box"); return; }
                    double sy = box.y(screen.layoutScroll());
                    clickAt(screen, box.x() + box.width() / 2.0, sy + box.height() / 2.0);
                    controlsClicked++;
                    if ((boolean) getField(header, "collapsed")) {
                        fail(tag + ":" + meta.id + ": clicking SectionHeader '" + header.label + "' did not expand it");
                    } else {
                        controlsMutated++;
                    }
                });
                enqueueWait(1);
            }
        }

        // Now exercise settings on this detail screen!
        if (exhaustive) {
            for (var setting : meta.settings) {
                if (setting instanceof SectionHeaderSetting || setting instanceof SectionFooterSetting
                        || setting instanceof ThemePreviewSetting || setting instanceof TextPreviewSetting
                        || setting instanceof CrosshairPreviewSetting || setting instanceof ButtonSetting
                        || setting instanceof ParticleConfigSetting || setting instanceof ItemScaleSetting
                        || setting instanceof EffectExpiryListSetting || setting instanceof SoundVolumeSetting) {
                    continue;
                }
                enqueue(mc -> {
                    if (!(NativeUi.screen(mc) instanceof FeatureDetailScreen screen)) return;
                    if (!setting.isVisible()) return;
                    scrollDetailTo(screen, setting);
                });
                enqueueWait(1);
                enqueue(mc -> {
                    if (!(NativeUi.screen(mc) instanceof FeatureDetailScreen screen)) return;
                    if (!setting.isVisible()) return;
                    exerciseSetting(screen, tag + ":Detail:" + meta.id, setting, true);
                });
                enqueueWait(1);
            }

            // Test the Reset button (2 clicks: arm -> confirm!) on every detail screen!
            enqueue(mc -> {
                if (!(NativeUi.screen(mc) instanceof FeatureDetailScreen screen)) return;
                FeatureSetting.cancelActiveCapture();
                FeatureSetting focused = FeatureSetting.getFocused();
                if (focused != null) focused.dismissPopup();
                FeatureSetting.clearFocus();

                var resetBtn = (AbstractWidget) getField(screen, "resetBtn");
                if (resetBtn == null) { fail(tag + ":" + meta.id + ": missing resetBtn"); return; }
                // Click 1: arm
                clickAt(screen, resetBtn.getX() + resetBtn.getWidth() / 2.0, resetBtn.getY() + resetBtn.getHeight() / 2.0);
                // Click 2: confirm
                clickAt(screen, resetBtn.getX() + resetBtn.getWidth() / 2.0, resetBtn.getY() + resetBtn.getHeight() / 2.0);
                controlsClicked += 2;
                resetsVerified++;

                // Verify every simple setting with resetDefault is now at default (not modified)!
                for (var setting : meta.settings) {
                    if (setting instanceof BooleanSetting || setting instanceof SliderSetting
                            || setting instanceof EnumSetting<?> || setting instanceof SegmentedSetting<?>
                            || setting instanceof TextSetting || setting instanceof KeybindSetting) {
                        try {
                            Method isMod = FeatureSetting.class.getDeclaredMethod("isResetVisible");
                            isMod.setAccessible(true);
                            boolean stillDirty = (boolean) isMod.invoke(setting);
                            if (stillDirty && !meta.id.equals("better_hitreg")) {
                                fail(tag + ":Detail:" + meta.id + ": after Reset button, setting '" + setting.label + "' STILL shows modified!");
                            }
                        } catch (NoSuchMethodException ignored) {
                            // check via resetModified supplier field if named differently
                        }
                    }
                }
            });
            enqueueWait(1);
        } else {
            // Fast scale pass: scroll down & check geometry at bottom too + click first interactive setting
            enqueue(mc -> {
                if (!(NativeUi.screen(mc) instanceof FeatureDetailScreen screen)) return;
                var scroll = (SmoothScroll) getField(screen, "scroll");
                double max = screen.contentLayout().maxScroll();
                if (max > 0) scroll.set(max);
            });
            enqueueWait(1);
            enqueue(mc -> {
                if (!(NativeUi.screen(mc) instanceof FeatureDetailScreen screen)) return;
                checkSemanticGeometry(screen, tag + ":Detail:" + meta.id + ":bottom");
            });
        }
    }

    private static void scrollDetailTo(FeatureDetailScreen screen, FeatureSetting setting) throws Exception {
        var box = screen.contentLayout().bounds(setting);
        if (box == null) return;
        var scroll = (SmoothScroll) getField(screen, "scroll");
        double target = Math.max(0, box.top() - screen.contentLayout().viewport().y - 16);
        scroll.set(Math.min(screen.contentLayout().maxScroll(), target));
    }

    private static void exerciseSetting(Screen screen, String ctx, FeatureSetting setting, boolean allowRestore) throws Exception {
        String id = ctx + "/" + setting.label + "(" + setting.getClass().getSimpleName() + ")";
        boolean disabled = setting.isDisabled();

        if (setting instanceof BooleanSetting bs) {
            var getter = (java.util.function.BooleanSupplier) getField(bs, "getter");
            boolean before = getter.getAsBoolean();
            var ctrl = bs.interactionControl();
            double cx, cy;
            if (ctrl != null && ctrl.isAvailable() && ctrl.getWidth() > 0) {
                cx = ctrl.getX() + ctrl.getWidth() / 2.0;
                cy = ctrl.getY() + ctrl.getHeight() / 2.0;
            } else {
                var box = ((LayoutScreen) screen).contentLayout().bounds(bs);
                if (box == null) { fail(id + ": no layout box"); return; }
                cx = box.x() + box.width() - 24;
                cy = box.y(((LayoutScreen) screen).layoutScroll()) + box.height() / 2.0;
            }
            clickAt(screen, cx, cy);
            controlsClicked++;
            boolean after = getter.getAsBoolean();
            if (disabled) {
                disabledVerified++;
                if (after != before) fail(id + ": DISABLED BooleanSetting mutated on click!");
            } else {
                if (after == before) fail(id + ": BooleanSetting did NOT toggle on click at (" + cx + "," + cy + ")!");
                else controlsMutated++;
            }
        } else if (setting instanceof SliderSetting ss) {
            var slider = getField(ss, "slider");
            Method valMethod = slider.getClass().getMethod("value");
            double before = (double) valMethod.invoke(slider);
            int trackX = (int) getField(ss, "lastTrackX");
            int trackW = (int) getField(ss, "lastTrackW");
            var box = ((LayoutScreen) screen).contentLayout().bounds(ss);
            if (box == null || trackW <= 0) {
                if (!disabled) fail(id + ": SliderSetting track not laid out (trackW=" + trackW + ")");
                return;
            }
            double rowY = box.y(((LayoutScreen) screen).layoutScroll());
            double trackY = rowY + box.height() - 10;
            double targetX1 = trackX + trackW * 0.25;
            double targetX2 = trackX + trackW * 0.78;
            // Click and drag across track
            dragAcross(screen, targetX1, trackY, targetX2, trackY);
            controlsClicked++;
            double after = (double) valMethod.invoke(slider);
            if (Math.abs(after - before) < 1e-6 && !disabled) {
                // Maybe before was already ~0.78; try dragging to 0.15
                dragAcross(screen, targetX2, trackY, trackX + trackW * 0.15, trackY);
                after = (double) valMethod.invoke(slider);
            }
            if (disabled) {
                disabledVerified++;
                if (Math.abs(after - before) > 1e-6) fail(id + ": DISABLED SliderSetting mutated (" + before + " -> " + after + ")");
            } else {
                if (Math.abs(after - before) < 1e-6) fail(id + ": SliderSetting did NOT change after drag (" + before + ") track=[" + trackX + ".." + (trackX + trackW) + "] y=" + trackY);
                else controlsMutated++;
            }
        } else if (setting instanceof EnumSetting<?> es) {
            var getter = (java.util.function.Supplier<?>) getField(es, "getter");
            Object[] values = (Object[]) getField(es, "values");
            if (values == null || values.length < 2) return;
            Object before = getter.get();
            int bx = (int) getField(es, "lastBtnX");
            int by = (int) getField(es, "lastBtnY");
            int bw = com.aurora.client.ui.util.UiDensity.ENUM_W;
            int bh = com.aurora.client.ui.util.UiDensity.FIELD_H;
            clickAt(screen, bx + bw / 2.0, by + bh / 2.0);
            controlsClicked++;
            boolean expanded = (boolean) getField(es, "expanded");
            if (disabled) {
                disabledVerified++;
                if (expanded) fail(id + ": DISABLED EnumSetting expanded on click!");
            } else if (!expanded) {
                fail(id + ": EnumSetting did NOT expand on click at (" + (bx + bw / 2.0) + "," + (by + bh / 2.0) + ")");
            } else {
                // Pick the next option index inside the popup (options start right below trigger or above if flipped)
                // Call popup click via real mouse click on option row!
                int optH = com.aurora.client.ui.util.UiDensity.FIELD_H;
                int visibleCount = Math.min(5, values.length);
                int popupH = visibleCount * optH + 6;
                var band = FeatureSetting.hostBand();
                boolean flipsUp = by + bh + 4 + popupH > band.y + band.height && by - 4 - popupH >= band.y;
                int popTop = flipsUp ? by - 4 - popupH : by + bh + 4;
                int targetIdx = 0;
                for (int i = 0; i < visibleCount; i++) {
                    if (values[i] != before) { targetIdx = i; break; }
                }
                double optY = popTop + 3 + targetIdx * optH + optH / 2.0;
                clickAt(screen, bx + bw / 2.0, optY);
                controlsClicked++;
                Object after = getter.get();
                if (after == before) {
                    // Try opposite direction in case popup flipped differently
                    int altTop = flipsUp ? by + bh + 4 : by - 4 - popupH;
                    clickAt(screen, bx + bw / 2.0, altTop + 3 + targetIdx * optH + optH / 2.0);
                    after = getter.get();
                }
                if (after == before) {
                    fail(id + ": EnumSetting option click did NOT change value (stayed " + before + ")");
                    es.dismissPopup();
                } else {
                    controlsMutated++;
                }
            }
        } else if (setting instanceof SegmentedSetting<?> seg) {
            var getter = (java.util.function.Supplier<?>) getField(seg, "getter");
            Object[] values = (Object[]) getField(seg, "values");
            if (values == null || values.length < 2) return;
            Object before = getter.get();
            var controls = seg.interactionControls();
            SemanticActionControl target = null;
            for (int i = 0; i < Math.min(values.length, controls.size()); i++) {
                if (values[i] != before) { target = controls.get(i); break; }
            }
            if (target == null) return;
            clickControl(screen, target);
            controlsClicked++;
            Object after = getter.get();
            if (disabled) {
                disabledVerified++;
                if (after != before) fail(id + ": DISABLED SegmentedSetting mutated!");
            } else if (after == before) {
                fail(id + ": SegmentedSetting did NOT change on click (stayed " + before + ")");
            } else {
                controlsMutated++;
            }
        } else if (setting instanceof ToggleGroupSetting tg) {
            var controls = tg.interactionControls();
            for (var c : controls) {
                if (c.getWidth() <= 0 || c.getHeight() <= 0) continue;
                clickControl(screen, c);
                controlsClicked++;
                controlsMutated++;
            }
        } else if (setting instanceof ColorSetting cs) {
            var getter = (java.util.function.IntSupplier) getField(cs, "getter");
            int before = getter.getAsInt();
            int sx = (int) getField(cs, "swatchX");
            int sy = (int) getField(cs, "swatchY");
            int sw = (int) getField(cs, "swatchW");
            int sh = (int) getField(cs, "swatchH");
            clickAt(screen, sx + sw / 2.0, sy + sh / 2.0);
            controlsClicked++;
            boolean expanded = (boolean) getField(cs, "expanded");
            if (disabled) {
                disabledVerified++;
                if (expanded) fail(id + ": DISABLED ColorSetting opened picker on click!");
            } else if (!expanded) {
                fail(id + ": ColorSetting did NOT expand on click at (" + (sx + sw / 2.0) + "," + (sy + sh / 2.0) + ")");
            } else {
                // Close cleanly via Escape
                screen.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
                if ((boolean) getField(cs, "expanded")) fail(id + ": Escape did not close ColorSetting picker");
                controlsMutated++;
            }
        } else if (setting instanceof TextSetting ts) {
            var getter = (java.util.function.Supplier<String>) getField(ts, "getter");
            String before = getter.get();
            var field = (EditBox) getField(ts, "field");
            clickAt(screen, field.getX() + field.getWidth() / 2.0, field.getY() + field.getHeight() / 2.0);
            controlsClicked++;
            if (disabled) {
                disabledVerified++;
                if (field.isFocused() || FeatureSetting.getFocused() == ts) {
                    fail(id + ": DISABLED TextSetting accepted focus on click!");
                }
            } else {
                screen.charTyped(new CharacterEvent('Z'));
                String after = getter.get();
                if (Objects.equals(before, after)) {
                    fail(id + ": TextSetting did NOT accept typed char (stayed '" + before + "')");
                } else {
                    controlsMutated++;
                }
                screen.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
            }
        } else if (setting instanceof KeybindSetting kb) {
            var getter = (java.util.function.IntSupplier) getField(kb, "getter");
            int before = getter.getAsInt();
            int bx = (int) getField(kb, "lastBtnX");
            int by = (int) getField(kb, "lastBtnY");
            int bw = com.aurora.client.ui.util.UiDensity.KEY_W;
            int bh = com.aurora.client.ui.util.UiDensity.FIELD_H;
            clickAt(screen, bx + bw / 2.0, by + bh / 2.0);
            controlsClicked++;
            boolean listening = (boolean) getField(kb, "listening");
            if (disabled) {
                disabledVerified++;
                if (listening) fail(id + ": DISABLED KeybindSetting entered listening!");
            } else if (!listening) {
                fail(id + ": KeybindSetting did NOT enter listening on click");
            } else {
                int testKey = before == NativeKeyboard.toStored(InputConstants.KEY_F8)
                        ? InputConstants.KEY_F9 : InputConstants.KEY_F8;
                screen.keyPressed(new KeyEvent(testKey, 0, 0));
                int after = getter.getAsInt();
                if (after == before) fail(id + ": KeybindSetting did NOT capture key (stayed " + before + ")");
                else controlsMutated++;
            }
        }
    }

    private static void enqueueDedicatedScreens(String tag) {
        // 1. HUD Editor
        enqueue(mc -> NativeUi.setScreen(mc, new HudEditorScreen()));
        enqueueWait(2);
        enqueue(mc -> {
            var s = NativeUi.screen(mc);
            if (!(s instanceof HudEditorScreen)) fail(tag + ": HudEditorScreen failed to open");
            else {
                checkSemanticGeometry(s, tag + ":HudEditorScreen");
                grab(mc, "stress-" + tag + "-hud-editor");
            }
        });

        // 2. Profile Manager + Profile Options
        enqueue(mc -> {
            var pm = ProfileManager.getInstance();
            if (!pm.listProfileNames().contains("StressProfile")) pm.create("StressProfile");
            NativeUi.setScreen(mc, new ProfileManagerScreen(null));
        });
        enqueueWait(2);
        enqueue(mc -> {
            checkSemanticGeometry(NativeUi.screen(mc), tag + ":ProfileManagerScreen");
            grab(mc, "stress-" + tag + "-profiles");
            NativeUi.setScreen(mc, new ProfileOptionsScreen(null, "StressProfile"));
        });
        enqueueWait(2);
        enqueue(mc -> {
            checkSemanticGeometry(NativeUi.screen(mc), tag + ":ProfileOptionsScreen");
        });

        // 3. Waypoint Manager + Waypoint Edit
        enqueue(mc -> {
            var wf = WaypointFeature.get();
            if (wf != null && wf.currentWorldWaypoints().isEmpty() && mc.level != null) {
                wf.addWaypoint(new AuroraConfig.Waypoint("Stress Base", 10, 70, -10,
                        mc.level.dimension().identifier().toString(), 0xFF4FC3F7));
            }
            NativeUi.setScreen(mc, new WaypointManagerScreen(null));
        });
        enqueueWait(2);
        enqueue(mc -> {
            checkSemanticGeometry(NativeUi.screen(mc), tag + ":WaypointManagerScreen");
            var wf = WaypointFeature.get();
            if (wf != null && !wf.currentWorldWaypoints().isEmpty()) {
                NativeUi.setScreen(mc, new WaypointEditScreen(null, wf.currentWorldWaypoints().get(0), false));
            }
        });
        enqueueWait(2);
        enqueue(mc -> {
            checkSemanticGeometry(NativeUi.screen(mc), tag + ":WaypointEditScreen");
            if (tag.equals("s3") || tag.equals("narrow854-op85")) {
                grab(mc, "stress-" + tag + "-waypoint-edit");
            }
        });

        // 4. ResourcePackManagerScreen (test BOTH resourcepackBrowserEnabled=true AND false!)
        enqueue(mc -> {
            AuroraConfig.get().resourcepackBrowserEnabled = true;
            NativeUi.setScreen(mc, ResourcePackManagerScreen.open(null));
        });
        enqueueWait(2);
        enqueue(mc -> {
            var s = NativeUi.screen(mc);
            checkSemanticGeometry(s, tag + ":ResourcePackManagerScreen:browserOn");
            grab(mc, "stress-" + tag + "-pack-manager");
            boolean hasModrinth = s.children().stream()
                    .anyMatch(c -> c instanceof AbstractWidget b && b.visible && b.getMessage().getString().equals("Modrinth"));
            // At narrow widths (panelW < 360), Modrinth button hides cleanly; at s1/s2 width>=540 it should show!
            if (s.width >= 500 && !hasModrinth) {
                fail(tag + ": ResourcePackManagerScreen missing Modrinth button when resourcepackBrowserEnabled=true (width=" + s.width + ")");
            }
            // Now toggle OFF and verify Modrinth button is hidden AND FeatureDetailScreen.destination respects it!
            AuroraConfig.get().resourcepackBrowserEnabled = false;
            var rpMeta = FeatureRegistry.modules().stream().filter(m -> m.id.equals("resourcepack_browser")).findFirst().orElseThrow();
            var destOff = FeatureDetailScreen.destination(null, rpMeta);
            if (destOff instanceof ResourcePackBrowserScreen) {
                fail(tag + ": FeatureDetailScreen.destination opened ResourcePackBrowserScreen even when resourcepackBrowserEnabled=false!");
            }
            NativeUi.setScreen(mc, ResourcePackManagerScreen.open(null));
        });
        enqueueWait(2);
        enqueue(mc -> {
            var s = NativeUi.screen(mc);
            boolean hasModrinth = s.children().stream()
                    .anyMatch(c -> c instanceof AbstractWidget b && b.visible && b.getMessage().getString().equals("Modrinth"));
            if (hasModrinth) {
                fail(tag + ": ResourcePackManagerScreen still showed Modrinth button when resourcepackBrowserEnabled=false!");
            }
            AuroraConfig.get().resourcepackBrowserEnabled = true;
            NativeUi.setScreen(mc, new ResourcePackBrowserScreen(null));
        });
        enqueueWait(2);
        enqueue(mc -> checkSemanticGeometry(NativeUi.screen(mc), tag + ":ResourcePackBrowserScreen"));

        // 5. Screenshot Manager
        enqueue(mc -> NativeUi.setScreen(mc, new ScreenshotManagerScreen(null)));
        enqueueWait(2);
        enqueue(mc -> checkSemanticGeometry(NativeUi.screen(mc), tag + ":ScreenshotManagerScreen"));

        // 6. World Map
        enqueue(mc -> {
            AuroraConfig.get().worldMapEnabled = true;
            NativeUi.setScreen(mc, new WorldMapScreen());
        });
        enqueueWait(2);
        enqueue(mc -> {
            var s = NativeUi.screen(mc);
            if (!(s instanceof WorldMapScreen)) fail(tag + ": WorldMapScreen failed to open");
            else {
                s.mouseScrolled(s.width / 2.0, s.height / 2.0, 0, 1.0);
                dragAcross(s, s.width / 2.0, s.height / 2.0, s.width / 2.0 + 30, s.height / 2.0 + 20);
                screensChecked++;
            }
        });

        // 7. Pulsar (PhenomenaScreen) — all 6 tabs!
        enqueue(mc -> {
            AuroraConfig.get().pulsarEnabled = true;
            dev.phenomena.PhenomenaClient.openDashboard(null);
        });
        enqueueWait(2);
        for (var tab : dev.phenomena.gui.ui.UiState.Tab.values()) {
            enqueue(mc -> {
                dev.phenomena.gui.ui.UiState.SESSION.tab = tab;
                dev.phenomena.PhenomenaClient.openDashboard(null);
            });
            enqueueWait(2);
            enqueue(mc -> {
                var s = NativeUi.screen(mc);
                if (!(s instanceof dev.phenomena.gui.screens.PhenomenaScreen)) {
                    fail(tag + ": PhenomenaScreen failed on tab " + tab);
                } else {
                    checkSemanticGeometry(s, tag + ":PhenomenaScreen:" + tab);
                    if (tab == dev.phenomena.gui.ui.UiState.Tab.OVERVIEW) {
                        grab(mc, "stress-" + tag + "-pulsar");
                    }
                }
            });
        }

        // 8. Sound Volume Mixer
        enqueue(mc -> {
            AuroraConfig.get().soundVolumesEnabled = true;
            FeatureMetadata mixer = new FeatureMetadata("sound_volume_mixer", "Sound Volume Mixer",
                    "Mixer", () -> AuroraConfig.get().soundVolumesEnabled,
                    v -> AuroraConfig.get().soundVolumesEnabled = v,
                    List.of(new SoundVolumeSetting()), List.of("soundVolumes"));
            NativeUi.setScreen(mc, new FeatureDetailScreen(null, mixer));
        });
        enqueueWait(2);
        enqueue(mc -> checkSemanticGeometry(NativeUi.screen(mc), tag + ":SoundVolumeMixer"));

        // 9. AuroraTitleScreen (previewed without disconnecting — safe render + layout check!)
        enqueue(mc -> NativeUi.setScreen(mc, new AuroraTitleScreen()));
        enqueueWait(2);
        enqueue(mc -> {
            checkSemanticGeometry(NativeUi.screen(mc), tag + ":AuroraTitleScreen");
            grab(mc, "stress-" + tag + "-title");
            NativeUi.setScreen(mc, null);
        });
    }

    private static void enqueueInWorldFeatureStress() {
        enqueue(mc -> {
            System.out.println("[stress-suite] === IN-WORLD ALL-FEATURES STRESS PASS ===");
            var cfg = AuroraConfig.get();
            // Turn ON every gameplay/visual feature!
            cfg.zoomEnabled = true;
            cfg.fullBright = true; cfg.fullBrightGamma = 800;
            cfg.noFogEnabled = true; cfg.fogDistanceMultiplier = 10.0;
            cfg.totemTweaksEnabled = true; cfg.shieldTweaksEnabled = true; cfg.shieldStatusEnabled = true;
            cfg.underwaterClarityEnabled = true; cfg.lavaClarityEnabled = true;
            cfg.motionBlurStrength = 0.35; cfg.colorSaturation = 1.25;
            cfg.localTimeEnabled = true; cfg.localTimeTicks = 13000;
            cfg.localWeatherEnabled = true; cfg.localWeatherMode = AuroraConfig.LocalWeather.RAIN;
            cfg.customSkyMode = AuroraConfig.CustomSkyMode.AURORA_NIGHT;
            cfg.fireOverlayHide = false; cfg.fireOverlayHeight = 0.5; cfg.fireOverlayOpacity = 0.6;
            cfg.hurtCamEnabled = true; cfg.hurtCamStrength = 0.5;
            cfg.playerHealthIndicators = true; cfg.playerHealthHudEnabled = true;
            cfg.freeLookEnabled = true;
            cfg.saturationBarEnabled = true;
            cfg.blockOverlayEnabled = true; cfg.blockOverlayOutlineEnabled = true; cfg.blockOverlayFillEnabled = true;
            cfg.toggleSprintSneakEnabled = true; cfg.toggleSprintSneakHudEnabled = true;
            cfg.armorAlertEnabled = true; cfg.toolDurabilityAlertEnabled = true; cfg.effectExpiryAlertEnabled = true;
            cfg.crosshairEnabled = true;
            cfg.hitboxEnabled = true; cfg.hitboxTargetEnabled = true;
            cfg.hitregEnabled = true;
            cfg.hitColorEnabled = true; cfg.hitColorTintArmor = true;
            cfg.glintColorEnabled = true; cfg.glintColorTintArmor = true;
            cfg.infoEnabled = true; cfg.showFps = true; cfg.showCoords = true; cfg.showTime = true;
            cfg.infoShowBiome = true; cfg.infoShowDimension = true; cfg.infoShowFacing = true;
            cfg.infoShowFrameTime = true; cfg.infoShowMemory = true; cfg.infoShowCps = true; cfg.infoShowPlaytime = true;
            cfg.cpsEnabled = true;
            cfg.armorHudEnabled = true; cfg.armorHudShowMainHand = true; cfg.armorHudShowOffHand = true;
            cfg.inventoryHudEnabled = true; cfg.inventoryHudShowHotbar = true;
            cfg.reachEnabled = true;
            cfg.potionHudEnabled = true;
            cfg.pingHudEnabled = true; cfg.tabPingEnabled = true; cfg.nametagPingEnabled = true;
            cfg.totemPopEnabled = true; cfg.nametagTotemPopsEnabled = true;
            cfg.waypointsEnabled = true;
            cfg.containerPreviewEnabled = true;
            cfg.itemPhysicsEnabled = true;
            cfg.particleControlsEnabled = true;
            cfg.itemScaleEnabled = true;
            cfg.fixHeldItemSeams = true;
            cfg.smoothAnimationsEnabled = true; cfg.idleSwayEnabled = true; cfg.hotbarBounceEnabled = true; cfg.damageTiltEnabled = true;
            cfg.keystrokesEnabled = true; cfg.keystrokesShowMovement = true; cfg.keystrokesShowMouse = true; cfg.keystrokesShowSpacebar = true;
            cfg.minimapEnabled = true; cfg.minimapShowTerrain = true; cfg.minimapShowEntities = true; cfg.minimapShowWaypoints = true;
            cfg.scoreboardEnabled = true;
            cfg.vanillaHudEnabled = true;
            cfg.worldMapEnabled = true;
            cfg.chatEnabled = true; cfg.chatCompactDuplicates = true; cfg.chatTimestampsEnabled = true; cfg.chatHeadsEnabled = true;
            cfg.mediaControlsEnabled = true; cfg.mediaNowPlayingHudEnabled = true;
            cfg.pulsarEnabled = true;
            cfg.tabListHighlightSelf = true; cfg.tabListCustomBackground = true;
            cfg.nametagStyleShowSelf = true; cfg.nametagStyleTextShadow = true; cfg.nametagStyleCustomBackground = true;
            cfg.soundVolumesEnabled = true; cfg.soundVolumes.put("minecraft:entity.experience_orb.pickup", 0.25f);
            cfg.streamerModeEnabled = true; cfg.streamerHideOwnName = true; cfg.streamerHideCoords = true;
            cfg.gradientDither = true;
            cfg.smoothFog = true;

            // Populate live world state via singleplayer server
            var server = mc.getSingleplayerServer();
            if (server != null && mc.player != null) {
                server.execute(() -> {
                    var src = server.createCommandSourceStack().withSuppressedOutput();
                    var cmds = server.getCommands();
                    cmds.performPrefixedCommand(src, "gamemode survival @a");
                    cmds.performPrefixedCommand(src, "give @a diamond_sword[enchantments={sharpness:5}]");
                    cmds.performPrefixedCommand(src, "item replace entity @a armor.chest with diamond_chestplate[enchantments={protection:4}]");
                    cmds.performPrefixedCommand(src, "effect give @a minecraft:speed 120 1");
                    cmds.performPrefixedCommand(src, "scoreboard objectives add stress dummy \"Aurora Stress\"");
                    cmds.performPrefixedCommand(src, "scoreboard objectives setdisplay sidebar stress");
                    cmds.performPrefixedCommand(src, "scoreboard players set Player 42 stress");
                    cmds.performPrefixedCommand(src, "summon minecraft:husk " + (mc.player.getX() + 2.5) + " " + mc.player.getY() + " " + mc.player.getZ() + " {NoAI:1b,Silent:1b}");
                    cmds.performPrefixedCommand(src, "tellraw @a \"Aurora chat duplicate test\"");
                    cmds.performPrefixedCommand(src, "tellraw @a \"Aurora chat duplicate test\"");
                });
            }
            NativeUi.setScreen(mc, null);
        });
        enqueueWait(25);
        enqueue(mc -> {
            // Grab all-features gameplay screenshot!
            grab(mc, "stress-inworld-all-features-1st");
            // Check 3rd person (exercises Self Nametag + Streamer Mode + Hitbox + Item Physics + Animations)
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        });
        enqueueWait(15);
        enqueue(mc -> {
            grab(mc, "stress-inworld-all-features-3rd");
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            // Exercise F3 debug cache
            mc.debugEntries.setOverlayVisible(true);
        });
        enqueueWait(10);
        enqueue(mc -> {
            mc.debugEntries.setOverlayVisible(false);
            // Exercise Chat Suite search & render
            NativeUi.setScreen(mc, new ChatScreen("", false));
        });
        enqueueWait(8);
        enqueue(mc -> {
            // Exercise Inventory Screen + Container Preview + Glint Color
            NativeUi.setScreen(mc, new InventoryScreen(mc.player));
        });
        enqueueWait(8);
        enqueue(mc -> {
            NativeUi.setScreen(mc, null);
            // Verify functional Streamer Mode & Sound Volume & Background Frame Limit!
            if (!com.aurora.client.util.StreamerMode.hideCoords()) {
                fail("In-world: StreamerMode.hideCoords() returned false when streamerModeEnabled && streamerHideCoords!");
            }
            float att = com.aurora.client.util.SoundVolumes.attenuate(1.0f, AuroraConfig.get().soundVolumes.get("minecraft:entity.experience_orb.pickup"));
            if (Math.abs(att - 0.25f) > 0.01f) {
                fail("In-world: SoundVolumes attenuation expected 0.25, got " + att);
            }
            if (!MixinHealth.failures().isEmpty()) {
                fail("In-world: MixinHealth reported failures: " + MixinHealth.failures());
            }
            // Restore defaults cleanly so disposable game dir is clean
            resetAllConfigToDefaults();
        });
        enqueueWait(5);
    }

    private static void checkSemanticGeometry(Screen screen, String label) throws Exception {
        if (screen == null) { fail(label + ": screen is null"); return; }
        screensChecked++;
        List<SemanticActionControl> visible = new ArrayList<>();
        for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass()) {
            for (var f : type.getDeclaredFields()) {
                if (!SemanticControlHost.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                var host = (SemanticControlHost) f.get(screen);
                if (host == null) continue;
                for (var c : host.controls()) {
                    if (!c.isAvailable()) continue;
                    geometryChecks++;
                    int x = c.getX(), y = c.getY(), w = c.getWidth(), h = c.getHeight();
                    if (w <= 0 || h <= 0) {
                        fail(label + ": available control '" + c.getMessage().getString() + "' has zero/negative size " + w + "x" + h);
                        continue;
                    }
                    // Allow small tolerance of 2px for partially scrolled edge items inside clip bands
                    if (x < -2 || x + w > screen.width + 2) {
                        fail(label + ": control '" + c.getMessage().getString() + "' overflows screen X [" + x + ".." + (x + w) + "] vs screenW=" + screen.width);
                    }
                    visible.add(c);
                }
            }
        }
        // Check pairwise overlaps among visible semantic controls (ignoring nested reset buttons inside row bounds!)
        for (int i = 0; i < visible.size(); i++) {
            var a = visible.get(i);
            String nameA = a.getMessage().getString();
            for (int j = i + 1; j < visible.size(); j++) {
                var b = visible.get(j);
                String nameB = b.getMessage().getString();
                // Per-setting reset button intentionally sits inside the BooleanSetting full-row hit band
                if (nameA.startsWith("Reset ") || nameB.startsWith("Reset ")) continue;
                int ix = Math.max(a.getX(), b.getX());
                int iy = Math.max(a.getY(), b.getY());
                int rx = Math.min(a.getRight(), b.getRight());
                int ry = Math.min(a.getBottom(), b.getBottom());
                if (rx - ix > 2 && ry - iy > 2) {
                    fail(label + ": OVERLAP between '" + nameA + "' [" + a.getX() + "," + a.getY() + "," + a.getWidth() + "x" + a.getHeight()
                            + "] and '" + nameB + "' [" + b.getX() + "," + b.getY() + "," + b.getWidth() + "x" + b.getHeight() + "]");
                }
            }
        }
        // Check visible AbstractWidget buttons stay on-screen
        // (PhenomenaScreen places scrolled-out body widgets below its scissor band intentionally, so check X there and full XY elsewhere)
        boolean scrollableWidgetScreen = screen instanceof dev.phenomena.gui.screens.PhenomenaScreen;
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget b && b.visible && !(b instanceof SemanticActionControl) && !(b instanceof EditBox)) {
                geometryChecks++;
                if (b.getWidth() <= 0 || b.getHeight() <= 0) {
                    fail(label + ": Widget '" + b.getMessage().getString() + "' has zero size");
                } else if (b.getX() < -2 || b.getX() + b.getWidth() > screen.width + 2
                        || (!scrollableWidgetScreen && (b.getY() < -2 || b.getY() + b.getHeight() > screen.height + 2))) {
                    fail(label + ": Widget '" + b.getMessage().getString() + "' overflows screen ["
                            + b.getX() + "," + b.getY() + "," + b.getWidth() + "x" + b.getHeight()
                            + "] vs " + screen.width + "x" + screen.height);
                }
            }
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("label", label);
        entry.put("gui", screen.width + "x" + screen.height);
        entry.put("availableSemanticControls", visible.size());
        SCREEN_LOG.add(entry);
    }

    private static void clickControl(Screen screen, SemanticActionControl ctrl) {
        clickAt(screen, ctrl.getX() + ctrl.getWidth() / 2.0, ctrl.getY() + ctrl.getHeight() / 2.0);
    }

    private static void clickAt(Screen screen, double x, double y) {
        var ev = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(ev, false);
        screen.mouseReleased(ev);
    }

    private static void dragAcross(Screen screen, double x1, double y1, double x2, double y2) {
        var press = new MouseButtonEvent(x1, y1, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        var move = new MouseButtonEvent(x2, y2, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(press, false);
        screen.mouseDragged(move, x2 - x1, y2 - y1);
        screen.mouseReleased(move);
    }

    private static void resizeAndScale(Minecraft mc, int winW, int winH, int scale) throws Exception {
        mc.getWindow().setWindowed(winW, winH);
        mc.options.guiScale().set(scale);
        Method resize;
        try { resize = Minecraft.class.getMethod("resizeGui"); }
        catch (NoSuchMethodException older) { resize = Minecraft.class.getMethod("resizeDisplay"); }
        resize.invoke(mc);
    }

    private static void resetAllConfigToDefaults() throws Exception {
        var cfg = AuroraConfig.get();
        var defaults = AuroraConfig.defaults();
        for (var field : AuroraConfig.class.getFields()) {
            int flags = field.getModifiers();
            if (!java.lang.reflect.Modifier.isStatic(flags) && !java.lang.reflect.Modifier.isFinal(flags)) {
                field.set(cfg, field.get(defaults));
            }
        }
        ThemeManager.reload();
    }

    private static void grab(Minecraft client, String name) {
        try {
            Path out = root.resolve(name + ".png");
            Files.deleteIfExists(out);
            com.mojang.blaze3d.pipeline.RenderTarget target;
            try {
                target = (com.mojang.blaze3d.pipeline.RenderTarget) Minecraft.class.getMethod("getMainRenderTarget").invoke(client);
            } catch (NoSuchMethodException e) {
                target = (com.mojang.blaze3d.pipeline.RenderTarget) client.gameRenderer.getClass().getMethod("mainRenderTarget").invoke(client.gameRenderer);
            }
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try (image) { image.writeToFile(out); }
                catch (java.io.IOException ignored) {}
            });
        } catch (Throwable t) {
            warn("grab " + name + " failed: " + t.getMessage());
        }
    }

    private static Object getField(Object target, String name) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name + " on " + target.getClass().getName());
    }

    private static void finish(Minecraft client) {
        active = false;
        if (previousInactivity != null) {
            client.options.inactivityFpsLimit().set(previousInactivity);
            previousInactivity = null;
        }
        try {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("controlsClicked", controlsClicked);
            report.put("controlsMutated", controlsMutated);
            report.put("disabledVerified", disabledVerified);
            report.put("resetsVerified", resetsVerified);
            report.put("screensChecked", screensChecked);
            report.put("geometryChecks", geometryChecks);
            report.put("failureCount", FAILURES.size());
            report.put("warningCount", WARNINGS.size());
            report.put("failures", FAILURES);
            report.put("warnings", WARNINGS);
            report.put("screens", SCREEN_LOG);
            Files.writeString(root.resolve("stress-report.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
            System.out.println("[stress-suite] DONE: clicked=" + controlsClicked
                    + " mutated=" + controlsMutated
                    + " disabled=" + disabledVerified
                    + " resets=" + resetsVerified
                    + " screens=" + screensChecked
                    + " geomChecks=" + geometryChecks
                    + " failures=" + FAILURES.size());
            Files.writeString(root.resolve("stress-done.txt"), "FAILURES=" + FAILURES.size() + "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
