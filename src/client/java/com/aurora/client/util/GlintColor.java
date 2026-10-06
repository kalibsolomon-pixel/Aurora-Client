package com.aurora.client.util;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

/**
 * Recolors the active resource pack's two glint textures. The default path
 * uses vanilla additive blending and the original value ramp. Alternative
 * Glint builds colored masks instead:
 * Black is transparent; source contrast is normalized into alpha coverage,
 * with the darkest base removed for textures that have no black pixels.
 * Pattern pixels carry the selected RGB without multiplying it by brightness. GlintRenderTypes
 * supplies alpha blending and a shader that applies fog/glint strength to
 * coverage, so pure black casts moving shadows instead of adding zero light.
 *
 * Disabled textures load untouched. Config changes synchronously reload only
 * these two textures on END_CLIENT_TICK, between frames; reloads in flight
 * defer that work. No GPU texture is destroyed during rendering.
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
    private static int lastIntensity = -1;
    private static int lastAlternative = -1;

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
        tint(id, contents, cfg.glintColor, cfg.glintColorIntensity, cfg.glintColorAlternative);
    }

    /** Bake RGB and mask coverage before vanilla uploads and closes the image. */
    private static void tint(Identifier id, TextureContents contents, int argb, int intensityPercent, boolean alternative) {
        NativeImage image = contents.image();
        if (image == null) return;


        int before = image.getPixel(0, 0);
        int width = image.getWidth();
        int height = image.getHeight();
        int base = 255, peak = 0;
        if (alternative) for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int px = image.getPixel(x, y);
            if ((px >>> 24) == 0) continue;
            int value = Math.max((px >>> 16) & 255, Math.max((px >>> 8) & 255, px & 255));
            base = Math.min(base, value);
            peak = Math.max(peak, value);
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int px = image.getPixel(x, y);
                image.setPixel(x, y, alternative
                        ? GlintMask.colorize(px, argb, intensityPercent, base, peak)
                        : GlintMask.additive(px, argb, intensityPercent));
            }
        }
        AuroraClient.LOGGER.info("[glintColor] {} {} ({}x{}): (0,0) {} -> {}",
                alternative ? "masked" : "colorized", id, width, height,
                Integer.toHexString(before), Integer.toHexString(image.getPixel(0, 0)));
    }

    /**
     * END_CLIENT_TICK (render thread, between frames). Short-circuits unless
     * (enabled, color, intensity, alternative, tintArmor) changed, mirroring Hit Color's
     * OverlayReloadListener tick pattern — the enabled check lives at the
     * point the effect is applied, never only in the settings UI.
     */
    public static void tick() {
        AuroraConfig cfg = AuroraConfig.get();
        int color = cfg.glintColor;
        int intensity = cfg.glintColorIntensity;
        int alternative = cfg.glintColorAlternative ? 1 : 0;
        int enabled = cfg.glintColorEnabled ? 1 : 0;
        int tintArmor = cfg.glintColorTintArmor ? 1 : 0;

        if (!initialized) {
            // First tick after boot: the glint textures haven't loaded yet,
            // and whenever they do, the hook reads the config fresh. Only arm
            // the change detector — forcing a load now would upload two
            // textures nothing asked for.
            initialized = true;
            lastColor = color;
            lastIntensity = intensity;
            lastAlternative = alternative;
            lastEnabled = enabled;
            lastTintArmor = tintArmor;
            return;
        }
        if (color == lastColor && intensity == lastIntensity && alternative == lastAlternative
                && enabled == lastEnabled && tintArmor == lastTintArmor) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        // A resource reload in flight has its own async texture loads in
        // play; releasing a texture one of its PendingReloads still holds
        // would apply() onto a closed texture. Leave the change pending
        // (last* not updated) so the next tick retries.
        if (client.getOverlay() != null) return;

        lastColor = color;
        lastIntensity = intensity;
        lastAlternative = alternative;
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
