package com.aurora.client.ui.util;

import com.aurora.client.health.MixinHealth;
import com.aurora.client.mixin.FontManagerAccessor;
import com.aurora.client.mixin.FontSetAccessor;
import com.aurora.client.mixin.MinecraftFontManagerAccessor;
import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Crisp custom fonts (owner report 2026-11-12: "choppy, not smooth").
 *
 * <p>Minecraft rasterizes a TrueType glyph at {@code size x oversample}
 * pixels and samples the font atlas with nearest filtering. Aurora's fonts
 * shipped with oversample 8, so at GUI scale 3 every glyph was shrunk 2.67x
 * by skipping texels: broken stems, uneven weight, jagged curves. Rasterizing
 * at the screen's pixel size instead (oversample = GUI scale) draws each
 * texel on exactly one pixel, so FreeType's anti-aliasing reaches the screen
 * untouched, and advances land on whole pixels.
 *
 * <p>The GUI scale is read when the providers load
 * ({@code TrueTypeProviderDefinitionMixin}); when it later changes (a
 * different setting, or Auto across a window resize) {@link #beginFrame}
 * re-loads only Aurora's providers and swaps them into their font sets in
 * place, at the next frame boundary — no resource reload.
 */
public final class AuroraFontQuality {
    private AuroraFontQuality() {}

    /** Aurora's live TrueType providers and the definitions they came from (weak: a resource reload drops them). */
    private static final Map<GlyphProvider, TrueTypeGlyphProviderDefinition> PROVIDERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** The GUI scale Aurora's providers were last rasterized for; 0 before the first load. */
    private static volatile int rasterScale;

    private static int guiScale() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return 2;
        return Math.max(1, Math.min(8, (int) mc.getWindow().getGuiScale()));
    }

    /** The oversample for an Aurora provider being loaded now: the GUI scale, one texel per screen pixel. */
    public static float oversample() {
        int scale = guiScale();
        rasterScale = scale;
        return scale;
    }

    public static void register(GlyphProvider provider, TrueTypeGlyphProviderDefinition definition) {
        PROVIDERS.put(provider, definition);
    }

    /** The scale the fonts are rasterized for (for tests and diagnostics). */
    public static int rasterScale() {
        return rasterScale;
    }

    /** Frame boundary (from {@code GlassSurface.beginFrame()}): re-rasterize when the GUI scale has changed. */
    public static void beginFrame() {
        int scale = guiScale();
        if (rasterScale == 0 || scale == rasterScale || PROVIDERS.isEmpty()) return;
        if (MixinHealth.failed("TrueTypeProviderDefinitionMixin") || MixinHealth.failed("FontManagerAccessor")
                || MixinHealth.failed("FontSetAccessor") || MixinHealth.failed("MinecraftFontManagerAccessor")) {
            rasterScale = scale; // cannot swap; stop retrying every frame
            return;
        }
        rescale();
    }

    private static void rescale() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc instanceof MinecraftFontManagerAccessor minecraft)) return;
        FontManager manager = minecraft.aurora$fontManager();
        if (!(manager instanceof FontManagerAccessor fonts)) return;
        Set<FontOption> options = FontManagerAccessor.aurora$fontOptions(mc.options);
        Map<GlyphProvider, GlyphProvider> replaced = new IdentityHashMap<>();
        Map<GlyphProvider, TrueTypeGlyphProviderDefinition> snapshot;
        synchronized (PROVIDERS) {
            snapshot = new IdentityHashMap<>(PROVIDERS);
        }
        for (FontSet set : fonts.aurora$fontSets().values()) {
            if (!(set instanceof FontSetAccessor providers)) continue;
            List<GlyphProvider.Conditional> current = providers.aurora$allProviders();
            List<GlyphProvider.Conditional> next = new ArrayList<>(current.size());
            boolean changed = false;
            for (GlyphProvider.Conditional entry : current) {
                TrueTypeGlyphProviderDefinition definition = snapshot.get(entry.provider());
                GlyphProvider fresh = definition == null ? null
                        : replaced.computeIfAbsent(entry.provider(), old -> load(definition, mc));
                if (fresh == null) {
                    next.add(entry);
                } else {
                    next.add(new GlyphProvider.Conditional(fresh, entry.filter()));
                    changed = true;
                }
            }
            if (changed) set.reload(next, options);
        }
        List<GlyphProvider> owned = fonts.aurora$providersToClose();
        for (Map.Entry<GlyphProvider, GlyphProvider> swap : replaced.entrySet()) {
            if (swap.getValue() == null) continue;
            owned.remove(swap.getKey());
            owned.add(swap.getValue());
            PROVIDERS.remove(swap.getKey());
            swap.getKey().close();
        }
        com.aurora.client.AuroraClient.LOGGER.info("[Fonts] Re-rasterized {} Aurora font provider(s) for GUI scale {}",
                replaced.size(), rasterScale);
    }

    private static GlyphProvider load(TrueTypeGlyphProviderDefinition definition, Minecraft mc) {
        try {
            var loader = definition.unpack().left();
            return loader.isPresent() ? loader.get().load(mc.getResourceManager()) : null;
        } catch (Exception e) {
            com.aurora.client.AuroraClient.LOGGER.warn("[Fonts] Could not re-load {}", definition.location(), e);
            return null;
        }
    }
}
