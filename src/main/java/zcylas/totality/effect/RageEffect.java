package zcylas.totality.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.combat.damage.DamageResistanceComponent;
import zcylas.totality.api.combat.damage.DamageResistanceRecalculator;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.check.AbilityCheckResolver;
import zcylas.totality.api.rpg.combat.CastingRestrictionRegistry;
import zcylas.totality.api.rpg.combat.DamageBonus;
import zcylas.totality.api.rpg.combat.DamageBonusRegistry;
import zcylas.totality.api.rpg.combat.RollModifierRegistry;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.networking.notification.SendNotificationPayload;

public class RageEffect extends MobEffect {

    public static final RageEffect INSTANCE = new RageEffect();

    private RageEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xCC3333);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }

    @Override
    public void onEffectAdded(LivingEntity entity, int amplifier) {
        if (!(entity instanceof ServerPlayer sp)) return;

        CastingRestrictionRegistry.register(sp, BarbarianRageAbility.ID,
                p -> "You cannot cast spells while raging.");

        DamageResistanceComponent comp = DamageResistanceComponent.get(sp);
        comp.addResistance(DamageTypes.BLUDGEONING, false);
        comp.addResistance(DamageTypes.PIERCING,    false);
        comp.addResistance(DamageTypes.SLASHING,    false);

        DamageBonusRegistry.register(sp, BarbarianRageAbility.ID, (p, ability, magical) -> {
            if (magical) return null;
            return new DamageBonus(BarbarianRageAbility.getRageDamageBonus(p), "Rage");
        });

        RollModifierRegistry.register(sp, BarbarianRageAbility.ID, new RollModifierRegistry.RollModifier() {
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

        // Sound + particles
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                net.minecraft.sounds.SoundEvents.RAVAGER_ROAR,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.8f);
        if (entity.level() instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    30, 0.4, 0.4, 0.4, 0.05);
        }
        SendNotificationPayload.send(sp, "⚔ Rage!", 0xFFCC3333);
    }

    @Override
    public void onEffectRemoved(MobEffectInstance effectInstance, LivingEntity entity) {
        if (entity instanceof ServerPlayer sp) {
            CastingRestrictionRegistry.remove(sp, BarbarianRageAbility.ID);
            DamageResistanceRecalculator.recalculate(sp);
            DamageBonusRegistry.remove(sp, BarbarianRageAbility.ID);
            RollModifierRegistry.remove(sp, BarbarianRageAbility.ID);

            // Only notify and deactivate if the toggle wasn't already cleared (e.g. forceStop)
            AbilityComponent abilities = AbilityComponents.ABILITIES.get((ComponentProvider) sp);
            if (abilities.isToggleActive(BarbarianRageAbility.ID)) {
                abilities.deactivateToggle(BarbarianRageAbility.ID);
                SendNotificationPayload.send(sp, "Rage ended.", SendNotificationPayload.GRAY);
            }
        }
        super.onEffectRemoved(effectInstance, entity);
    }
}
