package dev.phenomena.gui.screens;

import com.mojang.blaze3d.platform.InputConstants;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.util.OverlayLayout;
import com.aurora.client.util.SmoothScroll;
import dev.phenomena.PhenomenaClient;
import dev.phenomena.gui.Notices;
import dev.phenomena.gui.components.*;
import dev.phenomena.gui.pixel.Pixel;
import dev.phenomena.gui.theme.Theme;
import dev.phenomena.gui.ui.Layout;
import dev.phenomena.gui.ui.Layout.Rect;
import dev.phenomena.gui.ui.UiState;
import dev.phenomena.gui.ui.UiState.Tab;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;


/**
 * The Phenomena dashboard: header, persistent primary tabs, a contextual heading with optional
 * section chips, and one clipped scroll area. Navigation state lives in {@link UiState} so tabs,
 * sections, filters and scroll positions survive switching and reopening. Combat tracking never
 * depends on this screen being open.
 */
public final class PhenomenaScreen extends Screen implements com.aurora.client.ui.component.ThemedScreen {
  private final Screen parent;
  @Override public boolean parentGlassOnly() { return true; }
  @Override public boolean compactText() { return true; }
  /** One primary tab. */
  public interface Page {
    String title();

    /** Right-aligned context text beside the title, or null. */
    default String context() {
      return null;
    }

    /** Section chips under the title, or an empty list. */
    default List<String> sections() {
      return List.of();
    }

    default int section() {
      return 0;
    }

    default void section(int index) {}

    /** Fixed controls aligned right on the title row (dropdowns, actions). */
    default List<AbstractWidget> toolbar(PhenomenaScreen s) {
      return List.of();
    }

    void build(Content c);
  }

  public final UiState state = UiState.SESSION;
  private final Map<Tab, Page> pages = new EnumMap<>(Tab.class);
  private final List<Content.Item> items = new ArrayList<>();
  private final List<KButton> tabButtons = new ArrayList<>();
  private Rect frame, viewport, panel;
  private int contentHeight, scroll, headerH, tabsY, titleY;
  private final SmoothScroll focusScroll = new SmoothScroll(60).boundedMotion();
  private Popup popup;
  private Popup.Dropdown closingDropdown;
  private boolean draggingBar;
  /** Pointer offset from the thumb's top edge while dragging, so the thumb never jumps. */
  private double barGrab;
  private long builtVersion = -1;
  private float indicatorX = -1;
  private Consumer<KeyEvent> keyCapture;

  public PhenomenaScreen() { this(null); }
  public PhenomenaScreen(Screen parent) {
    super(Component.literal("Pulsar"));
    this.parent = parent;
    PhenomenaClient.ensureReady();
    pages.put(Tab.OVERVIEW, new OverviewPage(this));
    pages.put(Tab.OFFENSE, new OffensePage(this));
    pages.put(Tab.DEFENSE, new DefensePage(this));
    pages.put(Tab.FLOW, new FlowPage(this));
    pages.put(Tab.HISTORY, new HistoryPage(this));
    pages.put(Tab.SETTINGS, new SettingsPage(this));
  }

  public Page page() {
    return pages.get(state.tab);
  }

  // ---- layout --------------------------------------------------------------

  @Override
  protected void init() {
    popup = null;
    closingDropdown = null;
    for (var item : items) if (item.block() != null) item.block().close();
    items.clear();
    tabButtons.clear();
    boolean compact = tight();
    var bounds = com.aurora.client.ui.util.UiDensity.window(width, height);
    frame = new Rect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    headerH = com.aurora.client.ui.util.UiDensity.HEADER;
    tabsY = frame.y() + headerH;
    int tabH = compact ? 16 : 18;
    var tabRects = Layout.segments(frame.x() + 6, tabsY, frame.w() - 12, tabH, Tab.values().length, 2);
    for (var t : Tab.values()) {
      var r = tabRects.get(t.ordinal());
      var b =
          new KButton(t.label, KButton.Style.TAB, () -> t.label, () -> selectTab(t))
              .selected(() -> state.tab == t);
      b.setX(r.x());
      b.setY(r.y());
      b.size(r.w(), r.h());
      tabButtons.add(addRenderableWidget(b));
    }
    titleY = tabsY + tabH + (compact ? 6 : 9);
    var page = page();

    // Toolbar widgets right-aligned on the title row.
    int tx = frame.right() - 6;
    int toolH = compact ? 13 : 14;
    var tools = page.toolbar(this);
    for (int i = tools.size() - 1; i >= 0; i--) {
      var w = tools.get(i);
      tx -= w.getWidth();
      w.setX(tx);
      w.setY(titleY - 3);
      w.setHeight(toolH);
      addRenderableWidget(w);
      tx -= 4;
    }

    int y = titleY + (compact ? 12 : 14);
    var sections = page.sections();
    if (!sections.isEmpty()) {
      var rects =
          Layout.wrap(
              sections, s -> font.width(s), frame.x() + 6, y, frame.w() - 12, compact ? 13 : 14, 6, 3);
      for (int i = 0; i < sections.size(); i++) {
        int idx = i;
        var r = rects.get(i);
        var chip =
            new KButton(
                    "section:" + sections.get(i),
                    KButton.Style.CHIP,
                    () -> sections.get(idx),
                    () -> {
                      page.section(idx);
                      rebuild();
                    })
                .selected(() -> page.section() == idx);
        chip.setX(r.x());
        chip.setY(r.y());
        chip.size(r.w(), r.h());
        addRenderableWidget(chip);
      }
      y += Layout.rowsHeight(rects, y) + (compact ? 5 : 7);
    }
    int footerH = compact ? 20 : 24;
    panel = frame;
    viewport = new Rect(frame.x() + 6, y, frame.w() - 16, Math.max(20, frame.bottom() - footerH - y - 4));
    Viewport.set(viewport);

    var content = new Content(this, viewport.w(), compact);
    page.build(content);
    contentHeight = content.height();
    for (var item : content.items) {
      items.add(item);
      if (item.widget() != null) addWidget(item.widget());
    }
    scroll = state.clampScroll(contentHeight, viewport.h());
    focusScroll.set(scroll);
    placeWidgets();

    var close =
        new KButton("Done", KButton.Style.PRIMARY, this::onClose).tooltip("Escape or the menu key");
    close.size(48, 16);
    close.setX(frame.right() - 54);
    close.setY(frame.y() + 4);
    addRenderableWidget(close);
    builtVersion = PhenomenaClient.historyVersion;
  }

  private void placeWidgets() {
    for (var item : items) {
      if (item.widget() == null) continue;
      item.widget().setX(viewport.x() + item.x() + item.insetX());
      item.widget().setY(viewport.y() + item.y() + item.insetY() - scroll);
    }
  }

  /** Rebuild the current page, keeping scroll and restoring keyboard focus by widget id. */
  public void rebuild() {
    String focusId = idOf(getFocused());
    rebuildWidgets();
    if (focusId != null)
      for (var child : children())
        if (focusId.equals(idOf(child))) {
          setFocused(child);
          break;
        }
  }

  private static String idOf(GuiEventListener l) {
    if (l instanceof KButton b) return "b:" + b.id;
    if (l instanceof KSlider s) return "s:" + s.id;
    if (l instanceof KTextField t) return "t:" + t.id;
    if (l instanceof ColorSwatches c) return "c:" + c.id;
    if (l instanceof FightList) return "list";
    if (l instanceof ReadableBlock b) return "read:" + b.getMessage().getString();
    return null;
  }

  public void selectTab(Tab t) {
    if (state.tab == t) return;
    state.select(t);
    rebuild();
  }

  public void setScroll(int value) {
    applyScroll(value);
    focusScroll.set(scroll);
  }

  private void applyScroll(int value) {
    scroll = Math.max(0, Math.min(Math.max(0, contentHeight - viewport.h()), value));
    state.setScroll(scroll);
    placeWidgets();
  }

  /** Scrolls so an absolute screen-space band becomes visible. */
  public void reveal(int top, int bottom) {
    int max = Math.max(0, contentHeight - viewport.h());
    double target = OverlayLayout.scrollToReveal(focusScroll.target(), max, top + scroll, bottom + scroll,
        viewport.y(), viewport.bottom(), 8);
    if (Theme.reducedMotion() || AuroraConfig.get().reducedMotion) setScroll((int) target);
    else if (target != focusScroll.target()) focusScroll.retarget(target, max);
  }

  @Override
  public void setFocused(GuiEventListener listener) {
    super.setFocused(listener);
    if (listener instanceof AbstractWidget w && viewport != null)
      for (var item : items)
        if (item.widget() == w) {
          if (w instanceof ReadableBlock readable) { readable.revealFocus(); break; }
          int top = viewport.y() + item.y() - scroll;
          reveal(top, top + item.h());
          break;
        }
  }

  // ---- popups & helpers for pages -----------------------------------------

  public KButton dropdown(
      String id, Supplier<String> label, List<String> options, IntSupplier current, IntConsumer pick) {
    KButton[] self = new KButton[1];
    self[0] =
        new KButton(
            id,
            KButton.Style.NORMAL,
            () -> label.get() + " v",
            () -> {
              var b = self[0];
              popup =
                  new Popup.Dropdown(
                      new Rect(b.getX(), b.getY(), b.getWidth(), b.getHeight()),
                      options,
                      current.getAsInt(),
                      i -> {
                        pick.accept(i);
                        rebuild();
                      },
                      frame);
            });
    self[0].setWidth(font.width(label.get() + " v") + 28);
    return self[0];
  }

  public void confirm(String title, String message, String action, Runnable run) {
    popup = new Popup.Confirm(title, message, action, run);
  }

  public boolean popupOpen() {
    return popup != null;
  }

  private void dismissPopup(Popup dismissed) {
    if (popup == dismissed) popup = null;
    if (dismissed instanceof Popup.Dropdown dropdown) {
      dropdown.dismiss();
      closingDropdown = dropdown;
    }
  }

  /** Routes the next key press to the capture (key rebinding). */
  public void captureKey(Consumer<KeyEvent> capture) {
    keyCapture = capture;
  }

  public boolean capturing() {
    return keyCapture != null;
  }

  /** True for widgets inside the scroll area (used by layout checks). */
  public boolean isContentWidget(GuiEventListener l) {
    for (var item : items) if (item.widget() == l) return true;
    return false;
  }

  public int contentWidth() {
    return viewport == null ? 200 : viewport.w();
  }

  // ---- input ---------------------------------------------------------------

  @Override
  public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
    if (popup != null) {
      Popup active = popup;
      if (!active.click(e.x(), e.y())) dismissPopup(active);
      return true;
    }
    var bar = scrollTrack();
    if (bar != null && e.button() == 0 && e.x() >= bar.x() - 2 && e.x() < bar.right() + 2 && bar.contains(bar.x(), e.y())) {
      draggingBar = true;
      var thumb = Layout.thumb(bar, contentHeight, scroll);
      // On the thumb, keep the grab point; on the track, centre the thumb on the pointer.
      barGrab = thumb != null && e.y() >= thumb.y() && e.y() < thumb.y() + thumb.h()
          ? e.y() - thumb.y()
          : thumb == null ? 0 : thumb.h() / 2.0;
      dragBar(e.y());
      return true;
    }
    return super.mouseClicked(e, doubleClick);
  }

  @Override
  public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
    if (popup != null) return true;
    if (draggingBar) {
      dragBar(e.y());
      return true;
    }
    return super.mouseDragged(e, dx, dy);
  }

  @Override
  public boolean mouseReleased(MouseButtonEvent e) {
    if (popup != null) return true;
    draggingBar = false;
    return super.mouseReleased(e);
  }

  private void dragBar(double y) {
    var track = scrollTrack();
    if (track == null) return;
    setScroll(Layout.scrollForThumb(track, contentHeight, (int) Math.round(y - barGrab)));
  }

  private Rect scrollTrack() {
    if (viewport == null || contentHeight <= viewport.h()) return null;
    return new Rect(viewport.right() + 4, viewport.y(), 3, viewport.h());
  }

  @Override
  public boolean mouseScrolled(double x, double y, double h, double v) {
    if (popup != null) {
      popup.scroll(v);
      return true;
    }
    if (viewport != null && contentHeight > viewport.h()) {
      setScroll(scroll - (int) Math.round(v * Layout.wheelStep(viewport.h())));
      return true;
    }
    return super.mouseScrolled(x, y, h, v);
  }

  @Override
  public boolean keyPressed(KeyEvent e) {
    if (popup != null) {
      Popup active = popup;
      if (!active.key(e.key())) dismissPopup(active);
      return true;
    }
    if (keyCapture != null) {
      var capture = keyCapture;
      keyCapture = null;
      capture.accept(e);
      return true;
    }
    boolean typing = getFocused() instanceof EditBox;
    if (!typing) {
      switch (e.key()) {
        case InputConstants.KEY_PAGEDOWN -> {
          setScroll(scroll + viewport.h() - 20);
          return true;
        }
        case InputConstants.KEY_PAGEUP -> {
          setScroll(scroll - viewport.h() + 20);
          return true;
        }
        case InputConstants.KEY_HOME -> {
          setScroll(0);
          return true;
        }
        case InputConstants.KEY_END -> {
          setScroll(contentHeight);
          return true;
        }
        case InputConstants.KEY_1,
            InputConstants.KEY_2,
            InputConstants.KEY_3,
            InputConstants.KEY_4,
            InputConstants.KEY_5,
            InputConstants.KEY_6 -> {
          selectTab(Tab.values()[e.key() - InputConstants.KEY_1]);
          return true;
        }
        default -> {}
      }
      int binding = com.aurora.client.config.AuroraConfig.get().pulsarKey;
      if (binding >= 0 && com.aurora.client.adapter.NativeKeyboard.toStored(e.key()) == com.aurora.client.util.KeyCombo.key(binding)
          && (com.aurora.client.util.AuroraKey.heldModifiers() & com.aurora.client.util.KeyCombo.modifiers(binding)) == com.aurora.client.util.KeyCombo.modifiers(binding)) {
        onClose();
        return true;
      }
    }
    if (super.keyPressed(e)) return true;
    // Arrow keys on a focused tab switch tabs, matching common tab-strip behavior.
    if (getFocused() instanceof KButton b && tabButtons.contains(b)
        && (e.key() == InputConstants.KEY_LEFT || e.key() == InputConstants.KEY_RIGHT)) {
      int count = Tab.values().length;
      int i = (state.tab.ordinal() + (e.key() == InputConstants.KEY_LEFT ? count - 1 : 1)) % count;
      selectTab(Tab.values()[i]);
      setFocused(tabButtons.get(i));
      return true;
    }
    return false;
  }

  @Override public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
    return popup != null || super.charTyped(event);
  }

  @Override protected void updateNarrationState(net.minecraft.client.gui.narration.NarrationElementOutput output) {
    if (popup != null) output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, popup.narration());
    else super.updateNarrationState(output);
  }

  @Override public Component getNarrationMessage() {
    return Component.literal("Pulsar. " + page().title() + ". "
        + (page().context() == null ? "" : page().context() + ". ") + liveStatus());
  }

  // ---- lifecycle -------------------------------------------------------------

  @Override
  public void tick() {
    if (builtVersion != PhenomenaClient.historyVersion
        && popup == null
        && !(getFocused() instanceof EditBox)
        && !draggingBar) rebuild();
  }

  @Override
  public void onClose() {
    PhenomenaClient.saveConfig();
    Viewport.set(null);
    com.aurora.client.adapter.NativeUi.setScreen(minecraft, parent);
  }

  @Override
  public void removed() {
    PhenomenaClient.saveConfig();
    for (var item : items) if (item.block() != null) item.block().close();
    items.clear();
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  // ---- rendering -------------------------------------------------------------

  @Override
  public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
    if (!com.aurora.client.ui.component.GlassSurface.liveWorldBackdrop())
      com.aurora.client.ui.component.GlassSurface.renderMenuPanorama(this, g, delta);
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
    if (focusScroll.current() != focusScroll.target()) {
      if (Theme.reducedMotion() || AuroraConfig.get().reducedMotion) setScroll((int) focusScroll.target());
      else {
        int next = (int) Math.round(focusScroll.advance(Math.max(0, contentHeight - viewport.h())));
        if (next != scroll) applyScroll(next);
      }
    }
    Viewport.set(viewport);
    com.aurora.client.ui.component.GlassSurface.beginGlassPass();
    boolean captured = com.aurora.client.ui.component.GlassSurface.container(g, frame.x(), frame.y(), frame.w(), frame.h(), Theme.radius());
    com.aurora.client.ui.component.GlassSurface.overlayDim(g, width, height);
    window.renderShapes(g, frame.x(), frame.y(), frame.w(), frame.h(), captured);
    g.text(font, com.aurora.client.ui.util.AuroraFontRenderer.role("Pulsar", com.aurora.client.ui.util.AuroraFontRenderer.Role.TITLE),
        frame.x() + 12, frame.y() + 7, Theme.TEXT(), false);
    int toolsLeft = frame.right();
    for (var child : children())
      if (child instanceof AbstractWidget widget && widget.getY() == titleY - 3)
        toolsLeft = Math.min(toolsLeft, widget.getX());
    Component title = com.aurora.client.ui.util.AuroraFontRenderer.role(page().title(), com.aurora.client.ui.util.AuroraFontRenderer.Role.HEADING);
    g.text(font, title, frame.x() + 6, titleY, Theme.TEXT(), false);
    String context = page().context();
    int contextX = frame.x() + 12 + font.width(title);
    if (context != null && toolsLeft - contextX > 40)
      g.text(font, KButton.fit(font, context, toolsLeft - contextX - 6), contextX, titleY + 1, Theme.DIM(), false);
    super.extractRenderState(g, mx, my, delta);

    g.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
    int top = viewport.y() - scroll;
    for (var item : items) {
      int iy = top + item.y();
      if (iy + item.h() < viewport.y() || iy > viewport.bottom()) continue;
      if (item.block() != null) item.block().render(g, viewport.x() + item.x(), iy, item.w(), item.h(), mx, my);
      else item.widget().extractRenderState(g, mx, my, delta);
    }
    g.disableScissor();
    // Aurora's scroll fades (DESIGN_LANGUAGE section 8): content leaving either edge softens
    // into the window tint instead of being sliced mid-line.
    int surface = com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.WINDOW_FILL);
    com.aurora.client.util.ScrollFade.drawTop(g, viewport.x(), viewport.w(), viewport.y(),
        com.aurora.client.util.ScrollFade.FADE_PX, scroll, surface);
    com.aurora.client.util.ScrollFade.drawBottom(g, viewport.x(), viewport.w(), viewport.bottom(),
        com.aurora.client.util.ScrollFade.BOTTOM_FADE_PX, Math.max(0, contentHeight - viewport.h()) - scroll, surface);
    var track = scrollTrack();
    if (track != null) {
      Pixel.rounded(g, track.x(), track.y(), 3, track.h(), 1.5f, Theme.INSET());
      var thumb = Layout.thumb(track, contentHeight, scroll);
      if (thumb != null) Pixel.rounded(g, track.x(), thumb.y(), 3, thumb.h(), 1.5f, Theme.MUTED());
    }
    // Hairline at the top of the footer band: the status line is not clipped content.
    int rule = viewport.y() + viewport.h() + 2;
    Pixel.fillLogical(g, frame.x() + 6, rule, frame.x() + frame.w() - 6, rule + 1, Theme.BORDER());
    if (keyCapture == null && getFocused() instanceof ReadableBlock readable && KButton.keyboardFocus(readable)) {
      // The keyboard readout has its own band: the footer, never over content.
      var lines = TextLayout.lines(font, readable.description(), frame.w() - 12, 2);
      int ly = lines.size() == 1 ? frame.bottom() - 13 : Math.min(rule + 3, frame.bottom() - 21);
      for (var line : lines) {
        g.text(font, line, frame.x() + 6, ly, Theme.TEXT(), false);
        ly += 10;
      }
    } else {
      String footer = keyCapture != null ? "Press a key. Escape cancels."
          : Notices.recent() ? Notices.text : liveStatus();
      g.text(font, KButton.fit(font, footer, frame.w() - 12), frame.x() + 6, frame.bottom() - 13,
          Notices.recent() && Notices.level == Notices.Level.ERROR ? Theme.ERROR() : Theme.MUTED(), false);
    }
    if (closingDropdown != null && closingDropdown.finished()) closingDropdown = null;
    if (popup == null && closingDropdown != null) {
      g.nextStratum();
      closingDropdown.render(g, mx, my, width, height);
    }
    if (popup != null) {
      g.nextStratum();
      popup.render(g, mx, my, width, height);
      com.aurora.client.ui.component.TooltipPresenter.suppressFrame();
    }
    com.aurora.client.ui.component.TooltipPresenter.draw(g, frame.x(), frame.y(), frame.w(), frame.h(),
        Theme.reducedMotion() || com.aurora.client.config.AuroraConfig.get().reducedMotion);
  }
  private final com.aurora.client.ui.component.RoundedPanel window = new com.aurora.client.ui.component.RoundedPanel();

  private String liveStatus() {
    var e = PhenomenaClient.engine;
    if (!com.aurora.client.config.AuroraConfig.get().pulsarEnabled) return "Capture off. Enable Pulsar in Features to record.";
    if (PhenomenaClient.recording.manual()) return "Manual recording in progress";
    if (!PhenomenaClient.config.tracking) return "Automatic capture off";
    String rec = PhenomenaClient.config.recording ? "" : " / not recording";
    return switch (e.state()) {
      case ENGAGED -> "In fight / " + AnalysisSupport.hits(e.stats().hits) + rec;
      case ENDING -> "Fight ending" + rec;
      default -> "Capture ready" + rec;
    };
  }

  /** Compact spacing when chosen, or automatically when the scaled window is short. */
  public boolean tight() {
    return Theme.compact() || height < 300;
  }

  public static String keyName() {
    return com.aurora.client.screen.setting.KeybindSetting.keyName(com.aurora.client.config.AuroraConfig.get().pulsarKey);
  }
}
