package com.aurora.client.mixin;

import com.aurora.client.chat.ChatFormatting;
import com.aurora.client.chat.ChatSuite;
import com.aurora.client.chat.ChatSuiteAccess;
import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Core Chat Suite hooks on {@link ChatComponent}:
 * <ul>
 *   <li>Longer configurable chat history ({@code chatHistoryLimit})</li>
 *   <li>Compact duplicate messages ({@code [xN]})</li>
 *   <li>Local timestamps ({@code [HH:mm]} / {@code [HH:mm:ss]} / 12-hour)</li>
 *   <li>Inline player chat heads via vanilla {@code PlayerSprite}</li>
 *   <li>Chat Peek (hold keybind to view full focused chat while moving)</li>
 *   <li>Cursor-to-message hit resolution for Click / Ctrl+Click copy</li>
 * </ul>
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentSuiteMixin implements ChatSuiteAccess {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;

    @Shadow protected abstract void refreshTrimmedMessages();
    @Shadow protected abstract double getScale();
    @Shadow protected abstract int getLineHeight();
    @Shadow public abstract int getLinesPerPage();
    @Shadow protected abstract int getWidth();
    @Shadow protected abstract void addMessage(Component message, MessageSignature signature,
                                               GuiMessageSource source, GuiMessageTag tag);

    @Unique private boolean aurora$inDecoratedAdd;

    @ModifyConstant(
            method = {"addMessageToQueue", "addMessageToDisplayQueue"},
            constant = @Constant(intValue = 100)
    )
    private int aurora$raiseChatHistoryLimit(int vanillaLimit) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.chatEnabled) return vanillaLimit;
        return Math.max(100, Math.min(5000, cfg.chatHistoryLimit));
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void aurora$processIncomingChat(Component message, MessageSignature signature,
                                            GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        if (aurora$inDecoratedAdd || message == null || !ChatSuite.isEnabled()) return;

        AuroraConfig cfg = AuroraConfig.get();
        String rawPlain = message.getString();
        String canonical = ChatFormatting.normalizeForDuplicate(rawPlain);
        String copyText = ChatFormatting.extractCopyText(rawPlain);

        if (!ChatSuite.isReplayingHistory()) {
            ChatSuite.recordIncoming(minecraft, copyText);
        }

        int repeatCount = 1;
        if (cfg.chatCompactDuplicates && !ChatSuite.isReplayingHistory() && !canonical.isEmpty() && !allMessages.isEmpty()) {
            int window = Math.max(1, Math.min(50, cfg.chatCompactWindow));
            int limit = Math.min(allMessages.size(), window);
            for (int i = 0; i < limit; i++) {
                GuiMessage existing = allMessages.get(i);
                ChatSuite.MessageMeta prev = ChatSuite.meta(existing);
                String prevCanonical = prev != null
                        ? prev.canonicalText()
                        : ChatFormatting.normalizeForDuplicate(existing.content().getString());
                if (canonical.equals(prevCanonical)) {
                    repeatCount = (prev != null ? prev.repeatCount() : 1) + 1;
                    allMessages.remove(i);
                    ChatSuite.removeMeta(existing);
                    refreshTrimmedMessages();
                    break;
                }
            }
        }

        Component decorated = ChatSuite.decorate(minecraft, message, repeatCount);
        aurora$inDecoratedAdd = true;
        try {
            addMessage(decorated, signature, source, tag);
            if (!allMessages.isEmpty()) {
                ChatSuite.putMeta(allMessages.getFirst(), new ChatSuite.MessageMeta(canonical, repeatCount, copyText));
            }
        } finally {
            aurora$inDecoratedAdd = false;
        }
        ci.cancel();
    }

    @Inject(method = "clearMessages", at = @At("TAIL"))
    private void aurora$clearMetaOnClear(boolean clearSentHistory, CallbackInfo ci) {
        ChatSuite.clearMeta();
    }

    @ModifyVariable(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private ChatComponent.DisplayMode aurora$peekDisplayMode(ChatComponent.DisplayMode mode) {
        if (mode == ChatComponent.DisplayMode.BACKGROUND && AuroraConfig.get().chatEnabled && ChatSuite.isPeeking()) {
            return ChatComponent.DisplayMode.FOREGROUND;
        }
        return mode;
    }

    @Inject(method = "isChatFocused", at = @At("HEAD"), cancellable = true)
    private void aurora$peekFocusedHeight(CallbackInfoReturnable<Boolean> cir) {
        if (AuroraConfig.get().chatEnabled && ChatSuite.isPeeking()) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public GuiMessage aurora$messageAtScreenPos(double mouseX, double mouseY) {
        if (minecraft == null || trimmedMessages.isEmpty()) return null;
        double scale = getScale();
        if (scale <= 0.0) return null;
        double chatX = mouseX / scale - 4.0;
        double maxWidth = Mth.ceil(getWidth() / scale) + 8.0;
        if (chatX < -4.0 || chatX > maxWidth) return null;

        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        double bottomY = Math.floor((guiHeight - 40.0) / scale);
        double scaledY = mouseY / scale;
        double relFromBottom = bottomY - scaledY;
        if (relFromBottom < 0.0) return null;

        int lineHeight = getLineHeight();
        if (lineHeight <= 0) return null;
        int visibleRow = (int) (relFromBottom / lineHeight);
        if (visibleRow < 0 || visibleRow >= getLinesPerPage()) return null;

        int lineIndex = visibleRow + chatScrollbarPos;
        if (lineIndex < 0 || lineIndex >= trimmedMessages.size()) return null;
        return trimmedMessages.get(lineIndex).parent();
    }

    @Override
    public int aurora$visibleMessageCount() {
        return trimmedMessages.size();
    }

    @Override
    public int aurora$totalMessageCount() {
        return allMessages.size();
    }
}
