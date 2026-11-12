package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.DebugEntryRecording;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Debug Entry Cache (ADR 0009): the F3 screen asks every enabled entry for
 * its lines on every frame, and several entries format dozens of strings or
 * query the server's chunk each time. Each entry's output is recorded and
 * replayed for {@link #aurora$REFRESH_NANOS}, the game's tick length, so
 * the lines still change as often as the game state behind them.
 */
@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenEntryCacheMixin {
    @Unique private static final long aurora$REFRESH_NANOS = 50_000_000L;

    @Unique private final Map<DebugScreenEntry, DebugEntryRecording> aurora$recordings = new IdentityHashMap<>();

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/debug/DebugScreenEntry;display(Lnet/minecraft/client/gui/components/debug/DebugScreenDisplayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/chunk/LevelChunk;)V"))
    private void aurora$replayRecent(DebugScreenEntry entry, DebugScreenDisplayer displayer, @Nullable Level level,
                                     @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk, Operation<Void> original) {
        if (!AuroraConfig.get().perfDebugEntryCache) {
            if (!aurora$recordings.isEmpty()) aurora$recordings.clear();
            original.call(entry, displayer, level, clientChunk, serverChunk);
            return;
        }
        long now = System.nanoTime();
        DebugEntryRecording recording = aurora$recordings.get(entry);
        if (recording != null && recording.level == level && now - recording.takenAt < aurora$REFRESH_NANOS) {
            recording.target = displayer;
            for (Runnable call : recording.replay) call.run();
            return;
        }
        if (recording == null) aurora$recordings.put(entry, recording = new DebugEntryRecording());
        recording.replay.clear();
        recording.target = displayer;
        recording.takenAt = now;
        recording.level = level;
        original.call(entry, recording, level, clientChunk, serverChunk);
    }
}
