package zcylas.totality.init.events;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinel for the 2026-09-15 Rage synchronization fix's JOIN-lifecycle half —
 * a source-text sentinel, not a runtime proof: {@code ServerPlayConnectionEvents.JOIN}'s handler
 * needs a real {@code ServerPlayer}/{@code MinecraftServer}, unavailable under plain JUnit (the
 * same constraint documented by the Phase 3A implementation report for this same event). Real
 * pool-sync-value behavior is covered with executing tests in
 * {@code PlayerChargesRageCharacterizationTest} (missing-pool creation, existing-pool maximum
 * increase/decrease). This test only pins that the JOIN handler actually calls the sync.
 */
class PlayerConnectionEventsChargeSyncSourceRegressionTest {

    private static final Path SOURCE =
            Path.of("src/main/java/zcylas/totality/init/events/PlayerConnectionEvents.java");

    private static String read() throws Exception {
        assertTrue(Files.exists(SOURCE), "expected to find source file at " + SOURCE);
        return Files.readString(SOURCE);
    }

    @Test
    void joinHandlerSyncsPlayerChargesLikeEveryOtherClientMirroredComponent() throws Exception {
        String source = read();
        int joinStart = source.indexOf("ServerPlayConnectionEvents.JOIN.register(");
        int joinEnd = source.indexOf("ServerPlayerEvents.AFTER_RESPAWN.register(");
        assertTrue(joinStart >= 0 && joinEnd > joinStart, "expected to locate the JOIN handler body");
        String joinBody = source.substring(joinStart, joinEnd);

        assertTrue(joinBody.contains("ChargeComponents.PLAYER_CHARGES.sync((ComponentProvider) player)"),
                "JOIN must sync ChargeComponents.PLAYER_CHARGES like every other client-mirrored " +
                        "component (Abilities/Runes/Currency/Equipment/Dialogue/Alchemy/Skills/" +
                        "Masteries/Stats/SpellSlots/Class/Stamina) — without it, a reconnecting " +
                        "player's already-persisted charge pools (e.g. Rage) never reach their " +
                        "client mirror until an unrelated mutation or respawn happens to sync it.");
    }

    @Test
    void respawnHandlerStillRegistersAndSyncsTheChargePoolForBarbarians() throws Exception {
        // Guards against the JOIN fix accidentally being a copy/paste replacement of the
        // pre-existing, differently-shaped AFTER_RESPAWN handling (which also re-registers the
        // pool via registerChargePool, not just syncs it) rather than an addition alongside it.
        String source = read();
        int respawnStart = source.indexOf("ServerPlayerEvents.AFTER_RESPAWN.register(");
        assertTrue(respawnStart >= 0, "expected to locate the AFTER_RESPAWN handler");
        String respawnBody = source.substring(respawnStart);

        assertTrue(respawnBody.contains("BarbarianRageAbility.registerChargePool(newPlayer)"),
                "AFTER_RESPAWN must still re-register the Barbarian charge pool");
        assertTrue(respawnBody.contains("ChargeComponents.PLAYER_CHARGES.sync((ComponentProvider) newPlayer)"),
                "AFTER_RESPAWN must still sync the charge pool for Barbarians");
    }
}
