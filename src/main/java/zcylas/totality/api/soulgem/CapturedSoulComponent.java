package zcylas.totality.api.soulgem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.mob.stats.MobRank;

/**
 * The persistent + networked ItemStack data component storing the {@link CapturedSoul} inside a
 * filled Soul Gem. Absent = the gem is empty. Following {@code PotionDataComponent}'s established
 * pattern in this repo. See
 * {@code TOTALITY_SOUL_GEM_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-17.md} §9-10, §16.
 */
public final class CapturedSoulComponent {

    public static final Identifier CAPTURED_SOUL_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "captured_soul");

    private static final Codec<MobRank> MOB_RANK_CODEC =
            Codec.STRING.xmap(MobRank::fromId, MobRank::getId);
    private static final StreamCodec<ByteBuf, MobRank> MOB_RANK_STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(MobRank::fromId, MobRank::getId);

    public static final Codec<CapturedSoul> CAPTURED_SOUL_CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("soul_instance_id").forGetter(CapturedSoul::soulInstanceId),
            MOB_RANK_CODEC.fieldOf("rank").forGetter(CapturedSoul::rank),
            SoulCategory.CODEC.fieldOf("category").forGetter(CapturedSoul::category),
            Identifier.CODEC.fieldOf("source_entity_type").forGetter(CapturedSoul::sourceEntityType)
    ).apply(i, CapturedSoul::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CapturedSoul> CAPTURED_SOUL_STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, CapturedSoul::soulInstanceId,
                    MOB_RANK_STREAM_CODEC, CapturedSoul::rank,
                    SoulCategory.STREAM_CODEC, CapturedSoul::category,
                    Identifier.STREAM_CODEC, CapturedSoul::sourceEntityType,
                    CapturedSoul::new
            );

    public static final DataComponentType<CapturedSoul> CAPTURED_SOUL =
            DataComponentType.<CapturedSoul>builder()
                    .persistent(CAPTURED_SOUL_CODEC)
                    .networkSynchronized(CAPTURED_SOUL_STREAM_CODEC)
                    .build();

    public static void register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CAPTURED_SOUL_ID, CAPTURED_SOUL);
    }

    private CapturedSoulComponent() {}
}
