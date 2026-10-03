#!/bin/bash
# Records ONLY the Minecraft client's own audio stream (never the whole desktop mix) while a capture run plays.
# usage: record_game_audio.sh <out.wav> <timeout seconds>
# Waits for the PipeWire sink input of the game (node.name "java", OpenAL Soft), records it with parecord --monitor-stream,
# writes "<out.wav>.start" with the wall-clock ms at which recording started, stops when the stream disappears.
set -u
OUT="$1"; LIMIT="${2:-900}"; T0=$(date +%s)
idx=""
while [ -z "$idx" ]; do
    idx=$(pactl list sink-inputs 2>/dev/null | awk '/^Sink Input #/{i=substr($3,2)} /node\.name = "java"/{print i; exit}')
    [ $(( $(date +%s) - T0 )) -gt "$LIMIT" ] && { echo "no java audio stream"; exit 1; }
    sleep 0.2
done
echo "recording sink input $idx"
date +%s%3N > "$OUT.start"
parecord --monitor-stream="$idx" --file-format=wav --channels=2 --rate=44100 "$OUT" &
PID=$!
while pactl list short sink-inputs 2>/dev/null | awk '{print $1}' | grep -qx "$idx"; do
    [ $(( $(date +%s) - T0 )) -gt "$LIMIT" ] && break
    sleep 0.5
done
kill -INT $PID 2>/dev/null; wait $PID 2>/dev/null
echo "done: $OUT"
