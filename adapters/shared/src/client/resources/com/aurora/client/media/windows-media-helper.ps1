# Aurora's Windows media helper: one long-lived Windows PowerShell 5.1 process
# driven over stdin/stdout by SystemMediaBridge. Launched with -EncodedCommand,
# so nothing in this file is re-quoted by the Windows command line.
#
# Protocol (one request line, then its reply):
#   CMD <ACTION> <vk>  ->  OK <1|0>    asks the media session first (play/pause,
#                                       next, previous), else sends the virtual key
#   POLL               ->  KEY=VALUE lines, then ---END---
#   QUIT                   exits
# The first line written is "READY gsmtc=<0|1> keys=<0|1>".

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$keys = $false
try {
    Add-Type -Name K -Namespace AuroraWin -ErrorAction Stop -MemberDefinition @'
[DllImport("user32.dll")] public static extern void keybd_event(byte v, byte s, uint f, UIntPtr e);
'@
    $keys = $true
} catch {}

$mgr = $null
$asTask = $null
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
    $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]
} catch {}

# A task's IsCompletedSuccessfully property does not exist on .NET Framework (PowerShell 5.1); test Status.
function AwaitOp($op, $type) {
    try {
        $task = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
        if ($task.Wait(1500) -and $task.Status -eq [System.Threading.Tasks.TaskStatus]::RanToCompletion) {
            return $task.Result
        }
    } catch {}
    return $null
}

if ($asTask -ne $null) {
    $mgr = AwaitOp ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) `
        ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
}

# The session that is playing, else Windows' current one, else any.
function PickSession {
    $all = @($mgr.GetSessions())
    foreach ($x in $all) {
        $info = $x.GetPlaybackInfo()
        if ($info -ne $null -and $info.PlaybackStatus -eq 'Playing') { return $x }
    }
    $current = $mgr.GetCurrentSession()
    if ($current -ne $null) { return $current }
    if ($all.Count -gt 0) { return $all[0] }
    return $null
}

function Clean($value) {
    if ($value -eq $null) { return '' }
    return ([string]$value) -replace '[\r\n]+', ' '
}

Write-Output ('READY gsmtc=' + [int]($mgr -ne $null) + ' keys=' + [int]$keys)

while (($line = [Console]::In.ReadLine()) -ne $null) {
    if ($line -eq 'QUIT') { break }
    if ($line.StartsWith('CMD ')) {
        $parts = $line.Split(' ')
        $done = $false
        if ($mgr -ne $null) {
            try {
                $s = PickSession
                if ($s -ne $null) {
                    $op = $null
                    switch ($parts[1]) {
                        'PLAY_PAUSE' { $op = $s.TryTogglePlayPauseAsync() }
                        'NEXT' { $op = $s.TrySkipNextAsync() }
                        'PREVIOUS' { $op = $s.TrySkipPreviousAsync() }
                    }
                    if ($op -ne $null) { $done = (AwaitOp $op ([bool])) -eq $true }
                }
            } catch {}
        }
        if (-not $done -and $keys -and $parts.Count -gt 2) {
            try {
                $vk = [byte][int]$parts[2]
                [AuroraWin.K]::keybd_event($vk, 0, 0, [UIntPtr]::Zero)
                [AuroraWin.K]::keybd_event($vk, 0, 2, [UIntPtr]::Zero)
                $done = $true
            } catch {}
        }
        Write-Output ('OK ' + [int]$done)
    } elseif ($line -eq 'POLL') {
        if ($mgr -ne $null) {
            try {
                $s = PickSession
                if ($s -ne $null) {
                    $info = $s.GetPlaybackInfo()
                    $props = AwaitOp ($s.TryGetMediaPropertiesAsync()) `
                        ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                    if ($props -ne $null) {
                        Write-Output ('STATUS=' + $info.PlaybackStatus)
                        Write-Output ('TITLE=' + (Clean $props.Title))
                        Write-Output ('ARTIST=' + (Clean $props.Artist))
                        Write-Output ('ALBUM=' + (Clean $props.AlbumTitle))
                        $tl = $s.GetTimelineProperties()
                        if ($tl -ne $null) {
                            # Position is as of LastUpdatedTime; players such as Spotify update it rarely.
                            $pos = $tl.Position.TotalMilliseconds
                            if ($info.PlaybackStatus -eq 'Playing') {
                                $age = ([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalMilliseconds
                                if ($age -gt 0 -and $age -lt 21600000) { $pos += $age }
                            }
                            Write-Output ('POS=' + [long]$pos)
                            Write-Output ('DUR=' + [long]$tl.EndTime.TotalMilliseconds)
                        }
                    }
                }
            } catch {}
        }
        Write-Output '---END---'
    }
}
