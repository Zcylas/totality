package zcylas.totality.api.rpg.rest;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.TotalityComponent;

/**
 * Durable counterpart to {@link RestManager}'s short-rest cap: persists
 * {@code shortRestsUsedSinceLongRest} through logout/reconnect, death/respawn,
 * dimension changes, and server restarts via the component framework's existing
 * NBT save/load and respawn-copy hooks, instead of living only in memory.
 */
public class RestStateComponent implements TotalityComponent, CopyableComponent<RestStateComponent> {

    private int shortRestsUsedSinceLongRest;

    public int getShortRestsUsed() {
        return shortRestsUsedSinceLongRest;
    }

    public void setShortRestsUsed(int count) {
        this.shortRestsUsedSinceLongRest = Math.clamp(count, 0, RestManager.SHORT_REST_CAP);
    }

    public void incrementShortRestsUsed() {
        setShortRestsUsed(shortRestsUsedSinceLongRest + 1);
    }

    public void resetShortRestsUsed() {
        this.shortRestsUsedSinceLongRest = 0;
    }

    @Override
    public void readData(ValueInput input) {
        setShortRestsUsed(input.getIntOr("ShortRestsUsedSinceLongRest", 0));
    }

    @Override
    public void writeData(ValueOutput output) {
        output.putInt("ShortRestsUsedSinceLongRest", shortRestsUsedSinceLongRest);
    }

    @Override
    public void copyFrom(RestStateComponent other, HolderLookup.Provider registries) {
        this.shortRestsUsedSinceLongRest = other.shortRestsUsedSinceLongRest;
    }
}
