#!/bin/bash
# Run the full TeachTrack instrumented suite (3 classes, 52 tests) in ONE
# `am instrument` invocation, hardened against the OEM cached-app freezer
# hang diagnosed in TEACHTRACK_AGENT_COVERAGE_REPORT.md ("Full-suite
# intermittent hang" section).
#
# Root cause of the historical flake: when the suite was started while the
# app process was cached (backgrounded), the system cached-app freezer
# (stock CachedAppOptimizer on ColorOS, `am freeze` / "Async freezing")
# could freeze the whole app process mid-suite. The instrumentation thread
# lives in that process, so output simply stops — no proot child, no app
# logs, no ANR, and SIGQUIT (kill -3) never produces a trace because a
# frozen process cannot run its dump handler.
#
# This script therefore:
#   1. marks the app sticky-unfrozen (`am unfreeze --sticky`) so the system
#      freezer leaves it alone for the process lifetime;
#   2. keeps MainActivity in the foreground during the run (a TOP process
#      is never a freezer candidate) unless --background is passed;
#   3. watches progress and, if output stalls for >STALL_SECS, captures the
#      freezer state (`dumpsys activity processes` isFrozen/isPendingFreeze)
#      and issues `am unfreeze` to heal the run instead of hanging forever.
#
# Usage (inside WSL): bash scripts/run-device-suite.sh [device-serial] [--background]
# Env: ADB (path to adb), STALL_SECS (default 120), OUT (artifact path).
set -u
DEVICE="${1:-92f73a16}"
MODE="${2:---foreground}"
ADB="${ADB:-/root/android-sdk/platform-tools/adb} -s $DEVICE"
PKG=com.leslie.teachtrack
RUNNER="$PKG.test/androidx.test.runner.AndroidJUnitRunner"
CLASSES="${CLASSES:-com.leslie.teachtrack.TeachTrackDatabaseInstrumentedTest,com.leslie.teachtrack.AgentNewActionsInstrumentedTest,com.leslie.teachtrack.SandboxEndToEndInstrumentedTest}"
STALL_SECS="${STALL_SECS:-120}"
STAMP="${STAMP:-$(date +%Y%m%d)}"
OUT="${OUT:-$(dirname "$0")/../artifacts/oneplus-androidtest-full-$STAMP.txt}"

echo "== device suite on $DEVICE (mode=$MODE, stall=${STALL_SECS}s) =="

# (1) Immune to the system freezer for the lifetime of the process.
$ADB shell am unfreeze --sticky $PKG >/dev/null 2>&1 || true

if [ "$MODE" != "--background" ]; then
    # (2) Keep the app TOP during the run.
    $ADB shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
    $ADB shell am start -n $PKG/.MainActivity >/dev/null
    sleep 6
fi

: > "$OUT"
$ADB shell am instrument -w -r -e class "$CLASSES" "$RUNNER" >> "$OUT" 2>&1 &
IPID=$!

last_size=0
stale=0
healed=0
sticky_done=0
while kill -0 $IPID 2>/dev/null; do
    sleep 10
    # The pre-run `am unfreeze --sticky` is a no-op when the process does not
    # exist yet ("could not find process"), and the instrumented process can
    # be frozen DURING attach (observed: ApplicationThreadDeferred + zero
    # logs). Re-issue the sticky unfreeze as soon as the process exists.
    if [ "$sticky_done" = "0" ] && [ -n "$($ADB shell pidof $PKG 2>/dev/null | tr -d '\r')" ]; then
        $ADB shell am unfreeze --sticky $PKG >/dev/null 2>&1 && sticky_done=1
    fi
    size=$(stat -c %s "$OUT")
    if [ "$size" = "$last_size" ]; then
        stale=$((stale + 10))
    else
        stale=0
    fi
    last_size=$size
    if [ "$stale" -ge "$STALL_SECS" ]; then
        echo "!! stall detected after ${stale}s — capturing freezer state"
        $ADB shell dumpsys activity processes > "$OUT.freezecheck" 2>&1
        grep -A3 "ProcessRecord.*$PKG" "$OUT.freezecheck" | grep -E "isFrozen|isPendingFreeze" | head -3 || true
        echo "!! issuing am unfreeze (heal #$((healed + 1)))"
        $ADB shell am unfreeze $PKG || true
        healed=$((healed + 1))
        stale=0
        if [ "$healed" -ge 3 ]; then
            echo "!! stalled repeatedly — aborting"
            kill $IPID 2>/dev/null
            exit 2
        fi
    fi
done
wait $IPID

passes=$(grep -c 'INSTRUMENTATION_STATUS_CODE: 0' "$OUT" || true)
echo "== result: $passes passed (expect 52), heals=$healed =="
tail -3 "$OUT"
[ "$passes" = "52" ] && grep -q "OK (52 tests)" "$OUT"
