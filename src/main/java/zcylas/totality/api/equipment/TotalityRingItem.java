package zcylas.totality.api.equipment;

import net.minecraft.world.item.Item;
import zcylas.totality.api.item.TotalityArmorItem;

public abstract class TotalityRingItem extends TotalityArmorItem {

    public TotalityRingItem(Properties properties) {
        super(properties);
    }

    public int getAcBonus()   { return 0; }
    public int getSaveBonus() { return 0; }
}