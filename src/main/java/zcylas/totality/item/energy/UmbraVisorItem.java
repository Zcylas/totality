package zcylas.totality.item.energy;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.industrial.energy.UEArmorItem;

public class UmbraVisorItem extends UEArmorItem {

    private static final Identifier ARMOR_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "umbra_visor_armor");

    private static final AttributeModifier ARMOR_MODIFIER = new AttributeModifier(
            ARMOR_MODIFIER_ID, 4.0,
            AttributeModifier.Operation.ADD_VALUE);

    public UmbraVisorItem(Item.Properties properties, long capacity, long maxInput, long maxOutput) {
        super(properties, EquipmentSlot.HEAD, capacity, maxInput, maxOutput);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level,
                              Entity owner, @Nullable EquipmentSlot slot) {
        if (!(owner instanceof Player player)) return;

        if (slot != EquipmentSlot.HEAD) {
            removeEffects(player);
            return;
        }

        boolean hasEnergy = getStoredEnergy(stack) > 0;

        if (hasEnergy) {
            // Apply night vision — refresh every tick so it never expires
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false));

            // Add armor modifier if not present
            if (player.getAttribute(Attributes.ARMOR) != null
                    && !player.getAttributes().hasModifier(Attributes.ARMOR, ARMOR_MODIFIER_ID)) {
                player.getAttribute(Attributes.ARMOR).addTransientModifier(ARMOR_MODIFIER);
            }
        } else {
            removeEffects(player);
        }
    }

    private void removeEffects(Player player) {
        player.removeEffect(MobEffects.NIGHT_VISION);
        if (player.getAttribute(Attributes.ARMOR) != null
                && player.getAttributes().hasModifier(Attributes.ARMOR, ARMOR_MODIFIER_ID)) {
            player.getAttribute(Attributes.ARMOR).removeModifier(ARMOR_MODIFIER_ID);
        }
    }

    public static void registerDamageHandler() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof Player player)) return true;

            ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
            if (!(helmet.getItem() instanceof UmbraVisorItem visor)) return true;

            long stored = visor.getStoredEnergy(helmet);
            if (stored <= 0) return true;

            // Drain energy proportional to damage — 10 UE per damage point
            long energyCost = (long) (amount * 50);
            long actualCost = Math.min(energyCost, stored);
            visor.setStoredEnergy(helmet, stored - actualCost);

            return true;
        });
    }
}