package zcylas.totality.api.rpg.stamina.base;

import net.minecraft.world.entity.player.Player;

public class StaminaRegenCalcEvent {
    private final Player player;
    private float regenPercent;

    public StaminaRegenCalcEvent(Player player, float regenPercent) {
        this.player = player;
        this.regenPercent = regenPercent;
    }

    public Player getPlayer()           { return player; }
    public float getRegenPercent()      { return regenPercent; }
    public void setRegenPercent(float regenPercent) {
        this.regenPercent = regenPercent;
    }
}