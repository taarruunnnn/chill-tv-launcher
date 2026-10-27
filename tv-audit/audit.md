# TV Audit — Realme TV (Changhong DVB91RM "ikebukuro")

- Android 11 (API 30), armeabi-v7a (32-bit), ~1 GB RAM (~90 MB free before debloat)
- Stock launcher: `com.google.android.tvlauncher` (ads fed by `com.google.android.tvrecommendations`)
- Chipset: MediaTek. Captured 2026-07-18 via `adb connect 192.168.1.10:5555`.

## REMOVE — ads / tracking / recommendations

| Package | What it is |
|---|---|
| com.google.android.tvrecommendations | Feeds ads + "recommendations" rows to the launcher |
| tv.anoki.acr.controller | **ACR — tracks what you watch and phones home** |
| com.google.android.feedback | Google feedback/telemetry uploader |
| android.autoinstalls.config.homwee | Auto-installs OEM bloat after setup/reset |

> ⚠️ **`com.google.android.leanbacklauncher.partnercustomizer` looks like it
> belongs in this table — it does not.** It injects partner/sponsored rows, but
> it also owns the `tvlauncher.widget` ContentProvider that MediaTek's
> **persistent** `com.mediatek.tv.service` calls on every network change.
> Removing it puts that service into a permanent ~6-second crash loop and makes
> the TV appear to restart at random. See
> [`KNOWN-ISSUES.md`](../KNOWN-ISSUES.md#1-tv-appears-to-restart-on-its-own--commediatektvservice-crash-loop).
> Harmless to leave installed: its rows only ever reach the stock launcher,
> which stays disabled.

## REMOVE — OEM (Changhong/Homwee) bloat

| Package | What it is |
|---|---|
| com.homwee.aipont | OEM "AI" service |
| com.homwee.help | OEM help app |
| com.homwee.howtocast | Casting tutorial app |
| com.homwee.mmp.fileexplorer | OEM file explorer |
| com.homwee.mmp.imageplayer | OEM image viewer |
| com.homwee.mmp.screenshare | OEM screen share |
| com.homwee.multiscreenshare | OEM screen share (second one) |
| com.homwee.store | OEM app store |
| com.cltv.fast | Free ad-supported channels app |
| com.homwee.synckey | OEM telemetry/attribution, 86 MB resident (added 2026-08-04, see KNOWN-ISSUES.md #4) |

## REMOVE — unused Google apps (per user choices, 2026-07-18)

| Package | What it is |
|---|---|
| com.google.android.katniss | Google Assistant / voice search (user: remove; mic button stops working) |
| com.google.android.play.games | Play Games |
| com.google.android.youtube.tvmusic | YouTube Music |
| com.google.android.tv.remote.service | Phone-as-remote support |
| com.google.android.apps.nbu.smartconnect.tv | Google device-connect service |
| com.google.android.syncadapters.calendar | Calendar sync (useless on TV) |
| com.google.android.backdrop | Ambient screensaver that downloads images (basic screensaver remains) |
| com.google.android.marvin.talkback | Screen reader (restore if accessibility needed) |
| com.amazon.amazonvideo.livingroom | Prime Video (user did not mark as used; reinstallable from Play Store) |
| com.android.printspooler | Printing service (on a TV) |

## DISABLE ONLY IN PHASE D (after our launcher is verified)

- `com.google.android.tvlauncher` — stock launcher

## NEVER TOUCH (system-critical)

- `com.android.*` core (SystemUI, providers, networkstack, bluetooth, shell…)
- `com.mediatek.*` — chipset TV services, HDMI/tuner inputs, setup
- `com.google.android.gms`, `gsf`, `com.android.vending` (Play services + Store)
- `com.google.android.webview`, `packageinstaller`, `permissioncontroller`, `inputmethod.latin` (keyboard), `tts`
- `com.google.android.apps.mediashell` — **Chromecast (user: keep)**
- `com.google.android.tungsten.setupwraith`, `partnersetup`, `sss.authbridge`, `onetimeinitializer`, `modulemetadata`, `ext.*`, `frameworkpackagestubs`, `boot.appsplashscreen`
- `com.homwee.dmt.upgrade` (firmware OTA), `com.homwee.factory`, `com.homwee.overlay.tvservice`
- ~~`com.homwee.synckey`~~ — **reclassified REMOVE 2026-08-04.** Listed here on its name alone; its permissions
  are telemetry/attribution (INTERNET, Firebase push, install referrer, REAL_GET_TASKS), it was 86 MB resident,
  and removing it left the remote, playback and DRM working. See KNOWN-ISSUES.md #4.
- `com.android.tv.settings`, `com.mstar.android.tv.disclaimercustomization`, overlays/RROs
- User apps: Netflix, Hotstar, VLC, CloudStream, YouTube TV (NordVPN was uninstalled 2026-08-04 for memory)
