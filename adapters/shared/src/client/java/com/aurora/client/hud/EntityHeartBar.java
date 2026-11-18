package com.aurora.client.hud;

import com.aurora.client.util.AuroraHealthSnapshots;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Health Indicators' Hearts mode: an entity's health drawn above its head with
 * vanilla's own heart sprites and animation ({@link HeartBar}), billboarded
 * like a nametag. Sprites come from the GUI atlas, so resource packs that
 * restyle the HUD hearts restyle these too.
 *
 * <p>Drawn as custom geometry with the text render types, the same way the
 * font draws an atlas sprite in a nametag; a nametag that is not discrete
 * also shows through walls, faintly, like vanilla's.
 */
public final class EntityHeartBar {
    private EntityHeartBar() {}

    /** Vanilla nametag scale: one GUI pixel is 1/40 of a block. */
    private static final float SCALE = 0.025f;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int SEE_THROUGH_COLOR = 0x40FFFFFF;
    /** Coplanar layers (container, overlay, heart) are separated like the font's effect depth. */
    private static final float LAYER_DEPTH = 0.01f;

    private static final HeartBar.Animations ANIMATIONS = new HeartBar.Animations();
    private static final Matrix4f ROTATION = new Matrix4f();
    private static final Identifier[] SPRITE_IDS = new Identifier[HeartBar.SPRITE_COUNT];
    static {
        for (int i = 0; i < SPRITE_IDS.length; i++) SPRITE_IDS[i] = Identifier.withDefaultNamespace(HeartBar.spritePath(i));
    }
    private static GlyphRenderTypes renderTypes;

    public static void submit(PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera,
                              Vec3 attachment, boolean seeThrough, AuroraHealthSnapshots.Snapshot snap) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        TextureAtlas atlas = guiAtlas(client);
        if (atlas == null) return;

        long millis = System.currentTimeMillis();
        long tick = client.level.getGameTime();
        HeartBar.Animation animation = ANIMATIONS.get(snap.uuid, millis);
        animation.update(snap.health, snap.hurt, tick, millis);
        boolean hardcore = client.level.getLevelData().isHardcore();
        int[] bar = HeartBar.layout(snap.health, snap.maxHealth, snap.absorption, animation.displayHealth(),
                animation.blink(tick), snap.frozen ? HeartBar.Type.FROZEN : HeartBar.Type.NORMAL,
                hardcore, (int) tick);
        if (bar.length == 0) return;
        float left = -HeartBar.width(bar) / 2.0f;
        TextureAtlasSprite[] icons = new TextureAtlasSprite[HeartBar.SPRITE_COUNT];
        for (int i = 2; i < bar.length; i += HeartBar.STRIDE) {
            if (icons[bar[i]] == null) icons[bar[i]] = atlas.getSprite(SPRITE_IDS[bar[i]]);
        }

        poses.pushPose();
        poses.translate(attachment.x, attachment.y + 0.5, attachment.z);
        poses.mulPose(ROTATION.rotation(camera.orientation));
        poses.scale(SCALE, -SCALE, SCALE);
        collector.submitCustomGeometry(poses, renderTypes.select(Font.DisplayMode.NORMAL),
                (pose, buffer) -> quads(pose, buffer, bar, icons, left, 0xFFFFFFFF, true));
        if (seeThrough) {
            collector.submitCustomGeometry(poses, renderTypes.select(Font.DisplayMode.SEE_THROUGH),
                    (pose, buffer) -> quads(pose, buffer, bar, icons, left, SEE_THROUGH_COLOR, false));
        }
        poses.popPose();
    }

    private static void quads(PoseStack.Pose pose, VertexConsumer buffer, int[] bar, TextureAtlasSprite[] icons,
                              float left, int color, boolean layered) {
        float size = HeartBar.SPRITE_SIZE;
        for (int i = 0; i < bar.length; i += HeartBar.STRIDE) {
            TextureAtlasSprite sprite = icons[bar[i + 2]];
            float x0 = left + bar[i], y0 = bar[i + 1] - size;
            float x1 = x0 + size, y1 = y0 + size;
            float z = layered ? bar[i + 3] * LAYER_DEPTH : 0.0f;
            buffer.addVertex(pose, x0, y0, z).setColor(color).setUv(sprite.getU0(), sprite.getV0()).setLight(FULL_BRIGHT);
            buffer.addVertex(pose, x0, y1, z).setColor(color).setUv(sprite.getU0(), sprite.getV1()).setLight(FULL_BRIGHT);
            buffer.addVertex(pose, x1, y1, z).setColor(color).setUv(sprite.getU1(), sprite.getV1()).setLight(FULL_BRIGHT);
            buffer.addVertex(pose, x1, y0, z).setColor(color).setUv(sprite.getU1(), sprite.getV0()).setLight(FULL_BRIGHT);
        }
    }

    private static TextureAtlas guiAtlas(Minecraft client) {
        TextureAtlas atlas;
        try {
            atlas = client.getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
        } catch (RuntimeException e) {
            return null;
        }
        if (renderTypes == null) renderTypes = GlyphRenderTypes.createForColorTexture(atlas.location());
        return atlas;
    }
}
