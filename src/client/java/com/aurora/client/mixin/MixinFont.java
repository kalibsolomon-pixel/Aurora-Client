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
            Component comp = Component.literal(text).withStyle(style);
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
            cir.setReturnValue(self.width(Component.literal(text).withStyle(style)));
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
            cir.setReturnValue(self.width(Component.literal(plain).withStyle(style)));
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
                cir.setReturnValue(self.width(c.copy().withStyle(style)));
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

        if (!tail) {
            // Keep leading characters while they fit.
            StringBuilder sb = new StringBuilder();
            int width = 0;
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                String ch = new String(Character.toChars(cp));
                int charW = self.width(Component.literal(ch).withStyle(style));
                if (width + charW > maxWidth) break;
                sb.append(ch);
                width += charW;
                i += Character.charCount(cp);
            }
            cir.setReturnValue(sb.toString());
        } else {
            // Keep trailing characters while they fit.
            StringBuilder sb = new StringBuilder();
            int width = 0;
            for (int i = text.length(); i > 0; ) {
                int cp = text.codePointBefore(i);
                String ch = new String(Character.toChars(cp));
                int charW = self.width(Component.literal(ch).withStyle(style));
                if (width + charW > maxWidth) break;
                sb.insert(0, ch);
                width += charW;
                i -= Character.charCount(cp);
            }
            cir.setReturnValue(sb.toString());
        }
    }

    // ========================================================================
    //  Helpers
    // ========================================================================

    /**
     * Returns true if any character in the sequence already has a non-default
     * font set (e.g. Material Symbols icon glyphs), meaning we must NOT
     * override the font for that text.
     */
    private static boolean sequenceHasCustomFont(FormattedCharSequence sequence) {
        boolean[] found = { false };
        sequence.accept((index, style, codePoint) -> {
            // "Custom font" = any font that differs from the default empty
            // style's font (e.g. Material Symbols icon glyphs).
            if (style.getFont() != AuroraFontRenderer.DEFAULT_FONT) {
                found[0] = true;
                return false; // stop iterating
            }
            return true;
        });
        return found[0];
    }

}