#!/bin/bash
# VFX Experiment 1 (emissive glow) real-client verification: capture scene 66, optionally with regression scenes.
#
# usage (from the repository root):
#   Context/Tools/vfx-emissive-bloom/run_glow_capture.sh <name> <width> <height> <backend> [scenes]
#     backend  opengl | vulkan   (forced with the official --graphicsBackend launch argument; the options.txt
#              preferredGraphicsBackend value alone was not honoured on a fresh game dir)
#     scenes   capture scene slots (default 66; e.g. 58,64,65,66 adds Fireball and Phone/Camera regression)
#   e.g.  ... gl1080 1920 1080 opengl
#         ... vk1080 1920 1080 vulkan
# Output: build/vfx-glow-capture-<name>/screenshots/*.png, hologram-capture.log (PASS/FAIL and "timing:" lines),
#         Gradle/client log: build/vfx-glow-capture-<name>.log (contains the "Backend library" line).
set -u
cd "$(git rev-parse --show-toplevel)"
G="$(pwd)/build/vfx-glow-capture-$1"   # --gameDir must be absolute
rm -rf "$G"
mkdir -p "$G"
# Unfocused windows must not pause or throttle; no vsync or frame cap, so frame rates are not clamped; no tutorial toast.
printf 'pauseOnLostFocus:false\nonboardAccessibility:false\nsoundCategory_master:0.0\nguiScale:0\ntutorialStep:none\njoinedFirstServer:true\nenableVsync:false\nmaxFps:260\ninactivityFpsLimit:"minimized"\npreferredGraphicsBackend:"%s"\n' "$4" > "$G/options.txt"
LOG="$G.log"
JAVA_TOOL_OPTIONS="-Dtotality.hologram.capture=true -Dtotality.hologram.capture.scenes=${5:-66} -Dtotality.vfx.capture.tag=$1" \
    timeout 1800 ./gradlew runClient --offline --args="--gameDir $G --width $2 --height $3 --graphicsBackend $4" > "$LOG" 2>&1
code=$?
echo "EXIT $code" >> "$LOG"
grep -E "Using graphics backend|forced to|Failed to create backend|glow buffers" "$LOG" | head -6
grep -E "FAIL|TIMEOUT|error in check" "$G/hologram-capture.log"
grep "^timing:" "$G/hologram-capture.log"
echo "$1/$4: exit $code, $(grep -c '^PASS' "$G/hologram-capture.log") PASS, $(grep -c -E '^FAIL|FAIL:' "$G/hologram-capture.log") FAIL"
