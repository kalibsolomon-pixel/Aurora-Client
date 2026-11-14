package dev.phenomena;

import static org.junit.jupiter.api.Assertions.*;

import dev.phenomena.config.*;
import dev.phenomena.gui.ui.Layout;
import dev.phenomena.gui.ui.UiState;
import dev.phenomena.gui.ui.UiState.Tab;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Navigation state, responsive layout arithmetic and configuration compatibility. */
class InterfaceTest {

  @Test
  void tabSwitchingPreservesScrollAndFilters() {
    var s = new UiState();
    s.select(Tab.OFFENSE);
    s.offense = UiState.OffenseSection.REACH;
    s.setScroll(120);
    s.select(Tab.HISTORY);
    s.outcome = dev.phenomena.analytics.HistoryQuery.OutcomeFilter.LOSS;
    s.setScroll(40);
    s.select(Tab.SETTINGS);
    assertEquals(0, s.scroll());
    s.select(Tab.OFFENSE);
    assertEquals(UiState.OffenseSection.REACH, s.offense);
    assertEquals(120, s.scroll());
    s.offense = UiState.OffenseSection.AIM;
    assertEquals(0, s.scroll(), "each section has its own position");
    s.select(Tab.DEFENSE);
    s.defense = UiState.DefenseSection.SHIELDS;
    s.setScroll(30);
    s.select(Tab.FLOW);
    assertEquals(0, s.scroll());
    s.select(Tab.DEFENSE);
    assertEquals(30, s.scroll());
    s.select(Tab.HISTORY);
    assertEquals(40, s.scroll());
    assertEquals(dev.phenomena.analytics.HistoryQuery.OutcomeFilter.LOSS, s.outcome);
  }

  @Test
  void informationArchitectureHasSixShallowTabs() {
    assertEquals(
        List.of("Overview", "Offense", "Defense", "Fight Flow", "History", "Settings"),
        java.util.Arrays.stream(Tab.values()).map(t -> t.label).toList());
    assertEquals(6, UiState.OffenseSection.values().length);
    assertEquals(4, UiState.DefenseSection.values().length);
    assertEquals(3, UiState.FlowSection.values().length);
  }

  @Test
  void flowFocusClampsAndClearsComboSelection() {
    var s = new UiState();
    s.combo = 2;
    s.focus(5, 3);
    assertEquals(2, s.focus);
    assertEquals(-1, s.combo);
    s.focus(-1, 3);
    assertEquals(0, s.focus);
    s.focus(4, 0);
    assertEquals(0, s.focus);
  }

  @Test
  void detailNavigationAndDeletion() {
    var s = new UiState();
    s.openFight("a");
    assertEquals(Tab.HISTORY, s.tab);
    assertTrue(s.detail);
    s.setScroll(50);
    s.closeDetail();
    assertEquals(0, s.scroll(), "list keeps its own scroll");
    s.openFight("a");
    assertEquals(0, s.scroll(), "reopening a fight starts at the top");
    s.forget("a");
    assertNull(s.selectedFight);
    assertFalse(s.detail);
  }

  @Test
  void scrollClampsAfterResize() {
    var s = new UiState();
    s.setScroll(900);
    assertEquals(300, s.clampScroll(500, 200));
    assertEquals(0, s.clampScroll(100, 200));
    s.setScroll(-5);
    assertEquals(0, s.scroll());
  }

  @Test
  void frameAndTabsFitEveryGuiScale() {
    // Scaled GUI widths for 854x480 up to 3840x2160 at GUI scales 1-4.
    for (int w : new int[] {Layout.MIN_WIDTH, 427, 480, 640, 854, 960, 1280, 1920, 3840}) {
      for (boolean compact : new boolean[] {false, true}) {
        var frame = Layout.frame(w, 240, compact);
        assertTrue(frame.x() >= 0 && frame.right() <= w, "frame inside window at " + w);
        assertTrue(frame.w() <= Layout.MAX_FRAME);
        var tabs = Layout.segments(frame.x(), 0, frame.w(), 18, 4, 2);
        assertEquals(frame.right(), tabs.getLast().right(), "tabs span exactly the frame");
        for (int i = 1; i < tabs.size(); i++) assertTrue(tabs.get(i).x() >= tabs.get(i - 1).right());
        // Longest tab label is about 85 px in the Minecraft font.
        if (w >= 427) assertTrue(tabs.getFirst().w() >= 90, "tab label fits at " + w);
      }
    }
  }

  @Test
  void chipsWrapInsteadOfOverflowing() {
    var labels = List.of("Accuracy", "Reach", "Aim", "Timing", "Critical hits", "Sprint hits", "Jump resets", "Combos");
    var rects = Layout.wrap(labels, s -> s.length() * 6, 10, 50, 280, 14, 6, 3);
    for (var r : rects) assertTrue(r.x() >= 10 && r.right() <= 290, "chip inside width");
    assertTrue(Layout.rowsHeight(rects, 50) > 14, "wrapped onto more than one row");
    var wide = Layout.wrap(labels, s -> s.length() * 6, 10, 50, 1000, 14, 6, 3);
    assertEquals(14, Layout.rowsHeight(wide, 50));
  }

  @Test
  void columnsScrollbarAndReveal() {
    assertEquals(1, Layout.columns(80, 104, 4, 5));
    assertEquals(5, Layout.columns(2000, 104, 4, 5));
    assertEquals(3, Layout.columns(330, 104, 4, 5));
    var track = new Layout.Rect(0, 100, 3, 200);
    assertNull(Layout.thumb(track, 150, 0));
    var top = Layout.thumb(track, 800, 0);
    var bottom = Layout.thumb(track, 800, 600);
    assertEquals(100, top.y());
    assertEquals(300, bottom.bottom());
    assertEquals(500, Layout.reveal(0, 200, 600, 700));
    assertEquals(50, Layout.reveal(300, 200, 50, 70));
    assertEquals(300, Layout.reveal(300, 200, 350, 400));
  }


  @Test
  void hudOnlyAndChatModesMigrate() {
    for (var mode : Config.Display.values()) {
      var c = new Config();
      c.schemaVersion = 1;
      c.display = mode;
      c.validate();
      assertEquals(mode.chat(), c.chatSummary, mode.name());
      assertFalse(c.hudOverlay);
    }
  }


  @Test
  void defaultsAreDashboardFirst() {
    var c = new Config();
    assertFalse(c.hudOverlay);
    assertFalse(c.chatSummary);
    assertTrue(c.sound, "audio feedback retained");
    assertTrue(c.anonymizeExports);
  }
}
