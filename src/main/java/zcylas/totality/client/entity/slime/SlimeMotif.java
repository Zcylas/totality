package zcylas.totality.client.entity.slime;

import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * An element's surface details for the Small Slime: a 128x128 RGBA mask on the shared texture layout
 * ({@code textures/entity/slime_test/motifs/<element>.png}, overridable by resource packs), composited into the
 * generated body texture at resource load (see {@link SlimePaletteMapper}). Per texel, where alpha is not 0:
 * <ul>
 *   <li>red: tone shift added to the gray value before the palette, (red - 128) / 255 (patches, spots, mottling);</li>
 *   <li>green: height offset for the secondary ramp, (green - 128) / 255 (irregular moss edge, drippy snow cap);</li>
 *   <li>blue: how much of {@link #overlayColour} to lay over the result, blue / 255 (snowflakes, spores, lines).</li>
 * </ul>
 */
public record SlimeMotif(String element, int overlayColour) {

    public Identifier mask() {
        return Identifier.fromNamespaceAndPath(Totality.MOD_ID, "textures/entity/slime_test/motifs/" + this.element + ".png");
    }
}
