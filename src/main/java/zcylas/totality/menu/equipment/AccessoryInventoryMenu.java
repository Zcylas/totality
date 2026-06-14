package zcylas.totality.menu.equipment;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.equipment.EquipmentComponents;
import zcylas.totality.mixin.CraftingMenuAccessor;
import zcylas.totality.api.equipment.PlayerEquipmentComponent;
import zcylas.totality.api.equipment.TotalityAccessorySlot;
import zcylas.totality.api.equipment.TotalityRingItem;

/**
 * Inventory menu that mirrors vanilla InventoryMenu's slot layout and adds
 * two ring slots (indices 46–47) backed by PlayerEquipmentComponent.
 *
 * Slot layout:
 *   0       — crafting result
 *   1–4     — crafting grid (2×2)
 *   5–8     — armor (HEAD, CHEST, LEGS, FEET)
 *   9–35    — main inventory (3 rows × 9)
 *   36–44   — hotbar (9)
 *   45      — offhand
 *   46      — ring slot 1 (component index 1)
 *   47      — ring slot 2 (component index 2)
 *   48      — pouch slot  (component index 4)
 */
public class AccessoryInventoryMenu extends AbstractContainerMenu {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final Identifier[] ARMOR_ICONS = {
            InventoryMenu.EMPTY_ARMOR_SLOT_HELMET,
            InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS
    };

    public static final MenuType<AccessoryInventoryMenu> TYPE = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "accessory_inventory"),
            new MenuType<>(AccessoryInventoryMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS)
    );

    // Ring slot positions (relative to leftPos / topPos in screen)
    // Panel is 26×26 (Ohmega slot_panel.png); item area is 16×16 centered inside (+5 each side)
    public static final int RING_PANEL_X = 178; // 2px gap from inv right edge (176)
    public static final int RING_PANEL_Y = 20;  // panel top, relative to topPos
    public static final int RING_SLOT_X  = 183; // RING_PANEL_X + 4(border) + 1(item offset)
    public static final int RING_1_Y     = 25;  // RING_PANEL_Y + 4(border) + 1(item offset)
    public static final int RING_2_Y     = 43;  // RING_1_Y + 18 — adjacent, no gap
    public static final int POUCH_Y      = 61;  // RING_2_Y + 18 — adjacent, no gap

    private final Player player;
    private final CraftingContainer craftMatrix = new TransientCraftingContainer(this, 2, 2);
    private final ResultContainer craftResult   = new ResultContainer();

    // ── Client constructor (called by MenuType factory) ───────────────────────

    public AccessoryInventoryMenu(int syncId, Inventory playerInventory) {
        super(TYPE, syncId);
        this.player = playerInventory.player;

        // ── Crafting result ───────────────────────────────────────────────────
        addSlot(new ResultSlot(player, craftMatrix, craftResult, 0, 154, 28));

        // ── Crafting grid ─────────────────────────────────────────────────────
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                addSlot(new Slot(craftMatrix, col + row * 2, 98 + col * 18, 18 + row * 18));
            }
        }

        // ── Armor slots ───────────────────────────────────────────────────────
        // ARMOR_SLOTS and ARMOR_ICONS are both ordered HEAD→FEET, so use loop index directly.
        // equipSlot.getIndex() returns armor-type index (FEET=0…HEAD=3) — opposite of visual order.
        for (int i = 0; i < 4; i++) {
            final EquipmentSlot equipSlot = ARMOR_SLOTS[i];
            final Identifier armorIcon = ARMOR_ICONS[i];
            addSlot(new Slot(playerInventory, 39 - i, 8, 8 + i * 18) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return player.isEquippableInSlot(stack, equipSlot);
                }
                @Override public boolean mayPickup(Player p) {
                    ItemStack s = getItem();
                    return (s.isEmpty() || p.isCreative()
                            || !EnchantmentHelper.has(s, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE))
                            && super.mayPickup(p);
                }
                @Override public Identifier getNoItemIcon() { return armorIcon; }
            });
        }

        // ── Main inventory (3 rows × 9) ───────────────────────────────────────
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // ── Hotbar ────────────────────────────────────────────────────────────
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        // ── Offhand ───────────────────────────────────────────────────────────
        addSlot(new Slot(playerInventory, 40, 77, 62) {
            @Override public Identifier getNoItemIcon() { return InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD; }
        });

        // ── Ring slots ────────────────────────────────────────────────────────
        Container ringContainer;
        if (player.level() instanceof ServerLevel) {
            ringContainer = EquipmentComponents.get((ServerPlayer) player);
        } else {
            ringContainer = new SimpleContainer(PlayerEquipmentComponent.SLOT_COUNT);
        }
        Identifier ringIcon  = Identifier.fromNamespaceAndPath("totality", "container/slot/ring");
        Identifier pouchIcon = Identifier.fromNamespaceAndPath("totality", "container/slot/pouch");
        addSlot(new TotalityAccessorySlot(ringContainer, player,
                PlayerEquipmentComponent.IDX_RING_1, RING_SLOT_X, RING_1_Y, ringIcon));
        addSlot(new TotalityAccessorySlot(ringContainer, player,
                PlayerEquipmentComponent.IDX_RING_2, RING_SLOT_X, RING_2_Y, ringIcon));
        addSlot(new TotalityAccessorySlot(ringContainer, player,
                PlayerEquipmentComponent.IDX_POUCH, RING_SLOT_X, POUCH_Y, pouchIcon,
                stack -> true));
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public void slotsChanged(Container container) {
        if (player.level() instanceof ServerLevel level) {
            CraftingMenuAccessor.totality$slotChangedCraftingGrid(
                    this, level, player, craftMatrix, craftResult, null);
        }
        super.slotsChanged(container);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        craftResult.clearContent();
        if (!player.level().isClientSide()) {
            clearContainer(player, craftMatrix);
        }
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != craftResult && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack    = slot.getItem();
        ItemStack original = stack.copy();

        if (index == 0) {
            // Crafting result → inventory
            if (!moveItemStackTo(stack, 9, 45, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, original);
        } else if (index < 5) {
            // Crafting grid → inventory
            if (!moveItemStackTo(stack, 9, 45, false)) return ItemStack.EMPTY;
        } else if (index < 9) {
            // Armor → inventory
            if (!moveItemStackTo(stack, 9, 45, false)) return ItemStack.EMPTY;
        } else if (index == 45) {
            // Offhand → inventory
            if (!moveItemStackTo(stack, 9, 45, false)) return ItemStack.EMPTY;
        } else if (index >= 46) {
            // Ring / pouch slot → inventory
            if (!moveItemStackTo(stack, 9, 45, false)) return ItemStack.EMPTY;
        } else {
            // Inventory (9–44): try to equip ring, armor, offhand, or swap halves
            if (stack.getItem() instanceof TotalityRingItem) {
                if (!moveItemStackTo(stack, 46, 48, false)) {
                    // Ring slots full — swap between inv halves
                    if (!swapInventoryHalves(stack, index)) return ItemStack.EMPTY;
                }
            } else {
                EquipmentSlot equipSlot = player.getEquipmentSlotForItem(stack);
                if (equipSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                    int targetSlot = 8 - equipSlot.getIndex();
                    if (!slots.get(targetSlot).hasItem() && player.isEquippableInSlot(stack, equipSlot)) {
                        if (!moveItemStackTo(stack, targetSlot, targetSlot + 1, false)) return ItemStack.EMPTY;
                    } else if (!swapInventoryHalves(stack, index)) return ItemStack.EMPTY;
                } else if (equipSlot == EquipmentSlot.OFFHAND && !slots.get(45).hasItem()) {
                    if (!moveItemStackTo(stack, 45, 46, false)) return ItemStack.EMPTY;
                } else if (!swapInventoryHalves(stack, index)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY, original);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        if (index == 0) player.drop(stack, false);
        return original;
    }

    private boolean swapInventoryHalves(ItemStack stack, int fromIndex) {
        if (fromIndex >= 36 && fromIndex < 45) {
            return moveItemStackTo(stack, 9, 36, false);
        }
        return moveItemStackTo(stack, 36, 45, false);
    }

}
