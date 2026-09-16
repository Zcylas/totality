package zcylas.totality.api.rpg.classes;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.magic.spell.SpellSlotRecalculator;
import zcylas.totality.api.rpg.resources.MaximumChangePolicy;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.PlayerResourceRegistry;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.PlayerResourceStateComponent;
import zcylas.totality.api.rpg.resources.ResourceCause;
import zcylas.totality.api.rpg.resources.ResourceContext;
import zcylas.totality.api.rpg.resources.ResourceModel;
import zcylas.totality.api.rpg.resources.ResourceOperationResult;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.ResourceStateAuthority;
import zcylas.totality.api.rpg.resources.ResourceStateComponents;
import zcylas.totality.api.rpg.resources.integration.ResourceGrantReconciliation;

import java.util.List;
import java.util.Optional;

/**
 * The one universal post-class-mutation reconciliation seam. Every authoritative
 * {@link PlayerClassComponent} mutation path (first-time class selection, multiclass level-up,
 * subclass selection, and {@code /totality showclass}'s selection reset) calls
 * {@link #reconcile(ServerPlayer)} exactly once after the mutation is fully applied, rather than each
 * call site separately deciding which class-derived systems to touch.
 *
 * <p>This exists because {@code showclass} previously called {@code PlayerClassComponent.resetClass()}
 * directly with no reconciliation at all — a Barbarian's {@code totality:rage} Generic Resource
 * state survived losing the class, and a later, differently-leveled Barbarian re-acquisition could
 * inherit that stale current value against a freshly (and possibly lower) resolved maximum, crashing
 * {@code ResourceScalarWireSnapshot}'s {@code current <= maximum} invariant on the next sync. See the
 * implementation report for the full root-cause trace.
 *
 * <p>Deliberately class-agnostic: this class has no knowledge of Rage, Ki, Pact Magic, or any other
 * specific class-owned resource. Every step below re-derives "what should this player have right now"
 * from the player's CURRENT {@link PlayerClassComponent} state through the same generic mechanisms
 * every other caller already uses — there is no {@code if (classId.equals(BARBARIAN_ID))} branch here,
 * and there must never be one added.
 *
 * <ol>
 *   <li>{@link ResourceGrantReconciliation#reconcileAndSync} — re-evaluates every registered
 *   {@link zcylas.totality.api.rpg.resources.integration.ResourceGrantProvider} (Rage's
 *   {@code BarbarianRageResources} included, plus any future class-owned resource provider) against
 *   the player's current class ownership. A lost grant is removed per its declared
 *   {@link zcylas.totality.api.rpg.resources.integration.ResourceRemovalPolicy}; a freshly (re)granted
 *   resource is instantiated at its declared initialization — never a stale carried-over value.</li>
 *   <li>{@link #clampScalarResourcesAboveResolvedMaximum} — a defensive safety net for any
 *   {@code GENERIC_COMPONENT} scalar resource that stays continuously granted across the mutation
 *   (so step 1 never touches it) but whose dynamically-resolved maximum the mutation just lowered.
 *   No current production class-derived maximum table can actually decrease this way today (see the
 *   implementation report), but this closes the gap generically rather than trusting every future
 *   maximum table to stay monotonic forever.</li>
 *   <li>{@link SpellSlotRecalculator#recalculate} — existing multiclass spell slot derivation.</li>
 * </ol>
 */
public final class ClassChangeReconciler {

    public static void reconcile(ServerPlayer player) {
        ResourceGrantReconciliation.reconcileAndSync(player);
        clampScalarResourcesAboveResolvedMaximum(player);
        SpellSlotRecalculator.recalculate(player);
    }

    /**
     * For every {@code GENERIC_COMPONENT}-authority {@code SCALAR} resource the player currently has
     * live state for, clamps {@code current} down to the freshly resolved maximum if it now exceeds
     * it — canonical §11.4's {@code CLAMP_CURRENT} policy, applied unconditionally here since a bare
     * safety clamp (not a ratio/deficit-preserving reconciliation) needs no "previous maximum" input.
     * A resource whose current grant removal already deleted its state (step 1, above) is simply
     * absent from {@link PlayerResourceStateComponent#instantiatedResourceIds} and skipped; a dormant
     * (inactive) resource is left untouched, matching every other ordinary-gameplay mutation path's
     * dormant-state safety rule.
     */
    private static void clampScalarResourcesAboveResolvedMaximum(ServerPlayer player) {
        PlayerResourceStateComponent state = ResourceStateComponents.get(player);
        for (Identifier resourceId : List.copyOf(state.instantiatedResourceIds())) {
            if (!state.isActive(resourceId)) continue;
            Optional<PlayerResourceDefinition> definitionOpt = PlayerResourceRegistry.INSTANCE.get(resourceId);
            if (definitionOpt.isEmpty()) continue;
            PlayerResourceDefinition definition = definitionOpt.get();
            if (definition.stateAuthority() != ResourceStateAuthority.GENERIC_COMPONENT
                    || definition.model() != ResourceModel.SCALAR) {
                continue;
            }
            ResourceQueryResult result = PlayerResourceService.INSTANCE.query(player, resourceId);
            if (!(result instanceof ResourceQueryResult.Success success)) continue;
            long current = success.snapshot().currentUnits();
            long max = success.snapshot().maximumUnits();
            if (current <= max) continue;

            ResourceOperationResult reconciled = PlayerResourceService.INSTANCE.reconcileMaximum(
                    player, resourceId, max, MaximumChangePolicy.CLAMP_CURRENT,
                    ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.MIGRATION)));
            if (!reconciled.isSuccess()) {
                Totality.LOGGER.warn("[ClassChangeReconciler] {} was above its resolved maximum ({} > {}) after a class "
                        + "change but could not be clamped: {}", resourceId, current, max, reconciled);
            }
        }
    }

    private ClassChangeReconciler() {}
}
