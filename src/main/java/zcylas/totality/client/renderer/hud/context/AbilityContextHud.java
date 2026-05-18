// AbilityContextHud.java
package zcylas.totality.client.renderer.hud.context;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.networking.ability.ClientAbilityManager;

public class AbilityContextHud {

    private static final int COLOR_LABEL   = 0xFFCCCCCC;
    private static final int COLOR_KEY     = 0xFF00CCFF;
    private static final int COLOR_BRACKET = 0xFF5599BB;

    public static void render(GuiGraphicsExtractor graphics, Minecraft client,
                              int screenW, int screenH) {
        if (client.player == null || client.level == null) return;
        if (client.screen != null) return;

        // Only show prompt for the equipped ability
        var equippedId = ClientAbilityManager.getEquippedAbility();
        if (equippedId == null) return;
        if (ClientAbilityManager.isOnCooldown(equippedId)) return;

        Ability ability = AbilityRegistry.get(equippedId);
        if (ability == null || ability.getType() == Ability.Type.PASSIVE) return;

        AbilityContext context = ability.getContext(client, client.player);
        if (context == null) return;

        renderPrompt(graphics, client, screenW, screenH, context.promptLabel());
    }

    private static void renderPrompt(GuiGraphicsExtractor graphics, Minecraft client,
                                     int screenW, int screenH, String label) {
        String bracket1 = "[";
        String key      = "Z";
        String bracket2 = "] ";

        int b1w    = client.font.width(bracket1);
        int kw     = client.font.width(key);
        int b2w    = client.font.width(bracket2);
        int lw     = client.font.width(label);
        int totalW = b1w + kw + b2w + lw;

        int x = (screenW - totalW) / 2;
        int y = screenH / 2 + 16;

        graphics.text(client.font, bracket1, x,              y, COLOR_BRACKET, true);
        graphics.text(client.font, key,      x + b1w,        y, COLOR_KEY,     true);
        graphics.text(client.font, bracket2, x + b1w + kw,   y, COLOR_BRACKET, true);
        graphics.text(client.font, label,    x + b1w + kw + b2w, y, COLOR_LABEL, true);
    }

    private AbilityContextHud() {}
}