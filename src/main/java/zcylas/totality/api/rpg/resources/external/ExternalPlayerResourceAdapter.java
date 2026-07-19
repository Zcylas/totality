package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.ResourceSnapshot;

import java.util.Optional;
import java.util.Set;

/**
 * Query-time view over a resource whose mutable state is owned by another authoritative system
 * (vanilla Health/Combat, vanilla Food/Hunger). See
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §6.5.
 *
 * <p><b>Deliberate deviation from the canonical {@code ServerPlayer}-typed interface shown in the
 * design document:</b> this Phase 2A adapter is typed against {@link Player}, not
 * {@code ServerPlayer}. Health and Food both read values that vanilla already keeps synchronized
 * to the client through its own native packets ({@link ExternalResourceClientMirrorMode#NATIVE_SYNCHRONIZATION}) —
 * {@code player.getHealth()}/{@code getMaxHealth()} and {@code player.getFoodData()} return
 * correct, already-authoritative-mirrored values on {@code LocalPlayer} exactly as they do on
 * {@code ServerPlayer}. Typing this interface against the common {@link Player} superclass lets
 * the exact same query path serve both the server (future authoritative callers) and the client
 * HUD (canonical §19.8's "HP, Mana, Stamina, and Food/Hunger are constant HUD elements") without
 * a second client-only query surface or a duplicate packet. This is safe specifically because
 * Phase 2A is query-only — a future mutating adapter (restore/drain/set) would need to re-narrow
 * to {@code ServerPlayer} for those operations, since only the server may authoritatively mutate.
 */
public interface ExternalPlayerResourceAdapter {

    /** Matches the adapter identifier a {@link PlayerResourceDefinition} references via {@code externalAdapterId()}. */
    Identifier id();

    /**
     * Builds a read-only snapshot from the authoritative owner's current state. Must never mutate
     * the owner's state, instantiate any Resource API player state, or send a packet.
     *
     * <p>Returns {@link Optional#empty()} — never {@code null}, and never a fabricated/clamped
     * placeholder — when the owner's actual state cannot be represented as a valid snapshot (e.g.
     * a non-positive maximum). {@link zcylas.totality.api.rpg.resources.PlayerResourceService}
     * turns an empty result into a structured {@link zcylas.totality.api.rpg.resources.ResourceQueryFailureReason#MALFORMED_OWNER_STATE}
     * failure. This is a real, typed "I cannot answer" signal (introduced in Phase 2B for
     * {@code BreathResourceAdapter}), not {@code null} used as ordinary control flow.
     */
    Optional<ResourceSnapshot> snapshot(Player player, PlayerResourceDefinition definition);

    Set<ExternalResourceOperationSupport> supportedOperations();

    ExternalResourceClientMirrorMode clientMirrorMode();
}
