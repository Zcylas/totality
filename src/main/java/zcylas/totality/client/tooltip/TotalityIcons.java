package zcylas.totality.client.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

public final class TotalityIcons {

    public static final Identifier FONT = Identifier.fromNamespaceAndPath("totality", "icons");

    public static final String FLAME = "\uE001";
    public static final String ENERGY = "\uE002";
    public static final String WEIGHT = "\uE003";
    public static final String DAMAGE     = "\uE004"; // add sword glyph to font texture
    public static final String PROPERTIES = "\uE005"; // add scroll/list glyph
    public static final String RANGE      = "\uE006"; // add arrow glyph
    public static final String STAMINA    = "\uE007"; // add sprint/lightning glyph


    public static Component icon(String glyph, int color) {
        return Component.literal(glyph)
                .withStyle(s -> s.withFont(new FontDescription.Resource(FONT)).withColor(color));
    }
    public static Component iconLabel(String glyph, int iconColor, int labelColor, String label) {
        return icon(glyph, iconColor).copy()
                .append(Component.literal(" " + label)
                        .withStyle(s -> s
                                .withColor(labelColor)
                                .withFont(FontDescription.DEFAULT)));
    }

    public static Component iconLabel(String glyph, int labelColor, String label) {
        return iconLabel(glyph, -1, labelColor, label); // uses natural texture color for icon
    }
    private TotalityIcons() {}
}