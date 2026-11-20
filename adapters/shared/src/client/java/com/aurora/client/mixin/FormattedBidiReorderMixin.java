package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.LeftToRightText;
import net.minecraft.client.resources.language.FormattedBidiReorder;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Left-to-Right Text (ADR 0009): in a left-to-right language, text with no
 * right-to-left characters skips ICU's bidirectional pass and gets the same
 * pieces from {@link LeftToRightText}. Anything else, and every right-to-left
 * language, takes vanilla's path.
 */
@Mixin(FormattedBidiReorder.class)
public abstract class FormattedBidiReorderMixin {
    @Inject(method = "reorder", at = @At("HEAD"), cancellable = true)
    private static void aurora$leftToRight(FormattedText text, boolean defaultRightToLeft,
                                           CallbackInfoReturnable<FormattedCharSequence> cir) {
        if (defaultRightToLeft || !AuroraConfig.get().perfLeftToRightText) return;
        FormattedCharSequence ordered = LeftToRightText.reorder(text);
        if (ordered != null) cir.setReturnValue(ordered);
    }
}
