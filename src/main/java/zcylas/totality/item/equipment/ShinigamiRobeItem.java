package zcylas.totality.item.equipment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import zcylas.totality.api.item.TotalityArmorItem;

/**
 * Ordinary Shinigami uniform robe — a recognizable, non-magical garment, so unlike most
 * {@link TotalityArmorItem}s it skips attunement/identification entirely (see overrides below).
 * Temporary {@link ArmorCategory#CLOTHING} pending a real CLOTHING-vs-LIGHT balance pass.
 */
public class ShinigamiRobeItem extends TotalityArmorItem {

    public ShinigamiRobeItem(Item.Properties properties) {
        super(properties.equippable(EquipmentSlot.CHEST), ArmorCategory.CLOTHING);
    }

    @Override public boolean requiresAttunement() { return false; }

    @Override public boolean startsUnidentified() { return false; }
}
