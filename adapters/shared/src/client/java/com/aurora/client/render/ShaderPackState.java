package com.aurora.client.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Whether an Iris shader pack is drawing the world. A pack replaces the world's shaders with
 * its own, so world-shader work such as Gradient Dithering stands down while one is active.
 * Read through Iris's public API by reflection; without Iris, or if the API cannot be found,
 * no pack is in use.
 */
public final class ShaderPackState {
    private ShaderPackState() {}

    /** {@code IrisApi.getInstance().isShaderPackInUse()}, bound once; null without Iris. */
    private static final MethodHandle PACK_IN_USE = bind();

    private static MethodHandle bind() {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) return null;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup().findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class))
                    .bindTo(instance);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            com.aurora.client.AuroraClient.LOGGER.warn("Iris is installed but its shader pack state cannot be read; "
                    + "Gradient Dithering stays off while Iris is loaded", e);
            return MethodHandles.constant(boolean.class, true);
        }
    }

    /** True while an Iris shader pack renders the world. */
    public static boolean irisPackInUse() {
        if (PACK_IN_USE == null) return false;
        try {
            return (boolean) PACK_IN_USE.invokeExact();
        } catch (Throwable t) {
            return true;
        }
    }
}
