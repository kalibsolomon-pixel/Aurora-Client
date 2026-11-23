package com.aurora.client.ui.text;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

public final class NativeSdfText {
    private static final GlyphInfo UNUSED_INFO = GlyphInfo.simple(0);
    private static boolean validated;

    private NativeSdfText() {}

    private static final class Pipeline {
        static final RenderPipeline INSTANCE = RenderPipeline.builder(RenderPipelines.GUI_TEXT_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("aurora", "pipeline/gui_text_sdf"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("aurora", "core/gui_text_sdf"))
                .build();
    }

    static boolean pipelineAvailable() {
        if (validated) return true;
        validated = SdfTextSupport.checkPipeline(NativeSdfText::compiles);
        return validated;
    }

    private static boolean compiles() {
        var compiled = RenderSystem.getCompiledPipelineNullable(Pipeline.INSTANCE);
        return compiled != null && !compiled.isClosed();
    }

    static void invalidatePipeline() { validated = false; }

    static FontTexture createPage(int index) {
        Identifier name = Identifier.fromNamespaceAndPath("aurora", "font/sdf_" + index);
        FontTexture page = new FontTexture(name::toString, GlyphRenderTypes.createForGrayscaleTexture(name), false);
        clear(page.getTexture());
        return page;
    }

    /**
     * Vanilla never clears a font page, so the space between and below cells holds
     * whatever the driver left there; filtered and derivative samples at a glyph's
     * edge can reach it. Zero is "far outside" in the distance field.
     */
    private static void clear(GpuTexture texture) {
        int w = texture.getWidth(0);
        int h = texture.getHeight(0);
        try (NativeImage zeros = new NativeImage(NativeImage.Format.LUMINANCE, w, h, true)) {
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, zeros, 0, 0, 0, 0);
        }
    }

    /** The page's own texture size, not vanilla's 256: ImmediatelyFast resizes every font page. */
    static SdfAtlas.Cell insert(FontTexture page, SdfStrike.Bitmap bitmap) {
        Upload upload = new Upload(bitmap);
        if (page.add(UNUSED_INFO, upload) == null) return null;
        GpuTexture texture = page.getTexture();
        return new SdfAtlas.Cell(page, upload.x, upload.y, bitmap.width(), bitmap.height(),
                texture.getWidth(0), texture.getHeight(0));
    }

    public static boolean isSdf(TextRenderable renderable) { return renderable instanceof Instance; }

    static TextRenderable.Styled glyph(SdfAtlas.Cell cell, float penX, float penY, int color, int shadowColor,
            Style style, float advance, float boldOffset, float shadowOffset, float left, float top, float right,
            float bottom, float quadLeft, float quadTop, float quadRight, float quadBottom) {
        return new Instance(cell, penX, penY, color, shadowColor, style, advance, boldOffset, shadowOffset,
                left, top, right, bottom, quadLeft, quadTop, quadRight, quadBottom);
    }

    private static final class Instance extends SdfInstance {
        Instance(SdfAtlas.Cell cell, float penX, float penY, int color, int shadowColor, Style style, float advance,
                 float boldOffset, float shadowOffset, float left, float top, float right, float bottom,
                 float quadLeft, float quadTop, float quadRight, float quadBottom) {
            super(cell, penX, penY, color, shadowColor, style, advance, boldOffset, shadowOffset,
                    left, top, right, bottom, quadLeft, quadTop, quadRight, quadBottom);
        }

        @Override public RenderPipeline guiPipeline() { return Pipeline.INSTANCE; }
        @Override public GpuTextureView textureView() { return cell.page().getTextureView(); }
        @Override public RenderType renderType(Font.DisplayMode mode) {
            throw new IllegalStateException("SDF glyph instances are GUI-only");
        }
    }

    private static final class Upload implements GlyphBitmap {
        private final SdfStrike.Bitmap bitmap;
        private int x, y;

        Upload(SdfStrike.Bitmap bitmap) { this.bitmap = bitmap; }
        @Override public int getPixelWidth() { return bitmap.width(); }
        @Override public int getPixelHeight() { return bitmap.height(); }
        @Override public float getOversample() { return 1; }
        @Override public boolean isColored() { return false; }

        @Override public void upload(int x, int y, GpuTexture texture) {
            this.x = x;
            this.y = y;
            try (NativeImage image = new NativeImage(NativeImage.Format.LUMINANCE, bitmap.width(), bitmap.height(), false)) {
                MemoryUtil.memByteBuffer(image.getPointer(), bitmap.pixels().length).put(bitmap.pixels());
                RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image, 0, 0, x, y);
            }
        }
    }
}
