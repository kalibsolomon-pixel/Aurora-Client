package com.aurora.client.performance;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.health.MixinHealth;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;

/**
 * Skips extracting entities and block entities that opaque terrain certainly
 * hides from the camera (ADR 0009, {@code perfEntityOcclusion}).
 *
 * <p>Each frame {@link #begin} decides whether the terrain can be trusted at
 * all; {@link #hideBody} and {@link #hideChest} then run
 * {@link OcclusionSweep} against a cell predicate that is true only for a full
 * opaque cube ({@code isSolidRender}, a block model) in a section the renderer
 * has built and faded in and that has not changed within the settle window.
 * Vanilla culls a face only against an occluding neighbour, so a line that
 * enters such a cube has already met a drawn opaque face unless the eye
 * itself sits in occluding geometry, which {@link #begin} rules out.
 *
 * <p>Anything vanilla draws beyond the box, through walls, or from another
 * entity's state is never skipped: names, team labels and custom labels,
 * glowing outlines, leashes and fishing lines with both their ends, the
 * camera's own vehicle stack, players/avatars, dragons, withers, giants,
 * guardian beams, shulkers, minecarts, displays, oversized boxes, and the block
 * entities outside a small list of renderers that stay within their own cell.
 * Aurora's living-entity health labels keep living entities drawn while on.
 *
 * <p>Runs on the render thread inside level extraction only; every read of
 * the level happens there. Nothing allocates per frame.
 */
public final class EntityOcclusion {
    /** The eye is treated as a cube this far either side of the camera: view bobbing moves the projection, not the camera. */
    private static final double EYE = 0.25;
    /** Settle time beyond the chunk fade-in, after a section changes or the chunk builder was last busy. */
    private static final long SETTLE_MS = 1000L;
    private static final int FRAME_TESTS = 4096;
    private static final int FRAME_PROBES = 65536;
    private static final int CELL_SLOTS = 8192;
    private static final int SECTION_SLOTS = 1024;
    /** Boxes wider or taller than this are never skipped. */
    private static final double MAX_WIDTH = 3.0, MAX_HEIGHT = 4.0;

    private static final OcclusionSweep SWEEP = new OcclusionSweep(16, 36);
    private static final OcclusionSweep.Cells CELLS = EntityOcclusion::opaque;
    private static final SectionStability STABILITY = new SectionStability(8192);
    /** Loaded entities that can hold a leash end or a fishing line: walked each frame to find the other ends. */
    private static final IdentityTable<Entity> LINKERS = new IdentityTable<>(256);
    /** Leash holders and hooked entities this frame. */
    private static final IdentityTable<Entity> ENDS = new IdentityTable<>(64);
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos SECTION_POS = new BlockPos.MutableBlockPos();
    private static final long[] cellKeys = new long[CELL_SLOTS];
    private static final int[] cellTags = new int[CELL_SLOTS];
    private static final long[] sectionKeys = new long[SECTION_SLOTS];
    private static final int[] sectionTags = new int[SECTION_SLOTS];
    private static final MethodHandle SHADER_PACK = shaderPackProbe();

    private static boolean enabled;
    private static boolean active;
    private static ClientLevel level;
    private static ClientLevel linkerLevel;
    private static Entity viewer;
    private static double eyeX, eyeY, eyeZ;
    private static long now, window, busyAt = Long.MIN_VALUE / 2;
    private static int stamp = 1, tests;
    private static boolean keepLiving;

    /** Entities and block entities skipped since startup; read by the parity probe. */
    public static long bodiesSkipped, chestsSkipped, framesActive;

    private EntityOcclusion() {}

    /** Called once from the client initializer so no entity or chunk load is missed. */
    public static void initialize() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof Leashable) && !(entity instanceof FishingHook)) return;
            if (world != linkerLevel) {
                LINKERS.clear();
                linkerLevel = world;
            }
            LINKERS.add(entity);
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> LINKERS.remove(entity));
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (enabled && world == level) STABILITY.markColumn(chunk.getPos().x(), chunk.getPos().z(), Util.getMillis(), window);
        });
    }

    // ---- terrain changes (OcclusionDirtyMixin) ----

    public static void blockChanged(ClientLevel world, BlockPos pos) {
        if (!enabled || world != level || !AuroraConfig.get().perfEntityOcclusion) return;
        markSections((pos.getX() - 1) >> 4, (pos.getY() - 1) >> 4, (pos.getZ() - 1) >> 4,
                (pos.getX() + 1) >> 4, (pos.getY() + 1) >> 4, (pos.getZ() + 1) >> 4);
    }

    public static void sectionsChanged(ClientLevel world, int x0, int y0, int z0, int x1, int y1, int z1) {
        if (!enabled || world != level || !AuroraConfig.get().perfEntityOcclusion) return;
        markSections(x0, y0, z0, x1, y1, z1);
    }

    private static void markSections(int x0, int y0, int z0, int x1, int y1, int z1) {
        long time = Util.getMillis();
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 64) {
            STABILITY.reset(time, window);
            return;
        }
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) STABILITY.markSection(x, y, z, time, window);
    }

    // ---- per frame (OcclusionFrameMixin) ----

    public static void begin(Camera camera) {
        active = false;
        AuroraConfig cfg = AuroraConfig.get();
        Minecraft mc = Minecraft.getInstance();
        long time = Util.getMillis();
        window = OcclusionTerrain.fadeMillis(mc) + SETTLE_MS;
        if (!cfg.perfEntityOcclusion || mc.level == null) {
            enabled = false;
            return;
        }
        if (!enabled || mc.level != level) {
            // Changes made while the gate was off, or in another level, were not recorded.
            enabled = true;
            level = mc.level;
            STABILITY.reset(time, window);
        }
        if (!mc.isSameThread() || !MixinHealth.applied("OcclusionDirtyMixin") || shaderPackInUse()) return;
        if (!mc.levelRenderer.hasRenderedAllSections()) {
            busyAt = time;
            return;
        }
        Entity cameraEntity = camera.entity();
        if (time - busyAt < window || cameraEntity == null) return;
        Vec3 eye = camera.position();
        for (int x = floor(eye.x - EYE); x <= floor(eye.x + EYE); x++)
            for (int y = floor(eye.y - EYE); y <= floor(eye.y + EYE); y++)
                for (int z = floor(eye.z - EYE); z <= floor(eye.z + EYE); z++)
                    if (level.getBlockState(POS.set(x, y, z)).canOcclude()) return;
        now = time;
        viewer = cameraEntity;
        eyeX = eye.x;
        eyeY = eye.y;
        eyeZ = eye.z;
        keepLiving = cfg.playerHealthIndicators;
        if (stamp == Integer.MAX_VALUE >> 1) {
            Arrays.fill(cellTags, 0);
            Arrays.fill(sectionTags, 0);
            stamp = 1;
        } else {
            stamp++;
        }
        tests = 0;
        SWEEP.resetProbes();
        ENDS.clear();
        if (linkerLevel != level) {
            LINKERS.clear();
            linkerLevel = level;
        }
        for (int i = 0, n = LINKERS.capacity(); i < n; i++) {
            Entity linker = LINKERS.at(i);
            if (linker instanceof Leashable leashable && leashable.getLeashHolder() != null) ENDS.add(leashable.getLeashHolder());
            if (linker instanceof FishingHook hook) {
                if (hook.getHookedIn() != null) ENDS.add(hook.getHookedIn());
                if (hook.getOwner() != null) ENDS.add(hook.getOwner());
            }
        }
        framesActive++;
        active = true;
    }

    public static void end() {
        active = false;
        viewer = null;
    }

    // ---- decisions (OcclusionBodyMixin, OcclusionChestMixin) ----

    public static boolean hideBody(Entity entity) {
        if (!active || !mayHide(entity) || !budget()) return false;
        AABB box = entity.getBoundingBox();
        double width = Math.max(box.getXsize(), box.getZsize());
        // Grow the box by the model's overhang and the shadow, which reaches
        // a shadow radius (at most the box width) beyond and below the feet.
        double side = 1.0 + Math.max(0.5, width * 0.5), below = 1.0 + width;
        double dx = entity.xOld - entity.getX(), dy = entity.yOld - entity.getY(), dz = entity.zOld - entity.getZ();
        if (!SWEEP.hidden(CELLS, eyeX, eyeY, eyeZ, EYE,
                box.minX + Math.min(0, dx) - side, box.minY + Math.min(0, dy) - below, box.minZ + Math.min(0, dz) - side,
                box.maxX + Math.max(0, dx) + side, box.maxY + Math.max(0, dy) + side, box.maxZ + Math.max(0, dz) + side))
            return false;
        bodiesSkipped++;
        return true;
    }

    public static boolean hideChest(BlockEntity blockEntity) {
        if (!active || blockEntity.getLevel() != level || !OcclusionTerrain.contained(blockEntity.getType()) || !budget())
            return false;
        BlockPos pos = blockEntity.getBlockPos();
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (!SWEEP.hidden(CELLS, eyeX, eyeY, eyeZ, EYE, x - 1.0, y - 1.0, z - 1.0, x + 2.0, y + 2.0, z + 2.0)) return false;
        chestsSkipped++;
        return true;
    }

    private static boolean budget() {
        return ++tests <= FRAME_TESTS && SWEEP.probes() < FRAME_PROBES;
    }

    private static boolean mayHide(Entity entity) {
        if (entity == viewer || entity.level() != level || entity.getRootVehicle() == viewer.getRootVehicle()) return false;
        if (entity instanceof Player || entity instanceof Avatar || entity instanceof Display
                || entity instanceof FishingHook || entity instanceof LeashFenceKnotEntity
                || entity instanceof LightningBolt || entity instanceof EnderDragon || entity instanceof EndCrystal
                || entity instanceof WitherBoss || entity instanceof Giant || entity instanceof Guardian
                || entity instanceof Shulker || entity instanceof AbstractMinecart) return false;
        if (entity.hasCustomName() || entity.shouldShowName() || entity.getTeam() != null
                || Minecraft.getInstance().shouldEntityAppearGlowing(entity)) return false;
        if (entity instanceof Leashable leashable && (leashable.isLeashed() || leashable.getLeashData() != null)
                || ENDS.contains(entity)) return false;
        if (keepLiving && entity instanceof LivingEntity) return false;
        AABB box = entity.getBoundingBox();
        return Math.max(box.getXsize(), box.getZsize()) <= MAX_WIDTH && box.getYsize() <= MAX_HEIGHT;
    }

    // ---- terrain the renderer has certainly drawn ----

    private static boolean opaque(int x, int y, int z) {
        if (level.isOutsideBuildHeight(y)) return false;
        long key = BlockPos.asLong(x, y, z);
        int slot = mix(key) & (CELL_SLOTS - 1);
        int tag = cellTags[slot];
        if (cellKeys[slot] == key && tag >>> 1 == stamp) return (tag & 1) != 0;
        boolean opaque = sectionDrawn(x >> 4, y >> 4, z >> 4) && solid(level.getBlockState(POS.set(x, y, z)));
        cellKeys[slot] = key;
        cellTags[slot] = stamp << 1 | (opaque ? 1 : 0);
        return opaque;
    }

    private static boolean sectionDrawn(int sx, int sy, int sz) {
        long key = SectionStability.key(sx, sy, sz);
        int slot = mix(key) & (SECTION_SLOTS - 1);
        int tag = sectionTags[slot];
        if (sectionKeys[slot] == key && tag >>> 1 == stamp) return (tag & 1) != 0;
        Minecraft mc = Minecraft.getInstance();
        boolean drawn = STABILITY.settled(sx, sy, sz, now, window)
                && OcclusionTerrain.drawn(mc, SECTION_POS.set(sx << 4, sy << 4, sz << 4), window - SETTLE_MS);
        sectionKeys[slot] = key;
        sectionTags[slot] = stamp << 1 | (drawn ? 1 : 0);
        return drawn;
    }

    private static boolean solid(BlockState state) {
        return state.isSolidRender() && state.getRenderShape() == RenderShape.MODEL;
    }

    private static int mix(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 29));
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    // ---- shader packs ----

    /** A shader pack draws entities into shadow maps from the sun's view, where terrain hides different things. */
    private static boolean shaderPackInUse() {
        if (SHADER_PACK == null) return false;
        try {
            return (boolean) SHADER_PACK.invokeExact();
        } catch (Throwable failure) {
            return true;
        }
    }

    private static MethodHandle shaderPackProbe() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return null;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup()
                    .findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(instance);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            // Iris without the API it documents: assume a pack is on and skip nothing.
            return MethodHandles.constant(boolean.class, true);
        }
    }
}
