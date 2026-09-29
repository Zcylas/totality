package zcylas.totality.item.equipment;

import net.minecraft.world.item.Item;
import zcylas.totality.api.equipment.TotalityBackItem;

/**
 * Cape of the Mountebank (Context/References/Other/Cape of the Mountebank Reference Sheet(1).png): the first Back-slot
 * item, a wearable-art test (Master Reference v3.8 §26O.15 B). It is worn and drawn (BackEquipmentLayer) and does
 * nothing else: no Dimension Door, teleport, smoke, AC or other property; those belong to future design.
 * Like the D&D item it needs no attunement, and it is a recognisable garment, so it starts identified.
 */
public class CapeOfTheMountebankItem extends TotalityBackItem {

    public CapeOfTheMountebankItem(Item.Properties properties) {
        super(properties);
    }

    @Override public boolean requiresAttunement() { return false; }

    @Override public boolean startsUnidentified() { return false; }
}
