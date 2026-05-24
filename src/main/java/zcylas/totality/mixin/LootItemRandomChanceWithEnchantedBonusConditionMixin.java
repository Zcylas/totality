package zcylas.totality.mixin;

import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithEnchantedBonusCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import zcylas.totality.api.magic.grimoire.damage.SpellDamageSource;

@Mixin(LootItemRandomChanceWithEnchantedBonusCondition.class)
public class LootItemRandomChanceWithEnchantedBonusConditionMixin {

    @ModifyVariable(
            method = "test",
            at = @At(
                    value = "STORE"
            ),
            name = "enchantmentLevel"
    )
    private int totality$adjustLootingChance(int enchantmentLevel, LootContext context) {
        if (!(context.getOptionalParameter(LootContextParams.DAMAGE_SOURCE)
                instanceof SpellDamageSource spellSource)) return enchantmentLevel;
        int spellLuck = spellSource.getLuckLevel();
        return Math.max(enchantmentLevel, spellLuck);
    }
}