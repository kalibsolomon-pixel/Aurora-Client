package com.aurora.client;

import com.aurora.client.util.ItemPhysicsSmoothing;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the four follow-up fixes from the 2026-11-20 performance pass:
 * Item Physics temporal smoothing, block-respecting crosshair/target pick,
 * occlusion-matched hitbox visibility, and RRLS title-screen EntityModelSet safety.
 */
class PerfFollowupsTest {

    @Test
    void itemPhysicsSmoothingFirstSightMatchesInitialGroundState() {
        var airborne = ItemPhysicsSmoothing.advance(false, 0f, false, false, 16_000_000L);
        assertEquals(0f, airborne.groundT(), 1e-6f);
        assertFalse(airborne.settled());

        var resting = ItemPhysicsSmoothing.advance(false, 0f, false, true, 16_000_000L);
        assertEquals(1f, resting.groundT(), 1e-6f);
        assertTrue(resting.settled());
    }

    @Test
    void itemPhysicsSmoothingIsFrameRateIndependent() {
        // 60 FPS (6 steps of 20 ms = 120 ms) vs 2,400 FPS (288 steps of 416.67 us = 120 ms)
        ItemPhysicsSmoothing.Step slow = ItemPhysicsSmoothing.advance(false, 0f, false, false, 0L);
        ItemPhysicsSmoothing.Step fast = ItemPhysicsSmoothing.advance(false, 0f, false, false, 0L);

        // Advance halfway (60 ms)
        for (int i = 0; i < 3; i++) {
            slow = ItemPhysicsSmoothing.advance(true, slow.groundT(), slow.settled(), true, 20_000_000L);
        }
        for (int i = 0; i < 144; i++) {
            fast = ItemPhysicsSmoothing.advance(true, fast.groundT(), fast.settled(), true, 416_667L);
        }
        assertEquals(0.5f, slow.groundT(), 0.01f);
        assertEquals(0.5f, fast.groundT(), 0.01f);
        assertFalse(slow.settled());
        assertFalse(fast.settled());

        // Complete the remaining 60 ms -> settles to 1.0 and latches settled
        for (int i = 0; i < 3; i++) {
            slow = ItemPhysicsSmoothing.advance(true, slow.groundT(), slow.settled(), true, 20_000_000L);
        }
        for (int i = 0; i < 144; i++) {
            fast = ItemPhysicsSmoothing.advance(true, fast.groundT(), fast.settled(), true, 416_667L);
        }
        assertEquals(1f, slow.groundT(), 1e-6f);
        assertEquals(1f, fast.groundT(), 1e-6f);
        assertTrue(slow.settled());
        assertTrue(fast.settled());
    }

    @Test
    void itemPhysicsSmoothingSnapsLiftoffButKeepsSettledLatch() {
        var settled = new ItemPhysicsSmoothing.Step(1f, true);
        var falling = ItemPhysicsSmoothing.advance(true, settled.groundT(), settled.settled(), false, 16_000_000L);
        assertEquals(0f, falling.groundT(), 1e-6f, "liftoff snaps groundT to 0 immediately");
        assertTrue(falling.settled(), "settled latch persists after block beneath item breaks");
    }

    @Test
    void itemPhysicsSmoothingClampsZeroAndHugeDeltas() {
        var sameInstant = ItemPhysicsSmoothing.advance(true, 0.25f, false, true, 0L);
        assertEquals(0.25f, sameInstant.groundT(), 1e-6f);

        var negativeDt = ItemPhysicsSmoothing.advance(true, 0.25f, false, true, -5_000_000L);
        assertEquals(0.25f, negativeDt.groundT(), 1e-6f);

        var hugeSpike = ItemPhysicsSmoothing.advance(true, 0f, false, true, 5_000_000_000L);
        assertTrue(hugeSpike.groundT() < 1f, "lag spike clamped by MAX_DT_NANOS");
        assertFalse(hugeSpike.settled());
    }

    @Test
    void itemPhysicsTracksByEntityAndClearsOnWorldJoin() {
        String snaps = SourceTree.read("util/AuroraItemPhysicsSnapshots.java");
        assertTrue(snaps.contains("Map<ItemEntity, Track> TRACKS"));
        assertTrue(snaps.contains("ItemPhysicsSmoothing.advance("));
        String join = SourceTree.read("mixin/ClientPlayNetworkHandlerWorldJoinMixin.java");
        assertTrue(join.contains("AuroraItemPhysicsSnapshots.clearTracks();"));
    }

    @Test
    void crosshairAndHitboxTargetUseVanillaBlockClippedPick() throws IOException {
        String crosshair = SourceTree.read("hud/CrosshairRenderer.java");
        assertFalse(crosshair.contains("ProjectileUtil.getEntityHitResult"),
                "CrosshairRenderer must not raycast through walls");
        assertTrue(crosshair.contains("client.crosshairPickEntity"));

        for (String line : new String[]{"26.1", "26.2", "26.3"}) {
            String hitbox = Files.readString(Path.of("adapters/minecraft-" + line
                    + "/src/client/java/com/aurora/client/hud/HitboxRenderer.java")).replace("\r\n", "\n");
            assertFalse(hitbox.contains("ProjectileUtil.getEntityHitResult"),
                    line + " HitboxRenderer must not raycast through walls");
            assertTrue(hitbox.contains("client.crosshairPickEntity"), line);
            assertTrue(hitbox.contains("HitboxVisibleEntities.hasSnapshot()"), line);
        }
    }

    @Test
    void hitboxVisibleEntitiesMixinHooksAfterEntityCullingOnEveryLine() throws IOException {
        for (String line : new String[]{"26.1", "26.2", "26.3"}) {
            String mixin = Files.readString(Path.of("adapters/minecraft-" + line
                    + "/src/client/java/com/aurora/client/mixin/HitboxVisibleEntitiesMixin.java")).replace("\r\n", "\n");
            assertTrue(mixin.contains("method = \"extractVisibleEntities\""), line);
            assertTrue(mixin.contains("EntityRenderDispatcher;extractEntity("), line
                    + " must hook INVOKE EntityRenderDispatcher.extractEntity so EntityCulling HEAD cancels skip it");
        }
        String join = SourceTree.read("mixin/ClientPlayNetworkHandlerWorldJoinMixin.java");
        assertTrue(join.contains("HitboxVisibleEntities.clear();"));
    }

    @Test
    void titleScreenWaitsForEntityModelSetUnderRrls() {
        String screen = SourceTree.read("screen/AuroraTitleScreen.java");
        assertTrue(screen.contains("models != EntityModelSet.EMPTY"));
        assertTrue(screen.contains("catch (IllegalArgumentException missingModelLayer)"));
        assertTrue(screen.contains("models != failedModels"));
    }
}
