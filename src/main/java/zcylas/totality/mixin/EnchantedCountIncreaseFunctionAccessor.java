package zcylas.totality.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnchantedCountIncreaseFunction.class)
public interface EnchantedCountIncreaseFunctionAccessor {
    @Accessor("enchantment")
    Holder<Enchantment> getEnchantment();

    @Accessor("count")
    NumberProvider getCount();

    @Accessor("limit")
    int getLimit();
}