package zcylas.totality.client.resource.parity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityObservation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Narrow, read-only client shadow-parity observation view. This is the one class outside {@link
 * ClientResourceParityCoordinator} permitted to read tracker state — it never exposes the mutable
 * backing map, never exposes a mutation method, and no gameplay consumer, HUD, or screen reads it.
 * No logging, no debug command, no packet export, and no cross-session persistence exists for this
 * data yet.
 *
 * <p><b>Intended future consumers, not built in this slice:</b> Phase 3B-2C's bounded diagnostics
 * (log-level, coalesced reporting of persistent mismatches) and Phase 3B-3's parity verification
 * tooling (a debug command dumping the current snapshot). Nothing in Phase 3B-2B calls either
 * accessor below.
 */
@Environment(EnvType.CLIENT)
public final class ClientResourceParityObservations {

    private static final Identifier[] TRACKED_RESOURCE_IDS = {
            PlayerResourceIds.MANA,
            PlayerResourceIds.STAMINA,
            PlayerResourceIds.SPELL_SLOTS,
            PlayerResourceIds.RAGE
    };

    private ClientResourceParityObservations() {}

    /** The latest observation for one Resource id, if it has ever been polled. */
    public static Optional<ClientResourceParityObservation> latest(Identifier resourceId) {
        return ClientResourceParityCoordinator.tracker().latest(resourceId);
    }

    /** An immutable snapshot of every currently-tracked Resource's latest observation. Built fresh
     *  on each call from the four known eligible Resource ids — never a live view into the
     *  tracker's own backing map. */
    public static Map<Identifier, ClientResourceParityObservation> snapshot() {
        Map<Identifier, ClientResourceParityObservation> result = new LinkedHashMap<>();
        for (Identifier resourceId : TRACKED_RESOURCE_IDS) {
            ClientResourceParityCoordinator.tracker().latest(resourceId)
                    .ifPresent(observation -> result.put(resourceId, observation));
        }
        return Collections.unmodifiableMap(result);
    }
}
