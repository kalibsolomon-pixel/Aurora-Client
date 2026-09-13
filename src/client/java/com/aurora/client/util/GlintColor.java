package com.aurora.client.util;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * Enchant Glint Recolor — the load-side half of the feature (the settings
 * card is {@code glint_color}; the injection point is
 * {@code mixin/ReloadableTextureMixin}).
 *
 * <p>How the glint actually renders on 1.21.11 (verified by disassembly,
 * see {@code SimpleTextureMixin}): every enchant glint draw — held items,
 * GUI/hotbar items, dropped items, worn armor — is a second pass of the
 * item's quads through one of four static {@code RenderType}s
 * ({@code glint}, {@code glint_translucent}, {@code entity_glint},
 * {@code armor_entity_glint}), all on the {@code pipeline/glint} pipeline
 * with blend {@code (SRC_COLOR, ONE, ZERO, ONE)}: the fragment's RGB is
 * <em>added</em> to the framebuffer. The shader multiplies the texture
 * sample by {@code ColorModulator} (in practice white — the glint vertex
 * format is POSITION_TEX, no per-vertex color) and a fog fade. So the
 * purple color AND the shimmer pattern both live in exactly two 128×128
 * textures, whose ids vanilla bakes into {@code ItemRenderer}'s
 * {@code ENCHANTED_GLINT_ITEM} / {@code ENCHANTED_GLINT_ARMOR}.
 *
 * <p>Recoloring therefore means recoloring those pixels: the load hook
 * colorizes them — each pixel's intensity ({@code max(R,G,B)}) drives the
 * picker color at full strength, preserving the shimmer's value ramp while
 * replacing its hue (see {@link #tint}). This runs inside the texture
 * load/apply funnel, before vanilla uploads; vanilla closes the NativeImage
 * right after upload, so this is the only CPU-side moment the pixels exist.
 *
 * <p>The HitColor d84298c lesson (byte-swapped "disabled" constant painted
 * the wrong color) is answered structurally here: there is NO disabled-state
 * constant at all. When the feature is off the hook returns the vanilla
 * {@code TextureContents} untouched — "restore vanilla" is vanilla's own
 * file load, bit for bit. Every decompose/recompose goes through
 * {@link ARGB} helpers, never hand-packed hex.
 *
 * <p>Mid-session toggles/color changes can't rewrite pixels that no longer
 * exist on the CPU side, so {@link #tick()} (registered from
 * {@code AuroraClient} on END_CLIENT_TICK — render thread, between frames,
 * so no pass holds the old texture) forces a reload of exactly these two
 * textures via {@code TextureManager.release} + {@code getTexture}: that is
 * vanilla's own synchronous load path ({@code registerAndLoad} →
 * {@code loadContents} → {@code apply}), which re-enters the load hook with
 * the fresh config. Resource reloads (F3+T) re-run {@code loadContents} too,
 * so the recolor survives pack changes without any extra wiring.
 */
public final class GlintColor {

    /**
     * The two textures vanilla's glint render types sample — read from
     * {@code ItemRenderer}'s static initializer in the 1.21.11 client jar
     * ({@code ldc "textures/misc/enchanted_glint_item.png"} /
     * {@code ldc "textures/misc/enchanted_glint_armor.png"}), not typed by
     * hand: the item texture backs glint/glint_translucent/entity_glint
     * (held, GUI, dropped items), the armor texture backs
     * armor_entity_glint (worn armor via EquipmentLayerRenderer).
     */
    public static final Identifier GLINT_ITEM =
            Identifier.fromNamespaceAndPath("minecraft", "textures/misc/enchanted_glint_item.png");
    public static final Identifier GLINT_ARMOR =
            Identifier.fromNamespaceAndPath("minecraft", "textures/misc/enchanted_glint_armor.png");

    /** Change-detection state — sentinels force the first comparison to match. */
    private static boolean initialized;
    private static int lastColor = Integer.MIN_VALUE;
    private static int lastEnabled = -1;
    private static int lastTintArmor = -1;

    private GlintColor() {
    }

    /**
     * The load hook (called at HEAD of ReloadableTexture.apply by the
     * mixin). Recolors the pixels in place when the feature is enabled;
     * returns the contents untouched otherwise — including the armor texture
     * when only item glint is enabled (the "Tint Armor" sub-toggle, mirroring
     * Hit Color's).
     */
    public static void applyToLoadedContents(Identifier id, TextureContents contents) {
        if (contents == null) return;
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.glintColorEnabled) return;
        if (id.equals(GLINT_ARMOR)) {
            if (!cfg.glintColorTintArmor) return;
        } else if (!id.equals(GLINT_ITEM)) {
            return; // not a glint texture — never touch anything else
        }
        tint(id, contents, cfg.glintColor);
    }

    /**
     * Colorize semantics (the fix for the weak-recolor bug): because the
     * glint blend is additive with the source RGB as the weight, each output
     * pixel's channels ARE its additive energy. The old math multiplied the
     * original channels by the target color — a tint that can only dim, so
     * any target far from the source's dim purple (R≈29%, G≈14%, B≈48%)
     * came out weak no matter how saturated it was. Instead each pixel is
     * reduced to an INTENSITY — {@code max(R,G,B)} — and the output is
     * {@code intensity × target}: the target color at full strength wherever
     * the original texture was bright, scaling to near-black wherever the
     * original was dark, so the shimmer pattern (a fixed-hue value ramp)
     * survives as a value ramp of the target hue rather than flattening
     * into a solid block.
     *
     * <p>Why {@code max(R,G,B)} (HSV value) and not a weighted luminance
     * (0.299R+0.587G+0.114B): the source pattern's energy sits in the red
     * and blue channels (purple), which weighted luma systematically
     * undervalues — its green-dominant weights would re-dim the recolored
     * glint to a fraction of the target, a second-order version of the same
     * weakness being fixed. {@code max} reads the pattern's own crest as
     * "full strength" and reproduces the ramp exactly.
     *
     * <p>The picker's alpha still scales overall strength, and the
     * texture's own alpha is left alone (the glint shader only uses it for
     * a {@code < 0.1} discard, and the vanilla textures are fully opaque).
     */
    private static void tint(Identifier id, TextureContents contents, int argb) {
        NativeImage image = contents.image();
        if (image == null) return;

        float strength = ARGB.alpha(argb) / 255.0f;

        int before = image.getPixel(0, 0);
        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int px = image.getPixel(x, y);
                float intensity = Math.max(ARGB.red(px),
                        Math.max(ARGB.green(px), ARGB.blue(px))) / 255.0f;
                image.setPixel(x, y, ARGB.color(ARGB.alpha(px),
                        Math.round(ARGB.red(argb)   * intensity * strength),
                        Math.round(ARGB.green(argb) * intensity * strength),
                        Math.round(ARGB.blue(argb)  * intensity * strength)));
            }
        }
        AuroraClient.LOGGER.info("[glintColor] colorized {} ({}x{}): (0,0) {} -> {}",
                id, width, height, Integer.toHexString(before), Integer.toHexString(image.getPixel(0, 0)));
    }

    /**
     * END_CLIENT_TICK (render thread, between frames). Short-circuits unless
     * (enabled, color, tintArmor) changed, mirroring Hit Color's
     * OverlayReloadListener tick pattern — the enabled check lives at the
     * point the effect is applied, never only in the settings UI.
     */
    public static void tick() {
        AuroraConfig cfg = AuroraConfig.get();
        int color = cfg.glintColor;
        int enabled = cfg.glintColorEnabled ? 1 : 0;
        int tintArmor = cfg.glintColorTintArmor ? 1 : 0;

        if (!initialized) {
            // First tick after boot: the glint textures haven't loaded yet,
            // and whenever they do, the hook reads the config fresh. Only arm
            // the change detector — forcing a load now would upload two
            // textures nothing asked for.
            initialized = true;
            lastColor = color;
            lastEnabled = enabled;
            lastTintArmor = tintArmor;
            return;
        }
        if (color == lastColor && enabled == lastEnabled && tintArmor == lastTintArmor) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        // A resource reload in flight has its own async texture loads in
        // play; releasing a texture one of its PendingReloads still holds
        // would apply() onto a closed texture. Leave the change pending
        // (last* not updated) so the next tick retries.
        if (client.getOverlay() != null) return;

        lastColor = color;
        lastEnabled = enabled;
        lastTintArmor = tintArmor;

        TextureManager textures = client.getTextureManager();
        for (Identifier id : new Identifier[]{GLINT_ITEM, GLINT_ARMOR}) {
            textures.release(id);
            textures.getTexture(id); // synchronous reload through the load hook
        }
        AuroraClient.LOGGER.info("[glintColor] config changed (enabled={}, color={}, armor={}) — textures reloaded",
                enabled == 1, Integer.toHexString(color), tintArmor == 1);
    }
}
