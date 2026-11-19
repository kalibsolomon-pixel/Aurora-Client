package com.aurora.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;

/**
 * Replaces vanilla's ping bars icon with a numerical ping value (e.g. "23ms").
 * Vanilla's ping/pinging bars are simply not drawn (the incompatible and
 * unreachable status icons still are). The label takes the bars' slot at the
 * row's right edge, or sits just left of a status icon that is still drawn,
 * and the player count moves left by the extra width. It stays inside the
 * row: the gutter to its right belongs to the list's scrollbar.
 *
 * Player count is not added here because vanilla already renders it.
 */
@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class MultiplayerServerListWidgetMixin {

    /** Vanilla's status icon slot: 10 px wide, 5 px in from the row's right edge, 5 px gap before the player count. */
    @org.spongepowered.asm.mixin.Unique private static final int ICON_WIDTH = 10;
    @org.spongepowered.asm.mixin.Unique private static final int GAP = 5;
    @org.spongepowered.asm.mixin.Unique private static final int ICON_GAP = 4;

    @Shadow @Final private ServerData serverData;
    @Shadow private Identifier statusIcon;

    /** This frame's label, measured once when vanilla stores the player count's x and drawn at TAIL. */
    @org.spongepowered.asm.mixin.Unique private String aurora$label;
    @org.spongepowered.asm.mixin.Unique private int aurora$labelColor;
    @org.spongepowered.asm.mixin.Unique private int aurora$labelWidth;

    /** Skips the status icon's blit when it is one of the ping / pinging bar sprites. */
    @WrapWithCondition(method = "extractContent", require = 0, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private boolean aurora$hidePingBars(GuiGraphicsExtractor ctx, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
        return !aurora$isPingBars(sprite);
    }

    /** {@code server_list/ping_N} and {@code server_list/pinging_N} — the bars the label replaces. */
    @org.spongepowered.asm.mixin.Unique
    private static boolean aurora$isPingBars(Identifier sprite) {
        return sprite != null && sprite.getNamespace().equals("minecraft") && sprite.getPath().startsWith("server_list/ping");
    }

    /**
     * Moves the player count left of the label. Vanilla puts it {@code GAP}
     * left of the 10 px icon slot; the label (plus the icon, when one is still
     * drawn) is wider than that slot by {@code reserve - ICON_WIDTH}.
     */
    @ModifyVariable(method = "extractContent", at = @At("STORE"), name = "statusX", require = 0)
    private int aurora$makeRoomForLabel(int statusX) {
        aurora$measureLabel();
        if (aurora$label == null) return statusX;
        return statusX - (aurora$reserve() - ICON_WIDTH);
    }

    @Inject(method = "extractContent", at = @At("TAIL"), require = 0)
    private void aurora$replacePingIcon(
            GuiGraphicsExtractor ctx, int mouseX, int mouseY, boolean hovered, float tickDelta,
            CallbackInfo ci) {

        if (aurora$label == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return;

        // Pull row geometry from the Entry's own layout getters (1.21.11 stopped
        // passing top/left/width/height into renderContent). The getters live
        // on AbstractSelectionList.Entry, two levels above OnlineServerEntry, and
        // Mixin only resolves @Shadow methods against the target class's own
        // method table, so shadowing them fails at apply time. Call the public
        // inherited methods through a cast to the public ServerSelectionList.Entry
        // superclass instead (AbstractSelectionList.Entry itself is protected).
        ServerSelectionList.Entry self = (ServerSelectionList.Entry) (Object) this;
        int right = self.getContentRight() - GAP;
        if (!aurora$barsHidden()) right -= ICON_WIDTH + ICON_GAP;
        int textX = right - aurora$labelWidth;
        int textY = self.getContentY() + 1;
        // Mark this Aurora accent on a vanilla screen as non-window text: in
        // AURORA_ONLY the latency label stays vanilla like the search bars
        // on the same screens; All Text still styles it (owner ruling
        // 2026-11-17).
        com.aurora.client.ui.util.AuroraFontRenderer.setDrawingHud(true);
        try {
            ctx.text(client.font, aurora$label, textX, textY, aurora$labelColor, false);
        } finally {
            com.aurora.client.ui.util.AuroraFontRenderer.setDrawingHud(false);
        }
    }

    /** True when this frame's status icon is ping bars, which are not drawn, so the label takes their slot. */
    @org.spongepowered.asm.mixin.Unique
    private boolean aurora$barsHidden() {
        return statusIcon == null || aurora$isPingBars(statusIcon);
    }

    /** Width from the label's left edge to the row's right inset: label, plus the icon when it is still drawn. */
    @org.spongepowered.asm.mixin.Unique
    private int aurora$reserve() {
        return aurora$labelWidth + (aurora$barsHidden() ? 0 : ICON_WIDTH + ICON_GAP);
    }

    @org.spongepowered.asm.mixin.Unique
    private void aurora$measureLabel() {
        aurora$label = null;
        if (serverData == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return;

        long ping = serverData.ping;
        if (ping == -2L) {
            aurora$label = "...";
            aurora$labelColor = 0xFFAAAAAA;
        } else if (ping <= 0) {
            // An unreachable or incompatible icon already says why there is no ping.
            if (!aurora$barsHidden()) return;
            aurora$label = "?";
            aurora$labelColor = 0xFF888888;
        } else {
            aurora$label = ping + "ms";
            aurora$labelColor = ping < 50 ? 0xFF55FF55
                    : ping < 150 ? 0xFFFFFF55
                      : 0xFFFF5555;
        }
        // Measure in the same text scope the label draws in.
        com.aurora.client.ui.util.AuroraFontRenderer.setDrawingHud(true);
        try {
            aurora$labelWidth = client.font.width(aurora$label);
        } finally {
            com.aurora.client.ui.util.AuroraFontRenderer.setDrawingHud(false);
        }
    }
}
