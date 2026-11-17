package com.aurora.client.ui.text;

import com.aurora.client.ui.util.AuroraFontQuality;
import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.util.Map;

/** Wraps only the two dedicated font IDs; every existing provider loader is returned untouched. */
public final class SdfFontLoader {
    private static final Map<String, Float> SIZES = Map.of(
            "aurora:inter_medium_sdf", 9.0f,
            "aurora:inter_medium_ui_sdf", 8.0f);

    private SdfFontLoader() {}

    public static GlyphProviderDefinition.Loader wrap(Object builderId,
                                                       GlyphProviderDefinition.Loader original) {
        Identifier id = fontId(builderId);
        Float size = id == null ? null : SIZES.get(id.toString());
        if (size == null) return original;
        return resources -> load(original, resources, size);
    }

    static GlyphProvider load(GlyphProviderDefinition.Loader original, ResourceManager resources,
                              float size) throws IOException {
        // Read the same live oversample that TrueTypeProviderDefinitionMixin supplies.
        float rasterOversample = AuroraFontQuality.oversample();
        GlyphProvider raster = original.load(resources);
        if (raster == null) return null;
        // The wrapper, not its private raster delegate, participates in GUI-scale reloads.
        AuroraFontQuality.unregister(raster);
        if (SdfTextSupport.failed()) return raster;
        try {
            return SdfGlyphProvider.create(raster, original, resources, size,
                    rasterOversample * com.aurora.client.ui.util.SdfTextMath.STRIKE_MULTIPLIER);
        } catch (Exception | LinkageError failure) {
            SdfTextSupport.fail("FreeType initialization", failure);
            return raster;
        }
    }

    private static Identifier fontId(Object builderId) {
        try {
            var method = builderId.getClass().getDeclaredMethod("fontId");
            method.setAccessible(true);
            return (Identifier) method.invoke(builderId);
        } catch (ReflectiveOperationException failure) {
            SdfTextSupport.fail("provider ID access", failure);
            return null;
        }
    }
}
