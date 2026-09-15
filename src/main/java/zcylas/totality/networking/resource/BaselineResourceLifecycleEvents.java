package zcylas.totality.networking.resource;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.rpg.classes.ChargeComponents;
import zcylas.totality.api.rpg.classes.PlayerChargesComponent;
import zcylas.totality.api.rpg.resources.PlayerResourceComponent;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceStateComponent;
import zcylas.totality.api.rpg.resources.ResourceComponents;
import zcylas.totality.api.rpg.resources.ResourceStateComponents;
import zcylas.totality.api.rpg.resources.integration.PlayerBaselineResources;

import java.util.Optional;

/**
 * Wires {@link PlayerBaselineResources#reconcile} into every lifecycle point canonical §16.12
 * requires (join, respawn, dimension transfer) — the first production trigger for {@link
 * zcylas.totality.api.rpg.resources.integration.ResourceGrantReconciler}, and the one-time legacy
 * Mana/Stamina NBT import (canonical §24.4) on join. Phase 4 Mana/Stamina migration, 2026-09-15.
 *
 * <p>{@link #migrateLegacyIfAbsent} also imports legacy Rage as of the Phase 5 migration
 * (2026-09-15, same date) — reusing this exact one-time-import mechanism rather than a separate one.
 * Rage's own grant reconciliation needs no additional lifecycle wiring beyond what already exists
 * here: {@code BarbarianRageResources}'s provider is registered on the same shared, global {@code
 * ResourceGrantRegistry.INSTANCE} that {@link PlayerBaselineResources}'s own reconciler already
 * iterates in full, so every {@code PlayerBaselineResources.reconcile(player)} call below (join,
 * respawn, dimension transfer) transitively reconciles Rage's grant too — see {@code
 * ResourceGrantReconciler#reconcile}'s own Javadoc ("every registered provider"). The one additional
 * trigger this generic wiring cannot cover — Barbarian class selection — is handled directly by
 * {@code BarbarianRageAbility.registerChargePool} calling {@code BarbarianRageResources.reconcile}
 * itself.
 *
 * <p><b>Registration order matters.</b> This must run before {@code StatsServerEvents}'s
 * {@code JOIN}/{@code COPY_FROM} handlers (which call {@code PlayerResourceRecalculator
 * .recalculateAndRestore}, itself reading {@code PlayerManaManager.getMana}/{@code
 * PlayerStaminaManager.getStamina} and sending an immediate legacy sync packet with whatever it
 * reads) — otherwise that packet would carry a stale "0" for a resource this class has not yet
 * (re)instantiated. It must also run before {@code ResourceSyncLifecycleEvents} schedules the
 * Generic full snapshot, so a freshly (re)instantiated resource is actually included in it — that
 * class is deliberately registered last within {@code ModEvents.register()} for the same reason.
 * Registering this class immediately after {@code PlayerComponentEvents.init()} (which is what
 * actually invokes every component's {@code copyFrom} on {@code ServerPlayerEvents.COPY_FROM}, this
 * class included) and before both of the above satisfies every ordering constraint at once.
 *
 * <p>The migration-import step only runs on {@code JOIN}, not on respawn/dimension-transfer:
 * {@code PlayerResourceComponent.copyFrom} resets legacy Mana/Stamina to "uninitialized" on every
 * respawn in lockstep with {@link PlayerResourceStateComponent}'s own death-policy-driven drop (see
 * that class's {@code copyFrom}) — both stores reset together, so there is never real legacy data to
 * import on a respawn, only on a genuine first-ever join after this migration ships. Legacy Rage's
 * own {@code copyFrom} does <b>not</b> reset on respawn (it is a blanket preserve-all-pools copy),
 * but JOIN-only import is still correct for it: the durable {@code isLegacyMigrated} marker (checked
 * first, before any legacy value is even read) makes every call after the first a genuine no-op
 * regardless of what the legacy store still holds, and JOIN always fires before any respawn in the
 * same session — so a player can never reach a respawn with the marker still unset.
 */
public final class BaselineResourceLifecycleEvents {

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            migrateLegacyIfAbsent(player);
            PlayerBaselineResources.reconcile(player);
        });

        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                PlayerBaselineResources.reconcile(newPlayer));

        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
                PlayerBaselineResources.reconcile(player));
    }

    /**
     * Canonical §24.4 NBT migration, steps 1-2/6: import the exact legacy current value the first
     * (and only the first) time this resource has never been migrated for this player, then mark it
     * migrated (durably, via {@link PlayerResourceStateComponent#markLegacyMigrated}) so it is never
     * re-consulted again — <b>not</b> merely "Generic state currently exists," which final
     * external-review correction (2026-09-15) found insufficient: a resource that later becomes
     * absent from live state for any reason other than a genuine grant loss (persisted-data
     * quarantine due to corruption/model mismatch being the realistic case — see {@code
     * readLiveEntry}) would otherwise look exactly like "never migrated" to a presence-only check,
     * silently resurrecting a stale legacy value a player may have long since spent past. The state-
     * presence check is now a secondary guard *inside* the migrated-once branch (skip the actual
     * import, but still record the marker) — this is what makes a schema-3 save that was already
     * correctly migrated under the original (marker-less) Phase 4 code self-heal its marker on its
     * next join without ever re-importing, rather than requiring a hard schema cutover.
     *
     * <p>Public (not merely package-visible) so a dev-only verification can exercise it directly
     * against a {@code TotalityFakePlayer}, which never fires {@code ServerPlayConnectionEvents
     * .JOIN} — matching this codebase's established {@code ResourceGrantReconciler#reconcile}/
     * {@code *Verification} precedent of verifying real production logic through its real public
     * entry points rather than a reimplementation.
     */
    public static void migrateLegacyIfAbsent(ServerPlayer player) {
        PlayerResourceStateComponent state = ResourceStateComponents.get(player);
        PlayerResourceComponent legacy = ResourceComponents.get(player);
        migrateOne(state, PlayerResourceIds.MANA, legacy.isManaInitialized(), legacy.getMana());
        migrateOne(state, PlayerResourceIds.STAMINA, legacy.isStaminaInitialized(), legacy.getStamina());

        // Phase 5 Rage migration (2026-09-15): same one-time-import contract as Mana/Stamina above,
        // reusing the exact isLegacyMigrated/markLegacyMigrated marker mechanism rather than a new
        // one. "Legacy initialized" means the legacy PlayerChargesComponent already had a Rage pool
        // registered (i.e. this player had already selected Barbarian before this migration shipped)
        // — a player who never selected Barbarian has no legacy pool to import, and simply receives
        // Rage's ordinary AtMaximum grant instantiation once they do (see BarbarianRageResources).
        Optional<PlayerChargesComponent> legacyCharges = ChargeComponents.maybeGet(player);
        boolean legacyRageInitialized = legacyCharges.isPresent()
                && legacyCharges.get().getAllPools().containsKey(BarbarianRageAbility.CHARGE_ID);
        int legacyRageValue = legacyRageInitialized ? legacyCharges.get().getCurrent(BarbarianRageAbility.CHARGE_ID) : 0;
        migrateOne(state, PlayerResourceIds.RAGE, legacyRageInitialized, legacyRageValue);
    }

    private static void migrateOne(PlayerResourceStateComponent state, Identifier id, boolean legacyInitialized, int legacyValue) {
        if (state.isLegacyMigrated(id)) {
            return; // durably recorded already — never re-consult legacy again for this id, ever
        }
        if (legacyInitialized && !state.hasState(id)) {
            state.instantiateScalar(id, Math.max(0, legacyValue));
        }
        // Marked regardless of whether an import actually happened above: "nothing to migrate" (a
        // genuinely new player) and "already had live state" (the schema-3 self-heal case) are both
        // legitimate terminal outcomes that must never be reconsidered on a later join.
        state.markLegacyMigrated(id);
    }

    private BaselineResourceLifecycleEvents() {}
}
