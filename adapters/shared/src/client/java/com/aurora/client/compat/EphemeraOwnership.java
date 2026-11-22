package com.aurora.client.compat;

import net.fabricmc.loader.api.FabricLoader;

/** Startup capabilities, available before either mod constructs its client services. */
public final class EphemeraOwnership {
    private static final boolean ENGINE = advertised("engine_ownership");
    private static final boolean WORLD_VISUALS = advertised("world_visual_ownership");

    private EphemeraOwnership() {}

    public static boolean has(String capability) {
        return switch (capability) {
            case "engine_ownership" -> ENGINE;
            case "world_visual_ownership" -> WORLD_VISUALS;
            default -> false;
        };
    }

    public static boolean ownsWorldVisuals() {
        return WORLD_VISUALS;
    }

    private static boolean advertised(String capability) {
        return FabricLoader.getInstance().getModContainer("ephemera")
                .map(mod -> mod.getMetadata().getCustomValue("ephemera:" + capability))
                .map(value -> switch (value.getType()) {
                    case BOOLEAN -> value.getAsBoolean();
                    case STRING -> "true".equals(value.getAsString());
                    default -> false;
                })
                .orElse(false);
    }
}
