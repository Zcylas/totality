package zcylas.totality.api.equipment;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;
import zcylas.totality.api.item.Soulbound;
import zcylas.totality.api.item.TotalityArmorItem;
import zcylas.totality.api.item.TotalityItemComponents;

/**
 * Stores items equipped in Totality's accessory slots (Belt, Ring 1, Ring 2, Amulet, Pouch, Phone, Back).
 * Implements Container so TotalityAccessorySlot can back directly into it.
 *
 * Indices 0–3 map to Equipment Tab slots 6–9:
 *   0 = Belt, 1 = Ring 1, 2 = Ring 2, 3 = Amulet
 * 6 = Back (capacity 1; only {@link TotalityBackItem}s). The Back slot is independent of the vanilla chest armor
 * slot and grants nothing by itself. Its contents are mirrored, render-only, to {@link BackEquipmentAppearance}
 * so other players' clients can draw them; this component remains the only authority.
 */
public class PlayerEquipmentComponent implements SyncedComponent, CopyableComponent<PlayerEquipmentComponent>, Container {

    public static final int SLOT_COUNT = 7;
    public static final int IDX_BELT   = 0;
    public static final int IDX_RING_1 = 1;
    public static final int IDX_RING_2 = 2;
    public static final int IDX_AMULET = 3;
    public static final int IDX_POUCH  = 4;
    public static final int IDX_PHONE  = 5;
    public static final int IDX_BACK   = 6;

    private final ItemStack[] stacks = new ItemStack[SLOT_COUNT];
    private final ServerPlayer player;

    public PlayerEquipmentComponent(ServerPlayer player) {
        this.player = player;
        for (int i = 0; i < SLOT_COUNT; i++) stacks[i] = ItemStack.EMPTY;
    }

    /**
     * Only the owner receives this component (its payload carries no player id, so a watcher's client would apply
     * another player's slots as its own). What others need to see is mirrored by {@link BackEquipmentAppearance}.
     */
    @Override
    public boolean shouldSyncWith(ServerPlayer recipient) {
        return recipient == player;
    }

    /** Keeps the render-only Back mirror in step with this component (server side only). */
    private void mirrorBack() {
        if (player != null) BackEquipmentAppearance.mirror(player, stacks[IDX_BACK]);
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
            if (slot == IDX_BACK) mirrorBack();
            return stack;
        }
        ItemStack removed = stack.split(amount);
        if (stack.isEmpty()) stacks[slot] = ItemStack.EMPTY;
        if (slot == IDX_BACK) mirrorBack();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = stacks[slot];
        stacks[slot] = ItemStack.EMPTY;
        if (slot == IDX_BACK) mirrorBack();
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        stacks[slot] = stack;
        if (!stack.isEmpty() && stack.getCount() > 1) stack.setCount(1);
        if (player != null && slot == IDX_PHONE && stack.getItem() instanceof zcylas.totality.item.energy.PhoneItem) {
            zcylas.totality.api.quest.QuestManager.onPhoneEquipped(player);
        }
        if (slot == IDX_BACK) mirrorBack();
    }

    /**
     * Actual death (called from {@code Player#dropEquipment}, at the same point and under the same keepInventory rule
     * as the vanilla inventory drop): every equipped item drops like an inventory item, whatever its Attunement,
     * unless it is {@link Soulbound}, which stays in its slot and reaches the respawned player through the ordinary
     * respawn copy. Curse of Vanishing destroys an item, as in the vanilla inventory (and wins over Soulbound there too).
     */
    public void dropOnDeath() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = stacks[i];
            boolean vanishes = EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP);
            if (stack.isEmpty() || (!vanishes && Soulbound.isSoulbound(stack))) continue;
            stacks[i] = ItemStack.EMPTY;
            if (stack.getItem() instanceof TotalityArmorItem armor) armor.onUnequip(stack, player);
            if (!vanishes) player.drop(stack, true, false);
        }
        mirrorBack();
        sync();
    }

    /** Server-side validation for the Back slot (the menu slot applies the same rule); other slots are unchanged. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot != IDX_BACK || TotalityBackItem.isBackEquipment(stack);
    }

    @Override public void setChanged() {}

    @Override public boolean stillValid(Player player) { return true; }

    @Override
    public void clearContent() {
        for (int i = 0; i < SLOT_COUNT; i++) stacks[i] = ItemStack.EMPTY;
        mirrorBack();
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
        mirrorBack();
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
        mirrorBack();
    }
}