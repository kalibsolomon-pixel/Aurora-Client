package com.aurora.client.ui.text;

import com.aurora.client.ui.util.SdfTextMath;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

final class SdfStrike implements AutoCloseable {
    record Bitmap(int width, int height, byte[] pixels) {}

    private final ByteBuffer memory;
    private final FT_Face face;

    private SdfStrike(ByteBuffer memory, FT_Face face) {
        this.memory = memory;
        this.face = face;
    }

    static SdfStrike open(InputStream input) throws IOException {
        ByteBuffer memory = TextureUtil.readResource(input);
        FT_Face face = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                var pointer = stack.mallocPointer(1);
                FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), memory, 0, pointer),
                        "Opening SDF face");
                face = FT_Face.create(pointer.get(0));
                FreeTypeUtil.assertError(FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE),
                        "Selecting SDF charmap");
                ByteBuffer spread = stack.malloc(Integer.BYTES).putInt(0, SdfTextMath.SPREAD);
                FreeTypeUtil.assertError(FreeType.FT_Property_Set(FreeTypeUtil.getLibrary(), "bsdf", "spread", spread),
                        "Setting bitmap SDF spread");
                FreeTypeUtil.assertError(FreeType.FT_Set_Pixel_Sizes(face, SdfTextMath.STRIKE_SIZE, SdfTextMath.STRIKE_SIZE),
                        "Setting SDF strike size");
            }
            return new SdfStrike(memory, face);
        } catch (RuntimeException | LinkageError failure) {
            release(face, memory);
            throw failure;
        }
    }

    Bitmap render(int codePoint) {
        int index = FreeType.FT_Get_Char_Index(face, codePoint);
        if (index == 0) return null;
        FreeTypeUtil.assertError(FreeType.FT_Load_Glyph(face, index, FreeType.FT_LOAD_NO_BITMAP | FreeType.FT_LOAD_NO_HINTING),
                "Loading SDF glyph");
        var slot = face.glyph();
        if (slot == null) throw new IllegalStateException("SDF glyph slot unavailable");
        FreeTypeUtil.assertError(FreeType.FT_Render_Glyph(slot, FreeType.FT_RENDER_MODE_NORMAL), "Rasterizing SDF source");
        var bitmap = slot.bitmap();
        int inkWidth = bitmap.width(), inkHeight = bitmap.rows();
        if (inkWidth == 0 || inkHeight == 0) return null;
        if (SdfTextMath.paddedSize(Math.max(inkWidth, inkHeight), SdfTextMath.SPREAD + SdfTextMath.PADDING) > SdfAtlas.SIZE) {
            return null;
        }
        FreeTypeUtil.assertError(FreeType.FT_Render_Glyph(slot, FreeType.FT_RENDER_MODE_SDF), "Rendering bitmap SDF");
        int width = bitmap.width(), height = bitmap.rows(), pitch = bitmap.pitch();
        if (width != SdfTextMath.paddedSize(inkWidth, SdfTextMath.SPREAD)
                || height != SdfTextMath.paddedSize(inkHeight, SdfTextMath.SPREAD) || Math.abs(pitch) < width) {
            throw new IllegalStateException("Unexpected FreeType SDF cell dimensions or pitch");
        }
        int stride = Math.abs(pitch);
        int paddedWidth = SdfTextMath.paddedSize(width, SdfTextMath.PADDING);
        int paddedHeight = SdfTextMath.paddedSize(height, SdfTextMath.PADDING);
        byte[] pixels = new byte[paddedWidth * paddedHeight];
        ByteBuffer source = bitmap.buffer(stride * height);
        for (int row = 0; row < height; row++) {
            int sourceRow = pitch < 0 ? height - 1 - row : row;
            source.get(sourceRow * stride, pixels, (row + SdfTextMath.PADDING) * paddedWidth + SdfTextMath.PADDING, width);
        }
        return new Bitmap(paddedWidth, paddedHeight, pixels);
    }

    @Override public void close() { release(face, memory); }

    private static void release(FT_Face face, ByteBuffer memory) {
        try {
            if (face != null) {
                synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                    FreeTypeUtil.checkError(FreeType.FT_Done_Face(face), "Closing SDF face");
                }
            }
        } finally {
            MemoryUtil.memFree(memory);
        }
    }
}
