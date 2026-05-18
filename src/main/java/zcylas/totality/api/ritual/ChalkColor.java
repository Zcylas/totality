package zcylas.totality.api.ritual;

import net.minecraft.util.StringRepresentable;

public enum ChalkColor implements StringRepresentable {
    WHITE("white", 0xC8C8C8),
    GOLD("gold", 0xC8A83C),
    BLUE("blue", 0x3A5FAA),
    PURPLE("purple", 0x7A3A9A),
    RED("red", 0x9A2020);

    private final String name;
    private final int tint;

    ChalkColor(String name, int tint) {
        this.name = name;
        this.tint = tint;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public int getTint() {
        return tint;
    }
}