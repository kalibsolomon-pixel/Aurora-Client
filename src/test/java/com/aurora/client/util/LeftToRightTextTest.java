package com.aurora.client.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.FormattedBidiReorder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LeftToRightTextTest {
    private static final String LEFT_TO_RIGHT = "abcXYZ 0123456789 .,:;-+/%$#()[]\t\n§cé ü ß Ωλ Ж ❤★✔ ⚔ 漢字かな 한국 ́ ​ ‎ 😀";
    /** Hebrew, Arabic, an Arabic-Indic digit, RLM, RLE and RLI: each alone can reorder a line. */
    private static final String RIGHT_TO_LEFT = "שלוםمرحبا٣‏‫⁧";

    /** Every (index, style, code point) a sink sees: positions reveal how the sequence is cut. */
    private static List<String> visits(FormattedCharSequence sequence) {
        List<String> out = new ArrayList<>();
        sequence.accept((index, style, codePoint) -> {
            out.add(index + ":" + style + ":" + codePoint);
            return true;
        });
        return out;
    }

    private static String randomText(Random random, String alphabet, int length) {
        StringBuilder text = new StringBuilder();
        int[] points = alphabet.codePoints().toArray();
        for (int i = 0; i < length; i++) text.appendCodePoint(points[random.nextInt(points.length)]);
        return text.toString();
    }

    private static FormattedText randomFormatted(Random random, String alphabet) {
        Style[] styles = {Style.EMPTY, Style.EMPTY.withColor(ChatFormatting.RED), Style.EMPTY.withBold(true),
                Style.EMPTY.withColor(ChatFormatting.RED).withBold(true), Style.EMPTY.withItalic(true)};
        MutableComponent root = Component.literal(randomText(random, alphabet, random.nextInt(0, 6)))
                .withStyle(styles[random.nextInt(styles.length)]);
        for (int i = random.nextInt(0, 5); i > 0; i--) {
            root.append(Component.literal(randomText(random, alphabet, random.nextInt(0, 12))).withStyle(styles[random.nextInt(styles.length)]));
        }
        return random.nextBoolean() ? root : FormattedText.of(randomText(random, alphabet, random.nextInt(0, 30)));
    }

    @Test void leftToRightTextComesOutExactlyAsVanillaOrdersIt() {
        Random random = new Random(42);
        for (int i = 0; i < 5000; i++) {
            FormattedText text = randomFormatted(random, LEFT_TO_RIGHT);
            FormattedCharSequence fast = LeftToRightText.reorder(text);
            assertNotNull(fast, () -> "left-to-right text must take the fast path: " + text.getString());
            assertEquals(visits(FormattedBidiReorder.reorder(text, false)), visits(fast), text::getString);
        }
    }

    /** Every BMP code point the fast path accepts, between two letters, comes out as vanilla orders and shapes it. */
    @Test void everyAcceptedCodePointMatchesVanilla() {
        int accepted = 0;
        for (int cp = 0; cp <= 0xFFFF; cp++) {
            if (Character.isSurrogate((char) cp) || cp == '§') continue;
            FormattedText text = FormattedText.of("a" + Character.toString(cp) + "b");
            FormattedCharSequence fast = LeftToRightText.reorder(text);
            if (fast == null) continue;
            accepted++;
            final int point = cp;
            assertEquals(visits(FormattedBidiReorder.reorder(text, false)), visits(fast), () -> Integer.toHexString(point));
        }
        assertTrue(accepted > 50_000, "most of the BMP takes the fast path: " + accepted);
    }

    @Test void anythingThatCouldReorderGoesToVanilla() {
        Random random = new Random(43);
        for (int i = 0; i < 2000; i++) {
            // No section sign: it would swallow the next character as a formatting code.
            String plain = LEFT_TO_RIGHT.replace("§", "");
            String mixed = randomText(random, plain, random.nextInt(0, 8))
                    + randomText(random, RIGHT_TO_LEFT, 1) + randomText(random, plain, random.nextInt(0, 8));
            assertNull(LeftToRightText.reorder(FormattedText.of(mixed)), mixed);
        }
        assertNotNull(LeftToRightText.reorder(FormattedText.of("")));
        assertEquals(visits(FormattedBidiReorder.reorder(FormattedText.of(""), false)), visits(LeftToRightText.reorder(FormattedText.of(""))));
    }

    @Test void sameStyleFragmentsRetainVanillaFormattingAndVisitIndices() {
        for (FormattedText text : List.of(
                Component.literal("A\ud83d").append(Component.literal("\ude00B")),
                Component.literal("A").append("漢字").append("😀").append("B"),
                Component.literal("prefix").append("§c").append("suffix"),
                Component.literal("one §a").append(Component.literal("two").withStyle(ChatFormatting.GREEN))
                        .append("§rthree"))) {
            assertEquals(visits(FormattedBidiReorder.reorder(text, false)), visits(LeftToRightText.reorder(text)));
        }
    }
}
