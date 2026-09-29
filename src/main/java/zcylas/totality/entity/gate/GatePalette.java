package zcylas.totality.entity.gate;

import java.util.List;

/**
 * The colours of a Solo Leveling gate's visual layers (RGB, no alpha). The gate's textures are grayscale; each layer is
 * tinted by its own entry here at draw time, so a palette is the only difference between a blue and a red gate
 * (Creative Test K). Immutable: gates share palette instances safely.
 *
 * @param core      the white-hot centre
 * @param body      the main swirling energy
 * @param veins     the bright crackling veins inside the swirl
 * @param depth     the dark outer vortex behind the swirl (alpha-blended: gives the gate its depth)
 * @param rim       the irregular energy boundary
 * @param arcs      lightning arcs and sparks
 * @param mist      the haze around the gate
 * @param fragments the floating energy fragments
 */
public record GatePalette(String id, int core, int body, int veins, int depth, int rim, int arcs, int mist, int fragments) {

    /** The Normal Gate, tuned to the Test K reference sheet (solo_leveling_gate.png: swirl hue 215°). The default. */
    public static final GatePalette BLUE = new GatePalette("blue",
            0xF4FBFF, 0x3A7DFF, 0xBDEBFF, 0x041A5C, 0x72C8FF, 0xE4F6FF, 0x3C86FF, 0x8ADAFF);

    /** An alternate palette (visual only: a red gate has no gameplay here). */
    public static final GatePalette RED = new GatePalette("red",
            0xFFF4EE, 0xE22A22, 0xFFB08E, 0x3C0406, 0xFF6448, 0xFFE6DA, 0xD8302A, 0xFF8C72);

    public static final List<GatePalette> ALL = List.of(BLUE, RED);

    /** The palette with this id, or {@link #BLUE}. */
    public static GatePalette byId(String id) {
        for (GatePalette p : ALL) if (p.id.equals(id)) return p;
        return BLUE;
    }
}
