package zcylas.totality.api.mining;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards how the stale-target cadence correction is wired into {@code PlayerMiningManager} (the arithmetic itself
 * is covered by {@link MiningCadenceTest}; the live tick-driven behaviour by {@code MiningVerification}).
 */
class StaleTargetCadenceSourceRegressionTest {

    private static final Path MANAGER = Path.of("src/main/java/zcylas/totality/api/mining/PlayerMiningManager.java");

    private static String manager() throws Exception { return Files.readString(MANAGER); }

    @Test
    void powerSwingsAreNeverCorrected() throws Exception {
        assertTrue(manager().contains("s.swingIsPower ? null : actual -> correctRecovery(player, s, actual)"),
                "Power Mining charge/release timing is governed separately and must not be re-timed at contact");
    }

    @Test
    void correctionRunsOnlyForARealOwnedHitAndBeforeTheStrike() throws Exception {
        String src = manager();
        int body = src.indexOf("@Nullable Consumer<BlockState> onActualTarget) {");
        assertTrue(body >= 0);
        int swap = src.indexOf("if (!sameSource(swingSource, player.getMainHandItem())) return null;", body);
        int miss = src.indexOf("if (hit == null) return null;", body);
        int owned = src.indexOf("if (!ownsMining(player, level, hit.getBlockPos())) return null;", body);
        int notify = src.indexOf("onActualTarget.accept(level.getBlockState(hit.getBlockPos()));", body);
        int strike = src.indexOf("return strike(player, hit, power, force);", body);
        assertTrue(swap > body && miss > swap && owned > miss && notify > owned && strike > notify,
                "tool swap, miss and permission/ownership exits keep the scheduled cycle; the actual target is read "
                        + "before the strike can break it; exactly one strike follows");
    }

    @Test
    void correctionRewindsTheCarryAndReusesTheSpentWindUp() throws Exception {
        String src = manager();
        assertTrue(src.contains("profiledCycle(player, s.carryBeforeSwing, tool, actual, s.swingDuration)"));
        assertTrue(src.contains("MiningCadence.correctedRecovery(s.swingWindUpTicks, cycleTicks)"));
        assertTrue(src.contains("new zcylas.totality.networking.mining.MiningRecoveryPayload(recovery)"),
                "the corrected recovery is synchronized to the client animation");
    }
}
