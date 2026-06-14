package zcylas.totality.api.equipment;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;
import zcylas.totality.api.item.TotalityItemComponents;

/**
 * Stores items equipped in Totality's accessory slots (Belt, Ring 1, Ring 2, Amulet).
 * Implements Container so TotalityAccessorySlot can back directly into it.
 *
 * Indices 0–3 map to Equipment Tab slots 6–9:
 *   0 = Belt, 1 = Ring 1, 2 = Ring 2, 3 = Amulet
 */
public class PlayerEquipmentComponent implements SyncedComponent, CopyableComponent<PlayerEquipmentComponent>, Container {

    public static final int SLOT_COUNT = 5;
    public static final int IDX_BELT   = 0;
    public static final int IDX_RING_1 = 1;
    public static final int IDX_RING_2 = 2;
    public static final int IDX_AMULET = 3;
    public static final int IDX_POUCH  = 4;

    private final ItemStack[] stacks = new ItemStack[SLOT_COUNT];
    private final ServerPlayer player;

    public PlayerEquipmentComponent(ServerPlayer player) {
        this.player = player;
        for (int i = 0; i < SLOT_COUNT; i++) stacks[i] = ItemStack.EMPTY;
    }

    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            EquipmentComponents.EQUIPMENT.sync(
                    (zcylas.totality.api.core.component.ComponentProvider) player);
        }
    }

    // ── Bonus queries (used by ArmorClass / SavingThrow) ─────────────────────

    public int getAcBonus() {
        int bonus = 0;
        for (int idx : new int[]{ IDX_RING_1, IDX_RING_2 }) {
            ItemStack stack = stacks[idx];
            if (!stack.isEmpty() && stack.getItem() instanceof TotalityRingItem ring && isAttuned(stack)) {
                bonus += ring.getAcBonus();
            }
        }
        return bonus;
    }

    public int getSaveBonus() {
        int bonus = 0;
        for (int idx : new int[]{ IDX_RING_1, IDX_RING_2 }) {
            ItemStack stack = stacks[idx];
            if (!stack.isEmpty() && stack.getItem() instanceof TotalityRingItem ring && isAttuned(stack)) {
                bonus += ring.getSaveBonus();
            }
        }
        return bonus;
    }

    private boolean isAttuned(ItemStack stack) {
        if (player == null) return false;
        java.util.UUID attuned = stack.get(TotalityItemComponents.ATTUNED_TO);
        return attuned != null && attuned.equals(player.getUUID());
    }

    // ── Container interface ───────────────────────────────────────────────────

    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : stacks) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) { return stacks[slot]; }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = stacks[slot];
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (stack.getCount() <= amount) {
            stacks[slot] = ItemStack.EMPTY;
            return stack;
        }
        ItemStack removed = stack.split(amount);
        if (stack.isEmpty()) stacks[slot] = ItemStack.EMPTY;
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = stacks[slot];
        stacks[slot] = ItemStack.EMPTY;
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        stacks[slot] = stack;
        if (!stack.isEmpty() && stack.getCount() > 1) stack.setCount(1);
    }

    @Override public void setChanged() {}

    @Override public boolean stillValid(Player player) { return true; }

    @Override
    public void clearContent() {
        for (int i = 0; i < SLOT_COUNT; i++) stacks[i] = ItemStack.EMPTY;
    }

    /** Returns a snapshot of the stacks for client-side reading. */
    public ItemStack[] getStacks() {
        ItemStack[] copy = new ItemStack[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) copy[i] = stacks[i].copy();
        return copy;
    }

    // ── SyncedComponent ───────────────────────────────────────────────────────

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        for (ItemStack stack : stacks) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
        }
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            stacks[i] = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        }
    }

    // ── CopyableComponent ─────────────────────────────────────────────────────

    @Override
    public void copyFrom(PlayerEquipmentComponent other, HolderLookup.Provider registries) {
        for (int i = 0; i < SLOT_COUNT; i++) stacks[i] = other.stacks[i].copy();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public void writeData(ValueOutput output) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!stacks[i].isEmpty()) {
                output.store("eq_slot_" + i, ItemStack.CODEC, stacks[i]);
            }
        }
    }

    @Override
    public void readData(ValueInput input) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            stacks[i] = input.read("eq_slot_" + i, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        }
    }
}