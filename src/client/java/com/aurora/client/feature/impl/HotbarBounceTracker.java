package com.aurora.client.feature.impl;

import com.aurora.client.util.RoundedRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Per-slot pulse animation when a hotbar slot's item count increases. The
 * pulse is a fading white outline drawn over the slot for ~250ms.
 */
public final class HotbarBounceTracker {

    private static final int SLOTS = 9;
    private static final long PULSE_MS = 250L;

    private static final int[] lastCount = new int[SLOTS];
    private static final long[] pulseStart = new long[SLOTS];
    private static boolean initialized = false;

    private HotbarBounceTracker() {}

    public static void tickAndRender(GuiGraphics ctx, Minecraft mc) {
        if (mc.player == null) return;

        if (!initialized) {
            for (int i = 0; i < SLOTS; i++) {
                ItemStack s = mc.player.getInventory().getItem(i);
                lastCount[i] = s == null ? 0 : s.getCount();
            }
            initialized = true;
            return;
        }

        long now = System.currentTimeMillis();

        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            int count = stack == null ? 0 : stack.getCount();
            if (count > lastCount[i]) pulseStart[i] = now;
            lastCount[i] = count;
        }

        // Render pulses. Hotbar centered horizontally, slot is 20px wide,
        // 22px tall, with 1px gap between hotbar slots.
        int screenW = ctx.guiWidth();
        int screenH = ctx.guiHeight();
        int hotbarW = 182; // standard
        int hotbarX = (screenW - hotbarW) / 2;
        int hotbarY = screenH - 22;

        for (int i = 0; i < SLOTS; i++) {
            long age = now - pulseStart[i];
            if (pulseStart[i] == 0 || age > PULSE_MS) continue;
            float t = age / (float) PULSE_MS; // 0..1
            float alpha = 1f - t;
            alpha = alpha * alpha; // ease-out
            int alphaByte = Math.round(alpha * 200f);
            if (alphaByte < 4) continue;
            int color = (alphaByte << 24) | 0x00FFFFFF;
            int slotX = hotbarX + 1 + i * 20;
            int slotY = hotbarY + 1;
            // Pulse outline expands outward by 1px scaled with t
            int expand = Math.round(t * 2f);
            RoundedRect.outline(ctx,
                    slotX - expand, slotY - expand,
                    slotX + 18 + expand, slotY + 18 + expand,
                    2, color);
        }
    }
}