package com.aurora.client.media;

import com.aurora.client.AuroraClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The Now Playing HUD's cover art: one {@value SystemMediaBridge#COVER_SIZE}-pixel
 * texture, created on first use and re-uploaded in place when a new cover
 * arrives, so a track change allocates nothing on the GPU and no texture is ever
 * destroyed mid-frame. Render thread only.
 */
public final class MediaCoverTexture {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "media/now_playing_cover");
    private static final int SIZE = SystemMediaBridge.COVER_SIZE;

    private static DynamicTexture texture;
    private static long uploadedSerial = -1;
    private static String uploadedKey;
    private static boolean broken;

    private MediaCoverTexture() {}

    /**
     * The cover texture for {@code state}'s track, uploading a newly arrived
     * cover first; null while that track has none (or the texture failed).
     */
    public static Identifier forTrack(MediaTrackState state) {
        if (broken || !state.active()) return null;
        SystemMediaBridge.Cover cover = SystemMediaBridge.currentCover();
        if (cover == null || !cover.trackKey().equals(state.trackKey())) return null;
        if (cover.serial() != uploadedSerial) upload(cover);
        return cover.trackKey().equals(uploadedKey) ? ID : null;
    }

    private static void upload(SystemMediaBridge.Cover cover) {
        uploadedSerial = cover.serial();
        try (NativeImage image = NativeImage.read(cover.png())) {
            if (image.getWidth() != SIZE || image.getHeight() != SIZE) return;
            if (texture == null) {
                texture = new DynamicTexture(() -> "aurora_now_playing_cover", SIZE, SIZE, false);
                Minecraft.getInstance().getTextureManager().register(ID, texture);
            }
            texture.getPixels().copyFrom(image);
            texture.upload();
            uploadedKey = cover.trackKey();
        } catch (Throwable t) {
            // One bad cover just shows none; a failing texture turns covers off for the session.
            if (texture == null) broken = true;
            AuroraClient.LOGGER.debug("[Media] cover art not shown: {}", t.toString());
        }
    }
}
