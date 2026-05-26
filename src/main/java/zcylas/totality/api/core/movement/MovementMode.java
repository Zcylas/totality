package zcylas.totality.api.core.movement;

/**
 * Defines the special movement modes that can be unlocked by passive abilities.
 *
 * These are not equipped abilities — they are granted automatically
 * when the player has a passive that implements MovementModeProvider.
 *
 * Activated via the Movement Power Key (grave/backtick) + movement input:
 *   FLIGHT      — power key + Space (toggles creative-style flight)
 *   POWER_SPRINT — power key + W while on ground (high-speed ground dash)
 *   SUPER_LEAP  — power key + Space while on ground (large upward leap)
 */
public enum MovementMode {
    FLIGHT,
    POWER_SPRINT,
    SUPER_LEAP
}