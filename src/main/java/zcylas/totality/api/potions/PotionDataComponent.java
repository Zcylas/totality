package zcylas.totality.api.potions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.alchemy.AlchemyEffect;
import zcylas.totality.api.alchemy.AlchemyEffects;

import java.util.List;

/**
 * Stores PotionData on an AlchemyPotionItem stack so brewed potions
 * can have dynamic effects without needing individual item registrations.
 */
public final class PotionDataComponent {

    // Codec for EffectEntry
    private static final Codec<EffectEntry> EFFECT_ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.xmap(AlchemyEffect::get, AlchemyEffect::getId)
                    .fieldOf("effect").forGetter(EffectEntry::effect),
            Codec.FLOAT.fieldOf("magnitude").forGetter(EffectEntry::magnitude),
            Codec.INT.fieldOf("duration_ticks").forGetter(EffectEntry::durationTicks)
    ).apply(i, EffectEntry::new));

    // StreamCodec for EffectEntry
    private static final StreamCodec<RegistryFriendlyByteBuf, EffectEntry> EFFECT_ENTRY_STREAM =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC.map(AlchemyEffect::get, AlchemyEffect::getId),
                    EffectEntry::effect,
                    ByteBufCodecs.FLOAT,
                    EffectEntry::magnitude,
                    ByteBufCodecs.INT,
                    EffectEntry::durationTicks,
                    EffectEntry::new
            );

    // Codec for PotionData
    public static final Codec<PotionData> POTION_DATA_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("display_name").forGetter(PotionData::displayName),
            Codec.INT.fieldOf("color").forGetter(PotionData::color),
            EFFECT_ENTRY_CODEC.listOf().fieldOf("effects").forGetter(PotionData::effects),
            Codec.BOOL.fieldOf("is_poison").forGetter(PotionData::isPoison)
    ).apply(i, PotionData::new));

    // StreamCodec for PotionData
    public static final StreamCodec<RegistryFriendlyByteBuf, PotionData> POTION_DATA_STREAM =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    PotionData::displayName,
                    ByteBufCodecs.INT,
                    PotionData::color,
                    EFFECT_ENTRY_STREAM.apply(ByteBufCodecs.list()),
                    PotionData::effects,
                    ByteBufCodecs.BOOL,
                    PotionData::isPoison,
                    PotionData::new
            );

    public static final DataComponentType<PotionData> POTION_DATA =
            DataComponentType.<PotionData>builder()
                    .persistent(POTION_DATA_CODEC)
                    .networkSynchronized(POTION_DATA_STREAM)
                    .build();

    public static void register() {
        Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, "potion_data"),
                POTION_DATA
        );
    }

    private PotionDataComponent() {}
}