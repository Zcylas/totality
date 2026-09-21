package zcylas.totality.api.soulgem;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.mob.stats.MobRank;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@link CapturedSoul}'s record equality and {@link CapturedSoulComponent}'s codec round-trip
 * without needing a bootstrapped item registry — both operate on plain values/Codecs. See
 * {@code TOTALITY_SOUL_GEM_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-17.md} §9, §10.
 */
class CapturedSoulTest {

    private static final Identifier ZOMBIE = Identifier.fromNamespaceAndPath("minecraft", "zombie");
    private static final Identifier SKELETON = Identifier.fromNamespaceAndPath("minecraft", "skeleton");

    @Test
    void twoSoulsWithSameRankCategoryAndEntityButDifferentInstanceIdAreNotEqual() {
        CapturedSoul a = new CapturedSoul(UUID.randomUUID(), MobRank.F, SoulCategory.ORDINARY, ZOMBIE);
        CapturedSoul b = new CapturedSoul(UUID.randomUUID(), MobRank.F, SoulCategory.ORDINARY, ZOMBIE);
        assertNotEquals(a, b);
    }

    @Test
    void twoSoulsWithIdenticalFieldsIncludingInstanceIdAreEqual() {
        UUID id = UUID.randomUUID();
        CapturedSoul a = new CapturedSoul(id, MobRank.D, SoulCategory.ORDINARY, ZOMBIE);
        CapturedSoul b = new CapturedSoul(id, MobRank.D, SoulCategory.ORDINARY, ZOMBIE);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differingOnlySourceEntityTypeMakesSoulsUnequal() {
        UUID id = UUID.randomUUID();
        CapturedSoul a = new CapturedSoul(id, MobRank.F, SoulCategory.ORDINARY, ZOMBIE);
        CapturedSoul b = new CapturedSoul(id, MobRank.F, SoulCategory.ORDINARY, SKELETON);
        assertNotEquals(a, b);
    }

    @Test
    void storedRankIsTheSoulsActualRankNeverPromotedToAVesselCeiling() {
        // A Common Soul Gem's ceiling is D, but a captured Rank F soul must still record F.
        CapturedSoul soul = new CapturedSoul(UUID.randomUUID(), MobRank.F, SoulCategory.ORDINARY, ZOMBIE);
        assertEquals(MobRank.F, soul.rank());
        assertNotEquals(MobRank.D, soul.rank());
    }

    private static CapturedSoul roundTrip(CapturedSoul soul) {
        var encoded = CapturedSoulComponent.CAPTURED_SOUL_CODEC.encodeStart(JsonOps.INSTANCE, soul)
                .result().orElseThrow(() -> new AssertionError("encode failed for " + soul));
        return CapturedSoulComponent.CAPTURED_SOUL_CODEC.parse(JsonOps.INSTANCE, encoded)
                .result().orElseThrow(() -> new AssertionError("decode failed for " + encoded));
    }

    @Test
    void codecRoundTripPreservesEveryField() {
        CapturedSoul soul = new CapturedSoul(UUID.randomUUID(), MobRank.S, SoulCategory.ORDINARY, ZOMBIE);
        CapturedSoul decoded = roundTrip(soul);
        assertEquals(soul.soulInstanceId(), decoded.soulInstanceId());
        assertEquals(soul.rank(), decoded.rank());
        assertEquals(soul.category(), decoded.category());
        assertEquals(soul.sourceEntityType(), decoded.sourceEntityType());
        assertEquals(soul, decoded);
    }

    @Test
    void codecRoundTripsEveryMobRankIncludingFAndZAndZero() {
        for (MobRank rank : MobRank.values()) {
            CapturedSoul soul = new CapturedSoul(UUID.randomUUID(), rank, SoulCategory.ORDINARY, ZOMBIE);
            assertEquals(rank, roundTrip(soul).rank(), "round-trip failed for rank " + rank);
        }
    }
}
