package zcylas.totality.api.mana.base;

import net.minecraft.world.entity.player.Player;

public class ManaRegenCalcEvent {
    private final Player player;
    private float regenPercent;

    public ManaRegenCalcEvent(Player player, float regenPercent) {
        this.player = player;
        this.regenPercent = regenPercent;
    }

    public Player getPlayer() { return player; }
    public float getRegenPercent() { return regenPercent; }
    public void setRegenPercent(float regenPercent) {
        this.regenPercent = regenPercent;
    }
}
