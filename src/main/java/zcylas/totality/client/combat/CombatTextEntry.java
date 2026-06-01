// client/combat/CombatTextEntry.java
package zcylas.totality.client.combat;

import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.combat.damage.TotalityDamageType;

public final class CombatTextEntry {

    public enum TextType {
        DAMAGE,     // normal hit — shows number
        IMMUNE,     // immunity — shows "IMMUNE"
        RESIST,     // resistance — shows number, dimmer
        VULNERABLE, // vulnerability — shows number, brighter
        CONDITION,  // condition applied — shows condition name
        HEAL        // future — green number
    }

    private final TextType type;
    private final TotalityDamageType damageType;  // null for CONDITION/HEAL
    private final float amount;                    // 0 for IMMUNE/CONDITION
    private final String label;                    // "IMMUNE", condition name, or null
    private final Vec3 worldPos;
    private int age;                               // ticks since spawned
    private final boolean abbreviated;            // 1.2k vs 1200

    public static final int MAX_AGE = 30;          // 1.5 seconds at 20tps

    public CombatTextEntry(TextType type, TotalityDamageType damageType,
                           float amount, String label, Vec3 worldPos) {
        this.type = type;
        this.damageType = damageType;
        this.amount = amount;
        this.label = label;
        this.worldPos = worldPos;
        this.age = 0;
        this.abbreviated = true; // default, future setting will toggle
    }

    // ── Static factories ──────────────────────────────────────────────────────

    public static CombatTextEntry damage(TotalityDamageType type, float amount, Vec3 pos) {
        return new CombatTextEntry(TextType.DAMAGE, type, amount, null, pos);
    }

    public static CombatTextEntry immune(TotalityDamageType type, Vec3 pos) {
        return new CombatTextEntry(TextType.IMMUNE, type, 0, "IMMUNE", pos);
    }

    public static CombatTextEntry resist(TotalityDamageType type, float amount, Vec3 pos) {
        return new CombatTextEntry(TextType.RESIST, type, amount, null, pos);
    }

    public static CombatTextEntry vulnerable(TotalityDamageType type, float amount, Vec3 pos) {
        return new CombatTextEntry(TextType.VULNERABLE, type, amount, null, pos);
    }

    public static CombatTextEntry condition(TotalityDamageType damageType,
                                            String conditionName, Vec3 pos) {
        return new CombatTextEntry(TextType.CONDITION, damageType, 0, conditionName, pos);
    }

    // ── Display text ──────────────────────────────────────────────────────────

    public String getDisplayText() {
        return switch (type) {
            case IMMUNE, CONDITION -> label;
            case DAMAGE, RESIST, VULNERABLE -> formatAmount(amount);
            case HEAL -> "+" + formatAmount(amount);
        };
    }

    private String formatAmount(float amount) {
        if (!abbreviated) return String.valueOf((int) amount);
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000);
        if (amount >= 1_000)     return String.format("%.1fk", amount / 1_000);
        return String.valueOf((int) amount);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public void tick() { age++; }
    public boolean isExpired() { return age >= MAX_AGE; }

    // ── Getters ───────────────────────────────────────────────────────────────

    public TextType getType() { return type; }
    public TotalityDamageType getDamageType() { return damageType; }
    public float getAmount() { return amount; }
    public Vec3 getWorldPos() { return worldPos; }
    public int getAge() { return age; }
    public float getLifePercent() { return (float) age / MAX_AGE; }
}