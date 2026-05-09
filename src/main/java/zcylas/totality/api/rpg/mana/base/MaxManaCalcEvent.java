package zcylas.totality.api.rpg.mana.base;

import net.minecraft.world.entity.player.Player;

public class MaxManaCalcEvent {
    private final Player player;
    private int max;

    public MaxManaCalcEvent(Player player, int max) {
        this.player = player;
        this.max = max;
    }

    public Player getPlayer() { return player; }
    public int getMax() { return max; }
    public void setMax(int max) { this.max = max; }
}
