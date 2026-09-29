package zcylas.totality.api.equipment;

import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.item.TotalityArmorItem;

/**
 * Base for items worn in the Back equipment slot ({@link PlayerEquipmentComponent#IDX_BACK}): capes and cloaks now;
 * backpacks, wings, jetpacks and other back-mounted items later. Only these items are valid Back equipment.
 * <p>The slot itself grants nothing: no armor (it is an accessory, so no {@link ArmorCategory} and 0 AC), no flight,
 * no storage and no movement. An item adds behaviour only through the {@link TotalityArmorItem} hooks it overrides
 * ({@code onEquip}, {@code onUnequip}, ...), exactly as rings do.
 */
public abstract class TotalityBackItem extends TotalityArmorItem {

    protected TotalityBackItem(Properties properties) {
        super(properties);
    }

    /** True if {@code stack} may be placed in the Back slot. */
    public static boolean isBackEquipment(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof TotalityBackItem;
    }
}
