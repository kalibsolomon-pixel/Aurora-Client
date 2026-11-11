package com.aurora.client.health;

import com.aurora.client.feature.FeatureCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Settles the state of every Aurora mixin once, at the end of startup
 * (ADR 0004).
 *
 * <p>Mixins apply when their target class first loads, and some targets do
 * not load until a world is joined. Without this pass a broken mixin would
 * only be discovered mid-game. The audit loads every target class (without
 * initializing it), then marks any mixin that neither applied nor failed as
 * {@link MixinHealth.Cause#NOT_APPLIED}, which also covers a target class
 * that no longer exists on this Minecraft version.
 *
 * <p>A wrong verdict corrects itself: if a mixin applies later after all,
 * {@link MixinHealth} drops the entry and {@link FeatureHealth} gives the
 * feature back.
 */
public final class MixinAudit {
    private MixinAudit() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("aurora/health");

    /** Run once from the client-started event, on the client thread. */
    public static void run(ClassLoader loader) {
        for (String target : MixinHealth.targets()) {
            try {
                Class.forName(target, false, loader);
            } catch (Throwable t) {
                // Not ours to report here: if the class is genuinely broken
                // the game fails on it at its normal load point, as before.
                LOGGER.debug("Audit could not load {}: {}", target, t.toString());
            }
        }
        for (FeatureCatalog.MixinBinding binding : FeatureCatalog.mixins()) {
            String name = binding.mixin();
            if (MixinHealth.applied(name) || MixinHealth.failed(name)) continue;
            MixinHealth.noteFailure(name, null, MixinHealth.Cause.NOT_APPLIED,
                    "its target class did not load or does not exist on this version");
        }
        MixinHealth.markAudited();

        var failures = MixinHealth.failures();
        if (failures.isEmpty()) {
            LOGGER.info("All {} Aurora mixins applied.", FeatureCatalog.mixins().size());
            return;
        }
        LOGGER.warn("{} of {} Aurora mixins are not working; the game continues without them:",
                failures.size(), FeatureCatalog.mixins().size());
        for (MixinHealth.Failure f : failures) {
            LOGGER.warn("  {} {}{}", f.mixin(), f.cause().phrase, f.detail().isEmpty() ? "" : " - " + f.detail());
        }
        for (String feature : FeatureHealth.affectedFeatures()) {
            LOGGER.warn("  {}: {}", feature, FeatureHealth.noteFor(feature));
        }
    }
}
