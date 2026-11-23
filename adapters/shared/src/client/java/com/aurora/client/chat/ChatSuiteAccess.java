package com.aurora.client.chat;

import net.minecraft.client.multiplayer.chat.GuiMessage;

/** Mixin accessor exposed by {@code ChatComponentSuiteMixin} for Chat Suite actions. */
public interface ChatSuiteAccess {
    /** Returns the {@link GuiMessage} under the given GUI-scaled cursor coordinates, or null. */
    GuiMessage aurora$messageAtScreenPos(double mouseX, double mouseY);

    /** Sets Aurora's inline chat search query without replacing vanilla's visibleMessageFilter. */
    void aurora$setSearchQuery(String query);

    /** Returns the number of visible messages currently matching the active filter. */
    int aurora$visibleMessageCount();

    /** Returns the total number of retained messages in chat history. */
    int aurora$totalMessageCount();
}
