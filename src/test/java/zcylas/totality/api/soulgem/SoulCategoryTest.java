package zcylas.totality.api.soulgem;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoulCategoryTest {

    @Test
    void onlyOrdinaryExistsInThisFoundationPass() {
        assertArrayEquals(new SoulCategory[]{SoulCategory.ORDINARY}, SoulCategory.values());
    }

    @Test
    void serializedNameIsLowercase() {
        assertEquals("ordinary", SoulCategory.ORDINARY.getSerializedName());
    }

    @Test
    void codecRoundTripsOrdinary() {
        var encoded = SoulCategory.CODEC.encodeStart(JsonOps.INSTANCE, SoulCategory.ORDINARY)
                .result().orElseThrow(() -> new AssertionError("encode failed"));
        SoulCategory decoded = SoulCategory.CODEC.parse(JsonOps.INSTANCE, encoded)
                .result().orElseThrow(() -> new AssertionError("decode failed"));
        assertEquals(SoulCategory.ORDINARY, decoded);
    }
}
