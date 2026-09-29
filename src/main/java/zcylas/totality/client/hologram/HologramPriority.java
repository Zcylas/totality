package zcylas.totality.client.hologram;

/**
 * How important a System hologram is. A hologram interrupts the one on display only when it strictly
 * outranks it; the interrupted one is suspended (keeping its remaining time) and resumes afterwards.
 * Equal priorities queue in arrival order.
 */
public enum HologramPriority {
    /** Background status, e.g. "initializing". Anything else may interrupt it. */
    LOW,
    /** Ordinary significant events, e.g. "model ready", a new Daily Quest. */
    NORMAL,
    /** Warnings and failures the player should see now. */
    HIGH,
    /** Immediate danger or a decision that cannot wait. */
    CRITICAL;

    public boolean outranks(HologramPriority other) {
        return compareTo(other) > 0;
    }
}
