package zcylas.totality.api.mining;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Pass 3: Swords and Shears are excluded from Alt/Power Mining on both sides; bare hands and profiled tools are not. */
class SpecializedPowerExclusionSourceRegressionTest {

    private static String read(String p) throws Exception { return Files.readString(Path.of(p)); }

    @Test
    void ruleIsSwordsTagAndShearsOnly() throws Exception {
        String tier = read("src/main/java/zcylas/totality/api/mining/MiningTier.java");
        assertTrue(tier.contains("return stack.is(ItemTags.SWORDS) || stack.getItem() instanceof ShearsItem;"));
    }

    @Test
    void serverRefusesPowerStartReleaseAndQueuedSwings() throws Exception {
        String m = read("src/main/java/zcylas/totality/api/mining/PlayerMiningManager.java");
        int start = m.indexOf("case POWER_START -> {");
        assertTrue(m.indexOf("if (MiningTier.excludedFromPowerMining(player.getMainHandItem())) return;", start) - start < 120,
                "POWER_START is refused before any session state changes");
        int release = m.indexOf("case POWER_RELEASE -> {");
        int queue = m.indexOf("s.queuedForce = MiningTuning.meterValue(held);", release);
        int guard = m.indexOf("if (MiningTier.excludedFromPowerMining(player.getMainHandItem())) return;", release);
        assertTrue(guard > release && guard < queue, "a Sword/Shears in hand at release never queues a force");
        int swing = m.indexOf("private static void tryStartSwing(");
        int power = m.indexOf("if (power && MiningTier.excludedFromPowerMining(heldTool)) {", swing);
        int flag = m.indexOf("s.swingIsPower = power;", swing);
        assertTrue(power > swing && power < flag, "a queued Power swing is dropped before it can be flagged as Power");
    }

    @Test
    void clientDoesNotOpenThePowerMeterForExcludedItems() throws Exception {
        String c = read("src/main/java/zcylas/totality/client/mining/ClientMiningController.java");
        assertTrue(c.contains("&& !MiningTier.excludedFromPowerMining(mc.player.getMainHandItem());"));
    }
}
