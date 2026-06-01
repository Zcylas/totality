// api/combat/condition/ActiveCondition.java
package zcylas.totality.api.combat.condition;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public final class ActiveCondition {

    private final TotalityCondition condition;
    private int remainingTicks;          // -1 = permanent
    private final boolean permanent;
    @Nullable
    private final LivingEntity applier;  // who applied it, null = environmental

    public ActiveCondition(TotalityCondition condition, int durationTicks,
                           @Nullable LivingEntity applier) {
        this.condition = condition;
        this.remainingTicks = durationTicks;
        this.permanent = durationTicks == -1;
        this.applier = applier;
    }

    public TotalityCondition getCondition() { return condition; }
    public int getRemainingTicks() { return remainingTicks; }
    public boolean isPermanent() { return permanent; }
    @Nullable public LivingEntity getApplier() { return applier; }

    public boolean isExpired() {
        return !permanent && remainingTicks <= 0;
    }

    public void tick() {
        if (!permanent) remainingTicks--;
    }

    // Refresh duration if new application is longer
    public void refresh(int newDurationTicks) {
        if (!permanent) {
            remainingTicks = Math.max(remainingTicks, newDurationTicks);
        }
    }
}