package zcylas.totality.api.entitlement.integration;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.entitlement.EntitlementGrantProvider;
import zcylas.totality.api.entitlement.EntitlementService;
import zcylas.totality.api.entitlement.GrantSourceRef;
import zcylas.totality.api.entitlement.GrantSourceTypes;
import zcylas.totality.api.magic.spell.Spell;

import java.util.Set;

/**
 * The former "every spell is default-unlocked" development scaffolding, isolated as an explicit debug grant
 * (canonical §4.6, §10.10). Disabled by default. While enabled it grants every spell that reports
 * {@code isDefault()} through {@code totality:debug / totality:universal_spell_access}: source-bound,
 * {@code progressionEligible = false}, never persisted, and impossible to convert into permanent knowledge.
 * Disabling it removes the access on the next reconciliation.
 *
 * <p>Enable with {@code /totality entitlement debug universal_spells true} (runtime only) or start the server
 * with {@code -Dtotality.entitlement.debugUniversalSpellAccess=true}.
 */
public final class DebugSpellAccessProvider implements EntitlementGrantProvider {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("totality", "debug_universal_spell_access");
    public static final GrantSourceRef SOURCE = GrantSourceRef.of(GrantSourceTypes.DEBUG,
            Identifier.fromNamespaceAndPath("totality", "universal_spell_access"));
    public static final String SYSTEM_PROPERTY = "totality.entitlement.debugUniversalSpellAccess";

    private static volatile boolean enabled = Boolean.getBoolean(SYSTEM_PROPERTY);

    public static boolean isEnabled() {
        return enabled;
    }

    /** Toggles the debug setting and reconciles every online player. */
    public static void setEnabled(MinecraftServer server, boolean value) {
        enabled = value;
        Totality.LOGGER.warn("[Entitlement] Debug universal spell access {} (non-progression)", value ? "ENABLED" : "disabled");
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            EntitlementService.INSTANCE.reconcileProvider(player, ID);
        }
    }

    @Override
    public Identifier providerId() {
        return ID;
    }

    @Override
    public Set<Identifier> sourceTypeIds() {
        return Set.of(GrantSourceTypes.DEBUG);
    }

    @Override
    public void collectGrants(ServerPlayer player, Collector collector) {
        if (!enabled) return;
        for (Ability ability : AbilityRegistry.all()) {
            if (ability instanceof Spell && ability.isDefault()) {
                collector.grant(AbilityEntitlements.keyFor(ability), SOURCE);
            }
        }
    }
}
