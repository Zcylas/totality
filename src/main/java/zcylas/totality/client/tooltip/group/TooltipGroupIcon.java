package zcylas.totality.client.tooltip.group;

import net.minecraft.resources.Identifier;

import java.util.function.IntPredicate;

/**
 * The icon of a semantic body group, drawn at the start of its centred heading. Part of the group's
 * registration ({@link TooltipGroup}), never of an item instance.
 * <ul>
 *   <li>{@link Texture} — an ordinary texture resource (e.g. {@code totality:textures/gui/tooltip/groups/combat.png})
 *       drawn in its own authored colors, never recolored by rarity. The path is the group's stable icon identity:
 *       replacing the PNG, or overriding it from a resource pack, changes the icon with no code change;</li>
 *   <li>{@link Glyph} — a font symbol, tinted with the item's resolved rarity color like the heading text.
 *       Kept for integrations without art, and as every texture icon's fallback.</li>
 * </ul>
 * Distinct from stat icons (the {@code totality:icons} font glyphs and item/effect sprites of stat rows),
 * which keep their own colors and are untouched by this.
 */
public sealed interface TooltipGroupIcon {

    /** Whether the heading tints this icon with the rarity color. */
    boolean tinted();

    /**
     * A Unicode symbol in the default font, with a plain fallback used when the active font cannot
     * render the symbol (so a heading never shows a missing-glyph box).
     */
    record Glyph(String symbol, String fallback) implements TooltipGroupIcon {
        public Glyph {
            if (symbol == null || symbol.isEmpty()) throw new IllegalArgumentException("symbol must not be empty");
            if (fallback == null || fallback.isEmpty()) throw new IllegalArgumentException("fallback must not be empty");
        }

        /** The symbol if every one of its code points is renderable, otherwise the fallback. */
        public String resolve(IntPredicate renderable) {
            return symbol.codePoints().allMatch(renderable) ? symbol : fallback;
        }

        @Override
        public boolean tinted() {
            return true;
        }
    }

    /**
     * A texture resource drawn at {@link #SIZE}×{@link #SIZE} GUI pixels in its own colors, whatever its
     * resolution. When the resource does not exist (e.g. removed by a resource pack), {@link #fallback} is drawn
     * instead, so a heading never shows the missing-texture checkerboard.
     */
    record Texture(Identifier texture, Glyph fallback) implements TooltipGroupIcon {
        /**
         * Drawn size in GUI pixels. 8 keeps the icon within the 9 px heading row, and maps a 16 px texture to exact
         * whole screen pixels at GUI scales 1, 2 and 4 (0.5, 1 and 2 screen pixels per texel), so it stays sharp.
         */
        public static final int SIZE = 8;

        public Texture {
            if (texture == null) throw new IllegalArgumentException("texture must not be null");
            if (fallback == null) throw new IllegalArgumentException("fallback must not be null");
        }

        @Override
        public boolean tinted() {
            return false;
        }
    }
}
