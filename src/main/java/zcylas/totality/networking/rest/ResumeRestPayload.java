package zcylas.totality.networking.rest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/** C2S — "Keep Resting": resume a rest still within its post-interrupt grace window. */
public record ResumeRestPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ResumeRestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "resume_rest"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResumeRestPayload> STREAM_CODEC =
            StreamCodec.unit(new ResumeRestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
