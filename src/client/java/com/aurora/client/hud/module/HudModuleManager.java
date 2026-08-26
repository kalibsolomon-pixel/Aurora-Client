package com.aurora.client.hud.module;

import com.aurora.client.AuroraClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry + renderer for {@link HudModule}s.
 *
 * <p>The singleton is held on {@link AuroraClient}; the main HUD layer
 * delegates to {@link #renderAll}.
 */
public class HudModuleManager {
    private final Map<String, HudModule> modules = new LinkedHashMap<>();

    public void register(HudModule m) {
        if (modules.put(m.id(), m) != null) {
            AuroraClient.LOGGER.warn("HUD module {} registered twice", m.id());
        }
    }

    public HudModule get(String id) { return modules.get(id); }

    /**
     * Live, unmodifiable view of registered modules in registration
     * order. Returning a view (instead of {@code new ArrayList<>(...)})
     * is critical because {@link com.aurora.client.screen.HudEditorScreen}'s
     * render loop calls this every frame — each frame's ArrayList copy
     * was visible GC pressure when the editor is open.
     */
    public Collection<HudModule> all() { return Collections.unmodifiableCollection(modules.values()); }

    public void renderAll(GuiGraphics ctx) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;

        // Hide entire HUD while the F3 *text overlay* is up. We can't use
        // DebugScreenOverlay.showDebugScreen() here: in 1.21.11 it returns
        // true whenever ANY debug-screen entry is currently enabled — so
        // toggling F3+B (which enables ENTITY_HITBOXES alone) would also
        // make it true and would hide every Aurora HUD module. Check the
        // overlay-visible flag directly instead, which is exactly the F3
        // master toggle.
        if (client.debugEntries != null && client.debugEntries.isOverlayVisible()) return;

        int sw = ctx.guiWidth();
        int sh = ctx.guiHeight();

        for (HudModule m : modules.values()) {
            try {
                m.render(ctx, client, sw, sh);
            } catch (Exception e) {
                AuroraClient.LOGGER.error("HUD module {} crashed", m.id(), e);
            }
        }
    }
}
