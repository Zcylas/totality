package zcylas.totality.api.rpg.resources.sync;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.ResourceSnapshot;

import java.util.Objects;

/**
 * A client-safe, wire-shaped snapshot of one {@code SCALAR} resource, produced only by converting
 * an already-validated {@link ResourceSnapshot} (never constructed as a side effect of a failed
 * query). Carries only presentation-safe fields — no adapter, owner, or player reference.
 *
 * {@code overflowUnits} is always {@code 0} in Phase 3A: no production resource declares the
 * {@code OVERFLOW} capability yet, and {@link ResourceSnapshot} itself has no overflow field to
 * source one from. The field exists so a future {@code OVERFLOW}-capable resource does not require
 * a wire-shape change — see the Phase 3A implementation report.
 */
public record ResourceScalarWireSnapshot(
        Identifier resourceId,
        long unitScale,
        long currentUnits,
        long maximumUnits,
        long overflowUnits
) {

    public ResourceScalarWireSnapshot {
        Objects.requireNonNull(resourceId, "resourceId");
        if (unitScale < 1) {
            throw new IllegalArgumentException("unitScale must be >= 1, was " + unitScale);
        }
        if (currentUnits < 0 || maximumUnits < 0 || overflowUnits < 0) {
            throw new IllegalArgumentException("currentUnits/maximumUnits/overflowUnits must be >= 0");
        }
        if (currentUnits > Math.addExact(maximumUnits, overflowUnits)) {
            throw new IllegalArgumentException("currentUnits must not exceed maximumUnits + overflowUnits");
        }
    }

    public static ResourceScalarWireSnapshot from(ResourceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new ResourceScalarWireSnapshot(
                snapshot.resourceId(), snapshot.unitScale(), snapshot.currentUnits(), snapshot.maximumUnits(), 0L);
    }

    public static void write(FriendlyByteBuf buf, ResourceScalarWireSnapshot value) {
        buf.writeIdentifier(value.resourceId());
        buf.writeLong(value.unitScale());
        buf.writeLong(value.currentUnits());
        buf.writeLong(value.maximumUnits());
        buf.writeLong(value.overflowUnits());
    }

    public static ResourceScalarWireSnapshot read(FriendlyByteBuf buf) {
        Identifier id = buf.readIdentifier();
        long unitScale = buf.readLong();
        long current = buf.readLong();
        long maximum = buf.readLong();
        long overflow = buf.readLong();
        return new ResourceScalarWireSnapshot(id, unitScale, current, maximum, overflow);
    }
}
