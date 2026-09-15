package zcylas.totality.api.ability.impl.barbarian;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.jspecify.annotations.Nullable;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.rpg.classes.*;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.ResourceAmount;
import zcylas.totality.api.rpg.resources.ResourceCause;
import zcylas.totality.api.rpg.resources.ResourceContext;
import zcylas.totality.api.rpg.resources.ResourceCost;
import zcylas.totality.api.rpg.resources.ResourceOperationResult;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.integration.BarbarianRageResources;
import zcylas.totality.init.ModEffects;
import zcylas.totality.networking.resource.ResourceSyncManager;

public class BarbarianRageAbility extends Ability {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("totality", "barbarian_rage");
    // Rage charge pool ID
    public static final Identifier CHARGE_ID = Identifier.fromNamespaceAndPath("totality", "barbarian_rage");

    // Rage damage bonus per class level (index = class level 1-25)
    private static final int[] RAGE_DAMAGE_BONUS = {
            2,2,2,2,2,2,2,2, // levels 1-8: +2
            3,3,3,3,3,3,3,   // levels 9-15: +3
            4,4,4,4,4,4,4,4,4,4 // levels 16-25: +4
    };

    // Rage uses per class level
    private static final int[] RAGE_CHARGES = {
            2,2,3,3,3,4,4,4,4,4,4,5,5,5,5,5,5,6,6,6,6,6,6,6,6
    };

    public static final int RAGE_DURATION_TICKS = 1200; // 1 minute

    public BarbarianRageAbility() {
        super(
                ID,
                "Barbarian Rage",
                "Channel primal fury to enhance your combat. While raging, you gain bonus damage on " +
                        "Strength-based attacks, resistance to physical damage, and advantage on Strength checks. " +
                        "Rage ends if you don't attack or take damage for 20 seconds.",
                Type.TOGGLE,
                0, // no cooldown — gated by charges
                Identifier.fromNamespaceAndPath("totality", "textures/ability/barbarian_rage.png"),
                Source.CLASS,
                "Barbarian",
                "The storm does not apologize for the thunder."
        );
    }

    @Override
    public boolean canActivate(ServerPlayer player, @Nullable AbilityContext context) {
        // Must be a Barbarian
        if (!ClassComponents.get(player).hasClass(TotalityClasses.BARBARIAN_ID)) return false;

        AbilityComponent abilities = AbilityComponents.ABILITIES.get((ComponentProvider) player);

        // If already raging → can toggle off
        if (abilities.isToggleActive(ID)) return true;

        // Must have a charge — routed through the Generic Player Resource API (Phase 5 Rage
        // migration, 2026-09-15); totality:rage is now the authoritative current value.
        BarbarianRageResources.ensureInstantiated(player);
        ResourceQueryResult result = PlayerResourceService.INSTANCE.query(player, PlayerResourceIds.RAGE);
        return result instanceof ResourceQueryResult.Success success && success.snapshot().currentUnits() > 0;
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext context) {
        AbilityComponent abilities = AbilityComponents.ABILITIES.get((ComponentProvider) player);

        if (abilities.isToggleActive(ID)) {
            // Already raging → end rage
            onToggleOff(player);
            abilities.deactivateToggle(ID);
        } else {
            // Not raging → start rage if we have a charge. trySpend is affordability-checked and
            // all-or-nothing, matching legacy consume()'s exact contract (false, no mutation, if
            // current <= 0) — Phase 5 Rage migration, 2026-09-15.
            BarbarianRageResources.ensureInstantiated(player);
            ResourceOperationResult spendResult = PlayerResourceService.INSTANCE.trySpend(player,
                    new ResourceCost.Scalar(PlayerResourceIds.RAGE, 1),
                    ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.ABILITY_COST)));
            if (spendResult.isSuccess()) {
                onToggleOn(player);
                abilities.activateToggle(ID, RAGE_DURATION_TICKS);
            }
        }
    }

    @Override
    public void onToggleOn(ServerPlayer player) {
        // Applying the effect triggers RageEffect.onEffectAdded which handles mechanics,
        // visuals, sound, and the "⚔ Rage!" notification.
        player.addEffect(new MobEffectInstance(ModEffects.RAGE, RAGE_DURATION_TICKS, 0, false, false, true));
    }

    @Override
    public void onToggleOff(ServerPlayer player) {
        // Removing the effect triggers RageEffect.onEffectRemoved which handles cleanup
        // and the "Rage ended." notification (only if toggle was still active).
        player.removeEffect(ModEffects.RAGE);
    }

    @Override
    public void onToggleTick(ServerPlayer player) {
        // Small ambient particles every 10 ticks while raging
        if (player.tickCount % 10 == 0 && player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME,
                    player.getX(), player.getY() + 1, player.getZ(),
                    3, 0.3, 0.3, 0.3, 0.01);
        }
    }

    /**
     * Force-stops rage on death. Deactivates toggle first (so onEffectRemoved won't notify),
     * then removes the effect which triggers registry cleanup via RageEffect.onEffectRemoved.
     * Called from StatsServerEvents.COPY_FROM when alive == false.
     */
    public static void forceStop(ServerPlayer player) {
        AbilityComponent abilities = AbilityComponents.ABILITIES.get((ComponentProvider) player);
        if (abilities.isToggleActive(ID)) {
            abilities.deactivateToggle(ID);
        }
        player.removeEffect(ModEffects.RAGE);
    }

    /** Call this when a Barbarian attacks or takes damage to keep rage alive. */
    public static void refreshCombatTimer(ServerPlayer player) {
        AbilityComponents.ABILITIES.get((ComponentProvider) player)
                .refreshCombatTick(ID, player.tickCount);
    }

    /** Static helper — is this player currently raging? */
    public static boolean isRaging(ServerPlayer player) {
        return AbilityComponents.ABILITIES.get((ComponentProvider) player).isToggleActive(ID);
    }

    /** Rage damage bonus for the current class level. */
    public static int getRageDamageBonus(ServerPlayer player) {
        int classLevel = ClassComponents.get(player).getClassLevel(TotalityClasses.BARBARIAN_ID);
        int idx = Math.max(0, Math.min(classLevel - 1, RAGE_DAMAGE_BONUS.length - 1));
        return RAGE_DAMAGE_BONUS[idx];
    }

    /** Pure formula: Rage's maximum for the player's current Barbarian class level. Delegated to by
     *  {@link RageMaximumResolver} (Phase 5 Rage migration, 2026-09-15) — kept in exactly one place
     *  so the resolver and this class can never drift apart, mirroring {@code ManaMaximumResolver}
     *  delegating to {@code PlayerManaManager.getMaxMana}. */
    public static int getMaxRage(ServerPlayer player) {
        int classLevel = Math.max(1, ClassComponents.get(player).getClassLevel(TotalityClasses.BARBARIAN_ID));
        return RAGE_CHARGES[Math.min(classLevel - 1, RAGE_CHARGES.length - 1)];
    }

    /** Register the rage charge pool for a player. Called on class selection. Routed through the
     *  Generic Player Resource API's grant reconciliation (Phase 5 Rage migration, 2026-09-15) —
     *  a freshly-selected Barbarian's {@code totality:rage} grant now becomes visible to {@link
     *  BarbarianRageResources}'s provider on this very call, instantiating state at the resolved
     *  maximum instead of the legacy {@code ensurePool} call this replaces. */
    public static void registerChargePool(ServerPlayer player) {
        BarbarianRageResources.reconcile(player);
    }

    /** Call on character level-up to update max charges for the new class level. {@code
     *  totality:rage}'s maximum is resolved live on every query via {@link RageMaximumResolver}, so
     *  a level-up's higher maximum is reflected automatically without touching the stored current
     *  value (RAGE_CHARGES is monotonically non-decreasing and no respec/level-down path exists in
     *  production, so no clamp/reconcileMaximum call is needed — see the Phase 5 implementation
     *  report's "updateChargePool" section). External-review correction (2026-09-15): a maximum-only
     *  change is otherwise invisible to the Generic client sync path — nothing else marks
     *  totality:rage dirty when only its resolved maximum changes (unlike current, which is always
     *  marked dirty by whatever mutation changed it) — so the client could stay stuck at a stale
     *  maximum (e.g. 2/2) after the server has already advanced to 2/3, until an unrelated mutation
     *  or a full snapshot happened to catch up. Mirrors {@code PlayerResourceRecalculator.recalculate}
     *  /{@code recalculateAndRestore}'s own "maximum-only-change seam" comment for Mana/Stamina
     *  exactly: {@code markDirty} unconditionally requests a requery, and {@code
     *  PlayerResourceSyncState.computeDeltaAndApply} silently suppresses the packet if the freshly
     *  queried snapshot (current AND maximum) turns out unchanged, so this adds no packet spam. */
    public static void updateChargePool(ServerPlayer player) {
        BarbarianRageResources.ensureInstantiated(player);
        ResourceSyncManager.markDirty(player.getUUID(), PlayerResourceIds.RAGE);
    }

    /** Task Rest integration: Short Rest restores exactly 1 charge, clamped at the resolved maximum
     *  (canonical §25.6) — routed through {@link PlayerResourceService#restore}, which already
     *  clamps at maximum, so no deficit pre-computation is needed the way Long Rest's exact-deficit
     *  restore is (see {@link #onLongRest}). Registered as a {@code RestListener} in {@code
     *  PlayerConnectionEvents} alongside every other Rest-integrated resource. */
    public static void onShortRest(ServerPlayer player) {
        ResourceQueryResult result = PlayerResourceService.INSTANCE.query(player, PlayerResourceIds.RAGE);
        if (!(result instanceof ResourceQueryResult.Success)) return;
        PlayerResourceService.INSTANCE.restore(player, ResourceAmount.scalar(PlayerResourceIds.RAGE, 1),
                ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SHORT_REST)));
    }

    /** Task Rest integration: Long Rest fully restores Rage (canonical §25.6). Mirrors {@code
     *  PlayerStaminaManager#onLongRest}'s exact-deficit pattern so the checked-arithmetic mutation
     *  path cannot spuriously overflow-reject a legitimate full restore. */
    public static void onLongRest(ServerPlayer player) {
        ResourceQueryResult result = PlayerResourceService.INSTANCE.query(player, PlayerResourceIds.RAGE);
        if (!(result instanceof ResourceQueryResult.Success success)) return;
        long deficit = success.snapshot().maximumUnits() - success.snapshot().currentUnits();
        if (deficit <= 0) return;
        PlayerResourceService.INSTANCE.restore(player, ResourceAmount.scalar(PlayerResourceIds.RAGE, deficit),
                ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.LONG_REST)));
    }
}