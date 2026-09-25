package zcylas.totality.api.core.rpgutils.rarity;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

import java.util.function.IntFunction;

/**
 * Tooltip presentation vocabulary (see {@link TooltipProfileComponent}): Style of the single divider between the identity header and the tooltip body. Its color comes from the
 * resolved rarity; this only selects the shape.
 * Presentation only — never what the item's stats or content are.
 */
public enum TooltipDividerStyle implements StringRepresentable {
    GRADIENT, ORNAMENT, GRADIENT_ORNAMENT, NONE;

    public static final Codec<TooltipDividerStyle> CODEC = StringRepresentable.fromEnum(TooltipDividerStyle::values);
    private static final IntFunction<TooltipDividerStyle> BY_ID =
            ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, TooltipDividerStyle> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}
