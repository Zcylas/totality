package zcylas.totality.api.magic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import zcylas.totality.api.magic.formula.ArcaneFormula;
import zcylas.totality.api.magic.rune.AbstractRune;

/**
 * The DataComponent stored on a GrimoireItem.
 * Holds the current formula and the active slot index.
 */
public record GrimoireCaster(ArcaneFormula formula, int currentSlot, String spellName) {

    public static final GrimoireCaster EMPTY =
            new GrimoireCaster(ArcaneFormula.EMPTY, 0, "");

    public static final Codec<GrimoireCaster> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ArcaneFormula.CODEC.fieldOf("formula").forGetter(GrimoireCaster::formula),
                    Codec.INT.optionalFieldOf("slot", 0).forGetter(GrimoireCaster::currentSlot),
                    Codec.STRING.optionalFieldOf("name", "").forGetter(GrimoireCaster::spellName)
            ).apply(instance, GrimoireCaster::new));

    public static final StreamCodec<FriendlyByteBuf, GrimoireCaster> STREAM_CODEC =
            StreamCodec.composite(
                    ArcaneFormula.STREAM_CODEC, GrimoireCaster::formula,
                    ByteBufCodecs.INT, GrimoireCaster::currentSlot,
                    ByteBufCodecs.STRING_UTF8, GrimoireCaster::spellName,
                    GrimoireCaster::new);

    public GrimoireCaster withFormula(ArcaneFormula formula) {
        return new GrimoireCaster(formula, currentSlot, spellName);
    }

    public GrimoireCaster withName(String name) {
        return new GrimoireCaster(formula, currentSlot, name);
    }

    public GrimoireCaster addRune(AbstractRune rune) {
        return new GrimoireCaster(formula.add(rune), currentSlot, spellName);
    }

    public GrimoireCaster removeRune(int index) {
        return new GrimoireCaster(formula.remove(index), currentSlot, spellName);
    }

    public void saveToStack(net.minecraft.world.item.ItemStack stack,
                            net.minecraft.core.component.DataComponentType<GrimoireCaster> type) {
        stack.set(type, this);
    }
}