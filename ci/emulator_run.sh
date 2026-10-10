#!/usr/bin/env bash
#
# End-to-end check on an emulator: does a screenshot actually end up in a folder
# named after its app?
#
# The interesting failure modes are all silent. A permission that was not really
# granted, a move the media store refused, a self-inflicted loop where the sorter
# keeps touching its own output, an app that crashes three seconds after launch.
# None of those show up in "the activity started". So this drives the real thing
# and then looks at the filesystem.
#
# The sort itself is triggered through a receiver that exists only in debug
# builds. The release APK has no such entry point.

set -euo pipefail

LOG_DIR="emulator_logs"
mkdir -p "$LOG_DIR"

PKG="zlaco.sorter"
# Name follows the One UI pattern: prefix, timestamp, app label. The engine is
# what parses it, so the probe has to look like the real thing.
PROBE_APP="CIProbe"
PROBE_NAME="Screenshot_20261010_064921_${PROBE_APP}.png"
# The canonical path, not the /sdcard symlink: the media store records and
# matches on the real one, and mixing the two is how a probe ends up invisible.
ROOT="/storage/emulated/0"
SRC="${ROOT}/DCIM/Screenshots/${PROBE_NAME}"
DST="${ROOT}/Pictures/AppScreenshots/${PROBE_APP}/${PROBE_NAME}"

collect_on_exit() {
  adb devices -l > "$LOG_DIR/adb_devices_exit.txt" 2>&1 || true
  timeout 15 adb logcat -d > "$LOG_DIR/logcat.txt" 2>&1 || true
}
trap collect_on_exit EXIT

fail() {
  echo "E2E_FAIL: $1" | tee -a "$LOG_DIR/result.txt"
  exit 1
}

if ! timeout 15 adb wait-for-device; then
  echo "ADB_WAIT_TIMEOUT" > "$LOG_DIR/boot_error.txt"
  fail "device never appeared"
fi

APK="${APK_PATH:?APK_PATH_NOT_SET}"

if ! adb install -r "$APK" > "$LOG_DIR/install_log.txt" 2>&1; then
  echo "INSTALL_FAILED" > "$LOG_DIR/install_error.txt"
  fail "install failed"
fi

# The permission the app is built around. Granted here so the test exercises the
# real code path rather than an error branch.
adb shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow \
  > "$LOG_DIR/appops.txt" 2>&1 || true
timeout 5 adb logcat -c || true

adb shell am start -W -n "$PKG/zlaco.sorter.ui.MainActivity" \
  > "$LOG_DIR/launch_log.txt" 2>&1 || true
sleep 4

if ! timeout 5 adb shell pidof "$PKG" >/dev/null 2>&1; then
  fail "app is not running after launch"
fi

# A 1x1 PNG. Content is irrelevant, the sorter reads names, not pixels.
adb shell "mkdir -p ${ROOT}/DCIM/Screenshots" >/dev/null 2>&1 || true
printf '%s' \
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==' \
  | base64 -d > "$LOG_DIR/probe.png"

adb push "$LOG_DIR/probe.png" "$SRC" > "$LOG_DIR/push.txt" 2>&1 \
  || fail "could not place the probe file"

adb shell am broadcast -a zlaco.sorter.debug.ENABLE -p "$PKG" \
  > "$LOG_DIR/enable.txt" 2>&1 || true
sleep 1

# Ask the platform to index the file through its own API rather than hoping a
# shell write gets noticed. Until the store knows about the row there is
# nothing for the engine to act on, so this stage is verified on its own.
INDEXED=0
for i in $(seq 1 15); do
  adb shell am broadcast -a zlaco.sorter.debug.SCAN -p "$PKG" --es path "$SRC" \
    >/dev/null 2>&1 || true
  sleep 2
  ROWS=$(adb shell content query --uri content://media/external/images/media \
    --projection _id:DISPLAY_NAME:RELATIVE_PATH \
    --where "DISPLAY_NAME='${PROBE_NAME}'" 2>/dev/null | tr -d '\r')
  case "$ROWS" in
    *"${PROBE_APP}"*) INDEXED=1; break ;;
  esac
done

# Always keep what the store actually held, so a failure says which stage broke.
adb shell content query --uri content://media/external/images/media \
  --projection _id:DISPLAY_NAME:RELATIVE_PATH \
  > "$LOG_DIR/mediastore_dump.txt" 2>&1 || true

[ "$INDEXED" = "1" ] || fail "the media store never indexed the probe; see mediastore_dump.txt"

# Poll, re-running a pass each round, in case the app's own wake-up is slow.
MOVED=0
for i in $(seq 1 20); do
  adb shell am broadcast -a zlaco.sorter.debug.RUN -p "$PKG" >/dev/null 2>&1 || true
  sleep 3
  if adb shell "ls '$DST'" >/dev/null 2>&1; then
    MOVED=1
    echo "moved after $((i * 3))s" >> "$LOG_DIR/result.txt"
    break
  fi
done

[ "$MOVED" = "1" ] || fail "the probe never reached $DST"

if adb shell "ls '$SRC'" >/dev/null 2>&1; then
  fail "the probe is still in the source folder, so it was copied rather than moved"
fi

# Loop check. Our own move announces a change, which wakes the sorter again. A
# second and third pass must find nothing to do and leave the file alone.
adb shell am broadcast -a zlaco.sorter.debug.RUN -p "$PKG" >/dev/null 2>&1 || true
sleep 3
adb shell am broadcast -a zlaco.sorter.debug.RUN -p "$PKG" >/dev/null 2>&1 || true
sleep 3

if ! adb shell "ls '$DST'" >/dev/null 2>&1; then
  fail "the file went missing after re-running, which points at a loop"
fi

# Nothing should have piled up next to the original.
STRAY=$(adb shell "ls ${ROOT}/Pictures/AppScreenshots/${PROBE_APP}/ | wc -l" 2>/dev/null | tr -d '\r')
echo "files_in_target=${STRAY}" >> "$LOG_DIR/result.txt"
[ "$STRAY" = "1" ] || fail "expected exactly one file in the target folder, found ${STRAY}"

if ! timeout 5 adb shell pidof "$PKG" >/dev/null 2>&1; then
  fail "app died during the run"
fi

adb shell screencap -p /sdcard/sorter_after.png >/dev/null 2>&1 || true
adb pull /sdcard/sorter_after.png "$LOG_DIR/screen_after.png" >/dev/null 2>&1 || true

echo "E2E_OK" | tee -a "$LOG_DIR/result.txt"
exit 0
