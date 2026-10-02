package zcylas.totality.client.entity.slime;

import zcylas.totality.entity.dev.SlimeTestVariant;

/**
 * Small Slime V1 palettes: the research report's palettes (§4) with the refinement pass's approved Geo, Dendro and
 * Cryo (2026-10-02). Hexadecimal values unchanged from those reports.
 */
public final class SlimePalettes {

    private SlimePalettes() {}

    public static final SlimePalette ANEMO = SlimePalette.of("anemo", 0x2D7E6A, 0x4FB596, 0x8EDDC0, 0xC6F3DF, 0xF6FFF9);
    /** Approved 2026-10-02 (refinement candidate geo_stone_v3): light warm-taupe body under a near-charcoal rock dome. */
    public static final SlimePalette GEO = SlimePalette.of("geo", 0x5A4A3C, 0x85715E, 0xB09B84, 0xD2C0A6, 0xEEE2CC)
            .withTop(0.40F, 0.65F, 0x211C18, 0x332B25, 0x4A4038, 0x66594D, 0x8C7F72);
    public static final SlimePalette ELECTRO = SlimePalette.of("electro", 0x2E1450, 0x55287F, 0x8547B5, 0xB57EDB, 0xEBD5FF);
    /** Approved 2026-10-02 (refinement candidate dendro_v2): green dome blended in from 35% to 60% of the height. */
    public static final SlimePalette DENDRO = SlimePalette.of("dendro", 0x6F7F4C, 0x9AAA70, 0xC3CDA0, 0xDDE5C2, 0xF5F8E6)
            .withTop(0.35F, 0.60F, 0x2F5F1C, 0x4F8A2C, 0x78B444, 0xA6D66C, 0xE2F5B8);
    public static final SlimePalette HYDRO = SlimePalette.of("hydro", 0x0E5670, 0x1888A6, 0x2DB6CB, 0x7EDFE8, 0xE2FCFF);
    public static final SlimePalette PYRO = SlimePalette.of("pyro", 0x7E1A06, 0xC2400E, 0xF0791C, 0xFFB23A, 0xFFE9A6);
    /** Approved 2026-10-02 (the V1 optional look cryo_snowcap): sky-blue body with a white snow cap from 62% of the height. */
    public static final SlimePalette CRYO = SlimePalette.of("cryo", 0x2D5BA3, 0x4A88D4, 0x7AB6EE, 0xB5DCFA, 0xF4FAFF)
            .withTop(0.62F, 0xA9C3D6, 0xD4E4EE, 0xEEF6FB, 0xFAFDFF, 0xFFFFFF);

    /**
     * Element surface-detail masks (Small Slimes V1 final textures, 2026-10-02) and their overlay colours: Anemo pale
     * swirl lines, Electro lavender lines, Dendro spore dots, Pyro warm highlights, Cryo snow; Geo and Hydro use tone
     * shifts only (their overlay colour is unused).
     */
    public static SlimeMotif motifFor(SlimeTestVariant variant) {
        return switch (variant) {
            case GRAYSCALE -> null;
            case ANEMO -> new SlimeMotif("anemo", 0xE8FFF6);
            case GEO -> new SlimeMotif("geo", 0xD9CDB8);
            case ELECTRO -> new SlimeMotif("electro", 0xDCC2FF);
            case DENDRO -> new SlimeMotif("dendro", 0xE6F5A8);
            case HYDRO -> new SlimeMotif("hydro", 0xCFF6FA);
            case PYRO -> new SlimeMotif("pyro", 0xFFD27A);
            case CRYO -> new SlimeMotif("cryo", 0xFFFFFF);
        };
    }

    /**
     * Element eye colours (rim, glow, interior) measured from the Genshin Small Slime references (UV decision report
     * §2). Development comparison only until visually approved; the default eyes stay unchanged.
     */
    public static SlimeEyeColours eyesFor(SlimeTestVariant variant) {
        return switch (variant) {
            case GRAYSCALE -> null;
            case ANEMO -> new SlimeEyeColours(0xDD9C51, 0xF4CF83, 0xF3F9D9);
            case GEO -> new SlimeEyeColours(0xD76942, 0xF5BB7E, 0xF3F4D8);
            case ELECTRO -> new SlimeEyeColours(0x8239B2, 0xDE96D8, 0xF3F6DF);
            case DENDRO -> new SlimeEyeColours(0xD96B3F, 0xF4BF7E, 0xF2F6D8);
            case HYDRO -> new SlimeEyeColours(0x3493C7, 0x58B0D3, 0xE7F6E5);
            case PYRO -> new SlimeEyeColours(0xE36831, 0xF7BF7F, 0xF5F5D8);
            case CRYO -> new SlimeEyeColours(0x3692CF, 0x6BBFE1, 0xEBF7E7);
        };
    }

    /** The palette of a dev-test variant; the grayscale variant has none (it draws the source texture). */
    public static SlimePalette forVariant(SlimeTestVariant variant) {
        return switch (variant) {
            case GRAYSCALE -> null;
            case ANEMO -> ANEMO;
            case GEO -> GEO;
            case ELECTRO -> ELECTRO;
            case DENDRO -> DENDRO;
            case HYDRO -> HYDRO;
            case PYRO -> PYRO;
            case CRYO -> CRYO;
        };
    }
}
