import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/**
 * Standalone reproduction of the aurora.json save failure (no Minecraft, no Aurora).
 * Runs only inside a fresh directory under build/. Modes:
 *   loop  N delayMs  - one thread: writeString(tmp) + move(tmp -> target, REPLACE, ATOMIC), the
 *                      exact AuroraConfig.writeJsonAtomic sequence; counts failures by exception type.
 *   held             - deterministic: hold an open read handle on the target, then attempt the move.
 *   heldtmp          - deterministic: hold an open handle on the TEMP file, then attempt the move.
 */
public final class ReplaceProbe {
    public static void main(String[] a) throws Exception {
        Path dir = Path.of(a[0]);
        if (!dir.toAbsolutePath().toString().replace('\\', '/').contains("/build/")) throw new IllegalStateException("build/ only");
        Files.createDirectories(dir);
        Path target = dir.resolve("aurora.json"), tmp = dir.resolve("aurora.json.tmp");
        String json = "{\n" + "  \"x\": 1,\n".repeat(400) + "  \"end\": true\n}\n";
        Files.writeString(target, json);
        switch (a[1]) {
            case "loop" -> {
                int n = Integer.parseInt(a[2]); long delay = Long.parseLong(a[3]);
                Map<String, Integer> failures = new TreeMap<>();
                long worst = 0;
                for (int i = 0; i < n; i++) {
                    long t0 = System.nanoTime();
                    try {
                        Files.writeString(tmp, json + "//" + i);
                        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException e) {
                        failures.merge(e.getClass().getSimpleName() + ": " + e.getMessage().replace(dir.toString(), "<dir>"), 1, Integer::sum);
                        Files.deleteIfExists(tmp);
                    }
                    worst = Math.max(worst, System.nanoTime() - t0);
                    if (delay > 0) Thread.sleep(delay);
                }
                System.out.println("iterations=" + n + " delayMs=" + delay + " failures=" + failures.values().stream().mapToInt(x -> x).sum()
                        + " worstMs=" + worst / 1e6);
                failures.forEach((k, v) -> System.out.println("  " + v + " x " + k));
                System.out.println("finalTargetIntact=" + Files.readString(target).startsWith("{"));
            }
            case "held", "heldtmp" -> {
                Files.writeString(tmp, json + "//new");
                Path held = a[1].equals("held") ? target : tmp;
                // Java opens with FILE_SHARE_READ|WRITE|DELETE: the most permissive sharing a reader can ask for.
                try (FileChannel ch = FileChannel.open(held, StandardOpenOption.READ)) {
                    try {
                        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                        System.out.println(a[1] + ": move SUCCEEDED while handle open");
                    } catch (IOException e) {
                        System.out.println(a[1] + ": " + e.getClass().getSimpleName() + ": " + e.getMessage().replace(dir.toString(), "<dir>"));
                    }
                }
                System.out.println("targetStillOld=" + !Files.readString(target).endsWith("//new") + " tmpExists=" + Files.exists(tmp));
            }
            default -> throw new IllegalArgumentException(a[1]);
        }
    }
}
