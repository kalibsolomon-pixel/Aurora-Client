package com.aurora.client.ui.text;

import com.aurora.client.ui.util.AuroraFontQuality;
import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.Options;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aurora.client.mixin.FontManagerAccessor;
import com.aurora.client.mixin.FontSetAccessor;

public final class SdfFontSets implements AutoCloseable {
    public interface Lifecycle {
        void aurora$refreshSdf(Set<FontOption> options);
    }

    private record Alias(FontSet raster, FontSet sdf) {}

    private final ResourceManager resources;
    private final SdfAtlas atlas = new SdfAtlas();
    private final Map<Identifier, Alias> aliases = new HashMap<>();
    private final Map<Identifier, SdfFace> faces = new HashMap<>();

    public SdfFontSets(ResourceManager resources) { this.resources = resources; }

    public FontSet resolve(Identifier id, FontSet raster, TextureManager textures, Options options) {
        Alias existing = aliases.get(id);
        if (existing != null) return existing.sdf();
        List<GlyphProvider.Conditional> providers = providers(raster);
        if (providers == null) return null;
        FontSet sdf = new FontSet(new GlyphStitcher(textures, id));
        try {
            sdf.reload(providers, FontManagerAccessor.aurora$fontOptions(options));
            aliases.put(id, new Alias(raster, sdf));
            return sdf;
        } catch (RuntimeException | LinkageError failure) {
            sdf.close();
            throw failure;
        }
    }

    private List<GlyphProvider.Conditional> providers(FontSet raster) {
        if (!(raster instanceof FontSetAccessor access)) return null;
        List<GlyphProvider.Conditional> providers = new ArrayList<>();
        boolean hasSdf = false;
        for (GlyphProvider.Conditional entry : access.aurora$allProviders()) {
            var definition = AuroraFontQuality.definition(entry.provider());
            if (definition == null || definition.location().getPath().equals("material_symbols_rounded.ttf")) {
                providers.add(entry);
            } else {
                SdfFace face = faces.computeIfAbsent(definition.location(), id -> new SdfFace(resources, id, atlas));
                providers.add(new GlyphProvider.Conditional(new SdfGlyphProvider(entry.provider(), face), entry.filter()));
                hasSdf = true;
            }
        }
        return hasSdf ? providers : null;
    }

    public void refresh(Set<FontOption> options) {
        if (!SdfTextSupport.available()) return;
        try {
            for (Alias alias : aliases.values()) {
                var providers = providers(alias.raster());
                if (providers == null) throw new IllegalStateException("Raster providers unavailable after reload");
                alias.sdf().reload(providers, options);
            }
        } catch (RuntimeException | LinkageError failure) {
            SdfTextSupport.fail("font alias refresh", failure);
        }
    }

    @Override public void close() {
        NativeSdfText.invalidatePipeline();
        for (Alias alias : aliases.values()) closeOne(alias.sdf());
        for (SdfFace face : faces.values()) closeOne(face);
        closeOne(atlas);
        aliases.clear();
        faces.clear();
    }

    static void closeOne(AutoCloseable resource) {
        try {
            resource.close();
        } catch (Exception | LinkageError failure) {
            SdfTextSupport.fail("closing font aliases", failure);
        }
    }
}
