package parityprobe;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/** Actual glyph, fallback, decoration and obfuscation acceptance after a reload. */
final class FontSpecimenScreen extends Screen {
    private final Style style = Style.EMPTY.withFont(new FontDescription.Resource(
            Identifier.parse("aurora:roboto_semibold_ui")));

    FontSpecimenScreen() { super(Component.literal("Font acceptance")); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xff20242b);
        g.text(font, Component.literal("Aurora 0123456789").withStyle(style), 16, 20, 0xffffffff, false);
        g.text(font, Component.literal("\u00c9\u03a9\u0416\u4e16\u754c").withStyle(style), 16, 40, 0xffffffff, false);
        g.text(font, Component.literal("Bold italic").withStyle(style.withBold(true).withItalic(true)), 16, 60, 0xffffffff, false);
        g.text(font, Component.literal("Underline / strikethrough").withStyle(style.withUnderlined(true).withStrikethrough(true)), 16, 80, 0xffffffff, false);
        g.text(font, Component.literal("Obfuscated 0123456789").withStyle(style.withObfuscated(true)), 16, 100, 0xffffffff, false);
    }
}
