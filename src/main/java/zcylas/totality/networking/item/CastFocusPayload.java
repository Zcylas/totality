package zcylas.totality.networking.item;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Sent client → server when the player right-clicks an
 * {@link zcylas.totality.item.spell_material.ArcaneFocusItem}.
 * Contains the ID of the selected spell to cast.
 */
public record CastFocusPayload(String spellId) implements CustomPacketPayload {

    public static final Type<CastFocusPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "cast_focus"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastFocusPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, CastFocusPayload::spellId,
                    CastFocusPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}