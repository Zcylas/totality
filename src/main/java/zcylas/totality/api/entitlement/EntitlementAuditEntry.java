package zcylas.totality.api.entitlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * One persisted audit-log line for an uncommon, deliberate mutation — explicit revocation, admin
 * unlocks, migrations (canonical §1.3 "Revoked", §13 M10).
 */
public record EntitlementAuditEntry(
        long revision,
        long utcMillis,
        Identifier operation,
        EntitlementKey key,
        GrantSourceRef actor,
        Identifier reasonCode,
        String detail
) {

    public static final Identifier OP_FACT_ADDED = Identifier.fromNamespaceAndPath("totality", "fact_added");
    public static final Identifier OP_FACT_REVOKED = Identifier.fromNamespaceAndPath("totality", "fact_revoked");
    public static final Identifier OP_GRANT_CONVERTED = Identifier.fromNamespaceAndPath("totality", "grant_converted");
    public static final Identifier OP_REJECTED = Identifier.fromNamespaceAndPath("totality", "rejected");

    public static final Codec<EntitlementAuditEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("revision").forGetter(EntitlementAuditEntry::revision),
            Codec.LONG.fieldOf("time").forGetter(EntitlementAuditEntry::utcMillis),
            Identifier.CODEC.fieldOf("op").forGetter(EntitlementAuditEntry::operation),
            EntitlementKey.CODEC.fieldOf("key").forGetter(EntitlementAuditEntry::key),
            GrantSourceRef.CODEC.fieldOf("actor").forGetter(EntitlementAuditEntry::actor),
            Identifier.CODEC.fieldOf("reason").forGetter(EntitlementAuditEntry::reasonCode),
            Codec.STRING.optionalFieldOf("detail", "").forGetter(EntitlementAuditEntry::detail)
    ).apply(i, EntitlementAuditEntry::new));
}
