package zcylas.totality.api.magic.formula;

import com.mojang.serialization.Codec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import zcylas.totality.api.magic.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.rune.AbstractFormRune;
import zcylas.totality.api.magic.rune.AbstractRune;
import zcylas.totality.init.magic.RuneRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable ordered sequence of runes stored on a GrimoireItem.
 * Pattern: [Form] -> [Effect(s)] -> [Augment(s)]
 */
public final class ArcaneFormula {

    public static final ArcaneFormula EMPTY = new ArcaneFormula(List.of());

    // Serialize by ResourceLocation string list
    public static final Codec<ArcaneFormula> CODEC =
            net.minecraft.resources.Identifier.CODEC
                    .listOf()
                    .xmap(
                            ids -> new ArcaneFormula(
                                    ids.stream()
                                            .map(RuneRegistry::get)
                                            .filter(r -> r != null)
                                            .toList()),
                            formula -> formula.runes.stream()
                                    .map(AbstractRune::getId)
                                    .toList()
                    );

    public static final StreamCodec<FriendlyByteBuf, ArcaneFormula> STREAM_CODEC =
            StreamCodec.of(
                    (buf, formula) -> {
                        buf.writeInt(formula.runes.size());
                        formula.runes.forEach(r -> buf.writeIdentifier(r.getId()));
                    },
                    buf -> {
                        int size = buf.readInt();
                        List<AbstractRune> runes = new ArrayList<>();
                        for (int i = 0; i < size; i++) {
                            AbstractRune r = RuneRegistry.get(buf.readIdentifier());
                            if (r != null) runes.add(r);
                        }
                        return new ArcaneFormula(runes);
                    }
            );

    private final List<AbstractRune> runes;

    public ArcaneFormula(List<AbstractRune> runes) {
        this.runes = List.copyOf(runes);
    }

    public List<AbstractRune> getRunes() {
        return runes;
    }

    public boolean isEmpty() {
        return runes.isEmpty();
    }

    public int size() {
        return runes.size();
    }

    /**
     * Returns the Form rune, or null if the formula has none.
     */
    public AbstractFormRune getForm() {
        if (!runes.isEmpty() && runes.get(0) instanceof AbstractFormRune form)
            return form;
        return null;
    }

    /**
     * Returns augments that follow the rune at startIndex.
     */
    public List<AbstractAugmentRune> getAugments(int startIndex) {
        List<AbstractAugmentRune> augments = new ArrayList<>();
        for (int i = startIndex + 1; i < runes.size(); i++) {
            if (runes.get(i) instanceof AbstractAugmentRune aug)
                augments.add(aug);
            else
                break;
        }
        return augments;
    }

    /**
     * Total mana cost — sum of all rune costs.
     */
    public int getCost() {
        return runes.stream().mapToInt(AbstractRune::getManaCost).sum();
    }

    /**
     * Returns a new formula with the given rune appended.
     */
    public ArcaneFormula add(AbstractRune rune) {
        List<AbstractRune> newList = new ArrayList<>(runes);
        newList.add(rune);
        return new ArcaneFormula(newList);
    }

    /**
     * Returns a new formula with the rune at index removed.
     */
    public ArcaneFormula remove(int index) {
        List<AbstractRune> newList = new ArrayList<>(runes);
        newList.remove(index);
        return new ArcaneFormula(newList);
    }

    public boolean isValid() {
        return !isEmpty() && getForm() != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArcaneFormula that)) return false;
        return runes.equals(that.runes);
    }

    @Override
    public int hashCode() {
        return runes.hashCode();
    }
}