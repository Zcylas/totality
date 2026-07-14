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
 * <p>Every consuming suite must still gate its own entry point on {@link #isDevEnvironment()}
 * — this class only controls verbosity, not whether verification runs at all.
 */
public final class VerificationReporter {

    private static final String VERBOSE_PROPERTY = "totality.verboseVerification";

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
