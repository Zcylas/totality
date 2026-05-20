package zcylas.totality.api.ritual;

import net.minecraft.util.StringRepresentable;

public enum ChalkColor implements StringRepresentable {
    WHITE("white",  0xFFE8E8E8),
    GOLD("gold",    0xFFD4A030),
    BLUE("blue",    0xFF4A6AC8),
    PURPLE("purple",0xFF8844BB),
    RED("red",      0xFFAA3828),
    RESIDUUM("residuum", 0xFF2D8A4E);

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