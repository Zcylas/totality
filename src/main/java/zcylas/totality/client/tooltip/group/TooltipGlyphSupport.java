package zcylas.totality.client.tooltip.group;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import zcylas.totality.client.tooltip.renderer.TooltipGroupHeadingPainter;
import zcylas.totality.mixin.client.FontAccessor;

import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Whether the active default font can render a code point — i.e. it resolves to a real glyph rather than
 * the missing-glyph box. Used to pick a group icon's fallback when a resource pack or font change drops
 * its symbol. The lookup goes through the font's own glyph cache, so it is cheap and always reflects the
 * currently loaded fonts (no stale cache of our own across resource reloads).
 */
public final class TooltipGlyphSupport {

    public static boolean renderable(Font font, int codepoint) {
        return ((FontAccessor) font).totality$provider().glyphs(FontDescription.DEFAULT)
                .getGlyph(codepoint).info() != SpecialGlyphs.MISSING;
    }

    /** The glyph text to draw for {@code icon} with {@code font}: its symbol, or its fallback if the symbol is missing. */
    public static String resolve(Font font, TooltipGroupIcon.Glyph icon) {
        return icon.resolve(cp -> renderable(font, cp));
    }

    /** Whether a texture resource exists in the currently loaded resources (mod assets and resource packs). */
    public static boolean textureAvailable(Identifier texture) {
        return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
    }

    /**
     * What a group icon actually draws: a texture that exists, else its glyph fallback; a glyph icon's symbol,
     * else its fallback. Never the missing-texture checkerboard or a missing-glyph box.
     */
    public static TooltipGroupHeadingPainter.DrawnIcon resolveIcon(Font font, TooltipGroupIcon icon) {
        return resolveIcon(icon, TooltipGlyphSupport::textureAvailable, cp -> renderable(font, cp));
    }

    /** Pure resolution against the given availability checks — unit-tested. */
    public static TooltipGroupHeadingPainter.DrawnIcon resolveIcon(TooltipGroupIcon icon, Predicate<Identifier> textureExists,
                                                                   IntPredicate glyphRenderable) {
        return switch (icon) {
            case TooltipGroupIcon.Texture t -> textureExists.test(t.texture())
                    ? TooltipGroupHeadingPainter.DrawnIcon.texture(t.texture())
                    : TooltipGroupHeadingPainter.DrawnIcon.glyph(t.fallback().resolve(glyphRenderable));
            case TooltipGroupIcon.Glyph g -> TooltipGroupHeadingPainter.DrawnIcon.glyph(g.resolve(glyphRenderable));
        };
    }

    private TooltipGlyphSupport() {}
}
