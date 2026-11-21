package com.aurora.client.ui.util;

import com.aurora.client.health.MixinHealth;

/**
 * Whether Aurora's own shaders can be compiled yet (ADR 0010). Until vanilla's Shader Loader has applied once,
 * Minecraft compiles pipelines from the vanilla-only startup resources, where Aurora's shaders do not exist. A mod
 * that lets screens draw during the first reload (RRLS) makes that window visible: a compile that fails inside it
 * says nothing about the shader, so callers wait instead of deciding.
 */
public final class ShaderLoad {
    /** The mixin that reports the apply. */
    static final String HOOK = "ShaderManagerAppliedMixin";

    private static volatile boolean applied;

    private ShaderLoad() {}

    /** Called when the Shader Loader's apply returns; later reloads leave it set, their old pipelines stay usable. */
    public static void markApplied() { applied = true; }

    /**
     * True once a compile of Aurora's shaders means something. Also true when the hook that would say so is not
     * working, which leaves the decision where it was before the hook existed.
     */
    public static boolean settled() { return applied || MixinHealth.failed(HOOK); }

    public static void resetForTests() { applied = false; }
}
