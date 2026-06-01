package zcylas.totality.api.ability.impl.barbarian;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.combat.damage.DamageResistanceRecalculator;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.check.AbilityCheckResolver;
import zcylas.totality.api.rpg.classes.*;
import zcylas.totality.api.rpg.combat.CastingRestrictionRegistry;
import zcylas.totality.api.rpg.combat.DamageBonus;
import zcylas.totality.api.rpg.combat.DamageBonusRegistry;
import zcylas.totality.api.rpg.combat.RollModifierRegistry;
import zcylas.totality.api.rpg.rest.RestType;

import zcylas.totality.api.ability.trait.Traits;
import zcylas.totality.api.ability.trait.Trait;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.StatsComponents;
import zcylas.totality.networking.notification.SendNotificationPayload;

public class BarbarianRageAbility extends Ability {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("totality", "barbarian_rage");
    // Rage charge pool ID
    public static final Identifier CHARGE_ID = Identifier.fromNamespaceAndPath("totality", "barbarian_rage");

    // Rage damage bonus per class level (index = class level 1-25)
    private static final int[] RAGE_DAMAGE_BONUS = {
            2,2,2,2,2,2,2,2, // levels 1-8: +2
            3,3,3,3,3,3,3,   // levels 9-15: +3
            4,4,4,4,4,4,4,4,4,4 // levels 16-25: +4
    };

    // Rage uses per class level
    private static final int[] RAGE_CHARGES = {
            2,2,3,3,3,4,4,4,4,4,4,5,5,5,5,5,5,6,6,6,6,6,6,6,6
    };

    private static final int RAGE_DURATION_TICKS = 3600; // 3 minutes

    public BarbarianRageAbility() {
        super(
                ID,
                "Barbarian Rage",
                "Channel primal fury to enhance your combat. While raging, you gain bonus damage on " +
                        "Strength-based attacks, resistance to physical damage, and advantage on Strength checks. " +
                        "Rage ends if you don't attack or take damage for 20 seconds.",
                Type.TOGGLE,
                0, // no cooldown — gated by charges
                Identifier.fromNamespaceAndPath("totality", "textures/ability/barbarian_rage.png"),
                Source.CLASS,
                "Barbarian",
                "The storm does not apologize for the thunder."
        );
    }

    @Override
    public boolean canActivate(ServerPlayer player, @Nullable AbilityContext context) {
        // Must be a Barbarian
        if (!ClassComponents.get(player).hasClass(TotalityClasses.BARBARIAN_ID)) return false;

        AbilityComponent abilities = AbilityComponents.ABILITIES.get((ComponentProvider) player);

        // If already raging → can toggle off
        if (abilities.isToggleActive(ID)) return true;

        // Must have a charge
        return ChargeComponents.get(player).hasCharge(CHARGE_ID);
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext context) {
        AbilityComponent abilities = AbilityComponents.ABILITIES.get((ComponentProvider) player);

        if (abilities.isToggleActive(ID)) {
            // Already raging → end rage
            onToggleOff(player);
            abilities.deactivateToggle(ID);
        } else {
            // Not raging → start rage if we have a charge
            if (ChargeComponents.get(player).consume(CHARGE_ID)) {
                onToggleOn(player);
                abilities.activateToggle(ID, RAGE_DURATION_TICKS);
            }
        }
    }

    @Override
    public void onToggleOn(ServerPlayer player) {
        CastingRestrictionRegistry.register(player, ID,
                p -> "You cannot cast spells while raging.");
        DamageResistanceRecalculator.recalculate(player);

        // Register damage bonus
        DamageBonusRegistry.register(player, ID, (p, ability, magical) -> {
            if (magical) return null;
            return new DamageBonus(getRageDamageBonus(p), "Rage");
        });

        // Register STR advantage modifier
        RollModifierRegistry.register(player, ID, new RollModifierRegistry.RollModifier() {
            @Override
            public RollType modifySave(AbilityScore score, RollType current) {
                return score == AbilityScore.STR && current == RollType.NORMAL
                        ? RollType.ADVANTAGE : current;
            }
            @Override
            public AbilityCheckResolver.RollMode modifyCheck(AbilityScore score,
                                                             AbilityCheckResolver.RollMode current) {
                return score == AbilityScore.STR && current == AbilityCheckResolver.RollMode.NORMAL
                        ? AbilityCheckResolver.RollMode.ADVANTAGE : current;
            }
        });

        // Visuals
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.RAVAGER_ROAR,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.8f);
        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                    player.getX(), player.getY() + 1, player.getZ(),
                    30, 0.4, 0.4, 0.4, 0.05);
        }
        SendNotificationPayload.send(player, "⚔ Rage!", 0xFFCC3333);
    }

    @Override
    public void onToggleOff(ServerPlayer player) {
        CastingRestrictionRegistry.remove(player, ID);
        DamageResistanceRecalculator.recalculate(player);
        DamageBonusRegistry.remove(player, ID);
        RollModifierRegistry.remove(player, ID);
        SendNotificationPayload.send(player, "Rage ended.", SendNotificationPayload.GRAY);
    }

    @Override
    public void onToggleTick(ServerPlayer player) {
        // Small ambient particles every 10 ticks while raging
        if (player.tickCount % 10 == 0 && player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME,
                    player.getX(), player.getY() + 1, player.getZ(),
                    3, 0.3, 0.3, 0.3, 0.01);
        }
    }

    /** Call this when a Barbarian attacks or takes damage to keep rage alive. */
    public static void refreshCombatTimer(ServerPlayer player) {
        AbilityComponents.ABILITIES.get((ComponentProvider) player)
                .refreshCombatTick(ID, player.tickCount);
    }

    /** Static helper — is this player currently raging? */
    public static boolean isRaging(ServerPlayer player) {
        return AbilityComponents.ABILITIES.get((ComponentProvider) player).isToggleActive(ID);
    }

    /** Rage damage bonus for the current class level. */
    public static int getRageDamageBonus(ServerPlayer player) {
        int classLevel = ClassComponents.get(player).getClassLevel(TotalityClasses.BARBARIAN_ID);
        int idx = Math.max(0, Math.min(classLevel - 1, RAGE_DAMAGE_BONUS.length - 1));
        return RAGE_DAMAGE_BONUS[idx];
    }

    /** Register the rage charge pool for a player. Called on class selection. */
    public static void registerChargePool(ServerPlayer player) {
        int classLevel = Math.max(1, ClassComponents.get(player).getClassLevel(TotalityClasses.BARBARIAN_ID));
        int maxCharges = RAGE_CHARGES[Math.min(classLevel - 1, RAGE_CHARGES.length - 1)];
        ChargeComponents.get(player).ensurePool(CHARGE_ID, maxCharges, RestType.SHORT, 1);
    }

    /** Call on character level-up to update max charges for the new class level. */
    public static void updateChargePool(ServerPlayer player) {
        int classLevel = ClassComponents.get(player).getClassLevel(TotalityClasses.BARBARIAN_ID);
        if (classLevel <= 0) return;
        int maxCharges = RAGE_CHARGES[Math.min(classLevel - 1, RAGE_CHARGES.length - 1)];
        ChargeComponents.get(player).updatePoolMax(CHARGE_ID, maxCharges);
    }
}