package zcylas.totality.client.hologram;

/** Cells of {@code textures/hologram/icons.png} (a strip of 16-unit icons, in this order). */
public enum HologramIcon {
    SYSTEM,
    CHECK,
    WARNING,
    ERROR,
    MICROPHONE,
    /** Indeterminate activity; drawn rotating. */
    SPINNER,
    /** Objective marker. */
    DIAMOND;

    public static final int CELLS = values().length;
}
