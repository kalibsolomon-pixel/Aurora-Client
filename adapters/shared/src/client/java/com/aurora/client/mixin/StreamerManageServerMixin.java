package com.aurora.client.mixin;

import com.aurora.client.util.StreamerMode;
import com.aurora.client.util.StreamerPrivacy;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Masks the server address text box on the Add/Edit Server screen while
 * Streamer Mode's Hide Server Address option is active.
 */
@Mixin(ManageServerScreen.class)
public abstract class StreamerManageServerMixin {

    @Shadow private EditBox ipEdit;

    @Inject(method = "init", at = @At("TAIL"))
    private void aurora$maskManageServerIp(CallbackInfo ci) {
        if (ipEdit == null) return;
        ipEdit.addFormatter((text, offset) -> StreamerMode.hideServerAddress()
                ? FormattedCharSequence.forward(StreamerPrivacy.maskEditText(text), Style.EMPTY)
                : null);
    }
}
