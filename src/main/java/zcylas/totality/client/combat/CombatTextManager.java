// client/combat/CombatTextManager.java
package zcylas.totality.client.combat;

import net.minecraft.world.phys.Vec3;
import zcylas.totality.Totality;
import zcylas.totality.api.combat.damage.TotalityDamageType;
import zcylas.totality.api.combat.condition.TotalityCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class CombatTextManager {

    private static final List<CombatTextEntry> ENTRIES = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private CombatTextManager() {}

    // ── Spawn ─────────────────────────────────────────────────────────────────

    public static void spawnDamage(TotalityDamageType type, float amount,
                                   Vec3 entityPos, boolean resisted, boolean vulnerable) {
        Vec3 pos = randomOffset(entityPos);
        CombatTextEntry entry;
        if (resisted) {
            entry = CombatTextEntry.resist(type, amount, pos);
        } else if (vulnerable) {
            entry = CombatTextEntry.vulnerable(type, amount, pos);
        } else {
            entry = CombatTextEntry.damage(type, amount, pos);
        }
        ENTRIES.add(entry);
    }

    public static void spawnImmune(TotalityDamageType type, Vec3 entityPos) {
        ENTRIES.add(CombatTextEntry.immune(type, randomOffset(entityPos)));
    }

    public static void spawnCondition(TotalityDamageType damageType,
                                      TotalityCondition condition, Vec3 entityPos) {
        ENTRIES.add(CombatTextEntry.condition(
                damageType,
                condition.getDisplayName(),
                randomOffset(entityPos)
        ));
    }

    /** Block impacts are shown at the exact struck point (no entity-height offset), lightly jittered. */
    public static void spawnBlockDamage(float amount, Vec3 hitPos, int style) {
        ENTRIES.add(CombatTextEntry.blockDamage(amount, jitter(hitPos)).withStyle(style));
    }

    public static void spawnIneffective(String label, Vec3 hitPos) {
        ENTRIES.add(CombatTextEntry.ineffective(label, jitter(hitPos)));
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public static void tick() {
        ENTRIES.forEach(CombatTextEntry::tick);
        ENTRIES.removeIf(CombatTextEntry::isExpired);
    }

    // ── Access ────────────────────────────────────────────────────────────────

    public static List<CombatTextEntry> getEntries() {
        return ENTRIES;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Vec3 jitter(Vec3 base) {
        return new Vec3(
                base.x + (RANDOM.nextFloat() - 0.5f) * 0.3f,
                base.y + RANDOM.nextFloat() * 0.2f,
                base.z + (RANDOM.nextFloat() - 0.5f) * 0.3f
        );
    }

    // Slight random offset so multiple hits don't perfectly overlap
    private static Vec3 randomOffset(Vec3 base) {
        return new Vec3(
                base.x + (RANDOM.nextFloat() - 0.5f) * 0.5f,
                base.y + 1.8f + RANDOM.nextFloat() * 0.4f,
                base.z + (RANDOM.nextFloat() - 0.5f) * 0.5f
        );
    }
}