package zcylas.totality.networking.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.config.FaceConfig;

import java.util.HashMap;
import java.util.Map;

public record EnergyFaceConfigSyncPayload(BlockPos pos, Map<Direction, FaceConfig> faceMap)
        implements CustomPacketPayload {

    public static final Type<EnergyFaceConfigSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "energy_face_config_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnergyFaceConfigSyncPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, EnergyFaceConfigSyncPayload::pos,
                    ByteBufCodecs.map(
                            HashMap::new,
                            Direction.STREAM_CODEC,
                            ByteBufCodecs.INT.map(i -> FaceConfig.values()[i], FaceConfig::ordinal)
                    ), EnergyFaceConfigSyncPayload::faceMap,
                    EnergyFaceConfigSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}