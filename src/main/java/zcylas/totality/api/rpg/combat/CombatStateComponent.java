package zcylas.totality.api.rpg.combat;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.ComponentContainer;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.TotalityComponent;

/**
 * Tracks how many ticks remain in the player's combat state.
 * Counts down each tick; reset to COMBAT_DURATION whenever the player
 * deals or receives damage.
 *
 * Server-side only — no sync needed. The client only sees the resulting
 * stamina value, which already syncs via SyncStaminaPayload.
 *
 * Future: masteries can call isInCombat() via CombatStateManager to
 * decide whether to apply the out-of-combat regen bonus.
 */
public final class CombatStateComponent implements TotalityComponent, CopyableComponent<CombatStateComponent> {

    /** Ticks after last hit/attack before combat state ends. 6 seconds = 120 ticks. */
    public static final int COMBAT_DURATION = 120;

    private int timer = 0;

    public CombatStateComponent(ComponentContainer container) {}

    /** Called every server tick. Returns true if the timer just expired. */
    public boolean tick() {
        if (timer > 0) {
            timer--;
            return timer == 0;
        }
        return false;
    }

    /** Reset to full combat duration — call on damage dealt or received. */
    public void reset() {
        timer = COMBAT_DURATION;
    }

    public boolean isInCombat() {
        return timer > 0;
    }

    public int getTimer() {
        return timer;
    }

    @Override
    public void readData(ValueInput input) {
        timer = input.getIntOr("combat_timer", 0);
    }

    @Override
    public void writeData(ValueOutput output) {
        output.putInt("combat_timer", timer);
    }

    @Override
    public void copyFrom(CombatStateComponent other, HolderLookup.Provider registries) {
        this.timer = other.timer;
    }
}