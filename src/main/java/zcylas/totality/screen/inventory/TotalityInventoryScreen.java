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
import net.minecraft.world.entity.ai.attributes.Attributes;
import zcylas.totality.api.economy.currency.CurrencyHelper;
import zcylas.totality.networking.currency.ClientWalletManager;
import zcylas.totality.networking.inventory.InventoryDropPayload;
import zcylas.totality.networking.inventory.InventoryEquipPayload;
import zcylas.totality.networking.inventory.InventoryUsePayload;
import zcylas.totality.screen.menu.MainMenuScreen;
import zcylas.totality.init.ModTags;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_Q;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_R;

/**
 * Custom SkyUI-style inventory screen.
 *
 * Equip state machine:
 *   Two-handed / Bow / Crossbow: E or click → mainhand toggle only
 *   One-handed / Thrown:         E → mainhand → offhand → unequip
 *                                Left-click → mainhand, Right-click → offhand
 *   Armor:                       E → correct slot (swaps if occupied)
 *   Food / Potion:               E → close screen, use, reopen
 *   Misc:                        Q drop only
 *
 * Indicators in list: [R] mainhand  [L] offhand  [B] both  [A] armor slot
 * Stat comparison: shows delta vs currently equipped item in green/red
 */
public class TotalityInventoryScreen extends Screen {

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int COLOR_BG           = 0xEE000814;
    private static final int COLOR_BORDER       = 0xFF0A5070;
    private static final int COLOR_BORDER_GLOW  = 0x440A8FBF;
    private static final int COLOR_VALUE        = 0xFF00CCFF;
    private static final int COLOR_LABEL        = 0xFF5599BB;
    private static final int COLOR_SEPARATOR    = 0xFF0A3A5A;
    private static final int COLOR_ROW_SEL      = 0xCC002844;
    private static final int COLOR_ROW_HOV      = 0x88001830;
    private static final int COLOR_ROW_EVEN     = 0x11FFFFFF;
    private static final int COLOR_TAB_ACTIVE   = 0xCC002844;
    private static final int COLOR_TAB_INACTIVE = 0x88001020;
    private static final int COLOR_BOTTOM       = 0xDD000C1A;
    private static final int COLOR_EQUIP_SLOT   = 0xBB001020;
    private static final int COLOR_EQUIP_FILLED = 0xBB003355;
    private static final int COLOR_GOLD         = 0xFFFFCC44;
    private static final int COLOR_SILVER       = 0xFFCCCCCC;
    private static final int COLOR_COPPER       = 0xFFCC7722;
    private static final int COLOR_STAT_UP      = 0xFF44FF88; // green delta
    private static final int COLOR_STAT_DOWN    = 0xFFFF4444; // red delta
    private static final int COLOR_EQUIP_BADGE  = 0xFF44DDAA; // [R]/[L]/[B]/[A] badge

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int BOTTOM_H   = 28;
    private static final int TAB_H      = 16;
    private static final int CAT_TAB_H  = 14;
    private static final int ROW_H      = 16;
    private static final int LEFT_W_PCT = 45;
    private static final int PADDING    = 10;
    private static final int ICON_SIZE  = 16;
    private static final int COIN_SQ    = 7;

    // ── Categories ────────────────────────────────────────────────────────────
    private enum Category { ALL, WEAPONS, ARMOR, POTIONS, FOOD, TOOLS, MISC }
    private static final Category[] CATEGORIES = Category.values();
    private static final String[]   CAT_LABELS = { "✦", "⚔", "⛨", "⚗", "✿", "⚒", "?" };

    // ── State ─────────────────────────────────────────────────────────────────
    private enum MainTab { INVENTORY, EQUIPMENT }
    private MainTab mainTab = MainTab.INVENTORY;
    private Category category = Category.ALL;

    private int selectedItem = -1;
    private int hoveredRow   = -1;
    private int scrollOffset = 0;
    private int selectedSlot = -1;
    private int hoveredSlot  = -1;

    private final List<ItemEntry> filteredItems = new ArrayList<>();
    private int listX, listY, listW, listH;
    private int detailX, detailY, detailW, detailH;
    private int visibleRows;

    private float alpha = 0f;
    private boolean fadingOut = false;
    private Runnable onFadeOutDone = null;

    private static boolean isHandTool(ItemStack stack) {
        // Tools that equip to mainhand only (pickaxes, shovels, hoes, axes-as-tools)
        // Must be in ModTags.TOOLS and NOT a weapon tag
        return stack.is(ModTags.TOOLS)
                && !stack.is(ModTags.ONE_HANDED_WEAPONS)
                && !stack.is(ModTags.TWO_HANDED_WEAPONS)
                && !stack.is(ModTags.BOWS)
                && !stack.is(ModTags.CROSSBOWS)
                && !stack.is(ModTags.THROWN_WEAPONS);
    }


    private static class ItemEntry {
        final ItemStack stack;
        final int slot; // inventory slot index
        ItemEntry(ItemStack stack, int slot) { this.stack = stack; this.slot = slot; }
    }

    public TotalityInventoryScreen() {
        super(Component.literal("Inventory"));
    }

    @Override
    protected void init() {
        super.init();
        alpha = 0f;
        fadingOut = false;
        computeLayout();
        rebuildItemList();
    }

    // ── Item classification ───────────────────────────────────────────────────

    private static boolean isTwoHandedWeapon(ItemStack stack) {
        return stack.is(ModTags.TWO_HANDED_WEAPONS)
                || stack.is(ModTags.BOWS)
                || stack.is(ModTags.CROSSBOWS);
    }

    private static boolean isOneHandedWeapon(ItemStack stack) {
        return stack.is(ModTags.ONE_HANDED_WEAPONS);
    }

    private static boolean isThrownWeapon(ItemStack stack) {
        return stack.is(ModTags.THROWN_WEAPONS);
    }

    private static boolean isWeapon(ItemStack stack) {
        if (stack.is(ModTags.BOWS))               return true;
        if (stack.is(ModTags.CROSSBOWS))          return true;
        if (stack.is(ModTags.ONE_HANDED_WEAPONS)) return true;
        if (stack.is(ModTags.TWO_HANDED_WEAPONS)) return true;
        if (stack.is(ModTags.THROWN_WEAPONS))     return true;
        return false; // No DataComponents fallback — use tags explicitly
    }


    private static boolean isArmor(ItemStack stack) {
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        if (eq == null) return false;
        EquipmentSlot s = eq.slot();
        return s == EquipmentSlot.HEAD || s == EquipmentSlot.CHEST
                || s == EquipmentSlot.LEGS || s == EquipmentSlot.FEET;
    }

    private static boolean isPotion(ItemStack stack) {
        // Vanilla potions
        if (stack.has(DataComponents.POTION_CONTENTS)) return true;
        // Custom alchemy potions — add to totality:potions tag in datagen
        if (stack.is(ModTags.POTIONS)) return true;
        return false;
    }


    private static boolean isFood(ItemStack stack) {
        return stack.has(DataComponents.FOOD) && !isPotion(stack);
    }


    private static boolean isTool(ItemStack stack) {
        return stack.is(ModTags.TOOLS);
    }

    private static boolean isMisc(ItemStack stack) {
        return !isWeapon(stack) && !isArmor(stack) && !isPotion(stack)
                && !isFood(stack) && !isTool(stack);
    }

    // ── Equip state helpers ───────────────────────────────────────────────────
    // These check the actual player equipment slots to determine badge text.

    private static boolean isInMainhand(Player player, ItemStack stack) {
        ItemStack mh = player.getItemBySlot(EquipmentSlot.MAINHAND);
        return !mh.isEmpty() && ItemStack.isSameItem(mh, stack);
    }


    private static boolean isInOffhand(Player player, ItemStack stack) {
        ItemStack oh = player.getItemBySlot(EquipmentSlot.OFFHAND);
        return !oh.isEmpty() && ItemStack.isSameItem(oh, stack);
    }


    private static boolean isInArmorSlot(Player player, ItemStack stack) {
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        if (eq == null) return false;
        ItemStack slot = player.getItemBySlot(eq.slot());
        return !slot.isEmpty() && ItemStack.isSameItem(slot, stack);
    }


    /**
     * Returns the equip badge for a stack: [R], [L], [B], [A], or null.
     * R = right/mainhand, L = left/offhand, B = both, A = armor slot.
     */
    private static String getEquipBadge(Player player, ItemStack stack) {
        if (isArmor(stack))   return isInArmorSlot(player, stack) ? "[A]" : null;
        if (isWeapon(stack)) {
            boolean main = isInMainhand(player, stack);
            boolean off  = isInOffhand(player, stack);
            if (main && off) return "[B]";
            if (main)        return "[R]";
            if (off)         return "[L]";
        }
        return null;
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private void computeLayout() {
        listX = PADDING;
        listY = PADDING + TAB_H + 4 + CAT_TAB_H + 2;
        listW = width * LEFT_W_PCT / 100;
        listH = height - listY - BOTTOM_H - PADDING;
        visibleRows = Math.max(1, (listH - ROW_H) / ROW_H);

        detailX = listX + listW + PADDING;
        detailY = PADDING + TAB_H + 4;
        detailW = width - detailX - PADDING;
        detailH = height - detailY - BOTTOM_H - PADDING;
    }

    // ── Item list ─────────────────────────────────────────────────────────────

    private void rebuildItemList() {
        filteredItems.clear();
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (matchesCategory(stack, category))
                filteredItems.add(new ItemEntry(stack, i));
        }
        if (selectedItem >= filteredItems.size()) selectedItem = -1;
        scrollOffset = Math.max(0, Math.min(scrollOffset,
                Math.max(0, filteredItems.size() - visibleRows)));
    }

    private boolean matchesCategory(ItemStack stack, Category cat) {
        return switch (cat) {
            case ALL     -> true;
            case WEAPONS -> isWeapon(stack);
            case ARMOR   -> isArmor(stack);
            case POTIONS -> isPotion(stack);
            case FOOD    -> isFood(stack);
            case TOOLS   -> isTool(stack);
            case MISC    -> isMisc(stack);
        };
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, withAlpha(COLOR_BG, (int)(alpha * 0xEE)));
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

        computeLayout();
        rebuildItemList();

        drawMainTabs(g, mx, my, ba);
        if (mainTab == MainTab.INVENTORY) {
            drawCategoryTabs(g, mx, my, ba);
            drawItemList(g, mx, my, ba);
            int displayIdx = selectedItem >= 0 ? selectedItem
                    : hoveredRow  >= 0 ? hoveredRow : -1;
            if (displayIdx >= 0) drawItemDetail(g, mx, my, ba, displayIdx);
        } else {
            drawEquipmentTab(g, mx, my, ba);
        }
        drawBottomBar(g, mx, my, ba);
    }

    // ── Main tabs ─────────────────────────────────────────────────────────────

    private void drawMainTabs(GuiGraphicsExtractor g, int mx, int my, int ba) {
        String[] labels = { "INVENTORY", "EQUIPMENT" };
        int tabW = 90, tx = PADDING;
        for (int i = 0; i < labels.length; i++) {
            boolean active = mainTab == MainTab.values()[i];
            int bg  = withAlpha(active ? COLOR_TAB_ACTIVE   : COLOR_TAB_INACTIVE, ba);
            int tc  = withAlpha(active ? COLOR_VALUE         : COLOR_LABEL,        ba);
            int brc = withAlpha(active ? COLOR_VALUE         : COLOR_BORDER,       ba);
            g.fill(tx,        PADDING,         tx+tabW,  PADDING+TAB_H,   bg);
            g.fill(tx,        PADDING,         tx+tabW,  PADDING+1,        brc);
            g.fill(tx,        PADDING+TAB_H-1, tx+tabW,  PADDING+TAB_H,   brc);
            g.fill(tx,        PADDING,         tx+1,     PADDING+TAB_H,    brc);
            g.fill(tx+tabW-1, PADDING,         tx+tabW,  PADDING+TAB_H,   brc);
            g.text(font, Component.literal(labels[i]),
                    tx + tabW/2 - font.width(labels[i])/2,
                    PADDING + (TAB_H - 8)/2, tc, false);
            tx += tabW + 4;
        }
    }

    // ── Category tabs ─────────────────────────────────────────────────────────

    private void drawCategoryTabs(GuiGraphicsExtractor g, int mx, int my, int ba) {
        int catY = PADDING + TAB_H + 4;
        int n    = CATEGORIES.length;
        int tabW = listW / n;
        int tx   = listX;
        for (int i = 0; i < n; i++) {
            boolean active = category == CATEGORIES[i];
            int bg  = withAlpha(active ? COLOR_TAB_ACTIVE   : COLOR_TAB_INACTIVE, ba);
            int tc  = withAlpha(active ? COLOR_VALUE         : COLOR_LABEL,        ba);
            int brc = withAlpha(active ? COLOR_VALUE         : COLOR_BORDER,       ba);
            int x0 = tx, x1 = tx + tabW - 1;
            g.fill(x0,   catY,              x1, catY + CAT_TAB_H,   bg);
            g.fill(x0,   catY,              x1, catY + 1,            brc);
            g.fill(x0,   catY+CAT_TAB_H-1, x1, catY + CAT_TAB_H,   brc);
            g.fill(x0,   catY,              x0+1, catY + CAT_TAB_H, brc);
            g.fill(x1-1, catY,              x1,   catY + CAT_TAB_H, brc);
            String label  = CAT_LABELS[i];
            int    lw     = font.width(label);
            int    labelX = Math.max(x0 + 2, Math.min(x0 + (tabW-1)/2 - lw/2, x1 - lw - 2));
            g.text(font, Component.literal(label), labelX, catY + (CAT_TAB_H - 8)/2, tc, false);
            tx += tabW;
        }
    }

    // ── Item list panel ───────────────────────────────────────────────────────

    private void drawItemList(GuiGraphicsExtractor g, int mx, int my, int ba) {
        Player player = Minecraft.getInstance().player;

        g.fill(listX, listY, listX+listW, listY+listH, withAlpha(0x55001020, ba));
        g.fill(listX,         listY,         listX+listW, listY+1,        withAlpha(COLOR_BORDER, ba));
        g.fill(listX,         listY+listH-1, listX+listW, listY+listH,    withAlpha(COLOR_BORDER, ba));
        g.fill(listX,         listY,         listX+1,     listY+listH,    withAlpha(COLOR_BORDER, ba));
        g.fill(listX+listW-1, listY,         listX+listW, listY+listH,    withAlpha(COLOR_BORDER, ba));

        g.text(font, Component.literal("NAME"), listX+ICON_SIZE+6, listY+4, withAlpha(COLOR_LABEL, ba), false);
        g.text(font, Component.literal("QTY"),  listX+listW-55,    listY+4, withAlpha(COLOR_LABEL, ba), false);
        g.fill(listX, listY+ROW_H, listX+listW, listY+ROW_H+1, withAlpha(COLOR_SEPARATOR, ba));

        int startY    = listY + ROW_H + 1;
        int clipBottom = listY + listH - 1;
        hoveredRow = -1;

        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredItems.size()) break;
            ItemEntry entry = filteredItems.get(idx);
            int rowY   = startY + i * ROW_H;
            int drawBot = Math.min(rowY + ROW_H, clipBottom);
            if (rowY >= clipBottom) break;

            boolean sel = idx == selectedItem;
            boolean hov = inBounds(mx, my, listX+1, rowY, listW-2, ROW_H);
            if (hov) hoveredRow = idx;

            int rowBg = sel ? withAlpha(COLOR_ROW_SEL, ba)
                    : hov ? withAlpha(COLOR_ROW_HOV, ba)
                    : i%2==0 ? withAlpha(COLOR_ROW_EVEN, ba) : 0;
            if (rowBg != 0) g.fill(listX+1, rowY, listX+listW-1, drawBot, rowBg);

            int iconY = rowY + (ROW_H - 16) / 2;
            if (iconY + 16 <= clipBottom) g.item(entry.stack, listX+2, iconY);

            int textY = rowY + (ROW_H - 8) / 2;
            if (textY < clipBottom) {
                // Badge — [R], [L], [B], [A]
                String badge = player != null ? getEquipBadge(player, entry.stack) : null;

                // Reserve space for badge on the right of the name area
                int badgeW  = badge != null ? font.width(badge) + 4 : 0;
                int nameW   = listW - ICON_SIZE - 8 - 60 - badgeW;
                String name = entry.stack.getHoverName().getString();
                if (font.width(name) > nameW) name = truncate(name, nameW);

                g.text(font, Component.literal(name), listX+ICON_SIZE+6, textY,
                        withAlpha(sel ? COLOR_VALUE : 0xFFFFFFFF, ba), false);

                if (badge != null) {
                    int badgeX = listX + ICON_SIZE + 6 + font.width(name) + 3;
                    g.text(font, Component.literal(badge), badgeX, textY,
                            withAlpha(COLOR_EQUIP_BADGE, ba), false);
                }

                if (entry.stack.getCount() > 1) {
                    String qty = "x" + entry.stack.getCount();
                    g.text(font, Component.literal(qty), listX+listW-55, textY,
                            withAlpha(COLOR_LABEL, ba), false);
                }
            }
        }

        // Scrollbar
        if (filteredItems.size() > visibleRows) {
            int maxScroll = filteredItems.size() - visibleRows;
            int trackH   = listH - ROW_H - 4;
            int thumbH   = Math.max(10, trackH * visibleRows / filteredItems.size());
            int thumbY   = listY + ROW_H + 2
                    + (int)((float)scrollOffset / maxScroll * (trackH - thumbH));
            g.fill(listX+listW-3, listY+ROW_H+2, listX+listW-1, listY+listH-2,
                    withAlpha(0xFF001830, ba));
            g.fill(listX+listW-3, thumbY, listX+listW-1, thumbY+thumbH,
                    withAlpha(COLOR_BORDER, ba));
        }
    }

    // ── Item detail panel ─────────────────────────────────────────────────────

    private void drawItemDetail(GuiGraphicsExtractor g, int mx, int my, int ba, int displayIdx) {
        if (displayIdx >= filteredItems.size()) return;

        g.fill(detailX, detailY, detailX+detailW, detailY+detailH, withAlpha(0x55001020, ba));
        g.fill(detailX,           detailY,           detailX+detailW, detailY+1,         withAlpha(COLOR_BORDER, ba));
        g.fill(detailX,           detailY+detailH-1, detailX+detailW, detailY+detailH,   withAlpha(COLOR_BORDER, ba));
        g.fill(detailX,           detailY,           detailX+1,       detailY+detailH,   withAlpha(COLOR_BORDER, ba));
        g.fill(detailX+detailW-1, detailY,           detailX+detailW, detailY+detailH,   withAlpha(COLOR_BORDER, ba));

        ItemStack stack = filteredItems.get(displayIdx).stack;
        int cy = detailY + PADDING;

        // Big icon 3×
        int iconScale = 3, iconScreenW = 16 * iconScale;
        g.pose().pushMatrix();
        g.pose().translate(detailX + detailW/2f - iconScreenW/2f, (float) cy);
        g.pose().scale(iconScale, iconScale);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
        cy += iconScreenW + 6;

        // Name
        String name = stack.getHoverName().getString();
        g.text(font, Component.literal(name),
                detailX + detailW/2 - font.width(name)/2, cy,
                withAlpha(COLOR_VALUE, ba), true);
        cy += 12;

        // Type
        String type = getItemType(stack);
        g.text(font, Component.literal(type),
                detailX + detailW/2 - font.width(type)/2, cy,
                withAlpha(COLOR_LABEL, ba), false);
        cy += 10;

        g.fill(detailX+PADDING, cy, detailX+detailW-PADDING, cy+1, withAlpha(COLOR_SEPARATOR, ba));
        cy += 7;

        cy = drawItemStats(g, stack, detailX+PADDING, cy, detailW-PADDING*2, ba);

        g.fill(detailX+PADDING, cy, detailX+detailW-PADDING, cy+1, withAlpha(COLOR_SEPARATOR, ba));

        // Action hint
        String action = getActionHint(stack);
        g.text(font, Component.literal(action),
                detailX + detailW/2 - font.width(action)/2,
                detailY + detailH - PADDING - 8,
                withAlpha(COLOR_LABEL, ba), false);
    }

    // ── Stats + comparison ────────────────────────────────────────────────────

    private int drawItemStats(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int w, int ba) {
        var food      = stack.get(DataComponents.FOOD);
        var tool      = stack.get(DataComponents.TOOL);
        var attrMods  = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        var maxDamage = stack.get(DataComponents.MAX_DAMAGE);
        Player player = Minecraft.getInstance().player;

        if (isWeapon(stack)) {
            double atkDmg = 1.0, atkSpeed = 4.0;
            if (attrMods != null) {
                for (var entry : attrMods.modifiers()) {
                    if (entry.attribute().is(Attributes.ATTACK_DAMAGE))
                        atkDmg   = entry.modifier().amount() + 1.0;
                    else if (entry.attribute().is(Attributes.ATTACK_SPEED))
                        atkSpeed = entry.modifier().amount() + 4.0;
                }
            }
            double cmpDmg = 0, cmpSpeed = 0;
            if (player != null) {
                ItemStack mh = player.getItemBySlot(EquipmentSlot.MAINHAND);
                if (!mh.isEmpty() && !ItemStack.isSameItemSameComponents(mh, stack)) {
                    var cmpMods = mh.get(DataComponents.ATTRIBUTE_MODIFIERS);
                    if (cmpMods != null) for (var e : cmpMods.modifiers()) {
                        if (e.attribute().is(Attributes.ATTACK_DAMAGE))
                            cmpDmg   = e.modifier().amount() + 1.0;
                        else if (e.attribute().is(Attributes.ATTACK_SPEED))
                            cmpSpeed = e.modifier().amount() + 4.0;
                    }
                }
            }
            drawStatWithDelta(g, x, y, w, "Attack Damage",
                    String.format("%.1f", atkDmg), atkDmg - cmpDmg, cmpDmg != 0, ba); y += 11;
            drawStatWithDelta(g, x, y, w, "Attack Speed",
                    String.format("%.2f", atkSpeed), atkSpeed - cmpSpeed, cmpSpeed != 0, ba); y += 11;
            if (maxDamage != null) {
                drawStat(g, x, y, w, "Durability",
                        (maxDamage - stack.getDamageValue()) + "/" + maxDamage, ba); y += 11;
            }

        } else if (isArmor(stack)) {
            double armor = 0, toughness = 0;
            if (attrMods != null) for (var entry : attrMods.modifiers()) {
                if (entry.attribute().is(Attributes.ARMOR))           armor     = entry.modifier().amount();
                else if (entry.attribute().is(Attributes.ARMOR_TOUGHNESS)) toughness = entry.modifier().amount();
            }
            double cmpArmor = 0, cmpToughness = 0;
            if (player != null) {
                Equippable eq = stack.get(DataComponents.EQUIPPABLE);
                if (eq != null) {
                    ItemStack current = player.getItemBySlot(eq.slot());
                    if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) {
                        var cmpMods = current.get(DataComponents.ATTRIBUTE_MODIFIERS);
                        if (cmpMods != null) for (var e : cmpMods.modifiers()) {
                            if (e.attribute().is(Attributes.ARMOR))           cmpArmor     = e.modifier().amount();
                            else if (e.attribute().is(Attributes.ARMOR_TOUGHNESS)) cmpToughness = e.modifier().amount();
                        }
                    }
                }
            }
            drawStatWithDelta(g, x, y, w, "Armor",
                    String.format("%.0f", armor), armor - cmpArmor, cmpArmor != 0, ba); y += 11;
            drawStatWithDelta(g, x, y, w, "Toughness",
                    String.format("%.1f", toughness), toughness - cmpToughness, cmpToughness != 0, ba); y += 11;
            if (maxDamage != null) {
                drawStat(g, x, y, w, "Durability",
                        (maxDamage - stack.getDamageValue()) + "/" + maxDamage, ba); y += 11;
            }

        } else if (isPotion(stack)) {
            drawStat(g, x, y, w, "Type", "Potion", ba); y += 11;

        } else if (food != null) {
            drawStat(g, x, y, w, "Nutrition",  String.valueOf(food.nutrition()),         ba); y += 11;
            drawStat(g, x, y, w, "Saturation", String.format("%.1f", food.saturation()), ba); y += 11;

        } else if (isTool(stack)) {
            // Mining speed from Tool component
            if (tool != null) {
                double miningSpeed = tool.defaultMiningSpeed();
                // Compare vs current mainhand tool
                double cmpSpeed = 0;
                if (player != null) {
                    ItemStack mh = player.getItemBySlot(EquipmentSlot.MAINHAND);
                    if (!mh.isEmpty() && !ItemStack.isSameItemSameComponents(mh, stack)) {
                        var cmpTool = mh.get(DataComponents.TOOL);
                        if (cmpTool != null) cmpSpeed = cmpTool.defaultMiningSpeed();
                    }
                }
                drawStatWithDelta(g, x, y, w, "Mining Speed",
                        String.format("%.1f", miningSpeed),
                        miningSpeed - cmpSpeed, cmpSpeed != 0, ba); y += 11;
            }
            if (maxDamage != null) {
                drawStat(g, x, y, w, "Durability",
                        (maxDamage - stack.getDamageValue()) + "/" + maxDamage, ba); y += 11;
            }

        } else {
            if (maxDamage != null) {
                drawStat(g, x, y, w, "Durability",
                        (maxDamage - stack.getDamageValue()) + "/" + maxDamage, ba); y += 11;
            } else {
                g.text(font, Component.literal("Misc item"), x, y,
                        withAlpha(COLOR_LABEL, ba), false); y += 11;
            }
        }

        // ── Enchantments ──────────────────────────────────────────────────────────
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) {
            g.fill(x, y, x + w, y + 1, withAlpha(COLOR_SEPARATOR, ba));
            y += 5;
            g.text(font, Component.literal("Enchantments"), x, y,
                    withAlpha(COLOR_LABEL, ba), false);
            y += 10;
            for (var entry : enchantments.entrySet()) {
                String enchName = entry.getKey().unwrapKey()
                        .map(k -> k.identifier().getPath()).orElse("unknown");
                String display  = capitalize(enchName.replace('_', ' '));
                int    level    = entry.getIntValue();
                String label    = level > 1 ? display + " " + toRoman(level) : display;
                g.text(font, Component.literal(label), x, y,
                        withAlpha(0xFFFFDD44, ba), false);
                y += 10;
            }
        }
        return y;
    }


    /**
     * Draws a stat row. If showDelta is true, appends a colored delta after the value.
     * e.g. "7.0 (+2.0)" in green or "7.0 (-1.0)" in red.
     */
    private void drawStatWithDelta(GuiGraphicsExtractor g, int x, int y, int w,
                                   String label, String value, double delta,
                                   boolean showDelta, int ba) {
        g.text(font, Component.literal(label), x, y, withAlpha(COLOR_LABEL, ba), false);

        int valueX = x + w - font.width(value);
        if (showDelta && Math.abs(delta) > 0.001) {
            String deltaStr = String.format(delta > 0 ? "(+%.1f)" : "(%.1f)", delta);
            int deltaColor  = delta > 0 ? COLOR_STAT_UP : COLOR_STAT_DOWN;
            int deltaW      = font.width(deltaStr);
            // value right-aligned, delta just left of value
            valueX = x + w - font.width(value) - deltaW - 3;
            g.text(font, Component.literal(value), valueX, y,
                    withAlpha(COLOR_VALUE, ba), false);
            g.text(font, Component.literal(deltaStr), valueX + font.width(value) + 3, y,
                    withAlpha(deltaColor, ba), false);
        } else {
            g.text(font, Component.literal(value), valueX, y,
                    withAlpha(COLOR_VALUE, ba), false);
        }
    }

    private void drawStat(GuiGraphicsExtractor g, int x, int y, int w,
                          String label, String value, int ba) {
        g.text(font, Component.literal(label), x, y, withAlpha(COLOR_LABEL, ba), false);
        g.text(font, Component.literal(value), x + w - font.width(value), y,
                withAlpha(COLOR_VALUE, ba), false);
    }

    private String getItemType(ItemStack stack) {
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        if (isPotion(stack)) return "Potion";
        if (isFood(stack))   return "Food";
        if (isWeapon(stack)) {
            if (stack.is(ModTags.BOWS))      return "Bow";
            if (stack.is(ModTags.CROSSBOWS)) return "Crossbow";
            List<String> parts = new ArrayList<>();
            if (stack.is(ModTags.ONE_HANDED_WEAPONS)) parts.add("One-Handed");
            if (stack.is(ModTags.TWO_HANDED_WEAPONS)) parts.add("Two-Handed");
            if (stack.is(ModTags.THROWN_WEAPONS))     parts.add("Thrown");
            if (!parts.isEmpty()) return String.join(" / ", parts);
            return "Weapon";
        }
        if (isArmor(stack) && eq != null) return switch (eq.slot()) {
            case HEAD  -> "Helmet";
            case CHEST -> "Chestplate";
            case LEGS  -> "Leggings";
            case FEET  -> "Boots";
            default    -> "Equipment";
        };
        if (isTool(stack)) return "Tool";
        return "Misc";
    }

    /**
     * Action hint shown at the bottom of the detail panel.
     * Two-handed/Bow/Crossbow: [E] Equip/Unequip  [Q] Drop
     * One-handed:              [E] Main  [RMB] Off  [Q] Drop  (or Unequip if equipped)
     * Armor:                   [E] Equip  [Q] Drop
     * Food/Potion:             [E] Use  [Q] Drop
     * Misc:                    [Q] Drop
     */
    private String getActionHint(ItemStack stack) {
        Player player = Minecraft.getInstance().player;
        String dropHint = "   [Q] Drop  [R] Drop All";

        if (isPotion(stack) || isFood(stack)) return "[E] Use" + dropHint;
        if (isArmor(stack))   return "[E] Equip" + dropHint;
        if (isHandTool(stack)) {
            boolean inMain = player != null && isInMainhand(player, stack);
            return (inMain ? "[E] Unequip" : "[E] Equip") + dropHint;
        }
        if (isTwoHandedWeapon(stack)) {
            boolean inMain = player != null && isInMainhand(player, stack);
            return (inMain ? "[E] Unequip" : "[E] Equip") + dropHint;
        }
        if (isOneHandedWeapon(stack) || isThrownWeapon(stack)) {
            if (player != null) {
                boolean inMain = isInMainhand(player, stack);
                boolean inOff  = isInOffhand(player, stack);
                if (inMain && inOff) return "[E] Unequip" + dropHint;
                if (inMain)          return "[E] → Offhand" + dropHint;
                if (inOff)           return "[E] → Mainhand" + dropHint;
            }
            return "[E] Main  [RMB] Off" + dropHint;
        }
        if (isWeapon(stack)) return "[E] Equip" + dropHint;
        return "[Q] Drop  [R] Drop All";
    }



    // ── Equipment tab ─────────────────────────────────────────────────────────

    private static final String[] SLOT_LABELS = {
            "Head", "Chest", "Legs", "Feet", "Main Hand", "Off Hand",
            "Belt", "Ring 1", "Ring 2", "Amulet"
    };

    private void drawEquipmentTab(GuiGraphicsExtractor g, int mx, int my, int ba) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        int cols = 2, slotW = (width-PADDING*3)/cols, slotH = 24, slotGap = 6;
        int startY = PADDING + TAB_H + 10;
        hoveredSlot = -1;

        for (int i = 0; i < SLOT_LABELS.length; i++) {
            int col = i % cols, row = i / cols;
            int sx  = PADDING + col*(slotW+PADDING), sy = startY + row*(slotH+slotGap);
            boolean sel    = i == selectedSlot;
            boolean hov    = inBounds(mx, my, sx, sy, slotW, slotH);
            if (hov) hoveredSlot = i;

            ItemStack equipped = getEquippedForSlot(player, i);
            boolean filled = !equipped.isEmpty();

            int bg = withAlpha(sel ? COLOR_ROW_SEL : hov ? COLOR_ROW_HOV
                    : filled ? COLOR_EQUIP_FILLED : COLOR_EQUIP_SLOT, ba);
            g.fill(sx, sy, sx+slotW, sy+slotH, bg);
            if (sel) g.fill(sx-1, sy-1, sx+slotW+1, sy+slotH+1,
                    withAlpha(COLOR_BORDER_GLOW, ba/2));

            int bc = withAlpha(sel ? COLOR_VALUE : filled ? COLOR_BORDER : 0xFF1A3A5A, ba);
            g.fill(sx,         sy,         sx+slotW, sy+1,       bc);
            g.fill(sx,         sy+slotH-1, sx+slotW, sy+slotH,   bc);
            g.fill(sx,         sy,         sx+1,     sy+slotH,   bc);
            g.fill(sx+slotW-1, sy,         sx+slotW, sy+slotH,   bc);

            g.text(font, Component.literal(SLOT_LABELS[i]),
                    sx+PADDING, sy+3, withAlpha(COLOR_LABEL, ba), false);
            if (filled) {
                g.item(equipped, sx+slotW-14, sy+4);
                g.text(font, Component.literal(
                                truncate(equipped.getHoverName().getString(), slotW - PADDING*2 - 16)),
                        sx+PADDING, sy+13, withAlpha(COLOR_VALUE, ba), false);
            } else {
                g.text(font, Component.literal(getEmptySlotLabel(i)),
                        sx+PADDING, sy+13, withAlpha(0xFF2A4A5A, ba), false);
            }
        }

        int warnY = startY + (SLOT_LABELS.length/cols + 1)*(slotH+slotGap);
        g.text(font, Component.literal("⚠ Belt / Rings / Amulet slots coming soon"),
                PADDING, warnY, withAlpha(COLOR_LABEL, ba/2), false);

        if (selectedSlot >= 0 && !getEquippedForSlot(player, selectedSlot).isEmpty()) {
            String hint = "[E] Unequip";
            g.text(font, Component.literal(hint),
                    width/2 - font.width(hint)/2, height - BOTTOM_H - PADDING - 8,
                    withAlpha(COLOR_VALUE, ba), false);
        }
    }

    private ItemStack getEquippedForSlot(Player player, int i) {
        return switch (i) {
            case 0 -> player.getItemBySlot(EquipmentSlot.HEAD);
            case 1 -> player.getItemBySlot(EquipmentSlot.CHEST);
            case 2 -> player.getItemBySlot(EquipmentSlot.LEGS);
            case 3 -> player.getItemBySlot(EquipmentSlot.FEET);
            case 4 -> player.getItemBySlot(EquipmentSlot.MAINHAND);
            case 5 -> player.getItemBySlot(EquipmentSlot.OFFHAND);
            default -> ItemStack.EMPTY;
        };
    }

    private String getEmptySlotLabel(int i) {
        return switch (i) {
            case 6    -> "No belt equipped";
            case 7, 8 -> "No ring equipped  (max 2)";
            case 9    -> "No amulet equipped";
            default   -> "Empty";
        };
    }

    // ── Bottom bar ────────────────────────────────────────────────────────────

    private void drawBottomBar(GuiGraphicsExtractor g, int mx, int my, int ba) {
        int barY = height - BOTTOM_H;
        g.fill(0, barY, width, height, withAlpha(COLOR_BOTTOM, ba));
        g.fill(0, barY, width, barY+1, withAlpha(COLOR_BORDER, ba));
        int cy = barY + (BOTTOM_H - 8) / 2;

        Player player = Minecraft.getInstance().player;
        if (player != null) {
            int used = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++)
                if (!player.getInventory().getItem(i).isEmpty()) used++;
            g.text(font, Component.literal("Items: " + used),
                    PADDING, cy, withAlpha(COLOR_LABEL, ba), false);
        }

        long walletValue = ClientWalletManager.getValue();
        long goldCount = 0, silverCount = 0, copperCount = 0;
        for (var cc : CurrencyHelper.breakdown(walletValue)) {
            switch (cc.denomination()) {
                case GOLD   -> goldCount   = cc.count();
                case SILVER -> silverCount = cc.count();
                case COPPER -> copperCount = cc.count();
            }
        }
        String goldStr = ": " + goldCount, silverStr = ": " + silverCount,
                copperStr = ": " + copperCount;
        int sqTextGap = 3, entryGap = 14;
        int textW  = Math.max(font.width(goldStr), Math.max(font.width(silverStr), font.width(copperStr)));
        int entryW = COIN_SQ + sqTextGap + textW;
        int totalW = entryW * 3 + entryGap * 2;
        int cx     = width / 2 - totalW / 2;
        int sqY    = cy + (8 - COIN_SQ) / 2;
        drawCoinEntry(g, cx, cy, sqY, COLOR_GOLD,   goldStr,   ba); cx += entryW + entryGap;
        drawCoinEntry(g, cx, cy, sqY, COLOR_SILVER, silverStr, ba); cx += entryW + entryGap;
        drawCoinEntry(g, cx, cy, sqY, COLOR_COPPER, copperStr, ba);

        String tabHint = mainTab == MainTab.INVENTORY ? "[TAB] Equipment" : "[TAB] Inventory";
        g.text(font, Component.literal(tabHint),
                width - PADDING - font.width(tabHint), cy,
                withAlpha(COLOR_LABEL, ba), false);
    }

    private void drawCoinEntry(GuiGraphicsExtractor g, int x, int cy, int sqY,
                               int color, String countStr, int ba) {
        g.fill(x, sqY, x + COIN_SQ, sqY + COIN_SQ, withAlpha(color, ba));
        g.text(font, Component.literal(countStr),
                x + COIN_SQ + 3, cy, withAlpha(color, ba), false);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mainTab == MainTab.INVENTORY) {
            int maxScroll = Math.max(0, filteredItems.size() - visibleRows);
            scrollOffset  = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY));
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        int mx = (int) mouse.x(), my = (int) mouse.y();
        int button = mouse.button(); // 0 = left, 1 = right

        // Main tabs
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
            int catTabW = listW / CATEGORIES.length;
            int ctx     = listX;
            for (int i = 0; i < CATEGORIES.length; i++) {
                if (inBounds(mx, my, ctx, catY, catTabW - 1, CAT_TAB_H)) {
                    setCategory(CATEGORIES[i]); playClick(); return true;
                }
                ctx += catTabW;
            }

            // Rows
            int startY = listY + ROW_H + 1;
            for (int i = 0; i < visibleRows; i++) {
                int idx = scrollOffset + i;
                if (idx >= filteredItems.size()) break;
                int rowY = startY + i * ROW_H;
                if (rowY >= listY + listH - 1) break;
                if (inBounds(mx, my, listX+1, rowY, listW-2, ROW_H)) {
                    if (selectedItem != idx) {
                        selectedItem = idx; playClick();
                    } else {
                        // Already selected — perform action based on button
                        if (button == 1) performActionRight(idx); // right-click → offhand
                        else             performAction(idx);       // left-click → mainhand / default
                    }
                    return true;
                }
            }
            if (inBounds(mx, my, listX, listY, listW, listH)) {
                selectedItem = -1; return true;
            }

        } else {
            int cols = 2, slotW = (width-PADDING*3)/cols, slotH = 24, slotGap = 6;
            int startY = PADDING + TAB_H + 10;
            for (int i = 0; i < SLOT_LABELS.length; i++) {
                int sx = PADDING + (i%cols)*(slotW+PADDING), sy = startY + (i/cols)*(slotH+slotGap);
                if (inBounds(mx, my, sx, sy, slotW, slotH)) {
                    if (selectedSlot == i) performUnequip(i);
                    else { selectedSlot = i; playClick(); }
                    return true;
                }
            }
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
                setCategory(CATEGORIES[(category.ordinal() - 1 + CATEGORIES.length) % CATEGORIES.length]);
                playClick(); return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_D) {
                setCategory(CATEGORIES[(category.ordinal() + 1) % CATEGORIES.length]);
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
                if (selectedItem >= scrollOffset + visibleRows)
                    scrollOffset = selectedItem - visibleRows + 1;
                playClick(); return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_E && selectedItem >= 0) {
                performAction(selectedItem); return true;
            }
            if (key == GLFW_KEY_Q && selectedItem >= 0) { performDrop(selectedItem); return true; }
            if (key == GLFW_KEY_R && selectedItem >= 0) { performDropAll(selectedItem); return true; }
        } else {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_W) {
                selectedSlot = selectedSlot <= 0 ? 0 : selectedSlot - 1;
                playClick(); return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
                selectedSlot = Math.min(SLOT_LABELS.length-1,
                        selectedSlot < 0 ? 0 : selectedSlot + 1);
                playClick(); return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_E && selectedSlot >= 0) {
                performUnequip(selectedSlot); return true;
            }
        }
        return super.keyPressed(event);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void setCategory(Category cat) {
        category = cat; scrollOffset = 0; selectedItem = -1;
        rebuildItemList();
    }

    /**
     * Primary action (E key or left-click on already-selected item).
     *
     * Two-handed / Bow / Crossbow:
     *   Not equipped → equip to mainhand
     *   In mainhand  → unequip
     *
     * One-handed / Thrown:
     *   Not equipped anywhere → equip to mainhand
     *   In mainhand only      → equip to offhand as well
     *   In offhand only       → equip to mainhand
     *   In both               → unequip from both
     *
     * Armor:
     *   Equip to correct slot, swapping out old item to inventory
     *
     * Food / Potion:
     *   Close screen, use item, reopen
     *
     * Misc: no-op
     */
    private void performAction(int idx) {
        if (idx < 0 || idx >= filteredItems.size()) return;
        ItemEntry entry  = filteredItems.get(idx);
        ItemStack stack  = entry.stack;
        Player    player = Minecraft.getInstance().player;
        if (player == null) return;

        if (isArmor(stack)) {
            Equippable eq = stack.get(DataComponents.EQUIPPABLE);
            if (eq == null) return;
            ClientPlayNetworking.send(
                    new InventoryEquipPayload(entry.slot, eq.slot(), false));

        } else if (isHandTool(stack)) {
            if (isInMainhand(player, stack)) {
                ClientPlayNetworking.send(
                        new InventoryEquipPayload(entry.slot, EquipmentSlot.MAINHAND, true));
            } else {
                ClientPlayNetworking.send(
                        new InventoryEquipPayload(entry.slot, EquipmentSlot.MAINHAND, false));
            }

        } else if (isTwoHandedWeapon(stack)) {
            if (isInMainhand(player, stack)) {
                ClientPlayNetworking.send(
                        new InventoryEquipPayload(entry.slot, EquipmentSlot.MAINHAND, true));
            } else {
                ClientPlayNetworking.send(
                        new InventoryEquipPayload(entry.slot, EquipmentSlot.MAINHAND, false));
            }

        } else if (isOneHandedWeapon(stack) || isThrownWeapon(stack)) {
            boolean inMain = isInMainhand(player, stack);
            boolean inOff  = isInOffhand(player, stack);
            if (inMain && inOff) {
                ClientPlayNetworking.send(
                        new InventoryEquipPayload(entry.slot, EquipmentSlot.MAINHAND, true));
                ClientPlayNetworking.send(
                        new InventoryEquipPayload(entry.slot, EquipmentSlot.OFFHAND, true));
            } else if (inMain) {
                ClientPlayNetworking.send(
                        new InventoryEquipPayload(entry.slot, EquipmentSlot.OFFHAND, false));
            } else if (inOff) {
                ClientPlayNetworking.send(
                        new InventoryEquipPayload(entry.slot, EquipmentSlot.OFFHAND, true));
                ClientPlayNetworking.send(
                        new InventoryEquipPayload(entry.slot, EquipmentSlot.MAINHAND, false));
            } else {
                ClientPlayNetworking.send(
                        new InventoryEquipPayload(entry.slot, EquipmentSlot.MAINHAND, false));
            }

        } else if (isPotion(stack) || isFood(stack)) {
            ClientPlayNetworking.send(new InventoryUsePayload(entry.slot));

        } else if (isWeapon(stack)) {
            ClientPlayNetworking.send(
                    new InventoryEquipPayload(entry.slot, EquipmentSlot.MAINHAND, false));
        }

        playClick();
        // Don't call rebuildItemList() here — server will update inventory,
        // client will receive the update on next tick automatically.
    }


    /**
     * Right-click action — for one-handed weapons goes directly to offhand.
     * For everything else behaves the same as left-click.
     */
    private void performActionRight(int idx) {
        if (idx < 0 || idx >= filteredItems.size()) return;
        ItemEntry entry = filteredItems.get(idx);
        ItemStack stack = entry.stack;

        if ((isOneHandedWeapon(stack) || isThrownWeapon(stack)) && !isTwoHandedWeapon(stack)) {
            ClientPlayNetworking.send(
                    new InventoryEquipPayload(entry.slot, EquipmentSlot.OFFHAND, false));
            playClick();
        } else {
            performAction(idx);
        }
    }


    private void performDrop(int idx) {
        if (idx < 0 || idx >= filteredItems.size()) return;
        ClientPlayNetworking.send(
                new InventoryDropPayload(filteredItems.get(idx).slot, false));
        selectedItem = -1;
        playClick();
    }

    private void performDropAll(int idx) {
        if (idx < 0 || idx >= filteredItems.size()) return;
        ClientPlayNetworking.send(
                new InventoryDropPayload(filteredItems.get(idx).slot, true));
        selectedItem = -1;
        playClick();
    }



    private void performUnequip(int slotIndex) {
        if (slotIndex > 5) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        EquipmentSlot slot = switch (slotIndex) {
            case 0 -> EquipmentSlot.HEAD;
            case 1 -> EquipmentSlot.CHEST;
            case 2 -> EquipmentSlot.LEGS;
            case 3 -> EquipmentSlot.FEET;
            case 4 -> EquipmentSlot.MAINHAND;
            case 5 -> EquipmentSlot.OFFHAND;
            default -> null;
        };
        if (slot == null) return;
        ItemStack equipped = player.getItemBySlot(slot);
        if (equipped.isEmpty()) return;
        player.getInventory().add(equipped.copy());
        player.setItemSlot(slot, ItemStack.EMPTY);
        playClick();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void fadeOutTo(Runnable onDone) { fadingOut = true; onFadeOutDone = onDone; }

    private String truncate(String s, int maxPx) {
        if (font.width(s) <= maxPx) return s;
        while (s.length() > 3 && font.width(s + "...") > maxPx)
            s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private boolean inBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x+w && my >= y && my < y+h;
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private int withAlpha(int color, int alpha) {
        return ((((color >> 24) & 0xFF) * alpha / 255) << 24) | (color & 0x00FFFFFF);
    }

    private String capitalize(String s) {
        if (s.isEmpty()) return s;
        String[] words = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1));
            }
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    private String toRoman(int level) {
        return switch (level) {
            case 1  -> "I";   case 2  -> "II";  case 3  -> "III";
            case 4  -> "IV";  case 5  -> "V";   case 6  -> "VI";
            case 7  -> "VII"; case 8  -> "VIII";case 9  -> "IX";
            case 10 -> "X";
            default -> String.valueOf(level);
        };
    }

    @Override public boolean isInGameUi()    { return false; }
    @Override public boolean isPauseScreen() { return false; }
}