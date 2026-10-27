#!/bin/bash
# Debloat Realme TV (Changhong DVB91RM) — idempotent, safe to re-run after any firmware OTA.
# Removes packages for user 0 only (APKs stay on system partition; everything reversible via restore.sh).
# Usage: ./debloat.sh [TV_IP]           apply the list
#        ./debloat.sh [TV_IP] --check   only report listed packages that came back (e.g. after an OTA)

set -u
TV_IP="${1:-192.168.1.10}"
MODE="${2:-apply}"

adb connect "$TV_IP:5555" >/dev/null
if ! adb devices | grep -q "$TV_IP:5555[[:space:]]*device"; then
  echo "ERROR: TV at $TV_IP not connected/authorized. Check network debugging on the TV."
  exit 1
fi
export ANDROID_SERIAL="$TV_IP:5555"

REMOVE=(
  # Ads / tracking / recommendations
  com.google.android.tvrecommendations
  # NOTE: do NOT remove com.google.android.leanbacklauncher.partnercustomizer.
  # It owns the ContentProvider authority "tvlauncher.widget". The persistent
  # system service com.mediatek.tv.service calls notifyChange() on that authority
  # every time the network comes up; without the provider it dies with a
  # SecurityException and is respawned every ~6s, forever.
  tv.anoki.acr.controller
  com.google.android.feedback
  android.autoinstalls.config.homwee
  # OEM bloat
  com.homwee.aipont
  com.homwee.help
  com.homwee.howtocast
  com.homwee.mmp.fileexplorer
  com.homwee.mmp.imageplayer
  com.homwee.mmp.screenshare
  com.homwee.multiscreenshare
  com.homwee.store
  com.cltv.fast
  # OEM telemetry/attribution, ~86 MB resident on a 1 GB TV. Verified 2026-08-04: remote,
  # Netflix/Hotstar/YouTube playback and DRM unaffected (see KNOWN-ISSUES.md #4).
  com.homwee.synckey
  # Unused Google apps (see tv-audit/audit.md)
  com.google.android.katniss
  com.google.android.play.games
  com.google.android.youtube.tvmusic
  com.google.android.tv.remote.service
  com.google.android.apps.nbu.smartconnect.tv
  com.google.android.syncadapters.calendar
  com.google.android.backdrop
  com.google.android.marvin.talkback
  com.amazon.amazonvideo.livingroom
  com.android.printspooler
)

# Android core packages: with one of these gone the TV may never boot again (CleanYourTV #884).
# No override — take it out of the list instead.
CORE_RE='^(android|com\.android\.(systemui|shell|phone|settings|tv\.settings|permissioncontroller|packageinstaller|extservices|extshared|modulemetadata|providers\..*)|com\.google\.android\.(permissioncontroller|packageinstaller|extservices|extshared|modulemetadata|overlay\.modules.*|gms|gsf)|com\.android\.vending)$'
for pkg in "${REMOVE[@]}"; do
  if [[ "$pkg" =~ $CORE_RE ]]; then
    echo "ERROR: $pkg is an Android core package; refusing to run. Remove it from the list."
    exit 1
  fi
done

# One snapshot of the TV per run: installed for user 0, and disabled.
INSTALLED=$(adb shell pm list packages --user 0 | tr -d '\r' | sed 's/^package://')
DISABLED=$(adb shell pm list packages -d --user 0 | tr -d '\r' | sed 's/^package://')
state_of() {  # enabled | disabled | absent
  if printf '%s\n' "$DISABLED" | grep -qx "$1"; then echo disabled
  elif printf '%s\n' "$INSTALLED" | grep -qx "$1"; then echo enabled
  else echo absent; fi
}

if [[ "$MODE" == "--check" ]]; then
  back=0
  for pkg in "${REMOVE[@]}"; do
    if [[ "$(state_of "$pkg")" == enabled ]]; then echo "back on  $pkg"; back=$((back+1)); fi
  done
  echo "----"
  echo "$back of ${#REMOVE[@]} listed packages are back. Re-run without --check to remove them again."
  [[ $back -eq 0 ]]; exit
fi

# Record what the TV looked like before this script first touched it, so restore.sh puts back
# exactly that (and not, say, an app the owner had already disabled). Keyed by the TV's serial,
# not its IP: DHCP can hand the TV a new address between runs. The first record wins; later runs
# only add packages the list gained since.
TV_ID=$(adb shell getprop ro.serialno | tr -d '\r')
[[ -z "$TV_ID" ]] && TV_ID=$(adb shell settings get secure android_id | tr -d '\r')
STATE_DIR="$(cd "$(dirname "$0")" && pwd)/state"
STATE="$STATE_DIR/$TV_ID.txt"
mkdir -p "$STATE_DIR"
if [[ ! -f "$STATE" ]]; then
  for s in window_animation_scale transition_animation_scale animator_duration_scale; do
    echo "setting $s $(adb shell settings get global $s | tr -d '\r')" >> "$STATE"
  done
fi
for pkg in "${REMOVE[@]}"; do
  grep -q "^package $pkg " "$STATE" || echo "package $pkg $(state_of "$pkg")" >> "$STATE"
done
echo "Pre-change state: $STATE (restore.sh reads it; keep it)"

removed=0; skipped=0; failed=0
for pkg in "${REMOVE[@]}"; do
  if [[ "$(state_of "$pkg")" == absent ]]; then
    skipped=$((skipped+1)); continue
  fi
  out=$(adb shell pm uninstall -k --user 0 "$pkg" 2>&1 | tr -d '\r')
  if [[ "$out" == *Success* ]]; then
    echo "removed  $pkg"; removed=$((removed+1))
  else
    out2=$(adb shell pm disable-user --user 0 "$pkg" 2>&1 | tr -d '\r')
    if [[ "$out2" == *disabled* ]]; then
      echo "disabled $pkg"; removed=$((removed+1))
    else
      echo "FAILED   $pkg ($out)"; failed=$((failed+1))
    fi
  fi
done

# Speed: halve system animation durations
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5

# Android writes package state to disk ~10 s after a change; a reboot sent sooner boots the old state.
adb shell sync

echo "----"
echo "Done: $removed removed/disabled, $skipped already gone, $failed failed. Animations set to 0.5x."
echo "Reboot to test (wait ~15 s first). Bugs from removing the wrong package often only show after a reboot."
