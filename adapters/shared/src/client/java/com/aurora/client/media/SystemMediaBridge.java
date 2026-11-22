package com.aurora.client.media;

import com.aurora.client.AuroraClient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    // Reusable persistent Windows helper (only spawned on demand on Windows).
    private static Process winProcess;
    private static BufferedWriter winIn;
    private static BufferedReader winOut;
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
                AuroraClient.LOGGER.debug("[Media] failed to dispatch {}: {}", action, t.getMessage());
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
            Runtime.getRuntime().addShutdownHook(new Thread(SystemMediaBridge::closeWinProcess, "aurora-media-shutdown"));
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

    // ---- Windows persistent PowerShell helper (Win32 keybd_event + WinRT GSMTC) ----

    private static final String WIN_HELPER_SCRIPT =
            "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; "
            + "Add-Type -Name K -Namespace AuroraWin -MemberDefinition "
            + "'[DllImport(\"user32.dll\")] public static extern void keybd_event(byte v, byte s, uint f, UIntPtr e);'; "
            + "$gsmtcReady = $false; "
            + "try { "
            + "  Add-Type -AssemblyName System.Runtime.WindowsRuntime; "
            + "  $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]; "
            + "  $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { "
            + "    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' "
            + "  })[0]; "
            + "  function AwaitOp($op, $t) { $m = $asTaskGeneric.MakeGenericMethod($t); $task = $m.Invoke($null, @($op)); $task.Wait(1500) | Out-Null; if ($task.IsCompletedSuccessfully) { return $task.Result } return $null }; "
            + "  $mgr = AwaitOp ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]); "
            + "  if ($mgr -ne $null) { $gsmtcReady = $true } "
            + "} catch { $gsmtcReady = $false }; "
            + "Write-Output 'READY'; "
            + "while (($line = [Console]::ReadLine()) -ne $null) { "
            + "  if ($line -eq 'QUIT') { break } "
            + "  elseif ($line.StartsWith('VK ')) { "
            + "    $vk = [byte][int]$line.Substring(3); "
            + "    [AuroraWin.K]::keybd_event($vk, 0, 0, [UIntPtr]::Zero); "
            + "    [AuroraWin.K]::keybd_event($vk, 0, 2, [UIntPtr]::Zero); "
            + "    Write-Output 'OK'; "
            + "  } elseif ($line -eq 'POLL') { "
            + "    if ($gsmtcReady -and $mgr -ne $null) { "
            + "      try { "
            + "        $s = $mgr.GetCurrentSession(); "
            + "        if ($s -ne $null) { "
            + "          $info = $s.GetPlaybackInfo(); "
            + "          $props = AwaitOp ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]); "
            + "          $tl = $s.GetTimelineProperties(); "
            + "          if ($props -ne $null) { "
            + "            Write-Output ('STATUS=' + $info.PlaybackStatus); "
            + "            Write-Output ('TITLE=' + $props.Title); "
            + "            Write-Output ('ARTIST=' + $props.Artist); "
            + "            Write-Output ('ALBUM=' + $props.AlbumTitle); "
            + "            if ($tl -ne $null) { "
            + "              Write-Output ('POS=' + [long]$tl.Position.TotalMilliseconds); "
            + "              Write-Output ('DUR=' + [long]$tl.EndTime.TotalMilliseconds); "
            + "            } "
            + "          } "
            + "        } "
            + "      } catch {} "
            + "    } "
            + "    Write-Output '---END---'; "
            + "  } "
            + "}";

    private static synchronized boolean ensureWinHelper() {
        if (winProcess != null && winProcess.isAlive()) return true;
        long now = System.currentTimeMillis();
        if (now < winBackoffUntilMs) return false;
        closeWinProcess();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                    "-Command", WIN_HELPER_SCRIPT);
            pb.redirectErrorStream(false);
            winProcess = pb.start();
            winIn = new BufferedWriter(new OutputStreamWriter(winProcess.getOutputStream(), StandardCharsets.UTF_8));
            winOut = new BufferedReader(new InputStreamReader(winProcess.getInputStream(), StandardCharsets.UTF_8));
            String line = winOut.readLine();
            if ("READY".equals(line)) return true;
            closeWinProcess();
            winBackoffUntilMs = now + 15_000L;
            return false;
        } catch (IOException e) {
            closeWinProcess();
            winBackoffUntilMs = now + 30_000L;
            return false;
        }
    }

    private static synchronized void closeWinProcess() {
        try {
            if (winIn != null) {
                winIn.write("QUIT\n");
                winIn.flush();
                winIn.close();
            }
        } catch (IOException ignored) {}
        try { if (winOut != null) winOut.close(); } catch (IOException ignored) {}
        if (winProcess != null) {
            winProcess.destroyForcibly();
        }
        winProcess = null;
        winIn = null;
        winOut = null;
    }

    private static synchronized void dispatchWindows(MediaAction action) throws IOException {
        if (!ensureWinHelper()) return;
        winIn.write("VK " + action.windowsVk() + "\n");
        winIn.flush();
        String ack = winOut.readLine();
        if (ack == null) closeWinProcess();
    }

    private static synchronized void pollWindowsGsmtc() {
        if (!ensureWinHelper()) return;
        try {
            winIn.write("POLL\n");
            winIn.flush();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = winOut.readLine()) != null) {
                if ("---END---".equals(line)) break;
                sb.append(line).append('\n');
            }
            if (line == null) {
                closeWinProcess();
                return;
            }
            STATE.set(MediaTrackState.parseLines(sb.toString(), System.currentTimeMillis()));
        } catch (IOException e) {
            closeWinProcess();
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
