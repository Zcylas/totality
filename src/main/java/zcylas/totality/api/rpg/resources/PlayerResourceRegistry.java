package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
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
 * No production definitions are registered anywhere in this Phase 1 patch — {@link #INSTANCE} is
 * empty at runtime. Resource migration (Phase 2+) is what actually populates it.
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

    /** Finalizes the registry — structural fields may no longer hot-swap after this (canonical §5.3). */
    public synchronized void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }
}
