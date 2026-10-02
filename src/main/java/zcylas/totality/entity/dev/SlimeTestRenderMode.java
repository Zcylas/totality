package zcylas.totality.entity.dev;

import java.util.Locale;

/**
 * DEVELOPMENT TEST: how {@code totality:slime_test} colours its body. {@link #PALETTE} draws the variant's generated
 * texture (gradient-map palette plus the element's surface-detail motif); {@link #PLAIN} the palette without the motif
 * (the appearance before the final textures, for comparison); {@link #TINT} the grayscale source multiplied by the
 * variant's uniform tint (the V1 method, kept for comparison and future dynamic states). The grayscale variant looks
 * the same in every mode.
 */
public enum SlimeTestRenderMode {
    PALETTE, TINT, PLAIN;

    private static final SlimeTestRenderMode[] VALUES = values();

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SlimeTestRenderMode byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : PALETTE;
    }

    public static SlimeTestRenderMode byName(String name) {
        for (SlimeTestRenderMode m : VALUES) {
            if (m.serializedName().equals(name)) return m;
        }
        return PALETTE;
    }
}
