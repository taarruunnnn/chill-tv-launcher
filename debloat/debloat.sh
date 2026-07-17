#!/bin/bash
# Debloat Realme TV (Changhong DVB91RM) — idempotent, safe to re-run after any firmware OTA.
# Removes packages for user 0 only (APKs stay on system partition; everything reversible via restore.sh).
# Usage: ./debloat.sh [TV_IP]   (default 192.168.1.10)

set -u
TV_IP="${1:-192.168.1.10}"

adb connect "$TV_IP:5555" >/dev/null
if ! adb devices | grep -q "$TV_IP:5555[[:space:]]*device"; then
  echo "ERROR: TV at $TV_IP not connected/authorized. Check network debugging on the TV."
  exit 1
fi

REMOVE=(
  # Ads / tracking / recommendations
  com.google.android.tvrecommendations
  com.google.android.leanbacklauncher.partnercustomizer
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

removed=0; skipped=0; failed=0
for pkg in "${REMOVE[@]}"; do
  if ! adb shell pm list packages --user 0 "$pkg" | tr -d '\r' | grep -qx "package:$pkg"; then
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

echo "----"
echo "Done: $removed removed/disabled, $skipped already gone, $failed failed. Animations set to 0.5x."
