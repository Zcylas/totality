package zcylas.totality.item.equipment;

import net.minecraft.world.item.Item;
import zcylas.totality.api.equipment.TotalityRingItem;

/**
 * The representative required-attunement item for the Tooltip API's shared
 * {@link zcylas.totality.client.tooltip.contributor.AttunementContributor}, which now derives
 * its "+N AC/Save Bonus" and attunement-status tooltip lines directly from
 * {@link #getAcBonus()}/{@link #getSaveBonus()} — replacing the hand-typed
 * "+1 bonus to AC and saving throws" text this class used to duplicate, which could silently
 * drift from the real bonus values above it.
 */
public class RingOfProtectionItem extends TotalityRingItem {

    public RingOfProtectionItem(Item.Properties properties) {
        super(properties);
    }

    @Override public int getAcBonus()   { return 1; }
    @Override public int getSaveBonus() { return 1; }
}
