package parityprobe;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.InactivityFpsLimit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Standalone observer: deliberately has no Aurora dependency, for absent/installed/candidate A/B. */
public final class FpsObserver implements ClientModInitializer {
    @Override public void onInitializeClient() {
        Path root = Path.of(System.getProperty("aurora.parity.probe"));
        if (!Files.isRegularFile(root.resolve("DISPOSABLE.txt"))) throw new IllegalStateException("Disposable fixture required");
        var readings = new ArrayList<Integer>();
        long[] joined = {0}, next = {0};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null || client.isPaused()) return;
            long now = System.nanoTime();
            if (joined[0] == 0) {
                joined[0] = now;
                next[0] = now + 20_000_000_000L;
                client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
                client.options.framerateLimit().set(260);
                client.options.enableVsync().set(false);
                return;
            }
            if (now < next[0]) return;
            next[0] += 1_000_000_000L;
            readings.add(client.getFps());
            if (readings.size() != 20) return;
            try {
                Files.writeString(root.resolve("fps-observer.json"), new com.google.gson.Gson().toJson(readings) + "\n");
            } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
            client.stop();
        });
    }
}
