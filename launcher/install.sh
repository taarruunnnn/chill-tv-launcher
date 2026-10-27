#!/bin/bash
# Install Chill and make it Home, without ever leaving the TV with no working Home.
# Order matters: install, prove Chill starts, only then disable the stock launcher, then check
# HOME really resolves to Chill — and re-enable the stock launcher if it doesn't.
# Usage: ./install.sh [TV_IP] [APK]   (build first: ./gradlew assembleRelease)

set -u
TV_IP="${1:-192.168.1.10}"
APK="${2:-$(cd "$(dirname "$0")" && pwd)/app/build/outputs/apk/release/app-release.apk}"
CHILL=com.tarun.pontlauncher
# Only these are ever disabled; anything else answering HOME is left alone.
KNOWN_STOCK=(com.google.android.tvlauncher com.google.android.leanbacklauncher com.google.android.apps.tv.launcherx)

[[ -f "$APK" ]] || { echo "ERROR: $APK not found. Build it: ./gradlew assembleRelease"; exit 1; }

adb connect "$TV_IP:5555" >/dev/null
if ! adb devices | grep -q "$TV_IP:5555[[:space:]]*device"; then
  echo "ERROR: TV at $TV_IP not connected/authorized."
  exit 1
fi
export ANDROID_SERIAL="$TV_IP:5555"

home_pkg() {
  adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME \
    | tr -d '\r' | tail -1 | cut -d/ -f1
}

STOCK=$(home_pkg)
echo "Home before: $STOCK"

out=$(adb install -r "$APK" 2>&1 | tr -d '\r')
[[ "$out" == *Success* ]] || { echo "ERROR: install failed, nothing else changed: $out"; exit 1; }

out=$(adb shell am start -W -n "$CHILL/.MainActivity" 2>&1 | tr -d '\r')
if [[ "$out" != *"Status: ok"* || "$out" == *Error* ]]; then
  echo "ERROR: Chill did not start; stock launcher left as it is:"; echo "$out"; exit 1
fi

if [[ "$STOCK" == "$CHILL" ]]; then
  echo "Chill was already Home; updated in place."; exit 0
fi
known=0
for p in "${KNOWN_STOCK[@]}"; do [[ "$STOCK" == "$p" ]] && known=1; done
if [[ $known -eq 0 ]]; then
  echo "Home is $STOCK, which is not a known stock launcher, so it was not disabled."
  echo "Chill is installed; pick it from the TV's home chooser, or disable $STOCK yourself."
  exit 1
fi

adb shell pm disable-user --user 0 "$STOCK" >/dev/null
NOW=$(home_pkg)
if [[ "$NOW" != "$CHILL" ]]; then
  adb shell pm enable "$STOCK" >/dev/null
  echo "ERROR: Home went to '$NOW', not Chill. Re-enabled $STOCK; Home is back to $(home_pkg)."
  exit 1
fi
adb shell sync
echo "Chill is Home ($STOCK disabled). Press HOME on the remote, then reboot once to confirm."
echo "Undo: adb shell pm enable $STOCK && adb uninstall $CHILL"
