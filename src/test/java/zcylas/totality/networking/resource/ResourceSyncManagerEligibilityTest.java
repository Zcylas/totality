package zcylas.totality.networking.resource;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.TestResourceBootstrap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ResourceSyncManager#isEligibleForGenericSync(Identifier)} is the single shared gate every
 * generic-sync path (full snapshot query, delta query) must route through — this exercises that
 * decision directly against the real production registry, without needing a {@code ServerPlayer}
 * (unlike {@code ResourceSyncManager}'s query paths, which do).
 */
class ResourceSyncManagerEligibilityTest {

    @Test
    void nativelyMirroredResourcesAreIneligible() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertFalse(ResourceSyncManager.isEligibleForGenericSync(PlayerResourceIds.HEALTH),
                "Health is NATIVE_SYNCHRONIZATION — vanilla already reliably mirrors it");
        assertFalse(ResourceSyncManager.isEligibleForGenericSync(PlayerResourceIds.FOOD),
                "Food is NATIVE_SYNCHRONIZATION — vanilla already reliably mirrors it");
        assertFalse(ResourceSyncManager.isEligibleForGenericSync(PlayerResourceIds.BREATH),
                "Breath is NATIVE_SYNCHRONIZATION — vanilla already reliably mirrors it");
    }

    @Test
    void legacyBespokeMirroredResourcesAreStillEligibleInParallel() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        assertTrue(ResourceSyncManager.isEligibleForGenericSync(PlayerResourceIds.MANA));
        assertTrue(ResourceSyncManager.isEligibleForGenericSync(PlayerResourceIds.STAMINA));
        assertTrue(ResourceSyncManager.isEligibleForGenericSync(PlayerResourceIds.SPELL_SLOTS));
        assertTrue(ResourceSyncManager.isEligibleForGenericSync(PlayerResourceIds.RAGE));
    }

    @Test
    void unregisteredIdIsIneligible() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();

        Identifier unknown = Identifier.fromNamespaceAndPath("totality", "definitely_not_registered");
        assertFalse(ResourceSyncManager.isEligibleForGenericSync(unknown),
                "an id with no registered PlayerResourceDefinition must be discarded safely, never queried");
    }
}
