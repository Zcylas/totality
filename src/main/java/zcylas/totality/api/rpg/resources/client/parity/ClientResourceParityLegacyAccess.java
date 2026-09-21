package zcylas.totality.api.rpg.resources.client.parity;

/**
 * The narrowest possible read-only seam from the pure Phase 3B-2B polling algorithm
 * ({@link ClientResourceParityPoll}) to the four existing legacy client mirrors (Mana, Stamina,
 * standard spell slots, Rage). The real, production implementation (see {@code zcylas.totality.
 * client.resource.parity.LegacyClientResourceParityReaders}) reads {@code ClientManaManager},
 * {@code ClientStaminaManager}, {@code ClientSpellSlotManager}, and the client-side {@code
 * PlayerChargesComponent}; this interface exists purely so the polling algorithm can be driven by
 * a synthetic fake in tests, without booting a Minecraft client.
 *
 * <p><b>External-review correction (2026-07-25):</b> {@link #rageSummary()} always returns a
 * {@link ClientResourceParitySummary} — never an {@code Optional} — so a structurally missing
 * legacy Rage source (no local player, no attached charges component) is represented as an
 * {@link ClientResourceParitySummary.Unavailable} value the poll algorithm must still compare and
 * observe, rather than a signal the poll is permitted to skip that Resource entirely for the tick.
 */
public interface ClientResourceParityLegacyAccess {

    ClientResourceParitySummary manaSummary();

    ClientResourceParitySummary staminaSummary();

    ClientResourceParitySummary spellSlotSummary();

    /**
     * Never {@code null} and never a disguised {@code 0/0} fabrication for a genuine structural
     * anomaly — an absent local player or absent charges component is represented as an
     * {@link ClientResourceParitySummary.Unavailable} value, which {@link ClientRageParityPolicy}
     * classifies explicitly (as {@code MODEL_MISMATCH}) rather than the poll algorithm silently
     * skipping this Resource for the tick.
     */
    ClientResourceParitySummary rageSummary();

    /** Whether Rage is expected to be granted to the current player (diagnostic context only —
     *  never gameplay authority). Derived from existing client class state (e.g. {@code
     *  ClientClassManager.hasClass()} plus a Barbarian-class check) by the real implementation. */
    boolean rageExpectedForPlayer();
}
