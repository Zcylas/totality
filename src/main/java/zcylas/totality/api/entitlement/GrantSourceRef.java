package zcylas.totality.api.entitlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Provenance of a grant, permanent fact, suspension or revocation (canonical §3.4). The instance id
 * distinguishes two copies of the same source (e.g. two rings). Descriptive only — it is never a
 * substitute for validating the source itself.
 */
public record GrantSourceRef(Identifier sourceTypeId, Identifier sourceId, Optional<UUID> sourceInstanceId) {

    public static final Codec<GrantSourceRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("source_type").forGetter(GrantSourceRef::sourceTypeId),
            Identifier.CODEC.fieldOf("source").forGetter(GrantSourceRef::sourceId),
            UUIDUtil.CODEC.optionalFieldOf("instance").forGetter(GrantSourceRef::sourceInstanceId)
    ).apply(i, GrantSourceRef::new));

    public GrantSourceRef {
        Objects.requireNonNull(sourceTypeId, "sourceTypeId");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(sourceInstanceId, "sourceInstanceId");
    }

    public static GrantSourceRef of(Identifier sourceTypeId, Identifier sourceId) {
        return new GrantSourceRef(sourceTypeId, sourceId, Optional.empty());
    }

    public boolean isDebug() {
        return GrantSourceTypes.DEBUG.equals(sourceTypeId);
    }

    @Override
    public String toString() {
        return sourceTypeId + ":" + sourceId + sourceInstanceId.map(u -> "#" + u).orElse("");
    }
}
