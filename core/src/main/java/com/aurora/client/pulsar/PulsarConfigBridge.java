package com.aurora.client.pulsar;

import com.aurora.client.config.AuroraConfig;
import dev.phenomena.config.Config;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Keeps the upstream measurement configuration inside Aurora's profile schema. */
public final class PulsarConfigBridge {
    private static final Field[] FIELDS = java.util.Arrays.stream(Config.class.getFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers())).toArray(Field[]::new);
    private PulsarConfigBridge() {}

    /** A profile/reset replaces the object; ordinary ticks do no copying. */
    public static void reload(Config target) {
        AuroraConfig aurora = AuroraConfig.get();
        if (aurora.pulsarSettings == target) return;
        Config source = validatedOrDefault(aurora.pulsarSettings);
        try {
            for (Field field : FIELDS) field.set(target, field.get(source));
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot apply Pulsar settings", error);
        }
        aurora.pulsarSettings = target;
    }

    public static void save(Config config) {
        config.validate();
        AuroraConfig.get().pulsarSettings = config;
        AuroraConfig.save();
    }

    public static void reset() {
        AuroraConfig.resetByPrefix("pulsar");
        AuroraConfig.save();
    }

    /** A future or damaged nested schema must not prevent Aurora/profile startup. */
    public static Config validatedOrDefault(Config source) {
        if (source == null) return new Config();
        try {
            source.validate();
            return source;
        } catch (RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger("Aurora").warn("Invalid Pulsar settings; using defaults", error);
            return new Config();
        }
    }
}
