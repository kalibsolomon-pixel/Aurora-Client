package com.aurora.client.util;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UCharacterDirection;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Left-to-Right Text (ADR 0009): vanilla gives every string it draws to
 * ICU's bidirectional algorithm ({@code FormattedBidiReorder}), building a
 * per-character style list and an Arabic-shaped copy first. Text with no
 * right-to-left letters, Arabic digits or explicit direction controls, in a
 * left-to-right language, always comes back as one left-to-right run cut at
 * each style change; this builds those same pieces directly.
 */
public final class LeftToRightText {
    private LeftToRightText() {}

    private static final Optional<Boolean> STOP = Optional.of(Boolean.TRUE);

    /**
     * The visual order {@code FormattedBidiReorder.reorder(text, false)} returns,
     * or null when the text has a character that could change it.
     */
    public static @Nullable FormattedCharSequence reorder(FormattedText text) {
        var runs = new Runs();
        return text.visit(runs, Style.EMPTY).isPresent() ? null : runs.finish();
    }

    private static final class Runs implements FormattedText.StyledContentConsumer<Boolean> {
        private Style style;
        private String literal;
        private StringBuilder buffer;
        private List<FormattedCharSequence> completed;

        @Override public Optional<Boolean> accept(Style next, String contents) {
            if (contents.isEmpty()) return Optional.empty();
            for (int i = 0; i < contents.length(); i++) {
                char ch = contents.charAt(i);
                if (ch == '\u00a7') return formatted(next, contents);
                if (ch < 0x0590) continue;
                int cp = contents.codePointAt(i);
                if (Character.isSupplementaryCodePoint(cp)) i++;
                else if (Character.isSurrogate(ch)) return formatted(next, contents);
                if (!leftToRight(cp)) return STOP;
            }
            select(next);
            if (literal == null && buffer == null) literal = contents;
            else writable().append(contents);
            return Optional.empty();
        }

        // Let vanilla interpret section codes and replace unpaired surrogates
        // inside their original component before joining any neighbouring text.
        private Optional<Boolean> formatted(Style next, String contents) {
            return StringDecomposer.iterateFormatted(contents, next, (position, charStyle, cp) -> {
                if (cp >= 0x0590 && !leftToRight(cp)) return false;
                select(charStyle);
                writable().appendCodePoint(cp);
                return true;
            }) ? Optional.empty() : STOP;
        }

        // Sequences restart character indices on a style change. Adjacent equal
        // styles must share a run even across component boundaries.
        private void select(Style next) {
            if (style == next || next.equals(style)) return;
            if (style != null) {
                if (completed == null) completed = new ArrayList<>();
                completed.add(sequence());
                literal = null;
                buffer = null;
            }
            style = next;
        }

        private StringBuilder writable() {
            if (buffer == null) {
                buffer = literal == null ? new StringBuilder() : new StringBuilder(literal);
                literal = null;
            }
            return buffer;
        }

        private FormattedCharSequence sequence() {
            return FormattedCharSequence.forward(buffer == null ? literal : buffer.toString(), style);
        }

        private FormattedCharSequence finish() {
            if (style == null) return FormattedCharSequence.EMPTY;
            if (completed == null) return sequence();
            completed.add(sequence());
            return FormattedCharSequence.composite(completed);
        }
    }

    /**
     * Bidi classes that resolve to level 0 in a left-to-right paragraph with no right-to-left text.
     * The Hebrew-to-Arabic blocks and the presentation forms always go to vanilla: its Arabic
     * shaping rewrites some of their marks (harakat) even in left-to-right text.
     */
    private static boolean leftToRight(int codePoint) {
        if (codePoint <= 0x08FF || (codePoint >= 0xFB1D && codePoint <= 0xFEFF)) return false;
        return switch (UCharacter.getDirection(codePoint)) {
            case UCharacterDirection.LEFT_TO_RIGHT, UCharacterDirection.EUROPEAN_NUMBER,
                 UCharacterDirection.EUROPEAN_NUMBER_SEPARATOR, UCharacterDirection.EUROPEAN_NUMBER_TERMINATOR,
                 UCharacterDirection.COMMON_NUMBER_SEPARATOR, UCharacterDirection.BLOCK_SEPARATOR,
                 UCharacterDirection.SEGMENT_SEPARATOR, UCharacterDirection.WHITE_SPACE_NEUTRAL,
                 UCharacterDirection.OTHER_NEUTRAL, UCharacterDirection.DIR_NON_SPACING_MARK,
                 UCharacterDirection.BOUNDARY_NEUTRAL -> true;
            default -> false;
        };
    }
}
