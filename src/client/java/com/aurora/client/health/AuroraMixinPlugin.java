package com.aurora.client.health;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Config plugin for {@code aurora.mixins.json} (ADR 0004).
 *
 * <p>The config is {@code required: false}, so a mixin that cannot apply is
 * skipped instead of stopping the game. This plugin makes that visible: it
 * registers {@link AuroraMixinErrorHandler} to record every failure, notes
 * each mixin that did apply, and honours {@code -Daurora.mixin.disable} so a
 * player or tester can switch a conflicting mixin off by name.
 *
 * <p>It lives outside {@code com.aurora.client.mixin} on purpose: classes in
 * a mixin package cannot be loaded as ordinary classes.
 */
public final class AuroraMixinPlugin implements IMixinConfigPlugin {
    public static final String MIXIN_PACKAGE = MixinHealth.MIXIN_PACKAGE;

    public static final String DISABLE_PROPERTY = MixinHealth.DISABLE_PROPERTY;

    private static final Logger LOGGER = LoggerFactory.getLogger("aurora/health");

    private final Set<String> disabled = new HashSet<>();

    @Override
    public void onLoad(String mixinPackage) {
        String raw = System.getProperty(DISABLE_PROPERTY, "");
        for (String name : raw.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) disabled.add(trimmed);
        }
        if (!disabled.isEmpty()) LOGGER.warn("Mixins turned off by -D{}: {}", DISABLE_PROPERTY, disabled);
        Mixins.registerErrorHandlerClass(AuroraMixinErrorHandler.class.getName());
    }

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String name = MixinHealth.shortName(mixinClassName);
        if (!disabled.contains(name)) return true;
        MixinHealth.noteFailure(name, targetClassName, MixinHealth.Cause.DISABLED_BY_OPTION, "");
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        MixinHealth.addTargets(myTargets);
    }

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        MixinHealth.noteApplied(MixinHealth.shortName(mixinClassName));
    }
}
