package zcylas.totality.entity.dev;

import java.util.Locale;
import java.util.Map;

/**
 * DEVELOPMENT TEST appearances of {@code totality:slime_test}: the original grayscale and the seven elemental palettes.
 * Every appearance uses the same model and the same grayscale source texture. {@link #tint} is the colour used by
 * {@link SlimeTestRenderMode#TINT} (a uniform multiply): the V1 experiment's values for Hydro and Pyro, and the
 * research report's "best multiply" tints for the other elements.
 * Not an element system: these are dev-only visual keys.
 */
public enum SlimeTestVariant {
    GRAYSCALE(0xFFFFFFFF),
    ANEMO(0xFFBDFFE8),
    GEO(0xFFFFDFC1),
    ELECTRO(0xFFD672FF),
    DENDRO(0xFFDBFFAB),
    HYDRO(0xFF3FA9F5),
    PYRO(0xFFE8452C),
    CRYO(0xFF87D8FF);

    /**
     * Serialized names of retired development candidates, resolved to the element they belong to so that saved test
     * entities still load: the approved ones (cryo_snowcap, geo_stone_v3, dendro_v2) are now the normal appearances,
     * the rejected ones (and the geo_amber alternative) fall back to their element.
     */
    private static final Map<String, SlimeTestVariant> RETIRED = Map.of(
            "cryo_snowcap", CRYO, "cryo_snowcap_v2", CRYO, "cryo_snowcap_v3", CRYO,
            "geo_stone_v2", GEO, "geo_stone_v3", GEO, "geo_amber", GEO,
            "dendro_v2", DENDRO, "dendro_v3", DENDRO);

    private static final SlimeTestVariant[] VALUES = values();

    /** ARGB multiply tint for {@link SlimeTestRenderMode#TINT}. */
    public final int tint;

    SlimeTestVariant(int tint) {
        this.tint = tint;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SlimeTestVariant byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : GRAYSCALE;
    }

    public static SlimeTestVariant byName(String name) {
        for (SlimeTestVariant v : VALUES) {
            if (v.serializedName().equals(name)) return v;
        }
        return RETIRED.getOrDefault(name, GRAYSCALE);
    }
}
