#!/bin/bash
# Basic Copper Phone Phase 1 real-client verification: runs the Phone capture scenes (45 = existing phone flow,
# 64 = Phase 1 prototype, interactions, per-GUI-scale checks, /totalityphone, equipment slot) in a fresh game dir.
#
# usage (from the repository root): Context/Tools/basic-copper-phone-phase1/run_phone_capture.sh <name> <width> <height>
#   e.g. ... r1080 1920 1080   and   ... r720 1280 720
# Output: build/phone-phase1-capture-<name>/screenshots/*.png and hologram-capture.log (PASS/FAIL lines);
#         the Gradle log goes to build/phone-phase1-capture-<name>.log. The client exits by itself.
set -u
cd "$(git rev-parse --show-toplevel)"
G="$(pwd)/build/phone-phase1-capture-$1"   # --gameDir must be absolute
rm -rf "$G"
mkdir -p "$G"
# Unfocused windows must not pause; no tutorial toast over the top-right corner.
printf 'pauseOnLostFocus:false\nonboardAccessibility:false\nsoundCategory_master:0.0\nguiScale:0\ntutorialStep:none\njoinedFirstServer:true\n' > "$G/options.txt"
JAVA_TOOL_OPTIONS="-Dtotality.hologram.capture=true -Dtotality.hologram.capture.scenes=${4:-45,64}" \
    timeout 1800 ./gradlew runClient --offline --args="--gameDir $G --width $2 --height $3" > "$G.log" 2>&1
code=$?
echo "EXIT $code" >> "$G.log"
grep -E "FAIL|TIMEOUT" "$G/hologram-capture.log"
echo "$1: exit $code, $(grep -c '^PASS' "$G/hologram-capture.log") PASS"
