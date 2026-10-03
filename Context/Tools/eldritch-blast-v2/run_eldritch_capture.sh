#!/bin/bash
# Eldritch Blast V2 real-client capture: scene 70 (EldritchBlastCapture), one frame per game tick.
#
# usage (from the repository root):
#   Context/Tools/eldritch-blast-v2/run_eldritch_capture.sh <name> <width> <height> <backend> ["<extra -D options>"] [sound]
#     backend  opengl | vulkan (forced with --graphicsBackend)
#     extra    e.g. "-Dtotality.eldritch.label=v1" for the pre-change baseline run
#     sound    "sound" keeps the game audible (master 0.6) for audio recording; default muted
# Output: build/eldritch-capture-<name>/screenshots/*.png, hologram-capture.log, build/eldritch-capture-<name>.log
set -u
cd "$(git rev-parse --show-toplevel)"
G="$(pwd)/build/eldritch-capture-$1"   # --gameDir must be absolute
rm -rf "$G"
mkdir -p "$G"
MASTER=0.0
[ "${6:-}" = "sound" ] && MASTER=0.6
printf 'pauseOnLostFocus:false\nonboardAccessibility:false\nsoundCategory_master:%s\nguiScale:0\ntutorialStep:none\njoinedFirstServer:true\nenableVsync:false\nmaxFps:260\ninactivityFpsLimit:"minimized"\npreferredGraphicsBackend:"%s"\n' "$MASTER" "$4" > "$G/options.txt"
LOG="$G.log"
JAVA_TOOL_OPTIONS="-Dtotality.hologram.capture=true -Dtotality.hologram.capture.scenes=70 ${5:-}" \
    timeout 2400 ./gradlew runClient --offline --args="--gameDir $G --width $2 --height $3 --graphicsBackend $4" > "$LOG" 2>&1
code=$?
echo "EXIT $code" >> "$LOG"
grep -E "Using graphics backend|Backend library|Failed to create backend" "$LOG" | head -3
grep -E "FAIL|TIMEOUT|error in check" "$G/hologram-capture.log"
echo "$1/$4: exit $code, $(grep -c 'PASS' "$G/hologram-capture.log") PASS, $(grep -c -E 'FAIL' "$G/hologram-capture.log") FAIL, $(ls "$G/screenshots" 2>/dev/null | wc -l) screenshots"
