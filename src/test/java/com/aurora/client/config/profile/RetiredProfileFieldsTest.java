package com.aurora.client.config.profile;

import com.aurora.client.config.AuroraConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A retired name is skipped quietly on restore, so it must never shadow a live field. */
class RetiredProfileFieldsTest {

    @Test
    void retiredNamesAreNotLiveConfigFields() {
        Set<String> live = new HashSet<>();
        for (Field f : AuroraConfig.class.getFields())
            if (!Modifier.isStatic(f.getModifiers())) live.add(f.getName());
        for (String name : ProfileFieldSet.RETIRED)
            assertFalse(live.contains(name), name + " is retired but still an AuroraConfig field");
    }

    @Test
    void removedSmoothingAndHitregFieldsAreRetired() {
        assertTrue(ProfileFieldSet.isRetired("entityMovementSmoothingEnabled"));
        assertTrue(ProfileFieldSet.isRetired("hitregSettingsKey"));
        assertFalse(ProfileFieldSet.isRetired("hitregDelayMs"));
    }
}
