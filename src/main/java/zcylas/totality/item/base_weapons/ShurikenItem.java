package zcylas.totality.item.base_weapons;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import zcylas.totality.api.rpg.combat.weapon.TotalityWeaponItem;
import zcylas.totality.api.rpg.combat.weapon.WeaponType;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;
import zcylas.totality.entity.base_weapon.ThrownShurikenEntity;
import zcylas.totality.init.ModSounds;
import zcylas.totality.networking.stamina.StaminaServerTick;

import java.util.function.Consumer;

public class ShurikenItem extends Item implements TotalityWeaponItem {

    private final float throwDamage;

    public ShurikenItem(Properties properties, float throwDamage) {
        super(properties);
        this.throwDamage = throwDamage;
    }

    public float getThrowDamage() {
        return throwDamage;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide()) {
            ItemStack toThrow = stack.copyWithCount(1);
            ThrownShurikenEntity shuriken = new ThrownShurikenEntity(level, player, toThrow, throwDamage);
            shuriken.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 2.5f, 0.0f);
            level.addFreshEntity(shuriken);

            if (!player.getAbilities().instabuild && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                PlayerStaminaManager.removeStamina(serverPlayer, getThrownAttackCost());
                StaminaServerTick.syncStamina(serverPlayer);
            }

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        player.playSound(ModSounds.SHURIKEN_THROW, 1.0f, 1.0f);

        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.totality.shuriken.tooltip")
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.totality.shuriken.damage",
                        String.format("%.1f", throwDamage))
                .withStyle(ChatFormatting.DARK_RED));
    }

    @Override
    public WeaponType getWeaponType() {
        return WeaponType.THROWN;
    }
}