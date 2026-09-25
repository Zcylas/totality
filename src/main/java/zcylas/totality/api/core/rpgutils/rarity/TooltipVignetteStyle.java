package zcylas.totality.api.core.rpgutils.rarity;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

import java.util.function.IntFunction;

/**
 * Tooltip presentation vocabulary (see {@link TooltipProfileComponent}): Geometry of the whole-tooltip vignette. Its color always comes from the resolved rarity; this only
 * selects the shape.
 * Presentation only — never what the item's stats or content are.
 */
public enum TooltipVignetteStyle implements StringRepresentable {
    RADIAL, BOTTOM_GLOW, DIAGONAL_SWEEP, EDGE_FRAME;

    public static final Codec<TooltipVignetteStyle> CODEC = StringRepresentable.fromEnum(TooltipVignetteStyle::values);
    private static final IntFunction<TooltipVignetteStyle> BY_ID =
            ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, TooltipVignetteStyle> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}
