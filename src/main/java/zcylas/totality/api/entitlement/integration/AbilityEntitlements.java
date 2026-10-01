package zcylas.totality.api.entitlement.integration;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.entitlement.EntitlementActions;
import zcylas.totality.api.entitlement.EntitlementDecision;
import zcylas.totality.api.entitlement.EntitlementKey;
import zcylas.totality.api.entitlement.EntitlementQueryContext;
import zcylas.totality.api.entitlement.EntitlementRetentionPolicy;
import zcylas.totality.api.entitlement.EntitlementService;
import zcylas.totality.api.entitlement.EntitlementTypeDefinition;
import zcylas.totality.api.entitlement.ProgressionContext;
import zcylas.totality.api.entitlement.requirement.EntitlementDependencyKey;
import zcylas.totality.api.magic.spell.Spell;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ability-system adapter: maps registered abilities and spells to entitlement keys and answers the
 * Ability system's access questions through the Entitlement service. Cooldowns, favorites, equipped and
 * selected abilities, toggles and channeling stay in {@code AbilityComponent}.
 *
 * <p>Spells are their own type ({@code totality:spell}) so the same content id can never collide with an
 * ability. Spell knowledge is {@link EntitlementRetentionPolicy#DOMAIN_OWNED}: Entitlement never stores
 * permanent spell knowledge — that belongs to the future Spells V2 design.
 */
public final class AbilityEntitlements {

    public static final Identifier ABILITY_TYPE = id("ability");
    public static final Identifier SPELL_TYPE = id("spell");
    public static final Identifier OWNER = id("ability_system");

    /** Reported by {@code AbilityComponent} whenever its equipped ability or selected spell changes. */
    public static final EntitlementDependencyKey SELECTION = EntitlementDependencyKey.of(id("ability_selection"));

    static EntitlementTypeDefinition abilityType() {
        return EntitlementTypeDefinition.builder(ABILITY_TYPE, OWNER)
                .actions(EntitlementActions.USE, EntitlementActions.ACTIVATE, EntitlementActions.EQUIP, EntitlementActions.SELECT)
                .permanentUnlock()
                // Admin unlocks and the legacy-migration fallback may create independent permanent facts.
                .retention(EntitlementRetentionPolicy.EXPLICIT_PERMANENT_ACQUISITION)
                .trackAvailability()
                .content(id -> AbilityRegistry.get(id) != null && !(AbilityRegistry.get(id) instanceof Spell))
                .build();
    }

    static EntitlementTypeDefinition spellType() {
        return EntitlementTypeDefinition.builder(SPELL_TYPE, OWNER)
                .actions(EntitlementActions.USE, EntitlementActions.ACTIVATE, EntitlementActions.SELECT,
                        EntitlementActions.PREPARE, EntitlementActions.LEARN)
                .retention(EntitlementRetentionPolicy.DOMAIN_OWNED)
                .trackAvailability()
                .content(id -> AbilityRegistry.get(id) instanceof Spell)
                .build();
    }

    /** The typed key for an ability or spell id. Unknown ids map to the ability type and are unregistered. */
    public static EntitlementKey keyFor(Identifier abilityId) {
        return EntitlementKey.of(AbilityRegistry.get(abilityId) instanceof Spell ? SPELL_TYPE : ABILITY_TYPE, abilityId);
    }

    public static EntitlementKey keyFor(Ability ability) {
        return EntitlementKey.of(ability instanceof Spell ? SPELL_TYPE : ABILITY_TYPE, ability.getId());
    }

    /** Execution-time revalidation for one ability action. */
    public static EntitlementDecision check(ServerPlayer player, Identifier abilityId, Identifier actionId) {
        return EntitlementService.INSTANCE.checkServerAction(player, keyFor(abilityId), actionId);
    }

    public static boolean canUse(ServerPlayer player, Identifier abilityId, Identifier actionId) {
        return check(player, abilityId, actionId).allowed();
    }

    /** Whether the player's current use of this ability rests only on debug access, in which case it must run
     *  inside a {@link ProgressionContext} non-progression scope. Served from the per-revision decision cache,
     *  so per-tick callers do not re-evaluate. */
    public static boolean isDebugOnly(ServerPlayer player, Identifier abilityId) {
        EntitlementDecision decision = EntitlementService.INSTANCE.query(player, keyFor(abilityId), EntitlementActions.USE,
                EntitlementQueryContext.Purpose.UI_PREVIEW);
        return decision.allowed() && decision.snapshot().debugOnly();
    }

    /** Abilities and spells the player may currently use, in registry order. Served from the cached
     *  accessible sets, so per-tick callers do not re-evaluate entitlements. */
    public static Set<Identifier> accessibleAbilityIds(ServerPlayer player) {
        Set<EntitlementKey> abilities = EntitlementService.INSTANCE.accessible(player, ABILITY_TYPE, EntitlementActions.USE);
        Set<EntitlementKey> spells = EntitlementService.INSTANCE.accessible(player, SPELL_TYPE, EntitlementActions.USE);
        Set<Identifier> ids = new LinkedHashSet<>();
        for (Ability ability : AbilityRegistry.all()) {
            EntitlementKey key = keyFor(ability);
            if (abilities.contains(key) || spells.contains(key)) ids.add(ability.getId());
        }
        return ids;
    }

    public static boolean isAbilityKey(EntitlementKey key) {
        return key.typeId().equals(ABILITY_TYPE) || key.typeId().equals(SPELL_TYPE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private AbilityEntitlements() {}
}
