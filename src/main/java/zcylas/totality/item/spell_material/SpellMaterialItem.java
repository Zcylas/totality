package zcylas.totality.item.spell_material;

import net.minecraft.world.item.Item;
import zcylas.totality.api.item.SpellMaterialIngredient;

/**
 * A simple spell material component item — has no special behavior,
 * just implements {@link SpellMaterialIngredient} so it can go in a
 * {@link ComponentPouchItem} and satisfy spell material requirements.
 *
 * Register all simple materials through this class instead of making
 * individual item classes:
 *
 * <pre>
 *   public static final Item BAT_GUANO = register("bat_guano",
 *       props -> new SpellMaterialItem(props, "bat guano"), ...);
 *
 *   public static final Item EYE_OF_NEWT = register("eye_of_newt",
 *       props -> new SpellMaterialItem(props, "eye of newt"), ...);
 * </pre>
 *
 * Items that need their own behavior (pouches, focuses, etc.) keep
 * their own classes.
 */
public class SpellMaterialItem extends Item implements SpellMaterialIngredient {

    private final String materialName;

    public SpellMaterialItem(Properties properties, String materialName) {
        super(properties);
        this.materialName = materialName;
    }

    @Override
    public String getMaterialName() { return materialName; }
}