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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
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
 * <p>Performance fast paths (all mathematically exact):
 * <ul>
 *   <li>All-air sections ({@link LevelChunkSection#hasOnlyAir()}) short-circuit
 *       as non-opaque on the first probe; settled non-empty sections are read
 *       directly from a per-frame {@link LevelChunkSection} cache (cleared in
 *       {@link #end()} so no chunk is retained past the frame).</li>
 *   <li>Per-block solidity results persist across frames under a
 *       {@code terrainStamp} invalidated whenever any block, section or chunk
 *       changes, avoiding repeat {@code PalettedContainer.get} lookups.</li>
 *   <li>Exact {@link OcclusionSweep} decisions are cached per entity/block
 *       entity while the camera position, swept bounds, render distance and
 *       {@code terrainStamp} remain identical (and refreshed every 250 ms so
 *       newly settled sections begin culling promptly).</li>
 *   <li>Leash holders are rebuilt lazily on demand and only when a tick
 *       advances or a leash packet / entity load-unload fires; fishing hooks
 *       are tracked in their own table (empty when none are cast).</li>
 * </ul>
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
    /** Periodic refresh of cached sweep results so newly settled sections start culling promptly. */
    private static final long SWEEP_CACHE_TTL_MS = 250L;
    private static final int FRAME_TESTS = 4096;
    private static final int FRAME_PROBES = 65536;
    private static final int CELL_SLOTS = 8192;
    private static final int SECTION_SLOTS = 1024;
    private static final int BODY_SLOTS = 1024;
    private static final int CHEST_SLOTS = 512;
    /** Boxes wider or taller than this are never skipped. */
    private static final double MAX_WIDTH = 3.0, MAX_HEIGHT = 4.0;

    private static final OcclusionSweep SWEEP = new OcclusionSweep(16, 36);
    private static final OcclusionSweep.Cells CELLS = EntityOcclusion::opaque;
    private static final SectionStability STABILITY = new SectionStability(8192);
    /** Loaded leashable entities: rebuilt lazily at tick/packet cadence. */
    private static final IdentityTable<Entity> LEASHABLES = new IdentityTable<>(256);
    /** Loaded fishing hooks (typically empty): checked live per frame when non-empty. */
    private static final IdentityTable<Entity> HOOKS = new IdentityTable<>(16);
    /** Leash holders cached for the current tick/leash epoch. */
    private static final IdentityTable<Entity> LEASH_ENDS = new IdentityTable<>(64);
    /** Fishing hook endpoints for the current frame. */
    private static final IdentityTable<Entity> HOOK_ENDS = new IdentityTable<>(16);
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos SECTION_POS = new BlockPos.MutableBlockPos();

    /** Cross-frame block solidity cache tagged by {@link #terrainStamp}. */
    private static final long[] cellKeys = new long[CELL_SLOTS];
    private static final int[] cellTags = new int[CELL_SLOTS];

    /** Per-frame settled+drawn section cache tagged by {@link #frameStamp} (references cleared in {@link #end()}). */
    private static final long[] sectionKeys = new long[SECTION_SLOTS];
    private static final int[] sectionTags = new int[SECTION_SLOTS];
    private static final LevelChunkSection[] sectionCache = new LevelChunkSection[SECTION_SLOTS];
    private static final int[] touchedSections = new int[SECTION_SLOTS];

    /** Exact entity sweep-decision cache tagged by {@link #sweepStamp}. */
    private static final int[] bodyIds = new int[BODY_SLOTS];
    private static final int[] bodyTags = new int[BODY_SLOTS];
    private static final double[] bodyX0 = new double[BODY_SLOTS];
    private static final double[] bodyY0 = new double[BODY_SLOTS];
    private static final double[] bodyZ0 = new double[BODY_SLOTS];
    private static final double[] bodyX1 = new double[BODY_SLOTS];
    private static final double[] bodyY1 = new double[BODY_SLOTS];
    private static final double[] bodyZ1 = new double[BODY_SLOTS];

    /** Exact block-entity sweep-decision cache tagged by {@link #sweepStamp}. */
    private static final long[] chestKeys = new long[CHEST_SLOTS];
    private static final int[] chestTags = new int[CHEST_SLOTS];

    private static final MethodHandle SHADER_PACK = shaderPackProbe();

    private static boolean enabled;
    private static boolean active;
    private static boolean leashDirty = true;
    private static boolean hooksBuilt;
    private static long leashTick = Long.MIN_VALUE;
    private static ClientLevel level;
    private static ClientLevel linkerLevel;
    private static Entity viewer;
    private static double eyeX, eyeY, eyeZ;
    private static double cacheEyeX = Double.NaN, cacheEyeY = Double.NaN, cacheEyeZ = Double.NaN;
    private static int cacheTerrainStamp = -1, cacheRenderDist = -1;
    private static long cacheResetMs = Long.MIN_VALUE / 2;
    private static long now, window, busyAt = Long.MIN_VALUE / 2;
    private static int frameStamp = 1, terrainStamp = 1, sweepStamp = 1;
    private static int tests, touchedCount;
    private static boolean keepLiving;
    private static LevelChunk lastChunk;
    private static int lastChunkX = Integer.MIN_VALUE, lastChunkZ = Integer.MIN_VALUE;
    private static LevelChunkSection lastSection;
    private static int lastSx = Integer.MIN_VALUE, lastSy = Integer.MIN_VALUE, lastSz = Integer.MIN_VALUE;

    /** Entities and block entities skipped since startup; read by the parity probe. */
    public static long bodiesSkipped, chestsSkipped, framesActive;

    private EntityOcclusion() {}

    /** Called once from the client initializer so no entity or chunk load is missed. */
    public static void initialize() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            boolean isLeashable = entity instanceof Leashable;
            boolean isHook = entity instanceof FishingHook;
            if (!isLeashable && !isHook) return;
            if (world != linkerLevel) {
                LEASHABLES.clear();
                HOOKS.clear();
                LEASH_ENDS.clear();
                HOOK_ENDS.clear();
                linkerLevel = world;
                leashDirty = true;
            }
            if (isLeashable) {
                LEASHABLES.add(entity);
                if (((Leashable) entity).getLeashData() != null) leashDirty = true;
            }
            if (isHook) HOOKS.add(entity);
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof Leashable && LEASHABLES.remove(entity)) leashDirty = true;
            if (entity instanceof FishingHook) HOOKS.remove(entity);
        });
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (enabled && world == level) {
                STABILITY.markColumn(chunk.getPos().x(), chunk.getPos().z(), Util.getMillis(), window);
                bumpTerrainStamp();
            }
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            if (enabled && world == level) bumpTerrainStamp();
        });
    }

    // ---- terrain & leash changes (OcclusionDirtyMixin, OcclusionLeashMixin) ----

    public static void leashesChanged() {
        leashDirty = true;
    }

    public static void blockChanged(ClientLevel world, BlockPos pos) {
        if (!enabled || world != level || !AuroraConfig.get().perfEntityOcclusion) return;
        bumpTerrainStamp();
        markSections((pos.getX() - 1) >> 4, (pos.getY() - 1) >> 4, (pos.getZ() - 1) >> 4,
                (pos.getX() + 1) >> 4, (pos.getY() + 1) >> 4, (pos.getZ() + 1) >> 4);
    }

    public static void sectionsChanged(ClientLevel world, int x0, int y0, int z0, int x1, int y1, int z1) {
        if (!enabled || world != level || !AuroraConfig.get().perfEntityOcclusion) return;
        bumpTerrainStamp();
        markSections(x0, y0, z0, x1, y1, z1);
    }

    private static void bumpTerrainStamp() {
        if (terrainStamp == Integer.MAX_VALUE >> 1) {
            Arrays.fill(cellTags, 0);
            terrainStamp = 1;
        } else {
            terrainStamp++;
        }
    }

    private static void bumpSweepStamp() {
        if (sweepStamp == Integer.MAX_VALUE >> 1) {
            Arrays.fill(bodyTags, 0);
            Arrays.fill(chestTags, 0);
            sweepStamp = 1;
        } else {
            sweepStamp++;
        }
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
            bumpTerrainStamp();
            leashDirty = true;
        }
        if (!mc.isSameThread() || !MixinHealth.applied("OcclusionDirtyMixin") || shaderPackInUse()) return;
        if (!mc.levelRenderer.hasRenderedAllSections()) {
            busyAt = time;
            bumpTerrainStamp();
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
        if (frameStamp == Integer.MAX_VALUE >> 1) {
            Arrays.fill(sectionTags, 0);
            frameStamp = 1;
        } else {
            frameStamp++;
        }
        int renderDist = mc.options.getEffectiveRenderDistance();
        if (eyeX != cacheEyeX || eyeY != cacheEyeY || eyeZ != cacheEyeZ
                || terrainStamp != cacheTerrainStamp || renderDist != cacheRenderDist
                || time - cacheResetMs >= SWEEP_CACHE_TTL_MS) {
            bumpSweepStamp();
            cacheEyeX = eyeX;
            cacheEyeY = eyeY;
            cacheEyeZ = eyeZ;
            cacheTerrainStamp = terrainStamp;
            cacheRenderDist = renderDist;
            cacheResetMs = time;
        }
        if (!MixinHealth.applied("OcclusionLeashMixin")) leashDirty = true;
        tests = 0;
        touchedCount = 0;
        lastChunk = null;
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
        lastSection = null;
        lastSx = Integer.MIN_VALUE;
        lastSy = Integer.MIN_VALUE;
        lastSz = Integer.MIN_VALUE;
        hooksBuilt = false;
        SWEEP.resetProbes();
        framesActive++;
        active = true;
    }

    public static void end() {
        active = false;
        viewer = null;
        lastChunk = null;
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
        lastSection = null;
        lastSx = Integer.MIN_VALUE;
        lastSy = Integer.MIN_VALUE;
        lastSz = Integer.MIN_VALUE;
        for (int i = 0; i < touchedCount; i++) {
            sectionCache[touchedSections[i]] = null;
        }
        touchedCount = 0;
    }

    // ---- decisions (OcclusionBodyMixin, OcclusionChestMixin) ----

    public static boolean hideBody(Entity entity) {
        if (!active || !mayHideFast(entity) || !budget()) return false;
        AABB box = entity.getBoundingBox();
        double width = Math.max(box.getXsize(), box.getZsize());
        // Grow the box by the model's overhang and the shadow, which reaches
        // a shadow radius (at most the box width) beyond and below the feet.
        double side = 1.0 + Math.max(0.5, width * 0.5), below = 1.0 + width;
        double dx = entity.xOld - entity.getX(), dy = entity.yOld - entity.getY(), dz = entity.zOld - entity.getZ();
        double x0 = box.minX + Math.min(0, dx) - side;
        double y0 = box.minY + Math.min(0, dy) - below;
        double z0 = box.minZ + Math.min(0, dz) - side;
        double x1 = box.maxX + Math.max(0, dx) + side;
        double y1 = box.maxY + Math.max(0, dy) + side;
        double z1 = box.maxZ + Math.max(0, dz) + side;
        int id = entity.getId();
        int slot = mix(id) & (BODY_SLOTS - 1);
        int tag = bodyTags[slot];
        boolean hidden;
        if (tag >>> 1 == sweepStamp && bodyIds[slot] == id
                && bodyX0[slot] == x0 && bodyY0[slot] == y0 && bodyZ0[slot] == z0
                && bodyX1[slot] == x1 && bodyY1[slot] == y1 && bodyZ1[slot] == z1) {
            hidden = (tag & 1) != 0;
        } else {
            hidden = SWEEP.hidden(CELLS, eyeX, eyeY, eyeZ, EYE, x0, y0, z0, x1, y1, z1);
            bodyIds[slot] = id;
            bodyTags[slot] = sweepStamp << 1 | (hidden ? 1 : 0);
            bodyX0[slot] = x0;
            bodyY0[slot] = y0;
            bodyZ0[slot] = z0;
            bodyX1[slot] = x1;
            bodyY1[slot] = y1;
            bodyZ1[slot] = z1;
        }
        if (!hidden) return false;
        if (!mayHideSlow(entity)) return false;
        bodiesSkipped++;
        return true;
    }

    public static boolean hideChest(BlockEntity blockEntity) {
        if (!active || blockEntity.getLevel() != level || !OcclusionTerrain.contained(blockEntity.getType()) || !budget())
            return false;
        BlockPos pos = blockEntity.getBlockPos();
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        long key = pos.asLong();
        int slot = mix(key) & (CHEST_SLOTS - 1);
        int tag = chestTags[slot];
        boolean hidden;
        if (tag >>> 1 == sweepStamp && chestKeys[slot] == key) {
            hidden = (tag & 1) != 0;
        } else {
            hidden = SWEEP.hidden(CELLS, eyeX, eyeY, eyeZ, EYE, x - 1.0, y - 1.0, z - 1.0, x + 2.0, y + 2.0, z + 2.0);
            chestKeys[slot] = key;
            chestTags[slot] = sweepStamp << 1 | (hidden ? 1 : 0);
        }
        if (!hidden) return false;
        chestsSkipped++;
        return true;
    }

    private static boolean budget() {
        return ++tests <= FRAME_TESTS && SWEEP.probes() < FRAME_PROBES;
    }

    /** Ultra-cheap invariant exclusions checked before the terrain sweep. */
    private static boolean mayHideFast(Entity entity) {
        if (entity == viewer || entity.level() != level) return false;
        if (keepLiving && entity instanceof LivingEntity) return false;
        if (entity instanceof Player || entity instanceof Avatar || entity instanceof Display
                || entity instanceof FishingHook || entity instanceof LeashFenceKnotEntity
                || entity instanceof LightningBolt || entity instanceof EnderDragon || entity instanceof EndCrystal
                || entity instanceof WitherBoss || entity instanceof Giant || entity instanceof Guardian
                || entity instanceof Shulker || entity instanceof AbstractMinecart) return false;
        if (entity.getRootVehicle() == viewer.getRootVehicle()) return false;
        AABB box = entity.getBoundingBox();
        return Math.max(box.getXsize(), box.getZsize()) <= MAX_WIDTH && box.getYsize() <= MAX_HEIGHT;
    }

    /**
     * Metadata exclusions checked only after {@link OcclusionSweep} confirms
     * opaque terrain separates the camera from the entity, avoiding per-frame
     * glowing/team/leash overhead on visible open-air entities.
     */
    private static boolean mayHideSlow(Entity entity) {
        if (entity.hasCustomName() || entity.shouldShowName() || entity.getTeam() != null
                || Minecraft.getInstance().shouldEntityAppearGlowing(entity)) return false;
        if (entity instanceof Leashable leashable && (leashable.getLeashData() != null || leashable.isLeashed())) return false;
        ensureLeashEnds();
        if (LEASH_ENDS.contains(entity)) return false;
        if (HOOKS.size() > 0) {
            ensureHookEnds();
            if (HOOK_ENDS.contains(entity)) return false;
        }
        return true;
    }

    private static void ensureLeashEnds() {
        long tick = level.getGameTime();
        if (!leashDirty && tick == leashTick && linkerLevel == level) return;
        leashDirty = false;
        leashTick = tick;
        LEASH_ENDS.clear();
        if (linkerLevel != level) {
            LEASHABLES.clear();
            HOOKS.clear();
            HOOK_ENDS.clear();
            linkerLevel = level;
            return;
        }
        for (int i = 0, n = LEASHABLES.capacity(); i < n; i++) {
            Entity linker = LEASHABLES.at(i);
            if (linker == null) continue;
            Leashable leashable = (Leashable) linker;
            if (leashable.getLeashData() != null) {
                Entity holder = leashable.getLeashHolder();
                if (holder != null) LEASH_ENDS.add(holder);
            }
        }
    }

    private static void ensureHookEnds() {
        if (hooksBuilt) return;
        hooksBuilt = true;
        HOOK_ENDS.clear();
        for (int i = 0, n = HOOKS.capacity(); i < n; i++) {
            Entity linker = HOOKS.at(i);
            if (linker instanceof FishingHook hook) {
                if (hook.getHookedIn() != null) HOOK_ENDS.add(hook.getHookedIn());
                if (hook.getOwner() != null) HOOK_ENDS.add(hook.getOwner());
            }
        }
    }

    // ---- terrain the renderer has certainly drawn ----

    private static boolean opaque(int x, int y, int z) {
        if (level.isOutsideBuildHeight(y)) return false;
        int sx = x >> 4, sy = y >> 4, sz = z >> 4;
        LevelChunkSection section;
        if (sx == lastSx && sy == lastSy && sz == lastSz) {
            section = lastSection;
        } else {
            section = settledSection(sx, sy, sz);
            lastSx = sx;
            lastSy = sy;
            lastSz = sz;
            lastSection = section;
        }
        if (section == null) return false;
        long key = BlockPos.asLong(x, y, z);
        int slot = mix(key) & (CELL_SLOTS - 1);
        int tag = cellTags[slot];
        if (cellKeys[slot] == key && tag >>> 1 == terrainStamp) return (tag & 1) != 0;
        boolean opaque = solid(section.getBlockState(x & 15, y & 15, z & 15));
        cellKeys[slot] = key;
        cellTags[slot] = terrainStamp << 1 | (opaque ? 1 : 0);
        return opaque;
    }

    /**
     * Returns the {@link LevelChunkSection} when it is loaded, non-empty,
     * settled, and drawn by the renderer, or {@code null} when the section can
     * contain no occluding blocks (unloaded, all-air, unsettled, or unbuilt).
     */
    private static LevelChunkSection settledSection(int sx, int sy, int sz) {
        long key = SectionStability.key(sx, sy, sz);
        int slot = mix(key) & (SECTION_SLOTS - 1);
        int tag = sectionTags[slot];
        if (sectionKeys[slot] == key && tag >>> 1 == frameStamp) {
            return (tag & 1) != 0 ? sectionCache[slot] : null;
        }
        LevelChunkSection section = resolveSection(sx, sy, sz);
        if (section != null) {
            if (!STABILITY.settled(sx, sy, sz, now, window)
                    || !OcclusionTerrain.drawn(Minecraft.getInstance(), SECTION_POS.set(sx << 4, sy << 4, sz << 4), window - SETTLE_MS)) {
                section = null;
            }
        }
        sectionKeys[slot] = key;
        sectionTags[slot] = frameStamp << 1 | (section != null ? 1 : 0);
        if (section != null) {
            if (sectionCache[slot] == null && touchedCount < SECTION_SLOTS) {
                touchedSections[touchedCount++] = slot;
            }
            sectionCache[slot] = section;
        } else {
            sectionCache[slot] = null;
        }
        return section;
    }

    private static LevelChunkSection resolveSection(int sx, int sy, int sz) {
        LevelChunk chunk;
        if (sx == lastChunkX && sz == lastChunkZ) {
            chunk = lastChunk;
        } else {
            chunk = level.getChunkSource().getChunkNow(sx, sz);
            lastChunkX = sx;
            lastChunkZ = sz;
            lastChunk = chunk;
        }
        if (chunk == null) return null;
        int idx = level.getSectionIndexFromSectionY(sy);
        LevelChunkSection[] sections = chunk.getSections();
        if (idx < 0 || idx >= sections.length) return null;
        LevelChunkSection section = sections[idx];
        return (section == null || section.hasOnlyAir()) ? null : section;
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
