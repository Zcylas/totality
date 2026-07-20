package zcylas.totality.api.rpg.classes;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.rpg.rest.RestType;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins Barbarian Rage's current, already-correctly-generic behavior (readiness audit §2.4) —
 * {@code PlayerChargesComponent} is a genuine {@code Map<Identifier, ChargePool>}: a useful,
 * generically {@code Identifier}-keyed owner pattern that may hold several independently
 * identified pools at once. Each individual pool, including Rage's, is a plain {@code SCALAR}
 * current/maximum resource — Rage charges themselves have no partition identities, no
 * partition-specific spending, and no partial-fill segments (canonical §6.1/§6.3/§25.6). This is
 * <b>not</b> a {@code PARTITIONED_POOL} precedent; standard spell slots and future Hit Dice remain
 * the true {@code PARTITIONED_POOL} examples in this codebase. (Corrected in Phase 2E — an earlier
 * version of this Javadoc incorrectly described this class as a future {@code PARTITIONED_POOL}/
 * keyed-partition reference; see the Phase 2E report's "Documentation corrections" section.)
 *
 * {@code new PlayerChargesComponent(null)} is safe here: {@code onRest}'s body never dereferences
 * its {@code ServerPlayer player} parameter (only {@code sync()}, called on the instance field,
 * short-circuits safely when that field is null), matching the same nullable-player pattern this
 * class already uses for its own client-side component factory in {@code ChargeComponents}.
 */
class PlayerChargesRageCharacterizationTest {

    private static final Identifier RAGE_ID = Identifier.fromNamespaceAndPath("totality", "barbarian_rage");

    private static HolderLookup.Provider emptyRegistries() {
        return HolderLookup.Provider.create(Stream.of());
    }

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

    // ── Phase 2E additions: updatePoolMax, persistence, sync, copyFrom, unrelated pools ─────

    @Test
    void updatePoolMaxPreservesCurrentWhenBelowTheNewMaximum() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.registerPool(RAGE_ID, 3, RestType.SHORT, 1);
        component.consume(RAGE_ID);
        assertEquals(2, component.getCurrent(RAGE_ID));

        // Simulates BarbarianClass's ClassLevelUpRegistry callback -> BarbarianRageAbility.updateChargePool.
        component.updatePoolMax(RAGE_ID, 5);

        assertEquals(2, component.getCurrent(RAGE_ID), "current must be preserved when it is already below the new maximum");
        assertEquals(5, component.getMax(RAGE_ID));
    }

    @Test
    void updatePoolMaxClampsCurrentWhenMaximumDecreases() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.registerPool(RAGE_ID, 6, RestType.SHORT, 1);
        assertEquals(6, component.getCurrent(RAGE_ID));

        component.updatePoolMax(RAGE_ID, 2);

        assertEquals(2, component.getCurrent(RAGE_ID), "current must clamp down to the new, lower maximum");
        assertEquals(2, component.getMax(RAGE_ID));
    }

    @Test
    void updatePoolMaxOnAMissingPoolIsANoOp() {
        // BarbarianRageAbility.updateChargePool itself guards classLevel <= 0 before calling this,
        // but updatePoolMax's own no-op-on-missing-pool behavior is worth pinning directly.
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.updatePoolMax(RAGE_ID, 5);

        assertTrue(component.getAllPools().isEmpty(), "updatePoolMax must not create a pool that did not exist");
    }

    @Test
    void copyFromPreservesAllPoolsIndependently() {
        // Characterizes RespawnStrategy.ALWAYS_COPY's underlying mechanism: death/respawn preserves
        // Rage charges (and any other pool) exactly, via a straight pools.putAll.
        PlayerChargesComponent source = new PlayerChargesComponent(null);
        source.registerPool(RAGE_ID, 6, RestType.SHORT, 1);
        source.consume(RAGE_ID);
        source.consume(RAGE_ID);
        Identifier otherId = Identifier.fromNamespaceAndPath("totality", "some_other_pool");
        source.registerPool(otherId, 10, RestType.LONG, -1);
        source.consume(otherId);

        PlayerChargesComponent target = new PlayerChargesComponent(null);
        target.copyFrom(source, emptyRegistries());

        assertEquals(4, target.getCurrent(RAGE_ID), "Rage charges must be preserved exactly through copyFrom");
        assertEquals(6, target.getMax(RAGE_ID));
        assertEquals(9, target.getCurrent(otherId), "an unrelated pool must be preserved exactly too");
        assertEquals(10, target.getMax(otherId));

        // Independence: mutating the target must not affect the source.
        target.consume(RAGE_ID);
        assertEquals(3, target.getCurrent(RAGE_ID));
        assertEquals(4, source.getCurrent(RAGE_ID), "source must be unaffected by mutating the copy's target");
    }

    @Test
    void persistenceRoundTripsCurrentMaxRechargeTypeAndAmount() {
        PlayerChargesComponent original = new PlayerChargesComponent(null);
        original.registerPool(RAGE_ID, 5, RestType.SHORT, 1);
        original.consume(RAGE_ID);
        original.consume(RAGE_ID);

        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        original.writeData(out);

        PlayerChargesComponent restored = new PlayerChargesComponent(null);
        restored.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), out.buildResult()));

        assertEquals(3, restored.getCurrent(RAGE_ID));
        assertEquals(5, restored.getMax(RAGE_ID));
        assertEquals(RestType.SHORT, restored.getAllPools().get(RAGE_ID).rechargeType());
        assertEquals(1, restored.getAllPools().get(RAGE_ID).rechargeAmount());
    }

    @Test
    void persistenceRoundTripsMultipleIndependentPoolsByIdentifier() {
        PlayerChargesComponent original = new PlayerChargesComponent(null);
        original.registerPool(RAGE_ID, 4, RestType.SHORT, 1);
        Identifier otherId = Identifier.fromNamespaceAndPath("totality", "some_other_pool");
        original.registerPool(otherId, 8, RestType.LONG, -1);
        original.consume(RAGE_ID);

        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        original.writeData(out);

        PlayerChargesComponent restored = new PlayerChargesComponent(null);
        restored.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), out.buildResult()));

        assertEquals(3, restored.getCurrent(RAGE_ID));
        assertEquals(4, restored.getMax(RAGE_ID));
        assertEquals(8, restored.getCurrent(otherId), "the unrelated pool's value must round-trip unchanged");
        assertEquals(8, restored.getMax(otherId));
    }

    @Test
    void synchronizationRoundTripsCurrentAndMaximum() {
        PlayerChargesComponent server = new PlayerChargesComponent(null);
        server.registerPool(RAGE_ID, 6, RestType.SHORT, 1);
        server.consume(RAGE_ID);
        server.consume(RAGE_ID);

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        server.writeSyncPacket(buf, null);

        PlayerChargesComponent client = new PlayerChargesComponent(null);
        client.applySyncPacket(buf);

        assertEquals(4, client.getCurrent(RAGE_ID));
        assertEquals(6, client.getMax(RAGE_ID));
        assertEquals(0, buf.readableBytes(), "the entire sync payload must be consumed");
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

    @Test
    void classLevelsAboveTheTwentyFifthEntryRemainClampedToTheFinalValue() throws Exception {
        // Characterizes the exact clamp expression BarbarianRageAbility.registerChargePool/
        // updateChargePool both use (Math.min(classLevel - 1, RAGE_CHARGES.length - 1)) against the
        // real table, without needing a real ServerPlayer/ClassComponents — proving levels 25-30
        // currently produce the same value (6) as level 25 itself, per the Phase 2E audit's finding
        // that (unlike SpellSlotTable) there is no distinct authored value beyond the 25th entry.
        Field field = BarbarianRageAbility.class.getDeclaredField("RAGE_CHARGES");
        field.setAccessible(true);
        int[] table = (int[]) field.get(null);

        int level25Value = table[Math.min(25 - 1, table.length - 1)];
        assertEquals(6, level25Value);
        for (int classLevel = 25; classLevel <= 30; classLevel++) {
            int clampedValue = table[Math.min(classLevel - 1, table.length - 1)];
            assertEquals(level25Value, clampedValue, "class level " + classLevel + " must clamp to the level-25 value");
        }
    }
}
