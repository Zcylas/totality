package zcylas.totality.api.core.rpgutils.rarity;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

import java.util.function.IntFunction;

/**
 * Tooltip presentation vocabulary (see {@link TooltipProfileComponent}): How the large header preview presents the item. {@code AUTO} lets the client infer a sensible mode
 * from the item's own GUI model (flat sprite vs. 3D model vs. placeable block); any other value is an
 * explicit authoring choice that always wins over inference.
 * Presentation only — never what the item's stats or content are.
 */
public enum TooltipPreviewMode implements StringRepresentable {
    AUTO, SPRITE, ITEM_MODEL, BLOCK_MODEL;

    public static final Codec<TooltipPreviewMode> CODEC = StringRepresentable.fromEnum(TooltipPreviewMode::values);
    private static final IntFunction<TooltipPreviewMode> BY_ID =
            ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, TooltipPreviewMode> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}
