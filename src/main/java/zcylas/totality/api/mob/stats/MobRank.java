package zcylas.totality.api.mob.stats;

public enum MobRank {
    E(0xFF888888),
    D(0xFF44AA44),
    C(0xFF4488CC),
    B(0xFFAA44CC),
    A(0xFFCCAA00),
    S(0xFFCC4400);

    private final int color;
    MobRank(int color) { this.color = color; }
    public int getColor() { return color; }
}