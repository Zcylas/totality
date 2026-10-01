package zcylas.totality.api.entitlement.integration;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.entitlement.EntitlementKey;
import zcylas.totality.api.entitlement.EntitlementLedger;
import zcylas.totality.api.entitlement.EntitlementMutationResult;
import zcylas.totality.api.entitlement.EntitlementService;
import zcylas.totality.api.entitlement.GrantSourceRef;
import zcylas.totality.api.entitlement.GrantSourceTypes;
import zcylas.totality.api.entitlement.OrphanedEntitlementRecord;
import zcylas.totality.api.entitlement.PermanentEntitlementFact;
import zcylas.totality.api.entitlement.PlayerEntitlementState;
import zcylas.totality.api.magic.spell.Spell;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Phases M2/M3: migrates the pre-Entitlement flat {@code AbilityComponent.unlocked} set (canonical §13).
 * Runs on join after every provider has reconciled, so current provenance can be reconstructed. Per-id and
 * idempotent: each classified id is recorded in the ledger and never processed again; unresolvable ids stay
 * unprocessed (quarantined) so they are migrated automatically if their content is registered again.
 *
 * <p>Rules — no provenance is ever fabricated:
 * <ul>
 *   <li>A spell present only because {@code isDefault()} made every spell available is development
 *   scaffolding and is ignored as progression (never permanent knowledge).</li>
 *   <li>An id a current provider already grants (baseline, class, origin, mastery) is source-bound: nothing
 *   is stored.</li>
 *   <li>A recognizably source-bound ability (Class/Species/Origin/Ancestry/Mastery) that no current source
 *   grants is stale — not migrated, reported.</li>
 *   <li>A non-default spell has no acquisition evidence — not migrated, reported.</li>
 *   <li>Only an unexplained non-source-bound ability falls back to a permanent {@code UNLOCKED} fact with
 *   {@code legacy_migration:ability_component} provenance, flagged non-progression for review.</li>
 *   <li>An id no longer registered is quarantined, never deleted. The legacy list itself is kept unchanged
 *   in {@code AbilityComponent} for one transition release.</li>
 * </ul>
 */
public final class LegacyAbilityMigration {

    public static final GrantSourceRef SOURCE = GrantSourceRef.of(GrantSourceTypes.LEGACY_MIGRATION,
            Identifier.fromNamespaceAndPath("totality", "ability_component"));
    public static final Identifier METHOD = Identifier.fromNamespaceAndPath("totality", "legacy_migration");
    public static final Identifier QUARANTINE_ORIGIN = Identifier.fromNamespaceAndPath("totality", "legacy_ability_component");

    private static final Set<Ability.Source> SOURCE_BOUND_CATEGORIES = EnumSet.of(
            Ability.Source.CLASS, Ability.Source.SPECIES, Ability.Source.ORIGIN, Ability.Source.ANCESTRY,
            Ability.Source.MASTERY);

    public enum Outcome {
        SOURCE_BOUND_EXPLAINED,
        IGNORED_DEVELOPMENT_SPELL,
        STALE_SOURCE_BOUND,
        AMBIGUOUS_SPELL,
        LEGACY_PERMANENT,
        UNKNOWN_QUARANTINED
    }

    /** What the migration needs to know about a legacy id's registered ability. */
    public record AbilityInfo(boolean spell, boolean isDefault, Ability.Source category) {}

    public record Classification(Identifier abilityId, Outcome outcome) {}

    /** Pure classification of not-yet-processed legacy ids. */
    public static List<Classification> classify(Collection<Identifier> legacyIds,
                                                Function<Identifier, Optional<AbilityInfo>> lookup,
                                                Set<Identifier> explainedBySourcesNow,
                                                Set<Identifier> alreadyProcessed) {
        List<Classification> result = new ArrayList<>();
        for (Identifier id : legacyIds) {
            if (alreadyProcessed.contains(id)) continue;
            Optional<AbilityInfo> info = lookup.apply(id);
            Outcome outcome;
            if (info.isEmpty()) {
                outcome = Outcome.UNKNOWN_QUARANTINED;
            } else if (info.get().spell() && info.get().isDefault()) {
                outcome = Outcome.IGNORED_DEVELOPMENT_SPELL;
            } else if (explainedBySourcesNow.contains(id)) {
                outcome = Outcome.SOURCE_BOUND_EXPLAINED;
            } else if (info.get().spell()) {
                outcome = Outcome.AMBIGUOUS_SPELL;
            } else if (SOURCE_BOUND_CATEGORIES.contains(info.get().category()) || info.get().isDefault()) {
                outcome = Outcome.STALE_SOURCE_BOUND;
            } else {
                outcome = Outcome.LEGACY_PERMANENT;
            }
            result.add(new Classification(id, outcome));
        }
        return result;
    }

    /** Applies the migration for one player. Call after {@code reconcileAll}. */
    public static Map<Outcome, List<Identifier>> apply(ServerPlayer player) {
        EntitlementService service = EntitlementService.INSTANCE;
        PlayerEntitlementState state = service.state(player);
        EntitlementLedger ledger = state.ledger();
        Set<Identifier> legacy = AbilityComponents.ABILITIES.get((ComponentProvider) player).getLegacyUnlocked();

        Set<Identifier> explained = state.allGrants()
                .filter(g -> AbilityEntitlements.isAbilityKey(g.key()) && !g.isDebug())
                .map(g -> g.key().contentId())
                .collect(Collectors.toSet());
        List<Classification> classifications = classify(legacy, LegacyAbilityMigration::lookup, explained,
                ledger.processedLegacyAbilityIds());

        Map<Outcome, List<Identifier>> report = new EnumMap<>(Outcome.class);
        for (Classification c : classifications) {
            EntitlementKey key = AbilityEntitlements.keyFor(c.abilityId());
            boolean processed = true;
            switch (c.outcome()) {
                case UNKNOWN_QUARANTINED -> {
                    ledger.quarantineLegacy(new OrphanedEntitlementRecord(key, QUARANTINE_ORIGIN, Map.of(),
                            "legacy unlocked ability id is not registered"));
                    processed = false;
                }
                case LEGACY_PERMANENT -> {
                    EntitlementMutationResult result = service.grantPermanentFact(player, key,
                            PermanentEntitlementFact.UNLOCKED, SOURCE, METHOD, false);
                    processed = !result.isRejected();
                }
                default -> { }
            }
            if (processed) {
                ledger.markLegacyAbilityProcessed(c.abilityId());
                ledger.clearLegacyQuarantine(key);
            }
            report.computeIfAbsent(c.outcome(), o -> new ArrayList<>()).add(c.abilityId());
        }
        if (!report.isEmpty()) {
            Totality.LOGGER.info("[Entitlement] Legacy ability migration for {}: {}", player.getName().getString(), report);
        }
        return report;
    }

    private static Optional<AbilityInfo> lookup(Identifier id) {
        Ability ability = AbilityRegistry.get(id);
        if (ability == null) return Optional.empty();
        return Optional.of(new AbilityInfo(ability instanceof Spell, ability.isDefault(), ability.getSource()));
    }

    private LegacyAbilityMigration() {}
}
