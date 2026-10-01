package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.entitlement.EntitlementChange.RemovalReason;
import zcylas.totality.api.entitlement.PlayerEntitlementState.AccessibleCacheKey;
import zcylas.totality.api.entitlement.PlayerEntitlementState.AccessibleSet;
import zcylas.totality.api.entitlement.PlayerEntitlementState.DecisionCacheKey;
import zcylas.totality.api.entitlement.PlayerEntitlementState.SourceFailure;
import zcylas.totality.api.entitlement.requirement.EntitlementDependencyKey;
import zcylas.totality.api.entitlement.requirement.EntitlementRequirement;
import zcylas.totality.api.entitlement.requirement.RequirementEvaluation;
import zcylas.totality.api.entitlement.requirement.RequirementEvaluator;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/**
 * The pure decision and mutation engine. It operates on a {@link PlayerEntitlementState} and never touches
 * a {@code ServerPlayer} itself (conditions and contributors may read the player through the query context),
 * which is what makes every rule here unit-testable. {@link EntitlementService} is the server facade that
 * adds players, events and synchronization.
 */
public final class EntitlementEngine {

    private final EntitlementCatalog catalog;

    public EntitlementEngine(EntitlementCatalog catalog) {
        this.catalog = catalog;
    }

    public EntitlementCatalog catalog() {
        return catalog;
    }

    // ══ Queries ═══════════════════════════════════════════════════════════════

    /** Operation-specific decision. {@code SERVER_ENFORCEMENT} queries are always evaluated fresh. */
    public EntitlementDecision evaluate(PlayerEntitlementState state, EntitlementKey key, Identifier actionId,
                                        EntitlementQueryContext context) {
        boolean useCache = context.purpose() != EntitlementQueryContext.Purpose.SERVER_ENFORCEMENT;
        DecisionCacheKey cacheKey = new DecisionCacheKey(key, actionId, context.purpose());
        if (useCache) {
            EntitlementDecision cached = state.decisionCache.get(cacheKey);
            if (cached != null && cached.playerEntitlementRevision() == state.revision()) return cached;
        }
        EntitlementDecision decision = evaluateUncached(state, key, actionId, context);
        if (useCache && decision.cacheable()) state.decisionCache.put(cacheKey, decision);
        return decision;
    }

    /**
     * Revalidates a protected action at execution time (canonical §7.5). A client revision that differs from
     * the current one never rejects a still-valid request; a denial caused by changed state is reported as
     * {@code stale_client_state} with the current decision attached.
     */
    public EntitlementDecision checkServerAction(PlayerEntitlementState state, EntitlementKey key, Identifier actionId,
                                                 EntitlementQueryContext context, OptionalLong clientRevision) {
        EntitlementQueryContext enforcement = context.purpose() == EntitlementQueryContext.Purpose.SERVER_ENFORCEMENT
                ? context
                : new EntitlementQueryContext(context.player(), EntitlementQueryContext.Purpose.SERVER_ENFORCEMENT, context.clock());
        EntitlementDecision decision = evaluate(state, key, actionId, enforcement);
        if (!decision.allowed() && clientRevision.isPresent() && clientRevision.getAsLong() != state.revision()) {
            return decision.withPrimaryReason(EntitlementReasons.STALE_CLIENT_STATE);
        }
        return decision;
    }

    /** Every key of a type that currently allows {@code actionId}, cached until the revision changes or a
     *  dependency it read is invalidated. Candidates are keys with facts or grants plus keys enumerated by the
     *  owning domain APIs' contributors. */
    public Set<EntitlementKey> accessible(PlayerEntitlementState state, Identifier typeId, Identifier actionId,
                                          EntitlementQueryContext context) {
        AccessibleCacheKey cacheKey = new AccessibleCacheKey(typeId, actionId);
        AccessibleSet cached = state.accessibleCache.get(cacheKey);
        if (cached != null && cached.revision() == state.revision()) return cached.keys();

        Candidates candidates = candidates(state, typeId, context);
        Set<EntitlementKey> keys = new LinkedHashSet<>();
        Set<EntitlementDependencyKey> dependencies = new HashSet<>(candidates.dependencies());
        boolean cacheable = candidates.cacheable();
        for (EntitlementKey key : candidates.keys()) {
            EntitlementDecision decision = evaluate(state, key, actionId, context);
            if (decision.allowed()) keys.add(key);
            dependencies.addAll(decision.dependenciesRead());
            cacheable &= decision.cacheable();
        }
        Set<EntitlementKey> result = Set.copyOf(keys);
        if (cacheable) state.accessibleCache.put(cacheKey, new AccessibleSet(result, Set.copyOf(dependencies), state.revision()));
        return result;
    }

    /**
     * The owner's client display view (canonical §7.3): every non-hidden key of each client-view type —
     * explicitly defined content (so visible-but-locked entries appear) plus held content — reported for the
     * type's display action. Hidden content contributes nothing.
     */
    public List<EntitlementDisplaySnapshot> displayView(PlayerEntitlementState state, EntitlementQueryContext context) {
        List<EntitlementDisplaySnapshot> view = new ArrayList<>();
        for (EntitlementTypeDefinition type : catalog.types()) {
            if (!type.clientDisplayView()) continue;
            Set<EntitlementKey> keys = new LinkedHashSet<>();
            for (EntitlementDefinition definition : catalog.definitions()) {
                if (definition.key().typeId().equals(type.id())) keys.add(definition.key());
            }
            keys.addAll(candidateKeys(state, type.id(), context));
            for (EntitlementKey key : keys) {
                if (evaluate(state, key, EntitlementActions.VIEW, context).kind() == EntitlementDecision.Kind.HIDDEN) continue;
                EntitlementDisplaySnapshot.from(evaluate(state, key, type.displayActionId(), context)).ifPresent(view::add);
            }
        }
        return view;
    }

    /** Accessible keys of every availability-tracked type, for its display action. */
    public Map<Identifier, Set<EntitlementKey>> trackedAvailability(PlayerEntitlementState state, EntitlementQueryContext context) {
        Map<Identifier, Set<EntitlementKey>> result = new LinkedHashMap<>();
        for (EntitlementTypeDefinition type : catalog.types()) {
            if (type.trackAvailability()) result.put(type.id(), accessible(state, type.id(), type.displayActionId(), context));
        }
        return result;
    }

    /** Every Availability change since the last call (or baseline), updating the recorded baseline. Computed
     *  from the current state, not from cache contents, so a change is reported even when no decision had been
     *  cached before it happened. Empty until {@link #baselineAvailability} was called. */
    public List<AvailabilityChange> recomputeAvailability(PlayerEntitlementState state, EntitlementQueryContext context) {
        if (state.lastAccessible == null) return List.of();
        Map<Identifier, Set<EntitlementKey>> before = state.lastAccessible;
        Map<Identifier, Set<EntitlementKey>> now = trackedAvailability(state, context);
        state.lastAccessible = now;
        List<AvailabilityChange> changes = new ArrayList<>();
        for (var entry : now.entrySet()) {
            Identifier actionId = catalog.type(entry.getKey()).orElseThrow().displayActionId();
            Set<EntitlementKey> previous = before.getOrDefault(entry.getKey(), Set.of());
            for (EntitlementKey key : previous) if (!entry.getValue().contains(key)) changes.add(new AvailabilityChange(key, actionId, false));
            for (EntitlementKey key : entry.getValue()) if (!previous.contains(key)) changes.add(new AvailabilityChange(key, actionId, true));
        }
        return changes;
    }

    /** Records current availability without reporting it as changes (after join / respawn reconciliation). */
    public void baselineAvailability(PlayerEntitlementState state, EntitlementQueryContext context) {
        state.lastAccessible = trackedAvailability(state, context);
    }

    public record AvailabilityChange(EntitlementKey key, Identifier actionId, boolean available) {}

    public Set<EntitlementKey> candidateKeys(PlayerEntitlementState state, Identifier typeId, EntitlementQueryContext context) {
        return candidates(state, typeId, context).keys();
    }

    private record Candidates(Set<EntitlementKey> keys, Set<EntitlementDependencyKey> dependencies, boolean cacheable) {}

    private Candidates candidates(PlayerEntitlementState state, Identifier typeId, EntitlementQueryContext context) {
        Set<EntitlementKey> keys = new LinkedHashSet<>();
        Set<EntitlementDependencyKey> dependencies = new HashSet<>();
        boolean cacheable = true;
        for (EntitlementKey key : state.ledger.permanent.keySet()) if (key.typeId().equals(typeId)) keys.add(key);
        state.allGrants().map(EntitlementGrant::key).filter(k -> k.typeId().equals(typeId)).forEach(keys::add);
        for (EntitlementStateContributor contributor : catalog.contributors(typeId)) {
            dependencies.addAll(contributor.dependencies());
            try {
                for (EntitlementKey key : contributor.enumerate(typeId, context)) {
                    if (key.typeId().equals(typeId)) keys.add(key);
                }
            } catch (RuntimeException e) {
                // Fail closed: keys this domain could not enumerate are simply not candidates (never authorized).
                recordContributorFailure(state, contributor, e, context.clock());
                cacheable = false;
            }
        }
        return new Candidates(keys, dependencies, cacheable);
    }

    /** Non-expired grants for the key that may authorize: grants of a provider that failed verification
     *  are withheld (kept on record, never used) until that provider succeeds again. */
    public List<EntitlementGrant> activeGrants(PlayerEntitlementState state, EntitlementKey key, EntitlementClock clock) {
        Set<UUID> withheld = withheldIds(state);
        return state.allGrants().filter(g -> g.key().equals(key) && !g.isExpired(clock) && !withheld.contains(g.grantId())).toList();
    }

    private static Set<UUID> withheldIds(PlayerEntitlementState state) {
        if (state.failedProviders.isEmpty()) return Set.of();
        Set<UUID> ids = new HashSet<>();
        state.withheldGrants().forEach(g -> ids.add(g.grantId()));
        return ids;
    }

    private static void recordContributorFailure(PlayerEntitlementState state, EntitlementStateContributor contributor,
                                                 RuntimeException e, EntitlementClock clock) {
        SourceFailure previous = state.contributorFailures.get(contributor.id());
        String error = e.toString();
        if (previous == null) {
            Totality.LOGGER.error("[Entitlement] Contributor {} failed; decisions it applies to fail closed", contributor.id(), e);
            state.contributorFailures.put(contributor.id(), new SourceFailure(error, clock.utcMillis(), clock.utcMillis(), 1));
        } else {
            state.contributorFailures.put(contributor.id(), previous.again(error, clock.utcMillis()));
        }
    }

    private EntitlementDecision evaluateUncached(PlayerEntitlementState state, EntitlementKey key, Identifier actionId,
                                                 EntitlementQueryContext context) {
        long revision = state.revision();
        Optional<EntitlementTypeDefinition> typeOpt = catalog.type(key.typeId());
        if (typeOpt.isEmpty() || !catalog.isRegisteredContent(key)) {
            EntitlementDecision.Kind kind = context.purpose().clientFacing()
                    ? EntitlementDecision.Kind.HIDDEN : EntitlementDecision.Kind.DENIED;
            return bare(kind, EntitlementReasons.UNREGISTERED_CONTENT, key, actionId, revision);
        }
        EntitlementTypeDefinition type = typeOpt.get();
        if (!type.supportsAction(actionId) || !catalog.isAction(actionId)) {
            return bare(EntitlementDecision.Kind.DENIED, EntitlementReasons.SERVER_DENIED, key, actionId, revision);
        }

        Optional<EntitlementDefinition> definition = catalog.definition(key);
        EntitlementRuleSet rules = definition.map(EntitlementDefinition::rules).orElse(EntitlementRuleSet.EMPTY);
        EntitlementClock clock = context.clock();
        Evaluator evaluator = new Evaluator(context);

        Map<PermanentEntitlementFact, PermanentFactRecord> facts = state.ledger.facts(key);
        List<EntitlementGrant> grants = activeGrants(state, key, clock);
        boolean withheld = !state.failedProviders.isEmpty()
                && state.withheldGrants().anyMatch(g -> g.key().equals(key) && !g.isExpired(clock));
        List<EntitlementGrant> grantsForAction = grants.stream().filter(g -> g.authorizes(actionId)).toList();
        List<EntitlementSuspension> suspensions = state.allSuspensions()
                .filter(s -> s.key().equals(key) && !s.isExpired(clock) && s.blocks(actionId)).toList();
        // A time-limited grant or suspension makes the decision time-dependent: never cache it.
        if (grants.stream().anyMatch(g -> g.expiry().isPresent())
                || suspensions.stream().anyMatch(s -> s.expiry().isPresent())) {
            evaluator.cacheable = false;
        }

        EntitlementStateContributor.Collector contributed = new EntitlementStateContributor.Collector();
        for (EntitlementStateContributor contributor : catalog.contributors(key.typeId())) {
            evaluator.dependencies.addAll(contributor.dependencies());
            try {
                contributor.contribute(key, context, contributed);
            } catch (RuntimeException e) {
                // A contributor may supply suspensions or ownership the decision depends on. Its failure must not
                // silently authorize anything: the whole decision fails closed (hidden from clients, denied on
                // the server). Stored facts and grants are untouched, and the next query retries.
                recordContributorFailure(state, contributor, e, clock);
                evaluator.cacheable = false;
                EntitlementDecision.Kind kind = context.purpose().clientFacing()
                        ? EntitlementDecision.Kind.HIDDEN : EntitlementDecision.Kind.DENIED;
                return finish(kind, EntitlementReasons.SOURCE_UNVERIFIED, key, actionId, revision,
                        EntitlementSnapshot.empty(key), List.of(), List.of(), evaluator);
            }
        }

        // ── Visibility (canonical §5.3): held content is always visible to its holder ──
        boolean held = !facts.isEmpty() || !grants.isEmpty() || withheld || contributed.isKnown() || contributed.isOwned()
                || !contributed.paths().isEmpty();
        boolean visibleByRule = rules.visibility().map(r -> evaluator.eval(r).satisfied())
                .orElseGet(() -> definition.flatMap(EntitlementDefinition::visibleByDefault).orElse(type.visibleByDefault()));
        boolean visible = visibleByRule || held || grants.stream().anyMatch(EntitlementGrant::revealContent);
        boolean hideSuspension = suspensions.stream().anyMatch(EntitlementSuspension::hideInsteadOfDisable);
        boolean suspended = !suspensions.isEmpty() || !contributed.suspensionReasons().isEmpty();

        List<EntitlementSnapshot.SourceSummary> sources = new ArrayList<>();
        facts.values().forEach(r -> sources.add(new EntitlementSnapshot.SourceSummary(r.primarySource(), false, r.progressionEligible())));
        grants.forEach(g -> sources.add(new EntitlementSnapshot.SourceSummary(g.source(), true, g.progressionEligible())));

        if (!visible || hideSuspension) {
            return finish(EntitlementDecision.Kind.HIDDEN, EntitlementReasons.HIDDEN, key, actionId, revision,
                    snapshot(key, contributed, facts, grants, false, suspended, List.of(), false, sources),
                    List.of(), List.of(), evaluator);
        }
        if (suspended) {
            return finish(EntitlementDecision.Kind.DENIED, EntitlementReasons.TEMPORARILY_SUSPENDED, key, actionId,
                    revision, snapshot(key, contributed, facts, grants, true, true, List.of(), false, sources),
                    List.of(), List.of(), evaluator);
        }
        if (EntitlementActions.VIEW.equals(actionId)) {
            List<AuthorizationPath> viewPath = List.of(new AuthorizationPath(AuthorizationPath.VISIBLE,
                    Optional.empty(), false, false, true, false));
            return finish(EntitlementDecision.Kind.ALLOWED, EntitlementReasons.ALLOWED, key, actionId, revision,
                    snapshot(key, contributed, facts, grants, true, false, viewPath, true, sources),
                    List.of(), viewPath, evaluator);
        }

        // ── Authorization basis (policy) ──
        Identifier policyId = definition.flatMap(EntitlementDefinition::authorizationPolicyId)
                .orElse(type.defaultAuthorizationPolicyId());
        Optional<EntitlementAuthorizationPolicy> policy = catalog.policy(policyId);
        if (policy.isEmpty()) {
            Totality.LOGGER.error("[Entitlement] {} uses unregistered policy {} — denying", key, policyId);
            return finish(EntitlementDecision.Kind.DENIED, EntitlementReasons.SERVER_DENIED, key, actionId, revision,
                    snapshot(key, contributed, facts, grants, true, false, List.of(), false, sources),
                    List.of(), List.of(), evaluator);
        }
        EntitlementAuthorizationPolicy.Evaluation auth = policy.get().evaluate(new EntitlementAuthorizationPolicy.StateView(
                key, type, definition, facts, grantsForAction, contributed, context), actionId);
        List<AuthorizationPath> paths = new ArrayList<>(auth.paths());
        List<RequirementEvaluation> failures = new ArrayList<>();

        if (paths.isEmpty()) {
            // Explain how it could be obtained, subject to disclosure.
            rules.acquisition().map(evaluator::eval).filter(e -> !e.satisfied()).ifPresent(failures::add);
            // Withheld grants of a failed provider explain the denial: access is unverified, not lost.
            Identifier reason = withheld ? EntitlementReasons.SOURCE_UNVERIFIED : auth.missingBasisReason();
            return finish(EntitlementDecision.Kind.DENIED, reason, key, actionId, revision,
                    snapshot(key, contributed, facts, grants, true, false, List.of(), false, sources),
                    failures, List.of(), evaluator);
        }

        // ── Persistent eligibility: applies to durable access only; never deletes it ──
        if (rules.persistentEligibility().isPresent() && paths.stream().anyMatch(AuthorizationPath::durable)) {
            RequirementEvaluation eligibility = evaluator.eval(rules.persistentEligibility().get());
            if (!eligibility.satisfied()) {
                paths.removeIf(AuthorizationPath::durable);
                failures.add(eligibility);
            }
        }
        if (paths.isEmpty()) {
            return finish(EntitlementDecision.Kind.DENIED, EntitlementReasons.KNOWN_BUT_UNAVAILABLE, key, actionId,
                    revision, snapshot(key, contributed, facts, grants, true, false, List.of(), false, sources),
                    failures, List.of(), evaluator);
        }

        // ── Use-time requirement for this action ──
        EntitlementRequirement actionRequirement = rules.actionRequirements().get(actionId);
        if (actionRequirement != null) {
            RequirementEvaluation evaluation = evaluator.eval(actionRequirement);
            if (!evaluation.satisfied()) {
                failures.add(evaluation);
                boolean realBasis = paths.stream().anyMatch(p -> !AuthorizationPath.REQUIREMENTS.equals(p.pathTypeId()));
                return finish(EntitlementDecision.Kind.DENIED,
                        realBasis ? EntitlementReasons.KNOWN_BUT_UNAVAILABLE : EntitlementReasons.MISSING_REQUIREMENT,
                        key, actionId, revision,
                        snapshot(key, contributed, facts, grants, true, false, List.of(), false, sources),
                        failures, List.of(), evaluator);
            }
        }

        return finish(EntitlementDecision.Kind.ALLOWED, EntitlementReasons.ALLOWED, key, actionId, revision,
                snapshot(key, contributed, facts, grants, true, false, paths, true, sources),
                failures, paths, evaluator);
    }

    private static EntitlementSnapshot snapshot(EntitlementKey key, EntitlementStateContributor.Collector contributed,
                                                Map<PermanentEntitlementFact, PermanentFactRecord> facts,
                                                List<EntitlementGrant> grants, boolean visible, boolean suspended,
                                                List<AuthorizationPath> paths, boolean available,
                                                List<EntitlementSnapshot.SourceSummary> sources) {
        boolean temporarilyAccessible = available && paths.stream().noneMatch(AuthorizationPath::durable)
                && paths.stream().anyMatch(AuthorizationPath::temporary);
        boolean debugOnly = available && !paths.isEmpty() && paths.stream().allMatch(AuthorizationPath::debug);
        return new EntitlementSnapshot(key, contributed.discoveryLevel(), visible,
                facts.containsKey(PermanentEntitlementFact.KNOWN) || contributed.isKnown(),
                contributed.isOwned(),
                facts.containsKey(PermanentEntitlementFact.UNLOCKED),
                !grants.isEmpty(),
                contributed.isSelected(),
                available, temporarilyAccessible, suspended, debugOnly, sources);
    }

    private static EntitlementDecision finish(EntitlementDecision.Kind kind, Identifier reason, EntitlementKey key,
                                              Identifier actionId, long revision, EntitlementSnapshot snapshot,
                                              List<RequirementEvaluation> failures, List<AuthorizationPath> paths,
                                              Evaluator evaluator) {
        return new EntitlementDecision(kind, reason, actionId, snapshot, failures, paths, revision, true,
                evaluator.dependencies, evaluator.cacheable);
    }

    private static EntitlementDecision bare(EntitlementDecision.Kind kind, Identifier reason, EntitlementKey key,
                                            Identifier actionId, long revision) {
        return new EntitlementDecision(kind, reason, actionId, EntitlementSnapshot.empty(key), List.of(), List.of(),
                revision, true, Set.of(), true);
    }

    /** Accumulates dependencies and cacheability across every requirement one decision evaluates. */
    private final class Evaluator {
        private final EntitlementQueryContext context;
        private final Set<EntitlementDependencyKey> dependencies = new HashSet<>();
        private boolean cacheable = true;

        Evaluator(EntitlementQueryContext context) {
            this.context = context;
        }

        RequirementEvaluation eval(EntitlementRequirement requirement) {
            RequirementEvaluation result = RequirementEvaluator.evaluate(requirement, catalog::condition, context);
            dependencies.addAll(result.dependenciesRead());
            cacheable &= result.cacheable();
            return result;
        }
    }

    // ══ Mutations ═════════════════════════════════════════════════════════════

    /**
     * Writes a permanent fact (canonical §3.2, §4.4). Only for content whose retention policy allows an
     * explicit permanent acquisition; debug provenance is always rejected. Idempotent: an existing fact
     * keeps its original provenance.
     */
    public EntitlementMutationResult addPermanentFact(PlayerEntitlementState state, EntitlementKey key,
                                                      PermanentEntitlementFact fact, GrantSourceRef source,
                                                      Identifier acquisitionMethodId, boolean progressionEligible,
                                                      EntitlementClock clock) {
        UUID tx = UUID.randomUUID();
        Optional<EntitlementTypeDefinition> type = catalog.type(key.typeId());
        if (type.isEmpty() || !catalog.isRegisteredContent(key)) {
            return reject(state, key, source, EntitlementReasons.UNREGISTERED_CONTENT, "content is not registered", tx, clock);
        }
        if (source.isDebug()) {
            return reject(state, key, source, EntitlementReasons.DEBUG_ONLY, "debug access can never become a permanent fact", tx, clock);
        }
        if (!catalog.isSourceType(source.sourceTypeId())) {
            return reject(state, key, source, EntitlementReasons.SERVER_DENIED, "unregistered source type", tx, clock);
        }
        if (!type.get().supportsFact(fact)) {
            return reject(state, key, source, EntitlementReasons.SERVER_DENIED, "type does not support permanent " + fact, tx, clock);
        }
        EntitlementRetentionPolicy retention = catalog.definition(key).flatMap(EntitlementDefinition::retentionPolicy)
                .orElse(type.get().defaultRetention());
        if (retention != EntitlementRetentionPolicy.EXPLICIT_PERMANENT_ACQUISITION) {
            return reject(state, key, source, EntitlementReasons.SERVER_DENIED,
                    "retention policy " + retention + " forbids Entitlement-stored permanent facts", tx, clock);
        }
        EnumMap<PermanentEntitlementFact, PermanentFactRecord> facts =
                state.ledger.permanent.computeIfAbsent(key, k -> new EnumMap<>(PermanentEntitlementFact.class));
        if (facts.containsKey(fact)) return EntitlementMutationResult.noChange(tx);

        PermanentFactRecord record = new PermanentFactRecord(clock.utcMillis(), state.revision() + 1, source,
                acquisitionMethodId, progressionEligible);
        facts.put(fact, record);
        state.bumpRevision();
        state.ledger.addAudit(new EntitlementAuditEntry(state.revision(), clock.utcMillis(), EntitlementAuditEntry.OP_FACT_ADDED,
                key, source, acquisitionMethodId, fact.name()));
        return EntitlementMutationResult.applied(List.of(new EntitlementChange.FactAdded(key, fact, record)), tx);
    }

    /** Explicit, audited revocation (canonical §1.3 "Revoked"). Never the result of a requirement failing. */
    public EntitlementMutationResult revokePermanentFact(PlayerEntitlementState state, EntitlementKey key,
                                                         PermanentEntitlementFact fact, GrantSourceRef revokedBy,
                                                         Identifier reasonCode, EntitlementClock clock) {
        UUID tx = UUID.randomUUID();
        EnumMap<PermanentEntitlementFact, PermanentFactRecord> facts = state.ledger.permanent.get(key);
        if (facts == null || !facts.containsKey(fact)) return EntitlementMutationResult.noChange(tx);
        PermanentFactRecord record = facts.remove(fact);
        if (facts.isEmpty()) state.ledger.permanent.remove(key);
        state.bumpRevision();
        state.ledger.addAudit(new EntitlementAuditEntry(state.revision(), clock.utcMillis(), EntitlementAuditEntry.OP_FACT_REVOKED,
                key, revokedBy, reasonCode, fact.name() + " (was " + record.primarySource() + ")"));
        Totality.LOGGER.info("[Entitlement] Revoked {} {} by {} ({})", fact, key, revokedBy, reasonCode);
        return EntitlementMutationResult.applied(List.of(new EntitlementChange.FactRevoked(key, fact, record, revokedBy, reasonCode)), tx);
    }

    /**
     * Adds a SESSION or persisted grant (quest stage, lease, debug session...). Provider-owned
     * WHILE_SOURCE_ACTIVE grants are rejected here — they only arrive through reconciliation. A duplicate
     * grant id is a no-op when the payload matches and rejected when it conflicts (canonical §7.6).
     */
    public EntitlementMutationResult addGrant(PlayerEntitlementState state, EntitlementGrant grant, EntitlementClock clock) {
        UUID tx = UUID.randomUUID();
        Optional<String> problem = validateGrant(grant);
        if (grant.lifetime() == GrantLifetime.WHILE_SOURCE_ACTIVE) problem = Optional.of("source-bound grants come only from providers");
        if (grant.lifetime() == GrantLifetime.PERSISTENT_LEASE && grant.expiry().isEmpty()) problem = Optional.of("a lease needs an expiry");
        if (grant.isDebug() && grant.lifetime() != GrantLifetime.SESSION) problem = Optional.of("debug grants are session-only");
        if (problem.isPresent()) return reject(state, grant.key(), grant.source(), EntitlementReasons.SERVER_DENIED, problem.get(), tx, clock);

        EntitlementGrant existing = state.allGrants().filter(g -> g.grantId().equals(grant.grantId())).findFirst().orElse(null);
        if (existing != null) {
            if (existing.equals(grant)) return EntitlementMutationResult.noChange(tx);
            return reject(state, grant.key(), grant.source(), EntitlementReasons.SERVER_DENIED, "conflicting duplicate grant id", tx, clock);
        }
        (grant.lifetime().persisted() ? state.ledger.persistentGrants : state.sessionGrants).put(grant.grantId(), grant);
        state.bumpRevision();
        return EntitlementMutationResult.applied(List.of(new EntitlementChange.GrantAdded(grant)), tx);
    }

    /** Removes one SESSION/persisted grant. The caller must name the grant's exact source — one owner can
     *  never remove another owner's grant (canonical §14.7). Provider grants are only removed by reconciliation. */
    public EntitlementMutationResult removeGrant(PlayerEntitlementState state, UUID grantId, GrantSourceRef expectedSource,
                                                 EntitlementClock clock) {
        UUID tx = UUID.randomUUID();
        EntitlementGrant grant = state.sessionGrants.get(grantId);
        Map<UUID, EntitlementGrant> store = state.sessionGrants;
        if (grant == null) {
            grant = state.ledger.persistentGrants.get(grantId);
            store = state.ledger.persistentGrants;
        }
        if (grant == null) {
            boolean providerOwned = state.providerGrants.values().stream().anyMatch(m -> m.containsKey(grantId));
            if (providerOwned) {
                return reject(state, null, expectedSource, EntitlementReasons.SERVER_DENIED,
                        "provider grants are removed only by their provider's reconciliation", tx, clock);
            }
            return EntitlementMutationResult.noChange(tx);
        }
        if (!grant.source().equals(expectedSource)) {
            return reject(state, grant.key(), expectedSource, EntitlementReasons.SERVER_DENIED,
                    "source " + expectedSource + " does not own grant from " + grant.source(), tx, clock);
        }
        store.remove(grantId);
        state.bumpRevision();
        return EntitlementMutationResult.applied(List.of(new EntitlementChange.GrantRemoved(grant, RemovalReason.EXPLICIT)), tx);
    }

    /** Removes every SESSION/persisted grant whose source is exactly {@code source}. */
    public EntitlementMutationResult removeGrantsFrom(PlayerEntitlementState state, GrantSourceRef source) {
        UUID tx = UUID.randomUUID();
        List<EntitlementChange> changes = new ArrayList<>();
        for (Map<UUID, EntitlementGrant> store : List.of(state.sessionGrants, state.ledger.persistentGrants)) {
            store.values().removeIf(g -> {
                if (!g.source().equals(source)) return false;
                changes.add(new EntitlementChange.GrantRemoved(g, RemovalReason.EXPLICIT));
                return true;
            });
        }
        if (!changes.isEmpty()) state.bumpRevision();
        return EntitlementMutationResult.applied(changes, tx);
    }

    public EntitlementMutationResult addSuspension(PlayerEntitlementState state, EntitlementSuspension suspension,
                                                   EntitlementClock clock) {
        UUID tx = UUID.randomUUID();
        if (!catalog.isRegisteredContent(suspension.key())) {
            return reject(state, suspension.key(), suspension.source(), EntitlementReasons.UNREGISTERED_CONTENT,
                    "content is not registered", tx, clock);
        }
        Map<UUID, EntitlementSuspension> store = suspension.persistent() ? state.ledger.suspensions : state.runtimeSuspensions;
        EntitlementSuspension existing = store.get(suspension.suspensionId());
        if (existing != null) {
            if (existing.equals(suspension)) return EntitlementMutationResult.noChange(tx);
            return reject(state, suspension.key(), suspension.source(), EntitlementReasons.SERVER_DENIED,
                    "conflicting duplicate suspension id", tx, clock);
        }
        store.put(suspension.suspensionId(), suspension);
        state.bumpRevision();
        return EntitlementMutationResult.applied(List.of(new EntitlementChange.SuspensionAdded(suspension)), tx);
    }

    public EntitlementMutationResult removeSuspension(PlayerEntitlementState state, UUID suspensionId,
                                                      GrantSourceRef expectedSource, EntitlementClock clock) {
        UUID tx = UUID.randomUUID();
        for (Map<UUID, EntitlementSuspension> store : List.of(state.ledger.suspensions, state.runtimeSuspensions)) {
            EntitlementSuspension suspension = store.get(suspensionId);
            if (suspension == null) continue;
            if (!suspension.source().equals(expectedSource)) {
                return reject(state, suspension.key(), expectedSource, EntitlementReasons.SERVER_DENIED,
                        "source does not own this suspension", tx, clock);
            }
            store.remove(suspensionId);
            state.bumpRevision();
            return EntitlementMutationResult.applied(
                    List.of(new EntitlementChange.SuspensionRemoved(suspension, RemovalReason.EXPLICIT)), tx);
        }
        return EntitlementMutationResult.noChange(tx);
    }

    /**
     * The only way temporary access becomes permanent (canonical §3.11): an explicit transaction. A grant
     * never becomes permanent by lasting a long time, and debug / non-progression grants cannot be converted.
     */
    public EntitlementMutationResult convertGrantToPermanent(PlayerEntitlementState state, UUID grantId,
                                                             PermanentEntitlementFact fact, Identifier acquisitionMethodId,
                                                             EntitlementClock clock) {
        UUID tx = UUID.randomUUID();
        EntitlementGrant grant = state.allGrants().filter(g -> g.grantId().equals(grantId) && !g.isExpired(clock))
                .findFirst().orElse(null);
        if (grant == null) return EntitlementMutationResult.rejected(EntitlementReasons.EXPIRED_GRANT, "no active grant " + grantId, tx);
        if (grant.isDebug() || !grant.progressionEligible()) {
            return reject(state, grant.key(), grant.source(), EntitlementReasons.DEBUG_ONLY,
                    "non-progression grants cannot be converted to permanent facts", tx, clock);
        }
        EntitlementMutationResult result = addPermanentFact(state, grant.key(), fact, grant.source(), acquisitionMethodId, true, clock);
        if (result.changed()) {
            state.ledger.addAudit(new EntitlementAuditEntry(state.revision(), clock.utcMillis(),
                    EntitlementAuditEntry.OP_GRANT_CONVERTED, grant.key(), grant.source(), acquisitionMethodId, grantId.toString()));
        }
        return result;
    }

    /**
     * Source-scoped reconciliation (canonical §4.3): diffs a provider's desired grants against that provider's
     * current grants only. Deterministic and idempotent — reconciling the same source state twice changes
     * nothing and does not bump the revision. Grants that are not this provider's, use a foreign source type,
     * a non-source-bound lifetime or unregistered content are rejected and logged, never applied.
     */
    public ReconciliationResult reconcileProvider(PlayerEntitlementState state, Identifier providerId,
                                                  Set<Identifier> allowedSourceTypes, List<EntitlementGrant> desired) {
        UUID tx = UUID.randomUUID();
        Map<UUID, EntitlementGrant> wanted = new LinkedHashMap<>();
        List<String> rejected = new ArrayList<>();
        for (EntitlementGrant grant : desired) {
            Optional<String> problem = validateGrant(grant);
            if (!grant.providerId().equals(providerId)) problem = Optional.of("grant belongs to provider " + grant.providerId());
            else if (!allowedSourceTypes.contains(grant.source().sourceTypeId())) problem = Optional.of("source type not declared by provider");
            else if (grant.lifetime() != GrantLifetime.WHILE_SOURCE_ACTIVE) problem = Optional.of("providers emit only WHILE_SOURCE_ACTIVE grants");
            if (problem.isPresent()) {
                rejected.add(grant.key() + " from " + grant.source() + ": " + problem.get());
                Totality.LOGGER.warn("[Entitlement] Provider {} grant rejected — {} from {}: {}", providerId,
                        grant.key(), grant.source(), problem.get());
                continue;
            }
            wanted.putIfAbsent(grant.grantId(), grant);
        }

        Map<UUID, EntitlementGrant> current = state.providerGrants.computeIfAbsent(providerId, id -> new LinkedHashMap<>());
        List<EntitlementChange> changes = new ArrayList<>();
        for (var it = current.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            EntitlementGrant replacement = wanted.get(entry.getKey());
            if (replacement == null || !replacement.equals(entry.getValue())) {
                changes.add(new EntitlementChange.GrantRemoved(entry.getValue(), RemovalReason.SOURCE_ENDED));
                it.remove();
            }
        }
        for (EntitlementGrant grant : wanted.values()) {
            if (!current.containsKey(grant.grantId())) {
                current.put(grant.grantId(), grant);
                changes.add(new EntitlementChange.GrantAdded(grant));
            }
        }
        if (!changes.isEmpty()) state.bumpRevision();
        return new ReconciliationResult(providerId, changes, rejected, EntitlementMutationResult.applied(changes, tx), Optional.empty());
    }

    /**
     * Collects a provider's grants from its source system and reconciles them (canonical §4.3), safely:
     * if collection throws, the provider is marked unverified — its previous grants stay on record for
     * diagnostics and recovery but stop authorizing — instead of either erasing them or trusting them. A later
     * successful collection clears the failure. Permanent facts are never affected.
     *
     * @param player passed through to the provider; may be null in pure tests
     */
    public ReconciliationResult reconcile(PlayerEntitlementState state, EntitlementGrantProvider provider,
                                          ServerPlayer player, EntitlementClock clock) {
        Identifier providerId = provider.providerId();
        EntitlementGrantProvider.Collector collector = new EntitlementGrantProvider.Collector(providerId);
        try {
            provider.collectGrants(player, collector);
        } catch (RuntimeException e) {
            return markProviderFailed(state, providerId, e, clock);
        }
        boolean recovered = state.failedProviders.remove(providerId) != null;
        ReconciliationResult result = reconcileProvider(state, providerId, provider.sourceTypeIds(), collector.grants());
        if (recovered) {
            Totality.LOGGER.info("[Entitlement] Provider {} recovered", providerId);
            if (result.changes().isEmpty()) state.bumpRevision(); // withheld grants authorize again
        }
        return new ReconciliationResult(providerId, result.changes(), result.rejected(),
                recovered && result.changes().isEmpty()
                        ? new EntitlementMutationResult(EntitlementMutationResult.Status.APPLIED, EntitlementReasons.ALLOWED,
                                "provider recovered", List.of(), result.mutation().transactionId())
                        : result.mutation(), Optional.empty());
    }

    private ReconciliationResult markProviderFailed(PlayerEntitlementState state, Identifier providerId,
                                                    RuntimeException e, EntitlementClock clock) {
        UUID tx = UUID.randomUUID();
        SourceFailure previous = state.failedProviders.get(providerId);
        String error = e.toString();
        if (previous != null) {
            state.failedProviders.put(providerId, previous.again(error, clock.utcMillis()));
            return new ReconciliationResult(providerId, List.of(), List.of(), EntitlementMutationResult.noChange(tx), Optional.of(error));
        }
        Totality.LOGGER.error("[Entitlement] Provider {} failed to collect grants; its previous grants are withheld "
                + "(kept, not used) until it succeeds again", providerId, e);
        state.failedProviders.put(providerId, new SourceFailure(error, clock.utcMillis(), clock.utcMillis(), 1));
        state.bumpRevision();
        return new ReconciliationResult(providerId, List.of(), List.of(),
                new EntitlementMutationResult(EntitlementMutationResult.Status.APPLIED, EntitlementReasons.SOURCE_UNVERIFIED,
                        "provider unverified", List.of(), tx), Optional.of(error));
    }

    public record ReconciliationResult(Identifier providerId, List<EntitlementChange> changes, List<String> rejected,
                                       EntitlementMutationResult mutation, Optional<String> failure) {
        public ReconciliationResult {
            changes = List.copyOf(changes);
            rejected = List.copyOf(rejected);
        }
    }

    /** Removes expired session/persisted grants and suspensions. */
    public EntitlementMutationResult expire(PlayerEntitlementState state, EntitlementClock clock) {
        UUID tx = UUID.randomUUID();
        List<EntitlementChange> changes = new ArrayList<>();
        for (Map<UUID, EntitlementGrant> store : List.of(state.sessionGrants, state.ledger.persistentGrants)) {
            store.values().removeIf(g -> {
                if (!g.isExpired(clock)) return false;
                changes.add(new EntitlementChange.GrantRemoved(g, RemovalReason.EXPIRED));
                return true;
            });
        }
        for (Map<UUID, EntitlementSuspension> store : List.of(state.ledger.suspensions, state.runtimeSuspensions)) {
            store.values().removeIf(s -> {
                if (!s.isExpired(clock)) return false;
                changes.add(new EntitlementChange.SuspensionRemoved(s, RemovalReason.EXPIRED));
                return true;
            });
        }
        if (!changes.isEmpty()) state.bumpRevision();
        return EntitlementMutationResult.applied(changes, tx);
    }

    /** Quarantines records for unregistered content and restores ones whose content returned. */
    public boolean sortOrphans(PlayerEntitlementState state) {
        boolean moved = state.ledger.sortOrphans(catalog::isRegisteredContent);
        if (moved) state.bumpRevision();
        return moved;
    }

    private Optional<String> validateGrant(EntitlementGrant grant) {
        if (catalog.type(grant.key().typeId()).isEmpty()) return Optional.of("unregistered type " + grant.key().typeId());
        if (!catalog.isRegisteredContent(grant.key())) return Optional.of("unregistered content " + grant.key());
        if (!catalog.isSourceType(grant.source().sourceTypeId())) return Optional.of("unregistered source type " + grant.source().sourceTypeId());
        for (Identifier action : grant.authorizedActionIds()) {
            if (!catalog.isAction(action)) return Optional.of("unregistered action " + action);
        }
        return Optional.empty();
    }

    private EntitlementMutationResult reject(PlayerEntitlementState state, EntitlementKey key, GrantSourceRef actor,
                                             Identifier reason, String message, UUID tx, EntitlementClock clock) {
        Totality.LOGGER.warn("[Entitlement] Rejected mutation of {} by {}: {}", key, actor, message);
        if (key != null) {
            state.ledger.addAudit(new EntitlementAuditEntry(state.revision(), clock.utcMillis(), EntitlementAuditEntry.OP_REJECTED,
                    key, actor, reason, message));
        }
        return EntitlementMutationResult.rejected(reason, message, tx);
    }
}
