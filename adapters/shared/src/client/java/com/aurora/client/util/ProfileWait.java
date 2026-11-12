package com.aurora.client.util;

import com.aurora.client.adapter.NativeUi;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.diagnostics.Timed;
import com.aurora.client.mixin.MinecraftProfileAccessor;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.BanNoticeScreens;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * No Profile Wait Before Title (ADR 0009). Vanilla builds the startup
 * screens, and Aurora's title screen its player model, by joining the
 * session server's profile lookup, so a slow lookup holds the first screen.
 * With the switch on, both use what is there and catch up when it arrives:
 *
 * <ul>
 *   <li>the startup screens skip the profile's name- and skin-ban notices
 *       and {@link #showNoticesWhenReady} shows them once the lookup
 *       finishes, as soon as no world is loaded;</li>
 *   <li>the title screen draws the signed-in account's name and default
 *       skin, then rebuilds with the real profile.</li>
 * </ul>
 *
 * Without the accessor mixin, or with the switch off, every call joins as
 * vanilla does. The profile result's class moved between authlib packages
 * across the supported lines, so it is a type parameter here and each
 * adapter's {@code InitialScreensProfileMixin} supplies how to read it.
 */
public final class ProfileWait {
    private ProfileWait() {}

    private static volatile @Nullable Runnable pendingNotices;
    private static boolean tickRegistered;

    /**
     * The profile lookup for the startup screens: its result when finished,
     * otherwise null (no notices) with the notices deferred.
     */
    public static <R> @Nullable R forStartupScreens(Minecraft mc, CompletableFuture<@Nullable R> future,
            Function<R, ? extends Iterable<? extends Enum<?>>> actions, Function<R, String> name) {
        if (future.isDone() || !AuroraConfig.get().perfNoProfileWait) return future.join();
        future.thenAcceptAsync(result -> {
            if (result != null) showNoticesWhenReady(mc, actions.apply(result), name.apply(result));
        }, mc);
        return null;
    }

    /**
     * The player's profile without blocking: the fetched one when ready,
     * otherwise the session's id and name, with {@code onArrival} run on the
     * client thread once the lookup finishes.
     */
    public static GameProfile gameProfile(Minecraft mc, Runnable onArrival) {
        CompletableFuture<?> future =
                mc instanceof MinecraftProfileAccessor a && AuroraConfig.get().perfNoProfileWait ? a.aurora$profileFuture() : null;
        if (future == null || future.isDone()) return mc.getGameProfile();
        future.thenRunAsync(onArrival, mc);
        return new GameProfile(mc.getUser().getProfileId(), mc.getUser().getName());
    }

    private static void showNoticesWhenReady(Minecraft mc, Iterable<? extends Enum<?>> actions, String profileName) {
        // ProfileActionType moved packages too, so actions are matched by name.
        boolean nameBan = false, skinBan = false;
        for (Enum<?> action : actions) {
            nameBan |= action.name().equals("FORCED_NAME_CHANGE");
            skinBan |= action.name().equals("USING_BANNED_SKIN");
        }
        if (!nameBan && !skinBan) return;
        boolean showName = nameBan, showSkin = skinBan;
        pendingNotices = () -> {
            // Same order as vanilla's startup chain: name notice, then skin notice, then back.
            Screen back = NativeUi.screen(mc);
            Runnable next = () -> NativeUi.setScreen(mc, back);
            if (showSkin) {
                Screen skin = BanNoticeScreens.createSkinBan(next);
                next = () -> NativeUi.setScreen(mc, skin);
            }
            if (showName) {
                Screen name = BanNoticeScreens.createNameBan(profileName, next);
                next = () -> NativeUi.setScreen(mc, name);
            }
            next.run();
        };
        if (!tickRegistered) {
            tickRegistered = true;
            ClientTickEvents.END_CLIENT_TICK.register(Timed.endTick(client -> {
                Runnable notices = pendingNotices;
                if (notices == null || client.level != null) return;
                Screen screen = NativeUi.screen(client);
                if (screen != null && !screen.canInterruptWithAnotherScreen()) return;
                pendingNotices = null;
                notices.run();
            }));
        }
    }
}
