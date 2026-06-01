// api/dice/DiceSkin.java
package zcylas.totality.api.dice;

import net.minecraft.resources.Identifier;

/**
 * Visual skin for the dice roll die.
 * Default matches BG3's dark steel/crystal aesthetic.
 */
public record DiceSkin(
        Identifier id,
        String     displayName,
        int        outerColor,       // darker face color (side/back faces)
        int        centerColor,      // lighter face color (front face)
        int        borderColor,      // outer hexagon border
        int        facetColor,       // inner face division lines
        int        hoverBorderColor  // border when hovered in IDLE
) {
    public static final DiceSkin DEFAULT = new DiceSkin(
            Identifier.fromNamespaceAndPath("totality", "default"),
            "Default",
            0xFF3A3860,  // outer — dark blue-purple (side faces)
            0xFF8080C0,  // center — medium lavender (front face, luminous)
            0xFF181530,  // border — near-black dark blue
            0xFF252248,  // facet — dark purple facet lines
            0xFFAAAAE0   // hover — bright lavender
    );
}