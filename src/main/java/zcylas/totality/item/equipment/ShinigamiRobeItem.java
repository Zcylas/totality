package zcylas.totality.item.equipment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import zcylas.totality.api.item.TotalityArmorItem;

/**
 * Ordinary Shinigami uniform robe — a recognizable, non-magical garment, so unlike most
 * {@link TotalityArmorItem}s it skips attunement/identification entirely (see overrides below).
 * Classified {@link ArmorCategory#LIGHT} per the canonical design (Shinigami Robe is light armor,
 * not clothing). {@link #getAcBonus()} is left at the base class default (0) — the exact AC/
 * balance value for this category is separate, still-open design work; this change only fixes
 * the classification itself, not the numeric bonus.
 */
public class ShinigamiRobeItem extends TotalityArmorItem {

    public ShinigamiRobeItem(Item.Properties properties) {
        super(properties.equippable(EquipmentSlot.CHEST), ArmorCategory.LIGHT);
    }

    @Override public boolean requiresAttunement() { return false; }

    @Override public boolean startsUnidentified() { return false; }
}
