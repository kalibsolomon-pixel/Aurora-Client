package com.aurora.client.mixin;

import com.aurora.client.ui.util.AuroraFontRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Font.class, priority = 1500)
public abstract class MixinFont {

    @Inject(
            method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void prepareTextStringIntercept(String text, float x, float y, int color, boolean dropShadow, int light, CallbackInfoReturnable<Font.PreparedText> cir) {
        Style style = AuroraFontRenderer.getActiveStyle();
        if (style != null && text != null) {
            // Plain strings never carry their own font — always safe to apply.
            Component comp = AuroraFontRenderer.styledLiteral(text, style);
            Font self = (Font) (Object) this;
            cir.setReturnValue(self.prepareText(comp.getVisualOrderText(), x, y, color, dropShadow, false, light));
        }
    }

    @ModifyVariable(
            method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private FormattedCharSequence modifyPrepareTextSequence(FormattedCharSequence sequence) {
        Style style = AuroraFontRenderer.getActiveStyle();
        if (style != null && sequence != null) {
            // Skip sequences that already carry a font (e.g. Material Symbols
            // icon glyphs or text already styled by the interceptors above).
            if (sequenceHasCustomFont(sequence)) {
                return sequence;
            }
            // Font swap only — the sequence's per-character colors and other
            // style attributes survive (this is the 1.21.11 path the chat
            // input's CommandSuggestions coloring renders through, via
            // GuiTextRenderState at flush time).
            return AuroraFontRenderer.withCustomFont(sequence, style);
        }
        return sequence;
    }

    // ========================================================================
    //  Width / measurement interception
    //
    //  Critical for correct layout: vanilla code measures text with
    //  Font.width(plainString) using the DEFAULT font, then (via the
    //  prepareText interceptors above) renders with the CUSTOM font whose
    //  glyphs have different widths. Any centering/truncation math built
    //  on the default-font measurement then lands off-center or clips.
    //
    //  These interceptors re-route width() and plainSubstrByWidth()
    //  through the same active style so measure == render.
    // ========================================================================

    /**
     * Recursion guard. The width() interceptors below measure text by
     * re-routing through {@code Font.width(Component)}, which itself is a
     * {@code width(FormattedText)} overload that this mixin also intercepts.
     * Without this guard the call would recurse infinitely. While the guard
     * is held we let vanilla measure the already-styled component directly.
     */
    private static final ThreadLocal<int[]> MEASURING = ThreadLocal.withInitial(() -> new int[1]);

    @Inject(
            method = "width(Ljava/lang/String;)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private void aurora$widthString(String text, CallbackInfoReturnable<Integer> cir) {
        if (MEASURING.get()[0] > 0) return; // already inside a re-measurement
        Style style = AuroraFontRenderer.getActiveStyle();
        if (style == null || text == null || text.isEmpty()) return;
        // Measure the styled component instead of the bare string.
        Font self = (Font) (Object) this;
        MEASURING.get()[0]++;
        try {
            cir.setReturnValue(self.width(AuroraFontRenderer.styledLiteral(text, style)));
        } finally {
            MEASURING.get()[0]--;
        }
    }

    @Inject(
            method = "width(Lnet/minecraft/util/FormattedCharSequence;)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private void aurora$widthCharSequence(FormattedCharSequence text, CallbackInfoReturnable<Integer> cir) {
        if (MEASURING.get()[0] > 0) return; // already inside a re-measurement
        Style style = AuroraFontRenderer.getActiveStyle();
        if (style == null || text == null) return;
        // Sequences that already carry a font are measured correctly by vanilla.
        if (sequenceHasCustomFont(text)) return;
        String plain = AuroraFontRenderer.getSequenceString(text);
        if (plain.isEmpty()) return;
        Font self = (Font) (Object) this;
        MEASURING.get()[0]++;
        try {
            cir.setReturnValue(self.width(AuroraFontRenderer.styledLiteral(plain, style)));
        } finally {
            MEASURING.get()[0]--;
        }
    }

    @Inject(
            method = "width(Lnet/minecraft/network/chat/FormattedText;)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private void aurora$widthFormatted(net.minecraft.network.chat.FormattedText text, CallbackInfoReturnable<Integer> cir) {
        if (MEASURING.get()[0] > 0) return; // already inside a re-measurement
        Style style = AuroraFontRenderer.getActiveStyle();
        if (style == null || text == null) return;
        // Only re-route plain (unstyled) Components. Components that already
        // carry a font (ours or an icon font) are measured correctly by
        // vanilla, so we let them fall through.
        if (text instanceof Component c) {
            if (c.getStyle().getFont() != AuroraFontRenderer.DEFAULT_FONT) return;
            if (c.getString().isEmpty()) return;
            Font self = (Font) (Object) this;
            MEASURING.get()[0]++;
            try {
                cir.setReturnValue(self.width(AuroraFontRenderer.styledCopy(c, style)));
            } finally {
                MEASURING.get()[0]--;
            }
        }
    }

    @Inject(
            method = "plainSubstrByWidth(Ljava/lang/String;I)Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void aurora$plainSubstrByWidth(String text, int maxWidth, CallbackInfoReturnable<String> cir) {
        aurora$plainSubstrByWidthImpl(text, maxWidth, false, cir);
    }

    @Inject(
            method = "plainSubstrByWidth(Ljava/lang/String;IZ)Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void aurora$plainSubstrByWidthTail(String text, int maxWidth, boolean tail, CallbackInfoReturnable<String> cir) {
        aurora$plainSubstrByWidthImpl(text, maxWidth, tail, cir);
    }

    /**
     * Reimplement plainSubstrByWidth using the active style's glyph widths
     * so truncation matches what will actually render. {@code tail=true}
     * keeps the trailing portion (vanilla {@code plainTailByWidth});
     * {@code false} keeps the leading portion ({@code plainHeadByWidth}).
     */
    private void aurora$plainSubstrByWidthImpl(String text, int maxWidth, boolean tail,
                                               CallbackInfoReturnable<String> cir) {
        Style style = AuroraFontRenderer.getActiveStyle();
        if (style == null || text == null || text.isEmpty() || maxWidth <= 0) return;
        Font self = (Font) (Object) this;
        int len = text.length();
        MEASURING.get()[0]++;
        try {
            if (self.width(AuroraFontRenderer.styledLiteral(text, style)) <= maxWidth) {
                cir.setReturnValue(text);
                return;
            }
            int lo = 0, hi = len;
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (!tail) {
                    // Avoid splitting a UTF-16 surrogate pair.
                    int split = mid;
                    if (split > 0 && split < len && Character.isLowSurrogate(text.charAt(split))) split--;
                    if (split <= lo) split = mid + 1;
                    if (self.width(AuroraFontRenderer.styledLiteral(text.substring(0, split), style)) <= maxWidth) {
                        lo = split;
                    } else {
                        hi = mid - 1;
                    }
                } else {
                    int start = len - mid;
                    if (start > 0 && start < len && Character.isLowSurrogate(text.charAt(start))) start++;
                    int actualLen = len - start;
                    if (actualLen <= lo) {
                        start = len - (mid + 1);
                        if (start < 0) { hi = mid - 1; continue; }
                        actualLen = len - start;
                    }
                    if (self.width(AuroraFontRenderer.styledLiteral(text.substring(start), style)) <= maxWidth) {
                        lo = actualLen;
                    } else {
                        hi = mid - 1;
                    }
                }
            }
            cir.setReturnValue(tail ? text.substring(len - lo) : text.substring(0, lo));
        } finally {
            MEASURING.get()[0]--;
        }
    }

    // ========================================================================
    //  Helpers
    // ========================================================================

    private static final class CustomFontCheck implements net.minecraft.util.FormattedCharSink {
        boolean found;
        @Override public boolean accept(int index, Style style, int codePoint) {
            if (style.getFont() != AuroraFontRenderer.DEFAULT_FONT) {
                found = true;
                return false;
            }
            return true;
        }
    }

    private static final ThreadLocal<CustomFontCheck> CUSTOM_FONT_CHECK =
            ThreadLocal.withInitial(CustomFontCheck::new);

    /**
     * Returns true if any character in the sequence already has a non-default
     * font set (e.g. Material Symbols icon glyphs), meaning we must NOT
     * override the font for that text. Reuses a ThreadLocal sink to avoid
     * per-call lambda and array allocations on every text draw.
     */
    private static boolean sequenceHasCustomFont(FormattedCharSequence sequence) {
        CustomFontCheck check = CUSTOM_FONT_CHECK.get();
        check.found = false;
        sequence.accept(check);
        return check.found;
    }

}
