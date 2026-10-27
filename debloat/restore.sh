#!/bin/bash
# Restore everything debloat.sh removed (and re-enable the stock launcher).
# Usage: ./restore.sh [TV_IP] [package]   — no package = restore ALL.
# Uses the pre-change state debloat.sh saved in debloat/state/<tv serial>.txt, so a package that was
# already disabled before debloating stays disabled and animation speeds go back to what they were.

set -u
TV_IP="${1:-192.168.1.10}"
ONLY="${2:-}"

adb connect "$TV_IP:5555" >/dev/null
if ! adb devices | grep -q "$TV_IP:5555[[:space:]]*device"; then
  echo "ERROR: TV at $TV_IP not connected/authorized."
  exit 1
fi
export ANDROID_SERIAL="$TV_IP:5555"

RESTORE=(
  com.google.android.tvrecommendations
  com.google.android.leanbacklauncher.partnercustomizer
  tv.anoki.acr.controller
  com.google.android.feedback
  android.autoinstalls.config.homwee
  com.homwee.aipont
  com.homwee.help
  com.homwee.howtocast
  com.homwee.mmp.fileexplorer
  com.homwee.mmp.imageplayer
  com.homwee.mmp.screenshare
  com.homwee.multiscreenshare
  com.homwee.store
  com.cltv.fast
  com.homwee.synckey
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
  com.google.android.tvlauncher
)

TV_ID=$(adb shell getprop ro.serialno | tr -d '\r')
[[ -z "$TV_ID" ]] && TV_ID=$(adb shell settings get secure android_id | tr -d '\r')
STATE="$(cd "$(dirname "$0")" && pwd)/state/$TV_ID.txt"
if [[ ! -f "$STATE" ]]; then
  echo "WARNING: no saved state for this TV ($STATE)."
  echo "         Restoring every listed package and default animation speeds instead."
  STATE=/dev/null
fi
recorded() { awk -v k="$1" -v n="$2" '$1==k && $2==n {print $3}' "$STATE"; }

if [[ -n "$ONLY" ]]; then RESTORE=("$ONLY"); fi

for pkg in "${RESTORE[@]}"; do
  was=$(recorded package "$pkg")
  # The stock launcher is not in debloat.sh's list (it's disabled by hand or by
  # launcher/install.sh), so it has no record: always bring it back.
  case "${was:-enabled}" in
    absent)
      echo "skipped  $pkg (was not installed before debloating)" ;;
    disabled)
      adb shell cmd package install-existing "$pkg" >/dev/null 2>&1
      adb shell pm disable-user --user 0 "$pkg" >/dev/null 2>&1
      echo "restored $pkg (disabled, as it was before)" ;;
    *)
      adb shell cmd package install-existing "$pkg" >/dev/null 2>&1
      adb shell pm enable "$pkg" >/dev/null 2>&1
      echo "restored $pkg" ;;
  esac
done

# Restore animation speeds (the recorded ones, else Android's default 1.0)
if [[ -z "$ONLY" ]]; then
  for s in window_animation_scale transition_animation_scale animator_duration_scale; do
    v=$(recorded setting "$s")
    if [[ -z "$v" || "$v" == null ]]; then
      adb shell settings delete global "$s" >/dev/null 2>&1
    else
      adb shell settings put global "$s" "$v"
    fi
  done
fi

adb shell sync
echo "Done. If the stock launcher was disabled, it is re-enabled now."
