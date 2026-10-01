package zcylas.totality.api.entitlement.integration;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.dialogue.DialogueComponents;
import zcylas.totality.api.entitlement.EntitlementActions;
import zcylas.totality.api.entitlement.EntitlementDefinition;
import zcylas.totality.api.entitlement.EntitlementKey;
import zcylas.totality.api.entitlement.EntitlementMutationResult;
import zcylas.totality.api.entitlement.EntitlementRetentionPolicy;
import zcylas.totality.api.entitlement.EntitlementService;
import zcylas.totality.api.entitlement.EntitlementTypeDefinition;
import zcylas.totality.api.entitlement.GrantSourceRef;
import zcylas.totality.api.entitlement.GrantSourceTypes;
import zcylas.totality.api.entitlement.PermanentEntitlementFact;

import java.util.List;
import java.util.Map;

/**
 * Phone application access (canonical §9.10, M7) — limited to what the current Phone implementation
 * defines. Only the Bank app is registered: it is the one app whose access is a genuine permanent player
 * unlock today. Phone models, tiers, installation, transfer and downgrade rules are not modelled here.
 *
 * <p>The Bank app is visible-but-locked: {@code view} is always allowed, {@code open_service} requires the
 * permanent {@code UNLOCKED} fact that the Mobile Banking quest's Banker hand-off writes.
 */
public final class PhoneAppEntitlements {

    public static final Identifier PHONE_APP_TYPE = id("phone_app");
    public static final Identifier OWNER = id("phone");
    public static final EntitlementKey BANK_APP = EntitlementKey.of(PHONE_APP_TYPE, id("bank"));

    public static final Identifier QUEST_REWARD_METHOD = id("quest_reward");
    public static final Identifier ADMIN_RESET_REASON = id("quest_full_reset");
    static final Identifier BANK_FLAG_MIGRATION = id("bank_app_flag_v1");
    static final String LEGACY_BANK_FLAG = "bank_app_unlocked";

    /** Pure access booleans that used to live in narrative flags, mapped to the entitlement that replaced them. */
    private static final Map<String, EntitlementKey> LEGACY_ACCESS_FLAGS = Map.of(LEGACY_BANK_FLAG, BANK_APP);

    static EntitlementTypeDefinition phoneAppType() {
        return EntitlementTypeDefinition.builder(PHONE_APP_TYPE, OWNER)
                .actions(EntitlementActions.OPEN_SERVICE, EntitlementActions.INSTALL)
                .permanentUnlock()
                .retention(EntitlementRetentionPolicy.EXPLICIT_PERMANENT_ACQUISITION)
                .clientDisplayView()
                .trackAvailability()
                .displayAction(EntitlementActions.OPEN_SERVICE)
                .build();
    }

    static EntitlementDefinition bankAppDefinition() {
        return EntitlementDefinition.of(BANK_APP, "totality.phone_app.bank").withVisibleByDefault(true);
    }

    /** The Mobile Banking quest's Banker hand-off: an idempotent permanent unlock with Quest provenance. */
    public static EntitlementMutationResult unlockBankApp(ServerPlayer player, Identifier questId) {
        return EntitlementService.INSTANCE.grantPermanentFact(player, BANK_APP, PermanentEntitlementFact.UNLOCKED,
                GrantSourceRef.of(GrantSourceTypes.QUEST, questId), QUEST_REWARD_METHOD, true);
    }

    /**
     * A testing/admin quest full-reset clears that quest's flags. For a flag that used to be a pure access
     * boolean, the replacing permanent unlock is explicitly revoked (audited) — never as a side effect of
     * ordinary quest rollback (canonical §14.9).
     */
    public static void onQuestFullReset(ServerPlayer player, Identifier questId, List<String> resetFlags) {
        for (String flag : resetFlags) {
            EntitlementKey key = LEGACY_ACCESS_FLAGS.get(flag);
            if (key == null) continue;
            EntitlementService.INSTANCE.revokePermanentFact(player, key, PermanentEntitlementFact.UNLOCKED,
                    GrantSourceRef.of(GrantSourceTypes.ADMIN, questId), ADMIN_RESET_REASON);
        }
    }

    /**
     * M7: one-time, versioned migration of the legacy {@code bank_app_unlocked} narrative flag into the
     * permanent Bank app unlock. The flag itself is left in place (it is no longer read as authority), so an
     * older build can still read it. Idempotent: the migration id is recorded once applied.
     */
    static void migrateLegacyBankFlag(ServerPlayer player) {
        EntitlementService service = EntitlementService.INSTANCE;
        if (service.state(player).ledger().appliedMigrations().contains(BANK_FLAG_MIGRATION)) return;
        int flag = DialogueComponents.FLAGS.get((ComponentProvider) player).getFlag(LEGACY_BANK_FLAG);
        if (flag >= 1) {
            EntitlementMutationResult result = service.grantPermanentFact(player, BANK_APP, PermanentEntitlementFact.UNLOCKED,
                    GrantSourceRef.of(GrantSourceTypes.LEGACY_MIGRATION, id(LEGACY_BANK_FLAG)), LegacyAbilityMigration.METHOD, true);
            if (result.isRejected()) {
                Totality.LOGGER.error("[Entitlement] Bank app flag migration rejected for {}: {}", player.getName().getString(), result.message());
                return;
            }
        }
        service.state(player).ledger().markMigrationApplied(BANK_FLAG_MIGRATION);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private PhoneAppEntitlements() {}
}
