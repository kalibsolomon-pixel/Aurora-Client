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
        StringBuilder plain = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        int[][] starts = {new int[8]};
        boolean[] leftToRight = {true};
        text.visit((style, contents) -> {
            boolean whole = StringDecomposer.iterateFormatted(contents, style, (position, charStyle, codePoint) -> {
                if (codePoint >= 0x0590 && !leftToRight(codePoint)) {
                    leftToRight[0] = false;
                    return false;
                }
                int runs = styles.size();
                // A run continues while each character's style equals the run's first one,
                // as SubStringSource.substring compares them.
                if (runs == 0 || !charStyle.equals(styles.get(runs - 1))) {
                    if (runs == starts[0].length) starts[0] = java.util.Arrays.copyOf(starts[0], runs * 2);
                    starts[0][runs] = plain.length();
                    styles.add(charStyle);
                }
                plain.appendCodePoint(codePoint);
                return true;
            });
            return whole ? Optional.empty() : STOP;
        }, Style.EMPTY);
        if (!leftToRight[0]) return null;
        String all = plain.toString();
        List<FormattedCharSequence> parts = new ArrayList<>(styles.size());
        for (int i = 0; i < styles.size(); i++) {
            int end = i + 1 < styles.size() ? starts[0][i + 1] : all.length();
            parts.add(FormattedCharSequence.forward(all.substring(starts[0][i], end), styles.get(i)));
        }
        return FormattedCharSequence.composite(parts);
    }

    /** Bidi classes that resolve to level 0 in a left-to-right paragraph with no right-to-left text. */
    private static boolean leftToRight(int codePoint) {
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
