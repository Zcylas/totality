package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dormancy-safety proof for the three newly registered {@code GENERIC_COMPONENT} definitions —
 * {@code totality:thirst}, {@code totality:sanity}, {@code totality:ki} — against the real,
 * production-frozen {@link PlayerResourceRegistry#INSTANCE}. Mirrors the dormancy guarantees already
 * proven generically (for hypothetical ids) by {@code PlayerResourceStateComponentTest}/
 * {@code PlayerResourceServiceTest}, but exercised against these three specific ids so the report can
 * point at real, resource-specific coverage rather than only generic-shape coverage.
 *
 * <p>NBT round-trip restoration ({@code readLiveEntry}/{@code readOrphanEntry}) itself is NOT
 * exercised here — both require a real Mojang {@code ValueInput}/{@code ValueOutput}, which this
 * project has no harness to construct outside a running game (see
 * {@code PlayerResourceStateComponentTest}'s own class Javadoc for the same, already-documented
 * limitation). What IS exercised directly is every input those methods' restoration decision
 * actually depends on: whether a definition is registered, its model, and
 * {@link PlayerResourceStateComponent#isRegisteredExternalAdapterAuthority} — the exact three-part
 * condition {@code readOrphanEntry} evaluates — reproduced here against the real registered
 * definitions rather than duplicating the private method itself.
 */
class DormantResourceRegistrationTest {

    private static final Identifier[] DORMANT_IDS = {
            PlayerResourceIds.THIRST, PlayerResourceIds.SANITY, PlayerResourceIds.KI
    };

    // ── Registration alone does not instantiate state ────────────────────────────────────────

    @Test
    void registrationAloneLeavesAFreshComponentEmptyForAllThreeDormantIds() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent component = new PlayerResourceStateComponent(null);

        for (Identifier id : DORMANT_IDS) {
            assertFalse(component.hasState(id), () -> id + " must have no state for a fresh player");
        }
        assertTrue(component.instantiatedResourceIds().isEmpty());
        assertTrue(component.orphanedResourceIds().isEmpty());
    }

    // ── Query never instantiates, never fabricates ────────────────────────────────────────────

    @Test
    void queryingUninstantiatedThirstReturnsStructuredFailureNotAFabricatedSnapshot() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.THIRST).orElseThrow();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        ResourceQueryResult result = PlayerResourceService.INSTANCE.queryGenericState(definition, state);

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.STATE_NOT_INSTANTIATED, ((ResourceQueryResult.Failure) result).reason());
        assertFalse(state.hasState(PlayerResourceIds.THIRST), "the query itself must not have instantiated state");
    }

    @Test
    void queryingUninstantiatedSanityReturnsStructuredFailureNotAFabricatedSnapshot() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.SANITY).orElseThrow();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        ResourceQueryResult result = PlayerResourceService.INSTANCE.queryGenericState(definition, state);

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.STATE_NOT_INSTANTIATED, ((ResourceQueryResult.Failure) result).reason());
        assertFalse(state.hasState(PlayerResourceIds.SANITY));
    }

    @Test
    void queryingUninstantiatedKiReturnsStructuredFailureNotAFabricatedSnapshot() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.KI).orElseThrow();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        ResourceQueryResult result = PlayerResourceService.INSTANCE.queryGenericState(definition, state);

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.STATE_NOT_INSTANTIATED, ((ResourceQueryResult.Failure) result).reason());
        assertFalse(state.hasState(PlayerResourceIds.KI));
    }

    @Test
    void evenIfKiStateWereHypotheticallyInstantiatedQueryFailsWithMaximumUnavailableRatherThanFabricatingOne() {
        // The central proof for Part 5 (Ki definition gate): Ki declares no authoredBaseMaximum, so
        // even a hypothetical future instantiation (nothing in production code does this yet — see
        // DormantResourceScopeRegressionTest) can never produce a fabricated maximum like 100. This
        // is the exact, already-existing MAXIMUM_UNAVAILABLE mechanism
        // (PlayerResourceServiceTest.genericScalarWithoutAnAuthoredMaximumFailsStructurallyRatherThanFabricatingOne
        // proves it generically); this test proves it against Ki's real registered definition
        // specifically.
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.KI).orElseThrow();
        assertTrue(definition.authoredBaseMaximum().isEmpty(), "precondition: Ki must declare no authored maximum");

        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(PlayerResourceIds.KI, 5); // simulates a hypothetical future grant

        ResourceQueryResult result = PlayerResourceService.INSTANCE.queryGenericState(definition, state);

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.MAXIMUM_UNAVAILABLE, ((ResourceQueryResult.Failure) result).reason(),
                "Ki must never resolve a fabricated maximum — it must fail structurally until a real "
                        + "maximum-resolver framework exists");
    }

    @Test
    void ifThirstWereHypotheticallyInstantiatedItsAuthoredMaximumIsHonoredNotFabricated() {
        // Positive control: proves Thirst/Sanity's authoredBaseMaximum=100 is real, correctly-wired
        // metadata (not dead code) — a hypothetical future instantiation resolves successfully at
        // exactly the authored value, not some other invented number.
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(PlayerResourceIds.THIRST).orElseThrow();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(PlayerResourceIds.THIRST, 100);

        ResourceQueryResult result = PlayerResourceService.INSTANCE.queryGenericState(definition, state);

        assertInstanceOf(ResourceQueryResult.Success.class, result);
        assertEquals(100, ((ResourceQueryResult.Success) result).snapshot().maximumUnits());
    }

    // ── instantiateScalar is legitimately available (unlike EXTERNAL_ADAPTER ids) but nothing calls it ──

    @Test
    void instantiateScalarSucceedsForAllThreeDormantIdsSinceTheyAreGenuinelyGenericComponentAuthority() {
        // Unlike Health/Food/Breath/Mana/Stamina/SpellSlots/Rage (EXTERNAL_ADAPTER — instantiateScalar
        // would throw for them, see PlayerResourceStateComponentExternalSafetyTest), Thirst/Sanity/Ki
        // are legitimately GENERIC_COMPONENT — instantiateScalar must not throw. This proves the
        // *capability* exists for a future grant provider; DormantResourceScopeRegressionTest proves
        // nothing in current production code actually calls it.
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        for (Identifier id : DORMANT_IDS) {
            assertDoesNotThrow(() -> state.instantiateScalar(id, 0),
                    () -> id + " must be instantiable as GENERIC_COMPONENT state");
        }
    }

    // ── Stale/orphaned-state safety (Part 8) ──────────────────────────────────────────────────
    //
    // readLiveEntry/readOrphanEntry themselves require a real ValueInput/ValueOutput this project
    // has no harness for (see class Javadoc). What we CAN and do exercise directly is every input
    // their restoration decision depends on, against the real registered definitions.

    private static Method isRegisteredExternalAdapterAuthorityMethod() throws NoSuchMethodException {
        Method method = PlayerResourceStateComponent.class
                .getDeclaredMethod("isRegisteredExternalAdapterAuthority", Identifier.class);
        method.setAccessible(true);
        return method;
    }

    private static boolean isRegisteredExternalAdapterAuthority(Identifier id) throws Exception {
        return (boolean) isRegisteredExternalAdapterAuthorityMethod().invoke(null, id);
    }

    @Test
    void dormantIdsAreNotExternalAdapterAuthorityAndAreThereforeEligibleForOrphanRestoration() throws Exception {
        // Reproduces readOrphanEntry's exact "restorable" condition
        // (definition.model() == persistedModel && !isRegisteredExternalAdapterAuthority(id)) against
        // the real registry: a hypothetical SCALAR orphan under any of these three ids would be
        // restored into live state, exactly like any other GENERIC_COMPONENT resource — this is
        // existing, generic, already-tested behavior (PlayerResourceStateComponentTest's orphan
        // restoration tests); this test only confirms the id-specific inputs feeding that decision.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        for (Identifier id : DORMANT_IDS) {
            assertFalse(isRegisteredExternalAdapterAuthority(id),
                    () -> id + " must not be EXTERNAL_ADAPTER-authority");
            PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(id).orElseThrow();
            assertEquals(ResourceModel.SCALAR, definition.model());
            boolean wouldBeRestorable = definition.model() == ResourceModel.SCALAR && !isRegisteredExternalAdapterAuthority(id);
            assertTrue(wouldBeRestorable, () -> "a matching SCALAR orphan under " + id + " would be restorable");
        }
    }

    @Test
    void aModelMismatchedOrphanUnderADormantIdWouldRemainQuarantined() throws Exception {
        // Same restorable condition as above, but with a PARTITIONED_POOL persisted model — the
        // model-mismatch branch of readOrphanEntry's condition, which leaves the orphan quarantined
        // rather than restoring it as live state of the wrong shape.
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        for (Identifier id : DORMANT_IDS) {
            PlayerResourceDefinition definition = PlayerResourceRegistry.INSTANCE.get(id).orElseThrow();
            ResourceModel mismatchedPersistedModel = ResourceModel.PARTITIONED_POOL;
            boolean wouldBeRestorable = definition.model() == mismatchedPersistedModel
                    && !isRegisteredExternalAdapterAuthority(id);
            assertFalse(wouldBeRestorable,
                    () -> "a PARTITIONED_POOL orphan under " + id + " (registered SCALAR) must stay quarantined");
        }
    }

    /**
     * Renamed from {@code registeringTheseDefinitionsDoesNotDiscardAnyExistingOrphanedData} (review
     * correction pass): the original name claimed this proves "existing orphaned data" survives
     * registration, but the test never constructs, persists, or loads any orphaned state at all — it
     * starts from a clean component and only proves that instantiating live state for one dormant id
     * does not fabricate an orphan entry as a side effect. This is a real, narrower, and correctly
     * named claim; it does NOT prove NBT orphan restoration, persisted orphan preservation, old-world
     * migration, malformed-NBT quarantine, or compatible orphan reactivation. Those remain covered
     * only by {@link #dormantIdsAreNotExternalAdapterAuthorityAndAreThereforeEligibleForOrphanRestoration}
     * (the id-specific restoration *decision inputs*, exercised against the real registry) and by
     * {@code PlayerResourceStateComponentTest}'s generic, model-based (not id-specific)
     * {@code orphanToLiveState} reflection tests — neither of which touches real NBT, since this
     * project has no {@code ValueInput}/{@code ValueOutput} test harness (see both classes' Javadoc).
     */
    @Test
    void dormantDefinitionRegistrationDoesNotCreateOrphanEntries() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent component = new PlayerResourceStateComponent(null);

        assertTrue(component.orphanedResourceIds().isEmpty(), "a fresh component must start with no orphan entries");

        component.instantiateScalar(PlayerResourceIds.THIRST, 10);

        assertTrue(component.hasState(PlayerResourceIds.THIRST));
        assertTrue(component.orphanedResourceIds().isEmpty(),
                "instantiating live state for a dormant resource must not itself fabricate an orphan entry");
    }
}
