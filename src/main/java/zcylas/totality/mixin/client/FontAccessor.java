package zcylas.totality.mixin.client;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the font's glyph provider, so tooltip group icons can detect glyphs the active font lacks. */
@Mixin(Font.class)
public interface FontAccessor {
    @Accessor("provider")
    Font.Provider totality$provider();
}
