package com.aurora.client.hitreg.util;

import net.minecraft.world.damagesource.DamageSource;

/**
 * From BetterHitreg by Jass (modrinth.com/mod/betterhitreg), integrated into
 * Aurora with the author's permission. Logic is upstream's, moved verbatim —
 * see {@link com.aurora.client.hitreg.BetterHitreg} for the integration notes.
 */
public class DontAnimate extends DamageSource {
    public final DamageSource wrapped;

    public DontAnimate(DamageSource wrapped) {
        super(wrapped.typeHolder());
        this.wrapped = wrapped;
    }
}