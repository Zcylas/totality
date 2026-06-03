package zcylas.totality.util.math;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Utility for formatting millisecond durations as human-readable timestamps.
 *
 * <p>Primarily useful for displaying cooldown timers and duration readouts in the HUD.
 * Durations under one hour are formatted as {@code mm:ss}; one hour and above as {@code HH:mm:ss}.
 *
 * <p>Example: {@code TimeMath.timestamp(TotalityMaths.tickToMs(20))} → {@code "00:01"}
 *
 * Ported from CreativeCore {@code TimeMath} (team.creative.creativecore).
 */
public final class TimeMath {

    private TimeMath() {}

    private static final DateFormat FORMAT = new SimpleDateFormat("HH:mm:ss");

    static {
        FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * Formats {@code ms} milliseconds as a {@code mm:ss} or {@code HH:mm:ss} string.
     *
     * @param ms duration in milliseconds (non-negative)
     * @return formatted string, e.g. {@code "01:30"} or {@code "01:30:00"}
     */
    public static String timestamp(long ms) {
        if (ms < 3_600_000L) {
            long min = TimeUnit.MILLISECONDS.toMinutes(ms);
            long sec = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(min);
            return String.format("%02d:%02d", min, sec);
        }
        return FORMAT.format(ms);
    }
}
