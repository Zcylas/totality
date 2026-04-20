package zcylas.totality.api.magic.rune;

import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

import java.util.Objects;

public abstract class AbstractRune {

    private final Identifier id;
    private final String name;

    public AbstractRune(String id, String name) {
        this.id = Identifier.fromNamespaceAndPath(Totality.MOD_ID, id);
        this.name = name;
    }

    public Identifier getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * The mana cost of this rune when used in a formula.
     */
    public abstract int getManaCost();

    /**
     * 1 = Form, 2 = Effect, 3 = Augment — used for ordering in the GUI later.
     */
    public abstract int getTypeIndex();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractRune that = (AbstractRune) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}