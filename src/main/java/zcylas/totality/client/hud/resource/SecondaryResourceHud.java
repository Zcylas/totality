package zcylas.totality.client.hud.resource;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

import java.util.List;

public final class SecondaryResourceHud {

    public static final Identifier HUD_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "secondary_resource");

    private static final int PIP_SZ  = 8;
    private static final int PIP_GAP = 2;
    private static final int ROW_GAP = 3;

    public static void register() {
        HudElementRegistry.addLast(HUD_ID, (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.options.hideGui) return;

            List<ISecondaryResource> active = SecondaryResourceRegistry.all()
                    .stream()
                    .filter(r -> r.shouldShow(client) && r.getMax(client) > 0)
                    .toList();

            if (active.isEmpty()) return;

            int screenW = client.getWindow().getGuiScaledWidth();
            int screenH = client.getWindow().getGuiScaledHeight();

            // Right-aligned, below hunger bar
            int rightX = screenW / 2 + 91;
            int baseY  = screenH - 55; // just above health/hunger cluster for now

            for (ISecondaryResource resource : active) {
                int current = resource.getCurrent(client);
                int max     = resource.getMax(client);
                int color   = resource.getColor();
                int dim     = (color & 0x00FFFFFF) | 0x33000000;

                if (resource.getDisplayType() == ISecondaryResource.DisplayType.PIPS) {
                    int totalW = max * (PIP_SZ + PIP_GAP) - PIP_GAP;
                    int pipX   = rightX - totalW;

                    for (int i = 0; i < max; i++) {
                        boolean filled = i < current;
                        if (filled) {
                            graphics.fill(pipX, baseY,
                                    pipX + PIP_SZ, baseY + PIP_SZ, color);
                        } else {
                            graphics.fill(pipX, baseY,
                                    pipX + PIP_SZ, baseY + PIP_SZ, 0x22FFFFFF);
                            // outline
                            graphics.fill(pipX,           baseY,           pipX + PIP_SZ, baseY + 1,           dim);
                            graphics.fill(pipX,           baseY + PIP_SZ - 1, pipX + PIP_SZ, baseY + PIP_SZ, dim);
                            graphics.fill(pipX,           baseY,           pipX + 1,      baseY + PIP_SZ,    dim);
                            graphics.fill(pipX + PIP_SZ - 1, baseY,       pipX + PIP_SZ, baseY + PIP_SZ,    dim);
                        }
                        pipX += PIP_SZ + PIP_GAP;
                    }
                } else {
                    // BAR display (Solar Charge etc.)
                    int barW = 81;
                    int barH = 5;
                    int barX = rightX - barW;
                    int fill = max > 0 ? (int)((float) current / max * barW) : 0;

                    graphics.fill(barX, baseY, barX + barW, baseY + barH, 0x44000000);
                    if (fill > 0)
                        graphics.fill(barX, baseY, barX + fill, baseY + barH, color);
                    graphics.fill(barX, baseY, barX + barW, baseY + 1, dim);
                    graphics.fill(barX, baseY + barH - 1, barX + barW, baseY + barH, dim);
                    baseY -= barH + ROW_GAP;
                    continue; // skip pips baseY adjustment
                }

                baseY -= PIP_SZ + ROW_GAP;
            }
        });
    }

    private SecondaryResourceHud() {}
}