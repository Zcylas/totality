package zcylas.totality.entity.dev;

import java.util.Locale;

/**
 * DEVELOPMENT TEST: the eye look of {@code totality:slime_test} in the palette modes. {@link #PLAIN} keeps the
 * original eyes (dark rim, white interior); {@link #ELEMENT} recolours the same eye texels with the element's
 * Genshin-style rim, glow and interior colours. A comparison option until the coloured rims are visually approved.
 */
public enum SlimeTestEyeStyle {
    PLAIN, ELEMENT;

    private static final SlimeTestEyeStyle[] VALUES = values();

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SlimeTestEyeStyle byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : PLAIN;
    }

    public static SlimeTestEyeStyle byName(String name) {
        for (SlimeTestEyeStyle e : VALUES) {
            if (e.serializedName().equals(name)) return e;
        }
        return PLAIN;
    }
}
