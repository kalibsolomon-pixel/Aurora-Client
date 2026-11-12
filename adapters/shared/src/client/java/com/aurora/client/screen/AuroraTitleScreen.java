package com.aurora.client.screen;

import com.aurora.client.adapter.NativeUi;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.IconButton;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.util.AuroraBrand;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.MaterialIconRenderer;
import com.aurora.client.ui.util.MenuAtmosphere;
import com.aurora.client.ui.util.MenuComposition;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.UiDensity;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.ModInfo;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.List;
import java.util.function.Supplier;

/**
 * Aurora's main menu (redesigned after OneClient's, owner 2026-11-12): the
 * mark and wordmark over Singleplayer, Multiplayer and Realms, with Settings
 * (Minecraft's options) and Mods (Mod Menu) side by side; the player model and
 * account card on the right; Aurora, Fullscreen and Quit at the top right;
 * an animated gradient behind it all ({@link MenuAtmosphere}).
 *
 * <p>Geometry is {@link MenuComposition}'s. Nothing here talks to the network:
 * the skin comes through Minecraft's own skin lookup for the signed-in
 * profile. The account card is In-Game Account Switcher's entry point when
 * IAS is installed ({@link AuroraAccountSwitcherApi}); IAS does the signing in
 * and Aurora never sees an account. Without IAS the card only shows the
 * current account.
 */
public class AuroraTitleScreen extends Screen implements ThemedScreen {
    private static final Style WORDMARK = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("aurora", "wordmark")));
    private static final String WORDMARK_TEXT = "AURORA";
    private static final int WORDMARK_TRACKING = 3;
    private static final Component SWITCHER_NOTE = Component.translatable("aurora.title.account_switcher_missing");
    private static final Component COPYRIGHT = Component.translatable("title.credits");
    private static final int DROPDOWN_MIN_WIDTH = 170, DROPDOWN_ROW = 22, DROPDOWN_PAD = 6;

    private MenuComposition.Layout composition;
    private final MenuAtmosphere atmosphere = new MenuAtmosphere();
    private Supplier<PlayerSkin> skin;
    private AccountCard account;
    private boolean accountOpen;
    /** In-Game Account Switcher is installed: the card opens its account list instead of the dropdown. */
    private boolean switcher;
    // Text fitted once per layout and text style (overflow rule; no per-frame measuring).
    private FormattedCharSequence[] wordmarkGlyphs;
    private int[] wordmarkAdvances;
    private int wordmarkWidth;
    private List<FormattedCharSequence> noteLines = List.of();
    private String versionText = "", copyrightText = "";
    private int fittedKey = Integer.MIN_VALUE;

    public AuroraTitleScreen() {
        super(Component.translatable("aurora.title"));
    }

    @Override
    protected void init() {
        composition = MenuComposition.layout(this.width, this.height);
        accountOpen = false;
        fittedKey = Integer.MIN_VALUE;
        boolean online = this.minecraft.allowsMultiplayer();
        Tooltip offline = online ? null : Tooltip.create(Component.translatable("title.multiplayer.disabled"));

        addCard(0, Component.translatable("aurora.button.singleplayer"), "person",
                "Browse and play singleplayer worlds.",
                () -> NativeUi.setScreen(this.minecraft, new SelectWorldScreen(this)));
        ButtonWidget multiplayer = addCard(1, Component.translatable("aurora.button.multiplayer"), "group",
                "Browse multiplayer servers.",
                () -> NativeUi.setScreen(this.minecraft, new JoinMultiplayerScreen(this)));
        ButtonWidget realms = addCard(2, Component.translatable("menu.online"), "public",
                "Open Minecraft Realms.",
                () -> NativeUi.setScreen(this.minecraft, new RealmsMainScreen(this)));
        for (ButtonWidget gated : new ButtonWidget[]{multiplayer, realms}) {
            gated.active = online;
            gated.setTooltip(offline);
        }
        addCard(3, Component.translatable("aurora.button.settings"), "settings",
                "Open Minecraft's options.",
                () -> NativeUi.setScreen(this.minecraft, NativeUi.optionsScreen(this.minecraft, this)));
        ButtonWidget mods = addCard(4, Component.translatable("aurora.button.mods"), "extension",
                "Open Mod Menu's list of installed mods.",
                () -> {
                    Screen list = AuroraModMenuApi.open(this);
                    if (list != null) NativeUi.setScreen(this.minecraft, list);
                });
        if (!AuroraModMenuApi.available()) {
            mods.active = false;
            mods.setTooltip(Tooltip.create(Component.translatable("aurora.title.modmenu_missing")));
        }

        var aurora = composition.icon(0);
        addRenderableWidget(IconButton.brand(aurora.x(), aurora.y(), aurora.width(), Component.literal("Aurora"),
                () -> NativeUi.setScreen(this.minecraft, AuroraScreen.create())));
        var fullscreen = composition.icon(1);
        addRenderableWidget(IconButton.glyph(fullscreen.x(), fullscreen.y(), fullscreen.width(),
                Component.translatable("options.fullscreen"),
                () -> com.aurora.client.screen.IconCatalog.glyph(
                        this.minecraft.options.fullscreen().get() ? "fullscreen_exit" : "fullscreen"),
                this::toggleFullscreen));
        var quit = composition.icon(2);
        addRenderableWidget(IconButton.glyph(quit.x(), quit.y(), quit.width(), Component.translatable("menu.quit"),
                () -> com.aurora.client.screen.IconCatalog.glyph("power_settings_new"), () -> this.minecraft.stop()));

        skin = this.minecraft.getSkinManager().createLookup(this.minecraft.getGameProfile(), false);
        var model = composition.model();
        if (model.width() > 0) {
            PlayerSkinWidget figure = new PlayerSkinWidget(model.width(), model.height(), this.minecraft.getEntityModels(), skin);
            figure.setPosition(model.x(), model.y());
            addRenderableWidget(figure);
        }
        var card = composition.account();
        switcher = AuroraAccountSwitcherApi.available();
        account = card.width() > 0 ? addRenderableWidget(new AccountCard(card, composition.compact())) : null;
        if (account != null && switcher) account.setTooltip(Tooltip.create(Component.translatable("aurora.title.switch_account")));

        Font font = this.font;
        int lineY = this.height - MenuComposition.MARGIN - font.lineHeight + 1;
        int half = Math.max(0, (this.width - 3 * MenuComposition.MARGIN) / 2);
        int copyrightWidth = Math.min(half, font.width(COPYRIGHT));
        addRenderableWidget(new TextLink(this.width - MenuComposition.MARGIN - copyrightWidth, lineY - 1,
                copyrightWidth, font.lineHeight + 2, COPYRIGHT,
                () -> NativeUi.setScreen(this.minecraft, new CreditsAndAttributionScreen(this))));
    }

    private ButtonWidget addCard(int index, Component label, String icon, String description, Runnable onPress) {
        // Vanilla-backed semantic button: vanilla keeps pointer, keyboard, focus and the click sound.
        var bounds = composition.card(index);
        ButtonWidget btn = ButtonWidget.semantic(bounds.x(), bounds.y(), bounds.width(), bounds.height(), label, description, onPress);
        // The reference's outlined cards: no glass on a gradient (no world, no panorama: glass rule 5).
        btn.theming(Button.Theming.OUTLINED).icon(com.aurora.client.screen.IconCatalog.glyph(icon));
        this.addRenderableWidget(btn);
        return btn;
    }

    private void toggleFullscreen() {
        var option = this.minecraft.options.fullscreen();
        option.set(!option.get()); // the video settings' path: the option's callback resizes the window
        this.minecraft.options.save();
    }

    /** Re-fits the text that depends on the layout and the text options. */
    private void fitText() {
        int key = AuroraFontRenderer.styleKey() * 31 + this.width;
        if (key == fittedKey) return;
        fittedKey = key;
        wordmarkGlyphs = new FormattedCharSequence[WORDMARK_TEXT.length()];
        wordmarkAdvances = new int[WORDMARK_TEXT.length()];
        wordmarkWidth = 0;
        for (int i = 0; i < WORDMARK_TEXT.length(); i++) {
            // An explicit font: the text options never re-case or re-face the wordmark.
            wordmarkGlyphs[i] = Component.literal(WORDMARK_TEXT.substring(i, i + 1)).withStyle(WORDMARK).getVisualOrderText();
            wordmarkAdvances[i] = this.font.width(wordmarkGlyphs[i]);
            wordmarkWidth += wordmarkAdvances[i] + (i > 0 ? WORDMARK_TRACKING : 0);
        }
        int half = Math.max(0, (this.width - 3 * MenuComposition.MARGIN) / 2);
        String version = "Aurora " + ModInfo.version() + "  ·  Minecraft " + SharedConstants.getCurrentVersion().name();
        versionText = AuroraFontRenderer.ellipsize(this.font, version, half, 0);
        copyrightText = AuroraFontRenderer.ellipsize(this.font, COPYRIGHT.getString(), half, 0);
        noteLines = this.font.split(SWITCHER_NOTE, dropdownWidth() - 2 * DROPDOWN_PAD - 4);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // No panorama and no blur: the atmosphere is the whole backdrop.
        atmosphere.draw(ctx, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        fitText();
        var mark = composition.logo();
        if (mark.width() > 0) AuroraBrand.draw(ctx, mark.x(), mark.y(), mark.width());
        var word = composition.wordmark();
        if (word.height() > 0 && wordmarkWidth <= word.width()) {
            int x = word.x() + (word.width() - wordmarkWidth) / 2;
            int color = ThemeManager.color(ThemeToken.MATERIAL_TEXT);
            for (int i = 0; i < wordmarkGlyphs.length; i++) {
                ctx.text(this.font, wordmarkGlyphs[i], x, word.y(), color, false);
                x += wordmarkAdvances[i] + WORDMARK_TRACKING;
            }
        }
        ctx.text(this.font, versionText, MenuComposition.MARGIN, this.height - MenuComposition.MARGIN - this.font.lineHeight + 1,
                ThemeManager.color(ThemeToken.MATERIAL_TEXT_MUTED), false);

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        if (accountOpen && account != null) {
            ctx.nextStratum(); // above the cards and their labels
            drawDropdown(ctx);
        }
    }

    private int dropdownWidth() {
        var card = composition.account();
        return Math.min(Math.max(card.width(), DROPDOWN_MIN_WIDTH), this.width - 2 * MenuComposition.MARGIN);
    }

    /** The open dropdown's rect: below the card when it fits, otherwise above it. */
    private UiDensity.Rect dropdown() {
        var card = composition.account();
        int w = dropdownWidth();
        int h = 2 * DROPDOWN_PAD + DROPDOWN_ROW + 5 + noteLines.size() * (this.font.lineHeight + 2);
        int x = Math.max(MenuComposition.MARGIN, card.x() + card.width() - w);
        int below = card.y() + card.height() + 4;
        int y = below + h <= this.height - MenuComposition.MARGIN ? below : card.y() - 4 - h;
        return new UiDensity.Rect(x, Math.max(MenuComposition.MARGIN, y), w, h);
    }

    private void drawDropdown(GuiGraphicsExtractor ctx) {
        var r = dropdown();
        int radius = Math.round(ThemeManager.current().roundness().radiusSmall());
        MaterialSurface.floatingPanel(ctx, r.x(), r.y(), r.width(), r.height(), radius);
        int rowY = r.y() + DROPDOWN_PAD;
        int face = 12;
        PlayerFaceExtractor.extractRenderState(ctx, skin.get(), r.x() + DROPDOWN_PAD + 2, rowY + (DROPDOWN_ROW - face) / 2, face);
        int nameX = r.x() + DROPDOWN_PAD + 2 + face + 6;
        int checkX = r.x() + r.width() - DROPDOWN_PAD - 8;
        String name = AuroraFontRenderer.ellipsize(this.font, this.minecraft.getUser().getName(), checkX - 8 - nameX, 0);
        ctx.text(this.font, name, nameX, rowY + (DROPDOWN_ROW - this.font.lineHeight) / 2 + 1,
                ThemeManager.color(ThemeToken.MATERIAL_TEXT), false);
        MaterialIconRenderer.drawIcon(ctx, this.font, com.aurora.client.screen.IconCatalog.glyph("check"),
                checkX, rowY + DROPDOWN_ROW / 2f, 11f, ThemeManager.color(ThemeToken.ACCENT));
        int dividerY = rowY + DROPDOWN_ROW + 2;
        ctx.fill(r.x() + DROPDOWN_PAD, dividerY, r.x() + r.width() - DROPDOWN_PAD, dividerY + 1,
                MaterialSurface.color(ThemeToken.BORDER));
        int lineY = dividerY + 4;
        int muted = ThemeManager.color(ThemeToken.MATERIAL_TEXT_MUTED);
        for (FormattedCharSequence line : noteLines) {
            ctx.text(this.font, line, r.x() + DROPDOWN_PAD + 2, lineY, muted, false);
            lineY += this.font.lineHeight + 2;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (accountOpen) {
            // The open dropdown takes the click: inside it does nothing, outside it closes.
            if (!dropdown().contains(event.x(), event.y())) accountOpen = false;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (accountOpen && event.key() == InputConstants.KEY_ESCAPE) {
            accountOpen = false;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        super.removed();
        atmosphere.dispose();
        MaterialSurface.disposeTemplates();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /**
     * Report as a non-pause screen so MC's inactivity-FPS limiter (which
     * kicks in on pause screens after a few seconds of no input) does not
     * throttle the animated background down below 30 fps.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The signed-in account: face, name and a chevron. Opens IAS's account list, or the read-only dropdown without IAS. */
    private final class AccountCard extends AbstractButton {
        private final boolean chip;

        AccountCard(UiDensity.Rect r, boolean chip) {
            super(r.x(), r.y(), r.width(), r.height(), Component.literal(minecraft.getUser().getName()));
            this.chip = chip;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            Screen accounts = switcher ? AuroraAccountSwitcherApi.open(AuroraTitleScreen.this) : null;
            if (accounts != null) NativeUi.setScreen(minecraft, accounts); // back here re-inits: new name and skin
            else accountOpen = !accountOpen;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            float x = getX(), y = getY(), w = getWidth(), h = getHeight();
            float r = ThemeManager.current().roundness().radiusSmall();
            boolean hot = isHovered() || accountOpen;
            int windowAlpha = ThemeManager.color(ThemeToken.WINDOW_FILL) >>> 24;
            int fill = ThemeManager.color(hot ? ThemeToken.MATERIAL_HOVER : ThemeToken.MATERIAL_RAISED);
            RenderUtil.drawRoundedRectAA(g, x, y, w, h, r, ThemeManager.withAlpha(fill, windowAlpha));
            RenderUtil.drawRoundedOutlineAA(g, x, y, w, h, r, 1, MaterialSurface.color(ThemeToken.BORDER_HOVER));
            if (isFocused() && minecraft.getLastInputType().isKeyboard()) RenderUtil.drawRoundedOutlineAA(g, x - 2, y - 2, w + 4, h + 4,
                    MaterialSurface.outsetRadius(r, 2), 1, MaterialSurface.color(ThemeToken.ACCENT));
            int face = chip ? 12 : 16;
            int pad = (getHeight() - face) / 2;
            PlayerFaceExtractor.extractRenderState(g, skin.get(), getX() + pad, getY() + pad, face);
            int chevronX = getX() + getWidth() - pad - 6;
            int nameX = getX() + pad + face + 6;
            int text = ThemeManager.color(ThemeToken.MATERIAL_TEXT);
            String name = AuroraFontRenderer.ellipsize(font, getMessage().getString(), chevronX - 8 - nameX, 0);
            g.text(font, name, nameX, getY() + (getHeight() - font.lineHeight) / 2 + 1, text, false);
            MaterialIconRenderer.drawIcon(g, font, com.aurora.client.screen.IconCatalog.glyph(switcher ? "switch_account" : accountOpen ? "expand_less" : "expand_more"),
                    chevronX, getY() + getHeight() / 2f, 12f,
                    AuroraAnim.lerpArgb(ThemeManager.color(ThemeToken.MATERIAL_TEXT_SECONDARY), text, hot ? 1 : 0));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            this.defaultButtonNarrationText(builder);
        }
    }

    /** Small clickable footer text (the Mojang copyright line, which opens the credits as vanilla's does). */
    private final class TextLink extends AbstractButton {
        private final Runnable onPress;

        TextLink(int x, int y, int w, int h, Component label, Runnable onPress) {
            super(x, y, w, h, label);
            this.onPress = onPress;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            onPress.run();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            int color = ThemeManager.color(isHoveredOrFocused() ? ThemeToken.MATERIAL_TEXT : ThemeToken.MATERIAL_TEXT_MUTED);
            g.text(font, copyrightText, getX() + getWidth() - font.width(copyrightText), getY() + 1, color, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            this.defaultButtonNarrationText(builder);
        }
    }
}
