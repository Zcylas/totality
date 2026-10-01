#!/bin/bash
# Camera & Gallery V1 real-client verification (capture scene 65, plus 45/64 for Phone regression when asked).
#
# usage (from the repository root):
#   Context/Tools/camera-gallery-v1/run_camera_capture.sh <name> <width> <height> <mode> <username> [world] [scenes] [keep]
#     mode     fresh | persist | isolated   (see CameraGalleryCapture)
#     identity "default" = the dev client's own player (build.gradle fixes --username Zcylas); any other name runs
#              as a DIFFERENT player identity: --uuid is set to that name's offline UUID (the Gallery follows the UUID)
#     world    capture world name (default HologramCapture)
#     scenes   capture scene slots (default 65)
#     keep     "keep" reuses the game dir (persist/isolated runs), otherwise it starts fresh
#   e.g.  ... r1080 1920 1080 fresh default
#         ... r1080 1920 1080 persist default HologramCapture 65 keep
#         ... r1080 1920 1080 isolated OtherTester HologramCapture 65 keep
#         ... r1080 1920 1080 isolated default CameraWorldB 65 keep
# Output: build/camera-gallery-capture-<name>/screenshots/*.png, hologram-capture.log (PASS/FAIL lines),
#         totality/gallery/v1/... (the real Gallery files); Gradle log: build/camera-gallery-capture-<name>-<mode>.log.
set -u
cd "$(git rev-parse --show-toplevel)"
G="$(pwd)/build/camera-gallery-capture-$1"   # --gameDir must be absolute
if [ "${8:-}" != "keep" ]; then
    rm -rf "$G"
    mkdir -p "$G"
fi
# Unfocused windows must not pause; no tutorial toast over the top-right corner.
printf 'pauseOnLostFocus:false\nonboardAccessibility:false\nsoundCategory_master:0.0\nguiScale:0\ntutorialStep:none\njoinedFirstServer:true\n' > "$G/options.txt"
rm -f "$G/hologram-capture.log"
LOG="$G-$4-$5-${6:-HologramCapture}.log"
IDENTITY=""
if [ "$5" != "default" ]; then
    IDENTITY="--uuid $(python3 -c "import uuid,hashlib,sys; h=bytearray(hashlib.md5(('OfflinePlayer:'+sys.argv[1]).encode()).digest()); h[6]=h[6]&0x0f|0x30; h[8]=h[8]&0x3f|0x80; print(uuid.UUID(bytes=bytes(h)))" "$5")"
fi
JAVA_TOOL_OPTIONS="-Dtotality.hologram.capture=true -Dtotality.hologram.capture.scenes=${7:-65} -Dtotality.hologram.capture.camera=$4 -Dtotality.hologram.capture.world=${6:-HologramCapture}" \
    timeout 1800 ./gradlew runClient --offline --args="--gameDir $G --width $2 --height $3 $IDENTITY" > "$LOG" 2>&1
code=$?
echo "EXIT $code" >> "$LOG"
cp "$G/hologram-capture.log" "$G/hologram-capture-$4-$5-${6:-HologramCapture}.log" 2>/dev/null
grep -E "FAIL|TIMEOUT|error in check" "$G/hologram-capture.log"
echo "$1/$4/$5: exit $code, $(grep -c '^PASS' "$G/hologram-capture.log") PASS, $(grep -c -E '^FAIL|FAIL:' "$G/hologram-capture.log") FAIL"
