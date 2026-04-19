package zcylas.totality.api.energy.base;

import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import zcylas.totality.api.energy.UEStorage;

public class SimpleSidedUEContainer extends UEParticipant {

    public enum SideMode {
        INPUT(0xFF0000, "Input"),
        OUTPUT(0x00AA00, "Output"),
        NONE(0x808080, "None");

        private final int color;
        private final String label;

        SideMode(int color, String label) {
            this.color = color;
            this.label = label;
        }

        public int getColor() { return color; }
        public String getLabel() { return label; }

        public SideMode next() {
            return switch (this) {
                case INPUT -> OUTPUT;
                case OUTPUT -> NONE;
                case NONE -> INPUT;
            };
        }

        public boolean allowsInsertion() { return this == INPUT; }
        public boolean allowsExtraction() { return this == OUTPUT; }
    }
    private final SideMode[] sideModes = new SideMode[7];
    private final SideStorage[] sideStorages = new SideStorage[7];

    private long amount = 0;
    private final long capacity;
    private final long maxInsert;
    private final long maxExtract;

    public SimpleSidedUEContainer(long capacity, long maxInsert, long maxExtract) {
        if (capacity < 0) throw new IllegalArgumentException("Capacity must be non-negative");
        if (maxInsert < 0) throw new IllegalArgumentException("maxInsert must be non-negative");
        if (maxExtract < 0) throw new IllegalArgumentException("maxExtract must be non-negative");

        this.capacity = capacity;
        this.maxInsert = maxInsert;
        this.maxExtract = maxExtract;

        for (int i = 0; i < 7; i++) {
            sideModes[i] = SideMode.NONE;
            sideStorages[i] = new SideStorage(i == 6 ? null : Direction.from3DDataValue(i));
        }
    }

    public UEStorage getSideStorage(@Nullable Direction side) {
        return sideStorages[side == null ? 6 : side.get3DDataValue()];
    }

    public SideMode getSideMode(@Nullable Direction side) {
        return sideModes[side == null ? 6 : side.get3DDataValue()];
    }

    public void setSideMode(@Nullable Direction side, SideMode mode) {
        sideModes[side == null ? 6 : side.get3DDataValue()] = mode;
    }

    public void cycleSideMode(@Nullable Direction side) {
        int idx = side == null ? 6 : side.get3DDataValue();
        sideModes[idx] = sideModes[idx].next();
    }

    public long getAmount() { return amount; }
    public long getCapacity() { return capacity; }
    public long getMaxInsert() { return maxInsert; }
    public long getMaxExtract() { return maxExtract; }
    public boolean isFull() { return amount >= capacity; }
    public boolean isEmpty() { return amount == 0; }

    public void setAmountUnchecked(long amount) {
        this.amount = Math.min(amount, capacity);
    }

    @Override
    protected long createSnapshot() { return amount; }

    @Override
    protected void readSnapshot(long snapshot) { amount = snapshot; }

    public void saveToOutput(ValueOutput output) {
        output.putLong("Energy", amount);
        int[] modes = new int[7];
        for (int i = 0; i < 7; i++) modes[i] = sideModes[i].ordinal();
        output.putIntArray("SideModes", modes);
    }

    public void loadFromInput(ValueInput input) {
        setAmountUnchecked(input.getLongOr("Energy", 0L));
        input.getIntArray("SideModes").ifPresent(modes -> {
            SideMode[] values = SideMode.values();
            for (int i = 0; i < 7 && i < modes.length; i++) {
                if (modes[i] >= 0 && modes[i] < values.length) {
                    sideModes[i] = values[modes[i]];
                }
            }
        });
    }

    private class SideStorage implements UEStorage {
        private final @Nullable Direction side;

        private SideStorage(@Nullable Direction side) { this.side = side; }

        private SideMode mode() { return getSideMode(side); }

        @Override
        public boolean supportsInsertion() {
            return side == null || mode().allowsInsertion();
        }

        @Override
        public long insert(long maxAmount, UETransaction transaction) {
            if (maxAmount < 0) throw new IllegalArgumentException("maxAmount must be non-negative");
            if (!supportsInsertion()) return 0;
            long inserted = Math.min(maxInsert, Math.min(maxAmount, capacity - amount));
            if (inserted > 0) {
                updateSnapshots(transaction);
                amount += inserted;
            }
            return inserted;
        }

        @Override
        public boolean supportsExtraction() {
            return side == null || mode().allowsExtraction();
        }

        @Override
        public long extract(long maxAmount, UETransaction transaction) {
            if (maxAmount < 0) throw new IllegalArgumentException("maxAmount must be non-negative");
            if (!supportsExtraction()) return 0;
            long extracted = Math.min(maxExtract, Math.min(maxAmount, amount));
            if (extracted > 0) {
                updateSnapshots(transaction);
                amount -= extracted;
            }
            return extracted;
        }

        @Override public long getAmount() { return amount; }
        @Override public long getCapacity() { return capacity; }
        @Override public long getMaxInsert() { return maxInsert; }
        @Override public long getMaxExtract() { return maxExtract; }
    }
}