// api/combat/damage/DamageResistanceComponent.java
package zcylas.totality.api.combat.damage;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class DamageResistanceComponent {

    private final Map<TotalityDamageType, DamageModifier> modifiers = new HashMap<>();

    // ── Lookup ────────────────────────────────────────────────────────────────

    @Nullable
    public DamageModifier getModifier(TotalityDamageType type) {
        return modifiers.get(type);
    }

    public boolean isImmune(TotalityDamageType type) {
        return modifiers.get(type) instanceof DamageModifier.Immunity;
    }

    public boolean isResistant(TotalityDamageType type) {
        return modifiers.get(type) instanceof DamageModifier.Resistance;
    }

    public boolean isVulnerable(TotalityDamageType type) {
        return modifiers.get(type) instanceof DamageModifier.Vulnerability;
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    public void addImmunity(TotalityDamageType type, boolean nonMagicalOnly) {
        modifiers.put(type, new DamageModifier.Immunity(nonMagicalOnly));
    }

    public void addResistance(TotalityDamageType type, boolean nonMagicalOnly) {
        modifiers.put(type, new DamageModifier.Resistance(nonMagicalOnly));
    }

    public void addVulnerability(TotalityDamageType type) {
        modifiers.put(type, new DamageModifier.Vulnerability());
    }

    public void addCustom(TotalityDamageType type, float multiplier) {
        modifiers.put(type, new DamageModifier.Custom(multiplier));
    }

    public void remove(TotalityDamageType type) {
        modifiers.remove(type);
    }

    public void clear() {
        modifiers.clear();
    }

    // ── Static access via entity ──────────────────────────────────────────────

    @Nullable
    public static DamageModifier getModifier(LivingEntity entity, TotalityDamageType type) {
        if (entity instanceof DamageResistanceHolder holder) {
            return holder.totality$getDamageResistances().getModifier(type);
        }
        return null;
    }

    public static DamageResistanceComponent get(LivingEntity entity) {
        if (entity instanceof DamageResistanceHolder holder) {
            return holder.totality$getDamageResistances();
        }
        throw new IllegalStateException("Entity does not have DamageResistanceComponent: " + entity);
    }
}