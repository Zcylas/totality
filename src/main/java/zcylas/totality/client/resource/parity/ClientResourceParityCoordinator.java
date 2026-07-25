package zcylas.totality.client.resource.parity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.client.ClientResourceQueryResult;
import zcylas.totality.api.rpg.resources.client.ClientResourceService;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityGenericAccess;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityLifecycle;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityPoll;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityTracker;

/**
 * Phase 3B-2B client-only shadow-parity integration entry point. Registered from {@code
 * TotalityClient.onInitializeClient()} — {@link #tick()} on {@code END_CLIENT_TICK} (strictly after
 * the existing generic Resource synchronization tick handling, {@code
 * ClientResourceSyncManager.tick()}), {@link #clear()} on {@code
 * ClientPlayConnectionEvents.JOIN}/{@code DISCONNECT} and {@code
 * ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE}.
 *
 * <p>Strictly observational: every tick reads the already-settled generic façade and legacy mirror
 * state and records a comparison — it never mutates either source, never delays or reorders packet
 * application, and never influences gameplay, rendering, or Resource availability. No logging, no
 * debug command, no consumer of the resulting observations exists yet (see {@link
 * ClientResourceParityObservations} for the narrow read-only view reserved for Phase 3B-2C/Phase
 * 3B-3).
 *
 * <p>Implements {@link ClientResourceParityGenericAccess} itself (a two-method direct delegation to
 * {@code ClientResourceService.INSTANCE} — the Phase 3B-1 façade, the only generic read boundary
 * this integration uses) rather than a separate top-level class, since the delegation has no logic
 * of its own worth a dedicated file. The four-legacy-mirror read side is substantial enough to
 * warrant its own class — see {@link LegacyClientResourceParityReaders}.
 */
@Environment(EnvType.CLIENT)
public final class ClientResourceParityCoordinator implements ClientResourceParityGenericAccess {

    private static final ClientResourceParityCoordinator INSTANCE = new ClientResourceParityCoordinator();
    private static final ClientResourceParityTracker TRACKER = new ClientResourceParityTracker();
    private static final ClientResourceParityLifecycle<LocalPlayer> LIFECYCLE =
            new ClientResourceParityLifecycle<>(TRACKER);

    private ClientResourceParityCoordinator() {}

    /**
     * Called once per {@code END_CLIENT_TICK}. Idle (no poll, no reset beyond the single
     * null-transition reset already handled by {@link ClientResourceParityLifecycle}) while no
     * local player exists — never reads Rage or the other legacy managers as though a valid player
     * existed. Polls all four eligible Resources with fresh values exactly once when a local player
     * is present, using {@link ClientResourceParityPoll#pollOnce}, never {@code advanceDeadline}.
     */
    public static void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        LIFECYCLE.beginTick(player).ifPresent(currentTick ->
                ClientResourceParityPoll.pollOnce(TRACKER, currentTick, INSTANCE, LegacyClientResourceParityReaders.INSTANCE));
    }

    /** Full lifecycle reset — clears every tracked observation and resets the tick counter and
     *  player-identity baseline. Registered against JOIN/DISCONNECT/dimension-change; also reached
     *  indirectly via {@link ClientResourceParityLifecycle#beginTick} detecting a local-player
     *  identity change (covers death/respawn, which fires neither JOIN nor DISCONNECT). */
    public static void clear() {
        LIFECYCLE.clear();
    }

    /** Package-private read accessor for {@link ClientResourceParityObservations} — the one class
     *  outside this coordinator permitted to read tracker state directly. Never made public: an
     *  ordinary consumer must go through the narrow read-only view, never this mutable tracker
     *  reference. */
    static ClientResourceParityTracker tracker() {
        return TRACKER;
    }

    @Override
    public ClientResourceQueryResult queryScalar(Identifier resourceId) {
        return ClientResourceService.INSTANCE.queryScalar(resourceId);
    }

    @Override
    public ClientResourceQueryResult queryPartitioned(Identifier resourceId) {
        return ClientResourceService.INSTANCE.queryPartitioned(resourceId);
    }
}
