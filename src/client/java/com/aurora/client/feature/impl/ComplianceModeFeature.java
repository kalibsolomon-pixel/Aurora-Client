package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import com.aurora.client.hud.AlertManager;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Anti-cheat compliance mode: when enabled and the player connects to a
 * known-strict server, automatically disables Aurora features that server
 * anti-cheats may flag.
 *
 * <p>Detection is address-substring based:
 * <ul>
 *   <li>Built-in list of known strict networks (Hypixel, Mineplex, etc.).</li>
 *   <li>User-defined {@link AuroraConfig#complianceStrictServers} list
 *       (treated as strict).</li>
 *   <li>User-defined {@link AuroraConfig#complianceSafeServers} list
 *       (overrides strict detection — always safe).</li>
 * </ul>
 *
 * <p>When compliance is active, the following are force-disabled by
 * restoring their config booleans to {@code false}:
 * reach display, toggle sprint/sneak, hitbox, keystrokes, and particle
 * visibility changes. These are the features most commonly flagged by
 * movement/cheat detection systems.
 *
 * <p>On disconnect or joining a safe server, features return to the
 * user's configured state on the next profile reload or toggle.
 */
public class ComplianceModeFeature implements Feature {
    public static final String ID = "compliance_mode";

    private static ComplianceModeFeature instance;

    private boolean wasComplianceActive = false;
    private boolean savedReach;
    private boolean savedToggleSprint;
    private boolean savedHitbox;
    private boolean savedHitboxTarget;
    private boolean savedKeystrokes;
    private boolean savedParticles;

    /**
     * Built-in list of server address substrings known to run strict
     * anti-cheat. Conservative — only networks with documented history of
     * flagging common client QoL features.
     */
    private static final List<String> BUILTIN_STRICT = List.of(
            "hypixel",
            "mineplex",
            "cubecraft",
            "minemen.club",
            "mmc",
            "redesky.com",
            "gommehd",
            "timolia",
            "rewinside",
            "mcpvp.club",
            "mcpvp.com",
            "pvphq.com",
            "pvplegacy.net",
            "catpvp.xyz",
            "stray.gg"
    );

    @Override public String id() { return ID; }

    @Override
    public void onRegister() {
        instance = this;
    }

    @Override
    public void onTick(Minecraft client) {
        if (client == null) return;
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.complianceModeEnabled) {
            if (wasComplianceActive) {
                restoreFeatures(cfg);
                wasComplianceActive = false;
            }
            return;
        }

        boolean shouldComply = shouldComply(client);

        if (shouldComply && !wasComplianceActive) {
            saveAndDisable(cfg);
            wasComplianceActive = true;
            if (cfg.complianceModeToast) {
                AlertManager.fire("COMPLIANCE MODE",
                        "Strict features disabled for this server",
                        null, com.aurora.client.theme.HudStatus.ALERT_CAUTION,
                        AlertManager.Priority.INFO, 4_000L);
            }
        } else if (!shouldComply && wasComplianceActive) {
            restoreFeatures(cfg);
            wasComplianceActive = false;
            if (cfg.complianceModeToast) {
                AlertManager.fire("COMPLIANCE MODE",
                        "All features restored",
                        null, com.aurora.client.theme.HudStatus.RESTORED,
                        AlertManager.Priority.INFO, 4_000L);
            }
        }
    }

    private boolean shouldComply(Minecraft client) {
        String addr = getServerAddress(client);
        if (addr == null || addr.isEmpty()) return false;

        AuroraConfig cfg = AuroraConfig.get();
        String lower = addr.toLowerCase();

        for (String safe : cfg.complianceSafeServers) {
            if (safe != null && !safe.isBlank() && lower.contains(safe.toLowerCase())) {
                return false;
            }
        }

        for (String strict : cfg.complianceStrictServers) {
            if (strict != null && !strict.isBlank() && lower.contains(strict.toLowerCase())) {
                return true;
            }
        }

        for (String strict : BUILTIN_STRICT) {
            if (lower.contains(strict)) return true;
        }

        return false;
    }

    private String getServerAddress(Minecraft client) {
        if (client.getSingleplayerServer() != null) return null;
        if (client.getCurrentServer() != null) {
            return client.getCurrentServer().ip;
        }
        return null;
    }

    private void saveAndDisable(AuroraConfig cfg) {
        savedReach = cfg.reachEnabled;
        savedToggleSprint = cfg.toggleSprintSneakEnabled;
        // Save both hitbox sub-toggles — these are the fields the renderer
        // and EntityRenderDispatcherMixin actually read. The legacy
        // hitboxFeatureEnabled field is an orphan no renderer consults.
        savedHitbox = cfg.hitboxEnabled;
        savedHitboxTarget = cfg.hitboxTargetEnabled;
        savedKeystrokes = cfg.keystrokesEnabled;
        savedParticles = cfg.particleControlsEnabled;

        cfg.reachEnabled = false;
        cfg.toggleSprintSneakEnabled = false;
        cfg.hitboxEnabled = false;
        cfg.hitboxTargetEnabled = false;
        cfg.keystrokesEnabled = false;
        cfg.particleControlsEnabled = false;
    }

    private void restoreFeatures(AuroraConfig cfg) {
        cfg.reachEnabled = savedReach;
        cfg.toggleSprintSneakEnabled = savedToggleSprint;
        cfg.hitboxEnabled = savedHitbox;
        cfg.hitboxTargetEnabled = savedHitboxTarget;
        cfg.keystrokesEnabled = savedKeystrokes;
        cfg.particleControlsEnabled = savedParticles;
    }

    public static ComplianceModeFeature get() { return instance; }
    public boolean isActive() { return wasComplianceActive; }
}