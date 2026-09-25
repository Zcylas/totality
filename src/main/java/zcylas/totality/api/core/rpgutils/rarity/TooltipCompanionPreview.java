package zcylas.totality.api.core.rpgutils.rarity;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

import java.util.function.IntFunction;

/**
 * Tooltip presentation vocabulary (see {@link TooltipProfileComponent}): Optional second visual card shown beside the main tooltip. Opt-in only: items default to
 * {@code NONE}; armor/clothing may author {@code EQUIPPED_PLAYER} to show the local player wearing it.
 * Presentation only — never what the item's stats or content are.
 */
public enum TooltipCompanionPreview implements StringRepresentable {
    NONE, EQUIPPED_PLAYER;

    public static final Codec<TooltipCompanionPreview> CODEC = StringRepresentable.fromEnum(TooltipCompanionPreview::values);
    private static final IntFunction<TooltipCompanionPreview> BY_ID =
            ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, TooltipCompanionPreview> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}
