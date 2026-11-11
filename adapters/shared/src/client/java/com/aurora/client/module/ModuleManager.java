package com.aurora.client.module;

import com.aurora.client.feature.FeatureCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * The Modules grid's cards. Derived from {@link FeatureCatalog}, the one
 * list of features, in its order: there is nothing to keep in sync here.
 */
public class ModuleManager {
    private static final ModuleManager INSTANCE = new ModuleManager();
    public static ModuleManager getInstance() {
        return INSTANCE;
    }

    private final List<Module> modules = new ArrayList<>();

    private ModuleManager() {
        for (FeatureCatalog.Feature f : FeatureCatalog.featuresOn(FeatureCatalog.Tab.MODULES)) {
            modules.add(new Module(f.id(), f.name(), f.summary()));
        }
    }

    public List<Module> getModules() {
        return modules;
    }
}
