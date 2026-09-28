package riptide.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.util.Util;
import net.minecraft.util.Util.OS;

public final class RiptideSpotify {
   private static final String SEPARATOR = "\u001f";
   private static final long IDLE_TIMEOUT_MS = 15000L;
   private static final long RESTART_BACKOFF_MS = 5000L;
   private static final int MAX_RAPID_FAILURES = 3;
   private static final long HEALTHY_RUNTIME_MS = 30000L;
   private static final long GIVE_UP_COOLDOWN_MS = 60000L;
   private static final long WATCHDOG_INTERVAL_MS = 2000L;
   private static final long TOOL_TIMEOUT_MS = 3000L;
   private static final long GDBUS_POLL_MS = 2000L;
   private static final long OSASCRIPT_POLL_MS = 1500L;
   private static final long COMMAND_TIMEOUT_MS = 3000L;
   private static final int ART_MAX_BYTES = 524288;
   private static volatile RiptideSpotify.Snapshot current = new RiptideSpotify.Snapshot(
      RiptideSpotify.Status.UNAVAILABLE, "", "", 0L, 0.0, 0.0, false, RiptideSpotify.Repeat.UNKNOWN, -1, ""
   );
   private static volatile long lastWantedAtMs;
   private static volatile boolean hooksInstalled;
   private static volatile boolean sourceAnywhere;
   private static final Object LOCK = new Object();
   private static Process process;
   private static Thread worker;
   private static int generation;
   private static int consecutiveFailures;
   private static boolean coolingDown;
   private static long gaveUpAtMs;
   private static final Map<String, Boolean> TOOL_AVAILABILITY = new ConcurrentHashMap<>();
   private static volatile Path windowsScriptPath;
   private static volatile Path artFilePath;
   private static final Object COMMAND_WRITE_LOCK = new Object();
   private static final Object ART_FILE_LOCK = new Object();
   private static final Pattern ARTWORK_URL100 = Pattern.compile("\"artworkUrl100\" *: *\"([^\"]+)\"");
   private static volatile String pendingArtKey;
   private static final long ART_LOG_INTERVAL_MS = 30000L;
   static final Map<String, Long> ART_LOG_LAST = new ConcurrentHashMap<>();
   static final int ART_CACHE_MAX_ENTRIES = 512;
   static final Map<String, String> ART_FALLBACK_CACHE = new LinkedHashMap<String, String>(16, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<String, String> eldest) {
         return this.size() > 512;
      }
   };
   private static volatile String lastArtFileKey;
   private static volatile String lastArtFilePath;
   static String itunesBaseUrl = "https://itunes.apple.com/search";
   private static final String[] ART_RUNG_NAMES = new String[]{"strict", "title", "artist", "top-hit", "artist-query"};
   private static volatile int lastArtRung = -1;
   private static final String[] COVER_JUNK_MARKERS = new String[]{
      "karaoke",
      "tribute",
      "emulation",
      "chiptune",
      "8-bit",
      "16-bit",
      "8 bit",
      "16 bit",
      "lofi version",
      "cover version",
      "made famous",
      "in the style of",
      "instrumental version"
   };
   static String deezerBaseUrl = "https://api.deezer.com/search";
   private static final String POWERSHELL_SCRIPT = "$ErrorActionPreference = 'Continue'\n[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\nAdd-Type -AssemblyName System.Runtime.WindowsRuntime\n\n$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]\nfunction Await-Op($op, $resultType) {\n    $task = $asTaskGeneric.MakeGenericMethod($resultType).Invoke($null, @($op))\n    if (-not $task.Wait(5000)) { throw 'timed out waiting for a WinRT operation' }\n    return $task.Result\n}\n\n$code = @'\nusing System;\nusing System.Collections.Generic;\nusing System.Diagnostics;\nusing System.Runtime.InteropServices;\n\n// ISimpleAudioVolume get/set for the session belonging to a process-name hint.\n// Raw vtable throughout: on machines with an audio enhancement driver (probed on\n// this one), the session ENUMERATOR object answers QueryInterface with\n// E_NOINTERFACE for interfaces its vtable actually implements, so the typed RCW\n// path cannot reach it; the vtable contracts are stable, so calling slots\n// directly works on wrapped and healthy machines alike.\npublic static class RiptideSpotifyVolume {\n    [ComImport, Guid(\"A95664D2-9614-4F35-A746-DE8DB63617E6\"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]\n    public interface IMMDeviceEnumerator {\n        [PreserveSig] int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);\n        [PreserveSig] int GetDefaultAudioEndpoint(int dataFlow, int role, out IntPtr device);\n        [PreserveSig] int GetDevice([MarshalAs(UnmanagedType.LPWStr)] string id, out IntPtr device);\n        [PreserveSig] int RegisterEndpointNotificationCallback(IntPtr client);\n        [PreserveSig] int UnregisterEndpointNotificationCallback(IntPtr client);\n    }\n\n    [ComImport, Guid(\"BCDE0395-E52F-467C-8E3D-C4579291692E\")]\n    public class MMDeviceEnumeratorComObject { }\n\n    static readonly Guid IID_MGR2 = new Guid(\"77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F\");\n    static readonly Guid IID_VOLUME = new Guid(\"87CE5498-68D6-44E5-9215-6DA47EF883D8\");\n\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComOutOut(IntPtr t, out IntPtr v);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComGetCount(IntPtr t, out int v);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComItem(IntPtr t, int i, out IntPtr v);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComActivate(IntPtr t, [MarshalAs(UnmanagedType.LPStruct)] Guid iid, int c, IntPtr p, out IntPtr v);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComGetPid(IntPtr t, out uint pid);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComGetVolume(IntPtr t, out float level);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComSetVolume(IntPtr t, float level, IntPtr ctx);\n\n    static IntPtr Slot(IntPtr obj, int slot) {\n        return Marshal.ReadIntPtr(Marshal.ReadIntPtr(obj), slot * IntPtr.Size);\n    }\n\n    // Album art, take four. Every earlier read path failed on this box, all\n    // probed: PowerShell's binder cannot bind the WinRT AsStream extension, a\n    // winmd reference for compile-time WinRT types fails to load (0x80131047),\n    // the returned stream's RCW exposes no interfaces, and vtable reads against a\n    // NATIVE buffer (IBufferFactory) either lie (hollow S_OK) or segfault the\n    // helper (exit 139). So the read buffer is a .NET-BACKED one:\n    // System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBuffer wraps a\n    // managed byte[] as an IBuffer with a CLR-generated, guaranteed-valid vtable.\n    // The only native vtable call left is IInputStream.ReadAsync (slot 6), proven\n    // safe here. Bytes come back through WindowsRuntimeBufferExtensions.ToArray,\n    // purely managed. Crash containment: the MTA worker (apartment sensitivity,\n    // probed) plus HandleProcessCorruptedStateExceptions, and an all-zero guard -\n    // on this stack the read returns S_OK with a NULL op and a zero-filled buffer\n    // (hollow success), which must read as failure, never as a 4 MB black image.\n    [System.Runtime.ExceptionServices.HandleProcessCorruptedStateExceptions]\n    static byte[] ReadArtStreamMta(object streamObj) {\n        IntPtr streamPtr = IntPtr.Zero, istream = IntPtr.Zero, bufPtr = IntPtr.Zero, op = IntPtr.Zero;\n        try {\n            System.Reflection.Assembly wrtAsm = null;\n            foreach (System.Reflection.Assembly asm in AppDomain.CurrentDomain.GetAssemblies()) {\n                if (asm.GetName().Name == \"System.Runtime.WindowsRuntime\") { wrtAsm = asm; break; }\n            }\n            if (wrtAsm == null) return null;\n            Type wrb = wrtAsm.GetType(\"System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBuffer\");\n            Type ext = wrtAsm.GetType(\"System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBufferExtensions\");\n            if (wrb == null || ext == null) return null;\n            System.Reflection.MethodInfo create = null, toArray = null;\n            foreach (System.Reflection.MethodInfo m in wrb.GetMethods()) {\n                if (m.Name == \"Create\" && m.GetParameters().Length == 4) { create = m; break; }\n            }\n            foreach (System.Reflection.MethodInfo m in ext.GetMethods()) {\n                if (m.Name == \"ToArray\" && m.GetParameters().Length == 1) { toArray = m; break; }\n            }\n            if (create == null || toArray == null) return null;\n            byte[] backing = new byte[4 * 1024 * 1024];\n            object buffer = create.Invoke(null, new object[] { backing, 0, backing.Length, backing.Length });\n            Guid iidStream = new Guid(\"905a0fe1-bc53-11df-8c49-001e4fc686da\");\n            streamPtr = Marshal.GetIUnknownForObject(streamObj);\n            if (Marshal.QueryInterface(streamPtr, ref iidStream, out istream) < 0 || istream == IntPtr.Zero) return null;\n            bufPtr = Marshal.GetIUnknownForObject(buffer);\n            int hrRead = Marshal.GetDelegateForFunctionPointer<ComReadAsync>(Slot(istream, 6))(istream, bufPtr, (uint)backing.Length, 0u, out op);\n            if (hrRead < 0) return null;\n            if (op != IntPtr.Zero) { // async op exists: wait it out; a null op means the fill was synchronous\n                int status = 0;\n                long deadline = DateTime.UtcNow.Ticks + 5L * 10000000L;\n                while (status == 0 && DateTime.UtcNow.Ticks < deadline) {\n                    System.Threading.Thread.Sleep(10);\n                    if (Marshal.GetDelegateForFunctionPointer<ComGetCount>(Slot(op, 7))(op, out status) < 0) return null;\n                }\n                if (status != 1) return null;\n            }\n            byte[] result = (byte[])toArray.Invoke(null, new object[] { buffer });\n            // The hollow-success guard: a hollow read leaves the wrapper at its\n            // initial all-zero content; real album art always has non-zero bytes.\n            bool anyNonZero = false;\n            for (int i = 0; i < result.Length; i++) {\n                if (result[i] != 0) { anyNonZero = true; break; }\n            }\n            return anyNonZero ? result : null;\n        } catch (System.Exception) {\n            return null;\n        } finally {\n            if (op != IntPtr.Zero) Marshal.Release(op);\n            if (bufPtr != IntPtr.Zero) Marshal.Release(bufPtr);\n            if (istream != IntPtr.Zero) Marshal.Release(istream);\n            if (streamPtr != IntPtr.Zero) Marshal.Release(streamPtr);\n        }\n    }\n\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComReadAsync(IntPtr t, IntPtr buffer, uint count, uint options, out IntPtr op);\n\n    static HashSet<int> hintPids = new HashSet<int>();\n    static string hintCached = \"\";\n    static DateTime hintRefreshed = DateTime.MinValue;\n\n    // Process-name matching by either-way containment: \"spotify\" hits Spotify.exe,\n    // and a packaged AUMID head like \"SpotifyAB.SpotifyMusic_xxx\" still contains it.\n    static HashSet<int> PidsForHint(string appHint) {\n        if (appHint != hintCached || (DateTime.UtcNow - hintRefreshed).TotalSeconds > 2.0) {\n            HashSet<int> set = new HashSet<int>();\n            string hint = (appHint ?? \"\").ToLowerInvariant();\n            if (hint.Length > 0) {\n                foreach (Process p in Process.GetProcesses()) {\n                    try {\n                        string n = p.ProcessName.ToLowerInvariant();\n                        if (hint.Contains(n) || n.Contains(hint)) set.Add(p.Id);\n                    } catch { }\n                }\n            }\n            hintPids = set;\n            hintCached = appHint ?? \"\";\n            hintRefreshed = DateTime.UtcNow;\n        }\n        return hintPids;\n    }\n\n    public static int GetSessionVolume(string appHint) {\n        IntPtr volume = FindVolume(appHint);\n        if (volume == IntPtr.Zero) return -1;\n        try {\n            float v;\n            if (Marshal.GetDelegateForFunctionPointer<ComGetVolume>(Slot(volume, 4))(volume, out v) < 0) return -1;\n            return (int)Math.Round(v * 100.0f);\n        } finally {\n            Marshal.Release(volume);\n        }\n    }\n\n    public static bool SetSessionVolume(string appHint, double fraction) {\n        IntPtr volume = FindVolume(appHint);\n        if (volume == IntPtr.Zero) return false;\n        try {\n            return Marshal.GetDelegateForFunctionPointer<ComSetVolume>(Slot(volume, 3))(volume, (float)fraction, IntPtr.Zero) >= 0;\n        } finally {\n            Marshal.Release(volume);\n        }\n    }\n\n    static IntPtr FindVolume(string appHint) {\n        HashSet<int> pids = PidsForHint(appHint);\n        if (pids.Count == 0) return IntPtr.Zero;\n        IMMDeviceEnumerator enumerator = (IMMDeviceEnumerator)new MMDeviceEnumeratorComObject();\n        IntPtr collection;\n        if (enumerator.EnumAudioEndpoints(0, 1, out collection) < 0) return IntPtr.Zero;\n        try {\n            int deviceCount;\n            if (Marshal.GetDelegateForFunctionPointer<ComGetCount>(Slot(collection, 3))(collection, out deviceCount) < 0) return IntPtr.Zero;\n            for (int d = 0; d < deviceCount; d++) {\n                IntPtr device;\n                if (Marshal.GetDelegateForFunctionPointer<ComItem>(Slot(collection, 4))(collection, d, out device) < 0) continue;\n                if (device == IntPtr.Zero) continue;\n                try {\n                    IntPtr manager;\n                    if (Marshal.GetDelegateForFunctionPointer<ComActivate>(Slot(device, 3))(device, IID_MGR2, 23, IntPtr.Zero, out manager) < 0) continue;\n                    if (manager == IntPtr.Zero) continue;\n                    try {\n                        IntPtr enumPtr;\n                        if (Marshal.GetDelegateForFunctionPointer<ComOutOut>(Slot(manager, 5))(manager, out enumPtr) < 0) continue;\n                        if (enumPtr == IntPtr.Zero) continue;\n                        try {\n                            int count;\n                            if (Marshal.GetDelegateForFunctionPointer<ComGetCount>(Slot(enumPtr, 3))(enumPtr, out count) < 0) continue;\n                            for (int i = 0; i < count; i++) {\n                                IntPtr sessionPtr;\n                                if (Marshal.GetDelegateForFunctionPointer<ComItem>(Slot(enumPtr, 4))(enumPtr, i, out sessionPtr) < 0) continue;\n                                if (sessionPtr == IntPtr.Zero) continue;\n                                try {\n                                    uint sessionPid;\n                                    if (Marshal.GetDelegateForFunctionPointer<ComGetPid>(Slot(sessionPtr, 14))(sessionPtr, out sessionPid) < 0) continue;\n                                    if (!pids.Contains((int)sessionPid)) continue;\n                                    Guid iid = IID_VOLUME;\n                                    IntPtr volumePtr;\n                                    if (Marshal.QueryInterface(sessionPtr, ref iid, out volumePtr) < 0) continue;\n                                    return volumePtr; // caller releases\n                                } finally {\n                                    Marshal.Release(sessionPtr);\n                                }\n                            }\n                        } finally {\n                            Marshal.Release(enumPtr);\n                        }\n                    } finally {\n                        Marshal.Release(manager);\n                    }\n                } finally {\n                    Marshal.Release(device);\n                }\n            }\n            return IntPtr.Zero;\n        } finally {\n            Marshal.Release(collection);\n        }\n    }\n}\n'@\nAdd-Type -TypeDefinition $code\n\n$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]\n$propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime]\n$boolType = [bool]\n$us = [char]31\n$inv = [Globalization.CultureInfo]::InvariantCulture\n$manager = $null\n$artKey = ''\n$artPath = Join-Path $env:TEMP 'riptide_spotify_art.png'\n$anyMedia = $env:RIPTIDE_SPOTIFY_SOURCE -eq 'ANY'\n# Stdin commands are read through a raw StreamReader over the standard input,\n# NOT [Console]::In.ReadLineAsync: on .NET Framework that call takes the console\n# sync root while it blocks on an EMPTY anonymous pipe (exactly what Java's\n# ProcessBuilder hands us - probed: the helper froze silently after 'before\n# readline' with zero output). StreamReader.ReadLineAsync runs on a pool thread\n# with no console lock, so the status loop never stalls on an empty pipe.\n$stdinReader = New-Object System.IO.StreamReader([Console]::OpenStandardInput())\n$readTask = $stdinReader.ReadLineAsync()\n$stdinDead = $false\n# Event-driven refresh: MediaPropertiesChanged (track skip) and\n# PlaybackInfoChanged (play/pause) set a GLOBAL dirty flag (global, not script\n# scope, because the handler executes off the main runspace thread). The loop\n# below re-reads immediately on dirty, otherwise every 450 ms. NOTE: on this box\n# GSMTC property-change events register fine but NEVER fire (probed: two real\n# skips, zero firings - the same stack that hollows art streams and returns null\n# RepeatMode), so the event path is inert here and the 450 ms poll is what\n# actually delivers faster updates: worst-case staleness is halved.\n$global:spotifyDirty = $false\n$subscribedAumid = ''\n$lastPoll = [DateTime]::UtcNow.AddSeconds(-1)\n\nfunction Save-Art($props, $path) {\n    try {\n        if ($null -eq $props.Thumbnail) { return $false }\n        $stream = Await-Op ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])\n        if ($null -eq $stream) { return $false }\n        $bytes = [RiptideSpotifyVolume]::ReadArtStream($stream)\n        if ($null -eq $bytes) { return $false }\n        # Atomic-ish publish: write a temp sibling, then Copy over the stable path\n        # and delete the temp. [IO.File]::Move(source, dest, overwrite) does NOT\n        # exist on .NET Framework 4.x (only .NET Core 3.0+), so Copy+Delete is the\n        # safe form here - a render-thread read never sees a torn image.\n        $tmp = $path + '.part'\n        [System.IO.File]::WriteAllBytes($tmp, $bytes)\n        [System.IO.File]::Copy($tmp, $path, $true)\n        Remove-Item $tmp -Force\n        return $true\n    } catch {\n        return $false\n    }\n}\n\nfunction Invoke-SpotifyCommand($session, $hint, $line) {\n    if ($line -eq 'PLAY_PAUSE') {\n        if ($null -ne $session) { try { Await-Op ($session.TryTogglePlayPauseAsync()) $boolType | Out-Null } catch { } }\n    } elseif ($line -eq 'NEXT') {\n        if ($null -ne $session) { try { Await-Op ($session.TrySkipNextAsync()) $boolType | Out-Null } catch { } }\n    } elseif ($line -eq 'PREV') {\n        if ($null -ne $session) { try { Await-Op ($session.TrySkipPreviousAsync()) $boolType | Out-Null } catch { } }\n    } elseif ($line -eq 'SHUFFLE_ON') {\n        if ($null -ne $session) { try { Await-Op ($session.TryChangeShuffleActiveAsync($true)) $boolType | Out-Null } catch { } }\n    } elseif ($line -eq 'SHUFFLE_OFF') {\n        if ($null -ne $session) { try { Await-Op ($session.TryChangeShuffleActiveAsync($false)) $boolType | Out-Null } catch { } }\n    } elseif ($line -eq 'SOURCE=ANY') {\n        $script:anyMedia = $true\n    } elseif ($line -eq 'SOURCE=SPOTIFY') {\n        $script:anyMedia = $false\n    } elseif ($line -match '^REPEAT=(OFF|ALL|ONE)$') {\n        if ($null -ne $session) {\n            # WinRT enum values passed as their underlying int (None=0, Track=1,\n            # All=2): on some boxes the repeat-mode enum type cannot be projected\n            # into PowerShell at all (probed: [type] load fails and the binder then\n            # hides the method). There the call simply no-ops inside this catch;\n            # where the enum projects, the conversion binds and the command works.\n            $modeInt = switch ($Matches[1]) { 'OFF' { 0 } 'ALL' { 2 } 'ONE' { 1 } }\n            try { Await-Op ($session.TryChangeRepeatModeAsync($modeInt)) $boolType | Out-Null } catch { }\n        }\n    } elseif ($line -match '^VOLUME=(\\d+)$') {\n        [RiptideSpotifyVolume]::SetSessionVolume($hint, [double]$Matches[1] / 100.0) | Out-Null\n    }\n}\n\nwhile ($true) {\n    $elapsed = ([DateTime]::UtcNow - $lastPoll).TotalMilliseconds\n    if ($global:spotifyDirty -or $elapsed -ge 450) {\n        $global:spotifyDirty = $false\n        $lastPoll = [DateTime]::UtcNow\n    try {\n        if ($null -eq $manager) {\n            $subscribedAumid = ''\n            $manager = Await-Op ($managerType::RequestAsync()) $managerType\n        }\n        $session = $null\n        if ($anyMedia) {\n            $session = $manager.GetCurrentSession()\n        } else {\n            foreach ($candidate in $manager.GetSessions()) {\n                if ($candidate.SourceAppUserModelId -match 'spotify') { $session = $candidate; break }\n            }\n        }\n        if ($null -ne $session -and ([string]$session.SourceAppUserModelId) -ne $subscribedAumid) {\n            # Re-subscribe keyed on the AUMID STRING, not RCW object identity:\n            # if the projection ever yields a fresh RCW for the same session, an\n            # object-reference comparison would re-register handlers every poll.\n            try {\n                $null = $session.add_MediaPropertiesChanged({ $global:spotifyDirty = $true })\n                $null = $session.add_PlaybackInfoChanged({ $global:spotifyDirty = $true })\n                $subscribedAumid = [string]$session.SourceAppUserModelId\n            } catch { }\n        }\n        if ($null -eq $session) {\n            [Console]::WriteLine('STOPPED')\n        } else {\n            $hint = if ($anyMedia) { ([string]$session.SourceAppUserModelId).Split('!')[0] } else { 'spotify' }\n            $info = $session.GetPlaybackInfo()\n            $status = ([string]$info.PlaybackStatus).ToUpperInvariant()\n            $props = Await-Op ($session.TryGetMediaPropertiesAsync()) $propsType\n            $artist = ([string]$props.Artist) -replace \"[\\r\\n]\", ' '\n            $title = ([string]$props.Title) -replace \"[\\r\\n]\", ' '\n            $pos = '0'\n            $dur = '0'\n            try {\n                $tl = $session.GetTimelineProperties()\n                if ($null -ne $tl -and $tl.EndTime -gt $tl.StartTime) {\n                    $pos = [Math]::Max(0.0, $tl.Position.TotalSeconds).ToString('F1', $inv)\n                    $dur = ($tl.EndTime - $tl.StartTime).TotalSeconds.ToString('F1', $inv)\n                }\n            } catch { }\n            $shuffle = if ($info.ShuffleActive) { '1' } else { '0' }\n            $repeat = switch ([string]$info.RepeatMode) { 'None' { 'OFF' } 'Track' { 'ONE' } 'All' { 'ALL' } default { 'UNKNOWN' } }\n            $vol = [RiptideSpotifyVolume]::GetSessionVolume($hint)\n            $art = ''\n            if ($title.Length -gt 0) {\n                $key = $artist + '|' + $title\n                if ($key -ne $artKey) {\n                    $artKey = $key\n                    if (Save-Art $props $artPath) {\n                        $art = $artPath\n                    } elseif (Test-Path $artPath) {\n                        # A failed save must not leave the PREVIOUS track's art\n                        # behind for the elseif below to serve with the NEW track.\n                        Remove-Item $artPath -Force\n                    }\n                } elseif (Test-Path $artPath) {\n                    $art = $artPath\n                }\n            }\n            [Console]::WriteLine($status + $us + $artist + $us + $title + $us + $pos + $us + $dur + $us + $shuffle + $us + $repeat + $us + $vol + $us + $art)\n        }\n    } catch {\n        $manager = $null\n        $subscribedAumid = ''\n        [Console]::WriteLine('UNAVAILABLE')\n    }\n    }\n    if (-not $stdinDead -and $readTask.IsCompleted) {\n        try {\n            $line = $readTask.Result\n            if ($null -eq $line) {\n                $stdinDead = $true\n            } else {\n                $readTask = $stdinReader.ReadLineAsync()\n                $hintNow = if ($anyMedia -and $null -ne $session) { ([string]$session.SourceAppUserModelId).Split('!')[0] } else { 'spotify' }\n                Invoke-SpotifyCommand $session $hintNow $line\n            }\n        } catch {\n            $stdinDead = $true\n        }\n    }\n    Start-Sleep -Milliseconds 100\n}\n";
   private static final String OSASCRIPT_QUERY = "set US to (ASCII character 31)\nset spScript to \"set US to (ASCII character 31)\nif application \\\"Spotify\\\" is running then\n\ttell application \\\"Spotify\\\"\n\t\tset out to (player state as string) & US\n\t\ttry\n\t\t\tset out to out & (artist of current track) & US & (name of current track)\n\t\ton error\n\t\t\tset out to out & US\n\t\tend try\n\t\ttry\n\t\t\tset out to out & US & (((player position) * 1000) as integer) & US & ((duration of current track) as integer)\n\t\ton error\n\t\t\tset out to out & US & \\\"0\\\" & US & \\\"0\\\"\n\t\tend try\n\t\ttry\n\t\t\tset out to out & US & (shuffling as string) & US & (repeating as string) & US & (sound volume as string)\n\t\ton error\n\t\t\tset out to out & US & \\\"0\\\" & US & \\\"\\\" & US & \\\"-1\\\"\n\t\tend try\n\t\treturn out\n\tend tell\nend if\nreturn \\\"\\\"\"\nset muScript to \"set US to (ASCII character 31)\nif application \\\"Music\\\" is running then\n\ttell application \\\"Music\\\"\n\t\tset out to (player state as string) & US\n\t\ttry\n\t\t\tset out to out & (artist of current track) & US & (name of current track)\n\t\ton error\n\t\t\tset out to out & US\n\t\tend try\n\t\ttry\n\t\t\tset out to out & US & (((player position) * 1000) as integer) & US & (((duration of current track) * 1000) as integer)\n\t\ton error\n\t\t\tset out to out & US & \\\"0\\\" & US & \\\"0\\\"\n\t\tend try\n\t\ttry\n\t\t\tset rep to \\\"false\\\"\n\t\t\tif (song repeat as string) is not \\\"off\\\" then set rep to \\\"true\\\"\n\t\t\tset out to out & US & (shuffle enabled as string) & US & rep & US & (sound volume as string)\n\t\ton error\n\t\t\tset out to out & US & \\\"0\\\" & US & \\\"\\\" & US & \\\"-1\\\"\n\t\tend try\n\t\treturn out\n\tend tell\nend if\nreturn \\\"\\\"\"\nset spLine to \"\"\ntry\n\tset spLine to (run script spScript)\nend try\nset muLine to \"\"\ntry\n\tset muLine to (run script muScript)\nend try\nif spLine starts with \"playing\" then\n\treturn spLine\nelse if muLine starts with \"playing\" then\n\treturn muLine\nelse if spLine is not \"\" then\n\treturn spLine\nelse if muLine is not \"\" then\n\treturn muLine\nend if\nreturn \"\"\n";

   private RiptideSpotify() {
   }

   public static RiptideSpotify.Snapshot snapshot() {
      return current;
   }

   public static void setWanted() {
      long now = System.currentTimeMillis();
      lastWantedAtMs = now;

      try {
         ensureHooks();
         synchronized (LOCK) {
            if (worker != null) {
               return;
            }

            if (coolingDown && now - gaveUpAtMs < 60000L) {
               return;
            }

            coolingDown = false;
            consecutiveFailures = 0;
            startLocked();
         }
      } catch (Throwable var5) {
      }
   }

   public static void togglePlayPause() {
      command("PLAY_PAUSE");
   }

   public static void next() {
      command("NEXT");
   }

   public static void previous() {
      command("PREV");
   }

   public static void setShuffle(boolean on) {
      command(on ? "SHUFFLE_ON" : "SHUFFLE_OFF");
   }

   public static void cycleRepeat() {
      command("REPEAT=" + nextRepeat(snapshot().repeat()).name());
   }

   public static void setVolume(int percent) {
      command("VOLUME=" + clampVolume(percent));
   }

   public static void setSourceAnywhere(boolean anyMedia) {
      sourceAnywhere = anyMedia;
      command(anyMedia ? "SOURCE=ANY" : "SOURCE=SPOTIFY");
   }

   public static boolean sourceAnywhere() {
      return sourceAnywhere;
   }

   static RiptideSpotify.Repeat nextRepeat(RiptideSpotify.Repeat repeat) {
      if (repeat == null) {
         return RiptideSpotify.Repeat.OFF;
      } else {
         return switch (repeat) {
            case OFF -> RiptideSpotify.Repeat.ALL;
            case ALL -> RiptideSpotify.Repeat.ONE;
            default -> RiptideSpotify.Repeat.OFF;
         };
      }
   }

   static int clampVolume(int percent) {
      return Math.max(0, Math.min(100, percent));
   }

   private static void ensureHooks() {
      if (!hooksInstalled) {
         synchronized (LOCK) {
            if (!hooksInstalled) {
               hooksInstalled = true;
               Thread watchdog = new Thread(RiptideSpotify::watchdogLoop, "riptide-spotify-watchdog");
               watchdog.setDaemon(true);
               watchdog.start();
               Runtime.getRuntime().addShutdownHook(new Thread(RiptideSpotify::shutdownChild, "riptide-spotify-shutdown"));
               Thread artWorker = new Thread(RiptideSpotify::artWorkerLoop, "riptide-spotify-art");
               artWorker.setDaemon(true);
               artWorker.start();
            }
         }
      }
   }

   private static void watchdogLoop() {
      while (true) {
         try {
            Thread.sleep(2000L);
         } catch (InterruptedException var3) {
            Thread.currentThread().interrupt();
            return;
         }

         synchronized (LOCK) {
            if (worker != null && System.currentTimeMillis() - lastWantedAtMs > 15000L) {
               stopLocked();
            }
         }
      }
   }

   private static void shutdownChild() {
      synchronized (LOCK) {
         stopLocked();
      }
   }

   private static void stopLocked() {
      generation++;
      Process child = process;
      process = null;
      worker = null;
      if (child != null) {
         child.destroy();
      }

      current = new RiptideSpotify.Snapshot(
         RiptideSpotify.Status.UNAVAILABLE, "", "", System.currentTimeMillis(), 0.0, 0.0, false, RiptideSpotify.Repeat.UNKNOWN, -1, ""
      );
   }

   private static void startLocked() {
      RiptideSpotify.Backend backend = pickBackend();
      if (backend == null) {
         if (current.status() != RiptideSpotify.Status.UNAVAILABLE) {
            current = new RiptideSpotify.Snapshot(
               RiptideSpotify.Status.UNAVAILABLE, "", "", System.currentTimeMillis(), 0.0, 0.0, false, RiptideSpotify.Repeat.UNKNOWN, -1, ""
            );
         }
      } else {
         generation++;
         Thread thread = new Thread(() -> supervise(generation, backend), "riptide-spotify-backend");
         thread.setDaemon(true);
         worker = thread;
         thread.start();
      }
   }

   private static RiptideSpotify.Backend pickBackend() {
      OS os = Util.getPlatform();
      if (os == OS.WINDOWS) {
         return RiptideSpotify.Backend.WINDOWS_PS;
      } else if (os == OS.OSX) {
         return RiptideSpotify.Backend.OSX_OSA;
      } else {
         if (os == OS.LINUX) {
            if (toolAvailable("playerctl")) {
               return RiptideSpotify.Backend.PLAYERCTL;
            }

            if (toolAvailable("gdbus")) {
               return RiptideSpotify.Backend.GDBUS;
            }
         }

         return null;
      }
   }

   private static void supervise(int gen, RiptideSpotify.Backend backend) {
      try {
         switch (backend) {
            case GDBUS:
               gdbusLoop(gen);
               break;
            case OSX_OSA:
               osascriptLoop(gen);
               break;
            default:
               streamLoop(gen, backend);
         }
      } catch (Throwable var6) {
         synchronized (LOCK) {
            if (worker == Thread.currentThread()) {
               worker = null;
               coolingDown = true;
               gaveUpAtMs = System.currentTimeMillis();
               current = new RiptideSpotify.Snapshot(
                  RiptideSpotify.Status.UNAVAILABLE, "", "", System.currentTimeMillis(), 0.0, 0.0, false, RiptideSpotify.Repeat.UNKNOWN, -1, ""
               );
            }
         }
      }
   }

   private static void streamLoop(int gen, RiptideSpotify.Backend backend) {
      boolean respawning = false;

      while (true) {
         if (respawning) {
            try {
               Thread.sleep(5000L);
            } catch (InterruptedException var10) {
               Thread.currentThread().interrupt();
               return;
            }
         }

         synchronized (LOCK) {
            if (gen != generation) {
               return;
            }
         }

         Process child = spawn(backend);
         if (child == null) {
            if (!registerFailure(gen, 0L)) {
               return;
            }

            respawning = true;
         } else {
            long startedAt = System.currentTimeMillis();
            synchronized (LOCK) {
               if (gen != generation) {
                  child.destroy();
                  return;
               }

               process = child;
            }

            readLines(gen, child, backend);
            long aliveMs = System.currentTimeMillis() - startedAt;
            child.destroy();
            synchronized (LOCK) {
               if (process == child) {
                  process = null;
               }
            }

            if (!registerFailure(gen, aliveMs)) {
               return;
            }

            respawning = true;
         }
      }
   }

   private static boolean registerFailure(int gen, long aliveMs) {
      synchronized (LOCK) {
         if (gen != generation) {
            return false;
         } else {
            consecutiveFailures = aliveMs >= 30000L ? 0 : consecutiveFailures + 1;
            if (consecutiveFailures >= 3) {
               coolingDown = true;
               gaveUpAtMs = System.currentTimeMillis();
               worker = null;
               current = new RiptideSpotify.Snapshot(
                  RiptideSpotify.Status.UNAVAILABLE, "", "", gaveUpAtMs, 0.0, 0.0, false, RiptideSpotify.Repeat.UNKNOWN, -1, ""
               );
               return false;
            } else {
               return true;
            }
         }
      }
   }

   private static Process spawn(RiptideSpotify.Backend backend) {
      try {
         ProcessBuilder builder = switch (backend) {
            case WINDOWS_PS -> new ProcessBuilder(
               "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", windowsScript().toString()
            );
            case PLAYERCTL -> playerctlCommand();
            case GDBUS, OSX_OSA -> throw new IllegalStateException("polled backends have no streaming process");
         };
         if (backend == RiptideSpotify.Backend.WINDOWS_PS) {
            builder.environment().put("RIPTIDE_SPOTIFY_SOURCE", sourceAnywhere ? "ANY" : "SPOTIFY");
         }

         return builder.redirectError(Redirect.DISCARD).start();
      } catch (Exception var2) {
         return null;
      }
   }

   private static ProcessBuilder playerctlCommand() {
      return new ProcessBuilder(playerctlArgv());
   }

   static List<String> playerctlArgv() {
      List<String> argv = new ArrayList<>();
      argv.add("playerctl");
      if (!sourceAnywhere) {
         argv.add("--player=spotify");
      }

      argv.addAll(
         List.of(
            "--follow",
            "metadata",
            "--format",
            "{{status}}\u001f{{artist}}\u001f{{title}}\u001f{{position}}\u001f{{mpris:length}}\u001f{{shuffle}}\u001f{{loop}}\u001f{{volume}}\u001f{{mpris:artUrl}}"
         )
      );
      return argv;
   }

   static String windowsScriptText() {
      return "$ErrorActionPreference = 'Continue'\n[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\nAdd-Type -AssemblyName System.Runtime.WindowsRuntime\n\n$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]\nfunction Await-Op($op, $resultType) {\n    $task = $asTaskGeneric.MakeGenericMethod($resultType).Invoke($null, @($op))\n    if (-not $task.Wait(5000)) { throw 'timed out waiting for a WinRT operation' }\n    return $task.Result\n}\n\n$code = @'\nusing System;\nusing System.Collections.Generic;\nusing System.Diagnostics;\nusing System.Runtime.InteropServices;\n\n// ISimpleAudioVolume get/set for the session belonging to a process-name hint.\n// Raw vtable throughout: on machines with an audio enhancement driver (probed on\n// this one), the session ENUMERATOR object answers QueryInterface with\n// E_NOINTERFACE for interfaces its vtable actually implements, so the typed RCW\n// path cannot reach it; the vtable contracts are stable, so calling slots\n// directly works on wrapped and healthy machines alike.\npublic static class RiptideSpotifyVolume {\n    [ComImport, Guid(\"A95664D2-9614-4F35-A746-DE8DB63617E6\"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]\n    public interface IMMDeviceEnumerator {\n        [PreserveSig] int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);\n        [PreserveSig] int GetDefaultAudioEndpoint(int dataFlow, int role, out IntPtr device);\n        [PreserveSig] int GetDevice([MarshalAs(UnmanagedType.LPWStr)] string id, out IntPtr device);\n        [PreserveSig] int RegisterEndpointNotificationCallback(IntPtr client);\n        [PreserveSig] int UnregisterEndpointNotificationCallback(IntPtr client);\n    }\n\n    [ComImport, Guid(\"BCDE0395-E52F-467C-8E3D-C4579291692E\")]\n    public class MMDeviceEnumeratorComObject { }\n\n    static readonly Guid IID_MGR2 = new Guid(\"77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F\");\n    static readonly Guid IID_VOLUME = new Guid(\"87CE5498-68D6-44E5-9215-6DA47EF883D8\");\n\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComOutOut(IntPtr t, out IntPtr v);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComGetCount(IntPtr t, out int v);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComItem(IntPtr t, int i, out IntPtr v);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComActivate(IntPtr t, [MarshalAs(UnmanagedType.LPStruct)] Guid iid, int c, IntPtr p, out IntPtr v);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComGetPid(IntPtr t, out uint pid);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComGetVolume(IntPtr t, out float level);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComSetVolume(IntPtr t, float level, IntPtr ctx);\n\n    static IntPtr Slot(IntPtr obj, int slot) {\n        return Marshal.ReadIntPtr(Marshal.ReadIntPtr(obj), slot * IntPtr.Size);\n    }\n\n    // Album art, take four. Every earlier read path failed on this box, all\n    // probed: PowerShell's binder cannot bind the WinRT AsStream extension, a\n    // winmd reference for compile-time WinRT types fails to load (0x80131047),\n    // the returned stream's RCW exposes no interfaces, and vtable reads against a\n    // NATIVE buffer (IBufferFactory) either lie (hollow S_OK) or segfault the\n    // helper (exit 139). So the read buffer is a .NET-BACKED one:\n    // System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBuffer wraps a\n    // managed byte[] as an IBuffer with a CLR-generated, guaranteed-valid vtable.\n    // The only native vtable call left is IInputStream.ReadAsync (slot 6), proven\n    // safe here. Bytes come back through WindowsRuntimeBufferExtensions.ToArray,\n    // purely managed. Crash containment: the MTA worker (apartment sensitivity,\n    // probed) plus HandleProcessCorruptedStateExceptions, and an all-zero guard -\n    // on this stack the read returns S_OK with a NULL op and a zero-filled buffer\n    // (hollow success), which must read as failure, never as a 4 MB black image.\n    [System.Runtime.ExceptionServices.HandleProcessCorruptedStateExceptions]\n    static byte[] ReadArtStreamMta(object streamObj) {\n        IntPtr streamPtr = IntPtr.Zero, istream = IntPtr.Zero, bufPtr = IntPtr.Zero, op = IntPtr.Zero;\n        try {\n            System.Reflection.Assembly wrtAsm = null;\n            foreach (System.Reflection.Assembly asm in AppDomain.CurrentDomain.GetAssemblies()) {\n                if (asm.GetName().Name == \"System.Runtime.WindowsRuntime\") { wrtAsm = asm; break; }\n            }\n            if (wrtAsm == null) return null;\n            Type wrb = wrtAsm.GetType(\"System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBuffer\");\n            Type ext = wrtAsm.GetType(\"System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBufferExtensions\");\n            if (wrb == null || ext == null) return null;\n            System.Reflection.MethodInfo create = null, toArray = null;\n            foreach (System.Reflection.MethodInfo m in wrb.GetMethods()) {\n                if (m.Name == \"Create\" && m.GetParameters().Length == 4) { create = m; break; }\n            }\n            foreach (System.Reflection.MethodInfo m in ext.GetMethods()) {\n                if (m.Name == \"ToArray\" && m.GetParameters().Length == 1) { toArray = m; break; }\n            }\n            if (create == null || toArray == null) return null;\n            byte[] backing = new byte[4 * 1024 * 1024];\n            object buffer = create.Invoke(null, new object[] { backing, 0, backing.Length, backing.Length });\n            Guid iidStream = new Guid(\"905a0fe1-bc53-11df-8c49-001e4fc686da\");\n            streamPtr = Marshal.GetIUnknownForObject(streamObj);\n            if (Marshal.QueryInterface(streamPtr, ref iidStream, out istream) < 0 || istream == IntPtr.Zero) return null;\n            bufPtr = Marshal.GetIUnknownForObject(buffer);\n            int hrRead = Marshal.GetDelegateForFunctionPointer<ComReadAsync>(Slot(istream, 6))(istream, bufPtr, (uint)backing.Length, 0u, out op);\n            if (hrRead < 0) return null;\n            if (op != IntPtr.Zero) { // async op exists: wait it out; a null op means the fill was synchronous\n                int status = 0;\n                long deadline = DateTime.UtcNow.Ticks + 5L * 10000000L;\n                while (status == 0 && DateTime.UtcNow.Ticks < deadline) {\n                    System.Threading.Thread.Sleep(10);\n                    if (Marshal.GetDelegateForFunctionPointer<ComGetCount>(Slot(op, 7))(op, out status) < 0) return null;\n                }\n                if (status != 1) return null;\n            }\n            byte[] result = (byte[])toArray.Invoke(null, new object[] { buffer });\n            // The hollow-success guard: a hollow read leaves the wrapper at its\n            // initial all-zero content; real album art always has non-zero bytes.\n            bool anyNonZero = false;\n            for (int i = 0; i < result.Length; i++) {\n                if (result[i] != 0) { anyNonZero = true; break; }\n            }\n            return anyNonZero ? result : null;\n        } catch (System.Exception) {\n            return null;\n        } finally {\n            if (op != IntPtr.Zero) Marshal.Release(op);\n            if (bufPtr != IntPtr.Zero) Marshal.Release(bufPtr);\n            if (istream != IntPtr.Zero) Marshal.Release(istream);\n            if (streamPtr != IntPtr.Zero) Marshal.Release(streamPtr);\n        }\n    }\n\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComReadAsync(IntPtr t, IntPtr buffer, uint count, uint options, out IntPtr op);\n\n    static HashSet<int> hintPids = new HashSet<int>();\n    static string hintCached = \"\";\n    static DateTime hintRefreshed = DateTime.MinValue;\n\n    // Process-name matching by either-way containment: \"spotify\" hits Spotify.exe,\n    // and a packaged AUMID head like \"SpotifyAB.SpotifyMusic_xxx\" still contains it.\n    static HashSet<int> PidsForHint(string appHint) {\n        if (appHint != hintCached || (DateTime.UtcNow - hintRefreshed).TotalSeconds > 2.0) {\n            HashSet<int> set = new HashSet<int>();\n            string hint = (appHint ?? \"\").ToLowerInvariant();\n            if (hint.Length > 0) {\n                foreach (Process p in Process.GetProcesses()) {\n                    try {\n                        string n = p.ProcessName.ToLowerInvariant();\n                        if (hint.Contains(n) || n.Contains(hint)) set.Add(p.Id);\n                    } catch { }\n                }\n            }\n            hintPids = set;\n            hintCached = appHint ?? \"\";\n            hintRefreshed = DateTime.UtcNow;\n        }\n        return hintPids;\n    }\n\n    public static int GetSessionVolume(string appHint) {\n        IntPtr volume = FindVolume(appHint);\n        if (volume == IntPtr.Zero) return -1;\n        try {\n            float v;\n            if (Marshal.GetDelegateForFunctionPointer<ComGetVolume>(Slot(volume, 4))(volume, out v) < 0) return -1;\n            return (int)Math.Round(v * 100.0f);\n        } finally {\n            Marshal.Release(volume);\n        }\n    }\n\n    public static bool SetSessionVolume(string appHint, double fraction) {\n        IntPtr volume = FindVolume(appHint);\n        if (volume == IntPtr.Zero) return false;\n        try {\n            return Marshal.GetDelegateForFunctionPointer<ComSetVolume>(Slot(volume, 3))(volume, (float)fraction, IntPtr.Zero) >= 0;\n        } finally {\n            Marshal.Release(volume);\n        }\n    }\n\n    static IntPtr FindVolume(string appHint) {\n        HashSet<int> pids = PidsForHint(appHint);\n        if (pids.Count == 0) return IntPtr.Zero;\n        IMMDeviceEnumerator enumerator = (IMMDeviceEnumerator)new MMDeviceEnumeratorComObject();\n        IntPtr collection;\n        if (enumerator.EnumAudioEndpoints(0, 1, out collection) < 0) return IntPtr.Zero;\n        try {\n            int deviceCount;\n            if (Marshal.GetDelegateForFunctionPointer<ComGetCount>(Slot(collection, 3))(collection, out deviceCount) < 0) return IntPtr.Zero;\n            for (int d = 0; d < deviceCount; d++) {\n                IntPtr device;\n                if (Marshal.GetDelegateForFunctionPointer<ComItem>(Slot(collection, 4))(collection, d, out device) < 0) continue;\n                if (device == IntPtr.Zero) continue;\n                try {\n                    IntPtr manager;\n                    if (Marshal.GetDelegateForFunctionPointer<ComActivate>(Slot(device, 3))(device, IID_MGR2, 23, IntPtr.Zero, out manager) < 0) continue;\n                    if (manager == IntPtr.Zero) continue;\n                    try {\n                        IntPtr enumPtr;\n                        if (Marshal.GetDelegateForFunctionPointer<ComOutOut>(Slot(manager, 5))(manager, out enumPtr) < 0) continue;\n                        if (enumPtr == IntPtr.Zero) continue;\n                        try {\n                            int count;\n                            if (Marshal.GetDelegateForFunctionPointer<ComGetCount>(Slot(enumPtr, 3))(enumPtr, out count) < 0) continue;\n                            for (int i = 0; i < count; i++) {\n                                IntPtr sessionPtr;\n                                if (Marshal.GetDelegateForFunctionPointer<ComItem>(Slot(enumPtr, 4))(enumPtr, i, out sessionPtr) < 0) continue;\n                                if (sessionPtr == IntPtr.Zero) continue;\n                                try {\n                                    uint sessionPid;\n                                    if (Marshal.GetDelegateForFunctionPointer<ComGetPid>(Slot(sessionPtr, 14))(sessionPtr, out sessionPid) < 0) continue;\n                                    if (!pids.Contains((int)sessionPid)) continue;\n                                    Guid iid = IID_VOLUME;\n                                    IntPtr volumePtr;\n                                    if (Marshal.QueryInterface(sessionPtr, ref iid, out volumePtr) < 0) continue;\n                                    return volumePtr; // caller releases\n                                } finally {\n                                    Marshal.Release(sessionPtr);\n                                }\n                            }\n                        } finally {\n                            Marshal.Release(enumPtr);\n                        }\n                    } finally {\n                        Marshal.Release(manager);\n                    }\n                } finally {\n                    Marshal.Release(device);\n                }\n            }\n            return IntPtr.Zero;\n        } finally {\n            Marshal.Release(collection);\n        }\n    }\n}\n'@\nAdd-Type -TypeDefinition $code\n\n$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]\n$propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime]\n$boolType = [bool]\n$us = [char]31\n$inv = [Globalization.CultureInfo]::InvariantCulture\n$manager = $null\n$artKey = ''\n$artPath = Join-Path $env:TEMP 'riptide_spotify_art.png'\n$anyMedia = $env:RIPTIDE_SPOTIFY_SOURCE -eq 'ANY'\n# Stdin commands are read through a raw StreamReader over the standard input,\n# NOT [Console]::In.ReadLineAsync: on .NET Framework that call takes the console\n# sync root while it blocks on an EMPTY anonymous pipe (exactly what Java's\n# ProcessBuilder hands us - probed: the helper froze silently after 'before\n# readline' with zero output). StreamReader.ReadLineAsync runs on a pool thread\n# with no console lock, so the status loop never stalls on an empty pipe.\n$stdinReader = New-Object System.IO.StreamReader([Console]::OpenStandardInput())\n$readTask = $stdinReader.ReadLineAsync()\n$stdinDead = $false\n# Event-driven refresh: MediaPropertiesChanged (track skip) and\n# PlaybackInfoChanged (play/pause) set a GLOBAL dirty flag (global, not script\n# scope, because the handler executes off the main runspace thread). The loop\n# below re-reads immediately on dirty, otherwise every 450 ms. NOTE: on this box\n# GSMTC property-change events register fine but NEVER fire (probed: two real\n# skips, zero firings - the same stack that hollows art streams and returns null\n# RepeatMode), so the event path is inert here and the 450 ms poll is what\n# actually delivers faster updates: worst-case staleness is halved.\n$global:spotifyDirty = $false\n$subscribedAumid = ''\n$lastPoll = [DateTime]::UtcNow.AddSeconds(-1)\n\nfunction Save-Art($props, $path) {\n    try {\n        if ($null -eq $props.Thumbnail) { return $false }\n        $stream = Await-Op ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])\n        if ($null -eq $stream) { return $false }\n        $bytes = [RiptideSpotifyVolume]::ReadArtStream($stream)\n        if ($null -eq $bytes) { return $false }\n        # Atomic-ish publish: write a temp sibling, then Copy over the stable path\n        # and delete the temp. [IO.File]::Move(source, dest, overwrite) does NOT\n        # exist on .NET Framework 4.x (only .NET Core 3.0+), so Copy+Delete is the\n        # safe form here - a render-thread read never sees a torn image.\n        $tmp = $path + '.part'\n        [System.IO.File]::WriteAllBytes($tmp, $bytes)\n        [System.IO.File]::Copy($tmp, $path, $true)\n        Remove-Item $tmp -Force\n        return $true\n    } catch {\n        return $false\n    }\n}\n\nfunction Invoke-SpotifyCommand($session, $hint, $line) {\n    if ($line -eq 'PLAY_PAUSE') {\n        if ($null -ne $session) { try { Await-Op ($session.TryTogglePlayPauseAsync()) $boolType | Out-Null } catch { } }\n    } elseif ($line -eq 'NEXT') {\n        if ($null -ne $session) { try { Await-Op ($session.TrySkipNextAsync()) $boolType | Out-Null } catch { } }\n    } elseif ($line -eq 'PREV') {\n        if ($null -ne $session) { try { Await-Op ($session.TrySkipPreviousAsync()) $boolType | Out-Null } catch { } }\n    } elseif ($line -eq 'SHUFFLE_ON') {\n        if ($null -ne $session) { try { Await-Op ($session.TryChangeShuffleActiveAsync($true)) $boolType | Out-Null } catch { } }\n    } elseif ($line -eq 'SHUFFLE_OFF') {\n        if ($null -ne $session) { try { Await-Op ($session.TryChangeShuffleActiveAsync($false)) $boolType | Out-Null } catch { } }\n    } elseif ($line -eq 'SOURCE=ANY') {\n        $script:anyMedia = $true\n    } elseif ($line -eq 'SOURCE=SPOTIFY') {\n        $script:anyMedia = $false\n    } elseif ($line -match '^REPEAT=(OFF|ALL|ONE)$') {\n        if ($null -ne $session) {\n            # WinRT enum values passed as their underlying int (None=0, Track=1,\n            # All=2): on some boxes the repeat-mode enum type cannot be projected\n            # into PowerShell at all (probed: [type] load fails and the binder then\n            # hides the method). There the call simply no-ops inside this catch;\n            # where the enum projects, the conversion binds and the command works.\n            $modeInt = switch ($Matches[1]) { 'OFF' { 0 } 'ALL' { 2 } 'ONE' { 1 } }\n            try { Await-Op ($session.TryChangeRepeatModeAsync($modeInt)) $boolType | Out-Null } catch { }\n        }\n    } elseif ($line -match '^VOLUME=(\\d+)$') {\n        [RiptideSpotifyVolume]::SetSessionVolume($hint, [double]$Matches[1] / 100.0) | Out-Null\n    }\n}\n\nwhile ($true) {\n    $elapsed = ([DateTime]::UtcNow - $lastPoll).TotalMilliseconds\n    if ($global:spotifyDirty -or $elapsed -ge 450) {\n        $global:spotifyDirty = $false\n        $lastPoll = [DateTime]::UtcNow\n    try {\n        if ($null -eq $manager) {\n            $subscribedAumid = ''\n            $manager = Await-Op ($managerType::RequestAsync()) $managerType\n        }\n        $session = $null\n        if ($anyMedia) {\n            $session = $manager.GetCurrentSession()\n        } else {\n            foreach ($candidate in $manager.GetSessions()) {\n                if ($candidate.SourceAppUserModelId -match 'spotify') { $session = $candidate; break }\n            }\n        }\n        if ($null -ne $session -and ([string]$session.SourceAppUserModelId) -ne $subscribedAumid) {\n            # Re-subscribe keyed on the AUMID STRING, not RCW object identity:\n            # if the projection ever yields a fresh RCW for the same session, an\n            # object-reference comparison would re-register handlers every poll.\n            try {\n                $null = $session.add_MediaPropertiesChanged({ $global:spotifyDirty = $true })\n                $null = $session.add_PlaybackInfoChanged({ $global:spotifyDirty = $true })\n                $subscribedAumid = [string]$session.SourceAppUserModelId\n            } catch { }\n        }\n        if ($null -eq $session) {\n            [Console]::WriteLine('STOPPED')\n        } else {\n            $hint = if ($anyMedia) { ([string]$session.SourceAppUserModelId).Split('!')[0] } else { 'spotify' }\n            $info = $session.GetPlaybackInfo()\n            $status = ([string]$info.PlaybackStatus).ToUpperInvariant()\n            $props = Await-Op ($session.TryGetMediaPropertiesAsync()) $propsType\n            $artist = ([string]$props.Artist) -replace \"[\\r\\n]\", ' '\n            $title = ([string]$props.Title) -replace \"[\\r\\n]\", ' '\n            $pos = '0'\n            $dur = '0'\n            try {\n                $tl = $session.GetTimelineProperties()\n                if ($null -ne $tl -and $tl.EndTime -gt $tl.StartTime) {\n                    $pos = [Math]::Max(0.0, $tl.Position.TotalSeconds).ToString('F1', $inv)\n                    $dur = ($tl.EndTime - $tl.StartTime).TotalSeconds.ToString('F1', $inv)\n                }\n            } catch { }\n            $shuffle = if ($info.ShuffleActive) { '1' } else { '0' }\n            $repeat = switch ([string]$info.RepeatMode) { 'None' { 'OFF' } 'Track' { 'ONE' } 'All' { 'ALL' } default { 'UNKNOWN' } }\n            $vol = [RiptideSpotifyVolume]::GetSessionVolume($hint)\n            $art = ''\n            if ($title.Length -gt 0) {\n                $key = $artist + '|' + $title\n                if ($key -ne $artKey) {\n                    $artKey = $key\n                    if (Save-Art $props $artPath) {\n                        $art = $artPath\n                    } elseif (Test-Path $artPath) {\n                        # A failed save must not leave the PREVIOUS track's art\n                        # behind for the elseif below to serve with the NEW track.\n                        Remove-Item $artPath -Force\n                    }\n                } elseif (Test-Path $artPath) {\n                    $art = $artPath\n                }\n            }\n            [Console]::WriteLine($status + $us + $artist + $us + $title + $us + $pos + $us + $dur + $us + $shuffle + $us + $repeat + $us + $vol + $us + $art)\n        }\n    } catch {\n        $manager = $null\n        $subscribedAumid = ''\n        [Console]::WriteLine('UNAVAILABLE')\n    }\n    }\n    if (-not $stdinDead -and $readTask.IsCompleted) {\n        try {\n            $line = $readTask.Result\n            if ($null -eq $line) {\n                $stdinDead = $true\n            } else {\n                $readTask = $stdinReader.ReadLineAsync()\n                $hintNow = if ($anyMedia -and $null -ne $session) { ([string]$session.SourceAppUserModelId).Split('!')[0] } else { 'spotify' }\n                Invoke-SpotifyCommand $session $hintNow $line\n            }\n        } catch {\n            $stdinDead = $true\n        }\n    }\n    Start-Sleep -Milliseconds 100\n}\n";
   }

   private static Path windowsScript() throws IOException {
      Path existing = windowsScriptPath;
      if (existing != null) {
         return existing;
      } else {
         synchronized (LOCK) {
            if (windowsScriptPath == null) {
               Path file = Files.createTempFile("riptide_spotify", ".ps1");
               Files.writeString(
                  file,
                  "$ErrorActionPreference = 'Continue'\n[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\nAdd-Type -AssemblyName System.Runtime.WindowsRuntime\n\n$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]\nfunction Await-Op($op, $resultType) {\n    $task = $asTaskGeneric.MakeGenericMethod($resultType).Invoke($null, @($op))\n    if (-not $task.Wait(5000)) { throw 'timed out waiting for a WinRT operation' }\n    return $task.Result\n}\n\n$code = @'\nusing System;\nusing System.Collections.Generic;\nusing System.Diagnostics;\nusing System.Runtime.InteropServices;\n\n// ISimpleAudioVolume get/set for the session belonging to a process-name hint.\n// Raw vtable throughout: on machines with an audio enhancement driver (probed on\n// this one), the session ENUMERATOR object answers QueryInterface with\n// E_NOINTERFACE for interfaces its vtable actually implements, so the typed RCW\n// path cannot reach it; the vtable contracts are stable, so calling slots\n// directly works on wrapped and healthy machines alike.\npublic static class RiptideSpotifyVolume {\n    [ComImport, Guid(\"A95664D2-9614-4F35-A746-DE8DB63617E6\"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]\n    public interface IMMDeviceEnumerator {\n        [PreserveSig] int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);\n        [PreserveSig] int GetDefaultAudioEndpoint(int dataFlow, int role, out IntPtr device);\n        [PreserveSig] int GetDevice([MarshalAs(UnmanagedType.LPWStr)] string id, out IntPtr device);\n        [PreserveSig] int RegisterEndpointNotificationCallback(IntPtr client);\n        [PreserveSig] int UnregisterEndpointNotificationCallback(IntPtr client);\n    }\n\n    [ComImport, Guid(\"BCDE0395-E52F-467C-8E3D-C4579291692E\")]\n    public class MMDeviceEnumeratorComObject { }\n\n    static readonly Guid IID_MGR2 = new Guid(\"77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F\");\n    static readonly Guid IID_VOLUME = new Guid(\"87CE5498-68D6-44E5-9215-6DA47EF883D8\");\n\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComOutOut(IntPtr t, out IntPtr v);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComGetCount(IntPtr t, out int v);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComItem(IntPtr t, int i, out IntPtr v);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComActivate(IntPtr t, [MarshalAs(UnmanagedType.LPStruct)] Guid iid, int c, IntPtr p, out IntPtr v);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComGetPid(IntPtr t, out uint pid);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComGetVolume(IntPtr t, out float level);\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComSetVolume(IntPtr t, float level, IntPtr ctx);\n\n    static IntPtr Slot(IntPtr obj, int slot) {\n        return Marshal.ReadIntPtr(Marshal.ReadIntPtr(obj), slot * IntPtr.Size);\n    }\n\n    // Album art, take four. Every earlier read path failed on this box, all\n    // probed: PowerShell's binder cannot bind the WinRT AsStream extension, a\n    // winmd reference for compile-time WinRT types fails to load (0x80131047),\n    // the returned stream's RCW exposes no interfaces, and vtable reads against a\n    // NATIVE buffer (IBufferFactory) either lie (hollow S_OK) or segfault the\n    // helper (exit 139). So the read buffer is a .NET-BACKED one:\n    // System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBuffer wraps a\n    // managed byte[] as an IBuffer with a CLR-generated, guaranteed-valid vtable.\n    // The only native vtable call left is IInputStream.ReadAsync (slot 6), proven\n    // safe here. Bytes come back through WindowsRuntimeBufferExtensions.ToArray,\n    // purely managed. Crash containment: the MTA worker (apartment sensitivity,\n    // probed) plus HandleProcessCorruptedStateExceptions, and an all-zero guard -\n    // on this stack the read returns S_OK with a NULL op and a zero-filled buffer\n    // (hollow success), which must read as failure, never as a 4 MB black image.\n    [System.Runtime.ExceptionServices.HandleProcessCorruptedStateExceptions]\n    static byte[] ReadArtStreamMta(object streamObj) {\n        IntPtr streamPtr = IntPtr.Zero, istream = IntPtr.Zero, bufPtr = IntPtr.Zero, op = IntPtr.Zero;\n        try {\n            System.Reflection.Assembly wrtAsm = null;\n            foreach (System.Reflection.Assembly asm in AppDomain.CurrentDomain.GetAssemblies()) {\n                if (asm.GetName().Name == \"System.Runtime.WindowsRuntime\") { wrtAsm = asm; break; }\n            }\n            if (wrtAsm == null) return null;\n            Type wrb = wrtAsm.GetType(\"System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBuffer\");\n            Type ext = wrtAsm.GetType(\"System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBufferExtensions\");\n            if (wrb == null || ext == null) return null;\n            System.Reflection.MethodInfo create = null, toArray = null;\n            foreach (System.Reflection.MethodInfo m in wrb.GetMethods()) {\n                if (m.Name == \"Create\" && m.GetParameters().Length == 4) { create = m; break; }\n            }\n            foreach (System.Reflection.MethodInfo m in ext.GetMethods()) {\n                if (m.Name == \"ToArray\" && m.GetParameters().Length == 1) { toArray = m; break; }\n            }\n            if (create == null || toArray == null) return null;\n            byte[] backing = new byte[4 * 1024 * 1024];\n            object buffer = create.Invoke(null, new object[] { backing, 0, backing.Length, backing.Length });\n            Guid iidStream = new Guid(\"905a0fe1-bc53-11df-8c49-001e4fc686da\");\n            streamPtr = Marshal.GetIUnknownForObject(streamObj);\n            if (Marshal.QueryInterface(streamPtr, ref iidStream, out istream) < 0 || istream == IntPtr.Zero) return null;\n            bufPtr = Marshal.GetIUnknownForObject(buffer);\n            int hrRead = Marshal.GetDelegateForFunctionPointer<ComReadAsync>(Slot(istream, 6))(istream, bufPtr, (uint)backing.Length, 0u, out op);\n            if (hrRead < 0) return null;\n            if (op != IntPtr.Zero) { // async op exists: wait it out; a null op means the fill was synchronous\n                int status = 0;\n                long deadline = DateTime.UtcNow.Ticks + 5L * 10000000L;\n                while (status == 0 && DateTime.UtcNow.Ticks < deadline) {\n                    System.Threading.Thread.Sleep(10);\n                    if (Marshal.GetDelegateForFunctionPointer<ComGetCount>(Slot(op, 7))(op, out status) < 0) return null;\n                }\n                if (status != 1) return null;\n            }\n            byte[] result = (byte[])toArray.Invoke(null, new object[] { buffer });\n            // The hollow-success guard: a hollow read leaves the wrapper at its\n            // initial all-zero content; real album art always has non-zero bytes.\n            bool anyNonZero = false;\n            for (int i = 0; i < result.Length; i++) {\n                if (result[i] != 0) { anyNonZero = true; break; }\n            }\n            return anyNonZero ? result : null;\n        } catch (System.Exception) {\n            return null;\n        } finally {\n            if (op != IntPtr.Zero) Marshal.Release(op);\n            if (bufPtr != IntPtr.Zero) Marshal.Release(bufPtr);\n            if (istream != IntPtr.Zero) Marshal.Release(istream);\n            if (streamPtr != IntPtr.Zero) Marshal.Release(streamPtr);\n        }\n    }\n\n    [UnmanagedFunctionPointer(CallingConvention.StdCall)]\n    delegate int ComReadAsync(IntPtr t, IntPtr buffer, uint count, uint options, out IntPtr op);\n\n    static HashSet<int> hintPids = new HashSet<int>();\n    static string hintCached = \"\";\n    static DateTime hintRefreshed = DateTime.MinValue;\n\n    // Process-name matching by either-way containment: \"spotify\" hits Spotify.exe,\n    // and a packaged AUMID head like \"SpotifyAB.SpotifyMusic_xxx\" still contains it.\n    static HashSet<int> PidsForHint(string appHint) {\n        if (appHint != hintCached || (DateTime.UtcNow - hintRefreshed).TotalSeconds > 2.0) {\n            HashSet<int> set = new HashSet<int>();\n            string hint = (appHint ?? \"\").ToLowerInvariant();\n            if (hint.Length > 0) {\n                foreach (Process p in Process.GetProcesses()) {\n                    try {\n                        string n = p.ProcessName.ToLowerInvariant();\n                        if (hint.Contains(n) || n.Contains(hint)) set.Add(p.Id);\n                    } catch { }\n                }\n            }\n            hintPids = set;\n            hintCached = appHint ?? \"\";\n            hintRefreshed = DateTime.UtcNow;\n        }\n        return hintPids;\n    }\n\n    public static int GetSessionVolume(string appHint) {\n        IntPtr volume = FindVolume(appHint);\n        if (volume == IntPtr.Zero) return -1;\n        try {\n            float v;\n            if (Marshal.GetDelegateForFunctionPointer<ComGetVolume>(Slot(volume, 4))(volume, out v) < 0) return -1;\n            return (int)Math.Round(v * 100.0f);\n        } finally {\n            Marshal.Release(volume);\n        }\n    }\n\n    public static bool SetSessionVolume(string appHint, double fraction) {\n        IntPtr volume = FindVolume(appHint);\n        if (volume == IntPtr.Zero) return false;\n        try {\n            return Marshal.GetDelegateForFunctionPointer<ComSetVolume>(Slot(volume, 3))(volume, (float)fraction, IntPtr.Zero) >= 0;\n        } finally {\n            Marshal.Release(volume);\n        }\n    }\n\n    static IntPtr FindVolume(string appHint) {\n        HashSet<int> pids = PidsForHint(appHint);\n        if (pids.Count == 0) return IntPtr.Zero;\n        IMMDeviceEnumerator enumerator = (IMMDeviceEnumerator)new MMDeviceEnumeratorComObject();\n        IntPtr collection;\n        if (enumerator.EnumAudioEndpoints(0, 1, out collection) < 0) return IntPtr.Zero;\n        try {\n            int deviceCount;\n            if (Marshal.GetDelegateForFunctionPointer<ComGetCount>(Slot(collection, 3))(collection, out deviceCount) < 0) return IntPtr.Zero;\n            for (int d = 0; d < deviceCount; d++) {\n                IntPtr device;\n                if (Marshal.GetDelegateForFunctionPointer<ComItem>(Slot(collection, 4))(collection, d, out device) < 0) continue;\n                if (device == IntPtr.Zero) continue;\n                try {\n                    IntPtr manager;\n                    if (Marshal.GetDelegateForFunctionPointer<ComActivate>(Slot(device, 3))(device, IID_MGR2, 23, IntPtr.Zero, out manager) < 0) continue;\n                    if (manager == IntPtr.Zero) continue;\n                    try {\n                        IntPtr enumPtr;\n                        if (Marshal.GetDelegateForFunctionPointer<ComOutOut>(Slot(manager, 5))(manager, out enumPtr) < 0) continue;\n                        if (enumPtr == IntPtr.Zero) continue;\n                        try {\n                            int count;\n                            if (Marshal.GetDelegateForFunctionPointer<ComGetCount>(Slot(enumPtr, 3))(enumPtr, out count) < 0) continue;\n                            for (int i = 0; i < count; i++) {\n                                IntPtr sessionPtr;\n                                if (Marshal.GetDelegateForFunctionPointer<ComItem>(Slot(enumPtr, 4))(enumPtr, i, out sessionPtr) < 0) continue;\n                                if (sessionPtr == IntPtr.Zero) continue;\n                                try {\n                                    uint sessionPid;\n                                    if (Marshal.GetDelegateForFunctionPointer<ComGetPid>(Slot(sessionPtr, 14))(sessionPtr, out sessionPid) < 0) continue;\n                                    if (!pids.Contains((int)sessionPid)) continue;\n                                    Guid iid = IID_VOLUME;\n                                    IntPtr volumePtr;\n                                    if (Marshal.QueryInterface(sessionPtr, ref iid, out volumePtr) < 0) continue;\n                                    return volumePtr; // caller releases\n                                } finally {\n                                    Marshal.Release(sessionPtr);\n                                }\n                            }\n                        } finally {\n                            Marshal.Release(enumPtr);\n                        }\n                    } finally {\n                        Marshal.Release(manager);\n                    }\n                } finally {\n                    Marshal.Release(device);\n                }\n            }\n            return IntPtr.Zero;\n        } finally {\n            Marshal.Release(collection);\n        }\n    }\n}\n'@\nAdd-Type -TypeDefinition $code\n\n$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]\n$propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime]\n$boolType = [bool]\n$us = [char]31\n$inv = [Globalization.CultureInfo]::InvariantCulture\n$manager = $null\n$artKey = ''\n$artPath = Join-Path $env:TEMP 'riptide_spotify_art.png'\n$anyMedia = $env:RIPTIDE_SPOTIFY_SOURCE -eq 'ANY'\n# Stdin commands are read through a raw StreamReader over the standard input,\n# NOT [Console]::In.ReadLineAsync: on .NET Framework that call takes the console\n# sync root while it blocks on an EMPTY anonymous pipe (exactly what Java's\n# ProcessBuilder hands us - probed: the helper froze silently after 'before\n# readline' with zero output). StreamReader.ReadLineAsync runs on a pool thread\n# with no console lock, so the status loop never stalls on an empty pipe.\n$stdinReader = New-Object System.IO.StreamReader([Console]::OpenStandardInput())\n$readTask = $stdinReader.ReadLineAsync()\n$stdinDead = $false\n# Event-driven refresh: MediaPropertiesChanged (track skip) and\n# PlaybackInfoChanged (play/pause) set a GLOBAL dirty flag (global, not script\n# scope, because the handler executes off the main runspace thread). The loop\n# below re-reads immediately on dirty, otherwise every 450 ms. NOTE: on this box\n# GSMTC property-change events register fine but NEVER fire (probed: two real\n# skips, zero firings - the same stack that hollows art streams and returns null\n# RepeatMode), so the event path is inert here and the 450 ms poll is what\n# actually delivers faster updates: worst-case staleness is halved.\n$global:spotifyDirty = $false\n$subscribedAumid = ''\n$lastPoll = [DateTime]::UtcNow.AddSeconds(-1)\n\nfunction Save-Art($props, $path) {\n    try {\n        if ($null -eq $props.Thumbnail) { return $false }\n        $stream = Await-Op ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])\n        if ($null -eq $stream) { return $false }\n        $bytes = [RiptideSpotifyVolume]::ReadArtStream($stream)\n        if ($null -eq $bytes) { return $false }\n        # Atomic-ish publish: write a temp sibling, then Copy over the stable path\n        # and delete the temp. [IO.File]::Move(source, dest, overwrite) does NOT\n        # exist on .NET Framework 4.x (only .NET Core 3.0+), so Copy+Delete is the\n        # safe form here - a render-thread read never sees a torn image.\n        $tmp = $path + '.part'\n        [System.IO.File]::WriteAllBytes($tmp, $bytes)\n        [System.IO.File]::Copy($tmp, $path, $true)\n        Remove-Item $tmp -Force\n        return $true\n    } catch {\n        return $false\n    }\n}\n\nfunction Invoke-SpotifyCommand($session, $hint, $line) {\n    if ($line -eq 'PLAY_PAUSE') {\n        if ($null -ne $session) { try { Await-Op ($session.TryTogglePlayPauseAsync()) $boolType | Out-Null } catch { } }\n    } elseif ($line -eq 'NEXT') {\n        if ($null -ne $session) { try { Await-Op ($session.TrySkipNextAsync()) $boolType | Out-Null } catch { } }\n    } elseif ($line -eq 'PREV') {\n        if ($null -ne $session) { try { Await-Op ($session.TrySkipPreviousAsync()) $boolType | Out-Null } catch { } }\n    } elseif ($line -eq 'SHUFFLE_ON') {\n        if ($null -ne $session) { try { Await-Op ($session.TryChangeShuffleActiveAsync($true)) $boolType | Out-Null } catch { } }\n    } elseif ($line -eq 'SHUFFLE_OFF') {\n        if ($null -ne $session) { try { Await-Op ($session.TryChangeShuffleActiveAsync($false)) $boolType | Out-Null } catch { } }\n    } elseif ($line -eq 'SOURCE=ANY') {\n        $script:anyMedia = $true\n    } elseif ($line -eq 'SOURCE=SPOTIFY') {\n        $script:anyMedia = $false\n    } elseif ($line -match '^REPEAT=(OFF|ALL|ONE)$') {\n        if ($null -ne $session) {\n            # WinRT enum values passed as their underlying int (None=0, Track=1,\n            # All=2): on some boxes the repeat-mode enum type cannot be projected\n            # into PowerShell at all (probed: [type] load fails and the binder then\n            # hides the method). There the call simply no-ops inside this catch;\n            # where the enum projects, the conversion binds and the command works.\n            $modeInt = switch ($Matches[1]) { 'OFF' { 0 } 'ALL' { 2 } 'ONE' { 1 } }\n            try { Await-Op ($session.TryChangeRepeatModeAsync($modeInt)) $boolType | Out-Null } catch { }\n        }\n    } elseif ($line -match '^VOLUME=(\\d+)$') {\n        [RiptideSpotifyVolume]::SetSessionVolume($hint, [double]$Matches[1] / 100.0) | Out-Null\n    }\n}\n\nwhile ($true) {\n    $elapsed = ([DateTime]::UtcNow - $lastPoll).TotalMilliseconds\n    if ($global:spotifyDirty -or $elapsed -ge 450) {\n        $global:spotifyDirty = $false\n        $lastPoll = [DateTime]::UtcNow\n    try {\n        if ($null -eq $manager) {\n            $subscribedAumid = ''\n            $manager = Await-Op ($managerType::RequestAsync()) $managerType\n        }\n        $session = $null\n        if ($anyMedia) {\n            $session = $manager.GetCurrentSession()\n        } else {\n            foreach ($candidate in $manager.GetSessions()) {\n                if ($candidate.SourceAppUserModelId -match 'spotify') { $session = $candidate; break }\n            }\n        }\n        if ($null -ne $session -and ([string]$session.SourceAppUserModelId) -ne $subscribedAumid) {\n            # Re-subscribe keyed on the AUMID STRING, not RCW object identity:\n            # if the projection ever yields a fresh RCW for the same session, an\n            # object-reference comparison would re-register handlers every poll.\n            try {\n                $null = $session.add_MediaPropertiesChanged({ $global:spotifyDirty = $true })\n                $null = $session.add_PlaybackInfoChanged({ $global:spotifyDirty = $true })\n                $subscribedAumid = [string]$session.SourceAppUserModelId\n            } catch { }\n        }\n        if ($null -eq $session) {\n            [Console]::WriteLine('STOPPED')\n        } else {\n            $hint = if ($anyMedia) { ([string]$session.SourceAppUserModelId).Split('!')[0] } else { 'spotify' }\n            $info = $session.GetPlaybackInfo()\n            $status = ([string]$info.PlaybackStatus).ToUpperInvariant()\n            $props = Await-Op ($session.TryGetMediaPropertiesAsync()) $propsType\n            $artist = ([string]$props.Artist) -replace \"[\\r\\n]\", ' '\n            $title = ([string]$props.Title) -replace \"[\\r\\n]\", ' '\n            $pos = '0'\n            $dur = '0'\n            try {\n                $tl = $session.GetTimelineProperties()\n                if ($null -ne $tl -and $tl.EndTime -gt $tl.StartTime) {\n                    $pos = [Math]::Max(0.0, $tl.Position.TotalSeconds).ToString('F1', $inv)\n                    $dur = ($tl.EndTime - $tl.StartTime).TotalSeconds.ToString('F1', $inv)\n                }\n            } catch { }\n            $shuffle = if ($info.ShuffleActive) { '1' } else { '0' }\n            $repeat = switch ([string]$info.RepeatMode) { 'None' { 'OFF' } 'Track' { 'ONE' } 'All' { 'ALL' } default { 'UNKNOWN' } }\n            $vol = [RiptideSpotifyVolume]::GetSessionVolume($hint)\n            $art = ''\n            if ($title.Length -gt 0) {\n                $key = $artist + '|' + $title\n                if ($key -ne $artKey) {\n                    $artKey = $key\n                    if (Save-Art $props $artPath) {\n                        $art = $artPath\n                    } elseif (Test-Path $artPath) {\n                        # A failed save must not leave the PREVIOUS track's art\n                        # behind for the elseif below to serve with the NEW track.\n                        Remove-Item $artPath -Force\n                    }\n                } elseif (Test-Path $artPath) {\n                    $art = $artPath\n                }\n            }\n            [Console]::WriteLine($status + $us + $artist + $us + $title + $us + $pos + $us + $dur + $us + $shuffle + $us + $repeat + $us + $vol + $us + $art)\n        }\n    } catch {\n        $manager = $null\n        $subscribedAumid = ''\n        [Console]::WriteLine('UNAVAILABLE')\n    }\n    }\n    if (-not $stdinDead -and $readTask.IsCompleted) {\n        try {\n            $line = $readTask.Result\n            if ($null -eq $line) {\n                $stdinDead = $true\n            } else {\n                $readTask = $stdinReader.ReadLineAsync()\n                $hintNow = if ($anyMedia -and $null -ne $session) { ([string]$session.SourceAppUserModelId).Split('!')[0] } else { 'spotify' }\n                Invoke-SpotifyCommand $session $hintNow $line\n            }\n        } catch {\n            $stdinDead = $true\n        }\n    }\n    Start-Sleep -Milliseconds 100\n}\n",
                  StandardCharsets.UTF_8
               );
               file.toFile().deleteOnExit();
               windowsScriptPath = file;
            }

            return windowsScriptPath;
         }
      }
   }

   private static void readLines(int gen, Process child, RiptideSpotify.Backend backend) {
      String lastArtUrl = "";
      String lastArtPath = "";

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
         while (true) {
            RiptideSpotify.Snapshot snap;
            while (true) {
               String line;
               if ((line = reader.readLine()) == null) {
                  return;
               }

               try {
                  snap = backend == RiptideSpotify.Backend.PLAYERCTL ? parsePlayerctl(line) : parseLine(line);
                  break;
               } catch (Throwable var11) {
               }
            }

            if (backend == RiptideSpotify.Backend.PLAYERCTL && snap.artworkPath().startsWith("http")) {
               if (!snap.artworkPath().equals(lastArtUrl)) {
                  lastArtUrl = snap.artworkPath();
                  lastArtPath = downloadArt(lastArtUrl, snap.artist() + "|" + snap.title());
               }

               snap = snap.withArtworkPath(lastArtPath);
            }

            synchronized (LOCK) {
               if (gen != generation) {
                  return;
               }

               store(snap);
            }

            maybeFetchArtFallback(snap);
         }
      } catch (Throwable var14) {
      }
   }

   static void store(RiptideSpotify.Snapshot next) {
      RiptideSpotify.Snapshot prev = current;
      if (prev.status() == next.status() && prev.artist().equals(next.artist()) && prev.title().equals(next.title())) {
         if (next.artworkPath().isEmpty() && !prev.artworkPath().isEmpty()) {
            next = next.withArtworkPath(prev.artworkPath());
         }

         current = next.withTimestamp(prev.updatedAtMs());
      } else {
         current = next;
      }
   }

   private static RiptideSpotify.Snapshot unavailableNow() {
      return new RiptideSpotify.Snapshot(
         RiptideSpotify.Status.UNAVAILABLE, "", "", System.currentTimeMillis(), 0.0, 0.0, false, RiptideSpotify.Repeat.UNKNOWN, -1, ""
      );
   }

   private static void command(String cmd) {
      try {
         RiptideSpotify.Backend backend;
         Process child;
         synchronized (LOCK) {
            backend = pickBackend();
            child = process;
         }

         if (backend == null) {
            return;
         }

         Thread t = new Thread(() -> runCommand(backend, child, cmd), "riptide-spotify-command");
         t.setDaemon(true);
         t.start();
      } catch (Throwable var6) {
      }
   }

   private static void runCommand(RiptideSpotify.Backend backend, Process child, String cmd) {
      try {
         switch (backend) {
            case WINDOWS_PS:
               if (child != null && child.isAlive()) {
                  synchronized (COMMAND_WRITE_LOCK) {
                     OutputStream stdin = child.getOutputStream();
                     stdin.write((cmd + "\n").getBytes(StandardCharsets.UTF_8));
                     stdin.flush();
                     break;
                  }
               }

               return;
            case PLAYERCTL:
               runAndDiscard(playerctlAction(cmd));
            case GDBUS:
            default:
               break;
            case OSX_OSA:
               runAndDiscard(osascriptAction(cmd));
         }
      } catch (Throwable var7) {
      }
   }

   static String[] playerctlAction(String cmd) {
      String[] base = sourceAnywhere ? new String[]{"playerctl"} : new String[]{"playerctl", "--player=spotify"};

      String[] action = switch (cmd) {
         case "PLAY_PAUSE" -> new String[]{"play-pause"};
         case "NEXT" -> new String[]{"next"};
         case "PREV" -> new String[]{"previous"};
         case "SHUFFLE_ON" -> new String[]{"shuffle", "On"};
         case "SHUFFLE_OFF" -> new String[]{"shuffle", "Off"};
         case "REPEAT=OFF" -> new String[]{"loop", "None"};
         case "REPEAT=ALL" -> new String[]{"loop", "Playlist"};
         case "REPEAT=ONE" -> new String[]{"loop", "Track"};
         default -> cmd.startsWith("VOLUME=")
            ? new String[]{"volume", String.format(Locale.ROOT, "%.2f", clampVolume(Integer.parseInt(cmd.substring(7))) / 100.0)}
            : null;
      };
      if (action == null) {
         return null;
      } else {
         String[] argv = new String[base.length + action.length];
         System.arraycopy(base, 0, argv, 0, base.length);
         System.arraycopy(action, 0, argv, base.length, action.length);
         return argv;
      }
   }

   static String[] osascriptAction(String cmd) {
      String apple = switch (cmd) {
         case "PLAY_PAUSE" -> "tell application \"Spotify\" to playpause";
         case "NEXT" -> "tell application \"Spotify\" to next track";
         case "PREV" -> "tell application \"Spotify\" to previous track";
         case "SHUFFLE_ON" -> "tell application \"Spotify\" to set shuffling to true";
         case "SHUFFLE_OFF" -> "tell application \"Spotify\" to set shuffling to false";
         case "REPEAT=OFF" -> "tell application \"Spotify\" to set repeating to false";
         case "REPEAT=ALL", "REPEAT=ONE" -> "tell application \"Spotify\" to set repeating to true";
         default -> cmd.startsWith("VOLUME=") ? "tell application \"Spotify\" to set sound volume to " + clampVolume(Integer.parseInt(cmd.substring(7))) : null;
      };
      return apple == null ? null : new String[]{"osascript", "-e", apple};
   }

   private static void runAndDiscard(String[] argv) {
      if (argv != null) {
         Process process = null;

         try {
            process = new ProcessBuilder(argv).redirectOutput(Redirect.DISCARD).redirectError(Redirect.DISCARD).start();
            if (!process.waitFor(3000L, TimeUnit.MILLISECONDS)) {
               process.destroyForcibly();
            }
         } catch (Exception var3) {
            if (process != null) {
               process.destroyForcibly();
            }
         }
      }
   }

   private static boolean toolAvailable(String tool) {
      return TOOL_AVAILABILITY.computeIfAbsent(tool, RiptideSpotify::probeTool);
   }

   private static boolean probeTool(String tool) {
      Process process = null;

      try {
         process = new ProcessBuilder("sh", "-c", "command -v " + tool).redirectOutput(Redirect.DISCARD).redirectError(Redirect.DISCARD).start();
         if (!process.waitFor(3000L, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            return false;
         } else {
            return process.exitValue() == 0;
         }
      } catch (Exception var3) {
         if (process != null) {
            process.destroyForcibly();
         }

         return false;
      }
   }

   private static void gdbusLoop(int gen) {
      while (true) {
         RiptideSpotify.Snapshot parsed;
         try {
            parsed = parseGdbus(gdbusQuery());
         } catch (Throwable var5) {
            parsed = unavailableNow();
         }

         synchronized (LOCK) {
            if (gen != generation) {
               return;
            }

            store(parsed);
         }

         maybeFetchArtFallback(parsed);

         try {
            Thread.sleep(2000L);
         } catch (InterruptedException var4) {
            Thread.currentThread().interrupt();
            return;
         }
      }
   }

   private static String gdbusQuery() {
      Process process = null;

      try {
         process = new ProcessBuilder(
               "gdbus",
               "call",
               "--session",
               "--dest",
               "org.mpris.MediaPlayer2.spotify",
               "--object-path",
               "/org/mpris/MediaPlayer2",
               "--method",
               "org.freedesktop.DBus.Properties.GetAll",
               "org.mpris.MediaPlayer2.Player"
            )
            .redirectError(Redirect.DISCARD)
            .start();
         CompletableFuture<byte[]> reader = CompletableFuture.supplyAsync(() -> {
            try {
               return process.getInputStream().readAllBytes();
            } catch (IOException var2x) {
               return new byte[0];
            }
         });
         if (!process.waitFor(3000L, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            return "";
         } else {
            byte[] out = reader.get(1L, TimeUnit.SECONDS);
            return process.exitValue() != 0 ? "" : new String(out, StandardCharsets.UTF_8);
         }
      } catch (Exception var4) {
         if (process != null) {
            process.destroyForcibly();
         }

         return "";
      }
   }

   private static void osascriptLoop(int gen) {
      while (true) {
         RiptideSpotify.Snapshot parsed;
         try {
            parsed = parseOsascript(osascriptQuery());
         } catch (Throwable var5) {
            parsed = unavailableNow();
         }

         synchronized (LOCK) {
            if (gen != generation) {
               return;
            }

            store(parsed);
         }

         maybeFetchArtFallback(parsed);

         try {
            Thread.sleep(1500L);
         } catch (InterruptedException var4) {
            Thread.currentThread().interrupt();
            return;
         }
      }
   }

   private static String osascriptQuery() {
      Process process = null;

      try {
         process = new ProcessBuilder(
               "osascript",
               "-e",
               "set US to (ASCII character 31)\nset spScript to \"set US to (ASCII character 31)\nif application \\\"Spotify\\\" is running then\n\ttell application \\\"Spotify\\\"\n\t\tset out to (player state as string) & US\n\t\ttry\n\t\t\tset out to out & (artist of current track) & US & (name of current track)\n\t\ton error\n\t\t\tset out to out & US\n\t\tend try\n\t\ttry\n\t\t\tset out to out & US & (((player position) * 1000) as integer) & US & ((duration of current track) as integer)\n\t\ton error\n\t\t\tset out to out & US & \\\"0\\\" & US & \\\"0\\\"\n\t\tend try\n\t\ttry\n\t\t\tset out to out & US & (shuffling as string) & US & (repeating as string) & US & (sound volume as string)\n\t\ton error\n\t\t\tset out to out & US & \\\"0\\\" & US & \\\"\\\" & US & \\\"-1\\\"\n\t\tend try\n\t\treturn out\n\tend tell\nend if\nreturn \\\"\\\"\"\nset muScript to \"set US to (ASCII character 31)\nif application \\\"Music\\\" is running then\n\ttell application \\\"Music\\\"\n\t\tset out to (player state as string) & US\n\t\ttry\n\t\t\tset out to out & (artist of current track) & US & (name of current track)\n\t\ton error\n\t\t\tset out to out & US\n\t\tend try\n\t\ttry\n\t\t\tset out to out & US & (((player position) * 1000) as integer) & US & (((duration of current track) * 1000) as integer)\n\t\ton error\n\t\t\tset out to out & US & \\\"0\\\" & US & \\\"0\\\"\n\t\tend try\n\t\ttry\n\t\t\tset rep to \\\"false\\\"\n\t\t\tif (song repeat as string) is not \\\"off\\\" then set rep to \\\"true\\\"\n\t\t\tset out to out & US & (shuffle enabled as string) & US & rep & US & (sound volume as string)\n\t\ton error\n\t\t\tset out to out & US & \\\"0\\\" & US & \\\"\\\" & US & \\\"-1\\\"\n\t\tend try\n\t\treturn out\n\tend tell\nend if\nreturn \\\"\\\"\"\nset spLine to \"\"\ntry\n\tset spLine to (run script spScript)\nend try\nset muLine to \"\"\ntry\n\tset muLine to (run script muScript)\nend try\nif spLine starts with \"playing\" then\n\treturn spLine\nelse if muLine starts with \"playing\" then\n\treturn muLine\nelse if spLine is not \"\" then\n\treturn spLine\nelse if muLine is not \"\" then\n\treturn muLine\nend if\nreturn \"\"\n"
            )
            .redirectError(Redirect.DISCARD)
            .start();
         CompletableFuture<byte[]> reader = CompletableFuture.supplyAsync(() -> {
            try {
               return process.getInputStream().readAllBytes();
            } catch (IOException var2x) {
               return new byte[0];
            }
         });
         if (!process.waitFor(3000L, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            return "";
         } else {
            byte[] out = reader.get(1L, TimeUnit.SECONDS);
            return process.exitValue() != 0 ? "" : new String(out, StandardCharsets.UTF_8);
         }
      } catch (Exception var4) {
         if (process != null) {
            process.destroyForcibly();
         }

         return "";
      }
   }

   static String downloadArt(String url) {
      return downloadArt(url, null);
   }

   static String downloadArt(String url, String ownerKey) {
      try {
         URLConnection connection = URI.create(url).toURL().openConnection();
         connection.setConnectTimeout(3000);
         connection.setReadTimeout(3000);

         byte[] data;
         try (InputStream in = connection.getInputStream()) {
            data = in.readNBytes(524289);
         }

         if (!isImageBytes(data)) {
            return "";
         } else {
            data = RiptideImageCodec.ensurePng(data);
            if (data == null) {
               return "";
            } else {
               Path file = artFilePath;
               if (file == null) {
                  synchronized (LOCK) {
                     if (artFilePath == null) {
                        artFilePath = Files.createTempFile("riptide_spotify_art", ".img");
                        artFilePath.toFile().deleteOnExit();
                     }

                     file = artFilePath;
                  }
               }

               Path sibling = file.resolveSibling(file.getFileName() + "." + System.nanoTime() + ".part");
               Files.write(sibling, data);
               synchronized (ART_FILE_LOCK) {
                  try {
                     Files.move(sibling, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                  } finally {
                     Files.deleteIfExists(sibling);
                  }

                  lastArtFileKey = ownerKey;
                  lastArtFilePath = ownerKey == null ? null : file.toString();
               }

               return file.toString();
            }
         }
      } catch (Throwable var21) {
         return "";
      }
   }

   static boolean isImageBytes(byte[] data) {
      if (data == null || data.length < 3) {
         return false;
      } else {
         return (data[0] & 255) == 255 && (data[1] & 255) == 216 ? true : (data[0] & 255) == 137 && (data[1] & 255) == 80 && (data[2] & 255) == 78;
      }
   }

   static void logArt(String stage, String message) {
   }

   static String artCacheLookup(String key) {
      return key != null && key.equals(lastArtFileKey) ? lastArtFilePath : ART_FALLBACK_CACHE.get(key);
   }

   static void artCacheStore(String key, String path) {
      if (key != null && path != null) {
         if (path.isEmpty()) {
            ART_FALLBACK_CACHE.put(key, "");
         } else {
            lastArtFileKey = key;
            lastArtFilePath = path;
         }
      }
   }

   private static void maybeFetchArtFallback(RiptideSpotify.Snapshot snap) {
      if (snap.status() == RiptideSpotify.Status.PLAYING || snap.status() == RiptideSpotify.Status.PAUSED) {
         if (!snap.artist().isEmpty() && !snap.title().isEmpty() && snap.artworkPath().isEmpty()) {
            ensureHooks();
            pendingArtKey = snap.artist() + "|" + snap.title();
            logArt("enqueue", pendingArtKey);
         }
      }
   }

   private static void artWorkerLoop() {
      while (true) {
         String key = pendingArtKey;
         if (key == null) {
            try {
               Thread.sleep(200L);
            } catch (InterruptedException var6) {
               Thread.currentThread().interrupt();
               return;
            }
         } else {
            pendingArtKey = null;

            try {
               String path = artCacheLookup(key);
               boolean freshQuery = path == null;
               if (freshQuery) {
                  path = queryArtFallback(key);
                  artCacheStore(key, path);
               }

               logArt(
                  "query",
                  key
                     + " -> "
                     + (
                        path == null
                           ? "transient-failure(retry)"
                           : (path.isEmpty() ? "no-result(cached)" : path + (freshQuery ? artRungSuffix() : " (file alive)"))
                     )
               );
               if (path != null && !path.isEmpty()) {
                  synchronized (LOCK) {
                     RiptideSpotify.Snapshot cur = current;
                     if ((cur.artist() + "|" + cur.title()).equals(key) && cur.artworkPath().isEmpty()) {
                        store(cur.withArtworkPath(path));
                        logArt("publish", key + " -> " + path);
                     }
                  }
               }
            } catch (Throwable var8) {
            }
         }
      }
   }

   private static String artRungSuffix() {
      return lastArtRung < 0 ? "" : " (rung " + ART_RUNG_NAMES[lastArtRung] + ")";
   }

   static String queryArtFallback(String key) {
      return queryArtFallback(
         key,
         (source, term) -> fetchJson(
            "itunes".equals(source)
               ? itunesBaseUrl + "?term=" + URLEncoder.encode(term, StandardCharsets.UTF_8) + "&entity=song&limit=5"
               : deezerBaseUrl + "?q=" + URLEncoder.encode(term, StandardCharsets.UTF_8) + "&limit=5"
         )
      );
   }

   static String queryArtFallback(String key, BiFunction<String, String, String> fetcher) {
      lastArtRung = -1;
      String trackTerm = keyArtist(key).isEmpty() ? keyTitle(key) : key.replace('|', ' ');
      RiptideSpotify.Selection selection = selectBest(
         key, parseCandidates(fetcher.apply("itunes", trackTerm), true), parseCandidates(fetcher.apply("deezer", trackTerm), false)
      );
      if (selection.candidate != null) {
         return downloadSelection(selection, key, selection.rung());
      } else if (selection.transientSource()) {
         return null;
      } else {
         String artist = keyArtist(key).trim();
         if (!artist.isEmpty() && !keyTitle(key).trim().isEmpty()) {
            RiptideSpotify.Selection widened = selectBest(
               key, parseCandidates(fetcher.apply("itunes", artist), true), parseCandidates(fetcher.apply("deezer", artist), false)
            );
            if (widened.candidate != null) {
               return downloadSelection(widened, key, 4);
            } else {
               return widened.transientSource() ? null : "";
            }
         } else {
            return "";
         }
      }
   }

   private static List<RiptideSpotify.ArtCandidate> parseCandidates(String json, boolean itunes) {
      return json == null ? null : (itunes ? itunesCandidates(json) : deezerCandidates(json));
   }

   static RiptideSpotify.Selection selectBest(String key, List<RiptideSpotify.ArtCandidate> itunes, List<RiptideSpotify.ArtCandidate> deezer) {
      String artist = keyArtist(key);
      String title = keyTitle(key);

      for (int rung = 0; rung <= 3; rung++) {
         RiptideSpotify.ArtCandidate candidate = pickAtRung(itunes, artist, title, rung);
         if (candidate != null) {
            return new RiptideSpotify.Selection(candidate, rung, true, false);
         }

         candidate = pickAtRung(deezer, artist, title, rung);
         if (candidate != null) {
            return new RiptideSpotify.Selection(candidate, rung, false, false);
         }
      }

      return new RiptideSpotify.Selection(null, -1, false, itunes == null || deezer == null);
   }

   private static RiptideSpotify.ArtCandidate pickAtRung(List<RiptideSpotify.ArtCandidate> candidates, String artist, String title, int rung) {
      if (candidates == null) {
         return null;
      } else {
         RiptideSpotify.ArtCandidate firstJunk = null;

         for (RiptideSpotify.ArtCandidate candidate : candidates) {
            boolean junk = isJunkCover(artist, title, candidate);
            if (!junk || rung >= 3) {
               boolean match = switch (rung) {
                  case 0 -> validatesArtworkCandidate(artist, title, candidate.artist(), candidate.title());
                  case 1 -> titleMatches(title, candidate.title());
                  case 2 -> artistMatches(artist, candidate.artist());
                  default -> true;
               };
               if (match) {
                  if (!junk) {
                     return candidate;
                  }

                  if (firstJunk == null) {
                     firstJunk = candidate;
                  }
               }
            }
         }

         return rung == 3 ? firstJunk : null;
      }
   }

   static boolean isJunkCover(String snapshotArtist, String snapshotTitle, RiptideSpotify.ArtCandidate candidate) {
      String snapArtist = snapshotArtist.toLowerCase(Locale.ROOT);
      String snapTitle = snapshotTitle.toLowerCase(Locale.ROOT);
      String candArtist = candidate.artist().toLowerCase(Locale.ROOT);
      String candTitle = candidate.title().toLowerCase(Locale.ROOT);

      for (String marker : COVER_JUNK_MARKERS) {
         if ((candArtist.contains(marker) || candTitle.contains(marker)) && !snapArtist.contains(marker) && !snapTitle.contains(marker)) {
            return true;
         }
      }

      return false;
   }

   private static String downloadSelection(RiptideSpotify.Selection selection, String key, int rungLabel) {
      String url = selection.itunesSource() ? upgradeArtworkUrl(selection.candidate().imageUrl()) : selection.candidate().imageUrl();
      String path = downloadArt(url, key);
      if (path.isEmpty()) {
         return null;
      } else {
         lastArtRung = rungLabel;
         return path;
      }
   }

   private static String keyArtist(String key) {
      int sep = key.indexOf(124);
      return sep < 0 ? key : key.substring(0, sep);
   }

   private static String keyTitle(String key) {
      int sep = key.indexOf(124);
      return sep < 0 ? key : key.substring(sep + 1);
   }

   private static String fetchJson(String url) {
      try {
         URLConnection connection = URI.create(url).toURL().openConnection();
         connection.setConnectTimeout(3000);
         connection.setReadTimeout(3000);

         String var3;
         try (InputStream in = connection.getInputStream()) {
            var3 = new String(in.readNBytes(262144), StandardCharsets.UTF_8);
         }

         return var3;
      } catch (Throwable var7) {
         return null;
      }
   }

   static List<RiptideSpotify.ArtCandidate> itunesCandidates(String json) {
      List<RiptideSpotify.ArtCandidate> candidates = new ArrayList<>();
      if (json == null) {
         return candidates;
      } else {
         try {
            for (JsonElement element : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("results")) {
               try {
                  JsonObject result = element.getAsJsonObject();
                  String artist = jsonString(result, "artistName");
                  String title = jsonString(result, "trackName");
                  String imageUrl = jsonString(result, "artworkUrl100");
                  if (artist != null && title != null && imageUrl != null) {
                     candidates.add(new RiptideSpotify.ArtCandidate(artist, title, imageUrl));
                  }
               } catch (Throwable var8) {
               }
            }
         } catch (Throwable var9) {
         }

         return candidates;
      }
   }

   static List<RiptideSpotify.ArtCandidate> deezerCandidates(String json) {
      List<RiptideSpotify.ArtCandidate> candidates = new ArrayList<>();
      if (json == null) {
         return candidates;
      } else {
         try {
            for (JsonElement element : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("data")) {
               try {
                  JsonObject item = element.getAsJsonObject();
                  String title = jsonString(item, "title");
                  String artist = jsonString(item.getAsJsonObject("artist"), "name");
                  JsonObject album = item.getAsJsonObject("album");
                  String imageUrl = jsonString(album, "cover_big");
                  if (imageUrl == null) {
                     imageUrl = jsonString(album, "cover_xl");
                  }

                  if (artist != null && title != null && imageUrl != null) {
                     candidates.add(new RiptideSpotify.ArtCandidate(artist, title, imageUrl));
                  }
               } catch (Throwable var9) {
               }
            }
         } catch (Throwable var10) {
         }

         return candidates;
      }
   }

   private static String jsonString(JsonObject object, String field) {
      JsonElement element = object == null ? null : object.get(field);
      return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
   }

   static String extractArtworkUrl(String json) {
      if (json == null) {
         return "";
      } else {
         Matcher matcher = ARTWORK_URL100.matcher(json);
         return matcher.find() ? matcher.group(1) : "";
      }
   }

   static String normalizeMusicText(String text) {
      return text == null
         ? ""
         : text.toLowerCase(Locale.ROOT)
            .replaceAll("\\([^)]*\\)", " ")
            .replaceAll("\\[[^]]*\\]", " ")
            .replaceAll("\\b(feat\\.|ft\\.|featuring|slowed|reverb|remix|sped up|nightcore)\\b", " ")
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .trim();
   }

   private static boolean titleMatches(String snapshotTitle, String candidateTitle) {
      String snapTitle = normalizeMusicText(snapshotTitle);
      String candTitle = normalizeMusicText(candidateTitle);
      return !snapTitle.isEmpty() && !candTitle.isEmpty() && (snapTitle.contains(candTitle) || candTitle.contains(snapTitle));
   }

   private static boolean artistMatches(String snapshotArtist, String candidateArtist) {
      String snapArtist = normalizeMusicText(snapshotArtist);
      String candArtist = normalizeMusicText(candidateArtist);
      if (!snapArtist.isEmpty() && !candArtist.isEmpty()) {
         for (String token : snapArtist.split(" ")) {
            if (!token.isEmpty() && candArtist.contains(token)) {
               return true;
            }
         }

         for (String tokenx : candArtist.split(" ")) {
            if (!tokenx.isEmpty() && snapArtist.contains(tokenx)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   static boolean validatesArtworkCandidate(String snapshotArtist, String snapshotTitle, String candidateArtist, String candidateTitle) {
      return titleMatches(snapshotTitle, candidateTitle) && artistMatches(snapshotArtist, candidateArtist);
   }

   static RiptideSpotify.ArtCandidate firstValidatingCandidate(List<RiptideSpotify.ArtCandidate> candidates, String snapshotArtist, String snapshotTitle) {
      for (RiptideSpotify.ArtCandidate candidate : candidates) {
         if (validatesArtworkCandidate(snapshotArtist, snapshotTitle, candidate.artist(), candidate.title())) {
            return candidate;
         }
      }

      return null;
   }

   static String upgradeArtworkUrl(String url) {
      return url == null ? "" : url.replace("100x100bb", "600x600bb");
   }

   static RiptideSpotify.Snapshot parseLine(String line) {
      long now = System.currentTimeMillis();
      if (line != null && !line.isBlank()) {
         String[] p = line.split("\u001f", -1);
         RiptideSpotify.Status status = parseStatus(p[0]);
         if (status == RiptideSpotify.Status.UNAVAILABLE) {
            return unavailable(now);
         } else {
            String artist = p.length > 1 ? p[1] : "";
            String title = p.length > 2 ? p[2] : "";
            if (status != RiptideSpotify.Status.STOPPED && !title.isEmpty()) {
               double pos = p.length > 3 ? parseDouble(p[3], 0.0) : 0.0;
               double dur = p.length > 4 ? parseDouble(p[4], 0.0) : 0.0;
               boolean shuffle = p.length > 5 && isTrueish(p[5]);
               RiptideSpotify.Repeat repeat = p.length > 6 ? parseRepeat(p[6]) : RiptideSpotify.Repeat.UNKNOWN;
               int volume = p.length > 7 ? parseVolume(p[7]) : -1;
               String art = p.length > 8 ? p[8] : "";
               return new RiptideSpotify.Snapshot(status, artist, title, now, pos, dur, shuffle, repeat, volume, art);
            } else {
               return new RiptideSpotify.Snapshot(RiptideSpotify.Status.STOPPED, "", "", now, 0.0, 0.0, false, RiptideSpotify.Repeat.UNKNOWN, -1, "");
            }
         }
      } else {
         return unavailable(now);
      }
   }

   static RiptideSpotify.Snapshot parsePlayerctl(String line) {
      long now = System.currentTimeMillis();
      if (line != null && !line.isBlank()) {
         String[] p = line.split("\u001f", -1);
         RiptideSpotify.Status status = parseStatus(p[0]);
         if (status == RiptideSpotify.Status.UNAVAILABLE) {
            return unavailable(now);
         } else {
            String artist = p.length > 1 ? p[1] : "";
            String title = p.length > 2 ? p[2] : "";
            if (status != RiptideSpotify.Status.STOPPED && !title.isEmpty()) {
               double pos = p.length > 3 ? parseMicros(p[3]) : 0.0;
               double dur = p.length > 4 ? parseMicros(p[4]) : 0.0;
               boolean shuffle = p.length > 5 && isTrueish(p[5]);
               RiptideSpotify.Repeat repeat = p.length > 6 ? parseRepeat(p[6]) : RiptideSpotify.Repeat.UNKNOWN;
               int volume = -1;
               if (p.length > 7 && !p[7].isBlank()) {
                  double v = parseDouble(p[7], Double.NaN);
                  if (!Double.isNaN(v)) {
                     volume = (int)Math.round(v * 100.0);
                  }
               }

               String art = "";
               if (p.length > 8) {
                  String url = p[8];
                  if (url.startsWith("file://")) {
                     art = url.substring("file://".length()).replace("%20", " ");
                  } else if (url.startsWith("http://") || url.startsWith("https://")) {
                     art = url;
                  }
               }

               return new RiptideSpotify.Snapshot(status, artist, title, now, pos, dur, shuffle, repeat, volume, art);
            } else {
               return new RiptideSpotify.Snapshot(RiptideSpotify.Status.STOPPED, "", "", now, 0.0, 0.0, false, RiptideSpotify.Repeat.UNKNOWN, -1, "");
            }
         }
      } else {
         return unavailable(now);
      }
   }

   static RiptideSpotify.Snapshot parseOsascript(String rawStdout) {
      if (rawStdout == null) {
         return unavailable(System.currentTimeMillis());
      } else {
         String line = rawStdout;
         int newline = rawStdout.indexOf(10);
         if (newline >= 0) {
            line = rawStdout.substring(0, newline);
         }

         if (line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1);
         }

         long now = System.currentTimeMillis();
         if (line.isBlank()) {
            return unavailable(now);
         } else {
            String[] p = line.split("\u001f", -1);
            RiptideSpotify.Status status = parseStatus(p[0]);
            if (status == RiptideSpotify.Status.UNAVAILABLE) {
               return unavailable(now);
            } else {
               String artist = p.length > 1 ? p[1] : "";
               String title = p.length > 2 ? p[2] : "";
               if (status != RiptideSpotify.Status.STOPPED && !title.isEmpty()) {
                  double pos = p.length > 3 ? parseDouble(p[3], 0.0) / 1000.0 : 0.0;
                  double dur = p.length > 4 ? parseDouble(p[4], 0.0) / 1000.0 : 0.0;
                  boolean shuffle = p.length > 5 && isTrueish(p[5]);
                  RiptideSpotify.Repeat repeat = RiptideSpotify.Repeat.UNKNOWN;
                  if (p.length > 6) {
                     if (isTrueish(p[6])) {
                        repeat = RiptideSpotify.Repeat.ALL;
                     } else if (!p[6].isBlank()) {
                        repeat = RiptideSpotify.Repeat.OFF;
                     }
                  }

                  int volume = p.length > 7 ? parseVolume(p[7]) : -1;
                  String art = p.length > 8 ? p[8] : "";
                  return new RiptideSpotify.Snapshot(status, artist, title, now, pos, dur, shuffle, repeat, volume, art);
               } else {
                  return new RiptideSpotify.Snapshot(RiptideSpotify.Status.STOPPED, "", "", now, 0.0, 0.0, false, RiptideSpotify.Repeat.UNKNOWN, -1, "");
               }
            }
         }
      }
   }

   static RiptideSpotify.Snapshot parseGdbus(String output) {
      long now = System.currentTimeMillis();
      if (output != null && !output.isBlank()) {
         String statusToken = gvariantString(output, "PlaybackStatus");
         if (statusToken == null) {
            return unavailable(now);
         } else {
            RiptideSpotify.Status status = parseStatus(statusToken);
            if (status == RiptideSpotify.Status.UNAVAILABLE) {
               return unavailable(now);
            } else {
               String title = gvariantString(output, "xesam:title");
               if (status != RiptideSpotify.Status.STOPPED && title != null && !title.isEmpty()) {
                  String artist = gvariantFirstArrayString(output, "xesam:artist");
                  double dur = 0.0;
                  String length = gvariantString(output, "mpris:length");
                  if (length != null) {
                     String digits = length.replaceAll("[^0-9]", "");
                     if (!digits.isEmpty()) {
                        dur = parseDouble(digits, 0.0) / 1000000.0;
                     }
                  }

                  return new RiptideSpotify.Snapshot(status, artist == null ? "" : artist, title, now, 0.0, dur, false, RiptideSpotify.Repeat.UNKNOWN, -1, "");
               } else {
                  return new RiptideSpotify.Snapshot(RiptideSpotify.Status.STOPPED, "", "", now, 0.0, 0.0, false, RiptideSpotify.Repeat.UNKNOWN, -1, "");
               }
            }
         }
      } else {
         return unavailable(now);
      }
   }

   private static RiptideSpotify.Snapshot unavailable(long now) {
      return new RiptideSpotify.Snapshot(RiptideSpotify.Status.UNAVAILABLE, "", "", now, 0.0, 0.0, false, RiptideSpotify.Repeat.UNKNOWN, -1, "");
   }

   private static RiptideSpotify.Status parseStatus(String token) {
      if (token == null) {
         return RiptideSpotify.Status.UNAVAILABLE;
      } else {
         String var1 = token.trim().toUpperCase(Locale.ROOT);

         return switch (var1) {
            case "PLAYING" -> RiptideSpotify.Status.PLAYING;
            case "PAUSED" -> RiptideSpotify.Status.PAUSED;
            case "STOPPED" -> RiptideSpotify.Status.STOPPED;
            default -> RiptideSpotify.Status.UNAVAILABLE;
         };
      }
   }

   private static double parseDouble(String token, double fallback) {
      try {
         return Double.parseDouble(token.trim());
      } catch (Exception var4) {
         return fallback;
      }
   }

   private static double parseMicros(String token) {
      return parseDouble(token, 0.0) / 1000000.0;
   }

   static int parseVolume(String token) {
      if (token != null && !token.isBlank()) {
         try {
            int v = Integer.parseInt(token.trim());
            return v < 0 ? -1 : clampVolume(v);
         } catch (Exception var2) {
            return -1;
         }
      } else {
         return -1;
      }
   }

   private static boolean isTrueish(String token) {
      String t = token.trim().toLowerCase(Locale.ROOT);
      return t.equals("1") || t.equals("true") || t.equals("on");
   }

   static RiptideSpotify.Repeat parseRepeat(String token) {
      String var1 = token.trim().toUpperCase(Locale.ROOT);

      return switch (var1) {
         case "OFF", "NONE" -> RiptideSpotify.Repeat.OFF;
         case "ONE", "TRACK" -> RiptideSpotify.Repeat.ONE;
         case "ALL", "PLAYLIST" -> RiptideSpotify.Repeat.ALL;
         default -> RiptideSpotify.Repeat.UNKNOWN;
      };
   }

   private static String gvariantString(String output, String key) {
      return gvariantQuotedAfter(output, "'" + key + "': <");
   }

   private static String gvariantFirstArrayString(String output, String key) {
      return gvariantQuotedAfter(output, "'" + key + "': <[");
   }

   private static String gvariantQuotedAfter(String output, String needle) {
      int start = output.indexOf(needle);
      if (start < 0) {
         return null;
      } else {
         start += needle.length();
         if (start >= output.length()) {
            return null;
         } else {
            char quote = output.charAt(start);
            if (quote != '\'' && quote != '"') {
               return null;
            } else {
               int from = start + 1;

               while (true) {
                  int end = output.indexOf(quote, from);
                  if (end < 0) {
                     return null;
                  }

                  int backslashes = 0;

                  for (int j = end - 1; j >= from && output.charAt(j) == '\\'; j--) {
                     backslashes++;
                  }

                  if (backslashes % 2 == 0) {
                     return gvariantUnescape(output.substring(from, end), quote);
                  }

                  from = end + 1;
               }
            }
         }
      }
   }

   private static String gvariantUnescape(String value, char quote) {
      StringBuilder out = new StringBuilder(value.length());

      for (int i = 0; i < value.length(); i++) {
         char c = value.charAt(i);
         if (c != '\\' || i + 1 >= value.length() || value.charAt(i + 1) != '\\' && value.charAt(i + 1) != quote) {
            out.append(c);
         } else {
            out.append(value.charAt(++i));
         }
      }

      return out.toString();
   }

   record ArtCandidate(String artist, String title, String imageUrl) {
   }

   private static enum Backend {
      WINDOWS_PS,
      PLAYERCTL,
      GDBUS,
      OSX_OSA;
   }

   public static enum Repeat {
      OFF,
      ONE,
      ALL,
      UNKNOWN;
   }

   record Selection(RiptideSpotify.ArtCandidate candidate, int rung, boolean itunesSource, boolean transientSource) {
   }

   public record Snapshot(
      RiptideSpotify.Status status,
      String artist,
      String title,
      long updatedAtMs,
      double positionSec,
      double durationSec,
      boolean shuffle,
      RiptideSpotify.Repeat repeat,
      int volume,
      String artworkPath
   ) {
      public Snapshot(
         RiptideSpotify.Status status,
         String artist,
         String title,
         long updatedAtMs,
         double positionSec,
         double durationSec,
         boolean shuffle,
         RiptideSpotify.Repeat repeat,
         int volume,
         String artworkPath
      ) {
         if (status == null) {
            status = RiptideSpotify.Status.UNAVAILABLE;
         }

         if (artist == null) {
            artist = "";
         }

         if (title == null) {
            title = "";
         }

         if (repeat == null) {
            repeat = RiptideSpotify.Repeat.UNKNOWN;
         }

         if (artworkPath == null) {
            artworkPath = "";
         }

         if (volume > 100) {
            volume = 100;
         }

         if (volume < -1) {
            volume = -1;
         }

         if (Double.isNaN(positionSec) || positionSec < 0.0) {
            positionSec = 0.0;
         }

         if (Double.isNaN(durationSec) || durationSec < 0.0) {
            durationSec = 0.0;
         }

         this.status = status;
         this.artist = artist;
         this.title = title;
         this.updatedAtMs = updatedAtMs;
         this.positionSec = positionSec;
         this.durationSec = durationSec;
         this.shuffle = shuffle;
         this.repeat = repeat;
         this.volume = volume;
         this.artworkPath = artworkPath;
      }

      public RiptideSpotify.Snapshot withTimestamp(long ms) {
         return new RiptideSpotify.Snapshot(
            this.status, this.artist, this.title, ms, this.positionSec, this.durationSec, this.shuffle, this.repeat, this.volume, this.artworkPath
         );
      }

      public RiptideSpotify.Snapshot withArtworkPath(String path) {
         return new RiptideSpotify.Snapshot(
            this.status, this.artist, this.title, this.updatedAtMs, this.positionSec, this.durationSec, this.shuffle, this.repeat, this.volume, path
         );
      }
   }

   public static enum Status {
      PLAYING,
      PAUSED,
      STOPPED,
      UNAVAILABLE;
   }
}
