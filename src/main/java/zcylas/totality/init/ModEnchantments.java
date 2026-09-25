package zcylas.totality.init;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import zcylas.totality.Totality;

/**
 * V2 balance pass, §5 — Impact I-V, a Mining Damage-only enchantment (no vanilla effect component:
 * it has no combat/attribute effect at all, so it literally cannot touch attack damage). Its level
 * is read directly by {@link zcylas.totality.api.mining.MiningDamageCalculator}, the same way this
 * project already reads Efficiency. {@code supported_items}/{@code primary_items} is
 * {@link ModTags#ENCHANTABLE_MINING_DAMAGE}, generated from the authored mining-source table itself
 * (see {@code ModItemTagProvider}) so Impact can never end up on an item with no Mining Damage to add
 * to (Gold Pickaxe/Axe included, until Gold gets an authored row of its own).
 *
 * <p>Cost/weight mirror vanilla Efficiency's (weight 10, min cost 1+10/level, max cost 51+10/level,
 * anvil cost 1) — no cost/weight balance was specified for Impact, so these are inherited defaults,
 * not a chosen final balance.
 */
public final class ModEnchantments {

    private ModEnchantments() {}

    public static final ResourceKey<Enchantment> IMPACT =
            ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "impact"));

    public static void bootstrap(BootstrapContext<Enchantment> context) {
        HolderGetter<Item> items = context.lookup(Registries.ITEM);
        context.register(IMPACT, Enchantment.enchantment(
                Enchantment.definition(
                        items.getOrThrow(ModTags.ENCHANTABLE_MINING_DAMAGE),
                        10,
                        5,
                        Enchantment.dynamicCost(1, 10),
                        Enchantment.dynamicCost(51, 10),
                        1,
                        EquipmentSlotGroup.MAINHAND
                )).build(IMPACT.identifier()));
    }
}
