package guiprobe;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.profile.ProfileManager;
import com.aurora.client.feature.impl.WaypointFeature;
import com.aurora.client.screen.ColorPickerScreen;
import com.aurora.client.screen.FeatureMetadata;
import com.aurora.client.screen.FeatureRegistry;
import com.aurora.client.screen.HudEditorScreen;
import com.aurora.client.theme.ThemeManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.List;

import static guiprobe.GuiAudit.*;

/**
 * Native persistence acceptance across real relaunches (disposable fixture).
 * Phase 1 changes settings through the same APIs the UI uses and quits
 * normally; phase 2 verifies after relaunch and changes them again; phase 3
 * verifies those. Values are compared against fixed expectations.
 */
final class PersistAudit {
    static final int GREEN = 0xFF33CC66, BLUE_ACCENT = 0xFF2F80ED, TEAL_ACCENT = 0xFF14B8A6;

    static FeatureMetadata meta(String id) {
        return FeatureRegistry.all().stream().filter(m -> m.id.equals(id)).findFirst().orElseThrow();
    }

    static void clickWidget(String label) {
        for (var child : mc.screen.children()) {
            if (child instanceof AbstractWidget w && w.getMessage().getString().equals(label)) {
                var ev = new MouseButtonEvent(w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0, new MouseButtonInfo(0, 0));
                var screen = mc.screen; // Apply/Cancel close the screen inside mouseClicked
                screen.mouseClicked(ev, false);
                screen.mouseReleased(ev);
                return;
            }
        }
        throw new IllegalStateException("no widget " + label + " on " + mc.screen);
    }

    static void setHsla(Object picker, float h, float s, float l, float a) {
        field(picker, "hue", h); field(picker, "sat", s); field(picker, "lit", l); field(picker, "alpha", a);
    }

    static KeyMapping zoomMapping() {
        for (KeyMapping k : mc.options.keyMappings) if (k.getName().equals("key.aurora.zoom")) return k;
        throw new IllegalStateException("zoom mapping");
    }

    static void plan(int phase) {
        FeatureRegistry.modules(); FeatureRegistry.settings();
        var cfg = AuroraConfig.get();
        var pm = ProfileManager.getInstance();
        if (phase == 1) {
            actions.add(() -> { meta("full_bright").setEnabled(true); AuroraConfig.save(); });
            actions.add(() -> { cfg.zoomKey = GLFW.GLFW_KEY_X; AuroraConfig.save(); });           // KeybindSetting path
            actions.add(() -> { cfg.theme.accent = BLUE_ACCENT; ThemeManager.reload(); AuroraConfig.save(); });
            actions.add(() -> mc.setScreen(new ColorPickerScreen(null, "Outline", cfg.blockOverlayOutlineColor,
                    v -> { cfg.blockOverlayOutlineColor = v; AuroraConfig.save(); })));
            actions.add(() -> { setHsla(mc.screen, 0.4f, 0.6f, 0.5f, 1f); clickWidget("Apply"); });
            actions.add(() -> check("picker-apply-live", cfg.blockOverlayOutlineColor != 0xFF000000));
            actions.add(() -> mc.setScreen(new ColorPickerScreen(null, "Hit", cfg.hitColor,
                    v -> { cfg.hitColor = v; AuroraConfig.save(); })));
            actions.add(() -> { setHsla(mc.screen, 0.6f, 1f, 0.5f, 1f); clickWidget("Cancel"); });
            actions.add(() -> check("picker-cancel-live", cfg.hitColor == 0x66FF0000));
            actions.add(() -> { pm.create("Persist Test"); pm.switchTo("Persist Test"); cfg.zoomSmoothness = 0.55; AuroraConfig.save(); pm.saveCurrent(); });
            actions.add(() -> {
                var ping = AuroraClient.modules().get("ping"); ping.offsetX = 300; ping.offsetY = 200;
                mc.setScreen(new HudEditorScreen());
            });
            actions.add(() -> { invoke(mc.screen, "resetLayouts", new Class<?>[]{}); mc.setScreen(null); });  // Reset Positions
            actions.add(() -> {
                AuroraClient.modules().get("cps").offsetX = 120;
                AuroraClient.captureLayouts(AuroraClient.modules()); AuroraConfig.save();
            });
            actions.add(() -> {
                var w = new AuroraConfig.Waypoint("Persist marker", 11, 70, -22, mc.level.dimension().identifier().toString(), 0xFF00FFAA);
                w.group = "Acceptance"; WaypointFeature.get().addWaypoint(w);
            });
            actions.add(() -> { for (int i = 0; i <= 300; i++) { cfg.zoomLevel = 1 + i * 0.01; AuroraConfig.save(); } cfg.zoomLevel = 7.25; AuroraConfig.save(); });
            actions.add(() -> check("keybind-reconciled-live", zoomMapping().saveString().equals("key.keyboard.x")));
        } else if (phase == 2) {
            actions.add(() -> {
                check("p2-feature-toggle", meta("full_bright").isEnabled());
                check("p2-keybind-config", cfg.zoomKey == GLFW.GLFW_KEY_X);
                check("p2-keybind-vanilla", zoomMapping().saveString().equals("key.keyboard.x"));
                check("p2-theme-accent", cfg.theme.accent == BLUE_ACCENT);
                check("p2-picker-apply-persisted", cfg.blockOverlayOutlineColor != 0xFF000000);
                check("p2-picker-cancel-not-persisted", cfg.hitColor == 0x66FF0000);
                check("p2-active-profile", "Persist Test".equals(cfg.activeProfile) && "Persist Test".equals(pm.currentProfile()));
                check("p2-profile-value", Math.abs(cfg.zoomSmoothness - 0.55) < 1e-9);
                var ping = AuroraClient.modules().get("ping"); var cps = AuroraClient.modules().get("cps");
                check("p2-reset-positions", ping.offsetX == 4 && ping.offsetY == 80);
                check("p2-hud-move", cps.offsetX == 120);
                check("p2-waypoint", WaypointFeature.get().currentWorldWaypoints().stream()
                        .anyMatch(w -> w.name.equals("Persist marker") && "Acceptance".equals(w.group)));
                check("p2-rapid-saves-final", Math.abs(cfg.zoomLevel - 7.25) < 1e-9);
            });
            actions.add(() -> { pm.switchTo("default"); cfg.zoomLevel = 3.5; cfg.theme.accent = TEAL_ACCENT; ThemeManager.reload(); AuroraConfig.save(); });
        } else {
            actions.add(() -> {
                check("p3-active-profile", "default".equals(cfg.activeProfile));
                check("p3-zoom", Math.abs(cfg.zoomLevel - 3.5) < 1e-9);
                check("p3-accent", cfg.theme.accent == TEAL_ACCENT);
                try {
                    String other = Files.readString(mc.gameDirectory.toPath().resolve("config/profiles/Persist Test.json"));
                    check("p3-other-profile-kept", other.contains("\"zoomSmoothness\": 0.55"));
                } catch (Exception e) { check("p3-other-profile-kept", false); }
            });
        }
        actions.add(() -> {
            try (var s = Files.list(mc.gameDirectory.toPath().resolve("config"))) {
                check("no-leftover-temp", s.noneMatch(p -> p.toString().endsWith(".tmp")));
            } catch (Exception e) { check("no-leftover-temp", false); }
        });
    }

    private PersistAudit() {}
}
