package zcylas.totality.api.ritual;

import net.minecraft.util.StringRepresentable;

public enum ChalkGlyph implements StringRepresentable {
    CONDUIT("conduit_glyph"),
    INVOCATION("invocation_glyph"),
    BINDING("binding_glyph");

    private final String name;

    ChalkGlyph(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public ChalkGlyph next() {
        return values()[(this.ordinal() + 1) % values().length];
    }
}