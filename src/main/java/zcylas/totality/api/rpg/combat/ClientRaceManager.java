package zcylas.totality.api.rpg.race;

/**
 * Client-side cache of the local player's chosen race.
 * Populated when a sync packet is received from the server.
 * Used by render layers (elf ears etc.) and the race selection screen.
 */
public final class ClientRaceManager {

    private static Race race = null;

    private ClientRaceManager() {}

    public static void apply(Race incoming) {
        race = incoming;
    }

    public static Race getRace() {
        return race;
    }

    public static boolean hasRace() {
        return race != null;
    }
}