// api/combat/condition/ConditionComponent.java
package zcylas.totality.api.combat.condition;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.combat.damage.TotalityDamageType;

import java.util.*;

public final class ConditionComponent {

    private final Map<TotalityCondition, ActiveCondition> active = new LinkedHashMap<>();

    // ── Apply ─────────────────────────────────────────────────────────────────

    public void apply(TotalityCondition condition, int durationTicks,
                      @Nullable LivingEntity applier) {
        ActiveCondition existing = active.get(condition);
        if (existing != null) {
            existing.refresh(durationTicks);
        } else {
            active.put(condition, new ActiveCondition(condition, durationTicks, applier));
        }
    }

    public void applyPermanent(TotalityCondition condition, @Nullable LivingEntity applier) {
        apply(condition, -1, applier);
    }

    // ── Remove ────────────────────────────────────────────────────────────────

    public void remove(TotalityCondition condition) {
        active.remove(condition);
    }

    public void clearAll() {
        active.clear();
    }

    public void clearDebuffs() {
        active.entrySet().removeIf(e -> e.getKey().isDebuff());
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    public boolean has(TotalityCondition condition) {
        return active.containsKey(condition);
    }

    public boolean hasAny(TotalityCondition... conditions) {
        for (TotalityCondition c : conditions) {
            if (has(c)) return true;
        }
        return false;
    }

    @Nullable
    public ActiveCondition get(TotalityCondition condition) {
        return active.get(condition);
    }

    public Collection<ActiveCondition> getAll() {
        return Collections.unmodifiableCollection(active.values());
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public void tick() {
        active.values().forEach(ActiveCondition::tick);
        active.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    // ── Static access ─────────────────────────────────────────────────────────

    public static ConditionComponent get(LivingEntity entity) {
        if (entity instanceof ConditionHolder holder) {
            return holder.totality$getConditions();
        }
        throw new IllegalStateException("Entity missing ConditionComponent: " + entity);
    }

    public static boolean has(LivingEntity entity, TotalityCondition condition) {
        if (entity instanceof ConditionHolder holder) {
            return holder.totality$getConditions().has(condition);
        }
        return false;
    }

    public static void apply(LivingEntity entity, TotalityCondition condition,
                             int durationTicks, @Nullable LivingEntity applier) {
        if (entity instanceof ConditionHolder holder) {
            holder.totality$getConditions().apply(condition, durationTicks, applier);
        }
    }

    public static void applyFromDamageType(LivingEntity target,
                                           @Nullable LivingEntity source,
                                           TotalityDamageType type,
                                           Set<zcylas.totality.api.combat.damage.DamageFlags> flags) {
        // Spell bolts and condition ticks pass NO_CONDITIONS to manage conditions themselves
        if (flags.contains(zcylas.totality.api.combat.damage.DamageFlags.NO_CONDITIONS)) return;

        net.minecraft.resources.Identifier typeId = type.getId();
        if (typeId.equals(zcylas.totality.api.combat.damage.DamageTypes.FIRE.getId())) {
            apply(target, Conditions.BURNING, 60, source);
        } else if (typeId.equals(zcylas.totality.api.combat.damage.DamageTypes.FROST.getId())) {
            apply(target, Conditions.FROZEN, 40, source);
        } else if (typeId.equals(zcylas.totality.api.combat.damage.DamageTypes.POISON.getId())) {
            apply(target, Conditions.POISONED, 100, source);
        }
    }
}