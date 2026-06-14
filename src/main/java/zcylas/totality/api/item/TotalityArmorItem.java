package zcylas.totality.api.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Abstract base for all Totality wearable/equippable items — armor, clothing,
 * rings, necklaces, cloaks, belts, gloves, and trinkets.
 *
 * Extends plain {@link Item} (not MC's {@link net.minecraft.world.item.ArmorItem}) because
 * Totality equipment uses the custom Equipment API (rings, necklaces, etc.) rather than
 * vanilla armor slots. Items that go in vanilla armor slots can still override as needed.
 *
 * Implements {@link TotalityItem} for attunement and identification support.
 */
public abstract class TotalityArmorItem extends Item implements TotalityItem {

    /**
     * Armor category — determines how this item interacts with the AC system.
     *
     * CLOTHING : cosmetic/non-protective. Grants 0 AC. Wearing CLOTHING does NOT
     *            disable Unarmored Defense — a Barbarian in robes is still "unarmored."
     * LIGHT    : light armor. Full DEX modifier applies to AC.
     * MEDIUM   : medium armor. DEX modifier capped at +2.
     * HEAVY    : heavy armor. No DEX modifier. Imposes stealth disadvantage.
     *
     * Null (accessories — rings, necklaces, trinkets) behaves the same as CLOTHING
     * for Unarmored Defense purposes: it never contributes armor type, never blocks
     * the unarmored path.
     */
    public enum ArmorCategory {
        CLOTHING,
        LIGHT,
        MEDIUM,
        HEAVY;

        /** True if wearing this category counts as "armored" (disables Unarmored Defense). */
        public boolean isArmored() { return this != CLOTHING; }
    }

    @Nullable
    private final ArmorCategory armorCategory;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Constructor for accessories (rings, necklaces, trinkets) — no armor category.
     * These items contribute 0 AC and never disable Unarmored Defense.
     */
    public TotalityArmorItem(Properties properties) {
        this(properties, null);
    }

    /**
     * Constructor for body armor and clothing.
     *
     * @param category CLOTHING = no AC, Unarmored Defense intact;
     *                 LIGHT/MEDIUM/HEAVY = grants AC, blocks Unarmored Defense.
     *                 Pass null for accessories (equivalent to no-arg overload).
     */
    public TotalityArmorItem(Properties properties, @Nullable ArmorCategory category) {
        super(properties.stacksTo(1));
        this.armorCategory = category;
    }

    // ── Armor category ────────────────────────────────────────────────────────

    /**
     * Returns the armor category, or null for accessories/jewelry.
     * Null and CLOTHING both leave Unarmored Defense active.
     */
    @Nullable
    public ArmorCategory getArmorCategory() { return armorCategory; }

    // ── Default overrides ─────────────────────────────────────────────────────

    @Override
    public boolean requiresAttunement() { return true; }

    @Override
    public boolean startsUnidentified() { return true; }

    /** AC bonus granted when attuned and equipped. Override in subclasses. */
    public int getAcBonus()   { return 0; }

    /** Saving throw bonus granted when attuned and equipped. Override in subclasses. */
    public int getSaveBonus() { return 0; }

    /**
     * Called when this item is equipped into a Totality equipment slot.
     * Override to grant stat bonuses, AC, resistances, etc.
     * Only called if {@link #isAttuned} is true.
     */
    public void onEquip(ItemStack stack, net.minecraft.server.level.ServerPlayer player) {}

    /**
     * Called when this item is removed from a Totality equipment slot.
     * Override to remove the bonuses granted in {@link #onEquip}.
     */
    public void onUnequip(ItemStack stack, net.minecraft.server.level.ServerPlayer player) {}

    /**
     * Called each server tick while this item is equipped and attuned.
     * Override for passive tick effects (regeneration, auras, etc.).
     */
    public void onEquippedTick(ItemStack stack, net.minecraft.server.level.ServerPlayer player) {}
}