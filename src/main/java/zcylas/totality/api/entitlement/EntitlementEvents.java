package zcylas.totality.api.entitlement;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Post-commit entitlement events (canonical §8). Fired only after a transaction committed; listeners
 * receive the transaction id. Owning systems react here — e.g. the Ability system stops an active ability
 * whose last authorization path ended. Entitlement itself never performs gameplay shutdown.
 */
public final class EntitlementEvents {

    /** Permanent acquisition / revocation, grant gained / lost (with reason), suspension changes. */
    public static final Event<Mutation> MUTATION = EventFactory.createArrayBacked(Mutation.class,
            listeners -> (player, change, transactionId) -> {
                for (Mutation listener : listeners) listener.onMutation(player, change, transactionId);
            });

    /**
     * Fired when a key of an availability-tracked type gains or loses its availability for
     * {@code actionId}, whatever the cause (grant loss, suspension, revocation, requirement change).
     */
    public static final Event<AvailabilityChanged> AVAILABILITY_CHANGED = EventFactory.createArrayBacked(
            AvailabilityChanged.class,
            listeners -> (player, key, actionId, available) -> {
                for (AvailabilityChanged listener : listeners) listener.onAvailabilityChanged(player, key, actionId, available);
            });

    @FunctionalInterface
    public interface Mutation {
        void onMutation(ServerPlayer player, EntitlementChange change, UUID transactionId);
    }

    @FunctionalInterface
    public interface AvailabilityChanged {
        void onAvailabilityChanged(ServerPlayer player, EntitlementKey key, Identifier actionId, boolean available);
    }

    private EntitlementEvents() {}
}
