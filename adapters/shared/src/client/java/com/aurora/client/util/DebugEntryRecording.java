package com.aurora.client.util;

import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * One F3 entry's output for the Debug Entry Cache ({@code DebugScreenEntryCacheMixin}):
 * passes every call through to {@link #target} and records it so the next
 * frames can replay it without asking the entry again.
 */
public final class DebugEntryRecording implements DebugScreenDisplayer {
    public final List<Runnable> replay = new ArrayList<>();
    public DebugScreenDisplayer target;
    public long takenAt;
    public @Nullable Level level;

    @Override public void addPriorityLine(String line) {
        replay.add(() -> target.addPriorityLine(line));
        target.addPriorityLine(line);
    }

    @Override public void addLine(String line) {
        replay.add(() -> target.addLine(line));
        target.addLine(line);
    }

    @Override public void addToGroup(Identifier group, Collection<String> lines) {
        List<String> copy = List.copyOf(lines);
        replay.add(() -> target.addToGroup(group, copy));
        target.addToGroup(group, copy);
    }

    @Override public void addToGroup(Identifier group, String line) {
        replay.add(() -> target.addToGroup(group, line));
        target.addToGroup(group, line);
    }
}
