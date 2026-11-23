package com.aurora.client.mixin;

import com.aurora.client.adapter.NativeUi;
import com.aurora.client.chat.ChatFormatting;
import com.aurora.client.chat.ChatSuite;
import com.aurora.client.chat.ChatSuiteAccess;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.component.Toast;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds an inline chat search bar and Right-Click / Ctrl+Click message copying
 * to vanilla's {@link ChatScreen} while {@link AuroraConfig#chatEnabled} is on.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenSuiteMixin extends Screen {

    @Shadow protected EditBox input;

    @Unique private EditBox aurora$searchBox;
    @Unique private final Toast aurora$copyToast = new Toast();

    protected ChatScreenSuiteMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void aurora$initChatSearch(CallbackInfo ci) {
        aurora$searchBox = null;
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.chatEnabled || !cfg.chatSearchEnabled || minecraft == null) return;

        int boxW = Math.min(168, Math.max(100, this.width / 3));
        int boxX = this.width - boxW - 4;
        int boxY = this.height - 32;

        aurora$searchBox = new EditBox(this.font, boxX, boxY, boxW, 14, Component.literal("Search chat"));
        aurora$searchBox.setMaxLength(100);
        aurora$searchBox.setHint(Component.literal("Search chat (Ctrl+F)..."));
        aurora$searchBox.setBordered(true);
        aurora$searchBox.setFocused(false);
        aurora$searchBox.setResponder(query -> {
            ChatComponent chat = NativeUi.chat(minecraft);
            if (chat instanceof ChatSuiteAccess access) {
                access.aurora$setSearchQuery(query);
            }
        });
        this.addRenderableWidget(aurora$searchBox);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void aurora$clearChatFilterOnClose(CallbackInfo ci) {
        if (minecraft == null || aurora$searchBox == null) return;
        ChatComponent chat = NativeUi.chat(minecraft);
        if (chat instanceof ChatSuiteAccess access) {
            access.aurora$setSearchQuery("");
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void aurora$handleSearchKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (aurora$searchBox == null) return;

        // Ctrl+F focuses the chat search field directly.
        if (event.hasControlDownWithQuirk() && !event.hasAltDown() && event.key() == InputConstants.KEY_F) {
            aurora$focusSearch();
            cir.setReturnValue(true);
            return;
        }

        if (aurora$searchBox.isFocused()) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                aurora$searchBox.setValue("");
                aurora$focusChatInput();
                cir.setReturnValue(true);
                return;
            }
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                aurora$focusChatInput();
                cir.setReturnValue(true);
                return;
            }
            if (aurora$searchBox.keyPressed(event)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void aurora$handleSearchAndCopyClicks(MouseButtonEvent event, boolean doubleClick,
                                                  CallbackInfoReturnable<Boolean> cir) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.chatEnabled || minecraft == null) return;

        if (aurora$searchBox != null) {
            boolean inSearch = event.x() >= aurora$searchBox.getX()
                    && event.x() < aurora$searchBox.getX() + aurora$searchBox.getWidth()
                    && event.y() >= aurora$searchBox.getY()
                    && event.y() < aurora$searchBox.getY() + aurora$searchBox.getHeight();
            if (inSearch) {
                aurora$focusSearch();
                aurora$searchBox.mouseClicked(event, doubleClick);
                cir.setReturnValue(true);
                return;
            } else if (aurora$searchBox.isFocused()) {
                aurora$focusChatInput();
            }
        }

        if (cfg.chatCopyEnabled) {
            boolean rightClick = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
            boolean ctrlLeftClick = event.button() == InputConstants.MOUSE_BUTTON_LEFT
                    && event.hasControlDownWithQuirk();
            if (rightClick || ctrlLeftClick) {
                ChatComponent chat = NativeUi.chat(minecraft);
                if (chat instanceof ChatSuiteAccess access) {
                    GuiMessage msg = access.aurora$messageAtScreenPos(event.x(), event.y());
                    if (msg != null) {
                        String text = ChatSuite.copyText(msg);
                        if (!text.isEmpty()) {
                            minecraft.keyboardHandler.setClipboard(text);
                            aurora$copyToast.flash("Copied message", 1600L);
                            cir.setReturnValue(true);
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void aurora$drawSearchOverlayAndToast(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                  float partialTick, CallbackInfo ci) {
        if (!ChatSuite.isEnabled()) return;
        if (aurora$searchBox != null && !aurora$searchBox.getValue().isBlank() && minecraft != null) {
            ChatComponent chat = NativeUi.chat(minecraft);
            if (chat instanceof ChatSuiteAccess access) {
                String badge = access.aurora$visibleMessageCount() + " line"
                        + (access.aurora$visibleMessageCount() == 1 ? "" : "s");
                int bw = this.font.width(badge);
                int bx = aurora$searchBox.getX() + aurora$searchBox.getWidth() - bw - 2;
                int by = aurora$searchBox.getY() - this.font.lineHeight - 2;
                graphics.fill(bx - 3, by - 1, bx + bw + 3, by + this.font.lineHeight + 1, 0xA0000000);
                graphics.text(this.font, badge, bx, by, 0xFFD6DCE6, false);
            }
        }
        aurora$copyToast.render(graphics, this.font, this.width, this.height, 4);
    }

    @Unique
    private void aurora$focusSearch() {
        if (input != null) {
            input.setCanLoseFocus(true);
            input.setFocused(false);
        }
        if (aurora$searchBox != null) {
            aurora$searchBox.setFocused(true);
            this.setFocused(aurora$searchBox);
        }
    }

    @Unique
    private void aurora$focusChatInput() {
        if (aurora$searchBox != null) {
            aurora$searchBox.setFocused(false);
        }
        if (input != null) {
            input.setCanLoseFocus(false);
            input.setFocused(true);
            this.setFocused(input);
        }
    }
}
