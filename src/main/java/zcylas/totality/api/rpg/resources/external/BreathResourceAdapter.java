package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.ResourceSnapshot;

import java.util.Optional;
import java.util.Set;

/**
 * Wraps vanilla air supply ({@code Entity.getAirSupply()}/{@code setAirSupply(int)}/
 * {@code getMaxAirSupply()}). Vanilla remains fully authoritative for underwater depletion,
 * replenishment, drowning damage, Water Breathing/Conduit Power/Breath of the Nautilus
 * interaction, persistence, and synchronization — this adapter only ever reads. See the Phase 2B
 * report's "Vanilla air audit" section for the full source citations behind every claim below.
 *
 * <h2>Vanilla air audit summary (Minecraft 26.2 mapped source)</h2>
 * <ul>
 *     <li>{@code Entity.getAirSupply()}/{@code Entity.setAirSupply(int)} read/write a
 *         {@code SynchedEntityData} field ({@code DATA_AIR_SUPPLY_ID}) — natively, automatically
 *         synchronized to tracking/owning clients exactly like vanilla health, no custom packet
 *         needed or added.</li>
 *     <li>{@code Entity.getMaxAirSupply()} is a non-final, non-overridden-for-{@code Player}
 *         method returning a constant {@code 300} ({@code Entity.TOTAL_AIR_SUPPLY}) by default.
 *         Because it is virtual, this adapter reads it fresh on every query rather than caching or
 *         assuming the {@code 300} baseline — canonical §7.1's "dynamic maximum" requirement.</li>
 *     <li>{@code LivingEntity.baseTick()} decrements air by 1/tick while submerged and unable to
 *         breathe, and — critically — <b>does not clamp at zero</b>: air continues below zero down
 *         to {@code -20}, at which point {@code shouldTakeDrowningDamage()} returns {@code true},
 *         2 drowning damage is dealt, and air is reset to exactly {@code 0}. The negative range is
 *         vanilla's own drowning-damage timer, not usable Breath — see {@link #normalize} below.</li>
 *     <li>Air replenishes by {@code +4/tick} (capped at {@code getMaxAirSupply()}) whenever the
 *         entity is not submerged, or is submerged but {@code MobEffectUtil.shouldEffectsRefillAirsupply}
 *         returns {@code true} (Water Breathing or Conduit Power override Breath of the Nautilus's
 *         suppression; a turtle-helmet-style {@code canBreatheUnderwater()} tag bypasses drowning
 *         entirely).</li>
 *     <li>Persistence is entirely vanilla entity NBT (a short {@code "Air"} field), untouched by
 *         Totality. Respawn creates a fresh entity (air defaults to max via the constructor);
 *         dimension travel and logout/rejoin use vanilla's own entity-data carryover — none of
 *         this is Totality-owned or Totality-modified.</li>
 *     <li>Confirmed via full-source grep: no Totality code anywhere calls {@code getAirSupply}/
 *         {@code setAirSupply}, and {@code VanillaHudElements.AIR_BAR} is never replaced/suppressed
 *         by {@code TotalityHudRenderer} (only {@code HEALTH_BAR}/{@code ARMOR_BAR}/{@code FOOD_BAR}
 *         are). Vanilla's own air-bubble HUD element remains the only Breath-related rendering —
 *         unchanged by this phase, per its explicit scope.</li>
 * </ul>
 *
 * <h2>Normalization policy</h2>
 * Generic Breath exposes the <b>usable reserve</b>, not vanilla's raw internal drowning-cadence
 * counter: {@code current = clamp(rawAirSupply, 0, liveMaximum)}. A negative raw value (mid-drown)
 * normalizes to {@code 0} rather than becoming a negative generic Breath value — the negative range
 * is vanilla owner-specific drowning-timer metadata, not something this generic scalar resource
 * represents. See {@link #normalize} for the exact, directly-testable rule, including the
 * defensive {@link ResourceQueryFailureReason#MALFORMED_OWNER_STATE MALFORMED_OWNER_STATE} path for
 * a non-positive maximum (never actually produced by {@code Player} today, since
 * {@code getMaxAirSupply()} is unoverridden there, but the virtual method could theoretically be
 * overridden into something degenerate by a future entity type).
 */
public final class BreathResourceAdapter implements ExternalPlayerResourceAdapter {

    public static final Identifier ID = PlayerResourceIds.BREATH_ADAPTER;

    /** Vanilla's default {@code Entity.getMaxAirSupply()}/{@code Entity.TOTAL_AIR_SUPPLY} baseline. */
    public static final int VANILLA_BASELINE_MAXIMUM = 300;

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public Optional<ResourceSnapshot> snapshot(Player player, PlayerResourceDefinition definition) {
        return normalize(definition.id(), player.getAirSupply(), player.getMaxAirSupply(), definition.unitScale());
    }

    /**
     * Pure normalization core, package-visible so it can be unit-tested directly with arbitrary
     * {@code (rawCurrent, rawMaximum)} pairs — including values no real {@code Player} would ever
     * actually produce (e.g. a non-positive maximum) — without needing a real Minecraft runtime or
     * reflection.
     *
     * @return {@link Optional#empty()} if {@code rawMaximum} is not positive (the owner's state
     *         cannot be represented as a valid Breath snapshot); otherwise a snapshot whose
     *         {@code currentUnits()} is {@code rawCurrent} clamped into {@code [0, rawMaximum]}.
     */
    static Optional<ResourceSnapshot> normalize(Identifier resourceId, int rawCurrent, int rawMaximum, long unitScale) {
        if (rawMaximum <= 0) {
            return Optional.empty();
        }
        long clampedCurrent = Math.max(0, Math.min(rawCurrent, rawMaximum));
        return Optional.of(new ResourceSnapshot(resourceId, clampedCurrent, rawMaximum, unitScale));
    }

    @Override
    public Set<ExternalResourceOperationSupport> supportedOperations() {
        return Set.of(ExternalResourceOperationSupport.QUERY);
    }

    @Override
    public ExternalResourceClientMirrorMode clientMirrorMode() {
        return ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION;
    }

    public static final BreathResourceAdapter INSTANCE = new BreathResourceAdapter();
}
