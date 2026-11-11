package com.aurora.client.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Records every Aurora mixin that Mixin could not prepare or apply, and lets
 * the game continue (ADR 0004). Errors from any other mod's mixins pass
 * through untouched: this handler is global, and deciding for someone else's
 * mixin is not Aurora's call.
 *
 * <p>Mixin instantiates this class by name; it needs the public no-argument
 * constructor.
 */
public final class AuroraMixinErrorHandler implements IMixinErrorHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("aurora/health");

    public AuroraMixinErrorHandler() {}

    @Override
    public ErrorAction onPrepareError(IMixinConfig config, Throwable th, IMixinInfo mixin, ErrorAction action) {
        return record(null, th, mixin, action);
    }

    @Override
    public ErrorAction onApplyError(String targetClassName, Throwable th, IMixinInfo mixin, ErrorAction action) {
        return record(targetClassName, th, mixin, action);
    }

    private static ErrorAction record(String target, Throwable th, IMixinInfo mixin, ErrorAction action) {
        if (mixin == null) return action;
        String className = mixin.getClassName();
        if (className == null || !className.startsWith(AuroraMixinPlugin.MIXIN_PACKAGE + ".")) return action;
        String name = MixinHealth.shortName(className);
        String detail = MixinHealth.summarize(th);
        MixinHealth.noteFailure(name, target, MixinHealth.Cause.APPLY_FAILED, detail);
        LOGGER.error("Mixin {} could not be applied{}; the features that need it are off for this session. {}",
                name, target == null ? "" : " to " + target, detail);
        return ErrorAction.WARN;
    }
}
