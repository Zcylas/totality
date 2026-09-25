package zcylas.totality.api.core.rpgutils.rarity;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * One classification entry: a broad {@link ItemType} category, optionally paired with a specific type
 * from {@link ClassificationTypes} — e.g. {@code TOOL • AXE}. An item may carry several independent
 * pairs ({@code TOOL • AXE} and {@code WEAPON • TWO-HANDED}); each pair belongs together.
 *
 * <p>Serialization: an entry without a type encodes as the plain category string, exactly the format of
 * the original flat classification list, so existing data decodes unchanged and category-only entries
 * re-encode byte-identically. A paired entry encodes as {@code {"category": "tool", "type": "totality:axe"}}.
 */
public record Classification(ItemType category, Optional<Identifier> type) {

    public Classification {
        if (category == null) throw new IllegalArgumentException("category must not be null");
        type = type == null ? Optional.empty() : type;
    }

    /** A category-only entry — what every legacy flat-list entry decodes to. */
    public static Classification of(ItemType category) {
        return new Classification(category, Optional.empty());
    }

    /** An authored category/type pair. The type must be registered in {@link ClassificationTypes}. */
    public static Classification of(ItemType category, Identifier type) {
        if (!ClassificationTypes.isRegistered(type)) {
            throw new IllegalArgumentException("Unregistered classification type: " + type
                    + " (register it through ClassificationTypes.register first)");
        }
        return new Classification(category, Optional.of(type));
    }

    private static final Codec<Classification> PAIR_CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemType.CODEC.fieldOf("category").forGetter(Classification::category),
            Identifier.CODEC.optionalFieldOf("type").forGetter(Classification::type)
    ).apply(i, Classification::new));

    /** Accepts the legacy plain category string or a pair object; writes the plain string when there is no type. */
    public static final Codec<Classification> CODEC = Codec.either(ItemType.CODEC, PAIR_CODEC).xmap(
            either -> either.map(Classification::of, pair -> pair),
            entry -> entry.type().isEmpty() ? Either.left(entry.category()) : Either.right(entry));

    public static final StreamCodec<ByteBuf, Classification> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(s -> ItemType.valueOf(s.toUpperCase()), ItemType::getSerializedName),
            Classification::category,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC),
            Classification::type,
            Classification::new);
}
