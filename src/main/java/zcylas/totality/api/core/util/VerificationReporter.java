package zcylas.totality.api.core.util;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared dev-environment-gated self-test reporting for in-mod verification suites (e.g.
 * {@code ItemValueVerification}, {@code MerchantSellVerification}).
 *
 * <p>Concise by default: normal startup shows only one summary line per suite. Pass
 * {@code -Dtotality.verboseVerification=true} to also print each individual PASS line;
 * failures always print individually regardless of this setting, so a broken build is never
 * silently summarized away.
 *
 * <p>Every consuming suite must still gate its own entry point: read-only suites on
 * {@link #isDevEnvironment()}, and any suite that runs against the live server/world (fake players,
 * spawned entities, block edits, saved data, live registries) on {@link #liveWorldVerificationEnabled()}
 * — off by default, so an ordinary dev launch never touches the development save.
 */
public final class VerificationReporter {

    private static final String VERBOSE_PROPERTY = "totality.verboseVerification";
    private static final String LIVE_WORLD_PROPERTY = "totality.liveWorldVerification";
    private static boolean liveWorldWarned = false;

    private final Logger logger;
    private final String suiteName;
    private final List<String> failures = new ArrayList<>();
    private int total = 0;

    public VerificationReporter(Logger logger, String suiteName) {
        this.logger = logger;
        this.suiteName = suiteName;
    }

    public static boolean isDevEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    public static boolean verbose() {
        return Boolean.getBoolean(VERBOSE_PROPERTY);
    }

    /**
     * Explicit opt-in ({@code -Dtotality.liveWorldVerification=true}, dev environment only) for suites
     * that run against the live server and may change the loaded world. Use the disposable
     * {@code runVerificationServer} Gradle task rather than enabling this on a normal dev save.
     */
    public static synchronized boolean liveWorldVerificationEnabled() {
        if (!isDevEnvironment() || !Boolean.getBoolean(LIVE_WORLD_PROPERTY)) return false;
        if (!liveWorldWarned) {
            liveWorldWarned = true;
            zcylas.totality.Totality.LOGGER.warn(
                    "[Verification] -D{}=true: live-world verification suites are ENABLED. They create fake "
                            + "players, spawn/discard entities, edit blocks and block-damage saved data in the "
                            + "loaded world. Only run this against a disposable world.", LIVE_WORLD_PROPERTY);
        }
        return true;
    }

    /** Records one check's outcome. Only prints on failure, unless verbose mode is on. */
    public void check(String label, boolean pass, String detailIfFailed) {
        total++;
        if (pass) {
            if (verbose()) {
                logger.info("[{}] PASS - {}", suiteName, label);
            }
        } else {
            logger.error("[{}] FAIL - {} ({})", suiteName, label, detailIfFailed);
            failures.add(label);
        }
    }

    /** Prints the final one-line summary (or the full failure list if anything failed). */
    public void summarize() {
        if (failures.isEmpty()) {
            logger.info("[{}] All {} self-test checks passed.", suiteName, total);
        } else {
            logger.error("[{}] {}/{} self-test checks FAILED:", suiteName, failures.size(), total);
            for (String failure : failures) {
                logger.error("[{}]   - {}", suiteName, failure);
            }
        }
    }

    public int total() {
        return total;
    }

    public boolean allPassed() {
        return failures.isEmpty();
    }
}
