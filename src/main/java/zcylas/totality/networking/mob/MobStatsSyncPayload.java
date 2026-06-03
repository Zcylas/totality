package zcylas.totality.networking.mob;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

public record MobStatsSyncPayload(int entityId, int level, int rankOrdinal, int ac)
        implements CustomPacketPayload {

    public static final Type<MobStatsSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "mob_stats_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MobStatsSyncPayload> CODEC =
            StreamCodec.of(MobStatsSyncPayload::encode, MobStatsSyncPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, MobStatsSyncPayload p) {
        buf.writeInt(p.entityId());
        buf.writeInt(p.level());
        buf.writeInt(p.rankOrdinal());
        buf.writeInt(p.ac());
    }

    private static MobStatsSyncPayload decode(RegistryFriendlyByteBuf buf) {
        return new MobStatsSyncPayload(buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}