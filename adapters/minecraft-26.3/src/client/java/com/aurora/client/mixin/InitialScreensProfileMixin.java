package com.aurora.client.mixin;

import com.aurora.client.util.ProfileWait;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.services.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * No Profile Wait Before Title (ADR 0009): {@code Gui.addInitialScreens} builds the startup
 * screens without joining the session server's profile lookup. The
 * name- and skin-ban notices that depend on it are shown when it arrives
 * ({@code util/ProfileWait}).
 */
@Mixin(Gui.class)
public abstract class InitialScreensProfileMixin {
    @WrapOperation(method = "addInitialScreens", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;getProfileResult()Lcom/mojang/authlib/services/ProfileResult;"))
    private ProfileResult aurora$noProfileWait(Minecraft minecraft, Operation<ProfileResult> original) {
        if (!(minecraft instanceof MinecraftProfileAccessor a)) return original.call(minecraft);
        @SuppressWarnings("unchecked")
        var future = (java.util.concurrent.CompletableFuture<ProfileResult>) a.aurora$profileFuture();
        return ProfileWait.forStartupScreens(minecraft, future, ProfileResult::actions, result -> result.profile().name());
    }
}
