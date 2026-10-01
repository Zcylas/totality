package zcylas.totality.client.camera;

import java.util.Locale;

/**
 * The Camera's modes, in selector order (swiping left moves to the next one). Scan is reserved for the Codex phase:
 * selectable, but it shows an unavailable state and takes no photographs or discoveries in V1.
 */
public enum CameraMode {
    NORMAL("Normal", true),
    SCAN("Scan", false);

    private final String label;
    private final boolean available;

    CameraMode(String label, boolean available) {
        this.label = label;
        this.available = available;
    }

    public String label() {
        return label;
    }

    /** Whether the mode works yet (Scan arrives with the Codex). */
    public boolean available() {
        return available;
    }

    /** Stable id stored in photograph metadata. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
