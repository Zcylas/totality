package zcylas.totality.api.rpg.resources.sync;

import net.minecraft.network.FriendlyByteBuf;

/**
 * One partition's current/maximum/overflow triple within a {@link ResourcePartitionedWireSnapshot}.
 * {@code overflowUnits} is always {@code 0} in Phase 3A — see
 * {@link ResourceScalarWireSnapshot}'s Javadoc for why the field exists regardless.
 */
public record ResourcePartitionWireEntry(int partition, long currentUnits, long maximumUnits, long overflowUnits) {

    public ResourcePartitionWireEntry {
        if (currentUnits < 0 || maximumUnits < 0 || overflowUnits < 0) {
            throw new IllegalArgumentException("currentUnits/maximumUnits/overflowUnits must be >= 0");
        }
        if (currentUnits > Math.addExact(maximumUnits, overflowUnits)) {
            throw new IllegalArgumentException("currentUnits must not exceed maximumUnits + overflowUnits");
        }
    }

    public static void write(FriendlyByteBuf buf, ResourcePartitionWireEntry value) {
        buf.writeVarInt(value.partition());
        buf.writeLong(value.currentUnits());
        buf.writeLong(value.maximumUnits());
        buf.writeLong(value.overflowUnits());
    }

    public static ResourcePartitionWireEntry read(FriendlyByteBuf buf) {
        int partition = buf.readVarInt();
        long current = buf.readLong();
        long maximum = buf.readLong();
        long overflow = buf.readLong();
        return new ResourcePartitionWireEntry(partition, current, maximum, overflow);
    }
}
