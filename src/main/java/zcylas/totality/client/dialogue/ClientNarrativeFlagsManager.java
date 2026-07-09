package zcylas.totality.client.dialogue;

import java.util.HashMap;
import java.util.Map;

/** Client-side cache of the player's {@link zcylas.totality.api.dialogue.NarrativeFlagsComponent},
 *  used to gate client-only UI (e.g. the phone's Bank app) on narrative flags without a
 *  network round-trip. */
public final class ClientNarrativeFlagsManager {

    private static Map<String, Integer> flags = new HashMap<>();

    private ClientNarrativeFlagsManager() {}

    public static void sync(Map<String, Integer> newFlags) {
        flags = newFlags;
    }

    public static int getFlag(String key) {
        return flags.getOrDefault(key, 0);
    }

    public static boolean hasFlag(String key) {
        return getFlag(key) >= 1;
    }
}
