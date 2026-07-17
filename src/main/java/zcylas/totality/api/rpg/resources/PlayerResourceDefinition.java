package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.integration.ResourceGrantInitialization;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Immutable, registry-held metadata describing what a resource <b>is</b>, not what any one player
 * currently has. Never stored in player NBT, never copied per player, never contains a player
 * reference. See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §4.1.
 *
 * This Phase 1 shape intentionally omits fields that only matter once real behavior exists:
 * strategy identifiers (maximum resolver, regeneration strategy, ...) and presentation metadata
 * are deferred to the phase that actually implements resolvers/HUD selection. See the readiness
 * audit's Stage 2 deviation notes.
 */
public record PlayerResourceDefinition(
        Identifier id,
        ResourceModel model,
        ResourcePolarity polarity,
        ResourceStateAuthority stateAuthority,
        Optional<Identifier> externalAdapterId,
        long unitScale,
        long absoluteMinimum,
        OptionalLong authoredBaseMaximum,
        Set<ResourceCapability> capabilities,
        Optional<ResourceTargetRange> targetRange,
        ResourceLifecyclePolicy lifecycle,
        int definitionVersion
) {
    public PlayerResourceDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(polarity, "polarity");
        Objects.requireNonNull(stateAuthority, "stateAuthority");
        Objects.requireNonNull(externalAdapterId, "externalAdapterId");
        Objects.requireNonNull(authoredBaseMaximum, "authoredBaseMaximum");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(targetRange, "targetRange");
        Objects.requireNonNull(lifecycle, "lifecycle");
        capabilities = capabilities.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(capabilities));
    }

    public static Builder builder(Identifier id, ResourceModel model) {
        return new Builder(id, model);
    }

    public static final class Builder {
        private final Identifier id;
        private final ResourceModel model;
        private ResourcePolarity polarity = ResourcePolarity.HIGH_IS_GOOD;
        private ResourceStateAuthority stateAuthority = ResourceStateAuthority.GENERIC_COMPONENT;
        private Optional<Identifier> externalAdapterId = Optional.empty();
        private long unitScale = 1;
        private long absoluteMinimum = 0;
        private OptionalLong authoredBaseMaximum = OptionalLong.empty();
        private final Set<ResourceCapability> capabilities = EnumSet.noneOf(ResourceCapability.class);
        private Optional<ResourceTargetRange> targetRange = Optional.empty();
        private ResourceLifecyclePolicy lifecycle = ResourceLifecyclePolicy.DEFAULT;
        private int definitionVersion = 1;

        private Builder(Identifier id, ResourceModel model) {
            this.id = Objects.requireNonNull(id, "id");
            this.model = Objects.requireNonNull(model, "model");
        }

        public Builder polarity(ResourcePolarity polarity) {
            this.polarity = polarity;
            return this;
        }

        /** Marks this definition as {@link ResourceStateAuthority#EXTERNAL_ADAPTER}-backed. */
        public Builder externalAdapter(Identifier adapterId) {
            this.stateAuthority = ResourceStateAuthority.EXTERNAL_ADAPTER;
            this.externalAdapterId = Optional.of(adapterId);
            return this;
        }

        public Builder unitScale(long unitScale) {
            this.unitScale = unitScale;
            return this;
        }

        public Builder absoluteMinimum(long absoluteMinimum) {
            this.absoluteMinimum = absoluteMinimum;
            return this;
        }

        public Builder authoredBaseMaximum(long authoredBaseMaximum) {
            this.authoredBaseMaximum = OptionalLong.of(authoredBaseMaximum);
            return this;
        }

        public Builder capability(ResourceCapability capability) {
            this.capabilities.add(capability);
            return this;
        }

        public Builder capabilities(ResourceCapability... capabilities) {
            this.capabilities.addAll(Arrays.asList(capabilities));
            return this;
        }

        public Builder targetRange(ResourceTargetRange targetRange) {
            this.targetRange = Optional.of(targetRange);
            return this;
        }

        public Builder lifecycle(ResourceLifecyclePolicy lifecycle) {
            this.lifecycle = lifecycle;
            return this;
        }

        /**
         * Convenience for overriding just the {@link ResourceGrantInitialization} of the current
         * {@link #lifecycle(ResourceLifecyclePolicy)} value (starting from {@link ResourceLifecyclePolicy#DEFAULT}
         * unless {@code lifecycle(...)} was already called), rather than requiring callers to
         * reconstruct the whole policy record just to declare a different initialization.
         */
        public Builder initialization(ResourceGrantInitialization initialization) {
            this.lifecycle = new ResourceLifecyclePolicy(
                    this.lifecycle.deathPolicy(),
                    this.lifecycle.persistThroughLogout(),
                    this.lifecycle.persistThroughDimensionChange(),
                    this.lifecycle.persistTemporaryModifiers(),
                    initialization
            );
            return this;
        }

        public Builder definitionVersion(int definitionVersion) {
            this.definitionVersion = definitionVersion;
            return this;
        }

        public PlayerResourceDefinition build() {
            return new PlayerResourceDefinition(
                    id, model, polarity, stateAuthority, externalAdapterId,
                    unitScale, absoluteMinimum, authoredBaseMaximum,
                    capabilities, targetRange, lifecycle, definitionVersion
            );
        }
    }
}
