package zcylas.totality.api.entitlement;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static zcylas.totality.api.entitlement.EntitlementTestFixture.*;

class EntitlementPersistenceLifecycleTest {

    private final EntitlementTestFixture f = new EntitlementTestFixture();
    private final EntitlementKey flight = f.ability("flight");
    private final EntitlementKey dash = f.ability("dash");
    private final EntitlementKey blink = f.ability("blink");

    private EntitlementGrant grant(EntitlementKey key, GrantLifetime lifetime, Optional<EntitlementClock.Expiry> expiry) {
        return new EntitlementGrant(UUID.randomUUID(), key, source(QUEST_SOURCE, "trial"), id("quests"), Set.of(),
                lifetime, expiry, 0, false, true);
    }

    private JsonElement save(PlayerEntitlementState state) {
        return EntitlementLedger.CODEC.encodeStart(JsonOps.INSTANCE, state.ledger()).getOrThrow();
    }

    /** Simulates logout + login: only the saved ledger survives; runtime grants are rebuilt by providers. */
    private PlayerEntitlementState reload(JsonElement saved) {
        EntitlementLedger ledger = EntitlementLedger.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        ledger.sortOrphans(f.catalog::isRegisteredContent);
        return new PlayerEntitlementState(ledger);
    }

    @Test
    void permanentFactsAndPersistedRecordsSurviveSaveAndLoad() {
        f.unlock(flight, source(QUEST_SOURCE, "sky_trial"));
        f.engine.addGrant(f.state, grant(dash, GrantLifetime.UNTIL_EXPLICITLY_REMOVED, Optional.empty()), f.clock);
        EntitlementSuspension suspension = new EntitlementSuspension(UUID.randomUUID(), flight, source(QUEST_SOURCE, "story"),
                Set.of(EntitlementActions.ACTIVATE), id("grounded"), Optional.empty(), 0, false, true);
        f.engine.addSuspension(f.state, suspension, f.clock);
        f.state.ledger().markMigrationApplied(id("migration_v1"));
        long revision = f.state.revision();

        PlayerEntitlementState loaded = reload(save(f.state));
        assertEquals(f.state.ledger().facts(flight), loaded.ledger().facts(flight));
        assertEquals(f.state.ledger().persistentGrants(), loaded.ledger().persistentGrants());
        assertEquals(f.state.ledger().suspensions(), loaded.ledger().suspensions());
        assertEquals(Set.of(id("migration_v1")), loaded.ledger().appliedMigrations());
        assertEquals(revision, loaded.revision(), "the revision stays monotonic across sessions");
        assertEquals(f.state.ledger().audit(), loaded.ledger().audit());
        // Re-encoding a loaded ledger is stable.
        assertEquals(save(f.state), save(loaded));
    }

    @Test
    void sourceBoundAccessIsRebuiltNotLoaded() {
        f.reconcile("ancestry", Set.of(ORIGIN_SOURCE), List.of(providerGrant("ancestry", flight, source(ORIGIN_SOURCE, "kryptonian"))));
        f.state = reload(save(f.state));
        assertFalse(f.allowed(flight), "provider grants are never serialized");
        f.reconcile("ancestry", Set.of(ORIGIN_SOURCE), List.of(providerGrant("ancestry", flight, source(ORIGIN_SOURCE, "kryptonian"))));
        assertTrue(f.allowed(flight), "reconciling the unchanged source restores identical access");
    }

    @Test
    void lifetimesFollowDeathRespawnAndLogoutRules() {
        f.unlock(flight, source(QUEST_SOURCE, "q"));
        f.reconcile("items", Set.of(ITEM_SOURCE), List.of(providerGrant("items", blink, source(ITEM_SOURCE, "amulet"))));
        EntitlementGrant session = EntitlementGrant.debugSession(dash, id("tester"));
        f.engine.addGrant(f.state, session, f.clock);
        EntitlementKey untilDeath = f.ability("battle_trance");
        f.engine.addGrant(f.state, grant(untilDeath, GrantLifetime.UNTIL_DEATH, Optional.empty()), f.clock);

        PlayerEntitlementState lossless = f.state.copyForRespawn(false);
        assertEquals(1, lossless.ledger().persistentGrants().size(), "returning from the End keeps UNTIL_DEATH");

        PlayerEntitlementState respawned = f.state.copyForRespawn(true);
        assertTrue(respawned.ledger().persistentGrants().isEmpty(), "death ends UNTIL_DEATH grants");
        assertEquals(1, respawned.pendingDeathRemovals.size(), "and reports them once the new entity exists");
        assertFalse(respawned.ledger().facts(flight).isEmpty(), "permanent facts survive death");
        assertTrue(respawned.sessionGrants().containsKey(session.grantId()), "session grants survive respawn");
        assertTrue(respawned.providerGrants().isEmpty(), "provider grants are re-derived after respawn");
        assertTrue(respawned.revision() > f.state.revision());

        PlayerEntitlementState relogged = reload(save(f.state));
        assertTrue(relogged.sessionGrants().isEmpty(), "session grants end at logout");
        assertFalse(relogged.ledger().facts(flight).isEmpty());
    }

    @Test
    void expiryClocksBehavePerLifetimePolicy() {
        f.clock = new EntitlementClock(100, 5_000, 1_000_000);
        EntitlementGrant onlineLease = grant(flight, GrantLifetime.PERSISTENT_LEASE,
                Optional.of(new EntitlementClock.Expiry(EntitlementClock.Clock.ONLINE_TICKS, 200)));
        EntitlementGrant realTimeLease = grant(dash, GrantLifetime.PERSISTENT_LEASE,
                Optional.of(new EntitlementClock.Expiry(EntitlementClock.Clock.REAL_TIME_UTC, 2_000_000)));
        EntitlementGrant gameTime = grant(blink, GrantLifetime.PERSISTENT_LEASE,
                Optional.of(new EntitlementClock.Expiry(EntitlementClock.Clock.SERVER_GAME_TIME, 6_000)));
        f.engine.addGrant(f.state, onlineLease, f.clock);
        f.engine.addGrant(f.state, realTimeLease, f.clock);
        f.engine.addGrant(f.state, gameTime, f.clock);
        assertTrue(f.engine.addGrant(f.state, grant(f.ability("x"), GrantLifetime.PERSISTENT_LEASE, Optional.empty()), f.clock)
                .isRejected(), "a lease without an expiry is rejected");

        // Offline for a long real-world time: the online-tick counter did not advance.
        f.state.ledger().advanceOnlineTicks(0);
        f.clock = new EntitlementClock(f.state.ledger().onlineTicks() + 100, 5_500, 3_000_000);
        assertTrue(f.allowed(flight), "ONLINE_TICKS paused while offline");
        assertFalse(f.allowed(dash), "REAL_TIME_UTC expired while offline (query ignores expired grants immediately)");
        assertTrue(f.allowed(blink));

        EntitlementMutationResult expired = f.engine.expire(f.state, f.clock);
        assertEquals(1, expired.changes().size());
        assertInstanceOf(EntitlementChange.GrantRemoved.class, expired.changes().get(0));
        assertEquals(EntitlementChange.RemovalReason.EXPIRED, ((EntitlementChange.GrantRemoved) expired.changes().get(0)).reason());

        f.clock = new EntitlementClock(200, 6_000, 3_000_000);
        assertEquals(2, f.engine.expire(f.state, f.clock).changes().size());
        assertTrue(f.state.ledger().persistentGrants().isEmpty());
    }

    @Test
    void removedContentIsQuarantinedAndRestoredNeverDeleted() {
        f.unlock(flight, source(QUEST_SOURCE, "q"));
        f.engine.addGrant(f.state, grant(dash, GrantLifetime.UNTIL_EXPLICITLY_REMOVED, Optional.empty()), f.clock);
        JsonElement saved = save(f.state);

        // A mod update removes both abilities.
        f.content.remove(flight.contentId());
        f.content.remove(dash.contentId());
        PlayerEntitlementState updated = reload(saved);
        assertTrue(updated.ledger().keysWithFacts().isEmpty());
        assertTrue(updated.ledger().orphaned().containsKey(flight));
        assertEquals(1, updated.ledger().orphanedGrants().size());
        f.state = updated;
        assertFalse(f.allowed(flight), "orphaned records are never usable");

        // Saved while orphaned, then the content returns.
        JsonElement savedOrphaned = save(updated);
        f.content.add(flight.contentId());
        f.content.add(dash.contentId());
        PlayerEntitlementState restored = reload(savedOrphaned);
        assertFalse(restored.ledger().facts(flight).isEmpty());
        assertEquals(1, restored.ledger().persistentGrants().size());
        assertTrue(restored.ledger().orphaned().isEmpty());
    }

    @Test
    void malformedRecordsArePreservedVerbatim() {
        f.unlock(flight, source(QUEST_SOURCE, "q"));
        JsonObject saved = save(f.state).getAsJsonObject();
        JsonObject broken = new JsonObject();
        broken.addProperty("key", "this is not a key");
        saved.getAsJsonArray("facts").add(broken);
        JsonObject future = new JsonObject();
        future.addProperty("future_field", 42);
        saved.getAsJsonArray("grants").add(future);

        EntitlementLedger loaded = EntitlementLedger.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertFalse(loaded.facts(flight).isEmpty(), "valid entries still load");
        assertEquals(2, loaded.unreadableCount());

        JsonObject resaved = EntitlementLedger.CODEC.encodeStart(JsonOps.INSTANCE, loaded).getOrThrow().getAsJsonObject();
        JsonArray unreadable = resaved.getAsJsonArray("unreadable");
        assertEquals(2, unreadable.size());
        assertEquals(broken, unreadable.get(0).getAsJsonObject().get("data"));
        assertEquals("grants", unreadable.get(1).getAsJsonObject().get("section").getAsString());
        // And they survive another full round trip.
        EntitlementLedger again = EntitlementLedger.CODEC.parse(JsonOps.INSTANCE, resaved).getOrThrow();
        assertEquals(2, again.unreadableCount());
    }

    @Test
    void emptyOrMissingDataLoadsAsFreshLedger() {
        EntitlementLedger ledger = EntitlementLedger.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        assertEquals(EntitlementLedger.DATA_VERSION, ledger.dataVersion());
        assertEquals(0, ledger.revision());
        assertEquals(Map.of(), ledger.facts(flight));
    }
}
