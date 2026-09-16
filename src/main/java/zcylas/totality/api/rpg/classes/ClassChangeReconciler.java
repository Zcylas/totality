package zcylas.totality.api.rpg.classes;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.resources.MaximumChangePolicy;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.PlayerResourceRegistry;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.PlayerResourceStateComponent;
import zcylas.totality.api.rpg.resources.ResourceAmount;
import zcylas.totality.api.rpg.resources.ResourceCause;
import zcylas.totality.api.rpg.resources.ResourceContext;
import zcylas.totality.api.rpg.resources.ResourceMaximum;
import zcylas.totality.api.rpg.resources.ResourceModel;
import zcylas.totality.api.rpg.resources.ResourceOperationResult;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.ResourceResolutionContext;
import zcylas.totality.api.rpg.resources.ResourceStateAuthority;
import zcylas.totality.api.rpg.resources.ResourceStateComponents;
import zcylas.totality.api.rpg.resources.integration.ResourceGrantPolicyRegistry;
import zcylas.totality.api.rpg.resources.integration.ResourceGrantReconciliation;
import zcylas.totality.networking.resource.ResourceSyncManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *   <li>{@link #reconcileResourcesAgainstResolvedMaximum} — reconciles {@code current} against the
 *   freshly resolved maximum, per the resource's own declared {@link MaximumChangePolicy}, for any
 *   {@code GENERIC_COMPONENT} resource (scalar or partitioned — e.g. Rage, or Standard Spell Slots)
 *   that stays continuously granted across the mutation (so step 1 never touches it) but whose
 *   dynamically-resolved maximum the mutation just moved — in either direction; see {@link
 *   #reconcileScalar}/{@link #reconcilePartitioned} for the exact per-model behavior. <b>Correction
 *   pass (2026-09-16, Finding 1):</b> a class-derived maximum decreasing was originally believed to
 *   have no current production trigger — that was incorrect. No progression *table* decreases as a
 *   function of level (every table is monotonically non-decreasing by row), but the *level itself*
 *   can be forced downward outside ordinary level-up flow — e.g. {@code PlayerConnectionEvents}' own
 *   JOIN-time normalization clamping a class's stored level down to what the player's current
 *   character level can support (reachable after {@code /totality resetlevel}/{@code resetstats}/
 *   {@code resetall}, none of which touch {@code PlayerClassComponent}) — which resolves to exactly
 *   the same lower-maximum shape this step exists to catch. See the Phase 6 correction-pass report
 *   for the full trace.</li>
 * </ol>
 *
 * <p>There is deliberately no separate "recalculate Spell Slots" step: once {@code
 * totality:spell_slots} is granted (step 1), its maximum is resolved live on every query through
 * {@code StandardSpellSlotMaximumResolver} — nothing needs to be eagerly recomputed or written down
 * the way the retired {@code SpellSlotRecalculator.recalculate(ServerPlayer)} used to.
 *
 * <p><b>Correction pass (2026-09-16) — maximum-increase regression.</b> Step 1 deliberately never
 * refills a resource that stays continuously granted across the mutation (canonical §16.6: "the
 * stored value is never refilled by re-reconciliation"). That is correct for the common case, but it
 * means a resource whose resolved maximum just <i>grew</i> (e.g. a multiclass level-up unlocking a
 * new Standard Spell Slot tier) was never reconciled at all — {@link #reconcileResourcesAgainstResolvedMaximum}
 * used to only clamp {@code current} down when it exceeded the new maximum, with no symmetric handling
 * for growth. {@link #reconcile(ServerPlayer, Map)} now takes an optional {@code Map} of each
 * resource's maximum <i>as resolved immediately before the mutation</i> (captured by the caller via
 * {@link #captureResolvedMaximums} — this class cannot capture it itself, since every call site already
 * applies its mutation before calling {@code reconcile}) and applies each resource's own declared
 * {@link MaximumChangePolicy} (from {@link zcylas.totality.api.rpg.resources.integration.ResourceGrantPolicy#maximumChangePolicy()})
 * against it — {@code CLAMP_CURRENT} by default (identical to the old clamp-only behavior), or
 * {@code PRESERVE_DEFICIT} for Standard Spell Slots (see {@code StandardSpellSlotResources#register()}),
 * which both clamps a shrinking maximum and grants newly gained capacity while leaving already-spent
 * capacity spent. The no-arg {@link #reconcile(ServerPlayer)} overload (used by every call site that
 * has no growth-preserving mutation to reconcile against — first-time class selection, subclass
 * selection, class reset, and JOIN-time level normalization) passes an empty map, which degenerates
 * to exactly the old clamp-only behavior for every resource regardless of its declared policy — see
 * {@link #reconcileScalar}/{@link #reconcilePartitioned}'s own Javadoc for why that degeneration is
 * safe. See the Phase 6 correction-pass report for the full root-cause trace and manual reproduction.
 */
public final class ClassChangeReconciler {

    public static void reconcile(ServerPlayer player) {
        reconcile(player, Map.of());
    }

    /**
     * @param resolvedMaximumsBeforeMutation each currently-active {@code GENERIC_COMPONENT}
     *                                       resource's maximum as resolved immediately before the
     *                                       class mutation this call is reconciling, from {@link
     *                                       #captureResolvedMaximums}. An empty map degenerates to
     *                                       clamp-only behavior (see this class's Javadoc).
     */
    public static void reconcile(ServerPlayer player, Map<Identifier, ResourceMaximum> resolvedMaximumsBeforeMutation) {
        ResourceGrantReconciliation.reconcileAndSync(player);
        reconcileResourcesAgainstResolvedMaximum(player, resolvedMaximumsBeforeMutation);
    }

    /**
     * Captures every currently-active {@code GENERIC_COMPONENT} resource's resolved maximum — call
     * this <i>before</i> applying a mutation that might change one (e.g. {@code
     * PlayerClassComponent#addClassLevel}), then pass the result to {@link #reconcile(ServerPlayer, Map)}
     * after the mutation, so a resource that stays continuously granted (and therefore untouched by
     * grant reconciliation's own step) can still have newly gained capacity granted per its declared
     * {@link MaximumChangePolicy}. Skipping this call (using the no-arg {@link #reconcile(ServerPlayer)}
     * instead) is correct whenever the caller's mutation cannot plausibly grow a live resource's
     * maximum (first-time class selection always instantiates fresh state; subclass selection and
     * class reset do not currently affect any resource's maximum).
     */
    public static Map<Identifier, ResourceMaximum> captureResolvedMaximums(ServerPlayer player) {
        PlayerResourceStateComponent state = ResourceStateComponents.get(player);
        Map<Identifier, ResourceMaximum> before = new LinkedHashMap<>();
        for (Identifier resourceId : List.copyOf(state.instantiatedResourceIds())) {
            if (!state.isActive(resourceId)) continue;
            Optional<PlayerResourceDefinition> definitionOpt = PlayerResourceRegistry.INSTANCE.get(resourceId);
            if (definitionOpt.isEmpty()) continue;
            PlayerResourceDefinition definition = definitionOpt.get();
            if (definition.stateAuthority() != ResourceStateAuthority.GENERIC_COMPONENT) continue;
            PlayerResourceService.INSTANCE.resolveMaximum(player, definition, ResourceResolutionContext.EMPTY)
                    .ifPresent(max -> before.put(resourceId, max));
        }
        return before;
    }

    /**
     * For every {@code GENERIC_COMPONENT}-authority resource the player currently has live state
     * for, reconciles {@code current} against the freshly resolved maximum per the resource's own
     * declared {@link MaximumChangePolicy} — canonical §11.4. A resource whose current grant removal
     * already deleted its state (step 1, above) is simply absent from {@link
     * PlayerResourceStateComponent#instantiatedResourceIds} and skipped; a dormant (inactive)
     * resource is left untouched; a resource freshly (re)instantiated this same pass (absent from
     * {@code before} — step 1 already initialized it correctly via {@code AtMaximum}) is also left
     * untouched, matching every other ordinary-gameplay mutation path's dormant-state safety rule.
     */
    private static void reconcileResourcesAgainstResolvedMaximum(ServerPlayer player, Map<Identifier, ResourceMaximum> before) {
        PlayerResourceStateComponent state = ResourceStateComponents.get(player);
        for (Identifier resourceId : List.copyOf(state.instantiatedResourceIds())) {
            if (!state.isActive(resourceId)) continue;
            Optional<PlayerResourceDefinition> definitionOpt = PlayerResourceRegistry.INSTANCE.get(resourceId);
            if (definitionOpt.isEmpty()) continue;
            PlayerResourceDefinition definition = definitionOpt.get();
            if (definition.stateAuthority() != ResourceStateAuthority.GENERIC_COMPONENT) continue;

            ResourceMaximum previous = before.get(resourceId);
            if (definition.model() == ResourceModel.SCALAR) {
                ResourceMaximum.Scalar previousScalar =
                        previous instanceof ResourceMaximum.Scalar scalar ? scalar : null;
                reconcileScalar(player, resourceId, previousScalar);
            } else {
                ResourceMaximum.Partitioned previousPartitioned =
                        previous instanceof ResourceMaximum.Partitioned partitioned ? partitioned : null;
                reconcilePartitioned(player, definition, resourceId, state, previousPartitioned);
            }
        }
    }

    /**
     * {@code previousMax == null} (no genuine pre-mutation capture — every call site but {@code
     * AddClassLevelHandler}) is treated as "the maximum did not change": {@code
     * previousMaximumUnits} defaults to the freshly resolved maximum itself, which makes {@link
     * MaximumChangePolicy#reconcileCurrent} degenerate to plain clamp-down for every policy (a
     * PRESERVE_DEFICIT resource with {@code previousMax == newMax} always computes {@code
     * deficit = max(0, newMax - current)}, so {@code reconciled = newMax - deficit == current} when
     * {@code current <= newMax}, and {@code == newMax} — an ordinary clamp — when it doesn't). This
     * is deliberate: without a real "before" snapshot there is no way to distinguish "already at the
     * new, lower maximum" from "partially spent at a higher maximum that just dropped," so assuming
     * no change is the only safe default — exactly reproducing the pre-correction-pass clamp-only
     * behavior for JOIN-time level normalization and class reset.
     */
    private static void reconcileScalar(ServerPlayer player, Identifier resourceId, ResourceMaximum.Scalar previousMax) {
        ResourceQueryResult result = PlayerResourceService.INSTANCE.query(player, resourceId);
        if (!(result instanceof ResourceQueryResult.Success success)) return;
        long current = success.snapshot().currentUnits();
        long newMax = success.snapshot().maximumUnits();
        long previousMaximumUnits = previousMax != null ? previousMax.effectiveUnits() : newMax;
        if (current <= newMax && previousMaximumUnits == newMax) return; // nothing moved, nothing to reconcile

        MaximumChangePolicy policy = ResourceGrantPolicyRegistry.INSTANCE.get(resourceId).maximumChangePolicy();
        ResourceOperationResult reconciled = PlayerResourceService.INSTANCE.reconcileMaximum(
                player, resourceId, previousMaximumUnits, policy,
                ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.MIGRATION)));
        if (!reconciled.isSuccess()) {
            Totality.LOGGER.warn("[ClassChangeReconciler] {} could not be reconciled against its resolved maximum "
                    + "(previous={}, new={}, policy={}) after a class change: {}",
                    resourceId, previousMaximumUnits, newMax, policy, reconciled);
        }
    }

    /** Partitioned counterpart of {@link #reconcileScalar} — no {@code reconcileMaximum} primitive
     *  exists for {@code PARTITIONED_POOL} on {@link PlayerResourceService}, so this applies {@link
     *  MaximumChangePolicy#reconcileCurrent} per partition directly and moves the delta with the
     *  existing {@link PlayerResourceService#drain}/{@link PlayerResourceService#restore} primitives
     *  (both already mark the resource dirty for the sync path on success — this is also what
     *  guarantees the client sees a newly unlocked tier without reconnecting; see the correction-pass
     *  report). {@code previousMax == null} defaults every partition's previous maximum to its own
     *  freshly resolved maximum, for the same "assume no change" reasoning as {@link #reconcileScalar}.
     *
     *  <p><b>Cleanup pass (2026-09-16) — maximum-only sync edge:</b> a partition's resolved maximum
     *  can legitimately change while its reconciled {@code current} stays the same (e.g. {@code
     *  CLAMP_CURRENT} with {@code current} already below both the old and new maximum, or a {@code
     *  PRESERVE_DEFICIT} case whose math nets to the same value) — no {@code drain}/{@code restore}
     *  call runs for that partition, so nothing marks the resource dirty even though the client's
     *  cached maximum is now stale. If a genuine "before" snapshot shows at least one partition's
     *  maximum actually moved and no partition's {@code current} was mutated this pass, the resource
     *  is explicitly marked dirty once. This can only fire when a real {@code previousMax} was
     *  supplied — the {@code previousMax == null} degenerate default sets every partition's "previous"
     *  equal to its own freshly resolved maximum, so a maximum change can never be detected (and
     *  nothing is force-marked dirty) on every ordinary class change the way the task's "do not force
     *  every partitioned resource dirty" constraint requires. */
    private static void reconcilePartitioned(
            ServerPlayer player, PlayerResourceDefinition definition, Identifier resourceId,
            PlayerResourceStateComponent state, ResourceMaximum.Partitioned previousMax) {
        var partitionedState = state.getPartitioned(resourceId);
        if (partitionedState.isEmpty()) return;
        Optional<ResourceMaximum> maxOpt = PlayerResourceService.INSTANCE.resolveMaximum(player, definition, ResourceResolutionContext.EMPTY);
        if (maxOpt.isEmpty() || !(maxOpt.get() instanceof ResourceMaximum.Partitioned partitionedMax)) return;

        MaximumChangePolicy policy = ResourceGrantPolicyRegistry.INSTANCE.get(resourceId).maximumChangePolicy();
        long floor = definition.absoluteMinimum();
        boolean mutated = false;
        boolean maximumChanged = false;

        for (Map.Entry<Integer, Long> entry : partitionedMax.effectiveByPartition().entrySet()) {
            int partition = entry.getKey();
            long newMax = entry.getValue();
            long current = partitionedState.get().getCurrent(partition);
            long previousMaximumUnits = previousMax != null
                    ? previousMax.effectiveByPartition().getOrDefault(partition, newMax)
                    : newMax;
            if (previousMaximumUnits != newMax) maximumChanged = true;

            long reconciled = policy.reconcileCurrent(current, previousMaximumUnits, newMax, floor);
            long delta = reconciled - current;
            if (delta == 0) continue;
            mutated = true;

            ResourceContext context = ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.MIGRATION));
            ResourceOperationResult result = delta > 0
                    ? PlayerResourceService.INSTANCE.restore(player, ResourceAmount.partitioned(resourceId, partition, delta), context)
                    : PlayerResourceService.INSTANCE.drain(player, ResourceAmount.partitioned(resourceId, partition, -delta), context);
            if (!result.isSuccess()) {
                Totality.LOGGER.warn("[ClassChangeReconciler] {} partition {} could not be reconciled against its "
                        + "resolved maximum (previous={}, new={}, policy={}) after a class change: {}",
                        resourceId, partition, previousMaximumUnits, newMax, policy, result);
            }
        }

        if (maximumChanged && !mutated) {
            ResourceSyncManager.markDirty(player.getUUID(), resourceId);
        }
    }

    private ClassChangeReconciler() {}
}
