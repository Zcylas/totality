package zcylas.totality.api.rpg.ancestry;

/**
 * The unlock state of a Species or Origin.
 *
 * UNLOCKED — available from the start, selectable normally
 * LOCKED   — visible but greyed out, shows unlock requirement
 * HIDDEN   — not visible until discovered
 */
public enum UnlockState {
    UNLOCKED,
    LOCKED,
    HIDDEN
}