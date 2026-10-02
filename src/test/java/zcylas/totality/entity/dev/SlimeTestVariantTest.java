package zcylas.totality.entity.dev;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dev-test variant and render-mode selection for {@code totality:slime_test}: stable synced ids, NBT names that round
 * trip, safe fallbacks, the V1 tints kept for comparison, and the entity syncing and saving both values.
 */
class SlimeTestVariantTest {

    @Test
    void variantIdsAreStable() {
        // The synced byte is the ordinal: reordering would silently change existing test entities.
        assertEquals(List.of("grayscale", "anemo", "geo", "electro", "dendro", "hydro", "pyro", "cryo"),
                List.of(SlimeTestVariant.values()).stream().map(SlimeTestVariant::serializedName).toList());
        assertEquals(List.of("palette", "tint", "plain"), List.of(SlimeTestRenderMode.values()).stream().map(SlimeTestRenderMode::serializedName).toList());
        assertEquals(List.of("plain", "element"), List.of(SlimeTestEyeStyle.values()).stream().map(SlimeTestEyeStyle::serializedName).toList());
    }

    @Test
    void namesAndIdsRoundTripWithSafeFallbacks() {
        for (SlimeTestVariant v : SlimeTestVariant.values()) {
            assertSame(v, SlimeTestVariant.byName(v.serializedName()));
            assertSame(v, SlimeTestVariant.byId(v.ordinal()));
        }
        for (SlimeTestRenderMode m : SlimeTestRenderMode.values()) {
            assertSame(m, SlimeTestRenderMode.byName(m.serializedName()));
            assertSame(m, SlimeTestRenderMode.byId(m.ordinal()));
        }
        assertSame(SlimeTestVariant.GRAYSCALE, SlimeTestVariant.byName("lava"));
        assertSame(SlimeTestVariant.GRAYSCALE, SlimeTestVariant.byId(99));
        assertSame(SlimeTestRenderMode.PALETTE, SlimeTestRenderMode.byName(""));
        assertSame(SlimeTestRenderMode.PALETTE, SlimeTestRenderMode.byId(-1));
        for (SlimeTestEyeStyle e : SlimeTestEyeStyle.values()) {
            assertSame(e, SlimeTestEyeStyle.byName(e.serializedName()));
            assertSame(e, SlimeTestEyeStyle.byId(e.ordinal()));
        }
        assertSame(SlimeTestEyeStyle.PLAIN, SlimeTestEyeStyle.byName("glowing"));
        assertSame(SlimeTestEyeStyle.PLAIN, SlimeTestEyeStyle.byId(7));
    }

    @Test
    void retiredCandidateNamesStillLoadAsTheirElement() {
        // Approved candidates became the normal appearances; rejected ones (and geo_amber) fall back to their element.
        assertSame(SlimeTestVariant.CRYO, SlimeTestVariant.byName("cryo_snowcap"));
        assertSame(SlimeTestVariant.GEO, SlimeTestVariant.byName("geo_stone_v3"));
        assertSame(SlimeTestVariant.DENDRO, SlimeTestVariant.byName("dendro_v2"));
        for (String n : List.of("cryo_snowcap_v2", "cryo_snowcap_v3")) assertSame(SlimeTestVariant.CRYO, SlimeTestVariant.byName(n));
        for (String n : List.of("geo_stone_v2", "geo_amber")) assertSame(SlimeTestVariant.GEO, SlimeTestVariant.byName(n));
        assertSame(SlimeTestVariant.DENDRO, SlimeTestVariant.byName("dendro_v3"));
        assertSame(SlimeTestVariant.GRAYSCALE, SlimeTestVariant.byName("lava"));
    }

    @Test
    void tintModeKeepsTheV1Colours() {
        assertEquals(0xFFFFFFFF, SlimeTestVariant.GRAYSCALE.tint);
        assertEquals(0xFF3FA9F5, SlimeTestVariant.HYDRO.tint, "V1 experimental Hydro tint");
        assertEquals(0xFFE8452C, SlimeTestVariant.PYRO.tint, "V1 experimental Pyro tint");
        for (SlimeTestVariant v : SlimeTestVariant.values()) assertEquals(0xFF, v.tint >>> 24, v + " tint is opaque");
    }

    @Test
    void entitySyncsAndSavesVariantAndMode() throws Exception {
        String entity = Files.readString(Path.of("src/main/java/zcylas/totality/entity/dev/SlimeTestEntity.java"));
        assertTrue(entity.contains("builder.define(DATA_VARIANT, (byte) SlimeTestVariant.GRAYSCALE.ordinal());"));
        assertTrue(entity.contains("builder.define(DATA_MODE, (byte) SlimeTestRenderMode.PALETTE.ordinal());"));
        assertTrue(entity.contains("output.putString(\"Variant\", getVariant().serializedName());"));
        assertTrue(entity.contains("output.putString(\"Mode\", getRenderMode().serializedName());"));
        assertTrue(entity.contains("input.getStringOr(\"Variant\", SlimeTestVariant.GRAYSCALE.serializedName())"));
        assertTrue(entity.contains("input.getStringOr(\"Mode\", SlimeTestRenderMode.PALETTE.serializedName())"),
                "entities saved before Mode existed load in palette mode");
        assertTrue(entity.contains("builder.define(DATA_EYES, (byte) SlimeTestEyeStyle.PLAIN.ordinal());"), "eyes default to the original look");
        assertTrue(entity.contains("output.putString(\"Eyes\", getEyeStyle().serializedName());"));
        assertTrue(entity.contains("input.getStringOr(\"Eyes\", SlimeTestEyeStyle.PLAIN.serializedName())"));
        assertFalse(entity.contains("UvFix"), "the temporary UV comparison flag is gone");
    }
}
