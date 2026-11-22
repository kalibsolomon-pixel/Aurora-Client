package com.aurora.client.media;

/**
 * Cross-platform OS media control actions triggered by Aurora keybinds or UI buttons.
 */
public enum MediaAction {
    PLAY_PAUSE("Play / Pause", 0xB3),
    NEXT("Next Track", 0xB0),
    PREVIOUS("Previous Track", 0xB1),
    VOLUME_UP("Volume Up", 0xAF),
    VOLUME_DOWN("Volume Down", 0xAE);

    private final String label;
    private final int windowsVk;

    MediaAction(String label, int windowsVk) {
        this.label = label;
        this.windowsVk = windowsVk;
    }

    public String label() { return label; }

    /** Win32 Virtual-Key code for {@code user32.dll keybd_event}. */
    public int windowsVk() { return windowsVk; }
}
