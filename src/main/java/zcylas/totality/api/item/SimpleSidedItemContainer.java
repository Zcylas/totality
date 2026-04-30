package zcylas.totality.api.item;

import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class SimpleSidedItemContainer {

    private final ItemSideMode[] sideModes = new ItemSideMode[7];

    public SimpleSidedItemContainer() {
        for (int i = 0; i < 7; i++) {
            sideModes[i] = ItemSideMode.NONE;
        }
    }

    public ItemSideMode getSideMode(@Nullable Direction side) {
        return sideModes[side == null ? 6 : side.get3DDataValue()];
    }

    public void setSideMode(@Nullable Direction side, ItemSideMode mode) {
        sideModes[side == null ? 6 : side.get3DDataValue()] = mode;
    }

    public void cycleSideMode(@Nullable Direction side) {
        int idx = side == null ? 6 : side.get3DDataValue();
        sideModes[idx] = sideModes[idx].next();
    }

    public void saveToOutput(ValueOutput output) {
        int[] modes = new int[7];
        for (int i = 0; i < 7; i++) modes[i] = sideModes[i].ordinal();
        output.putIntArray("ItemSideModes", modes);
    }

    public void loadFromInput(ValueInput input) {
        input.getIntArray("ItemSideModes").ifPresent(modes -> {
            ItemSideMode[] values = ItemSideMode.values();
            for (int i = 0; i < 7 && i < modes.length; i++) {
                if (modes[i] >= 0 && modes[i] < values.length) {
                    sideModes[i] = values[modes[i]];
                }
            }
        });
    }
}