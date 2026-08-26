package com.aurora.client.feature;

import com.aurora.client.AuroraClient;
import com.aurora.client.feature.impl.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FeatureManager {
    private final Map<String, Feature> features = new LinkedHashMap<>();

    public void registerAll() {
        register(new ZoomFeature());
        register(new FullBrightFeature());
        register(new FpsDisplayFeature());
        register(new AutoSprintFeature());
        register(new ClickTrackerFeature());
        register(new ReachTrackerFeature());
        register(new HudEditorFeature());
        register(new FreeLookFeature());
        register(new ToggleSprintFeature());
        register(new BlockOverlayFeature());
        register(new ArmorAlertFeature());
        register(new HitboxFeature());
        register(new CameraSmoothFeature());
        register(new LowLatencyFeature());
        register(new PlaytimeFeature());
        register(new TotemPopFeature());
        register(new WaypointFeature());
        register(new MinimapFeature());
        register(new WorldMapFeature());

        register(new ContainerPreviewFeature());
        register(new ItemPhysicsFeature());
        register(new ParticleControlFeature());
        register(new ShieldStatusFeature());
        register(new StatsTrackerFeature());
        register(new ThemeFeature());
        register(new StatusAlertFeature());
        register(new ComplianceModeFeature());
        register(new AccessibilityFeature());
        register(new TickSyncFeature());
        for (Feature f : features.values()) {
            try {
                f.onRegister();
            } catch (Exception e) {
                AuroraClient.LOGGER.error("Feature {} failed to register", f.id(), e);
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (Feature f : features.values()) {
                try {
                    f.onTick(client);
                } catch (Exception e) {
                    AuroraClient.LOGGER.error("Feature {} crashed on tick", f.id(), e);
                }
            }
        });
    }

    public void register(Feature f) {
        features.put(f.id(), f);
    }

    @SuppressWarnings("unchecked")
    public <T extends Feature> T get(String id) {
        return (T) features.get(id);
    }

    public List<Feature> all() {
        return Collections.unmodifiableList(new ArrayList<>(features.values()));
    }
}


