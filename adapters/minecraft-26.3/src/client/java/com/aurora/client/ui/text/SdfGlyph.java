package com.aurora.client.ui.text;

import com.aurora.client.ui.util.SdfTextMath;
import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;

/** One already-rendered SDF bitmap plus its exact raster-glyph metrics and fallback. */
final class SdfGlyph implements UnbakedGlyph {
    private final SdfGlyphProvider owner;
    private final UnbakedGlyph raster;
    private final byte[] pixels;
    private final int width, height;
    private final float bearingLeft, bearingTop, oversample;

    SdfGlyph(SdfGlyphProvider owner, UnbakedGlyph raster, byte[] pixels, int width, int height,
             float bearingLeft, float bearingTop, float oversample) {
        this.owner = owner;
        this.raster = raster;
        this.pixels = pixels;
        this.width = width;
        this.height = height;
        this.bearingLeft = bearingLeft;
        this.bearingTop = bearingTop;
        this.oversample = oversample;
    }

    /** The same GlyphInfo object as the raster provider: advances cannot drift. */
    @Override public GlyphInfo info() { return raster.info(); }

    @Override public BakedGlyph bake(Stitcher stitcher) {
        BakedGlyph fallback = raster.bake(stitcher);
        if (SdfTextSupport.failed()) return fallback;
        try {
            BakedGlyph sdf = owner.atlas().add(info(), new Bitmap());
            return new SdfBakedGlyph(info(), sdf, fallback);
        } catch (Exception | LinkageError failure) {
            SdfTextSupport.fail("atlas creation/upload", failure);
            return fallback;
        }
    }

    private final class Bitmap implements GlyphBitmap {
        @Override public int getPixelWidth() { return width; }
        @Override public int getPixelHeight() { return height; }
        @Override public float getOversample() { return oversample; }
        @Override public float getBearingLeft() { return bearingLeft; }
        @Override public float getBearingTop() { return bearingTop; }
        @Override public boolean isColored() { return false; }

        @Override public void upload(int x, int y, GpuTexture texture) {
            try (NativeImage image = new NativeImage(NativeImage.Format.LUMINANCE, width, height, false)) {
                image.getPixelBytes().clear().put(pixels);
                RenderSystem.getDevice().createCommandEncoder()
                        .writeToTexture(texture, image, 0, 0, x, y);
            }
        }
    }
}
