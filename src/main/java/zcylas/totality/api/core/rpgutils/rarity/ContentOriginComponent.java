package zcylas.totality.api.core.rpgutils.rarity;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * The fictional/content source an item belongs to — e.g. Totality, Bleach, Skyrim — shown centred in the tooltip
 * footer. An explicit, authored fact: it is never inferred from the item's registry namespace (Bleach content is
 * registered under {@code totality:}), and it is not the item's acquisition source ("Bloodrayne Raid" is where an
 * item comes from; its origin may still be Bleach).
 *
 * <p>The origin is a namespaced id ({@link ContentOrigins}) whose display label is localized under
 * {@code content_origin.<namespace>.<path>}, so it can later link into the Codex. Encoded as the bare id string.
 */
public record ContentOriginComponent(Identifier origin) {

    public ContentOriginComponent {
        if (origin == null) throw new IllegalArgumentException("origin must not be null");
    }

    public static final Codec<ContentOriginComponent> CODEC =
            Identifier.CODEC.xmap(ContentOriginComponent::new, ContentOriginComponent::origin);

    public static final StreamCodec<ByteBuf, ContentOriginComponent> STREAM_CODEC =
            Identifier.STREAM_CODEC.map(ContentOriginComponent::new, ContentOriginComponent::origin);

    /** Localization key of the origin's display label, e.g. {@code content_origin.totality.bleach}. */
    public String translationKey() {
        return "content_origin." + origin.getNamespace() + "." + origin.getPath();
    }
}
