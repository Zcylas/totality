package zcylas.totality.screen.inventory;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import zcylas.totality.api.economy.currency.CurrencyHelper;
import zcylas.totality.networking.currency.ClientWalletManager;
import zcylas.totality.networking.inventory.InventoryActionHandler;
import zcylas.totality.networking.inventory.InventoryDropPayload;
import zcylas.totality.networking.inventory.InventoryEquipPayload;
import zcylas.totality.networking.inventory.InventoryUsePayload;
import zcylas.totality.screen.menu.MainMenuScreen;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_Q;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_R;
import static zcylas.totality.screen.inventory.InventoryColors.*;
import static zcylas.totality.screen.inventory.InventoryLayout.*;
import static zcylas.totality.screen.inventory.InventoryEquipHelper.*;

public class TotalityInventoryScreen extends Screen {

    // ── Categories ────────────────────────────────────────────────────────────
    public enum Category { ALL, WEAPONS, ARMOR, POTIONS, FOOD, TOOLS, SPECIAL, MISC }
    static final Category[] CATEGORIES = Category.values();
    static final String[]   CAT_LABELS = { "✦", "⚔", "⛨", "⚗", "✿", "⚒", "✧", "?" };

    // ── Tabs ──────────────────────────────────────────────────────────────────
    private enum MainTab { INVENTORY, EQUIPMENT }
    private MainTab  mainTab  = MainTab.INVENTORY;
    private Category category = Category.ALL;

    // ── Item list state ───────────────────────────────────────────────────────
    private int selectedItem = -1;
    private int hoveredRow   = -1;
    private int scrollOffset = 0;

    private final List<ItemEntry> filteredItems = new ArrayList<>();

    private boolean refreshPending = false;
    private int     refreshDelay   = 0;

    // ── Layout ────────────────────────────────────────────────────────────────
    private InventoryLayout layout;

    // ── Sub-components ────────────────────────────────────────────────────────
    private final InventorySortManager  sort     = new InventorySortManager();
    private final InventoryItemDetail   detail   = new InventoryItemDetail();
    private final EquipmentTabRenderer  equipTab = new EquipmentTabRenderer();

    // ── Fade ──────────────────────────────────────────────────────────────────
    private float    alpha         = 0f;
    private boolean  fadingOut     = false;
    private Runnable onFadeOutDone = null;

    public TotalityInventoryScreen() { super(Component.literal("Inventory")); }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        alpha = 0f;
        fadingOut = false;
        layout = new InventoryLayout(width, height);
        rebuildItemList();
    }

    @Override
    public void tick() {
        super.tick();
        if (refreshPending) {
            refreshDelay--;
            if (refreshDelay <= 0) {
                refreshItemStacks();
                refreshPending = false;
            }
        }
    }

    private void scheduleRefresh() {
        refreshPending = true;
        refreshDelay   = 3;
    }

    // ── Item list management ──────────────────────────────────────────────────

    /**
     * Full rebuild — call on init and category change.
     * Establishes stable insertion order, then applies current sort.
     */
    private void rebuildItemList() {
        filteredItems.clear();
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && matchesCategory(stack))
                filteredItems.add(new ItemEntry(stack, InventoryActionHandler.SLOT_ARMOR, slot));
        }

        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (!offhand.isEmpty() && matchesCategory(offhand))
            filteredItems.add(new ItemEntry(offhand, InventoryActionHandler.SLOT_OFFHAND));

        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && matchesCategory(stack))
                filteredItems.add(new ItemEntry(stack, i));
        }

        clampSelection();
        sort.apply(filteredItems);
    }

    /**
     * Refresh-in-place after equip/unequip — updates slot references without reordering.
     */
    private void refreshItemStacks() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        for (int i = 0; i < filteredItems.size(); i++) {
            ItemEntry entry   = filteredItems.get(i);
            ItemStack current = entry.stack;
            if (current.isEmpty()) continue;

            // 1. Check recorded slot first
            ItemStack atSlot = ItemStack.EMPTY;
            if (entry.slot == InventoryActionHandler.SLOT_ARMOR && entry.armorSlot != null) {
                atSlot = player.getItemBySlot(entry.armorSlot);
            } else if (entry.slot == InventoryActionHandler.SLOT_OFFHAND) {
                atSlot = player.getItemBySlot(EquipmentSlot.OFFHAND);
            } else if (entry.slot >= 0 && entry.slot < 36) {
                atSlot = player.getInventory().getItem(entry.slot);
            }

            if (!atSlot.isEmpty() && ItemStack.isSameItem(atSlot, current)) {
                filteredItems.set(i, new ItemEntry(atSlot, entry.slot, entry.armorSlot));
                continue;
            }

            // 2. Item moved — search all locations
            ItemStack     found      = ItemStack.EMPTY;
            int           foundSlot  = entry.slot;
            EquipmentSlot foundArmor = entry.armorSlot;

            Equippable eq = current.get(DataComponents.EQUIPPABLE);
            if (eq != null && isArmorEquipSlot(eq.slot())) {
                ItemStack inArmor = player.getItemBySlot(eq.slot());
                if (!inArmor.isEmpty() && ItemStack.isSameItem(inArmor, current)) {
                    found = inArmor; foundSlot = InventoryActionHandler.SLOT_ARMOR; foundArmor = eq.slot();
                }
            }
            if (found.isEmpty()) {
                ItemStack inOff = player.getItemBySlot(EquipmentSlot.OFFHAND);
                if (!inOff.isEmpty() && ItemStack.isSameItem(inOff, current)) {
                    found = inOff; foundSlot = InventoryActionHandler.SLOT_OFFHAND; foundArmor = null;
                }
            }
            if (found.isEmpty()) {
                for (int s = 0; s < 36; s++) {
                    ItemStack inInv = player.getInventory().getItem(s);
                    if (!inInv.isEmpty() && ItemStack.isSameItem(inInv, current)) {
                        found = inInv; foundSlot = s; foundArmor = null; break;
                    }
                }
            }

            filteredItems.set(i, new ItemEntry(
                    found.isEmpty() ? ItemStack.EMPTY : found, foundSlot, foundArmor));
        }

        filteredItems.removeIf(e -> e.stack.isEmpty());
        sort.apply(filteredItems);
        clampSelection();
    }

    private boolean matchesCategory(ItemStack stack) {
        return switch (category) {
            case ALL     -> true;
            case WEAPONS -> isWeapon(stack);
            case ARMOR   -> isArmor(stack);
            case POTIONS -> isPotion(stack);
            case FOOD    -> isFood(stack);
            case TOOLS   -> isTool(stack) && !isSpecial(stack);
            case SPECIAL -> isSpecial(stack);
            case MISC    -> isMisc(stack);
        };
    }

    private void clampSelection() {
        if (selectedItem >= filteredItems.size()) selectedItem = -1;
        scrollOffset = Math.max(0, Math.min(scrollOffset,
                Math.max(0, filteredItems.size() - layout.visibleRows)));
    }

    private void setCategory(Category cat) {
        category = cat; scrollOffset = 0; selectedItem = -1;
        rebuildItemList();
    }

    // ── Sort ──────────────────────────────────────────────────────────────────

    private void cycleSort(InventorySortManager.SortColumn col) {
        sort.cycleColumn(col);
        if (sort.isDefault()) rebuildItemList(); // restore insertion order
        else sort.apply(filteredItems);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, withAlpha(BG, (int)(alpha * 0xEE)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);

        if (fadingOut) {
            alpha = Math.max(0f, alpha - 0.12f);
            if (alpha <= 0f && onFadeOutDone != null) { onFadeOutDone.run(); return; }
        } else {
            alpha = Math.min(1f, alpha + 0.12f);
        }
        int ba = (int)(alpha * 255);

        layout = new InventoryLayout(width, height);

        drawMainTabs(g, mx, my, ba);

        if (mainTab == MainTab.INVENTORY) {
            drawCategoryTabs(g, mx, my, ba);
            Player player = Minecraft.getInstance().player;
            hoveredRow = InventoryItemList.draw(
                    g, font, layout, sort, filteredItems,
                    scrollOffset, selectedItem, mx, my, ba, player);

            int displayIdx = selectedItem >= 0 ? selectedItem
                    : hoveredRow >= 0 ? hoveredRow : -1;
            detail.draw(g, font, layout, filteredItems, displayIdx, ba);
        } else {
            Player player = Minecraft.getInstance().player;
            if (player != null)
                equipTab.draw(g, font, width, height, mx, my, ba, player);
        }

        drawBottomBar(g, mx, my, ba);
    }

    // ── Tab rendering (stays in screen — it's structural, not content) ────────

    private void drawMainTabs(GuiGraphicsExtractor g, int mx, int my, int ba) {
        String[] labels = { "INVENTORY", "EQUIPMENT" };
        int tabW = 90, tx = PADDING;
        for (int i = 0; i < labels.length; i++) {
            boolean active = mainTab == MainTab.values()[i];
            int bg  = withAlpha(active ? TAB_ACTIVE   : TAB_INACTIVE, ba);
            int tc  = withAlpha(active ? VALUE         : LABEL,        ba);
            int brc = withAlpha(active ? VALUE         : BORDER,       ba);
            g.fill(tx, PADDING, tx+tabW, PADDING+TAB_H, bg);
            g.fill(tx,        PADDING,         tx+tabW, PADDING+1,        brc);
            g.fill(tx,        PADDING+TAB_H-1, tx+tabW, PADDING+TAB_H,   brc);
            g.fill(tx,        PADDING,         tx+1,    PADDING+TAB_H,    brc);
            g.fill(tx+tabW-1, PADDING,         tx+tabW, PADDING+TAB_H,   brc);
            g.text(font, Component.literal(labels[i]),
                    tx + tabW/2 - font.width(labels[i])/2,
                    PADDING + (TAB_H - 8)/2, tc, false);
            tx += tabW + 4;
        }
    }

    private void drawCategoryTabs(GuiGraphicsExtractor g, int mx, int my, int ba) {
        int catY = PADDING + TAB_H + 4;
        int n    = CATEGORIES.length;
        int tabW = layout.listW / n;
        int tx   = layout.listX;
        for (int i = 0; i < n; i++) {
            boolean active = category == CATEGORIES[i];
            int bg  = withAlpha(active ? TAB_ACTIVE   : TAB_INACTIVE, ba);
            int tc  = withAlpha(active ? VALUE         : LABEL,        ba);
            int brc = withAlpha(active ? VALUE         : BORDER,       ba);
            int x0 = tx, x1 = tx + tabW - 1;
            g.fill(x0,   catY,              x1, catY+CAT_TAB_H,   bg);
            g.fill(x0,   catY,              x1, catY+1,            brc);
            g.fill(x0,   catY+CAT_TAB_H-1, x1, catY+CAT_TAB_H,   brc);
            g.fill(x0,   catY,              x0+1, catY+CAT_TAB_H, brc);
            g.fill(x1-1, catY,              x1,   catY+CAT_TAB_H, brc);
            String label  = CAT_LABELS[i];
            int    lw     = font.width(label);
            int    labelX = Math.max(x0+2, Math.min(x0+(tabW-1)/2-lw/2, x1-lw-2));
            g.text(font, Component.literal(label), labelX, catY+(CAT_TAB_H-8)/2, tc, false);
            tx += tabW;
        }
    }

    private void drawBottomBar(GuiGraphicsExtractor g, int mx, int my, int ba) {
        int barY = height - BOTTOM_H;
        g.fill(0, barY, width, height, withAlpha(BOTTOM, ba));
        g.fill(0, barY, width, barY+1, withAlpha(BORDER, ba));
        int cy = barY + (BOTTOM_H - 8) / 2;

        Player player = Minecraft.getInstance().player;
        if (player != null) {
            int uniqueItems = filteredItems.size();
            int totalItems  = filteredItems.stream().mapToInt(e -> e.stack.getCount()).sum();
            g.text(font, Component.literal("Items: " + uniqueItems + " (" + totalItems + ")"),
                    PADDING, cy, withAlpha(LABEL, ba), false);
        }

        long walletValue  = ClientWalletManager.getValue();
        long goldCount = 0, silverCount = 0, copperCount = 0;
        for (var cc : CurrencyHelper.breakdown(walletValue)) {
            switch (cc.denomination()) {
                case GOLD   -> goldCount   = cc.count();
                case SILVER -> silverCount = cc.count();
                case COPPER -> copperCount = cc.count();
            }
        }
        String gs = ": " + goldCount, ss = ": " + silverCount, cs = ": " + copperCount;
        int sqGap  = 3, entryGap = 14;
        int textW  = Math.max(font.width(gs), Math.max(font.width(ss), font.width(cs)));
        int entryW = COIN_SQ + sqGap + textW;
        int totalW = entryW * 3 + entryGap * 2;
        int cx     = width / 2 - totalW / 2;
        int sqY    = cy + (8 - COIN_SQ) / 2;
        drawCoin(g, cx, cy, sqY, GOLD,   gs, ba); cx += entryW + entryGap;
        drawCoin(g, cx, cy, sqY, SILVER, ss, ba); cx += entryW + entryGap;
        drawCoin(g, cx, cy, sqY, COPPER, cs, ba);

        String tabHint = mainTab == MainTab.INVENTORY ? "[TAB] Equipment" : "[TAB] Inventory";
        g.text(font, Component.literal(tabHint),
                width - PADDING - font.width(tabHint), cy,
                withAlpha(LABEL, ba), false);
    }

    private void drawCoin(GuiGraphicsExtractor g, int x, int cy, int sqY,
                          int color, String str, int ba) {
        g.fill(x, sqY, x+COIN_SQ, sqY+COIN_SQ, withAlpha(color, ba));
        g.text(font, Component.literal(str), x+COIN_SQ+3, cy, withAlpha(color, ba), false);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mainTab == MainTab.INVENTORY) {
            int max  = Math.max(0, filteredItems.size() - layout.visibleRows);
            scrollOffset = (int) Math.max(0, Math.min(max, scrollOffset - sy));
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        int mx = (int) mouse.x(), my = (int) mouse.y();
        int button = mouse.button();

        // Main tab buttons
        int tabW = 90, tx = PADDING;
        for (int i = 0; i < 2; i++) {
            if (inBounds(mx, my, tx, PADDING, tabW, TAB_H)) {
                mainTab = MainTab.values()[i]; playClick(); return true;
            }
            tx += tabW + 4;
        }

        if (mainTab == MainTab.INVENTORY) {
            // Category tabs
            int catY    = PADDING + TAB_H + 4;
            int catTabW = layout.listW / CATEGORIES.length;
            int ctx     = layout.listX;
            for (int i = 0; i < CATEGORIES.length; i++) {
                if (inBounds(mx, my, ctx, catY, catTabW-1, CAT_TAB_H)) {
                    setCategory(CATEGORIES[i]); playClick(); return true;
                }
                ctx += catTabW;
            }

            // Sort header clicks
            InventorySortManager.SortColumn hit = InventoryItemList.hitTestHeaders(font, layout, mx, my);
            if (hit != null) { cycleSort(hit); playClick(); return true; }

            // Item rows
            int startY = layout.listY + ROW_H + 1;
            for (int i = 0; i < layout.visibleRows; i++) {
                int idx  = scrollOffset + i;
                if (idx >= filteredItems.size()) break;
                int rowY = startY + i * ROW_H;
                if (rowY >= layout.listY + layout.listH - 1) break;
                if (inBounds(mx, my, layout.listX+1, rowY, layout.listW-2, ROW_H)) {
                    if (selectedItem != idx) { selectedItem = idx; playClick(); }
                    else {
                        if (button == 1) performActionRight(idx);
                        else             performAction(idx);
                    }
                    return true;
                }
            }
            if (inBounds(mx, my, layout.listX, layout.listY, layout.listW, layout.listH)) {
                selectedItem = -1; return true;
            }
        } else {
            Player player = Minecraft.getInstance().player;
            if (player != null && equipTab.isUnequipClick(mx, my, width, height, player)) {
                performUnequip(equipTab.getSelectedSlot());
            } else {
                equipTab.mouseClicked(mx, my, width, height);
                playClick();
            }
            return true;
        }

        return super.mouseClicked(mouse, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();

        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            mainTab = mainTab == MainTab.INVENTORY ? MainTab.EQUIPMENT : MainTab.INVENTORY;
            playClick(); return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            fadeOutTo(() -> Minecraft.getInstance().setScreen(new MainMenuScreen()));
            return true;
        }

        if (mainTab == MainTab.INVENTORY) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_A) {
                setCategory(CATEGORIES[(category.ordinal()-1+CATEGORIES.length) % CATEGORIES.length]);
                playClick(); return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_D) {
                setCategory(CATEGORIES[(category.ordinal()+1) % CATEGORIES.length]);
                playClick(); return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_W) {
                selectedItem = selectedItem <= 0 ? 0 : selectedItem - 1;
                if (selectedItem < scrollOffset) scrollOffset = selectedItem;
                playClick(); return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
                selectedItem = Math.min(filteredItems.size()-1,
                        selectedItem < 0 ? 0 : selectedItem + 1);
                if (selectedItem >= scrollOffset + layout.visibleRows)
                    scrollOffset = selectedItem - layout.visibleRows + 1;
                playClick(); return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_E && selectedItem >= 0) {
                performAction(selectedItem); return true;
            }
            if (key == GLFW_KEY_Q && selectedItem >= 0) { performDrop(selectedItem);    return true; }
            if (key == GLFW_KEY_R && selectedItem >= 0) { performDropAll(selectedItem); return true; }
        } else {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_W) {
                int sel = equipTab.getSelectedSlot();
                // set via mouseClicked equivalent — just manipulate directly
                if (sel > 0) equipTab.mouseClicked(
                        PADDING + ((sel-1)%2)*(((width-PADDING*3)/2)+PADDING),
                        PADDING + TAB_H + 10 + ((sel-1)/2)*30,
                        width, height);
                playClick(); return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
                int sel = equipTab.getSelectedSlot();
                int next = Math.min(EquipmentTabRenderer.SLOT_LABELS.length-1, sel < 0 ? 0 : sel+1);
                equipTab.mouseClicked(
                        PADDING + (next%2)*(((width-PADDING*3)/2)+PADDING),
                        PADDING + TAB_H + 10 + (next/2)*30,
                        width, height);
                playClick(); return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_E && equipTab.getSelectedSlot() >= 0) {
                performUnequip(equipTab.getSelectedSlot()); return true;
            }
        }
        return super.keyPressed(event);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void performAction(int idx) {
        if (idx < 0 || idx >= filteredItems.size()) return;
        ItemEntry entry  = filteredItems.get(idx);
        ItemStack stack  = entry.stack;
        Player    player = Minecraft.getInstance().player;
        if (player == null) return;

        if (entry.slot == InventoryActionHandler.SLOT_OFFHAND) {
            ClientPlayNetworking.send(new InventoryEquipPayload(
                    InventoryActionHandler.SLOT_OFFHAND, EquipmentSlot.OFFHAND, true));
            playClick(); scheduleRefresh(); return;
        }
        if (entry.slot == InventoryActionHandler.SLOT_ARMOR && entry.armorSlot != null) {
            ClientPlayNetworking.send(new InventoryEquipPayload(
                    InventoryActionHandler.SLOT_ARMOR, entry.armorSlot, true));
            playClick(); scheduleRefresh(); return;
        }

        if (isArmor(stack)) {
            Equippable eq = stack.get(DataComponents.EQUIPPABLE);
            if (eq == null) return;
            if (isInArmorSlot(player, stack))
                ClientPlayNetworking.send(new InventoryEquipPayload(InventoryActionHandler.SLOT_ARMOR, eq.slot(), true));
            else
                ClientPlayNetworking.send(new InventoryEquipPayload(entry.slot, eq.slot(), false));

        } else if (isSpecial(stack) || isHandTool(stack)) {
            int hs = findInHotbar(player, stack);
            ClientPlayNetworking.send(hs >= 0
                    ? new InventoryEquipPayload(hs, EquipmentSlot.MAINHAND, true)
                    : new InventoryEquipPayload(entry.slot, EquipmentSlot.MAINHAND, false));

        } else if (isTwoHandedWeapon(stack)) {
            int hs = findInHotbar(player, stack);
            ClientPlayNetworking.send(hs >= 0
                    ? new InventoryEquipPayload(hs, EquipmentSlot.MAINHAND, true)
                    : new InventoryEquipPayload(entry.slot, EquipmentSlot.MAINHAND, false));

        } else if (isOneHandedWeapon(stack) || isThrownWeapon(stack)) {
            boolean inMain = isInMainhand(player, stack);
            boolean inOff  = isInOffhand(player, stack);
            int     hs     = findInHotbar(player, stack);

            if (inMain && inOff) {
                ClientPlayNetworking.send(new InventoryEquipPayload(hs >= 0 ? hs : entry.slot, EquipmentSlot.MAINHAND, true));
                ClientPlayNetworking.send(new InventoryEquipPayload(InventoryActionHandler.SLOT_OFFHAND, EquipmentSlot.OFFHAND, true));
            } else if (inMain) {
                ClientPlayNetworking.send(new InventoryEquipPayload(hs >= 0 ? hs : entry.slot, EquipmentSlot.OFFHAND, false));
            } else if (inOff) {
                ClientPlayNetworking.send(new InventoryEquipPayload(InventoryActionHandler.SLOT_OFFHAND, EquipmentSlot.OFFHAND, true));
            } else {
                ClientPlayNetworking.send(new InventoryEquipPayload(entry.slot, EquipmentSlot.MAINHAND, false));
            }

        } else if (isPotion(stack) || isFood(stack)) {
            ClientPlayNetworking.send(new InventoryUsePayload(entry.slot));

        } else if (isWeapon(stack)) {
            ClientPlayNetworking.send(new InventoryEquipPayload(entry.slot, EquipmentSlot.MAINHAND, false));
        }

        playClick();
        scheduleRefresh();
    }

    private void performActionRight(int idx) {
        if (idx < 0 || idx >= filteredItems.size()) return;
        ItemEntry entry = filteredItems.get(idx);
        ItemStack stack = entry.stack;
        if ((isOneHandedWeapon(stack) || isThrownWeapon(stack)) && !isTwoHandedWeapon(stack)) {
            ClientPlayNetworking.send(new InventoryEquipPayload(entry.slot, EquipmentSlot.OFFHAND, false));
            playClick(); scheduleRefresh();
        } else {
            performAction(idx);
        }
    }

    private void performDrop(int idx) {
        if (idx < 0 || idx >= filteredItems.size()) return;
        ItemEntry entry = filteredItems.get(idx);
        if (entry.slot < 0) return;
        ClientPlayNetworking.send(new InventoryDropPayload(entry.slot, false));
        selectedItem = -1; playClick(); scheduleRefresh();
    }

    private void performDropAll(int idx) {
        if (idx < 0 || idx >= filteredItems.size()) return;
        ItemEntry entry = filteredItems.get(idx);
        if (entry.slot < 0) return;
        ClientPlayNetworking.send(new InventoryDropPayload(entry.slot, true));
        selectedItem = -1; playClick(); scheduleRefresh();
    }

    private void performUnequip(int slotIndex) {
        if (slotIndex < 0 || slotIndex > 5) return;
        Player player = Minecraft.getInstance().player;
        EquipmentSlot slot = EquipmentTabRenderer.equipmentSlotFor(slotIndex);
        if (slot == null) return;

        int invSlot = -1;
        if (slot == EquipmentSlot.MAINHAND && player != null) {
            ItemStack mh = player.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!mh.isEmpty()) invSlot = findInHotbar(player, mh);
        }
        ClientPlayNetworking.send(new InventoryEquipPayload(invSlot, slot, true));
        equipTab.clearSelected(); playClick(); scheduleRefresh();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void fadeOutTo(Runnable onDone) { fadingOut = true; onFadeOutDone = onDone; }

    private static boolean inBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x+w && my >= y && my < y+h;
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override public boolean isInGameUi()    { return false; }
    @Override public boolean isPauseScreen() { return false; }
}