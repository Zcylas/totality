package zcylas.totality.api.rpg.resources;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Final external-review correction pass (2026-09-15): proves the durable, per-resource
 * {@link PlayerResourceStateComponent#isLegacyMigrated}/{@link PlayerResourceStateComponent#markLegacyMigrated}
 * marker (canonical §24.4's "mark migration version") behaves independently of {@link
 * PlayerResourceStateComponent#hasState} — the actual gap this marker exists to close. The
 * production JOIN-time usage (which additionally needs a real {@code ServerPlayer} to read the
 * legacy {@code PlayerResourceComponent}) is proven end-to-end by {@code
 * BaselineResourceMigrationVerification}, a dev-only self-test; this file covers the marker's own
 * storage/persistence/respawn-propagation contract in isolation.
 */
class PlayerResourceStateComponentLegacyMigrationMarkerTest {

    private static HolderLookup.Provider emptyRegistries() {
        return HolderLookup.Provider.create(Stream.of());
    }

    @Test
    void aResourceIsNotMigratedUntilExplicitlyMarked() {
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        assertFalse(state.isLegacyMigrated(PlayerResourceIds.MANA));
    }

    @Test
    void markingMigratedIsIndependentOfWhetherLiveStateExists() {
        // The exact gap this marker closes: a resource can be "migrated" (the marker is set) while
        // simultaneously having no live state at all — e.g. it was quarantined to an orphan after
        // this check ran, or removed by an unrelated future admin tool. hasState() must never be
        // treated as a substitute for this marker.
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.markLegacyMigrated(PlayerResourceIds.MANA);

        assertTrue(state.isLegacyMigrated(PlayerResourceIds.MANA));
        assertFalse(state.hasState(PlayerResourceIds.MANA), "the marker must not itself create live state");
    }

    @Test
    void theMarkerSurvivesAnNbtRoundTrip() {
        PlayerResourceStateComponent original = new PlayerResourceStateComponent(null);
        original.markLegacyMigrated(PlayerResourceIds.MANA);
        original.markLegacyMigrated(PlayerResourceIds.STAMINA);

        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        original.writeData(out);
        CompoundTag written = out.buildResult();

        PlayerResourceStateComponent reloaded = new PlayerResourceStateComponent(null);
        reloaded.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), written));

        assertTrue(reloaded.isLegacyMigrated(PlayerResourceIds.MANA));
        assertTrue(reloaded.isLegacyMigrated(PlayerResourceIds.STAMINA));
    }

    @Test
    void aPreSchema4SaveWithNoMarkerEntriesDefaultsToUnmigrated() {
        // A save written before schema 4 has no LegacyMigratedCount key at all — must default to
        // "not migrated" (0), not throw or fabricate an entry. Combined with BaselineResourceLifecycleEvents's
        // own state-presence secondary guard, this is exactly what makes a pre-correction Phase 4
        // save self-heal its marker on next join without re-importing (see that class's Javadoc).
        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        out.putInt("SchemaVersion", 3);
        out.putInt("ResourceCount", 0);
        out.putInt("OrphanedCount", 0);
        CompoundTag legacyShapedTag = out.buildResult();

        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), legacyShapedTag));

        assertFalse(state.isLegacyMigrated(PlayerResourceIds.MANA));
        assertFalse(state.isLegacyMigrated(PlayerResourceIds.STAMINA));
    }

    @Test
    void copyFromCarriesTheMigrationMarkerAcrossRespawnUnconditionally() {
        // Migration completion is a permanent fact about the player, independent of whatever
        // ResourceDeathPolicy does to the resource's actual value (see PlayerResourceStateComponentDeathPolicyTest
        // for that separate concern) — must survive respawn even for a RESET_TO_MAXIMUM resource
        // whose live state is deliberately dropped by the same copyFrom call.
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent source = new PlayerResourceStateComponent(null);
        source.instantiateScalar(PlayerResourceIds.MANA, 40);
        source.markLegacyMigrated(PlayerResourceIds.MANA);

        PlayerResourceStateComponent target = new PlayerResourceStateComponent(null);
        target.copyFrom(source, emptyRegistries());

        assertFalse(target.hasState(PlayerResourceIds.MANA), "sanity: RESET_TO_MAXIMUM still drops the value");
        assertTrue(target.isLegacyMigrated(PlayerResourceIds.MANA), "the migration marker must survive regardless");
    }
}
