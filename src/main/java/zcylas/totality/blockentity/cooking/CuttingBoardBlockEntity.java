package zcylas.totality.blockentity.cooking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import zcylas.totality.init.ModBlockEntities;
import zcylas.totality.init.items.SKIngredientItems;

/**
 * The Cutting Board's single ingredient slot (one stack of one ingredient) and its V1 chopping step.
 *
 * <p>V1 knows one preparation, Garlic -> 4 Garlic Cloves, and chopping always succeeds. Planned (not implemented):
 * data-driven cutting recipes in the Cooking API, a Knife/tool system replacing the temporary sword, and a DEX-based
 * dice check (tool quality, proficiency and ingredient difficulty as modifiers) feeding food quality with
 * Perfect/Bad outcomes.
 *
 * <p>All changes happen on the server; clients receive the stored stack through the block entity update packet.
 * Chopped output goes into the player's inventory, and whatever does not fit is popped on top of the board, so an
 * ingredient is only ever consumed together with its full output.
 */
public class CuttingBoardBlockEntity extends BlockEntity {

    private static final String INGREDIENT_KEY = "Ingredient";

    private ItemStack ingredient = ItemStack.EMPTY;

    public CuttingBoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CUTTING_BOARD, pos, state);
    }

    /** What one chop of this ingredient yields; empty when the board cannot chop it. */
    public static ItemStack chopResult(ItemStack ingredient) {
        return ingredient.is(SKIngredientItems.GARLIC) ? new ItemStack(SKIngredientItems.GARLIC_CLOVE, 4) : ItemStack.EMPTY;
    }

    public static boolean canChop(ItemStack stack) {
        return !chopResult(stack).isEmpty();
    }

    public ItemStack getIngredient() {
        return ingredient;
    }

    /** Only the same ingredient stacks up, and never beyond one full stack: nothing on the board is overwritten. */
    public boolean canAdd(ItemStack stack) {
        if (!canChop(stack)) return false;
        if (ingredient.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(ingredient, stack) && ingredient.getCount() < ingredient.getMaxStackSize();
    }

    /** Moves one item from the player's stack onto the board (survival players lose it from their hand). */
    public void addOne(ItemStack stack, Player player) {
        if (!canAdd(stack)) return;
        if (ingredient.isEmpty()) ingredient = stack.copyWithCount(1);
        else ingredient.grow(1);
        stack.consume(1, player);
        playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 1.0F);
        changed();
    }

    /** Hands the whole stored stack back to the player; what does not fit drops at the player's feet. */
    public void takeAll(Player player) {
        if (ingredient.isEmpty()) return;
        ItemStack taken = ingredient;
        ingredient = ItemStack.EMPTY;
        changed();
        if (!player.getInventory().add(taken)) player.drop(taken, false);
        playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0F);
    }

    /**
     * One chop: consumes exactly one stored ingredient and delivers its whole result (inventory first, the rest on top
     * of the board). The sword takes one point of wear, as a tool does for one use.
     */
    public void chop(Player player, InteractionHand hand) {
        ItemStack result = chopResult(ingredient);
        if (result.isEmpty() || !(level instanceof ServerLevel serverLevel)) return;
        ingredient.shrink(1);
        if (ingredient.isEmpty()) ingredient = ItemStack.EMPTY;
        changed();
        player.getInventory().add(result);
        if (!result.isEmpty()) Block.popResourceFromFace(serverLevel, worldPosition, Direction.UP, result);
        player.getItemInHand(hand).hurtAndBreak(1, player, hand);
        playSound(SoundEvents.SHEEP_SHEAR, 1.4F);
    }

    private void playSound(SoundEvent sound, float pitch) {
        if (level != null) level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 0.8F, pitch);
    }

    private void changed() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Whatever is on the board drops when the board is removed (broken, exploded or replaced). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !ingredient.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, ingredient);
            ingredient = ItemStack.EMPTY;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!ingredient.isEmpty()) output.store(INGREDIENT_KEY, ItemStack.CODEC, ingredient);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ingredient = input.read(INGREDIENT_KEY, ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
