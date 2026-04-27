package zcylas.totality.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zcylas.totality.api.magic.damage.SpellDamageSource;

@Mixin(EnchantedCountIncreaseFunction.class)
public class EnchantedCountIncreaseFunctionMixin {

    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void totality$adjustLooting(ItemStack itemStack, LootContext context,
                                        CallbackInfoReturnable<ItemStack> cir) {
        if (!(context.getOptionalParameter(LootContextParams.DAMAGE_SOURCE)
                instanceof SpellDamageSource spellSource)) return;
        int spellLuck = spellSource.getLuckLevel();
        if (spellLuck <= 0) return;

        EnchantedCountIncreaseFunctionAccessor accessor =
                (EnchantedCountIncreaseFunctionAccessor)(Object) this;

        Entity killer = context.getOptionalParameter(LootContextParams.ATTACKING_ENTITY);
        if (!(killer instanceof LivingEntity)) return;

        // Use spell luck level instead of enchantment level
        float addition = spellLuck * accessor.getCount().getFloat(context);
        itemStack.grow(Math.round(addition));
        if (accessor.getLimit() > 0) {
            itemStack.limitSize(accessor.getLimit());
        }
        cir.setReturnValue(itemStack);
    }
}