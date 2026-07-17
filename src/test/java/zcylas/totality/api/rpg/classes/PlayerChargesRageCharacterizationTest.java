package zcylas.totality.api.rpg.classes;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.rpg.rest.RestType;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins Barbarian Rage's current, already-correctly-generic behavior (readiness audit §2.4) —
 * {@code PlayerChargesComponent} is a genuine {@code Map<Identifier, ChargePool>} and is used
 * here as the reference precedent the Resource API's own PARTITIONED_POOL/keyed-scalar shape
 * should generalize from, not as something this Phase 1 patch migrates.
 *
 * {@code new PlayerChargesComponent(null)} is safe here: {@code onRest}'s body never dereferences
 * its {@code ServerPlayer player} parameter (only {@code sync()}, called on the instance field,
 * short-circuits safely when that field is null), matching the same nullable-player pattern this
 * class already uses for its own client-side component factory in {@code ChargeComponents}.
 */
class PlayerChargesRageCharacterizationTest {

    private static final Identifier RAGE_ID = Identifier.fromNamespaceAndPath("totality", "barbarian_rage");

    @Test
    void chargeIdIsUnchanged() {
        assertEquals(RAGE_ID, BarbarianRageAbility.CHARGE_ID);
    }

    @Test
    void freshComponentHasNoRagePools() {
        // Baseline for "non-Barbarians do not gain or display Rage": nothing registers a pool
        // unless BarbarianRageAbility.registerChargePool is actually called for that player.
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        assertTrue(component.getAllPools().isEmpty());
        assertEquals(0, component.getCurrent(RAGE_ID));
        assertEquals(0, component.getMax(RAGE_ID));
        assertFalse(component.hasCharge(RAGE_ID));
    }

    @Test
    void registerPoolThenConsumeDecrementsCurrent() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.registerPool(RAGE_ID, 3, RestType.SHORT, 1);

        assertEquals(3, component.getCurrent(RAGE_ID));
        assertTrue(component.consume(RAGE_ID));
        assertEquals(2, component.getCurrent(RAGE_ID));
    }

    @Test
    void consumeFailsWhenEmpty() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.registerPool(RAGE_ID, 1, RestType.SHORT, 1);

        assertTrue(component.consume(RAGE_ID));
        assertFalse(component.consume(RAGE_ID), "must not go below zero");
        assertEquals(0, component.getCurrent(RAGE_ID));
    }

    @Test
    void shortRestRestoresExactlyOneAndClampsAtMax() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.registerPool(RAGE_ID, 5, RestType.SHORT, 1);
        component.consume(RAGE_ID);
        component.consume(RAGE_ID);
        assertEquals(3, component.getCurrent(RAGE_ID));

        component.onRest(null, RestType.SHORT);
        assertEquals(4, component.getCurrent(RAGE_ID), "Short Rest must restore exactly 1");

        component.onRest(null, RestType.SHORT);
        component.onRest(null, RestType.SHORT);
        assertEquals(5, component.getCurrent(RAGE_ID), "must clamp at max, not exceed it");
    }

    @Test
    void longRestRestoresToMax() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.registerPool(RAGE_ID, 6, RestType.SHORT, 1);
        component.consume(RAGE_ID);
        component.consume(RAGE_ID);
        component.consume(RAGE_ID);
        assertEquals(3, component.getCurrent(RAGE_ID));

        component.onRest(null, RestType.LONG);
        assertEquals(6, component.getCurrent(RAGE_ID), "Long Rest must restore all charges");
    }

    @Test
    void restPriorityIsFiveBeforeAbilities() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        assertEquals(5, component.restPriority());
    }

    @Test
    void ensurePoolPreservesCurrentChargesWhenMaxIncreases() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.ensurePool(RAGE_ID, 3, RestType.SHORT, 1);
        component.consume(RAGE_ID);
        assertEquals(2, component.getCurrent(RAGE_ID));

        // Simulates a Barbarian class level-up increasing max charges (BarbarianClass -> updateChargePool).
        component.ensurePool(RAGE_ID, 5, RestType.SHORT, 1);
        assertEquals(2, component.getCurrent(RAGE_ID), "current charges must be preserved across a max increase");
        assertEquals(5, component.getMax(RAGE_ID));
    }

    /**
     * Reflection is used deliberately instead of widening the field's visibility, so this
     * characterization test adds zero footprint to production code (the task's Stage 2 scope
     * forbids touching {@code BarbarianRageAbility.java} at all).
     */
    @Test
    void rageChargesByClassLevelTableIsUnchanged() throws Exception {
        Field field = BarbarianRageAbility.class.getDeclaredField("RAGE_CHARGES");
        field.setAccessible(true);
        int[] table = (int[]) field.get(null);

        assertArrayEquals(
                new int[]{2, 2, 3, 3, 3, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 6, 6},
                table
        );
    }
}
