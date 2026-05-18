package zcylas.totality.api.ritual;

import net.minecraft.util.StringRepresentable;

public enum ChalkSigil implements StringRepresentable {
    FOCUS("focus_sigil"),
    PLACEHOLDER("placeholder_sigil");

    private final String name;

    ChalkSigil(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public ChalkSigil next() {
        return values()[(this.ordinal() + 1) % values().length];
    }
}