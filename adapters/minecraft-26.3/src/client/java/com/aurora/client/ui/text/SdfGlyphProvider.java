package com.aurora.client.ui.text;

import com.aurora.client.ui.util.AuroraFontQuality;
import com.aurora.client.ui.util.SdfTextMath;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.mojang.blaze3d.platform.TextureUtil;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.CodepointMap;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;

/** Inter Medium SDF glyphs with a vanilla TrueType provider as metrics and rendering fallback. */
final class SdfGlyphProvider implements GlyphProvider, AuroraFontQuality.Reloadable {
    private static final Identifier FONT = Identifier.fromNamespaceAndPath("aurora", "font/inter-medium.ttf");
    private static final int LOAD_FLAGS = FreeType.FT_LOAD_NO_BITMAP | FreeType.FT_LOAD_FORCE_AUTOHINT;

    private final GlyphProvider raster;
    private final GlyphProviderDefinition.Loader rasterLoader;
    private final float size;
    private final float oversample;
    private final CodepointMap<UnbakedGlyph> glyphs =
            new CodepointMap<>(UnbakedGlyph[]::new, UnbakedGlyph[][]::new);
    private final SdfAtlas atlas = new SdfAtlas();
    private ByteBuffer fontMemory;
    private FT_Face face;

    private SdfGlyphProvider(GlyphProvider raster, GlyphProviderDefinition.Loader rasterLoader,
                             float size, float oversample, ByteBuffer fontMemory, FT_Face face) {
        this.raster = raster;
        this.rasterLoader = rasterLoader;
        this.size = size;
        this.oversample = oversample;
        this.fontMemory = fontMemory;
        this.face = face;
    }

    static GlyphProvider create(GlyphProvider raster, GlyphProviderDefinition.Loader rasterLoader,
                                ResourceManager resources, float size, float oversample) throws IOException {
        FaceData opened = openFace(resources, size, oversample);
        return new SdfGlyphProvider(raster, rasterLoader, size, oversample, opened.memory, opened.face);
    }

    @Override public GlyphProvider reload(Minecraft mc) {
        try {
            return SdfFontLoader.load(rasterLoader, mc.getResourceManager(), size);
        } catch (Exception | LinkageError failure) {
            SdfTextSupport.fail("GUI-scale reload", failure);
            return null;
        }
    }

    @Override public IntSet getSupportedGlyphs() { return raster.getSupportedGlyphs(); }

    @Override public UnbakedGlyph getGlyph(int codePoint) {
        if (SdfTextSupport.failed() || face == null) return raster.getGlyph(codePoint);
        UnbakedGlyph cached = glyphs.get(codePoint);
        if (cached != null) return cached;
        UnbakedGlyph rasterGlyph = raster.getGlyph(codePoint);
        if (rasterGlyph == null) return null;
        synchronized (face) {
            if (SdfTextSupport.failed()) return rasterGlyph;
            cached = glyphs.get(codePoint);
            if (cached != null) return cached;
            try {
                UnbakedGlyph loaded = loadSdf(codePoint, rasterGlyph);
                glyphs.put(codePoint, loaded);
                return loaded;
            } catch (RuntimeException | LinkageError failure) {
                SdfTextSupport.fail("FreeType SDF rendering", failure);
                return rasterGlyph;
            }
        }
    }

    private UnbakedGlyph loadSdf(int codePoint, UnbakedGlyph rasterGlyph) {
        int index = (int) FreeType.FT_Get_Char_Index(face, codePoint);
        if (index == 0) return rasterGlyph;
        FreeTypeUtil.assertError(FreeType.FT_Load_Glyph(face, index, LOAD_FLAGS),
                String.format(Locale.ROOT, "Loading SDF glyph U+%06X", codePoint));
        var slot = face.glyph();
        if (slot == null) throw new IllegalStateException("SDF glyph slot was not initialized");
        FreeTypeUtil.assertError(FreeType.FT_Render_Glyph(slot, FreeType.FT_RENDER_MODE_SDF),
                String.format(Locale.ROOT, "Rendering SDF glyph U+%06X", codePoint));
        var bitmap = slot.bitmap();
        int width = bitmap.width(), height = bitmap.rows(), pitch = bitmap.pitch();
        if (width <= 0 || height <= 0) return rasterGlyph;
        if (pitch < width) throw new IllegalStateException("Unsupported SDF bitmap pitch " + pitch);

        int paddedWidth = SdfTextMath.paddedSize(width, SdfTextMath.PADDING);
        int paddedHeight = SdfTextMath.paddedSize(height, SdfTextMath.PADDING);
        byte[] pixels = new byte[paddedWidth * paddedHeight]; // zero = spread outside = transparent
        ByteBuffer source = bitmap.buffer(pitch * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[(y + SdfTextMath.PADDING) * paddedWidth + x + SdfTextMath.PADDING] =
                        source.get(y * pitch + x);
            }
        }
        return new SdfGlyph(this, rasterGlyph, pixels, paddedWidth, paddedHeight,
                SdfTextMath.paddedLeftBearing(slot.bitmap_left(), SdfTextMath.PADDING, oversample),
                SdfTextMath.paddedTopBearing(slot.bitmap_top(), SdfTextMath.PADDING, oversample),
                oversample);
    }

    SdfAtlas atlas() { return atlas; }

    private static FaceData openFace(ResourceManager resources, float size, float oversample)
            throws IOException {
        ByteBuffer memory = null;
        FT_Face face = null;
        try (InputStream input = resources.open(FONT)) {
            memory = TextureUtil.readResource(input);
            synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    var pointer = stack.mallocPointer(1);
                    FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(
                            FreeTypeUtil.getLibrary(), memory, 0, pointer), "Initializing SDF font face");
                    face = FT_Face.create(pointer.get(0));
                    String format = FreeType.FT_Get_Font_Format(face);
                    if (!"TrueType".equals(format)) throw new IOException("Not a TrueType font: " + format);
                    FreeTypeUtil.assertError(FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE),
                            "Find SDF unicode charmap");
                    ByteBuffer spread = stack.malloc(Integer.BYTES);
                    spread.putInt(0, SdfTextMath.SPREAD);
                    FreeTypeUtil.assertError(FreeType.FT_Property_Set(FreeTypeUtil.getLibrary(),
                            "sdf", "spread", spread), "Set SDF spread");
                    int strike = Math.round(size * oversample);
                    FreeTypeUtil.assertError(FreeType.FT_Set_Pixel_Sizes(face, strike, strike),
                            "Set SDF strike size");
                }
            }
            return new FaceData(memory, face);
        } catch (IOException | RuntimeException failure) {
            closeFace(face, memory);
            throw failure;
        }
    }

    @Override public void close() {
        try {
            atlas.close();
        } finally {
            try {
                closeFace(face, fontMemory);
                face = null;
                fontMemory = null;
            } finally {
                raster.close();
            }
        }
    }

    private static void closeFace(FT_Face face, ByteBuffer memory) {
        try {
            if (face != null) {
                synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                    FreeTypeUtil.checkError(FreeType.FT_Done_Face(face), "Deleting SDF face");
                }
            }
        } finally {
            if (memory != null) MemoryUtil.memFree(memory);
        }
    }

    private static final class FaceData {
        private final ByteBuffer memory;
        private final FT_Face face;
        private FaceData(ByteBuffer memory, FT_Face face) { this.memory = memory; this.face = face; }
    }
}
