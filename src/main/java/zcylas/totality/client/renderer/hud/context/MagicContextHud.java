package zcylas.totality.client.renderer.hud.context;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.magic.grimoire.GrimoireCaster;
import zcylas.totality.api.magic.grimoire.MagicComponents;
import zcylas.totality.item.magic.GrimoireItem;

public class MagicContextHud {

    /**
     * Renders all magic-related contextual HUD elements.
     * Called every frame by TotalityHudRenderer.
     */
    public static void render(GuiGraphicsExtractor graphics, Minecraft client, int screenW, int screenH) {
        if (client.player == null) return;

        renderGrimoireDisplay(graphics, client, screenW, screenH);

        // TODO: render active spell effects / buffs here
        // TODO: render arcane focus info here
    }

    /**
     * Shows the currently active spell slot and spell name when holding a Grimoire.
     * Positioned centered between the XP number and item name popup.
     */
    private static void renderGrimoireDisplay(GuiGraphicsExtractor graphics, Minecraft client, int screenW, int screenH) {
        ItemStack main = client.player.getMainHandItem();
        ItemStack off  = client.player.getOffhandItem();

        boolean holdingGrimoire = main.getItem() instanceof GrimoireItem
                || off.getItem() instanceof GrimoireItem;

        if (!holdingGrimoire) return;

        ItemStack grimStack = main.getItem() instanceof GrimoireItem ? main : off;

        GrimoireCaster caster = grimStack.getOrDefault(
                MagicComponents.GRIMOIRE_CASTER, GrimoireCaster.EMPTY);

        int slot = caster.currentSlot() + 1;
        String spellName = caster.spellName();
        String display = spellName.isEmpty() ? "Slot " + slot : slot + "  " + spellName;

        int textW = client.font.width(display);
        int textX = (screenW - textW) / 2;
        int textY = screenH - 48;

        graphics.text(client.font, display, textX, textY, 0xFFAAAAFF, true);
    }

    private MagicContextHud() {}
}