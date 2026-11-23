package com.aurora.client.media;

import com.aurora.client.AuroraClient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cross-platform OS media control dispatcher and Windows-first GSMTC Now Playing poller.
 *
 * <p>All OS process I/O runs exclusively off the render/tick thread on a single
 * daemon worker. In factory defaults (feature off), zero threads or processes are spawned.
 */
public final class SystemMediaBridge {

    private static final long POLL_INTERVAL_MS = 2_000L;
    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean WINDOWS = OS.contains("win");
    private static final boolean MAC = OS.contains("mac");

    private static final AtomicReference<MediaTrackState> STATE = new AtomicReference<>(MediaTrackState.EMPTY);
    private static final AtomicBoolean POLL_IN_FLIGHT = new AtomicBoolean(false);

    private static volatile ExecutorService executor;
    private static volatile long lastPollRequestMs = 0L;

    // Reusable persistent Windows helper (only spawned on demand on Windows), guarded by
    // HELPER rather than the class lock: worker() runs on the tick thread and must never
    // wait behind a helper read.
    private static final Object HELPER = new Object();
    private static volatile Process winProcess;
    private static BufferedWriter winIn;
    private static BlockingQueue<String> winLines;
    private static long winBackoffUntilMs = 0L;

    private SystemMediaBridge() {}

    public static boolean isWindows() { return WINDOWS; }

    /** Latest polled desktop media track state (never null). */
    public static MediaTrackState currentTrack() { return STATE.get(); }

    /** Dispatches an OS media control action asynchronously off the tick thread. */
    public static void dispatch(MediaAction action) {
        if (action == null) return;
        worker().execute(() -> {
            try {
                if (WINDOWS) {
                    dispatchWindows(action);
                } else if (MAC) {
                    dispatchMac(action);
                } else {
                    dispatchLinux(action);
                }
            } catch (Throwable t) {
                AuroraClient.LOGGER.warn("[Media] failed to dispatch {}: {}", action, t.getMessage());
            }
        });
    }

    /**
     * Lightweight tick-thread heartbeat. Schedules an async Windows GSMTC poll at most
     * once every {@value #POLL_INTERVAL_MS} ms while {@code hudEnabled} is true; shuts down
     * the helper cleanly when disabled.
     */
    public static void tickPoll(boolean hudEnabled) {
        if (!WINDOWS) return;
        if (!hudEnabled) {
            if (winProcess != null || STATE.get().active()) {
                STATE.set(MediaTrackState.EMPTY);
                shutdownHelperAsync();
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPollRequestMs < POLL_INTERVAL_MS) return;
        if (!POLL_IN_FLIGHT.compareAndSet(false, true)) return;
        lastPollRequestMs = now;

        worker().execute(() -> {
            try {
                pollWindowsGsmtc();
            } finally {
                POLL_IN_FLIGHT.set(false);
            }
        });
    }

    /** Stops any active Windows helper process and clears cached track state. */
    public static void shutdown() {
        STATE.set(MediaTrackState.EMPTY);
        shutdownHelperAsync();
    }

    private static synchronized ExecutorService worker() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "aurora-media-io");
                t.setDaemon(true);
                return t;
            });
            // Lock-free: the worker may hold HELPER while it waits on a starting helper.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Process p = winProcess;
                if (p != null) p.destroyForcibly();
            }, "aurora-media-shutdown"));
        }
        return executor;
    }

    private static void shutdownHelperAsync() {
        ExecutorService exec = executor;
        if (exec == null) {
            closeWinProcess();
            return;
        }
        exec.execute(SystemMediaBridge::closeWinProcess);
    }

    // ---- Windows persistent PowerShell helper (WinRT GSMTC, Win32 keybd_event fallback) ----

    /** Longest wait for one helper reply; a stuck helper is restarted rather than waited on. */
    private static final long REPLY_TIMEOUT_MS = 4_000L;
    private static final long READY_TIMEOUT_MS = 15_000L;
    /** Marks the end of the helper's stdout in {@link #winLines}; compared by identity. */
    private static final String END_OF_STREAM = new String("<eof>");

    /** The helper script ({@code windows-media-helper.ps1}) as {@code -EncodedCommand} wants it. */
    private static String encodedHelperScript() throws IOException {
        try (InputStream in = SystemMediaBridge.class.getResourceAsStream("windows-media-helper.ps1")) {
            if (in == null) throw new IOException("windows-media-helper.ps1 is missing from the jar");
            String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // UTF-16LE base64: the Windows command line never re-quotes the script (a plain
            // -Command argument lost its inner double quotes, so its C# never compiled).
            return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        }
    }

    private static boolean ensureWinHelper() {
        synchronized (HELPER) {
            if (winProcess != null && winProcess.isAlive()) return true;
            long now = System.currentTimeMillis();
            if (now < winBackoffUntilMs) return false;
            closeWinProcess();
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                        "-EncodedCommand", encodedHelperScript());
                Process process = pb.start();
                winProcess = process;
                winIn = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                winLines = new LinkedBlockingQueue<>();
                pump(process.getInputStream(), winLines, "aurora-media-stdout");
                pump(process.getErrorStream(), null, "aurora-media-stderr");
                String ready = nextLine(READY_TIMEOUT_MS);
                if (ready != null && ready.startsWith("READY")) {
                    AuroraClient.LOGGER.info("[Media] Windows media helper ready (media sessions: {}, media keys: {})",
                            ready.contains("gsmtc=1") ? "yes" : "unavailable",
                            ready.contains("keys=1") ? "yes" : "unavailable");
                    return true;
                }
                AuroraClient.LOGGER.warn("[Media] Windows media helper did not start (first line: {}); retrying in 15 s", ready);
                closeWinProcess();
                winBackoffUntilMs = now + 15_000L;
                return false;
            } catch (IOException e) {
                AuroraClient.LOGGER.warn("[Media] could not start the Windows media helper: {}", e.getMessage());
                closeWinProcess();
                winBackoffUntilMs = now + 30_000L;
                return false;
            }
        }
    }

    /**
     * Drains one helper stream on a daemon thread, so a full pipe never blocks the
     * helper and every read on the worker can time out. stdout lines go to
     * {@code sink}; stderr lines are logged, apart from PowerShell's CLIXML progress records.
     */
    private static void pump(InputStream stream, BlockingQueue<String> sink, String name) {
        Thread t = new Thread(() -> {
            boolean warned = false;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (sink != null) {
                        sink.add(line);
                    } else if (!line.startsWith("#< CLIXML") && !line.startsWith("<Objs ")) {
                        if (warned) AuroraClient.LOGGER.debug("[Media] helper: {}", line);
                        else AuroraClient.LOGGER.warn("[Media] helper: {}", line);
                        warned = true;
                    }
                }
            } catch (IOException ignored) {
                // the helper was closed
            } finally {
                if (sink != null) sink.add(END_OF_STREAM);
            }
        }, name);
        t.setDaemon(true);
        t.start();
    }

    /** The helper's next stdout line, or null after its exit or a timeout (both close it). */
    private static String nextLine(long timeoutMs) {
        BlockingQueue<String> lines = winLines;
        if (lines == null) return null;
        try {
            String line = lines.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (line != null && line != END_OF_STREAM) return line;
            if (line == null) AuroraClient.LOGGER.warn("[Media] Windows media helper stopped answering; restarting it");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        closeWinProcess();
        return null;
    }

    private static void closeWinProcess() {
        synchronized (HELPER) {
            try {
                if (winIn != null) {
                    winIn.write("QUIT\n");
                    winIn.flush();
                    winIn.close();
                }
            } catch (IOException ignored) {}
            if (winProcess != null) {
                winProcess.destroyForcibly();
            }
            winProcess = null;
            winIn = null;
            winLines = null;
        }
    }

    private static void dispatchWindows(MediaAction action) throws IOException {
        synchronized (HELPER) {
            if (!ensureWinHelper()) return;
            // The helper asks the media session itself (works whatever window has focus) and
            // falls back to the virtual media key; volume is always the key.
            winIn.write("CMD " + action.name() + " " + action.windowsVk() + "\n");
            winIn.flush();
            if ("OK 0".equals(nextLine(REPLY_TIMEOUT_MS))) {
                AuroraClient.LOGGER.info("[Media] no media player took {}", action.label());
            }
        }
    }

    private static void pollWindowsGsmtc() {
        synchronized (HELPER) {
            if (!ensureWinHelper()) return;
            try {
                winIn.write("POLL\n");
                winIn.flush();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = nextLine(REPLY_TIMEOUT_MS)) != null) {
                    if ("---END---".equals(line)) break;
                    sb.append(line).append('\n');
                }
                if (line == null) return;
                STATE.set(MediaTrackState.parseLines(sb.toString(), System.currentTimeMillis()));
            } catch (IOException e) {
                closeWinProcess();
            }
        }
    }

    // ---- macOS & Linux cross-platform media control dispatch ----

    private static void dispatchMac(MediaAction action) throws IOException {
        String script = switch (action) {
            case PLAY_PAUSE -> "if application \"Spotify\" is running then tell application \"Spotify\" to playpause else if application \"Music\" is running then tell application \"Music\" to playpause";
            case NEXT -> "if application \"Spotify\" is running then tell application \"Spotify\" to next track else if application \"Music\" is running then tell application \"Music\" to next track";
            case PREVIOUS -> "if application \"Spotify\" is running then tell application \"Spotify\" to previous track else if application \"Music\" is running then tell application \"Music\" to previous track";
            case VOLUME_UP -> "set volume output volume ((output volume of (get volume settings)) + 6)";
            case VOLUME_DOWN -> "set volume output volume ((output volume of (get volume settings)) - 6)";
        };
        new ProcessBuilder("osascript", "-e", script).start();
    }

    private static void dispatchLinux(MediaAction action) throws IOException {
        String[] cmd = switch (action) {
            case PLAY_PAUSE -> new String[]{"playerctl", "play-pause"};
            case NEXT -> new String[]{"playerctl", "next"};
            case PREVIOUS -> new String[]{"playerctl", "previous"};
            case VOLUME_UP -> new String[]{"pactl", "set-sink-volume", "@DEFAULT_SINK@", "+5%"};
            case VOLUME_DOWN -> new String[]{"pactl", "set-sink-volume", "@DEFAULT_SINK@", "-5%"};
        };
        new ProcessBuilder(cmd).start();
    }
}
