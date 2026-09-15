package zcylas.totality.api.rpg.resources;

import net.minecraft.core.HolderLookup;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.integration.ResourceRemovalPolicy;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@link PlayerResourceStateComponent#copyFrom} now actually consults each resource's
 * declared {@link ResourceDeathPolicy} instead of always blanket-copying — the Phase 4 Mana/Stamina
 * migration's fix for the gap the pre-Phase-4 foundation correction pass identified (a
 * {@code RESET_TO_MAXIMUM} resource's respawn behavior would otherwise silently change from "full
 * refill" to "preserve current"). Uses the real production registry so the real Mana/Stamina
 * ({@code RESET_TO_MAXIMUM}) and Thirst ({@code ResourceLifecyclePolicy.DEFAULT}, i.e.
 * {@code KEEP_CURRENT}) definitions are exercised directly, not a synthetic stand-in.
 */
class PlayerResourceStateComponentDeathPolicyTest {

    private static HolderLookup.Provider emptyRegistries() {
        return HolderLookup.Provider.create(Stream.of());
    }

    @Test
    void copyFromDropsManaOnRespawnSinceItDeclaresResetToMaximum() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent source = new PlayerResourceStateComponent(null);
        source.instantiateScalar(PlayerResourceIds.MANA, 40);

        PlayerResourceStateComponent target = new PlayerResourceStateComponent(null);
        target.copyFrom(source, emptyRegistries());

        assertFalse(target.hasState(PlayerResourceIds.MANA),
                "RESET_TO_MAXIMUM must drop the old value on respawn, not preserve a depleted amount");
    }

    @Test
    void copyFromDropsStaminaOnRespawnSinceItDeclaresResetToMaximum() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent source = new PlayerResourceStateComponent(null);
        source.instantiateScalar(PlayerResourceIds.STAMINA, 15);

        PlayerResourceStateComponent target = new PlayerResourceStateComponent(null);
        target.copyFrom(source, emptyRegistries());

        assertFalse(target.hasState(PlayerResourceIds.STAMINA));
    }

    @Test
    void copyFromStillPreservesKeepCurrentResourcesLikeThirst() {
        // Regression guard: the death-policy consultation this test file's other cases exercise
        // must not change behavior for a resource that still declares the default (KEEP_CURRENT)
        // policy — Thirst is dormant in production, but its definition is real.
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent source = new PlayerResourceStateComponent(null);
        source.instantiateScalar(PlayerResourceIds.THIRST, 72);

        PlayerResourceStateComponent target = new PlayerResourceStateComponent(null);
        target.copyFrom(source, emptyRegistries());

        assertTrue(target.hasState(PlayerResourceIds.THIRST), "KEEP_CURRENT must still copy the value across respawn");
        assertEquals(72, target.getScalar(PlayerResourceIds.THIRST).orElseThrow().currentUnits());
    }

    @Test
    void copyFromPreservesGrantRemovalPolicyBookkeepingRegardlessOfDeathPolicy() {
        // The in-memory grantRemovalPolicies map is unconditionally carried across respawn (it is
        // not itself subject to any death policy) — confirmed here so the RESET_TO_MAXIMUM drop path
        // above is proven not to accidentally interact with this separate piece of bookkeeping.
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        PlayerResourceStateComponent source = new PlayerResourceStateComponent(null);
        source.instantiateScalar(PlayerResourceIds.MANA, 40);
        source.setGrantRemovalPolicy(PlayerResourceIds.MANA, ResourceRemovalPolicy.REMOVE_STATE);

        PlayerResourceStateComponent target = new PlayerResourceStateComponent(null);
        target.copyFrom(source, emptyRegistries());

        assertEquals(java.util.Optional.of(ResourceRemovalPolicy.REMOVE_STATE), target.getGrantRemovalPolicy(PlayerResourceIds.MANA));
    }
}
