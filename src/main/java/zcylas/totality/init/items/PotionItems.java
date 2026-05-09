package zcylas.totality.init.items;

import zcylas.totality.api.rpg.skills.alchemy.AlchemyEffects;
import zcylas.totality.api.rpg.skills.alchemy.potions.DurationTier;
import zcylas.totality.api.rpg.skills.alchemy.potions.FortifyTier;
import zcylas.totality.api.rpg.skills.alchemy.potions.RegenerateTier;
import zcylas.totality.api.rpg.skills.alchemy.potions.MagnitudeTier;
import zcylas.totality.api.rpg.skills.alchemy.potions.PotionData;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.potion.AlchemyPotionItem;

public final class PotionItems {

    // ── Brewed Potion base item ───────────────────────────────────────────────
    // Used for dynamically brewed potions — PotionData is stored as a component on the stack
    public static final AlchemyPotionItem BREWED_POTION = TotalityRegistry.registerPotion(
            "brewed_potion",
            MagnitudeTier.STANDARD, AlchemyEffects.RESTORE_HEALTH, PotionData.COLOR_RED
    );

    // ── Restore Health ────────────────────────────────────────────────────────
    public static final AlchemyPotionItem POTION_OF_MINOR_HEALING =
            TotalityRegistry.registerPotion("potion_of_minor_healing",
                    MagnitudeTier.MINOR, AlchemyEffects.RESTORE_HEALTH, PotionData.COLOR_RED, "Healing");

    public static final AlchemyPotionItem POTION_OF_HEALING =
            TotalityRegistry.registerPotion("potion_of_healing",
                    MagnitudeTier.STANDARD, AlchemyEffects.RESTORE_HEALTH, PotionData.COLOR_RED, "Healing");

    public static final AlchemyPotionItem POTION_OF_PLENTIFUL_HEALING =
            TotalityRegistry.registerPotion("potion_of_plentiful_healing",
                    MagnitudeTier.PLENTIFUL, AlchemyEffects.RESTORE_HEALTH, PotionData.COLOR_RED, "Healing");

    public static final AlchemyPotionItem POTION_OF_VIGOROUS_HEALING =
            TotalityRegistry.registerPotion("potion_of_vigorous_healing",
                    MagnitudeTier.VIGOROUS, AlchemyEffects.RESTORE_HEALTH, PotionData.COLOR_RED, "Healing");

    public static final AlchemyPotionItem POTION_OF_EXTREME_HEALING =
            TotalityRegistry.registerPotion("potion_of_extreme_healing",
                    MagnitudeTier.EXTREME, AlchemyEffects.RESTORE_HEALTH, PotionData.COLOR_RED, "Healing");

    public static final AlchemyPotionItem POTION_OF_ULTIMATE_HEALING =
            TotalityRegistry.registerPotion("potion_of_ultimate_healing",
                    MagnitudeTier.ULTIMATE, AlchemyEffects.RESTORE_HEALTH, PotionData.COLOR_RED, "Healing");

    // ── Restore Mana ──────────────────────────────────────────────────────────
    public static final AlchemyPotionItem POTION_OF_MINOR_MANA =
            TotalityRegistry.registerPotion("potion_of_minor_mana",
                    MagnitudeTier.MINOR, AlchemyEffects.RESTORE_MANA, PotionData.COLOR_BLUE);

    public static final AlchemyPotionItem POTION_OF_MANA =
            TotalityRegistry.registerPotion("potion_of_mana",
                    MagnitudeTier.STANDARD, AlchemyEffects.RESTORE_MANA, PotionData.COLOR_BLUE);

    public static final AlchemyPotionItem POTION_OF_PLENTIFUL_MANA =
            TotalityRegistry.registerPotion("potion_of_plentiful_mana",
                    MagnitudeTier.PLENTIFUL, AlchemyEffects.RESTORE_MANA, PotionData.COLOR_BLUE);

    public static final AlchemyPotionItem POTION_OF_VIGOROUS_MANA =
            TotalityRegistry.registerPotion("potion_of_vigorous_mana",
                    MagnitudeTier.VIGOROUS, AlchemyEffects.RESTORE_MANA, PotionData.COLOR_BLUE);

    public static final AlchemyPotionItem POTION_OF_EXTREME_MANA =
            TotalityRegistry.registerPotion("potion_of_extreme_mana",
                    MagnitudeTier.EXTREME, AlchemyEffects.RESTORE_MANA, PotionData.COLOR_BLUE);

    public static final AlchemyPotionItem POTION_OF_ULTIMATE_MANA =
            TotalityRegistry.registerPotion("potion_of_ultimate_mana",
                    MagnitudeTier.ULTIMATE, AlchemyEffects.RESTORE_MANA, PotionData.COLOR_BLUE);

    // ── Restore Stamina ───────────────────────────────────────────────────────
    public static final AlchemyPotionItem POTION_OF_MINOR_STAMINA =
            TotalityRegistry.registerPotion("potion_of_minor_stamina",
                    MagnitudeTier.MINOR, AlchemyEffects.RESTORE_STAMINA, PotionData.COLOR_GREEN);

    public static final AlchemyPotionItem POTION_OF_STAMINA =
            TotalityRegistry.registerPotion("potion_of_stamina",
                    MagnitudeTier.STANDARD, AlchemyEffects.RESTORE_STAMINA, PotionData.COLOR_GREEN);

    public static final AlchemyPotionItem POTION_OF_PLENTIFUL_STAMINA =
            TotalityRegistry.registerPotion("potion_of_plentiful_stamina",
                    MagnitudeTier.PLENTIFUL, AlchemyEffects.RESTORE_STAMINA, PotionData.COLOR_GREEN);

    public static final AlchemyPotionItem POTION_OF_VIGOROUS_STAMINA =
            TotalityRegistry.registerPotion("potion_of_vigorous_stamina",
                    MagnitudeTier.VIGOROUS, AlchemyEffects.RESTORE_STAMINA, PotionData.COLOR_GREEN);

    public static final AlchemyPotionItem POTION_OF_EXTREME_STAMINA =
            TotalityRegistry.registerPotion("potion_of_extreme_stamina",
                    MagnitudeTier.EXTREME, AlchemyEffects.RESTORE_STAMINA, PotionData.COLOR_GREEN);

    public static final AlchemyPotionItem POTION_OF_ULTIMATE_STAMINA =
            TotalityRegistry.registerPotion("potion_of_ultimate_stamina",
                    MagnitudeTier.ULTIMATE, AlchemyEffects.RESTORE_STAMINA, PotionData.COLOR_GREEN);

    // ── Waterbreathing ────────────────────────────────────────────────────────
    public static final AlchemyPotionItem POTION_OF_WATERBREATHING =
            TotalityRegistry.registerPotion("potion_of_waterbreathing",
                    DurationTier.POTION, AlchemyEffects.WATERBREATHING, PotionData.COLOR_WHITE);

    public static final AlchemyPotionItem DRAUGHT_OF_WATERBREATHING =
            TotalityRegistry.registerPotion("draught_of_waterbreathing",
                    DurationTier.DRAUGHT, AlchemyEffects.WATERBREATHING, PotionData.COLOR_WHITE);

    public static final AlchemyPotionItem PHILTER_OF_WATERBREATHING =
            TotalityRegistry.registerPotion("philter_of_waterbreathing",
                    DurationTier.PHILTER, AlchemyEffects.WATERBREATHING, PotionData.COLOR_WHITE);

    public static final AlchemyPotionItem ELIXIR_OF_WATERBREATHING =
            TotalityRegistry.registerPotion("elixir_of_waterbreathing",
                    DurationTier.ELIXIR, AlchemyEffects.WATERBREATHING, PotionData.COLOR_WHITE);

    // ── Regenerate Health ─────────────────────────────────────────────────────
    public static final AlchemyPotionItem POTION_OF_REGENERATION =
            TotalityRegistry.registerRegenPotion("potion_of_regeneration",
                    "Regeneration", RegenerateTier.POTION, AlchemyEffects.REGENERATE_HEALTH, PotionData.COLOR_RED);

    public static final AlchemyPotionItem DRAUGHT_OF_REGENERATION =
            TotalityRegistry.registerRegenPotion("draught_of_regeneration",
                    "Regeneration", RegenerateTier.DRAUGHT, AlchemyEffects.REGENERATE_HEALTH, PotionData.COLOR_RED);

    public static final AlchemyPotionItem SOLUTION_OF_REGENERATION =
            TotalityRegistry.registerRegenPotion("solution_of_regeneration",
                    "Regeneration", RegenerateTier.SOLUTION, AlchemyEffects.REGENERATE_HEALTH, PotionData.COLOR_RED);

    public static final AlchemyPotionItem PHILTER_OF_REGENERATION =
            TotalityRegistry.registerRegenPotion("philter_of_regeneration",
                    "Regeneration", RegenerateTier.PHILTER, AlchemyEffects.REGENERATE_HEALTH, PotionData.COLOR_RED);

    public static final AlchemyPotionItem ELIXIR_OF_REGENERATION =
            TotalityRegistry.registerRegenPotion("elixir_of_regeneration",
                    "Regeneration", RegenerateTier.ELIXIR, AlchemyEffects.REGENERATE_HEALTH, PotionData.COLOR_RED);

    // ── Regenerate Mana ───────────────────────────────────────────────────────
    public static final AlchemyPotionItem POTION_OF_LASTING_POTENCY =
            TotalityRegistry.registerRegenPotion("potion_of_lasting_potency",
                    "Lasting Mana", RegenerateTier.POTION, AlchemyEffects.REGENERATE_MANA, PotionData.COLOR_BLUE);

    public static final AlchemyPotionItem DRAUGHT_OF_LASTING_POTENCY =
            TotalityRegistry.registerRegenPotion("draught_of_lasting_potency",
                    "Lasting Mana", RegenerateTier.DRAUGHT, AlchemyEffects.REGENERATE_MANA, PotionData.COLOR_BLUE);

    public static final AlchemyPotionItem SOLUTION_OF_LASTING_POTENCY =
            TotalityRegistry.registerRegenPotion("solution_of_lasting_potency",
                    "Lasting Mana", RegenerateTier.SOLUTION, AlchemyEffects.REGENERATE_MANA, PotionData.COLOR_BLUE);

    public static final AlchemyPotionItem PHILTER_OF_LASTING_POTENCY =
            TotalityRegistry.registerRegenPotion("philter_of_lasting_potency",
                    "Lasting Mana", RegenerateTier.PHILTER, AlchemyEffects.REGENERATE_MANA, PotionData.COLOR_BLUE);

    public static final AlchemyPotionItem ELIXIR_OF_LASTING_POTENCY =
            TotalityRegistry.registerRegenPotion("elixir_of_lasting_potency",
                    "Lasting Mana", RegenerateTier.ELIXIR, AlchemyEffects.REGENERATE_MANA, PotionData.COLOR_BLUE);

    // ── Fortify Health ────────────────────────────────────────────────────────
    public static final AlchemyPotionItem POTION_OF_HEALTH =
            TotalityRegistry.registerFortifyPotion("potion_of_health",
                    "Health", FortifyTier.POTION, AlchemyEffects.FORTIFY_HEALTH, PotionData.COLOR_RED);

    public static final AlchemyPotionItem DRAUGHT_OF_HEALTH =
            TotalityRegistry.registerFortifyPotion("draught_of_health",
                    "Health", FortifyTier.DRAUGHT, AlchemyEffects.FORTIFY_HEALTH, PotionData.COLOR_RED);

    public static final AlchemyPotionItem SOLUTION_OF_HEALTH =
            TotalityRegistry.registerFortifyPotion("solution_of_health",
                    "Health", FortifyTier.SOLUTION, AlchemyEffects.FORTIFY_HEALTH, PotionData.COLOR_RED);

    public static final AlchemyPotionItem PHILTER_OF_HEALTH =
            TotalityRegistry.registerFortifyPotion("philter_of_health",
                    "Health", FortifyTier.PHILTER, AlchemyEffects.FORTIFY_HEALTH, PotionData.COLOR_RED);

    public static final AlchemyPotionItem ELIXIR_OF_HEALTH =
            TotalityRegistry.registerFortifyPotion("elixir_of_health",
                    "Health", FortifyTier.ELIXIR, AlchemyEffects.FORTIFY_HEALTH, PotionData.COLOR_RED);

    // ── Fortify Mana ──────────────────────────────────────────────────────────
    public static final AlchemyPotionItem POTION_OF_EXTRA_MANA =
            TotalityRegistry.registerFortifyPotion("potion_of_extra_mana",
                    "Extra Mana", FortifyTier.POTION, AlchemyEffects.FORTIFY_MANA, PotionData.COLOR_BLUE);

    public static final AlchemyPotionItem DRAUGHT_OF_EXTRA_MANA =
            TotalityRegistry.registerFortifyPotion("draught_of_extra_mana",
                    "Extra Mana", FortifyTier.DRAUGHT, AlchemyEffects.FORTIFY_MANA, PotionData.COLOR_BLUE);

    public static final AlchemyPotionItem SOLUTION_OF_EXTRA_MANA =
            TotalityRegistry.registerFortifyPotion("solution_of_extra_mana",
                    "Extra Mana", FortifyTier.SOLUTION, AlchemyEffects.FORTIFY_MANA, PotionData.COLOR_BLUE);

    public static final AlchemyPotionItem PHILTER_OF_EXTRA_MANA =
            TotalityRegistry.registerFortifyPotion("philter_of_extra_mana",
                    "Extra Mana", FortifyTier.PHILTER, AlchemyEffects.FORTIFY_MANA, PotionData.COLOR_BLUE);

    public static final AlchemyPotionItem ELIXIR_OF_EXTRA_MANA =
            TotalityRegistry.registerFortifyPotion("elixir_of_extra_mana",
                    "Extra Mana", FortifyTier.ELIXIR, AlchemyEffects.FORTIFY_MANA, PotionData.COLOR_BLUE);

    // ── Special Potions ───────────────────────────────────────────────────────
    // Add unique/special potions here
    // Example:
    // public static final AlchemyPotionItem ELIXIR_OF_THE_MAGE =
    //     TotalityRegistry.registerSpecialPotion("elixir_of_the_mage",
    //         "Elixir of the Mage", PotionData.COLOR_GOLD, false,
    //         EffectEntry.instant(AlchemyEffects.RESTORE_MANA, 0.5f),
    //         EffectEntry.timed(AlchemyEffects.FORTIFY_MANA, 1200));

    // ── Poisons ───────────────────────────────────────────────────────────────
    // Add poisons here
    // Example:
    // public static final AlchemyPotionItem POISON_OF_MINOR_DAMAGE =
    //     TotalityRegistry.registerPoison("poison_of_minor_damage",
    //         MagnitudeTier.MINOR, AlchemyEffects.DAMAGE_HEALTH, PotionData.COLOR_PURPLE);

    private PotionItems() {}

    public static void register() {}
}