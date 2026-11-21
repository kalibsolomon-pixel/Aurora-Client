package com.aurora.client.ui.text;

import com.aurora.client.health.HealthTestAccess;
import com.aurora.client.ui.util.ShaderLoad;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RRLS lets screens draw during the first reload, so the first SDF text lookup can come before the Shader Loader
 * has applied, when only the vanilla startup shaders exist. That is not a shader failure (ADR 0010).
 */
class SdfPipelineCheckTest {
    private final AtomicInteger compiles = new AtomicInteger();

    @BeforeEach @AfterEach void reset() {
        ShaderLoad.resetForTests();
        SdfTextSupport.resetForTests();
        HealthTestAccess.reset(null);
        compiles.set(0);
    }

    @Test void waitsWithoutCompilingOrFailingBeforeTheShaderLoaderApplied() {
        for (int look = 0; look < 5; look++) {
            assertFalse(SdfTextSupport.checkPipeline(() -> { compiles.incrementAndGet(); return false; }));
        }
        assertEquals(0, compiles.get(), "a compile before the first apply would run against the startup shaders");
        assertTrue(SdfTextSupport.available(), "waiting must not turn SDF text off for the session");
    }

    @Test void compilesOnceTheShaderLoaderHasApplied() {
        assertFalse(SdfTextSupport.checkPipeline(() -> { compiles.incrementAndGet(); return true; }));
        ShaderLoad.markApplied();
        assertTrue(SdfTextSupport.checkPipeline(() -> { compiles.incrementAndGet(); return true; }));
        assertEquals(1, compiles.get());
        assertTrue(SdfTextSupport.available());
    }

    @Test void aCompileThatFailsAfterTheApplyIsARealFailure() {
        ShaderLoad.markApplied();
        assertFalse(SdfTextSupport.checkPipeline(() -> false));
        assertFalse(SdfTextSupport.available(), "raster text is the fallback for the session");
    }

    @Test void aCompileThatThrowsAfterTheApplyIsARealFailure() {
        ShaderLoad.markApplied();
        assertFalse(SdfTextSupport.checkPipeline(() -> { throw new IllegalStateException("link failed"); }));
        assertFalse(SdfTextSupport.available());
    }

    @Test void aLaterReloadKeepsTheShaderLoaderSettled() {
        ShaderLoad.markApplied();
        assertTrue(SdfTextSupport.checkPipeline(() -> true));
        // A resource reload re-checks (the font reload invalidates the pipeline); it must not wait again.
        assertTrue(SdfTextSupport.checkPipeline(() -> true));
        assertTrue(ShaderLoad.settled());
    }

    @Test void aBrokenAppliedHookDecidesAsBeforeTheHookExisted() {
        HealthTestAccess.fail("ShaderManagerAppliedMixin");
        assertTrue(ShaderLoad.settled(), "without the hook there is nothing to wait for");
        assertTrue(SdfTextSupport.checkPipeline(() -> { compiles.incrementAndGet(); return true; }));
        assertEquals(1, compiles.get());
    }
}
