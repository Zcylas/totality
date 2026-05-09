package zcylas.totality.networking.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.industrial.energy.config.FaceConfig;

public record EnergyFaceConfigPayload(BlockPos pos, Direction face, FaceConfig config)
        implements CustomPacketPayload {

    public static final Type<EnergyFaceConfigPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "energy_face_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnergyFaceConfigPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, EnergyFaceConfigPayload::pos,
                    Direction.STREAM_CODEC, EnergyFaceConfigPayload::face,
                    ByteBufCodecs.INT.map(i -> FaceConfig.values()[i], FaceConfig::ordinal),
                    EnergyFaceConfigPayload::config,
                    EnergyFaceConfigPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}