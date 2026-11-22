package com.aurora.client.compat;

import com.aurora.client.AuroraClient;
import com.aurora.client.adapter.NativeUi;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ObjectShare;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Zero-compile-coupling Fabric {@link ObjectShare} bridge to Ephemera.
 *
 * <p>When Ephemera is absent, {@link #MOD_LOADED} is constant {@code false} and every fast-write
 * check folds away with zero runtime overhead. When Ephemera is loaded, Fabric's
 * {@link ObjectShare#whenAvailable} registers its published {@link MethodHandle} fast writers
 * ({@code ephemera:fast_write}) and live performance controls ({@code ephemera:settings})
 * regardless of mod initialization order.
 */
public final class EphemeraBridge {
    private static final String SHARE_FAST_WRITE = "ephemera:fast_write";
    private static final String SHARE_SETTINGS = "ephemera:settings";

    private static final boolean MOD_LOADED = detectLoaded();

    private static volatile boolean fastWriteDisabled;
    private static volatile MethodHandle sdfGlyphHandle;
    private static volatile MethodHandle sdfBoxHandle;
    private static volatile MethodHandle lineHandle;

    private static volatile Map<String, Object> settingsMap;

    static {
        if (MOD_LOADED) {
            try {
                ObjectShare share = FabricLoader.getInstance().getObjectShare();
                share.whenAvailable(SHARE_FAST_WRITE, (key, value) -> onFastWriteAvailable(value));
                share.whenAvailable(SHARE_SETTINGS, (key, value) -> onSettingsAvailable(value));
            } catch (Throwable t) {
                AuroraClient.LOGGER.warn("[Ephemera] Failed to subscribe to ObjectShare bridge: {}", t.toString());
            }
        }
    }

    private EphemeraBridge() {}

    public record OptionInfo(
            String key,
            String label,
            String description,
            String category,
            boolean value,
            boolean defaultValue,
            boolean requiresRestart
    ) {}

    private static boolean detectLoaded() {
        try {
            return FabricLoader.getInstance().isModLoaded("ephemera");
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void onFastWriteAvailable(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return;
        Object glyph = raw.get("sdfGlyph");
        Object box = raw.get("sdfBox");
        Object line = raw.get("line");
        if (glyph instanceof MethodHandle mh) sdfGlyphHandle = mh;
        if (box instanceof MethodHandle mh) sdfBoxHandle = mh;
        if (line instanceof MethodHandle mh) lineHandle = mh;
        AuroraClient.LOGGER.info("[Ephemera] Connected direct fast-write bridge (sdfGlyph={}, sdfBox={}, line={})",
                sdfGlyphHandle != null, sdfBoxHandle != null, lineHandle != null);
    }

    @SuppressWarnings("unchecked")
    private static void onSettingsAvailable(Object value) {
        if (value instanceof Map<?, ?> raw) {
            settingsMap = (Map<String, Object>) raw;
            AuroraClient.LOGGER.info("[Ephemera] Connected settings bridge ({})", statusSummary());
        }
    }

    /** True when the Ephemera mod is present in this Fabric runtime. */
    public static boolean isLoaded() {
        return MOD_LOADED;
    }

    /** True once Ephemera has published its settings bridge into Fabric ObjectShare. */
    public static boolean hasSettings() {
        return MOD_LOADED && settingsMap != null;
    }

    /**
     * Delegates an Aurora SDF glyph draw (1, 2, or 4 quads) to Ephemera's single-reservation
     * native vertex writer when available. Returns {@code false} to fall back to Aurora's
     * standard {@link VertexConsumer} path.
     */
    public static boolean writeSdfGlyph(VertexConsumer out, Matrix4fc pose, int light,
                                        float penX, float penY,
                                        float quadLeft, float quadTop, float quadRight, float quadBottom,
                                        float boldOffset, float shadowOffset,
                                        int color, int shadowColor,
                                        boolean bold, boolean italic,
                                        float u0, float u1, float v0, float v1) {
        if (!MOD_LOADED || fastWriteDisabled) return false;
        MethodHandle handle = sdfGlyphHandle;
        if (handle == null) return false;
        try {
            return (boolean) handle.invokeExact(
                    out, pose, light, penX, penY,
                    quadLeft, quadTop, quadRight, quadBottom,
                    boldOffset, shadowOffset, color, shadowColor,
                    bold, italic, u0, u1, v0, v1);
        } catch (Throwable t) {
            disableFastWrite("sdfGlyph", t);
            return false;
        }
    }

    /**
     * Delegates an Aurora SDF wireframe box draw to Ephemera's single-reservation native line writer.
     */
    public static boolean writeSdfBox(VertexConsumer buf, Matrix4f m, Vec3 camPos,
                                      double x1, double y1, double z1,
                                      double x2, double y2, double z2,
                                      float r, float g, float b, float a, float width) {
        if (!MOD_LOADED || fastWriteDisabled) return false;
        MethodHandle handle = sdfBoxHandle;
        if (handle == null) return false;
        try {
            return (boolean) handle.invokeExact(
                    buf, m, camPos, x1, y1, z1, x2, y2, z2, r, g, b, a, width);
        } catch (Throwable t) {
            disableFastWrite("sdfBox", t);
            return false;
        }
    }

    /**
     * Delegates an Aurora world-space line segment draw to Ephemera's native line writer.
     */
    public static boolean writeLine(VertexConsumer buf, Matrix4f m, Vec3 camPos,
                                    double x1, double y1, double z1,
                                    double x2, double y2, double z2,
                                    float r, float g, float b, float a,
                                    float width, boolean sdf) {
        if (!MOD_LOADED || fastWriteDisabled) return false;
        MethodHandle handle = lineHandle;
        if (handle == null) return false;
        try {
            return (boolean) handle.invokeExact(
                    buf, m, camPos, x1, y1, z1, x2, y2, z2, r, g, b, a, width, sdf);
        } catch (Throwable t) {
            disableFastWrite("line", t);
            return false;
        }
    }

    private static void disableFastWrite(String op, Throwable t) {
        if (!fastWriteDisabled) {
            fastWriteDisabled = true;
            AuroraClient.LOGGER.warn("[Ephemera] Disabling direct fast-write bridge after {} failure: {}", op, t.toString());
        }
    }

    public static String statusSummary() {
        String s = readStringSupplier("statusSummary");
        return s != null && !s.isBlank() ? s : "Ephemera rendering engine";
    }

    /**
     * One line for the copied diagnostics report: Ephemera's own summary and whether the
     * direct fast-write bridge is carrying Aurora's glyph and line writes. Empty when
     * Ephemera is not installed.
     */
    public static String diagnosticsLine() {
        if (!MOD_LOADED) return "";
        String fastWrite;
        if (fastWriteDisabled) {
            fastWrite = "off after a failure";
        } else if (sdfGlyphHandle == null && sdfBoxHandle == null && lineHandle == null) {
            fastWrite = "not connected";
        } else {
            fastWrite = "on (sdfGlyph=" + (sdfGlyphHandle != null) + ", sdfBox=" + (sdfBoxHandle != null)
                    + ", line=" + (lineHandle != null) + ")";
        }
        return (hasSettings() ? statusSummary() : "settings bridge not connected") + " | direct fast-write " + fastWrite;
    }

    public static String backend() {
        String s = readStringSupplier("backend");
        return s != null ? s : "UNKNOWN";
    }

    public static boolean isMeshShaderActive() {
        Map<String, Object> map = settingsMap;
        if (map != null && map.get("meshShaderActive") instanceof BooleanSupplier bs) {
            try {
                return bs.getAsBoolean();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static List<OptionInfo> options() {
        Map<String, Object> map = settingsMap;
        if (map == null || !(map.get("options") instanceof Supplier<?> supplier)) {
            return List.of();
        }
        try {
            Object raw = supplier.get();
            if (!(raw instanceof List<?> list)) return List.of();
            List<OptionInfo> out = new ArrayList<>(list.size());
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) continue;
                String key = stringOrEmpty(m.get("key"));
                String label = stringOrEmpty(m.get("label"));
                if (key.isEmpty() || label.isEmpty()) continue;
                String description = stringOrEmpty(m.get("description"));
                String category = stringOrEmpty(m.get("category"));
                boolean val = m.get("value") instanceof Boolean b && b;
                boolean defVal = m.get("defaultValue") instanceof Boolean b
                        ? b
                        : !"diagnosticsHudOverlay".equals(key);
                boolean restart = m.get("requiresRestart") instanceof Boolean b && b;
                out.add(new OptionInfo(key, label, description, category, val, defVal, restart));
            }
            return List.copyOf(out);
        } catch (Throwable t) {
            AuroraClient.LOGGER.warn("[Ephemera] Failed to read Ephemera option list: {}", t.toString());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean getBoolean(String key) {
        Map<String, Object> map = settingsMap;
        if (map != null && map.get("get") instanceof Predicate<?> pred) {
            try {
                return ((Predicate<String>) pred).test(key);
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static void setBoolean(String key, boolean value) {
        Map<String, Object> map = settingsMap;
        if (map != null && map.get("set") instanceof BiPredicate<?, ?> setter) {
            try {
                ((BiPredicate<String, Boolean>) setter).test(key, value);
            } catch (Throwable t) {
                AuroraClient.LOGGER.warn("[Ephemera] Failed to update option '{}': {}", key, t.toString());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void openConfigScreen(Screen parent) {
        Map<String, Object> map = settingsMap;
        if (map != null && map.get("openConfigScreen") instanceof Function<?, ?> fn) {
            try {
                Object result = ((Function<Object, Object>) fn).apply(parent);
                if (result instanceof Screen screen) {
                    NativeUi.setScreen(Minecraft.getInstance(), screen);
                }
            } catch (Throwable t) {
                AuroraClient.LOGGER.warn("[Ephemera] Failed to open Ephemera settings screen: {}", t.toString());
            }
        }
    }

    private static String readStringSupplier(String key) {
        Map<String, Object> map = settingsMap;
        if (map != null && map.get(key) instanceof Supplier<?> supplier) {
            try {
                Object val = supplier.get();
                return val instanceof String s ? s : null;
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static String stringOrEmpty(Object value) {
        return value instanceof String s ? s : "";
    }
}
