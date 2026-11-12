package com.aurora.client.mixin;

import com.aurora.client.util.ProfileWait;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

/**
 * No Profile Wait Before Title (ADR 0009): {@code Minecraft.addInitialScreens} builds the startup
 * screens without joining the session server's profile lookup. The
 * name- and skin-ban notices that depend on it are shown when it arrives
 * ({@code util/ProfileWait}).
 */
@Mixin(Minecraft.class)
public abstract class InitialScreensProfileMixin {
    @WrapOperation(method = "addInitialScreens", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"))
    @SuppressWarnings("unchecked")
    private Object aurora$noProfileWait(CompletableFuture<?> future, Operation<Object> original) {
        if (future.isDone()) return original.call(future);
        return ProfileWait.forStartupScreens((Minecraft) (Object) this, (CompletableFuture<ProfileResult>) future,
                ProfileResult::actions, result -> result.profile().name());
    }
}
