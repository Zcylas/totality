package zcylas.totality.api.entitlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One independently removable authorization path (canonical §3.4). Several grants for the same key
 * are redundant paths, never stacked strength (§3.9) — potency and stacking stay with the owning
 * gameplay system.
 *
 * @param providerId          the provider/owner that created the grant; reconciliation and removal are
 *                            scoped to it, so one owner can never delete another owner's grants
 * @param authorizedActionIds actions this grant authorizes; empty means every action the type supports
 * @param priority            only orders UI explanations — it never overrides or deletes other grants (§3.8)
 * @param revealContent       whether the grant makes otherwise-hidden content visible
 */
public record EntitlementGrant(
        UUID grantId,
        EntitlementKey key,
        GrantSourceRef source,
        Identifier providerId,
        Set<Identifier> authorizedActionIds,
        GrantLifetime lifetime,
        Optional<EntitlementClock.Expiry> expiry,
        int priority,
        boolean revealContent,
        boolean progressionEligible
) {

    public static final Codec<EntitlementGrant> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(EntitlementGrant::grantId),
            EntitlementKey.CODEC.fieldOf("key").forGetter(EntitlementGrant::key),
            GrantSourceRef.CODEC.fieldOf("source").forGetter(EntitlementGrant::source),
            Identifier.CODEC.fieldOf("provider").forGetter(EntitlementGrant::providerId),
            Identifier.CODEC.listOf().xmap(Set::copyOf, list -> list.stream().sorted().toList())
                    .optionalFieldOf("actions", Set.of()).forGetter(EntitlementGrant::authorizedActionIds),
            Codec.STRING.xmap(GrantLifetime::valueOf, GrantLifetime::name).fieldOf("lifetime").forGetter(EntitlementGrant::lifetime),
            EntitlementClock.Expiry.CODEC.optionalFieldOf("expiry").forGetter(EntitlementGrant::expiry),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(EntitlementGrant::priority),
            Codec.BOOL.optionalFieldOf("reveal", false).forGetter(EntitlementGrant::revealContent),
            Codec.BOOL.fieldOf("progression_eligible").forGetter(EntitlementGrant::progressionEligible)
    ).apply(i, EntitlementGrant::new));

    public EntitlementGrant {
        Objects.requireNonNull(grantId, "grantId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(lifetime, "lifetime");
        Objects.requireNonNull(expiry, "expiry");
        authorizedActionIds = Set.copyOf(authorizedActionIds);
        // Debug provenance is never progression, whatever the caller asked for (§4.6).
        if (source.isDebug()) progressionEligible = false;
    }

    /** Builds a WHILE_SOURCE_ACTIVE grant whose id is derived from (provider, source, key), so
     *  re-collecting the same source state always yields the same id — reconciliation idempotency. */
    public static EntitlementGrant sourceBound(Identifier providerId, EntitlementKey key, GrantSourceRef source,
                                               Set<Identifier> actions, boolean revealContent) {
        return new EntitlementGrant(deterministicId(providerId, source, key), key, source, providerId, actions,
                GrantLifetime.WHILE_SOURCE_ACTIVE, Optional.empty(), 0, revealContent, !source.isDebug());
    }

    /** Owner of debug-tool grants (commands, dev verification suites). */
    public static final Identifier DEBUG_TOOLS = Identifier.fromNamespaceAndPath("totality", "debug_tools");

    /** A SESSION-lifetime debug grant: non-progression, never persisted, idempotent per (source, key). */
    public static EntitlementGrant debugSession(EntitlementKey key, Identifier debugSourceId) {
        GrantSourceRef source = GrantSourceRef.of(GrantSourceTypes.DEBUG, debugSourceId);
        return new EntitlementGrant(deterministicId(DEBUG_TOOLS, source, key), key, source, DEBUG_TOOLS, Set.of(),
                GrantLifetime.SESSION, Optional.empty(), 0, false, false);
    }

    public static UUID deterministicId(Identifier providerId, GrantSourceRef source, EntitlementKey key) {
        String seed = providerId + "\u0000" + source + "\u0000" + key;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    public boolean authorizes(Identifier actionId) {
        return authorizedActionIds.isEmpty() || authorizedActionIds.contains(actionId);
    }

    public boolean isDebug() {
        return source.isDebug();
    }

    public boolean isExpired(EntitlementClock now) {
        return expiry.isPresent() && expiry.get().isExpired(now);
    }
}
