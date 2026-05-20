package zcylas.totality.networking.magic.rune;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Client-side cache of the player's rune knowledge.
 * Populated when a sync packet is received from the server.
 */
public final class ClientRuneKnowledgeManager {

    private static final Set<String> knownRunes = new HashSet<>();

    private ClientRuneKnowledgeManager() {}

    public static void apply(Set<String> runes) {
        knownRunes.clear();
        knownRunes.addAll(runes);
    }

    public static boolean knowsRune(String runeId) {
        return knownRunes.contains(runeId);
    }

    public static Set<String> getKnownRunes() {
        return Collections.unmodifiableSet(knownRunes);
    }
}