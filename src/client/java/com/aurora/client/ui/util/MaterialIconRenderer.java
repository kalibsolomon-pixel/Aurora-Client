package com.aurora.client.ui.util;

import com.aurora.client.AuroraClient;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Native-resolution rendering for Material Symbols icon glyphs (the
 * module-tile icons, dropdown chevrons, …).
 *
 * <p><b>Why this exists.</b> Minecraft bakes every TTF glyph exactly once —
 * at {@code size × oversample} pixels per em ({@code TrueTypeGlyphProvider}
 * calls {@code FT_Set_Pixel_Sizes(size*oversample)}) — into a shared 256×256
 * font atlas whose sampler is hardwired to {@code FilterMode.NEAREST} (see
 * {@code FontTexture}'s constructor). Vanilla text looks crisp because
 * bitmap-font glyphs are drawn at a whole-number multiple of their baked
 * texel size, where point sampling is lossless. Aurora's icons break that
 * assumption: the material-symbols font is baked at 11×8 = 88 px/em and then
 * scaled per site (grid tiles ≈ 2–3×, list rows and chevrons ≈ 0.4–1×) and
 * across GUI scales 1–4+, so the atlas texel grid never lines up with the
 * screen pixel grid. NEAREST point sampling at that mismatch produces
 * exactly the reported artifacts: ragged, inconsistently weighted strokes
 * plus thin features (sun rays, palette dots, chevron strokes) that wash out
 * or drop out entirely as individual texels are skipped or doubled.
 *
 * <p><b>What this does.</b> Bypasses the shared atlas: each needed codepoint
 * is rasterized directly with FreeType — the same rasterizer and TTF
 * Minecraft's own pipeline uses ({@link FreeTypeUtil#getLibrary()}) — at the
 * <b>exact device-pixel size</b> the icon will occupy on screen
 * ({@code emGui × guiScale}), and blitted 1:1 through the standard
 * {@code GUI_TEXTURED} pipeline. No resampling ever happens: anti-aliasing
 * is resolved by the rasterizer on the final pixel grid, so edges stay
 * smooth and thin strokes survive at every GUI scale and icon size.
 *
 * <p>Sizing keeps visual parity with the old glyph path: callers pass the
 * em size in GUI units the icon used to occupy (e.g. the fit-box the old
 * pose-scaling math produced), the rasterizer works at
 * {@code round(emGui × guiScale)} pixels, and the ink is drawn at its
 * natural proportion centered on the requested point.
 *
 * <p>Rasters are cached per (codepoint, em-px); a GUI-scale change clears
 * the cache. At GUI scale 4 the whole cache is a few MB of RGBA textures —
 * rasterization itself runs only on first use or scale change. Any
 * FreeType failure permanently flips this class into a pass-through that
 * draws the old way via the font atlas, so rendering can never break.
 */
public final class MaterialIconRenderer {

    private static final Logger LOG = LogUtils.getLogger();

    /** Nominal size of the {@code aurora:material_symbols} font provider (font JSON), in GUI units. */
    public static final float NATURAL_EM_GUI = 11.0f;

    private static final String FONT_RESOURCE = "font/material_symbols_rounded.ttf";

    private static final Style SYMBOL_STYLE = Style.EMPTY.withFont(
            new net.minecraft.network.chat.FontDescription.Resource(
                    Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "material_symbols")));

    private static final AtomicLong SEQ = new AtomicLong();

    /** Guards face + raster cache; draw calls are render-thread, but sync keeps vanilla's LIBRARY_LOCK discipline. */
    private static final Object LOCK = new Object();

    /** Max cached rasters before a full clear (45+ icons × a few sizes each in practice). */
    private static final int MAX_RASTERS = 128;
    /** Upper bound for a raster's em size in device pixels (48 GUI × scale 4 = 192; headroom for odd setups). */
    private static final int MAX_EM_PX = 512;

    private static FT_Face face;
    /** Backing bytes for {@link #face}; FreeType reads them lazily, so this reference must outlive the face. */
    private static ByteBuffer fontBytes;
    private static boolean initFailed;
    private static final Map<Long, Raster> RASTERS = new HashMap<>();
    private static int cachedGuiScale = -1;

    /** One rasterized icon: a registered {@link DynamicTexture} of the glyph's ink at device-pixel density. */
    private record Raster(Identifier id, int width, int height) {}

    private MaterialIconRenderer() {}

    /**
     * Draws {@code icon} centered at ({@code cx}, {@code cy}) as if the
     * material-symbols font were rendered at {@code emGui} GUI units — but
     * rasterized at the device's true pixel grid instead of the shared
     * point-sampled atlas.
     *
     * <p>Falls back to the old atlas path (styled glyph via
     * {@code drawString} with pose scaling) whenever the crisp path is
     * unavailable, so call sites need no fallback logic of their own.
     */
    public static void drawIcon(GuiGraphics g, Font font, String icon, float cx, float cy, float emGui, int argb) {
        if (icon == null || icon.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        int guiScale = mc.getWindow().getGuiScale();
        if (guiScale <= 0) return;
        int emPx = Math.max(1, Math.min(MAX_EM_PX, Math.round(emGui * guiScale)));

        Raster r;
        synchronized (LOCK) {
            if (cachedGuiScale != guiScale) {
                releaseAllLocked();
                cachedGuiScale = guiScale;
            }
            long key = key(icon.codePointAt(0), emPx);
            r = RASTERS.get(key);
            if (r == null) {
                if (initFailed) {
                    drawVanillaScaled(g, font, icon, cx, cy, emGui, argb);
                    return;
                }
                if (!ensureFaceLocked()) {
                    drawVanillaScaled(g, font, icon, cx, cy, emGui, argb);
                    return;
                }
                r = rasterizeLocked(icon.codePointAt(0), emPx);
                if (r == null) {
                    drawVanillaScaled(g, font, icon, cx, cy, emGui, argb);
                    return;
                }
                if (RASTERS.size() >= MAX_RASTERS) releaseAllLocked();
                RASTERS.put(key, r);
            }
        }

        float guiW = r.width() / (float) guiScale;
        float guiH = r.height() / (float) guiScale;
        int x = Math.round(cx - guiW / 2f);
        int y = Math.round(cy - guiH / 2f);
        g.blit(RenderPipelines.GUI_TEXTURED, r.id(), x, y, 0f, 0f,
                Math.max(1, Math.round(guiW)), Math.max(1, Math.round(guiH)),
                r.width(), r.height(), r.width(), r.height(), argb);
    }

    // ========================================================================
    //  Rasterization
    // ========================================================================

    private static long key(int codepoint, int emPx) {
        return ((long) codepoint << 21) | emPx;
    }

    private static Raster rasterizeLocked(int codepoint, int emPx) {
        try {
            long glyphIndex = FreeType.FT_Get_Char_Index(face, codepoint);
            if (glyphIndex == 0L) return null; // codepoint not in the subset — fall back
            // FreeTypeUtil.checkError returns TRUE when the code is an ERROR
            // (vanilla's name means "did an error occur"), so the branches
            // below deliberately throw/return on `checkError(...)` being true.
            if (FreeTypeUtil.checkError(FreeType.FT_Set_Pixel_Sizes(face, 0, emPx),
                    "setting material-symbols pixel size " + emPx)) return null;
            if (FreeTypeUtil.checkError(FreeType.FT_Load_Glyph(face, (int) glyphIndex,
                    FreeType.FT_LOAD_RENDER | FreeType.FT_LOAD_NO_HINTING),
                    "loading material-symbols glyph U+" + Integer.toHexString(codepoint))) return null;

            FT_Bitmap bitmap = face.glyph().bitmap();
            int w = bitmap.width();
            int h = bitmap.rows();
            if (w <= 0 || h <= 0) return null;
            int pitch = bitmap.pitch();
            if (pitch <= 0) return null; // negative pitch would need bottom-up reads; FreeType never emits it here
            ByteBuffer src = bitmap.buffer(pitch * h);

            // Alpha-only gray raster → white RGBA (ARGB ints; NativeImage converts to ABGR).
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, w, h, false);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int a = src.get(y * pitch + x) & 0xFF;
                    img.setPixel(x, y, (a << 24) | 0x00FFFFFF);
                }
            }

            long seq = SEQ.incrementAndGet();
            Identifier id = Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "icon_glyph/" + seq);
            DynamicTexture tex = new DynamicTexture(() -> "aurora_icon_glyph_" + seq, img);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            tex.upload();
            return new Raster(id, w, h);
        } catch (Throwable t) {
            LOG.warn("[Aurora] Material icon rasterization failed for U+{}; falling back to glyph atlas",
                    Integer.toHexString(codepoint), t);
            return null;
        }
    }

    private static boolean ensureFaceLocked() {
        if (face != null) return true;
        try {
            Minecraft mc = Minecraft.getInstance();
            var resource = mc.getResourceManager()
                    .getResource(Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, FONT_RESOURCE))
                    .orElseThrow(() -> new IllegalStateException("material-symbols TTF not found"));
            byte[] bytes;
            try (InputStream in = resource.open()) {
                bytes = in.readAllBytes();
            }
            fontBytes = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            fontBytes.put(bytes);
            fontBytes.flip();

            synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                long library = FreeTypeUtil.getLibrary();
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    PointerBuffer handle = stack.mallocPointer(1);
                    // checkError returns true on error (see note in rasterizeLocked).
                    if (FreeTypeUtil.checkError(FreeType.FT_New_Memory_Face(library, fontBytes, 0, handle),
                            "opening material-symbols face")) {
                        throw new IllegalStateException("FT_New_Memory_Face failed");
                    }
                    face = FT_Face.create(handle.get(0));
                }
                if (FreeTypeUtil.checkError(FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE),
                        "selecting material-symbols unicode charmap")) {
                    throw new IllegalStateException("FT_Select_Charmap failed");
                }
            }
            LOG.info("[Aurora] MaterialIconRenderer active: icons render at native device resolution via FreeType");
            return true;
        } catch (Throwable t) {
            initFailed = true;
            LOG.warn("[Aurora] Material-symbols icon face unavailable; icons use the glyph atlas path", t);
            return false;
        }
    }

    private static void releaseAllLocked() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getTextureManager() != null) {
            for (Raster r : RASTERS.values()) {
                try {
                    mc.getTextureManager().release(r.id());
                } catch (Throwable ignored) {
                }
            }
        }
        RASTERS.clear();
    }

    // ========================================================================
    //  Fallback — the pre-fix atlas path (soft at any texel/pixel mismatch)
    // ========================================================================

    /**
     * Old rendering: the size-11 atlas glyph pose-scaled so its advance ×
     * line-height box fits {@code emGui}. Kept pixel-compatible with the
     * previous call-site math so the fallback is visually what shipped
     * before this renderer existed.
     */
    private static void drawVanillaScaled(GuiGraphics g, Font font, String icon, float cx, float cy, float emGui, int argb) {
        Component comp = Component.literal(icon).withStyle(SYMBOL_STYLE);
        int gw = Math.max(1, font.width(comp));
        int gh = Math.max(1, font.lineHeight);
        float scale = Math.min(8f, Math.min(emGui / gw, emGui / gh));
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.drawString(font, comp.getVisualOrderText(), -gw / 2, -gh / 2, argb, false);
        g.pose().popMatrix();
    }
}
