package com.aurora.client.launcher;

/** Test-only process used by the launcher's explicit Java/Rust interoperability check. */
public final class LauncherHistoryContractHarness {
    private LauncherHistoryContractHarness() {}

    public static void main(String[] ignored) throws Exception {
        var bootstrap = BridgeBootstrap.fromEnvironment(System.getenv());
        if (bootstrap == null || bootstrap.protocol != 2) throw new IllegalStateException("v2 bootstrap required");
        try (var bridge = new LauncherActivityBridge(bootstrap, message -> {})) {
            Thread.sleep(400);
            bridge.publish(ActivitySnapshot.project(true, true, "Display World", null, null, "stable-save", null));
            Thread.sleep(400);
            bridge.publish(ActivitySnapshot.mainMenu());
            Thread.sleep(400);
            bridge.publish(ActivitySnapshot.project(true, false, null, "Display Server", "example.invalid", null, "EXAMPLE.invalid"));
            Thread.sleep(800);
        }
    }
}
