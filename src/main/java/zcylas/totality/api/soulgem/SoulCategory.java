package zcylas.totality.api.soulgem;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The soul classification family a Soul Gem's {@link SoulGemAcceptanceRule} checks against. Only
 * {@link #ORDINARY} is usable in this foundation pass — Black/sapient/undead/boss/divine categories
 * are intentionally not designed yet. Adding one later is meant to be a pure enum addition; it must
 * not require changing {@code SoulGemItem}, {@link SoulGemAcceptanceRule}'s shape, or {@code
 * CapturedSoulComponent}'s codec. See
 * {@code TOTALITY_SOUL_GEM_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-17.md} §7.
 */
public enum SoulCategory implements StringRepresentable {
    ORDINARY;

    public static final Codec<SoulCategory> CODEC = StringRepresentable.fromEnum(SoulCategory::values);

    public static final StreamCodec<ByteBuf, SoulCategory> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(
            s -> SoulCategory.valueOf(s.toUpperCase(Locale.ROOT)),
            SoulCategory::getSerializedName);

    @Override
    public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
}
