package com.aurora.client.mixin;

import com.aurora.client.util.StreamerMode;
import com.aurora.client.util.StreamerPrivacy;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Masks the server address text box on the Direct Connection screen while
 * Streamer Mode's Hide Server Address option is active.
 */
@Mixin(DirectJoinServerScreen.class)
public abstract class StreamerDirectJoinMixin {

    @Shadow private EditBox ipEdit;

    @Inject(method = "init", at = @At("TAIL"))
    private void aurora$maskDirectJoinIp(CallbackInfo ci) {
        if (ipEdit == null) return;
        ipEdit.addFormatter((text, offset) -> StreamerMode.hideServerAddress()
                ? FormattedCharSequence.forward(StreamerPrivacy.maskEditText(text), Style.EMPTY)
                : null);
    }
}
