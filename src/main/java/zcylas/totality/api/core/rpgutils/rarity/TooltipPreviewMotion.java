package zcylas.totality.api.core.rpgutils.rarity;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

import java.util.function.IntFunction;

/**
 * Tooltip presentation vocabulary (see {@link TooltipProfileComponent}): Whether the large header preview moves. {@code AUTO} lets the client choose from the resolved preview
 * mode (flat sprites stay still); {@code SLOW_ROTATE} is a calm display-turntable rotation, never a fast
 * spin or any other animation.
 * Presentation only — never what the item's stats or content are.
 */
public enum TooltipPreviewMotion implements StringRepresentable {
    AUTO, STATIC, SLOW_ROTATE;

    public static final Codec<TooltipPreviewMotion> CODEC = StringRepresentable.fromEnum(TooltipPreviewMotion::values);
    private static final IntFunction<TooltipPreviewMotion> BY_ID =
            ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, TooltipPreviewMotion> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}
