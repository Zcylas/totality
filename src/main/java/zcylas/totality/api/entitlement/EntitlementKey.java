package zcylas.totality.api.entitlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Typed identity of an entitlement-controlled subject (canonical §2.1). The type is a registered,
 * namespaced identifier — never a Java enum — so {@code (totality:ability, totality:flight)} and
 * {@code (totality:spell, totality:flight)} are distinct keys that can never collide.
 */
public record EntitlementKey(Identifier typeId, Identifier contentId) {

    public static final Codec<EntitlementKey> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("type").forGetter(EntitlementKey::typeId),
            Identifier.CODEC.fieldOf("content").forGetter(EntitlementKey::contentId)
    ).apply(i, EntitlementKey::new));

    public EntitlementKey {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(contentId, "contentId");
    }

    public static EntitlementKey of(Identifier typeId, Identifier contentId) {
        return new EntitlementKey(typeId, contentId);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeIdentifier(typeId);
        buf.writeIdentifier(contentId);
    }

    public static EntitlementKey read(FriendlyByteBuf buf) {
        return new EntitlementKey(buf.readIdentifier(), buf.readIdentifier());
    }

    @Override
    public String toString() {
        return typeId + "|" + contentId;
    }
}
