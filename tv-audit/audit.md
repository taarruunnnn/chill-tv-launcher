# TV Audit — Realme TV (Changhong DVB91RM "ikebukuro")

- Android 11 (API 30), armeabi-v7a (32-bit), ~1 GB RAM (~90 MB free before debloat)
- Stock launcher: `com.google.android.tvlauncher` (ads fed by `com.google.android.tvrecommendations`)
- Chipset: MediaTek. Captured 2026-07-18 via `adb connect 192.168.1.10:5555`.

## REMOVE — ads / tracking / recommendations

| Package | What it is |
|---|---|
| com.google.android.tvrecommendations | Feeds ads + "recommendations" rows to the launcher |
| com.google.android.leanbacklauncher.partnercustomizer | Partner/sponsored app row injection |
| tv.anoki.acr.controller | **ACR — tracks what you watch and phones home** |
| com.google.android.feedback | Google feedback/telemetry uploader |
| android.autoinstalls.config.homwee | Auto-installs OEM bloat after setup/reset |

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
- `com.homwee.dmt.upgrade` (firmware OTA), `com.homwee.factory`, `com.homwee.synckey`, `com.homwee.overlay.tvservice`
- `com.android.tv.settings`, `com.mstar.android.tv.disclaimercustomization`, overlays/RROs
- User apps: Netflix, Hotstar, VLC, CloudStream, NordVPN, YouTube TV
