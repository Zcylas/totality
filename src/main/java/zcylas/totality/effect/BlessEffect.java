package zcylas.totality.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.DiceBonus;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.magic.spell.restoration.BlessSpell;
import zcylas.totality.api.rpg.check.AbilityCheckResolver;
import zcylas.totality.api.rpg.combat.RollModifierRegistry;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.networking.notification.SendNotificationPayload;

import java.util.List;

public class BlessEffect extends MobEffect {

    public static final BlessEffect INSTANCE = new BlessEffect();

    private BlessEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFCC33);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }

    @Override
    public void onEffectAdded(LivingEntity entity, int amplifier) {
        if (!(entity instanceof ServerPlayer sp)) return;

        RollModifierRegistry.register(sp, BlessSpell.ID, new RollModifierRegistry.RollModifier() {
            @Override
            public RollType modifySave(AbilityScore score, RollType current) { return current; }

            @Override
            public AbilityCheckResolver.RollMode modifyCheck(AbilityScore score,
                                                             AbilityCheckResolver.RollMode current) {
                return current;
            }

            @Override
            public List<DiceBonus> getAttackBonusList(AbilityScore score) {
                return List.of(new DiceBonus("Bless", Dice.D4.roll(sp.getRandom())));
            }

            @Override
            public List<DiceBonus> getSaveBonusList(AbilityScore score) {
                return List.of(new DiceBonus("Bless", Dice.D4.roll(sp.getRandom())));
            }
        });

        SendNotificationPayload.send(sp, "✦ Blessed — +1d4 to attacks & saves", 0xFFFFCC33);
    }

    @Override
    public void onEffectRemoved(MobEffectInstance effectInstance, LivingEntity entity) {
        if (entity instanceof ServerPlayer sp) {
            RollModifierRegistry.remove(sp, BlessSpell.ID);
            SendNotificationPayload.send(sp, "Bless faded", 0xFF888844);
        }
        super.onEffectRemoved(effectInstance, entity);
    }
}
