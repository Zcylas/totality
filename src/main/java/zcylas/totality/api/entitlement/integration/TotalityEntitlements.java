package zcylas.totality.api.entitlement.integration;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.entitlement.EntitlementCatalog;
import zcylas.totality.api.entitlement.EntitlementComponents;
import zcylas.totality.api.entitlement.EntitlementEvents;
import zcylas.totality.api.entitlement.EntitlementKey;
import zcylas.totality.api.entitlement.EntitlementService;
import zcylas.totality.api.entitlement.GrantSourceTypes;
import zcylas.totality.api.entitlement.PermanentEntitlementFact;
import zcylas.totality.api.entitlement.ProgressionContext;

import java.util.List;

/**
 * Wires the Entitlement API into Totality: registers the types, source types, providers, contributor,
 * condition types and definitions that existing systems need, and owns the join / respawn sequence.
 */
public final class TotalityEntitlements {

    private static final Identifier RESET_ALL = Identifier.fromNamespaceAndPath("totality", "reset_all_command");

    private TotalityEntitlements() {}

    public static void register() {
        EntitlementCatalog catalog = EntitlementCatalog.INSTANCE;
        EntitlementComponents.register();

        for (Identifier sourceType : List.of(GrantSourceTypes.BASELINE, GrantSourceTypes.CLASS, GrantSourceTypes.SUBCLASS,
                GrantSourceTypes.SPECIES, GrantSourceTypes.ORIGIN, GrantSourceTypes.MASTERY, GrantSourceTypes.QUEST)) {
            catalog.registerSourceType(sourceType);
        }

        catalog.registerType(AbilityEntitlements.abilityType());
        catalog.registerType(AbilityEntitlements.spellType());
        catalog.registerType(PhoneAppEntitlements.phoneAppType());
        catalog.registerDefinition(PhoneAppEntitlements.bankAppDefinition());

        catalog.registerConditionType(TotalityConditionTypes.HAS_CLASS);
        catalog.registerConditionType(TotalityConditionTypes.CLASS_LEVEL_AT_LEAST);
        catalog.registerConditionType(TotalityConditionTypes.SPECIES);
        catalog.registerConditionType(TotalityConditionTypes.ORIGIN);
        catalog.registerConditionType(TotalityConditionTypes.NARRATIVE_FLAG_AT_LEAST);

        catalog.registerProvider(new BaselineAbilityGrantProvider());
        catalog.registerProvider(new ClassEntitlementGrantProvider());
        catalog.registerProvider(new AncestryEntitlementGrantProvider());
        catalog.registerProvider(new MasteryAbilityGrantProvider());
        catalog.registerProvider(new DebugSpellAccessProvider());
        catalog.registerContributor(new AbilitySelectionContributor());

        List<String> problems = catalog.validate();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid entitlement catalog: " + problems);
        }

        ServerTickEvents.END_SERVER_TICK.register(EntitlementService.INSTANCE::tick);
        // Entities a player spawns inside a non-progression scope inherit it (ProgressionContext contract).
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> ProgressionContext.onEntityLoad(entity));
        EntitlementEvents.AVAILABILITY_CHANGED.register((player, key, actionId, available) -> {
            if (AbilityEntitlements.isAbilityKey(key)) onAbilityAvailabilityChanged(player, key, available);
        });
        if (DebugSpellAccessProvider.isEnabled()) {
            Totality.LOGGER.warn("[Entitlement] Debug universal spell access is ENABLED by system property (non-progression)");
        }
    }

    /**
     * Join sequence (canonical §3.12–3.13): rebuild every source-bound grant from its source system, run the
     * versioned legacy migrations against that reconstructed provenance, then record availability without
     * reporting the restored state as new acquisitions.
     */
    public static void onJoin(ServerPlayer player) {
        EntitlementService service = EntitlementService.INSTANCE;
        service.reconcileAll(player);
        LegacyAbilityMigration.apply(player);
        PhoneAppEntitlements.migrateLegacyBankFlag(player);
        service.baselineAvailability(player);
    }

    /** Respawn: report UNTIL_DEATH grants that ended, then rebuild source-bound grants for the new entity. */
    public static void onRespawn(ServerPlayer player) {
        EntitlementService service = EntitlementService.INSTANCE;
        service.reportDeathRemovals(player);
        service.reconcileAll(player);
        service.baselineAvailability(player);
        AbilityComponents.ABILITIES.sync((ComponentProvider) player);
    }

    /** Ancestry (Species/Origin) changed: reconcile its provider and invalidate ancestry conditions. */
    public static void onAncestryChanged(ServerPlayer player) {
        EntitlementService.INSTANCE.reconcileProvider(player, AncestryEntitlementGrantProvider.ID);
        EntitlementService.INSTANCE.invalidate(player, TotalityConditionTypes.ANCESTRY_DEPENDENCY);
    }

    /** Class state changed (selection, level, subclass, reset): reconcile and invalidate class conditions. */
    public static void onClassChanged(ServerPlayer player) {
        EntitlementService.INSTANCE.reconcileProvider(player, ClassEntitlementGrantProvider.ID);
        EntitlementService.INSTANCE.invalidate(player, TotalityConditionTypes.CLASS_DEPENDENCY);
    }

    public static void onMasteriesChanged(ServerPlayer player) {
        EntitlementService.INSTANCE.reconcileProvider(player, MasteryAbilityGrantProvider.ID);
    }

    /** Admin reset ({@code /totality resetall}): revokes, audited, every permanent ability fact (admin unlocks,
     *  legacy fallbacks). Source-bound grants are untouched — they follow their sources. */
    public static void revokePermanentAbilityFacts(ServerPlayer player) {
        EntitlementService service = EntitlementService.INSTANCE;
        for (EntitlementKey key : List.copyOf(service.state(player).ledger().keysWithFacts())) {
            if (!AbilityEntitlements.isAbilityKey(key)) continue;
            for (PermanentEntitlementFact fact : PermanentEntitlementFact.values()) {
                service.revokePermanentFact(player, key, fact, EntitlementCommands.ADMIN_SOURCE, RESET_ALL);
            }
        }
    }

    /**
     * The Ability system's own reaction to access changes (canonical §14.2–14.3): when an ability's last
     * authorization path ends, its active channel / toggle / passive is shut down by the ability itself.
     * Entitlement only reported the loss.
     */
    private static void onAbilityAvailabilityChanged(ServerPlayer player, EntitlementKey key, boolean available) {
        AbilityComponent abilities = AbilityComponents.ABILITIES.get((ComponentProvider) player);
        Ability ability = AbilityRegistry.get(key.contentId());
        if (!available && ability != null) {
            if (abilities.isChanneling(ability.getId())) {
                abilities.stopChanneling();
                ability.onChannelStop(player, null);
            }
            if (abilities.isToggleActive(ability.getId())) {
                ability.onToggleOff(player);
                abilities.deactivateToggle(ability.getId());
            }
            if (ability.getType() == Ability.Type.PASSIVE) {
                ability.onPassiveRemoved(player);
            }
        }
        AbilityComponents.ABILITIES.sync((ComponentProvider) player);
    }
}
