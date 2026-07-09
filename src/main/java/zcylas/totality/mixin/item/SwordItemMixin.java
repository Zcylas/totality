package zcylas.totality.mixin.item;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zcylas.totality.api.rpg.combat.weapon.TotalityMeleeWeaponItem;

@Mixin(Item.class)
public abstract class SwordItemMixin {

    @Unique
    private boolean totality$isSword(ItemStack stack) {
        return (Object)this instanceof TotalityMeleeWeaponItem || stack.is(ItemTags.SWORDS);
    }

    // Shield right-click is suppressed — blocking is now triggered by the V key
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void totality$cancelShieldRightClick(Level level, Player player, InteractionHand hand,
                                                  CallbackInfoReturnable<InteractionResult> cir) {
        if ((Object)this instanceof ShieldItem) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void totality$swordBlockDuration(ItemStack stack, LivingEntity entity,
                                              CallbackInfoReturnable<Integer> cir) {
        if (totality$isSword(stack)
                && !(entity.getOffhandItem().getItem() instanceof ShieldItem)) {
            cir.setReturnValue(72000);
        }
    }

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void totality$swordBlockAnimation(ItemStack stack,
                                               CallbackInfoReturnable<ItemUseAnimation> cir) {
        if (totality$isSword(stack)) {
            cir.setReturnValue(ItemUseAnimation.BLOCK);
        }
    }
}
