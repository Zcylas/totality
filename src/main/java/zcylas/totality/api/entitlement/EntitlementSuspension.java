package zcylas.totality.api.entitlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Blocks operations on an entitlement without deleting any permanent fact or grant (canonical §3.7).
 *
 * @param blockedActionIds actions blocked; empty blocks every action except {@code view}
 * @param persistent       whether the suspension is saved with the player (story states) or runtime-only
 *                         (e.g. an anti-magic zone re-applied by its owning system)
 */
public record EntitlementSuspension(
        UUID suspensionId,
        EntitlementKey key,
        GrantSourceRef source,
        Set<Identifier> blockedActionIds,
        Identifier reasonCode,
        Optional<EntitlementClock.Expiry> expiry,
        int priority,
        boolean hideInsteadOfDisable,
        boolean persistent
) {

    public static final Codec<EntitlementSuspension> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(EntitlementSuspension::suspensionId),
            EntitlementKey.CODEC.fieldOf("key").forGetter(EntitlementSuspension::key),
            GrantSourceRef.CODEC.fieldOf("source").forGetter(EntitlementSuspension::source),
            Identifier.CODEC.listOf().xmap(Set::copyOf, list -> list.stream().sorted().toList())
                    .optionalFieldOf("actions", Set.of()).forGetter(EntitlementSuspension::blockedActionIds),
            Identifier.CODEC.fieldOf("reason").forGetter(EntitlementSuspension::reasonCode),
            EntitlementClock.Expiry.CODEC.optionalFieldOf("expiry").forGetter(EntitlementSuspension::expiry),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(EntitlementSuspension::priority),
            Codec.BOOL.optionalFieldOf("hide", false).forGetter(EntitlementSuspension::hideInsteadOfDisable),
            Codec.BOOL.optionalFieldOf("persistent", true).forGetter(EntitlementSuspension::persistent)
    ).apply(i, EntitlementSuspension::new));

    public EntitlementSuspension {
        Objects.requireNonNull(suspensionId, "suspensionId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(expiry, "expiry");
        blockedActionIds = Set.copyOf(blockedActionIds);
    }

    public boolean blocks(Identifier actionId) {
        if (blockedActionIds.isEmpty()) return !EntitlementActions.VIEW.equals(actionId) || hideInsteadOfDisable;
        return blockedActionIds.contains(actionId);
    }

    public boolean isExpired(EntitlementClock now) {
        return expiry.isPresent() && expiry.get().isExpired(now);
    }
}
