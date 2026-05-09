package zcylas.totality.client.color;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import zcylas.totality.api.rpg.skills.alchemy.potions.PotionData;
import zcylas.totality.api.rpg.skills.alchemy.potions.PotionDataComponent;

/**
 * Reads the potion color from the POTION_DATA component on the stack.
 * Always returns ARGB.opaque() to match vanilla potion rendering behavior.
 */
public record PotionTintSource(int defaultColor) implements ItemTintSource {

    public static final PotionTintSource INSTANCE = new PotionTintSource(PotionData.COLOR_PURPLE);

    public static final MapCodec<PotionTintSource> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    net.minecraft.util.ExtraCodecs.RGB_COLOR_CODEC
                            .optionalFieldOf("default", PotionData.COLOR_PURPLE)
                            .forGetter(PotionTintSource::defaultColor)
            ).apply(i, PotionTintSource::new));

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        PotionData data = stack.get(PotionDataComponent.POTION_DATA);
        if (data != null) {
            return ARGB.opaque(data.color());
        }
        if (stack.getItem() instanceof zcylas.totality.item.potion.AlchemyPotionItem potion) {
            return ARGB.opaque(potion.getPotionData(stack).color());
        }
        return ARGB.opaque(defaultColor);
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}