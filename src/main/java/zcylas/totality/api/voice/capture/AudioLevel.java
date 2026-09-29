package zcylas.totality.api.voice.capture;

/** Signal-level helpers for 16-bit PCM. */
public final class AudioLevel {

    /** Full scale of signed 16-bit audio. */
    public static final double FULL_SCALE = 32768.0;
    /** Floor used for display and for "no level" (digital silence). */
    public static final double FLOOR_DBFS = -90.0;

    private AudioLevel() {}

    /** Largest absolute sample value in {@code samples[0..count)}. */
    public static int peak(short[] samples, int count) {
        int peak = 0;
        for (int i = 0; i < count; i++) {
            int v = Math.abs((int) samples[i]);
            if (v > peak) peak = v;
        }
        return peak;
    }

    public static double toDbfs(int peak) {
        return peak <= 0 ? FLOOR_DBFS : Math.max(FLOOR_DBFS, 20.0 * Math.log10(peak / FULL_SCALE));
    }

    /** Maps a peak to 0..1 for a meter spanning -60..0 dBFS. */
    public static float meter(int peak) {
        double db = toDbfs(peak);
        return (float) Math.max(0.0, Math.min(1.0, (db + 60.0) / 60.0));
    }
}
