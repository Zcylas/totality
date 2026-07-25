package zcylas.totality.api.rpg.resources.client.parity;

import zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason;

/** Test-only fake driving {@link ClientResourceParityPoll} without a real Minecraft client or any
 *  real legacy manager.
 *
 *  <p>External-review correction (2026-07-25): {@link #rageSummary()} now always returns a {@link
 *  ClientResourceParitySummary} (never an {@code Optional}), matching the corrected {@link
 *  ClientResourceParityLegacyAccess} contract — a structurally missing legacy Rage source is
 *  configured via {@link #setRageUnavailable}, not a Java {@code Optional.empty()}. Also records a
 *  bounded, test-only per-method invocation count so tests can prove every legacy read happens
 *  exactly once per poll — never referenced by production code. */
final class FakeClientResourceParityLegacyAccess implements ClientResourceParityLegacyAccess {

    private ClientResourceParitySummary mana = new ClientResourceParitySummary.Scalar(100, 100, 0, 1);
    private ClientResourceParitySummary stamina = new ClientResourceParitySummary.Scalar(100, 100, 0, 1);
    private ClientResourceParitySummary spellSlots = allZeroSpellSlots();
    private ClientResourceParitySummary rage = new ClientResourceParitySummary.Scalar(0, 0, 0, 1);
    private boolean rageExpected = false;

    private int manaCalls = 0;
    private int staminaCalls = 0;
    private int spellSlotCalls = 0;
    private int rageCalls = 0;
    private int rageExpectedCalls = 0;

    void setMana(long current, long maximum) {
        this.mana = new ClientResourceParitySummary.Scalar(current, maximum, 0, 1);
    }

    void setStamina(long current, long maximum) {
        this.stamina = new ClientResourceParitySummary.Scalar(current, maximum, 0, 1);
    }

    void setSpellSlots(ClientResourceParitySummary.Partitioned summary) {
        this.spellSlots = summary;
    }

    void setRage(ClientResourceParitySummary rage) {
        this.rage = rage;
    }

    void setRageUnavailable(ClientResourceUnavailableReason reason) {
        this.rage = new ClientResourceParitySummary.Unavailable(reason);
    }

    void setRageExpected(boolean rageExpected) {
        this.rageExpected = rageExpected;
    }

    int manaCallCount() {
        return manaCalls;
    }

    int staminaCallCount() {
        return staminaCalls;
    }

    int spellSlotCallCount() {
        return spellSlotCalls;
    }

    int rageCallCount() {
        return rageCalls;
    }

    int rageExpectedCallCount() {
        return rageExpectedCalls;
    }

    void clearCallCounts() {
        manaCalls = 0;
        staminaCalls = 0;
        spellSlotCalls = 0;
        rageCalls = 0;
        rageExpectedCalls = 0;
    }

    private static ClientResourceParitySummary.Partitioned allZeroSpellSlots() {
        java.util.List<ClientResourceParitySummary.Partitioned.Partition> partitions = new java.util.ArrayList<>();
        for (int level = 1; level <= 10; level++) {
            partitions.add(new ClientResourceParitySummary.Partitioned.Partition(level, 0, 0, 0));
        }
        return ClientResourceParitySummary.Partitioned.of(partitions, 1);
    }

    @Override
    public ClientResourceParitySummary manaSummary() {
        manaCalls++;
        return mana;
    }

    @Override
    public ClientResourceParitySummary staminaSummary() {
        staminaCalls++;
        return stamina;
    }

    @Override
    public ClientResourceParitySummary spellSlotSummary() {
        spellSlotCalls++;
        return spellSlots;
    }

    @Override
    public ClientResourceParitySummary rageSummary() {
        rageCalls++;
        return rage;
    }

    @Override
    public boolean rageExpectedForPlayer() {
        rageExpectedCalls++;
        return rageExpected;
    }
}
