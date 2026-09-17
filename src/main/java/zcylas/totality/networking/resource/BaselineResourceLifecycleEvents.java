package zcylas.totality.networking.resource;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.magic.spell.SpellSlotComponent;
import zcylas.totality.api.magic.spell.SpellSlotComponents;
import zcylas.totality.api.magic.spell.SpellSlotRecalculator;
import zcylas.totality.api.magic.spell.SpellSlotTable;
import zcylas.totality.api.rpg.classes.ChargeComponents;
import zcylas.totality.api.rpg.classes.PlayerChargesComponent;
import zcylas.totality.api.rpg.resources.PlayerResourceComponent;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceRegistry;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.PlayerResourceStateComponent;
import zcylas.totality.api.rpg.resources.ResourceComponents;
import zcylas.totality.api.rpg.resources.ResourceMaximum;
import zcylas.totality.api.rpg.resources.ResourceResolutionContext;
import zcylas.totality.api.rpg.resources.ResourceStateComponents;
import zcylas.totality.api.rpg.resources.integration.PlayerBaselineResources;
import zcylas.totality.api.rpg.resources.state.PartitionedResourceState;

import java.util.Optional;

/**
 * Wires {@link PlayerBaselineResources#reconcile} into every lifecycle point canonical §16.12
 * requires (join, respawn, dimension transfer) — the first production trigger for {@link
 * zcylas.totality.api.rpg.resources.integration.ResourceGrantReconciler}, and the one-time legacy
 * Mana/Stamina NBT import (canonical §24.4) on join. Phase 4 Mana/Stamina migration, 2026-09-15.
 *
 * <p>{@link #migrateLegacyIfAbsent} also imports legacy Rage as of the Phase 5 migration
 * (2026-09-15, same date), and legacy Standard Spell Slots as of the Phase 6 migration
 * (2026-09-16, see {@link #migrateSpellSlots}) — reusing this exact one-time-import mechanism rather
 * than a separate one. The Food 0-100 migration (2026-09-17) reuses it a fourth time, this time
 * importing from vanilla's own {@code FoodData} rather than a Totality-owned legacy store — see
 * {@link #migrateLegacyIfAbsent}'s own trailing paragraph. Both resources' own grant reconciliation need no additional lifecycle wiring
 * beyond what already exists here: {@code BarbarianRageResources}'s and {@code
 * StandardSpellSlotResources}'s providers are registered on the same shared, global {@code
 * ResourceGrantRegistry.INSTANCE} that {@link PlayerBaselineResources}'s own reconciler already
 * iterates in full, so every {@code PlayerBaselineResources.reconcile(player)} call below (join,
 * respawn, dimension transfer) transitively reconciles both grants too — see {@code
 * ResourceGrantReconciler#reconcile}'s own Javadoc ("every registered provider"). The one additional
 * trigger this generic wiring cannot cover — Barbarian class selection — is handled directly by
 * {@code BarbarianRageAbility.registerChargePool} calling {@code BarbarianRageResources.reconcile}
 * itself; Standard Spell Slots needs no such extra call since every class-mutation path already
 * routes through {@code ClassChangeReconciler}, which reconciles the shared registry directly.
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

        migrateSpellSlots(player, state);

        // Food 0-100 migration (2026-09-17): same one-time-import contract as Mana/Stamina/Rage
        // above, reusing the exact isLegacyMigrated/markLegacyMigrated marker. "Legacy initialized"
        // is unconditionally true — unlike Mana/Stamina's -1 sentinel, vanilla FoodData always has a
        // real value (a brand-new player's own vanilla default is foodLevel=20), so there is never a
        // "nothing to migrate" case to distinguish; every player, new or existing, imports through
        // this exact same path. The conversion is an exact x5 (100/20), never a rounding
        // approximation: old 20/18/14/10/1/0 -> new 100/90/70/50/5/0, matching the task's locked
        // migration examples precisely.
        migrateOne(state, PlayerResourceIds.FOOD, true, player.getFoodData().getFoodLevel() * 5);
    }

    /**
     * Phase 6 Standard Spell Slot migration (2026-09-16): same one-time-import contract as Mana/
     * Stamina/Rage above, but {@code PARTITIONED_POOL}-shaped (nine tiers, never a tenth — see
     * {@link SpellSlotTable}'s class Javadoc) instead of scalar, so it cannot reuse {@link
     * #migrateOne} directly.
     *
     * <p>{@link SpellSlotComponent} has no initialization sentinel distinguishing "never touched" from
     * "genuinely all-zero" (see {@code StandardSpellSlotsResourceAdapter}'s own Javadoc on this exact
     * point), so "legacy initialized" cannot be read off the legacy store itself the way Mana/Stamina's
     * {@code -1} sentinel or Rage's pool-map key presence allow. Instead this gates on the player's
     * <em>current</em> combined caster level (the same {@link SpellSlotRecalculator
     * #computeCombinedCasterLevel} the grant provider itself uses): a player with real entitlement
     * right now was, by construction, entitled under the identical class levels before this migration
     * shipped (class data is untouched by this migration), so their legacy remaining values are
     * meaningful and worth importing; a player with no current entitlement has nothing meaningful to
     * import (any leftover legacy array is either a stale value from a class they no longer have —
     * moot, since no grant will retain state for them anyway — or a genuinely-untouched all-zero
     * array that must NOT be allowed to pre-empt a future genuine first grant with a frozen 0/0).
     * Values are clamped against the freshly resolved 1-9 maxima (task requirement); tier 10 is never
     * read from the legacy array at all — it is discarded by construction, never migrated, never
     * resurrected.
     */
    private static void migrateSpellSlots(ServerPlayer player, PlayerResourceStateComponent state) {
        Identifier id = PlayerResourceIds.SPELL_SLOTS;
        if (state.isLegacyMigrated(id)) {
            return;
        }
        if (!state.hasState(id) && SpellSlotRecalculator.computeCombinedCasterLevel(player) > 0) {
            Optional<zcylas.totality.api.rpg.resources.PlayerResourceDefinition> definition = PlayerResourceRegistry.INSTANCE.get(id);
            Optional<ResourceMaximum> maxOpt = definition.flatMap(def ->
                    PlayerResourceService.INSTANCE.resolveMaximum(player, def, ResourceResolutionContext.EMPTY));
            if (maxOpt.isPresent() && maxOpt.get() instanceof ResourceMaximum.Partitioned partitionedMax) {
                SpellSlotComponent legacy = SpellSlotComponents.get(player);
                PartitionedResourceState partitionState = state.instantiatePartitioned(id);
                for (int level = 1; level <= SpellSlotTable.STANDARD_SLOT_LEVELS; level++) {
                    long legacyRemaining = Math.max(0, (long) legacy.getMax(level) - legacy.getUsed(level));
                    long max = partitionedMax.effectiveByPartition().getOrDefault(level, 0L);
                    partitionState.setCurrent(level, Math.min(legacyRemaining, max));
                }
                // Tier 10 deliberately never read from `legacy` above — see this method's own Javadoc.
            }
        }
        state.markLegacyMigrated(id);
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
