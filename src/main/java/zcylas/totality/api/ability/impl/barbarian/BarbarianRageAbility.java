package zcylas.totality.api.ability.impl.barbarian;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.jspecify.annotations.Nullable;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.rpg.classes.*;
import zcylas.totality.api.rpg.rest.RestType;
import zcylas.totality.init.ModEffects;

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

    public static final int RAGE_DURATION_TICKS = 1200; // 1 minute

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
        // Applying the effect triggers RageEffect.onEffectAdded which handles mechanics,
        // visuals, sound, and the "⚔ Rage!" notification.
        player.addEffect(new MobEffectInstance(ModEffects.RAGE, RAGE_DURATION_TICKS, 0, false, false, true));
    }

    @Override
    public void onToggleOff(ServerPlayer player) {
        // Removing the effect triggers RageEffect.onEffectRemoved which handles cleanup
        // and the "Rage ended." notification (only if toggle was still active).
        player.removeEffect(ModEffects.RAGE);
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

    /**
     * Force-stops rage on death. Deactivates toggle first (so onEffectRemoved won't notify),
     * then removes the effect which triggers registry cleanup via RageEffect.onEffectRemoved.
     * Called from StatsServerEvents.COPY_FROM when alive == false.
     */
    public static void forceStop(ServerPlayer player) {
        AbilityComponent abilities = AbilityComponents.ABILITIES.get((ComponentProvider) player);
        if (abilities.isToggleActive(ID)) {
            abilities.deactivateToggle(ID);
        }
        player.removeEffect(ModEffects.RAGE);
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