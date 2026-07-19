package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry;
import zcylas.totality.api.rpg.resources.integration.ResourceGrantInitialization;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry of {@link PlayerResourceDefinition}s. See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §5.
 *
 * Production code should use {@link #INSTANCE}. Unlike {@link zcylas.totality.api.core.component.ComponentRegistry},
 * this class stays instantiable rather than a pure static singleton: registration/duplicate/freeze
 * validation needs to be exercised by isolated automated tests without sharing mutable static state
 * across the whole test JVM. See the readiness audit's Stage 2 deviation notes.
 *
 * Phase 1 registered no production definitions ({@link #INSTANCE} was empty at runtime). As of
 * Phase 2A, {@link #INSTANCE} contains exactly two frozen, {@code EXTERNAL_ADAPTER}-authority
 * definitions — {@code totality:health} and {@code totality:food} — registered by
 * {@link ProductionResourceDefinitions#register()}. No {@code GENERIC_COMPONENT} resource is
 * registered yet; that remains later-phase migration work.
 */
public final class PlayerResourceRegistry {

    public static final PlayerResourceRegistry INSTANCE = new PlayerResourceRegistry();

    private final Map<Identifier, PlayerResourceDefinition> definitions = new LinkedHashMap<>();
    private volatile boolean frozen = false;

    public PlayerResourceRegistry() {}

    /**
     * Registers a definition. Rejects duplicate IDs, structurally invalid definitions, and any
     * registration attempted after {@link #freeze()} — never silently replaces an existing entry.
     */
    public synchronized PlayerResourceDefinition register(PlayerResourceDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (frozen) {
            throw new IllegalStateException(
                    "Cannot register " + definition.id() + " — PlayerResourceRegistry is frozen");
        }
        if (definitions.containsKey(definition.id())) {
            throw new IllegalArgumentException("Duplicate resource id: " + definition.id());
        }
        validate(definition);
        definitions.put(definition.id(), definition);
        return definition;
    }

    private void validate(PlayerResourceDefinition d) {
        if (d.unitScale() < 1) {
            throw new IllegalArgumentException(
                    d.id() + ": unitScale must be >= 1, was " + d.unitScale());
        }
        if (d.definitionVersion() < 1) {
            throw new IllegalArgumentException(
                    d.id() + ": definitionVersion must be positive, was " + d.definitionVersion());
        }
        if (d.authoredBaseMaximum().isPresent()
                && d.authoredBaseMaximum().getAsLong() <= d.absoluteMinimum()) {
            throw new IllegalArgumentException(
                    d.id() + ": authoredBaseMaximum (" + d.authoredBaseMaximum().getAsLong()
                            + ") must be greater than absoluteMinimum (" + d.absoluteMinimum() + ")");
        }
        if (d.polarity() == ResourcePolarity.TARGET_RANGE && d.targetRange().isEmpty()) {
            throw new IllegalArgumentException(
                    d.id() + ": TARGET_RANGE polarity requires a targetRange");
        }
        validateTargetRange(d);
        if (d.stateAuthority() == ResourceStateAuthority.EXTERNAL_ADAPTER && d.externalAdapterId().isEmpty()) {
            throw new IllegalArgumentException(
                    d.id() + ": EXTERNAL_ADAPTER authority requires an externalAdapterId");
        }
        if (d.stateAuthority() == ResourceStateAuthority.GENERIC_COMPONENT && d.externalAdapterId().isPresent()) {
            throw new IllegalArgumentException(
                    d.id() + ": GENERIC_COMPONENT authority must not declare an externalAdapterId");
        }
        if (d.model() == ResourceModel.SCALAR
                && d.capabilities().contains(ResourceCapability.PARTITIONED_SPENDING)) {
            throw new IllegalArgumentException(
                    d.id() + ": PARTITIONED_SPENDING capability requires PARTITIONED_POOL model");
        }
        validateInitialization(d);
    }

    /**
     * Cross-checks a declared {@link ResourceTargetRange} against the definition's own bounds:
     * "the preferred range lies inside the absolute bounds" (canonical §8.2). Internal
     * preferred-vs-warning consistency is already enforced by {@link ResourceTargetRange}'s own
     * compact constructor, since that only needs the record's own fields.
     */
    private void validateTargetRange(PlayerResourceDefinition d) {
        if (d.targetRange().isEmpty()) return;
        ResourceTargetRange range = d.targetRange().get();
        if (range.preferredMinimumUnits() < d.absoluteMinimum()) {
            throw new IllegalArgumentException(
                    d.id() + ": targetRange preferredMinimumUnits (" + range.preferredMinimumUnits()
                            + ") must be >= absoluteMinimum (" + d.absoluteMinimum() + ")");
        }
        if (d.authoredBaseMaximum().isPresent()
                && range.preferredMaximumUnits() > d.authoredBaseMaximum().getAsLong()) {
            throw new IllegalArgumentException(
                    d.id() + ": targetRange preferredMaximumUnits (" + range.preferredMaximumUnits()
                            + ") must be <= authoredBaseMaximum (" + d.authoredBaseMaximum().getAsLong() + ")");
        }
    }

    /**
     * Structural validation of the declared default {@link ResourceGrantInitialization} — "invalid
     * absolute declarations" (canonical §16.6): an {@code AtAbsolute} amount must reference this
     * resource's own id, and must (not) specify a partition consistently with the definition's
     * {@link ResourceModel}. {@code AtFraction}/{@code Custom} self-validate in their own compact
     * constructors (invalid fractions / missing strategy identifiers); nothing further to check
     * here for them, or for the data-free {@code AtMinimum}/{@code AtMaximum}/{@code PreserveExisting}.
     */
    private void validateInitialization(PlayerResourceDefinition d) {
        if (!(d.lifecycle().initializationPolicy() instanceof ResourceGrantInitialization.AtAbsolute atAbsolute)) {
            return;
        }
        ResourceAmount amount = atAbsolute.amount();
        if (!amount.resourceId().equals(d.id())) {
            throw new IllegalArgumentException(
                    d.id() + ": AtAbsolute amount must reference this resource's own id, was "
                            + amount.resourceId());
        }
        if (d.model() == ResourceModel.SCALAR && amount.partition().isPresent()) {
            throw new IllegalArgumentException(
                    d.id() + ": SCALAR resource's AtAbsolute amount must not specify a partition");
        }
        if (d.model() == ResourceModel.PARTITIONED_POOL && amount.partition().isEmpty()) {
            throw new IllegalArgumentException(
                    d.id() + ": PARTITIONED_POOL resource's AtAbsolute amount must specify a partition");
        }
    }

    public Optional<PlayerResourceDefinition> get(Identifier id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public boolean isRegistered(Identifier id) {
        return definitions.containsKey(id);
    }

    public Collection<PlayerResourceDefinition> all() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public int size() {
        return definitions.size();
    }

    /**
     * Finalizes the registry — structural fields may no longer hot-swap after this (canonical §5.3).
     * Performs no cross-registry adapter validation; prefer {@link #freeze(ExternalPlayerResourceAdapterRegistry)}
     * whenever any {@code EXTERNAL_ADAPTER} definition might be registered. Kept as a separate
     * overload (rather than requiring every caller to pass an adapter registry) so registries that
     * never register an external-authority definition — including every existing Phase 1 test —
     * are not forced to depend on {@link ExternalPlayerResourceAdapterRegistry}.
     */
    public synchronized void freeze() {
        frozen = true;
    }

    /**
     * Finalizes the registry after confirming every {@code EXTERNAL_ADAPTER} definition already
     * registered here references an adapter actually present in {@code adapterRegistry} (canonical
     * §5.2: "Require a registered external adapter when {@code stateAuthority == EXTERNAL_ADAPTER}").
     * Fails clearly, before freezing, when a definition references a missing adapter — the registry
     * is left unfrozen and every definition registered so far remains untouched.
     */
    public synchronized void freeze(ExternalPlayerResourceAdapterRegistry adapterRegistry) {
        Objects.requireNonNull(adapterRegistry, "adapterRegistry");
        for (PlayerResourceDefinition definition : definitions.values()) {
            if (definition.stateAuthority() != ResourceStateAuthority.EXTERNAL_ADAPTER) continue;
            Identifier adapterId = definition.externalAdapterId().orElseThrow(() -> new IllegalStateException(
                    definition.id() + ": EXTERNAL_ADAPTER definition has no externalAdapterId "
                            + "(should have been rejected at registration time)"));
            if (!adapterRegistry.isRegistered(adapterId)) {
                throw new IllegalStateException(
                        definition.id() + ": references external adapter " + adapterId
                                + ", which is not registered in the given ExternalPlayerResourceAdapterRegistry");
            }
        }
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }
}
