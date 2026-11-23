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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Cross-platform OS media control dispatcher and Windows-first GSMTC Now Playing poller.
 *
 * <p>All OS process I/O runs exclusively off the render/tick thread on a single
 * daemon worker. In factory defaults (feature off), zero threads or processes are spawned.
 */
public final class SystemMediaBridge {

    private static final long POLL_INTERVAL_MS = 2_000L;
    /** Keep the command helper warm between isolated keypresses when the HUD is off. */
    static final long IDLE_SHUTDOWN_MS = 120_000L;
    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean WINDOWS = OS.contains("win");
    private static final boolean MAC = OS.contains("mac");

    @FunctionalInterface
    interface WinProcessFactory {
        Process start() throws IOException;
    }

    private static final WinProcessFactory REAL_WIN_FACTORY = () -> new ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
            "-EncodedCommand", encodedHelperScript()).start();

    private static volatile WinProcessFactory winProcessFactory = REAL_WIN_FACTORY;
    private static volatile LongSupplier clock = System::currentTimeMillis;
    private static volatile boolean windowsOverride = WINDOWS;

    private static final AtomicReference<MediaTrackState> STATE = new AtomicReference<>(MediaTrackState.EMPTY);
    private static final AtomicBoolean POLL_IN_FLIGHT = new AtomicBoolean(false);
    private static final AtomicBoolean CLOSE_SCHEDULED = new AtomicBoolean(false);
    private static final AtomicInteger SCHEDULED_CLOSES = new AtomicInteger(0);

    private static volatile ExecutorService executor;
    private static volatile long lastPollRequestMs = 0L;
    private static volatile long lastHelperUseMs = 0L;
    private static volatile boolean controlsActive = false;
    private static volatile boolean hudPollingActive = false;

    // Reusable persistent Windows helper (only spawned on demand on Windows), guarded by
    // HELPER rather than the class lock: worker() runs on the tick thread and must never
    // wait behind a helper read.
    private static final Object HELPER = new Object();
    private static volatile Process winProcess;
    private static BufferedWriter winIn;
    private static BlockingQueue<String> winLines;
    private static long winBackoffUntilMs = 0L;

    private SystemMediaBridge() {}

    public static boolean isWindows() { return windowsOverride; }

    /** Installs deterministic test seams (package-private). */
    static void configureForTest(WinProcessFactory factory, LongSupplier testClock, boolean pretendWindows) {
        closeWinProcess();
        winProcessFactory = factory != null ? factory : REAL_WIN_FACTORY;
        clock = testClock != null ? testClock : System::currentTimeMillis;
        windowsOverride = pretendWindows;
        STATE.set(MediaTrackState.EMPTY);
        COVER.set(null);
        POLL_IN_FLIGHT.set(false);
        CLOSE_SCHEDULED.set(false);
        SCHEDULED_CLOSES.set(0);
        lastPollRequestMs = 0L;
        lastHelperUseMs = 0L;
        controlsActive = false;
        hudPollingActive = false;
        winBackoffUntilMs = 0L;
    }

    static void resetForTest() {
        configureForTest(null, null, WINDOWS);
    }

    static int scheduledCloseCountForTest() {
        return SCHEDULED_CLOSES.get();
    }

    /** Waits for currently queued worker tasks to finish (tests only). */
    static boolean awaitWorkerIdleForTest(long timeoutMs) {
        ExecutorService exec = executor;
        if (exec == null) return true;
        try {
            exec.submit(() -> {}).get(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Latest polled desktop media track state (never null). */
    public static MediaTrackState currentTrack() { return STATE.get(); }

    /**
     * A track's cover: a {@value #COVER_SIZE}-pixel square PNG, for the track with
     * {@code trackKey} ({@link MediaTrackState#trackKey()}). {@code serial} rises with each new cover.
     */
    public record Cover(String trackKey, byte[] png, long serial) {}

    public static final int COVER_SIZE = 96;
    /** A 96-pixel PNG is a few tens of KB; anything far larger is not the helper's cover. */
    private static final int MAX_COVER_BASE64 = 512 * 1024;
    private static final AtomicReference<Cover> COVER = new AtomicReference<>();
    private static long coverSerial;

    /** The most recent cover, or null; it belongs to the current track only when the keys match. */
    public static Cover currentCover() { return COVER.get(); }

    private static void acceptCover(String trackKey, String base64) {
        if (base64.isEmpty() || base64.length() > MAX_COVER_BASE64) return;
        try {
            COVER.set(new Cover(trackKey, Base64.getDecoder().decode(base64), ++coverSerial));
        } catch (IllegalArgumentException e) {
            AuroraClient.LOGGER.debug("[Media] unreadable cover art: {}", e.getMessage());
        }
    }

    /** Dispatches an OS media control action asynchronously off the tick thread. */
    public static void dispatch(MediaAction action) {
        if (action == null) return;
        lastHelperUseMs = clock.getAsLong();
        worker().execute(() -> {
            try {
                if (windowsOverride) {
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
     * Lightweight tick-thread heartbeat separating Media Controls helper
     * lifetime from Now Playing HUD polling:
     * <ul>
     *   <li>Disabling Now Playing while Media Controls stays on stops polling
     *       and clears HUD track state on transition, keeping the warm helper
     *       alive until {@value #IDLE_SHUTDOWN_MS} ms of inactivity so isolated
     *       media keypresses do not restart PowerShell.</li>
     *   <li>Disabling Media Controls altogether shuts down the helper
     *       immediately, coalescing close requests so a stalled helper never
     *       accumulates closure tasks on the worker queue.</li>
     * </ul>
     */
    public static void tick(boolean controlsEnabled, boolean hudEnabled) {
        if (!windowsOverride) return;
        controlsActive = controlsEnabled;
        if (!controlsEnabled) {
            hudPollingActive = false;
            clearTrackUiState();
            if (winProcess != null) {
                shutdownHelperAsync(false);
            }
            return;
        }
        long now = clock.getAsLong();
        if (!hudEnabled) {
            if (hudPollingActive || STATE.get().active() || COVER.get() != null) {
                hudPollingActive = false;
                clearTrackUiState();
            }
            if (winProcess != null && lastHelperUseMs > 0L && now - lastHelperUseMs >= IDLE_SHUTDOWN_MS) {
                shutdownHelperAsync(true);
            }
            return;
        }
        hudPollingActive = true;
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

    /**
     * Legacy single-flag entry point; delegates to {@link #tick(boolean, boolean)}.
     */
    public static void tickPoll(boolean hudEnabled) {
        tick(hudEnabled, hudEnabled);
    }

    /** Stops any active Windows helper process and clears cached track state. */
    public static void shutdown() {
        controlsActive = false;
        hudPollingActive = false;
        clearTrackUiState();
        shutdownHelperAsync(false);
    }

    private static void clearTrackUiState() {
        STATE.set(MediaTrackState.EMPTY);
        COVER.set(null);
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

    private static void shutdownHelperAsync(boolean idleOnly) {
        if (!CLOSE_SCHEDULED.compareAndSet(false, true)) return;
        SCHEDULED_CLOSES.incrementAndGet();
        ExecutorService exec = executor;
        if (exec == null) {
            CLOSE_SCHEDULED.set(false);
            if (!idleOnly || shouldCloseForIdle()) closeWinProcess();
            return;
        }
        exec.execute(() -> {
            CLOSE_SCHEDULED.set(false);
            if (!idleOnly || shouldCloseForIdle()) closeWinProcess();
        });
    }

    private static boolean shouldCloseForIdle() {
        return !controlsActive || (!hudPollingActive && clock.getAsLong() - lastHelperUseMs >= IDLE_SHUTDOWN_MS);
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
            long now = clock.getAsLong();
            if (now < winBackoffUntilMs) return false;
            closeWinProcess();
            try {
                Process process = winProcessFactory.start();
                winProcess = process;
                lastHelperUseMs = now;
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
            lastHelperUseMs = clock.getAsLong();
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
            lastHelperUseMs = clock.getAsLong();
            try {
                winIn.write(com.aurora.client.config.AuroraConfig.get().mediaHudShowArt ? "POLL ART\n" : "POLL\n");
                winIn.flush();
                StringBuilder sb = new StringBuilder();
                String art = null;
                String line;
                while ((line = nextLine(REPLY_TIMEOUT_MS)) != null) {
                    if ("---END---".equals(line)) break;
                    if (line.startsWith("ART=")) art = line.substring(4);
                    else sb.append(line).append('\n');
                }
                if (line == null) return;
                MediaTrackState state = MediaTrackState.parseLines(sb.toString(), clock.getAsLong());
                if (hudPollingActive) {
                    STATE.set(state);
                    if (art != null && state.active()) acceptCover(state.trackKey(), art);
                }
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
