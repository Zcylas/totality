package zcylas.totality.item.spell_material;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import zcylas.totality.menu.ComponentPouchMenu;

/**
 * Component Pouch — right-clicking opens a filtered 2-row GUI.
 * Only accepts SpellMaterialIngredient items.
 * Contents persisted in DataComponents.CONTAINER on the item stack.
 */
public class ComponentPouchItem extends Item {

    public ComponentPouchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        int slotIndex = hand == InteractionHand.MAIN_HAND
                ? sp.getInventory().getSelectedSlot()
                : Inventory.SLOT_OFFHAND;

        ItemStack pouchStack = sp.getItemInHand(hand);
        SimpleContainer contents = ComponentPouchMenu.loadFrom(pouchStack);

        sp.openMenu(new ExtendedMenuProvider<Integer>() {
            @Override
            public Integer getScreenOpeningData(ServerPlayer p) { return slotIndex; }

            @Override
            public Component getDisplayName() { return Component.literal("Component Pouch"); }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player p) {
                return new ComponentPouchMenu(syncId, inv, slotIndex, contents);
            }
        });
        player.level().playSound(null,
                player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.BUNDLE_DROP_CONTENTS,
                net.minecraft.sounds.SoundSource.PLAYERS,
                1.0f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        return InteractionResult.SUCCESS_SERVER;
    }
}