package zcylas.totality.api.mob.stats;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinel for the Soul Gem foundation pass's MobRank ordinal-safety fix. Proves,
 * at the source-text level, that the two identified ordinal-dependent consumers
 * ({@code MobCombatStats}'s network-encode site and {@code MobHealthBarHud}'s decode site) were
 * actually changed to the stable {@code order()}/{@code fromOrder(...)} surface, not merely added
 * alongside the old pattern. See
 * {@code TOTALITY_SOUL_GEM_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-17.md} §5.
 */
class MobRankNetworkSourceRegressionTest {

    private static final Path MOB_COMBAT_STATS =
            Path.of("src/main/java/zcylas/totality/api/mob/stats/MobCombatStats.java");
    private static final Path MOB_HEALTH_BAR_HUD =
            Path.of("src/main/java/zcylas/totality/client/renderer/hud/MobHealthBarHud.java");
    private static final Path MOB_STAT_BLOCK =
            Path.of("src/main/java/zcylas/totality/api/mob/stats/MobStatBlock.java");

    private static String read(Path path) throws Exception {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    @Test
    void mobCombatStatsEncodesRankViaOrderNotOrdinal() throws Exception {
        String source = read(MOB_COMBAT_STATS);
        assertTrue(source.contains("this.rank.order()"), "must encode via the stable authored order()");
        assertFalse(source.contains("this.rank.ordinal()"), "must not encode the raw Java ordinal");
    }

    @Test
    void mobHealthBarHudDecodesRankViaFromOrderNotValuesIndexing() throws Exception {
        String source = read(MOB_HEALTH_BAR_HUD);
        assertTrue(source.contains("MobRank.fromOrder("), "must decode via the stable fromOrder(...) lookup");
        assertFalse(source.contains("MobRank.values()["), "must not index MobRank.values() by a raw ordinal/int");
    }

    @Test
    void mobStatBlockParsesFixedRankViaFromIdNotValueOf() throws Exception {
        String source = read(MOB_STAT_BLOCK);
        assertTrue(source.contains("MobRank.fromId(rank)"), "must parse via the id-based, 0-aware helper");
        assertFalse(source.contains("MobRank.valueOf(rank"), "must not use the old valueOf-based parse");
    }
}
