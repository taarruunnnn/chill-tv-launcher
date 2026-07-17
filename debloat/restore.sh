#!/bin/bash
# Restore everything debloat.sh removed (and re-enable the stock launcher).
# Usage: ./restore.sh [TV_IP] [package]   — no package = restore ALL.

set -u
TV_IP="${1:-192.168.1.10}"
ONLY="${2:-}"

adb connect "$TV_IP:5555" >/dev/null
if ! adb devices | grep -q "$TV_IP:5555[[:space:]]*device"; then
  echo "ERROR: TV at $TV_IP not connected/authorized."
  exit 1
fi

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

if [[ -n "$ONLY" ]]; then RESTORE=("$ONLY"); fi

for pkg in "${RESTORE[@]}"; do
  adb shell cmd package install-existing "$pkg" >/dev/null 2>&1
  adb shell pm enable "$pkg" >/dev/null 2>&1
  echo "restored $pkg"
done

# Restore default animation speeds
adb shell settings put global window_animation_scale 1.0
adb shell settings put global transition_animation_scale 1.0
adb shell settings put global animator_duration_scale 1.0

echo "Done. If the stock launcher was disabled, it is re-enabled now."
