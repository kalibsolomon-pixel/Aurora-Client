package com.aurora.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

/**
 * The session server's profile lookup, read without joining it
 * ({@code util/ProfileWait}). Its result class differs between lines, so it
 * is left unnamed.
 */
@Mixin(Minecraft.class)
public interface MinecraftProfileAccessor {
    @Accessor("profileFuture")
    CompletableFuture<?> aurora$profileFuture();
}
