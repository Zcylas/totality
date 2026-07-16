package zcylas.totality.networking.classes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Sent client → server to spend one unspent class point.
 *
 * {@code classId} may be:
 *   - An existing class the player already has (adds a level to it)
 *   - A new class (begins multiclassing — starts at level 1)
 *
 * The server validates available class points before applying.
 */
public record AddClassLevelPayload(String classId) implements CustomPacketPayload {

    public static final Type<AddClassLevelPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "add_class_level"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AddClassLevelPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AddClassLevelPayload::classId,
                    AddClassLevelPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}