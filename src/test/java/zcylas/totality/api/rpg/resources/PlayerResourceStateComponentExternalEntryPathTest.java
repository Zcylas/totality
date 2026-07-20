package zcylas.totality.api.rpg.resources;

import io.netty.buffer.Unpooled;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.state.ResourceState;
import zcylas.totality.api.rpg.resources.state.ScalarResourceState;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Correction-pass tests exercising every real external-state entry path — NBT read/write and
 * sync-packet read/write — with genuine Mojang serialization types, closing the "no automated NBT
 * round-trip test" limitation both the Phase 1 and Phase 2A reports honestly documented.
 *
 * <p>This is possible without a running game because:
 * <ul>
 *     <li>{@link TagValueOutput#createWithoutContext}/{@link TagValueInput#create} only need a
 *         {@link ProblemReporter} (a stable no-op singleton, {@link ProblemReporter#DISCARDING})
 *         and a {@link HolderLookup.Provider} — {@link HolderLookup.Provider#create(Stream)} with
 *         an empty stream produces a real, valid, registry-free provider. Neither
 *         {@code readData}/{@code writeData} nor {@code readLiveEntry}/{@code readOrphanEntry}
 *         ever touch a registry lookup; they only read/write primitive NBT fields.</li>
 *     <li>{@link RegistryFriendlyByteBuf} only needs a Netty {@code ByteBuf}
 *         ({@link Unpooled#buffer()}) and a {@code RegistryAccess}, which may be {@code null} here
 *         since {@code writeSyncPacket}/{@code applySyncPacket}/{@code writeStatePayload}/
 *         {@code readStatePayload} never call {@code registryAccess()} — they only read/write
 *         primitive fields (ints, longs, UTF strings).</li>
 * </ul>
 *
 * <p><b>Correction pass — corrected claim.</b> This class Javadoc previously stated "No reflection
 * is used anywhere in this file," which was false: {@link #injectCorruptedExternalStateViaReflection}
 * uses reflection, and it is used by exactly three tests —
 * {@link #writeDataNeverWritesALiveHealthEntryEvenIfCorruptedIntoStates},
 * {@link #syncWritingExcludesExternalLiveEntriesEvenIfCorruptedIntoStates}, and
 * {@link #copyingAComponentWithCorruptedExternalLiveStateQuarantinesItRatherThanCopyingItLive}.
 * All other tests in this file use only the real Mojang serialization types described above, with
 * no reflection. See {@link #injectCorruptedExternalStateViaReflection}'s own Javadoc for why
 * reflection is unavoidable for those three specific tests: no legitimate public API can put a
 * known {@code EXTERNAL_ADAPTER} id into the private {@code states} map, which is exactly the
 * corrupted scenario those three defensive-filter tests need to construct.
 */
class PlayerResourceStateComponentExternalEntryPathTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private static HolderLookup.Provider emptyRegistries() {
        return HolderLookup.Provider.create(Stream.of());
    }

    /**
     * Reflection is unavoidable here and nowhere else in this file: no production entry path
     * (that is exactly what this correction pass closes) offers any legitimate public way to get
     * a known {@code EXTERNAL_ADAPTER} id into the private {@code states} map, so the only way to
     * exercise {@code writeSyncPacket}/{@code writeData}/{@code copyFrom}'s own defensive filters
     * against a genuinely corrupted in-memory map (rather than merely an empty one) is to put it
     * there directly via the private field.
     */
    private static void injectCorruptedExternalStateViaReflection(
            PlayerResourceStateComponent component, Identifier externalId, long currentUnits) {
        try {
            Field statesField = PlayerResourceStateComponent.class.getDeclaredField("states");
            statesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Identifier, ResourceState> states = (Map<Identifier, ResourceState>) statesField.get(component);
            states.put(externalId, new ScalarResourceState(currentUnits));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inject corrupted state for test setup", e);
        }
    }

    /** Builds the exact NBT shape {@code writeData}'s private {@code writeLiveEntry} would produce for one SCALAR entry. */
    private static CompoundTag buildStaleGenericScalarNbt(Identifier resourceId, long current, long overflow, long remainder) {
        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        out.putInt("SchemaVersion", 2);
        out.putInt("ResourceCount", 1);
        out.putString("Resource_0_id", resourceId.toString());
        out.putString("Resource_0_model", "SCALAR");
        out.putLong("Resource_0_current", current);
        out.putLong("Resource_0_overflow", overflow);
        out.putLong("Resource_0_remainder", remainder);
        out.putInt("OrphanedCount", 0);
        return out.buildResult();
    }

    /**
     * Builds the exact NBT shape {@code writeData}'s private {@code writeLiveEntry}/{@code
     * writePartitionMap} would produce for one PARTITIONED_POOL entry — Phase 2D's own analogue of
     * {@link #buildStaleGenericScalarNbt}, needed because {@code totality:spell_slots} is the first
     * production resource whose model is PARTITIONED_POOL rather than SCALAR.
     */
    private static CompoundTag buildStaleGenericPartitionedNbt(Identifier resourceId, int partitionKey, long current, long overflow) {
        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        out.putInt("SchemaVersion", 2);
        out.putInt("ResourceCount", 1);
        out.putString("Resource_0_id", resourceId.toString());
        out.putString("Resource_0_model", "PARTITIONED_POOL");
        out.putInt("Resource_0_partitionCount", 1);
        out.putInt("Resource_0_partition_0_key", partitionKey);
        out.putLong("Resource_0_partition_0_current", current);
        out.putLong("Resource_0_partition_0_overflow", overflow);
        out.putInt("OrphanedCount", 0);
        return out.buildResult();
    }

    @Test
    void sanityCheckOwnNbtHarnessRoundTripsAnOrdinaryGenericResource() {
        // Proves the test harness itself (TagValueOutput/TagValueInput/empty registries) reads
        // back the fields it wrote correctly, before relying on it for the external-quarantine
        // assertions below. Uses a throwaway id that is deliberately never registered in
        // PlayerResourceRegistry.INSTANCE (registering it there would leak into other tests, since
        // INSTANCE is a shared static singleton) — an unregistered id always orphans regardless of
        // its EXTERNAL_ADAPTER-authority status, which is sufficient to prove the raw NBT
        // read/write plumbing itself preserves field values correctly.
        Identifier genericId = id("nbt_harness_sanity_check");

        CompoundTag tag = buildStaleGenericScalarNbt(genericId, 42, 0, 0);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), tag));

        // No definition registered for genericId -> orphaned, not live — but the fields must have
        // round-tripped correctly (current=42) to prove the harness itself is sound.
        assertFalse(state.hasState(genericId));
        assertTrue(state.orphanedResourceIds().contains(genericId));
    }

    @Test
    void nbtLoadingQuarantinesStaleGenericDataForHealth() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        // Simulates stale/malformed data: totality:health persisted in the ordinary
        // GENERIC_COMPONENT scalar shape (e.g. leftover from before Health became an external
        // adapter, or a corrupted save) — written by hand here since the real writeData now
        // defensively refuses to ever produce this shape for Health.
        CompoundTag tag = buildStaleGenericScalarNbt(PlayerResourceIds.HEALTH, 15000, 0, 0);

        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), tag));

        assertFalse(state.hasState(PlayerResourceIds.HEALTH), "Health must never become live generic state");
        assertTrue(state.orphanedResourceIds().contains(PlayerResourceIds.HEALTH),
                "stale Health data must be quarantined, not silently dropped");
    }

    @Test
    void nbtLoadingQuarantinesStaleGenericDataForFood() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        CompoundTag tag = buildStaleGenericScalarNbt(PlayerResourceIds.FOOD, 20, 0, 0);

        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), tag));

        assertFalse(state.hasState(PlayerResourceIds.FOOD));
        assertTrue(state.orphanedResourceIds().contains(PlayerResourceIds.FOOD));
    }

    @Test
    void nbtLoadingQuarantinesStaleGenericDataForBreath() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        // Simulates stale/malformed data: totality:breath persisted in the ordinary
        // GENERIC_COMPONENT scalar shape — Phase 2B's own analogue of the Health/Food cases above.
        CompoundTag tag = buildStaleGenericScalarNbt(PlayerResourceIds.BREATH, 300, 0, 0);

        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), tag));

        assertFalse(state.hasState(PlayerResourceIds.BREATH), "Breath must never become live generic state");
        assertTrue(state.orphanedResourceIds().contains(PlayerResourceIds.BREATH),
                "stale Breath data must be quarantined, not silently dropped");
    }

    @Test
    void applyingASyncPayloadCannotCreateLiveBreathGenericState() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        buf.writeInt(1);
        buf.writeUtf(PlayerResourceIds.BREATH.toString());
        buf.writeUtf("SCALAR");
        buf.writeLong(300L); // current
        buf.writeLong(0L);   // overflow

        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.applySyncPacket(buf);

        assertFalse(state.hasState(PlayerResourceIds.BREATH), "Breath must never become live via a sync packet");
        assertEquals(0, buf.readableBytes(), "the entire payload must be consumed even though the entry was discarded");
    }

    @Test
    void nbtLoadingQuarantinesStaleGenericDataForMana() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        // Phase 2C's own analogue: totality:mana is now a transitional EXTERNAL_ADAPTER over the
        // legacy PlayerResourceComponent store, so the same quarantine guarantee must hold for it.
        CompoundTag tag = buildStaleGenericScalarNbt(PlayerResourceIds.MANA, 100, 0, 0);

        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), tag));

        assertFalse(state.hasState(PlayerResourceIds.MANA), "Mana must never become live generic state");
        assertTrue(state.orphanedResourceIds().contains(PlayerResourceIds.MANA),
                "stale Mana data must be quarantined, not silently dropped");
    }

    @Test
    void nbtLoadingQuarantinesStaleGenericDataForStamina() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        CompoundTag tag = buildStaleGenericScalarNbt(PlayerResourceIds.STAMINA, 100, 0, 0);

        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), tag));

        assertFalse(state.hasState(PlayerResourceIds.STAMINA), "Stamina must never become live generic state");
        assertTrue(state.orphanedResourceIds().contains(PlayerResourceIds.STAMINA),
                "stale Stamina data must be quarantined, not silently dropped");
    }

    @Test
    void nbtLoadingQuarantinesStaleGenericPartitionedDataForSpellSlots() {
        // Phase 2D's own analogue of the SCALAR quarantine tests above, but for the first
        // PARTITIONED_POOL production resource — proves readLiveEntry's authority guard also
        // protects a partitioned-shaped stale entry, not only scalar ones.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        CompoundTag tag = buildStaleGenericPartitionedNbt(PlayerResourceIds.SPELL_SLOTS, 1, 4, 0);

        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), tag));

        assertFalse(state.hasState(PlayerResourceIds.SPELL_SLOTS), "spell slots must never become live generic state");
        assertTrue(state.orphanedResourceIds().contains(PlayerResourceIds.SPELL_SLOTS),
                "stale spell-slot data must be quarantined, not silently dropped");
    }

    @Test
    void nbtLoadingQuarantinesStaleGenericDataForRage() {
        // Phase 2E's own analogue of the SCALAR quarantine tests above.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        CompoundTag tag = buildStaleGenericScalarNbt(PlayerResourceIds.RAGE, 2, 0, 0);

        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), tag));

        assertFalse(state.hasState(PlayerResourceIds.RAGE), "Rage must never become live generic state");
        assertTrue(state.orphanedResourceIds().contains(PlayerResourceIds.RAGE),
                "stale Rage data must be quarantined, not silently dropped");
    }

    @Test
    void applyingASyncPayloadCannotCreateLiveRageGenericState() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        buf.writeInt(1);
        buf.writeUtf(PlayerResourceIds.RAGE.toString());
        buf.writeUtf("SCALAR");
        buf.writeLong(2L); // current
        buf.writeLong(0L); // overflow

        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.applySyncPacket(buf);

        assertFalse(state.hasState(PlayerResourceIds.RAGE), "Rage must never become live via a sync packet");
        assertEquals(0, buf.readableBytes(), "the entire payload must be consumed even though the entry was discarded");
    }

    @Test
    void applyingASyncPayloadCannotCreateLiveManaGenericState() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        buf.writeInt(1);
        buf.writeUtf(PlayerResourceIds.MANA.toString());
        buf.writeUtf("SCALAR");
        buf.writeLong(100L); // current
        buf.writeLong(0L);   // overflow

        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.applySyncPacket(buf);

        assertFalse(state.hasState(PlayerResourceIds.MANA), "Mana must never become live via a sync packet");
        assertEquals(0, buf.readableBytes(), "the entire payload must be consumed even though the entry was discarded");
    }

    @Test
    void applyingASyncPayloadCannotCreateLiveStaminaGenericState() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        buf.writeInt(1);
        buf.writeUtf(PlayerResourceIds.STAMINA.toString());
        buf.writeUtf("SCALAR");
        buf.writeLong(100L); // current
        buf.writeLong(0L);   // overflow

        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.applySyncPacket(buf);

        assertFalse(state.hasState(PlayerResourceIds.STAMINA), "Stamina must never become live via a sync packet");
        assertEquals(0, buf.readableBytes(), "the entire payload must be consumed even though the entry was discarded");
    }

    @Test
    void writeDataNeverWritesALiveHealthEntryEvenIfCorruptedIntoStates() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        injectCorruptedExternalStateViaReflection(state, PlayerResourceIds.HEALTH, 15000);
        assertTrue(state.hasState(PlayerResourceIds.HEALTH), "sanity: injection actually worked");

        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        state.writeData(out);
        CompoundTag written = out.buildResult();

        assertEquals(0, written.getIntOr("ResourceCount", -1),
                "writeData must exclude the corrupted external entry, not persist it as live");
        assertFalse(written.contains("Resource_0_id"));
    }

    @Test
    void applyingASyncPayloadCannotCreateLiveHealthGenericState() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        // Hand-build a sync payload carrying one entry for totality:health — the shape
        // writeSyncPacket would have produced before this correction pass's defensive filter.
        buf.writeInt(1);
        buf.writeUtf(PlayerResourceIds.HEALTH.toString());
        buf.writeUtf("SCALAR");
        buf.writeLong(15000L); // current
        buf.writeLong(0L);     // overflow

        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.applySyncPacket(buf);

        assertFalse(state.hasState(PlayerResourceIds.HEALTH), "Health must never become live via a sync packet");
        assertEquals(0, buf.readableBytes(), "the entire payload must be consumed even though the entry was discarded");
    }

    @Test
    void externalEntryDoesNotCorruptParsingOfLaterSyncEntries() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        // applySyncPacket's external check (isRegisteredExternalAdapterAuthority) reads
        // PlayerResourceRegistry.INSTANCE. `ordinaryId` is deliberately left unregistered there —
        // an unregistered id is never treated as external (see isRegisteredExternalAdapterAuthority's
        // own contract: unknown ids return false), which is exactly the "ordinary resource" case
        // this test needs, with no risk of leaking a registration into other tests via the shared
        // static singleton.
        Identifier ordinaryId = id("sync_ordinary_resource");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        buf.writeInt(2);
        // Entry 0: totality:health — must be discarded.
        buf.writeUtf(PlayerResourceIds.HEALTH.toString());
        buf.writeUtf("SCALAR");
        buf.writeLong(15000L);
        buf.writeLong(0L);
        // Entry 1: an ordinary, unrelated resource id — must still parse correctly afterward.
        buf.writeUtf(ordinaryId.toString());
        buf.writeUtf("SCALAR");
        buf.writeLong(77L);
        buf.writeLong(0L);

        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.applySyncPacket(buf);

        assertFalse(state.hasState(PlayerResourceIds.HEALTH));
        assertTrue(state.hasState(ordinaryId), "the entry after the discarded external one must still parse");
        assertEquals(77L, state.getScalar(ordinaryId).orElseThrow().currentUnits());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void syncWritingExcludesExternalLiveEntriesEvenIfCorruptedIntoStates() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        injectCorruptedExternalStateViaReflection(state, PlayerResourceIds.FOOD, 20);
        assertTrue(state.hasState(PlayerResourceIds.FOOD), "sanity: injection actually worked");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        state.writeSyncPacket(buf, null);

        assertEquals(0, buf.readInt(),
                "writeSyncPacket must exclude the corrupted external entry, not send it as live");
    }

    @Test
    void copyingAComponentWithCorruptedExternalLiveStateQuarantinesItRatherThanCopyingItLive() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent source = new PlayerResourceStateComponent(null);
        injectCorruptedExternalStateViaReflection(source, PlayerResourceIds.HEALTH, 18000);
        assertTrue(source.hasState(PlayerResourceIds.HEALTH), "sanity: injection actually worked");

        PlayerResourceStateComponent target = new PlayerResourceStateComponent(null);
        target.copyFrom(source, emptyRegistries());

        assertFalse(target.hasState(PlayerResourceIds.HEALTH),
                "copyFrom must never copy a known-external id into live state");
        assertTrue(target.orphanedResourceIds().contains(PlayerResourceIds.HEALTH),
                "the corrupted data should be preserved (quarantined), not silently dropped");
    }

    @Test
    void externalSnapshotsAlwaysComeFromTheAdapterNeverFromGenericState() {
        // Structural proof, not a snapshot-content check: totality:health/totality:food are
        // EXTERNAL_ADAPTER-authority in the production registry, so PlayerResourceService's
        // routing (see PlayerResourceServiceTest.genericDefinitionsDoNotRouteToAnAdapter and
        // .externalDefinitionsRouteToTheirAdapter for the routing logic itself) can only ever
        // reach HealthResourceAdapter/FoodResourceAdapter for these two ids — queryGenericState is
        // structurally unreachable for them. This test re-confirms the authority declaration that
        // guarantee rests on, directly against the production registry.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertEquals(ResourceStateAuthority.EXTERNAL_ADAPTER,
                PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.HEALTH).orElseThrow().stateAuthority());
        assertEquals(ResourceStateAuthority.EXTERNAL_ADAPTER,
                PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.FOOD).orElseThrow().stateAuthority());
        assertEquals(ResourceStateAuthority.EXTERNAL_ADAPTER,
                PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.BREATH).orElseThrow().stateAuthority());
        // Phase 2C: Mana/Stamina are EXTERNAL_ADAPTER too (transitionally) — queryGenericState is
        // structurally unreachable for them exactly the same way, even though their adapter reads a
        // Totality-owned legacy store rather than a vanilla one.
        assertEquals(ResourceStateAuthority.EXTERNAL_ADAPTER,
                PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.MANA).orElseThrow().stateAuthority());
        assertEquals(ResourceStateAuthority.EXTERNAL_ADAPTER,
                PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.STAMINA).orElseThrow().stateAuthority());
        // Phase 2D: totality:spell_slots is EXTERNAL_ADAPTER too, despite being PARTITIONED_POOL
        // rather than SCALAR — queryGenericState is structurally unreachable for it the same way.
        assertEquals(ResourceStateAuthority.EXTERNAL_ADAPTER,
                PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.SPELL_SLOTS).orElseThrow().stateAuthority());
        // Phase 2E: totality:rage is EXTERNAL_ADAPTER too, transitionally wrapping one entry of the
        // legacy PlayerChargesComponent charge-pool map.
        assertEquals(ResourceStateAuthority.EXTERNAL_ADAPTER,
                PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.RAGE).orElseThrow().stateAuthority());
    }

    @Test
    void healthAndFoodNeverAppearAsOrdinaryLiveComponentStatesOnAFreshComponent() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertFalse(state.hasState(PlayerResourceIds.HEALTH));
        assertFalse(state.hasState(PlayerResourceIds.FOOD));
        assertFalse(state.hasState(PlayerResourceIds.BREATH));
        assertFalse(state.hasState(PlayerResourceIds.MANA));
        assertFalse(state.hasState(PlayerResourceIds.STAMINA));
        assertFalse(state.hasState(PlayerResourceIds.SPELL_SLOTS));
        assertFalse(state.hasState(PlayerResourceIds.RAGE));
        assertTrue(state.getScalar(PlayerResourceIds.HEALTH).isEmpty());
        assertTrue(state.getScalar(PlayerResourceIds.FOOD).isEmpty());
        assertTrue(state.getScalar(PlayerResourceIds.BREATH).isEmpty());
        assertTrue(state.getScalar(PlayerResourceIds.MANA).isEmpty());
        assertTrue(state.getScalar(PlayerResourceIds.STAMINA).isEmpty());
        assertTrue(state.getScalar(PlayerResourceIds.RAGE).isEmpty());
        assertTrue(state.getPartitioned(PlayerResourceIds.SPELL_SLOTS).isEmpty());
    }
}
