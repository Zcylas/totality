package zcylas.totality.client.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

public final class TotalityIcons {

    public static final Identifier FONT = Identifier.fromNamespaceAndPath("totality", "icons");

    public static final String FLAME = "\uE001";
    public static final String ENERGY = "\uE002";
    public static final String WEIGHT = "\uE003";

    public static Component icon(String glyph, int color) {
        return Component.literal(glyph)
                .withStyle(s -> s.withFont(new FontDescription.Resource(FONT)).withColor(color));
    }

    private TotalityIcons() {}
}