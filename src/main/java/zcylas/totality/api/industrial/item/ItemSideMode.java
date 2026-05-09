package zcylas.totality.api.industrial.item;

public enum ItemSideMode {
    INPUT(0xFF0000, "Input"),
    OUTPUT(0x00AA00, "Output"),
    BOTH(0x0055FF, "Both"),
    NONE(0x808080, "None");

    private final int color;
    private final String label;

    ItemSideMode(int color, String label) {
        this.color = color;
        this.label = label;
    }

    public int getColor()  { return color; }
    public String getLabel() { return label; }

    public ItemSideMode next() {
        return switch (this) {
            case INPUT  -> OUTPUT;
            case OUTPUT -> BOTH;
            case BOTH   -> NONE;
            case NONE   -> INPUT;
        };
    }

    public boolean allowsInsertion()  { return this == INPUT  || this == BOTH; }
    public boolean allowsExtraction() { return this == OUTPUT || this == BOTH; }
}