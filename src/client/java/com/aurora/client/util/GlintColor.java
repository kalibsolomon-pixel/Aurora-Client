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
 * {@code mixin/SimpleTextureMixin}).
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
 * multiplies every pixel's channels by the picker color (tint semantics —
 * the pattern's per-pixel intensity carries the shimmer structure through
 * untouched). This runs inside {@code SimpleTexture.loadContents}, before
 * vanilla uploads the texture; vanilla closes the NativeImage right after
 * upload, so this is the only CPU-side moment the pixels exist.
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
     * The load hook (called at RETURN of SimpleTexture.loadContents by the
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
     * Tint semantics: because the glint blend is additive with the source
     * RGB as the weight, each pixel's channels ARE its additive energy.
     * Multiplying per-channel by the picker color re-hues the pattern while
     * preserving its intensity structure exactly. The picker's alpha scales
     * overall strength (the texture's own alpha is left alone — the glint
     * shader only uses it for a {@code < 0.1} discard, and the vanilla
     * textures are fully opaque).
     */
    private static void tint(Identifier id, TextureContents contents, int argb) {
        NativeImage image = contents.image();
        if (image == null) return;

        float red   = ARGB.red(argb)   / 255.0f;
        float green = ARGB.green(argb) / 255.0f;
        float blue  = ARGB.blue(argb)  / 255.0f;
        float strength = ARGB.alpha(argb) / 255.0f;

        int before = image.getPixel(0, 0);
        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int px = image.getPixel(x, y);
                image.setPixel(x, y, ARGB.color(ARGB.alpha(px),
                        Math.round(ARGB.red(px)   * red   * strength),
                        Math.round(ARGB.green(px) * green * strength),
                        Math.round(ARGB.blue(px)  * blue  * strength)));
            }
        }
        AuroraClient.LOGGER.info("[glintColor] tinted {} ({}x{}): (0,0) {} -> {}",
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
