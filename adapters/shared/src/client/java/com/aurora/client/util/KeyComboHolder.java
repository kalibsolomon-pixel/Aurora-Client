package com.aurora.client.util;

/**
 * The Ctrl / Shift / Alt a vanilla {@code KeyMapping} needs with its key
 * ({@link KeyCombo} bits), added by {@code KeyMappingComboMixin}. The mixin
 * may be absent: reach it through {@code instanceof}, never a cast.
 */
public interface KeyComboHolder {
    int aurora$modifiers();

    void aurora$setModifiers(int modifiers);
}
