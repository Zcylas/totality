package zcylas.totality.api.ability.impl;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.ability.trait.Trait;
import zcylas.totality.api.core.movement.MovementMode;
import zcylas.totality.api.core.movement.MovementModeProvider;

import java.util.List;
import java.util.Set;

/**
 * A reusable passive ability class for species/ancestry physiology passives.
 *
 * Each species gets its own named instance with its own display info,
 * but shares the same underlying implementation.
 *
 * Example:
 *   public static final PhysiologyPassive VILTRUMITE_PHYSIOLOGY =
 *       new PhysiologyPassive(
 *           Identifier.of("totality", "viltrumite_physiology"),
 *           "Viltrumite Physiology",
 *           "Your inner-ear graviton field...",
 *           Identifier.of("totality", "textures/ability/viltrumite_physiology.png"),
 *           Source.ANCESTRY, "Viltrumite", "Born to conquer. Built to survive.",
 *           Set.of(MovementMode.FLIGHT, MovementMode.POWER_SPRINT, MovementMode.SUPER_LEAP),
 *           List.of(
 *               Traits.noFallDamage(),
 *               Traits.knockbackResistance("viltrumite_knockback", 1.0),
 *               Traits.attackDamage("viltrumite_attack", 4.0),
 *               Traits.armor("viltrumite", 4.0, 3.0)
 *           )
 *       );
 */
public class PhysiologyPassive extends Ability implements MovementModeProvider {

    private final Set<MovementMode> grantedModes;
    private final List<Trait>       traits;

    public PhysiologyPassive(Identifier id,
                             String displayName,
                             String description,
                             Identifier icon,
                             Source source,
                             String sourceDetail,
                             String flavourText,
                             Set<MovementMode> grantedModes,
                             List<Trait> traits) {
        super(id, displayName, description, Type.PASSIVE, 0,
                icon, source, sourceDetail, flavourText);
        this.grantedModes = Set.copyOf(grantedModes);
        this.traits       = List.copyOf(traits);
    }

    // ── MovementModeProvider ──────────────────────────────────────────────────

    @Override
    public Set<MovementMode> getGrantedModes() {
        return grantedModes;
    }

    // ── Passive tick — apply all traits, self-healing ─────────────────────────

    @Override
    public void onPassiveTick(ServerPlayer player) {
        for (Trait trait : traits) {
            trait.apply(player);
        }
    }

    // ── Passive removed — clean up all traits ─────────────────────────────────

    @Override
    public void onPassiveRemoved(ServerPlayer player) {
        for (Trait trait : traits) {
            trait.remove(player);
        }
        // Ground the player if they were flying via this passive
        player.getAbilities().flying  = false;
        player.getAbilities().mayfly  = false;
        player.onUpdateAbilities();
    }

    // ── Not used for passives ─────────────────────────────────────────────────

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext context) {}

    @Override
    public boolean isDefault() { return false; }
}