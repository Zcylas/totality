package zcylas.totality.client.renderer.mana;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import zcylas.totality.Totality;
import zcylas.totality.item.magic.GrimoireItem;
import zcylas.totality.networking.magic.grimoire.ClientGrimoireHudManager;
import zcylas.totality.networking.mana.ClientManaManager;

public class ManaHudRenderer {
    public static final Identifier MANA_HUD_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "mana_hud");

    public static boolean showDebugNumbers = true;

    public static void register() {
        HudElementRegistry.addLast(MANA_HUD_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.options.hideGui) return;

            int mana    = ClientManaManager.getMana();
            int maxMana = ClientManaManager.getMaxMana();
            if (maxMana == 0) return;

            boolean holdingGrimoire =
                    client.player.getMainHandItem().getItem() instanceof GrimoireItem
                            || client.player.getOffhandItem().getItem() instanceof GrimoireItem;

            boolean manaNotFull = mana < maxMana;

            if (!holdingGrimoire && !manaNotFull) return;

            int screenHeight = context.guiHeight();

            // Bar dimensions — bottom left, like AN
            int barWidth  = 96;
            int barHeight = 5;
            int x = 10;
            int y = screenHeight - 20; // above hotbar

            // ── Spell name above bar (only when holding grimoire) ──
            if (holdingGrimoire) {
                String spellName = ClientGrimoireHudManager.getSpellName();
                int slot = ClientGrimoireHudManager.getCurrentSlot() + 1;
                String display = spellName.isEmpty() || spellName.equals("Unnamed")
                        ? String.valueOf(slot)
                        : slot + " " + spellName;
                context.text(client.font, display, x, y - 12, 0xFFFFFFFF, true);
            }

            // ── Background ──
            context.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0xFF111122);

            // ── Fill ──
            int filledWidth = (int)(Mth.clamp(mana / (float) maxMana, 0f, 1f) * barWidth);
            context.fill(x, y, x + filledWidth, y + barHeight, 0xFF4455FF);

            // ── Highlight line ──
            if (filledWidth > 0) {
                context.fill(x, y, x + filledWidth, y + 1, 0xFF8899FF);
            }

            // ── Border ──
            context.fill(x - 1, y - 1, x + barWidth + 1, y,                        0xFF223366);
            context.fill(x - 1, y + barHeight, x + barWidth + 1, y + barHeight + 1, 0xFF223366);
            context.fill(x - 1, y - 1, x,               y + barHeight + 1,          0xFF223366);
            context.fill(x + barWidth, y - 1, x + barWidth + 1, y + barHeight + 1,  0xFF223366);

            // ── Debug numbers ──
            if (showDebugNumbers) {
                String text = mana + " / " + maxMana;
                context.text(client.font, text,
                        x + (barWidth - client.font.width(text)) / 2,
                        y + barHeight + 3, 0xFFAAAACC, true);
            }
        });
    }
}