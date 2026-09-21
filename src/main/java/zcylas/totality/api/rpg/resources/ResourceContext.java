package zcylas.totality.api.rpg.resources;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The "why" behind a mutation call — canonical {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md}
 * §20.3, exact field shape. Passed to every {@link PlayerResourceService} mutation method.
 *
 * <p>This foundation pass does not implement the event bus (§20.1/§20.2) or idempotency-request
 * deduplication (§13.4) that would ultimately consume most of these fields — see
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API_PRE_PHASE4_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-15.md}
 * for what is deferred and why. The record still carries every canonical field now (rather than a
 * narrowed subset) so a caller can supply a {@code requestNonce} today without a later signature
 * change once idempotency caching is actually built.
 */
public record ResourceContext(
        ResourceCause cause,
        Optional<Identifier> abilityId,
        Optional<Identifier> spellId,
        Optional<Identifier> itemId,
        Optional<Identifier> restActivityId,
        Optional<BlockPos> position,
        Set<Identifier> flags,
        Optional<UUID> requestNonce
) {
    public ResourceContext {
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(abilityId, "abilityId");
        Objects.requireNonNull(spellId, "spellId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(restActivityId, "restActivityId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(flags, "flags");
        Objects.requireNonNull(requestNonce, "requestNonce");
        flags = Set.copyOf(flags);
    }

    /** The common case: only a cause, no ability/spell/item/rest/position/nonce detail. */
    public static ResourceContext of(ResourceCause cause) {
        return new ResourceContext(cause, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Set.of(), Optional.empty());
    }

    /** Common cause-type identifiers — canonical §20.3's own list, not exhaustive. */
    public static final class CauseTypes {
        public static final Identifier ABILITY_COST = id("ability_cost");
        public static final Identifier SPELL_COST = id("spell_cost");
        public static final Identifier MOVEMENT_COST = id("movement_cost");
        public static final Identifier PASSIVE_REGENERATION = id("passive_regeneration");
        public static final Identifier ENVIRONMENT_DRAIN = id("environment_drain");
        public static final Identifier FOOD_RESTORE = id("food_restore");
        public static final Identifier DRINK_RESTORE = id("drink_restore");
        public static final Identifier SHORT_REST = id("short_rest");
        public static final Identifier LONG_REST = id("long_rest");
        public static final Identifier CLASS_FEATURE = id("class_feature");
        public static final Identifier SPECIES_FEATURE = id("species_feature");
        public static final Identifier EQUIPMENT_MODIFIER = id("equipment_modifier");
        public static final Identifier STATUS_EFFECT = id("status_effect");
        public static final Identifier DEATH = id("death");
        public static final Identifier MIGRATION = id("migration");
        public static final Identifier ADMIN_COMMAND = id("admin_command");

        private static Identifier id(String path) {
            return Identifier.fromNamespaceAndPath("totality", path);
        }

        private CauseTypes() {}
    }
}
