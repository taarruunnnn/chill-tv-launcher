# Known issues & fixes

Real bugs hit on the audited TV (Realme/Changhong DVB91RM, Android TV 11), with
the diagnosis that found them and the fix that closed them. Everything here was
reproduced and verified on hardware — no speculative advice.

---

## 1. TV appears to restart on its own — `com.mediatek.tv.service` crash loop

**Status:** fixed 2026-08-04 · **Cause:** this repo's own debloat list

### Symptom

Every so often the TV blanks and comes back at the launcher, as if it rebooted.
No pattern the user could pin down.

### Root cause

`debloat.sh` used to remove `com.google.android.leanbacklauncher.partnercustomizer`
as an ads package. It *is* an ads package — but it also owns the ContentProvider
authority **`tvlauncher.widget`**:

```
[tvlauncher.widget]:
  Provider{... com.google.android.leanbacklauncher.partnercustomizer/.ConfigurationProvider}
```

MediaTek's `com.mediatek.tv.service` is flagged **`PERSISTENT`** in its manifest,
and it registers a network callback that calls `ContentResolver.notifyChange()`
on that authority every time the network comes up:

```
FATAL EXCEPTION: ConnectivityThread
Process: com.mediatek.tv.service
java.lang.SecurityException: Failed to find provider tvlauncher.widget for user 0;
    expected to find a valid ContentProvider for this authority
    at ...MyNetworkCallback.notifyChange(MyStartUp.kt:40)
    at ...MyNetworkCallback.onAvailable(MyStartUp.kt:25)
```

With the provider gone the exception is fatal, the process dies — and because
it's persistent, Android immediately respawns it, where it crashes again. The
observed rate was **one crash every ~6 seconds, indefinitely**, with a fresh PID
each time.

Knock-on effects:

- `system_server` spamming `WTF ActivityManager: Restarting service that is not needed`.
- The system dropbox saturated at **300/300 entries**, all from this loop —
  evicting every other diagnostic record, so real crash evidence was being lost.
- Constant process-spawn and dex-load churn on a 1 GB device.

A persistent-process crash loop is the standard cause of a **runtime restart**
(zygote/system_server restart): the screen blanks, the boot animation plays, and
you land back at the launcher. It looks exactly like a reboot but never reaches
the bootloader, which is why `ro.boot.bootreason` still reads
`shutdown,userrequested` from the last real power cycle.

> Honest scope note: the crash loop is proven. The runtime-restart mechanism is
> the leading explanation for the visible restarts, but no kernel panic or
> watchdog was ever recorded in `persist.sys.boot.reason.history` — the restart
> itself was not captured in the act.

### Fix

```bash
adb shell cmd package install-existing com.google.android.leanbacklauncher.partnercustomizer
```

`debloat.sh` no longer removes this package. Keeping it costs nothing visible:
its only job is injecting partner/sponsored rows into the **stock** launcher,
which stays disabled.

### Verifying

```bash
# provider must resolve
adb shell dumpsys package providers | grep -A2 '\[tvlauncher.widget\]'

# PID must stay stable — before the fix it never survived past ~6s
adb shell ps -o PID,ETIME,NAME -p $(adb shell pidof com.mediatek.tv.service)
```

Result after the fix: same PID across a 90-second watch, **0 crashes** in the
event log over that window (~15 expected at the old rate).

---

## 2. Clock stops updating automatically

**Status:** improved; a firmware limit remains

### Symptom

The TV's time is occasionally wrong and stays wrong for a long stretch, then
silently fixes itself.

### Root cause

This panel has **no battery-backed RTC**. When it can't reach NTP it boots to
the epoch fallback — visible in the boot history as timestamps around
`946684809` (2000-01-01 00:00:09 UTC).

The firmware's polling policy is the real problem:

```
$ adb shell dumpsys network_time_update_service
PollingIntervalMs:        +1d0h0m0s0ms   # once a day
PollingIntervalShorterMs: +1m0s0ms       # retry gap
TryAgainTimesMax:         3              # then give up
```

If NTP fails 3 times at boot, one minute apart, the service **gives up for a
full 24 hours**. Issue #1 made this materially worse: the crash loop fired on
network-available at boot — precisely the window those three attempts use.

`auto_time` and `auto_time_zone` are already `1` and the timezone is correct.
Chasing those settings is a dead end.

### Fix (partial)

```bash
adb shell settings put global ntp_server time.google.com
adb shell settings put global ntp_timeout 10000
```

Google's anycast NTP answers in ~4 ms from this TV; the default was unset, so it
used the firmware's pool. Better odds of landing inside those three attempts.

**What can't be fixed without root:** the polling interval, retry gap and retry
count come from framework resources, not from `settings`. Changing them needs
root or a framework overlay.

Also worth knowing: a VPN app that auto-connects at boot can block NTP's UDP 123
and reintroduce this. NordVPN was installed on the audited TV but was not
connected during diagnosis, so it was not the cause here. (It was later
uninstalled for memory reasons — see #4.)

---

## 3. App cache creeping up on a small `/data`

**Status:** maintenance, not a bug

Streaming apps quietly accumulate hundreds of MB. On the audited TV, 68% of a
4.2 GB `/data` was used, ~408 MB of it cache — including 32 MB held by the
**disabled** stock launcher.

```bash
adb shell pm trim-caches 4500000000     # target free bytes; exceeds the partition, so trims everything
adb shell df -h /data/user/0
```

Freed ~390 MB (68% → 58% used). Safe: it only touches `cache/` directories,
never app data. Note it also clears Chill's ~0.2 MB icon cache, so the next cold
start re-renders art once and is back to instant afterwards.

Per-app breakdown without root:

```bash
adb shell dumpsys diskstats     # "Package Names" and "Cache Sizes" arrays line up by index
```

---

## 4. Black screen mid-video, and "TV restarted by itself" — out of memory

**Status:** improved 2026-08-04 · **Cause:** 1 GB of RAM vs. apps that keep growing

### Symptom

A video goes black partway through (no error dialog), or the whole TV blanks
and comes back at the launcher. It started after months of working fine.

### Root cause

The TV boots with only ~40 MB free. When the working set grows past that, the
kernel's low-memory killer kills the **foreground** app: the process dies while
`mResumedActivity` still points at it, which shows as a black screen, not a
crash. Caught live: YouTube missing from the process table,
`Free memory is -9176kB above reserved`, and the same cascade taking Netflix.

Under heavier pressure `system_server` itself dies:

```
*** FATAL EXCEPTION IN SYSTEM PROCESS ...
java.lang.OutOfMemoryError ... at Parcel.nativeWriteBlob / UsageEvents.writeToParcel
```

That is a runtime restart, not a reboot: a `SYSTEM_RESTART` dropbox entry,
unbroken `uptime`, and `persist.sys.boot.reason.history` unchanged. **Every
mysterious `adb: device offline` drop is one of these** — it kills the adb
session too. Loading Netflix + YouTube (playing) + Hotstar in quick succession
reproduces it reliably.

Why it appeared out of nowhere: the firmware never changed, but every big app
auto-updated past what shipped with it — Play Store 45.8 → 52.4, Play Services
25.14 → 26.26 are now the largest consumers (~200–350 MB together). The
low-memory killer is a cliff, not a slope.

### Fix

The two largest non-essential processes went:

```bash
adb shell pm disable-user --user 0 com.homwee.synckey   # 86 MB resident; now in debloat.sh
adb uninstall com.nordvpn.android                       # 81 MB resident, not even connected
```

`com.homwee.synckey` was originally classified NEVER TOUCH because of its
name. Its permissions say otherwise: `INTERNET`, Firebase push,
`BIND_GET_INSTALL_REFERRER_SERVICE`, `REAL_GET_TASKS` — telemetry/attribution,
with no input, display, tuner or DRM permissions. Widevine and Netflix DRM run
in their own vendor HALs. (CleanYourTV's AI PONT lists still say keep it; this
TV's evidence says otherwise, but check your own set.)

Verified after: Netflix and Hotstar launch, YouTube plays a full video **with
Netflix resident** (the original failure) with 0 low-memory kills, and the IR
remote and keypad are still registered.

### What doesn't work — don't bother

- Disabling apps' `BOOT_COMPLETED` receivers: on all three streaming apps it's
  just AndroidX WorkManager. Netflix is woken by TV-firmware receivers and FCM;
  force-stopping it is futile, it's back within seconds.
- `pm disable-user` on a *component* of another app: Android 11 answers
  `SecurityException: Shell cannot change component state`. Needs root.

### Reading it correctly

Judge success by whether the low-memory killer kills the foreground app, **not**
by free memory: Android refills freed RAM immediately (still ~35 MB free after
reclaiming 86 MB, and that's fine). And don't credit a fix without checking
timestamps — two crash loops here stopped on their own before a change that
looked like it fixed them.

`com.homwee.dmt.upgrade` crashing ~22–25 s after each restart looks like a
cause but is a symptom.

---

## Debugging notes for this platform

Things that cost time to figure out, worth knowing before you chase a ghost:

- **Load average sits at 90–110 permanently, and that's normal.** Dozens of
  MediaTek/MStar kernel threads (`DMX_Filt_Task`, `GPIO poll Task`,
  `T_sensor_Check`) park in uninterruptible `D` state by design, and Linux counts
  D-state tasks into the load average. Judge CPU by `top`'s idle % instead — the
  TV was ~50% idle while showing a load of 104.
- **No root, no `su`.** `/data/tombstones`, `/data/anr` and `/data/data/*/cache`
  are unreadable by the shell user. Use `dumpsys dropbox --print <tag>` and
  `dumpsys diskstats` instead.
- **Real reboot history lives in `persist.sys.boot.reason.history`**, and it's
  multi-line — a plain `getprop | grep` truncates it. Read it directly:
  ```bash
  adb shell getprop persist.sys.boot.reason.history
  ```
- **`exec-out screencap` emits garbage on this TV.** Use
  `adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png`.
- **Netflix, Hotstar, Live TV and HDMI screenshot as 0 bytes.** They set
  `FLAG_SECURE` / use a protected video layer; that's not a failure. Check
  `dumpsys activity activities | grep mResumedActivity` instead.
- **Test every debloat change with a reboot**, and wait ~15 s (or
  `adb shell sync`) before rebooting: package state hits disk ~10 s after a
  change. Issue #1 only showed itself after a reboot.
- **Check nobody's watching before sending key events:**
  ```bash
  adb shell dumpsys activity activities | grep mResumedActivity
  ```

## Reporting something new

Include the output of `adb shell getprop persist.sys.boot.reason.history`,
`adb shell dumpsys dropbox | tail -20`, and the package list from
`adb shell pm list packages -u -f` — the last one matters because most issues in
this repo trace back to something the debloat list removed.
