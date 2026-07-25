package zcylas.totality.client.resource.parity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.magic.spell.ClientSpellSlotManager;
import zcylas.totality.api.rpg.classes.ChargeComponents;
import zcylas.totality.api.rpg.classes.ClientClassManager;
import zcylas.totality.api.rpg.classes.PlayerChargesComponent;
import zcylas.totality.api.rpg.classes.TotalityClasses;
import zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityLegacyAccess;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParitySummary;
import zcylas.totality.api.rpg.resources.client.parity.ClientSpellSlotParityComparator;
import zcylas.totality.networking.mana.ClientManaManager;
import zcylas.totality.networking.stamina.ClientStaminaManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The real, production {@link ClientResourceParityLegacyAccess} implementation — reads the four
 * existing legacy client mirrors directly, read-only, with no mutation, no pool creation, and no
 * fabricated numeric value on failure. Every read here reuses the exact accessor an existing
 * production consumer already trusts (e.g. {@code SpellRadialScreen} already reads {@code
 * ClientSpellSlotManager.getMax}/{@code getRemaining} the same way) — this class adds no new
 * legacy-manager method and modifies none of the four managers.
 */
@Environment(EnvType.CLIENT)
public final class LegacyClientResourceParityReaders implements ClientResourceParityLegacyAccess {

    public static final LegacyClientResourceParityReaders INSTANCE = new LegacyClientResourceParityReaders();

    private LegacyClientResourceParityReaders() {}

    /** Reads only {@code ClientManaManager.getMana()}/{@code getMaxMana()} — no clamping, no
     *  normalization, no write-back, no initialized flag. The known rune-cast (@code
     *  FormulaResolver.tryCast}) and initial-join Mana staleness remain observable as ordinary
     *  parity mismatches: nothing here special-cases or suppresses them. */
    @Override
    public ClientResourceParitySummary manaSummary() {
        return new ClientResourceParitySummary.Scalar(
                ClientManaManager.getMana(), ClientManaManager.getMaxMana(), 0, 1);
    }

    /** Reads only {@code ClientStaminaManager.getStamina()}/{@code getMaxStamina()} — never reads
     *  or alters join synchronization, regeneration, action spending, or {@code
     *  TotalityMovementHandler}'s own gameplay-gating read of the same manager. Parity is
     *  diagnostic only and never becomes movement authority. */
    @Override
    public ClientResourceParitySummary staminaSummary() {
        return new ClientResourceParitySummary.Scalar(
                ClientStaminaManager.getStamina(), ClientStaminaManager.getMaxStamina(), 0, 1);
    }

    /** Reads only {@code ClientSpellSlotManager.getRemaining(level)}/{@code getMax(level)} for
     *  every level 1-10 (the exact range {@link ClientSpellSlotParityComparator} requires) — never
     *  reads or retains the manager's internal mutable arrays, never compares against used slots. */
    @Override
    public ClientResourceParitySummary spellSlotSummary() {
        List<ClientResourceParitySummary.Partitioned.Partition> partitions =
                new ArrayList<>(ClientSpellSlotParityComparator.MAX_LEVEL - ClientSpellSlotParityComparator.MIN_LEVEL + 1);
        for (int level = ClientSpellSlotParityComparator.MIN_LEVEL; level <= ClientSpellSlotParityComparator.MAX_LEVEL; level++) {
            partitions.add(new ClientResourceParitySummary.Partitioned.Partition(
                    level, ClientSpellSlotManager.getRemaining(level), ClientSpellSlotManager.getMax(level), 0));
        }
        return ClientResourceParitySummary.Partitioned.of(partitions, 1);
    }

    /**
     * Reads the client-side {@code PlayerChargesComponent} attached to the current local player via
     * the safe, non-throwing {@code ChargeComponents.PLAYER_CHARGES.maybeGet(...)} lookup — never
     * {@code get(...)}, which throws if absent, and never a broad {@code catch (Exception)} around
     * it. Always returns a {@link ClientResourceParitySummary} — never {@code null}, never an {@code
     * Optional} — so a structurally missing legacy Rage source is represented, not skippable.
     *
     * <p><b>External-review correction (2026-07-25):</b> a genuinely absent local player or absent
     * charges component is now represented as an {@link ClientResourceParitySummary.Unavailable}
     * value (never a disguised {@code 0/0}), which {@link
     * zcylas.totality.api.rpg.resources.client.parity.ClientRageParityPolicy} classifies as {@code
     * MODEL_MISMATCH} downstream. The sparse-map "never granted" {@code 0/0} case for a real,
     * attached component is a completely different, expected situation, still handled correctly by
     * {@code getCurrent}/{@code getMax}'s own convention and by {@code ClientRageParityPolicy}'s
     * Rage-absence-expectation reasoning. No {@code hasPool} accessor was added; no pool is created,
     * registered, or mutated; the known live maximum-update gap ({@code
     * PlayerChargesComponent.applySyncPacket} not updating an already-existing pool's maximum) is
     * left completely unfixed so parity can observe it.
     */
    @Override
    public ClientResourceParitySummary rageSummary() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return new ClientResourceParitySummary.Unavailable(ClientResourceUnavailableReason.NO_LOCAL_PLAYER);
        }
        Optional<PlayerChargesComponent> component =
                ChargeComponents.PLAYER_CHARGES.maybeGet((ComponentProvider) player);
        if (component.isEmpty()) {
            return new ClientResourceParitySummary.Unavailable(ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE);
        }
        int current = component.get().getCurrent(BarbarianRageAbility.CHARGE_ID);
        int max = component.get().getMax(BarbarianRageAbility.CHARGE_ID);
        return new ClientResourceParitySummary.Scalar(current, max, 0, 1);
    }

    /**
     * Diagnostic context only — never gameplay authority. Reuses the exact same check already
     * precedented in production code for the Rage HUD pip bar's own visibility gate ({@code
     * TotalityClient}'s {@code ISecondaryResource} registration): {@code ClientClassManager.hasClass()}
     * plus a primary-class-equals-Barbarian check. {@code ClientClassManager} is read here, in this
     * client-only impure class, never passed into or imported by any pure parity class.
     */
    @Override
    public boolean rageExpectedForPlayer() {
        return ClientClassManager.hasClass()
                && TotalityClasses.BARBARIAN_ID.equals(ClientClassManager.getPrimaryClassId());
    }
}
