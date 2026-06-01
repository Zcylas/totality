package zcylas.totality.item.base_weapons;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.combat.damage.TotalityDamageType;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.rpg.combat.weapon.TotalityThrownWeaponItem;
import zcylas.totality.api.rpg.combat.weapon.TotalityWeaponItem;
import zcylas.totality.api.rpg.combat.weapon.WeaponCategory;
import zcylas.totality.api.rpg.combat.weapon.WeaponType;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.entity.base_weapon.ThrownShurikenEntity;
import zcylas.totality.init.ModSounds;
import zcylas.totality.networking.stamina.StaminaServerTick;

import java.util.function.Consumer;

public class ShurikenItem extends TotalityThrownWeaponItem {

    public ShurikenItem(Properties properties, Dice damageDie, int diceCount) {
        super(properties, damageDie, diceCount, 0); // bonusDamage = 0
    }

    @Override public TotalityDamageType getDamageType()        { return DamageTypes.PIERCING; }
    @Override public AbilityScore getDefaultAbilityScore()     { return AbilityScore.DEX; }
    @Override public boolean isFinesse()                       { return true; }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            ThrownShurikenEntity shuriken = new ThrownShurikenEntity(
                    level, player, stack.copyWithCount(1), bonusDamage);
            shuriken.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, 2.5f, 0f);
            level.addFreshEntity(shuriken);

            if (!player.getAbilities().instabuild
                    && player instanceof ServerPlayer sp) {
                PlayerStaminaManager.removeStamina(sp, getThrownAttackCost());
                StaminaServerTick.syncStamina(sp);
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        player.playSound(ModSounds.SHURIKEN_THROW, 1f, 1f);
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        builder.accept(Component.translatable("item.totality.shuriken.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override public boolean isLight()      { return true; }
    @Override public int[]  getThrowRange() { return new int[]{20, 60}; }
    @Override public WeaponCategory getWeaponCategory() { return WeaponCategory.SIMPLE_RANGED; }
}