package zcylas.totality.api.industrial.fluid;

public enum FluidTier {

    BASIC(8_000),       //  8 buckets
    ADVANCED(32_000),   // 32 buckets
    ELITE(128_000),     // 128 buckets
    ULTIMATE(512_000);  // 512 buckets

    private final long capacityMb;

    FluidTier(long capacityMb) {
        this.capacityMb = capacityMb;
    }

    public long getCapacityMb() { return capacityMb; }

    // Fabric uses droplets internally (1 mB = 81 droplets)
    public long getCapacityDroplets() { return capacityMb * 81L; }

    public FluidTier next() {
        FluidTier[] tiers = values();
        int next = this.ordinal() + 1;
        return next < tiers.length ? tiers[next] : this;
    }

}
