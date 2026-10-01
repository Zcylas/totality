package zcylas.totality.api.entitlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * Provenance of one permanent {@code KNOWN}/{@code UNLOCKED} fact (canonical §3.2).
 *
 * @param acquisitionMethodId how it was acquired, e.g. {@code totality:quest_reward},
 *                            {@code totality:admin_command}, {@code totality:legacy_migration}
 * @param progressionEligible false for administrative or migrated facts that were not legitimately earned
 */
public record PermanentFactRecord(
        long acquiredAtUtc,
        long acquiredAtRevision,
        GrantSourceRef primarySource,
        Identifier acquisitionMethodId,
        boolean progressionEligible
) {

    public static final Codec<PermanentFactRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("acquired_at").forGetter(PermanentFactRecord::acquiredAtUtc),
            Codec.LONG.optionalFieldOf("revision", 0L).forGetter(PermanentFactRecord::acquiredAtRevision),
            GrantSourceRef.CODEC.fieldOf("source").forGetter(PermanentFactRecord::primarySource),
            Identifier.CODEC.fieldOf("method").forGetter(PermanentFactRecord::acquisitionMethodId),
            Codec.BOOL.fieldOf("progression_eligible").forGetter(PermanentFactRecord::progressionEligible)
    ).apply(i, PermanentFactRecord::new));
}
