package zcylas.totality.entity.base_weapon;

import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.combat.damage.TotalityDamageType;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.combat.CombatResolver;
import zcylas.totality.api.rpg.stats.AbilityScore;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the fix for the Thrown Shuriken notification-label bug:
 * {@code ThrownShurikenEntity.onHitEntity} previously computed {@code weaponName} from the
 * projectile's own stored item stack but never passed it to {@code CombatResolver}, so the
 * 8-argument convenience overload it called instead derived the label from the attacker's
 * *current* main-hand item at the moment of impact — wrong whenever the thrown Shuriken was the
 * last of its stack, the attacker switched held items in flight, the attacker's hand was empty on
 * impact, or the projectile had a custom display name.
 *
 * <p>{@code ThrownShurikenEntity} extends {@code AbstractArrow} (a Minecraft {@code Entity}), and
 * cannot be constructed under plain JUnit for the same registry-bootstrap/freeze reason documented
 * in {@code HealingPotionItemContractTest}'s class Javadoc (confirmed by direct probing during this
 * task: entity construction requires a live, unfrozen registry). The reflection check below is real
 * runtime proof of the target overload's existence and signature; the source-text checks are
 * sentinels only — they cannot execute {@code onHitEntity} or observe actual argument values at
 * runtime.
 */
class ThrownShurikenEntityWeaponNameTest {

    private static final Path SHURIKEN_ENTITY =
            Path.of("src/main/java/zcylas/totality/entity/base_weapon/ThrownShurikenEntity.java");

    private static String read(Path path) throws Exception {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    // ── Real reflection proof: the explicit-name overload exists ─────────────

    @Test
    void combatResolverHasAnExplicitWeaponNameOverload() throws Exception {
        Method m = CombatResolver.class.getDeclaredMethod("resolveAttack",
                LivingEntity.class, LivingEntity.class, AbilityScore.class, boolean.class,
                RollType.class, int.class, Dice.class, TotalityDamageType.class, String.class);
        assertEquals(void.class, m.getReturnType());
    }

    // ── Source sentinels (see class Javadoc for evidentiary limits) ──────────

    @Test
    void onHitEntityStillDerivesWeaponNameFromTheStoredShurikenStack() throws Exception {
        String source = read(SHURIKEN_ENTITY);
        assertTrue(source.contains("String weaponName = shurikenStack.getHoverName().getString();"),
                "expected weaponName to still be derived from the projectile's own stored item stack");
    }

    /** Extracts the {@code CombatResolver.resolveAttack(...)} call's argument text, with {@code
     *  //}-style line comments stripped and whitespace collapsed, so formatting/comment changes
     *  don't break the sentinel. */
    private static String extractNormalizedResolveAttackCall(String source) {
        int callStart = source.indexOf("CombatResolver.resolveAttack(");
        assertTrue(callStart >= 0, "expected a CombatResolver.resolveAttack( call");
        int callEnd = source.indexOf(");", callStart);
        assertTrue(callEnd >= 0, "expected the call to close with );");
        String callText = source.substring(callStart, callEnd);
        String noComments = callText.replaceAll("//[^\n]*", "");
        return noComments.replaceAll("\\s+", " ").trim();
    }

    @Test
    void onHitEntityPassesWeaponNameAsTheFinalResolveAttackArgument() throws Exception {
        String source = read(SHURIKEN_ENTITY);
        String normalized = extractNormalizedResolveAttackCall(source);
        assertEquals(
                "CombatResolver.resolveAttack( attacker, target, effective, weapon.isProficient(attacker), "
                        + "weapon.modifyRollType(attacker, target, RollType.NORMAL), weapon.getDiceCount(), "
                        + "weapon.getDamageDie(), weapon.getDamageType(), weaponName",
                normalized,
                "expected the call to CombatResolver.resolveAttack to pass weaponName as its final argument, "
                        + "using the explicit-name 9-argument overload");
    }

    @Test
    void onHitEntityNoLongerCallsTheEightArgumentOverloadThatDerivesFromMainHand() throws Exception {
        // The buggy call ended with `weapon.getDamageType()` as its LAST argument (no name).
        String source = read(SHURIKEN_ENTITY);
        String normalized = extractNormalizedResolveAttackCall(source);
        assertFalse(normalized.endsWith("weapon.getDamageType()"),
                "must not call the 8-argument overload that derives the weapon name from the attacker's current main-hand item");
        assertTrue(normalized.endsWith("weaponName"),
                "expected weaponName to be the final argument of the call");
    }

    @Test
    void otherAttackParametersRemainPresentAndUnchanged() throws Exception {
        // Attack-roll ability score, proficiency, roll type, dice count, damage die, and damage
        // type must all still be passed through unchanged — only the missing name was added.
        String source = read(SHURIKEN_ENTITY);
        assertTrue(source.contains("effective,"), "expected the finesse-aware ability score argument to remain");
        assertTrue(source.contains("weapon.isProficient(attacker),"), "expected the proficiency argument to remain");
        assertTrue(source.contains("weapon.modifyRollType(attacker, target, RollType.NORMAL),"), "expected the roll-type argument to remain");
        assertTrue(source.contains("weapon.getDiceCount(),"), "expected the dice-count argument to remain");
        assertTrue(source.contains("weapon.getDamageDie(),"), "expected the damage-die argument to remain");
        assertTrue(source.contains("weapon.getDamageType(),"), "expected the damage-type argument to remain");
    }

    @Test
    void pickupDurabilityAndProjectilePhysicsCodePathsAreUntouched() throws Exception {
        // Narrow sentinel: confirms the surrounding pickup/durability/discard logic is still
        // present, unmodified in shape, immediately after the attack-resolution block.
        String source = read(SHURIKEN_ENTITY);
        assertTrue(source.contains("pickup.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);"));
        assertTrue(source.contains("this.spawnAtLocation(serverLevel, pickup, 0.1f);"));
        assertTrue(source.contains("this.discard();"));
    }
}
