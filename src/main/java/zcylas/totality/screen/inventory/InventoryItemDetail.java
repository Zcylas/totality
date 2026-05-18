package zcylas.totality.screen.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import zcylas.totality.networking.inventory.InventoryActionHandler;

import java.util.ArrayList;
import java.util.List;

import static zcylas.totality.screen.inventory.InventoryColors.*;
import static zcylas.totality.screen.inventory.InventoryLayout.*;
import static zcylas.totality.screen.inventory.InventoryEquipHelper.*;

/**
 * Renders the right-side item detail panel.
 * Instantiated by TotalityInventoryScreen and kept alive for the session.
 * Future: pull tooltip data / custom stat components from items here.
 */
public final class InventoryItemDetail {

    public void draw(GuiGraphicsExtractor g, Font font,
                     InventoryLayout layout, List<ItemEntry> items,
                     int displayIdx, int ba) {
        if (displayIdx < 0 || displayIdx >= items.size()) return;
        ItemEntry entry = items.get(displayIdx);
        if (entry.stack.isEmpty()) return;

        int dx = layout.detailX, dy = layout.detailY,
                dw = layout.detailW, dh = layout.detailH;

        // Panel background + border
        g.fill(dx, dy, dx+dw, dy+dh, withAlpha(0x55001020, ba));
        g.fill(dx,      dy,      dx+dw, dy+1,    withAlpha(BORDER, ba));
        g.fill(dx,      dy+dh-1, dx+dw, dy+dh,   withAlpha(BORDER, ba));
        g.fill(dx,      dy,      dx+1,  dy+dh,    withAlpha(BORDER, ba));
        g.fill(dx+dw-1, dy,      dx+dw, dy+dh,   withAlpha(BORDER, ba));

        ItemStack stack = entry.stack;
        int cy = dy + PADDING;

        // Large item icon
        int iconScale = 3, iconScreenW = 16 * iconScale;
        g.pose().pushMatrix();
        g.pose().translate(dx + dw/2f - iconScreenW/2f, (float) cy);
        g.pose().scale(iconScale, iconScale);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
        cy += iconScreenW + 6;

        // Name
        String name = stack.getHoverName().getString();
        g.text(font, Component.literal(name),
                dx + dw/2 - font.width(name)/2, cy,
                withAlpha(VALUE, ba), true);
        cy += 12;

        // Type
        String type = getItemType(stack);
        g.text(font, Component.literal(type),
                dx + dw/2 - font.width(type)/2, cy,
                withAlpha(LABEL, ba), false);
        cy += 10;

        g.fill(dx+PADDING, cy, dx+dw-PADDING, cy+1, withAlpha(SEPARATOR, ba));
        cy += 7;

        cy = drawStats(g, font, stack, dx+PADDING, cy, dw-PADDING*2, ba);

        g.fill(dx+PADDING, cy, dx+dw-PADDING, cy+1, withAlpha(SEPARATOR, ba));

        // Action hint
        String action = getActionHint(entry);
        g.text(font, Component.literal(action),
                dx + dw/2 - font.width(action)/2,
                dy + dh - PADDING - 8,
                withAlpha(LABEL, ba), false);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    private int drawStats(GuiGraphicsExtractor g, Font font, ItemStack stack,
                          int x, int y, int w, int ba) {
        var food      = stack.get(DataComponents.FOOD);
        var tool      = stack.get(DataComponents.TOOL);
        var attrMods  = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        var maxDamage = stack.get(DataComponents.MAX_DAMAGE);
        Player player = Minecraft.getInstance().player;

        if (isWeapon(stack)) {
            double atkDmg = 1.0, atkSpeed = 4.0;
            if (attrMods != null) for (var e : attrMods.modifiers()) {
                if (e.attribute().is(Attributes.ATTACK_DAMAGE))     atkDmg   = e.modifier().amount() + 1.0;
                else if (e.attribute().is(Attributes.ATTACK_SPEED)) atkSpeed = e.modifier().amount() + 4.0;
            }
            double cmpDmg = 0, cmpSpeed = 0;
            if (player != null) {
                ItemStack mh = player.getItemBySlot(EquipmentSlot.MAINHAND);
                if (!mh.isEmpty() && !ItemStack.isSameItemSameComponents(mh, stack)) {
                    var cmpMods = mh.get(DataComponents.ATTRIBUTE_MODIFIERS);
                    if (cmpMods != null) for (var e : cmpMods.modifiers()) {
                        if (e.attribute().is(Attributes.ATTACK_DAMAGE))     cmpDmg   = e.modifier().amount() + 1.0;
                        else if (e.attribute().is(Attributes.ATTACK_SPEED)) cmpSpeed = e.modifier().amount() + 4.0;
                    }
                }
            }
            drawStatDelta(g, font, x, y, w, "Attack Damage",
                    String.format("%.1f", atkDmg), atkDmg-cmpDmg, cmpDmg!=0, ba); y += 11;
            drawStatDelta(g, font, x, y, w, "Attack Speed",
                    String.format("%.2f", atkSpeed), atkSpeed-cmpSpeed, cmpSpeed!=0, ba); y += 11;
            if (maxDamage != null) {
                drawStat(g, font, x, y, w, "Durability",
                        (maxDamage - stack.getDamageValue()) + "/" + maxDamage, ba); y += 11;
            }
        } else if (isArmor(stack)) {
            double armor = 0, toughness = 0;
            if (attrMods != null) for (var e : attrMods.modifiers()) {
                if (e.attribute().is(Attributes.ARMOR))               armor     = e.modifier().amount();
                else if (e.attribute().is(Attributes.ARMOR_TOUGHNESS)) toughness = e.modifier().amount();
            }
            double cmpArmor = 0, cmpToughness = 0;
            if (player != null) {
                Equippable eq = stack.get(DataComponents.EQUIPPABLE);
                if (eq != null) {
                    ItemStack cur = player.getItemBySlot(eq.slot());
                    if (!cur.isEmpty() && !ItemStack.isSameItemSameComponents(cur, stack)) {
                        var cmpMods = cur.get(DataComponents.ATTRIBUTE_MODIFIERS);
                        if (cmpMods != null) for (var e : cmpMods.modifiers()) {
                            if (e.attribute().is(Attributes.ARMOR))               cmpArmor     = e.modifier().amount();
                            else if (e.attribute().is(Attributes.ARMOR_TOUGHNESS)) cmpToughness = e.modifier().amount();
                        }
                    }
                }
            }
            drawStatDelta(g, font, x, y, w, "Armor",
                    String.format("%.0f", armor), armor-cmpArmor, cmpArmor!=0, ba); y += 11;
            drawStatDelta(g, font, x, y, w, "Toughness",
                    String.format("%.1f", toughness), toughness-cmpToughness, cmpToughness!=0, ba); y += 11;
            if (maxDamage != null) {
                drawStat(g, font, x, y, w, "Durability",
                        (maxDamage - stack.getDamageValue()) + "/" + maxDamage, ba); y += 11;
            }
        } else if (isPotion(stack)) {
            drawStat(g, font, x, y, w, "Type", "Potion", ba); y += 11;
        } else if (food != null) {
            drawStat(g, font, x, y, w, "Nutrition",  String.valueOf(food.nutrition()), ba); y += 11;
            drawStat(g, font, x, y, w, "Saturation", String.format("%.1f", food.saturation()), ba); y += 11;
        } else if (isTool(stack)) {
            if (tool != null) {
                double speed = tool.defaultMiningSpeed(), cmpSpeed = 0;
                if (player != null) {
                    ItemStack mh = player.getItemBySlot(EquipmentSlot.MAINHAND);
                    if (!mh.isEmpty() && !ItemStack.isSameItemSameComponents(mh, stack)) {
                        var ct = mh.get(DataComponents.TOOL);
                        if (ct != null) cmpSpeed = ct.defaultMiningSpeed();
                    }
                }
                drawStatDelta(g, font, x, y, w, "Mining Speed",
                        String.format("%.1f", speed), speed-cmpSpeed, cmpSpeed!=0, ba); y += 11;
            }
            if (maxDamage != null) {
                drawStat(g, font, x, y, w, "Durability",
                        (maxDamage - stack.getDamageValue()) + "/" + maxDamage, ba); y += 11;
            }
        } else {
            if (maxDamage != null) {
                drawStat(g, font, x, y, w, "Durability",
                        (maxDamage - stack.getDamageValue()) + "/" + maxDamage, ba); y += 11;
            } else {
                g.text(font, Component.literal("Misc item"), x, y, withAlpha(LABEL, ba), false); y += 11;
            }
        }

        var enchants = stack.get(DataComponents.ENCHANTMENTS);
        if (enchants != null && !enchants.isEmpty()) {
            g.fill(x, y, x+w, y+1, withAlpha(SEPARATOR, ba)); y += 5;
            g.text(font, Component.literal("Enchantments"), x, y, withAlpha(LABEL, ba), false); y += 10;
            for (var e : enchants.entrySet()) {
                String raw   = e.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");
                String label = capitalize(raw.replace('_', ' '));
                int    lvl   = e.getIntValue();
                String text  = lvl > 1 ? label + " " + toRoman(lvl) : label;
                g.text(font, Component.literal(text), x, y, withAlpha(0xFFFFDD44, ba), false); y += 10;
            }
        }
        return y;
    }

    private static void drawStatDelta(GuiGraphicsExtractor g, Font font,
                                      int x, int y, int w,
                                      String label, String value, double delta,
                                      boolean showDelta, int ba) {
        g.text(font, Component.literal(label), x, y, withAlpha(LABEL, ba), false);
        if (showDelta && Math.abs(delta) > 0.001) {
            String ds    = String.format(delta > 0 ? "(+%.1f)" : "(%.1f)", delta);
            int    dc    = delta > 0 ? STAT_UP : STAT_DOWN;
            int    valX  = x + w - font.width(value) - font.width(ds) - 3;
            g.text(font, Component.literal(value), valX, y, withAlpha(VALUE, ba), false);
            g.text(font, Component.literal(ds), valX + font.width(value) + 3, y, withAlpha(dc, ba), false);
        } else {
            g.text(font, Component.literal(value), x+w-font.width(value), y, withAlpha(VALUE, ba), false);
        }
    }

    private static void drawStat(GuiGraphicsExtractor g, Font font,
                                 int x, int y, int w, String label, String value, int ba) {
        g.text(font, Component.literal(label), x, y, withAlpha(LABEL, ba), false);
        g.text(font, Component.literal(value), x+w-font.width(value), y, withAlpha(VALUE, ba), false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String getItemType(ItemStack stack) {
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        if (isSpecial(stack)) return "Special";
        if (isPotion(stack))  return "Potion";
        if (isFood(stack))    return "Food";
        if (isWeapon(stack)) {
            if (stack.is(zcylas.totality.init.ModTags.BOWS))      return "Bow";
            if (stack.is(zcylas.totality.init.ModTags.CROSSBOWS)) return "Crossbow";
            List<String> parts = new ArrayList<>();
            if (stack.is(zcylas.totality.init.ModTags.ONE_HANDED_WEAPONS)) parts.add("One-Handed");
            if (stack.is(zcylas.totality.init.ModTags.TWO_HANDED_WEAPONS)) parts.add("Two-Handed");
            if (stack.is(zcylas.totality.init.ModTags.THROWN_WEAPONS))     parts.add("Thrown");
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

    private static String getActionHint(ItemEntry entry) {
        ItemStack stack  = entry.stack;
        Player    player = Minecraft.getInstance().player;
        String    drop   = "   [Q] Drop  [R] Drop All";

        if (entry.slot == InventoryActionHandler.SLOT_OFFHAND) return "[E] Unequip (Offhand)" + drop;
        if (entry.slot == InventoryActionHandler.SLOT_ARMOR)   return "[E] Unequip (Armor)"   + drop;
        if (isPotion(stack) || isFood(stack))                  return "[E] Use" + drop;

        if (isArmor(stack)) {
            boolean eq = player != null && isInArmorSlot(player, stack);
            return (eq ? "[E] Unequip" : "[E] Equip") + drop;
        }
        if (isSpecial(stack) || isHandTool(stack)) {
            boolean m = player != null && isInMainhand(player, stack);
            return (m ? "[E] Unequip" : "[E] Equip") + drop;
        }
        if (isTwoHandedWeapon(stack)) {
            boolean m = player != null && isInMainhand(player, stack);
            return (m ? "[E] Unequip" : "[E] Equip") + drop;
        }
        if (isOneHandedWeapon(stack) || isThrownWeapon(stack)) {
            if (player != null) {
                boolean m = isInMainhand(player, stack);
                boolean o = isInOffhand(player, stack);
                if (m && o) return "[E] Unequip All" + drop;
                if (m)      return "[E] → Offhand"   + drop;
                if (o)      return "[E] Unequip"      + drop;
            }
            return "[E] Main  [RMB] Off" + drop;
        }
        if (isWeapon(stack)) return "[E] Equip" + drop;
        return "[Q] Drop  [R] Drop All";
    }

    private static String capitalize(String s) {
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

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV";
            case 5 -> "V"; case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII";
            case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(n);
        };
    }
}