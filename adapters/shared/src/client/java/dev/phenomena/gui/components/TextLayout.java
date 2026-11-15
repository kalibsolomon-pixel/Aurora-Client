package dev.phenomena.gui.components;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Whole-word paragraphs measured in the active Aurora font; oversized words stay bounded. */
public final class TextLayout {
  private TextLayout() {}

  public static List<FormattedCharSequence> wrap(Font font, String text, int width) {
    return lines(font, text, width).stream()
        .map(line -> Component.literal(line).getVisualOrderText())
        .toList();
  }

  /** The same whole-word lines as plain strings. */
  public static List<String> lines(Font font, String text, int width) {
    var lines = new ArrayList<String>();
    for (String paragraph : text.split("\\n", -1)) {
      String line = "";
      for (String word : paragraph.strip().split("\\s+")) {
        String candidate = line.isEmpty() ? word : line + " " + word;
        if (!line.isEmpty() && font.width(candidate) > width) {
          lines.add(line);
          line = "";
        }
        String fitted = KButton.fit(font, word, width);
        line = line.isEmpty() ? fitted : line + " " + fitted;
      }
      lines.add(line);
    }
    return List.copyOf(lines);
  }

  /**
   * At most {@code max} whole-word lines; when text remains, the last line ends in a bounded
   * ellipsis.
   */
  public static List<String> lines(Font font, String text, int width, int max) {
    var all = lines(font, text, width);
    if (all.size() <= max) return all;
    var kept = new ArrayList<>(all.subList(0, max - 1));
    kept.add(KButton.fit(font, String.join(" ", all.subList(max - 1, all.size())), width));
    return List.copyOf(kept);
  }
}
