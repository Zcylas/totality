package zcylas.totality.init.items;

import net.minecraft.world.item.Item;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.spell_material.*;

/**
 * All items that serve as spell material components.
 * These implement {@link zcylas.totality.api.item.SpellMaterialIngredient} and
 * can go in a {@link ComponentPouchItem}.
 * Separate from {@link IngredientItems} which is for crafting materials.
 */
public final class SpellComponentItems {

    // ── Natural drops ─────────────────────────────────────────────────────────

    /** Dropped by bats. Used in: Fireball (V,S,M). */
    public static final Item BAT_GUANO = TotalityRegistry.registerItem("bat_guano",
            props -> new SpellMaterialItem(props, "bat guano"),
            new Item.Properties().stacksTo(64));

    /** Dropped by Blazes (30%). Used in: Fireball, Lightning Bolt, Disintegrate. */
    public static final Item SULPHUR_DUST = TotalityRegistry.registerItem("sulphur_dust",
            props -> new SpellMaterialItem(props, "sulphur"),
            new Item.Properties().stacksTo(64));

    /** Animal fur. Dropped by wolves/rabbits (future). Used in: Lightning Bolt. */
    public static final Item FUR = TotalityRegistry.registerItem("fur",
            props -> new SpellMaterialItem(props, "fur"),
            new Item.Properties().stacksTo(64));

    /** Thin glass rod, craftable from glass. Used in: Lightning Bolt. */
    public static final Item GLASS_ROD = TotalityRegistry.registerItem("glass_rod",
            props -> new SpellMaterialItem(props, "glass rod"),
            new Item.Properties().stacksTo(16));

    // ── Caster equipment ──────────────────────────────────────────────────────

    /**
     * Component Pouch — satisfies ALL replaceable material components.
     * Does not satisfy costly components (diamonds, etc.).
     */
    public static final Item COMPONENT_POUCH = TotalityRegistry.registerItem("component_pouch",
            ComponentPouchItem::new, new Item.Properties().stacksTo(1));

    /**
     * Arcane Focus — satisfies all replaceable material components when held
     * in the main hand or off-hand.
     */
    public static final Item ARCANE_FOCUS = TotalityRegistry.registerItem("arcane_focus",
            ArcaneFocusItem::new, new Item.Properties().stacksTo(1));

    public static void init() { /* touch to load */ }

    private SpellComponentItems() {}
}