package zcylas.totality.api.config;

import net.minecraft.ChatFormatting;

public enum FaceConfig {

    DISABLED(0x808080, "Disabled"),   // grey
    INPUT(0xFF0000, "Input"),          // red
    OUTPUT(0x00AA00, "Output"),        // green
    BOTH(0xFFAA00, "Both");            // yellow/orange

    private final int color;
    private final String label;

    FaceConfig(int color, String label) {
        this.color = color;
        this.label = label;
    }

    public int getColor() { return color; }
    public String getLabel() { return label; }

    public FaceConfig next() {
        FaceConfig[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public boolean allowsInput() { return this == INPUT || this == BOTH; }
    public boolean allowsOutput() { return this == OUTPUT || this == BOTH; }
}