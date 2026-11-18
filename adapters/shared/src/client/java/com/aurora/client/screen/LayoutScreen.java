package com.aurora.client.screen;

import com.aurora.client.ui.layout.ScreenLayout;

public interface LayoutScreen {
    ScreenLayout contentLayout();
    double layoutScroll();
    default ScreenLayout fixedLayout() { return null; }
}
