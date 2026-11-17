package com.aurora.client.adapter;

import com.aurora.client.ui.text.SdfTextSupport;

/** Version-owned opt-in: only 26.3 has the SDF provider and pipeline. */
public final class NativeSdfText {
    private NativeSdfText() {}
    public static boolean enabled() { return SdfTextSupport.enabled(); }
}
