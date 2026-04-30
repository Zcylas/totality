package zcylas.totality.blockentity.energy;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.energy.HasSidedEnergy;
import zcylas.totality.api.energy.UETransferTicker;
import zcylas.totality.api.energy.base.SimpleSidedUEContainer;
import zcylas.totality.api.item.HasSidedItems;
import zcylas.totality.api.item.ItemSideMode;
import zcylas.totality.api.item.ItemTransferTicker;
import zcylas.totality.api.item.SimpleSidedItemContainer;
import zcylas.totality.block.energy.ElectricFurnaceBlock;
import zcylas.totality.init.ModBlockEntities;
import zcylas.totality.menu.energy.ElectricFurnaceMenu;

import java.util.Optional;

public class ElectricFurnaceBlockEntity extends BlockEntity
        implements ExtendedMenuProvider<BlockPos>, HasSidedEnergy, HasSidedItems, WorldlyContainer {

    public static final long CAPACITY         = 10_000L;
    public static final long INPUT_OUTPUT     = 64L;
    public static final long ENERGY_PER_SMELT = 1_000L;
    public static final int  SMELT_TIME_TOTAL = 200;

    public static final int SLOT_INPUT  = 0;
    public static final int SLOT_OUTPUT = 1;

    private static final int[] SLOTS_INPUT  = {SLOT_INPUT};
    private static final int[] SLOTS_OUTPUT = {SLOT_OUTPUT};
    private static final int[] SLOTS_BOTH   = {SLOT_INPUT, SLOT_OUTPUT};
    private static final int[] SLOTS_NONE   = {};

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);

    public int smeltTime = 0;

    private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> recipeCheck =
            RecipeManager.createCheck(RecipeType.SMELTING);

    public final SimpleSidedUEContainer energy = new SimpleSidedUEContainer(
            CAPACITY, INPUT_OUTPUT, INPUT_OUTPUT) {
        @Override
        protected void onCommit() { setChanged(); }
    };

    public final SimpleSidedItemContainer itemSides = new SimpleSidedItemContainer();

    public ElectricFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_FURNACE, pos, state);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────
    public static void tick(Level level, BlockPos pos, BlockState state,
                            ElectricFurnaceBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Always run item transfer, even when idle
        ItemTransferTicker.tick(serverLevel, pos, be, be.itemSides);

        Optional<RecipeHolder<SmeltingRecipe>> recipe = getSmeltingRecipe(serverLevel, be);

        if (recipe.isEmpty() || be.energy.getAmount() < ENERGY_PER_SMELT) {
            if (be.smeltTime > 0) {
                be.smeltTime = 0;
                be.setChanged();
            }
            updateActiveState(serverLevel, pos, state, false);
            return;
        }

        ItemStack result = recipe.get().value()
                .assemble(new SingleRecipeInput(be.items.get(SLOT_INPUT)));

        if (!canInsertOutput(be, result)) {
            be.smeltTime = 0;
            updateActiveState(serverLevel, pos, state, false);
            return;
        }

        be.smeltTime++;
        be.setChanged();
        updateActiveState(serverLevel, pos, state, true);

        if (be.smeltTime >= SMELT_TIME_TOTAL) {
            be.smeltTime = 0;
            be.items.get(SLOT_INPUT).shrink(1);
            ItemStack output = be.items.get(SLOT_OUTPUT);
            if (output.isEmpty()) {
                be.items.set(SLOT_OUTPUT, result.copy());
            } else {
                output.grow(result.getCount());
            }
            be.energy.setAmountUnchecked(be.energy.getAmount() - ENERGY_PER_SMELT);
            be.setChanged();
        }
    }

    private static void updateActiveState(Level level, BlockPos pos,
                                          BlockState state, boolean active) {
        if (state.getValue(ElectricFurnaceBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(ElectricFurnaceBlock.ACTIVE, active), 3);
        }
    }

    private static Optional<RecipeHolder<SmeltingRecipe>> getSmeltingRecipe(
            ServerLevel level, ElectricFurnaceBlockEntity be) {
        ItemStack input = be.items.get(SLOT_INPUT);
        if (input.isEmpty()) return Optional.empty();
        return be.recipeCheck.getRecipeFor(new SingleRecipeInput(input), level);
    }

    private static boolean canInsertOutput(ElectricFurnaceBlockEntity be, ItemStack result) {
        if (result.isEmpty()) return false;
        ItemStack current = be.items.get(SLOT_OUTPUT);
        if (current.isEmpty()) return true;
        if (!ItemStack.isSameItemSameComponents(current, result)) return false;
        return current.getCount() + result.getCount() <= current.getMaxStackSize();
    }

    // ── WorldlyContainer ──────────────────────────────────────────────────────
    @Override
    public int[] getSlotsForFace(Direction direction) {
        ItemSideMode mode = itemSides.getSideMode(direction);
        return switch (mode) {
            case INPUT  -> SLOTS_INPUT;
            case OUTPUT -> SLOTS_OUTPUT;
            case BOTH   -> SLOTS_BOTH;
            case NONE   -> SLOTS_NONE;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        if (direction == null) return true;
        ItemSideMode mode = itemSides.getSideMode(direction);
        if (!mode.allowsInsertion()) return false;
        // Only allow insertion into the input slot
        return slot == SLOT_INPUT;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        ItemSideMode mode = itemSides.getSideMode(direction);
        if (!mode.allowsExtraction()) return false;
        // Only allow extraction from the output slot
        return slot == SLOT_OUTPUT;
    }

    // ── Container ─────────────────────────────────────────────────────────────
    @Override public int getContainerSize()              { return items.size(); }
    @Override public boolean isEmpty()                   { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public void clearContent() { items.replaceAll(i -> ItemStack.EMPTY); setChanged(); }
    @Override public ItemStack getItem(int slot)         { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack stack = items.get(slot).split(count);
        setChanged();
        return stack;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        return stack;
    }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); setChanged(); }
    @Override public boolean stillValid(Player player)   { return Container.stillValidBlockEntity(this, player); }
    @Override public void setChanged()                   { super.setChanged(); }

    public NonNullList<ItemStack> getItems() { return items; }

    // ── Energy / Item sides ───────────────────────────────────────────────────
    @Override public SimpleSidedUEContainer getEnergy()        { return energy; }
    @Override public SimpleSidedItemContainer getItemSides()   { return itemSides; }

    public long getStoredEnergy() { return energy.getAmount(); }
    public long getMaxEnergy()    { return energy.getCapacity(); }

    // ── Menu provider ─────────────────────────────────────────────────────────
    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) { return worldPosition; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.totality.electric_furnace");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            syncSideModes(serverPlayer, worldPosition);
            syncItemSideModes(serverPlayer, worldPosition);
        }
        return ElectricFurnaceMenu.create(syncId, inventory, this);
    }

    // ── Serialization ─────────────────────────────────────────────────────────
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("SmeltTime", smeltTime);
        energy.saveToOutput(output);
        itemSides.saveToOutput(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        smeltTime = input.getIntOr("SmeltTime", 0);
        energy.loadFromInput(input);
        itemSides.loadFromInput(input);
    }

    // ── Client sync ───────────────────────────────────────────────────────────
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (var reporter = new ProblemReporter.ScopedCollector(problemPath(), Totality.LOGGER)) {
            var output = TagValueOutput.createWithContext(reporter, registries);
            energy.saveToOutput(output);
            return output.buildResult();
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}