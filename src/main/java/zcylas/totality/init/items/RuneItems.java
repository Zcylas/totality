package zcylas.totality.init.items;

import net.minecraft.world.item.Item;
import zcylas.totality.api.core.rpgutils.rarity.*;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.magic.rune.RuneItem;

// Forms
import zcylas.totality.item.magic.rune.form.ProjectileForm;
import zcylas.totality.item.magic.rune.form.SelfForm;
import zcylas.totality.item.magic.rune.form.TouchForm;

// Effects
import zcylas.totality.item.magic.rune.effect.*;
import zcylas.totality.item.magic.rune.effect.augment.*;

public class RuneItems {

    // ── Forms ─────────────────────────────────────────────────────────────────
    public static final Item BLANK_FORM = TotalityRegistry.registerItem(
            "blank_form",
            Item::new,
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A blank form rune. Used in rituals to craft specific form runes."
                    ))
    );
    public static final Item BLANK_EFFECT = TotalityRegistry.registerItem(
            "blank_effect",
            Item::new,
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A blank effect rune. Used in rituals to craft specific effect runes."
                    ))
    );
    public static final Item BLANK_AUGMENT = TotalityRegistry.registerItem(
            "blank_augment",
            Item::new,
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A blank augment rune. Used in rituals to craft specific augment runes."
                    ))
    );
    public static final Item RUNE_TOUCH = TotalityRegistry.registerItem(
            "rune_touch",
            props -> new RuneItem(TouchForm.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A form rune. Delivers the spell's effect to whatever the caster touches directly."
                    ))
    );

    public static final Item RUNE_PROJECTILE = TotalityRegistry.registerItem(
            "rune_projectile",
            props -> new RuneItem(ProjectileForm.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A form rune. Launches a magical projectile that delivers the spell's effect on impact."
                    ))
    );

    public static final Item RUNE_SELF = TotalityRegistry.registerItem(
            "rune_self",
            props -> new RuneItem(SelfForm.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A form rune. Applies the spell's effect directly to the caster."
                    ))
    );

    // ── Effects ───────────────────────────────────────────────────────────────

    public static final Item RUNE_BREAK = TotalityRegistry.registerItem(
            "rune_break",
            props -> new RuneItem(BreakEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Breaks blocks at the target location."
                    ))
    );

    public static final Item RUNE_PICKUP = TotalityRegistry.registerItem(
            "rune_pickup",
            props -> new RuneItem(PickupEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Pulls nearby items toward the caster."
                    ))
    );

    public static final Item RUNE_LAUNCH = TotalityRegistry.registerItem(
            "rune_launch",
            props -> new RuneItem(LaunchEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Launches the target into the air."
                    ))
    );

    public static final Item RUNE_IGNITE = TotalityRegistry.registerItem(
            "rune_ignite",
            props -> new RuneItem(IgniteEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Sets the target ablaze."
                    ))
    );

    public static final Item RUNE_EXPLOSION = TotalityRegistry.registerItem(
            "rune_explosion",
            props -> new RuneItem(ExplosionEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.RARE))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Creates an explosion at the target location."
                    ))
    );

    public static final Item RUNE_GLIDE = TotalityRegistry.registerItem(
            "rune_glide",
            props -> new RuneItem(GlideEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Grants the caster the ability to glide through the air."
                    ))
    );

    public static final Item RUNE_SMELT = TotalityRegistry.registerItem(
            "rune_smelt",
            props -> new RuneItem(SmeltEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Smelts blocks or items at the target location."
                    ))
    );

    public static final Item RUNE_ORBIT = TotalityRegistry.registerItem(
            "rune_orbit",
            props -> new RuneItem(OrbitEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.RARE))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Creates an orbiting projectile around the caster."
                    ))
    );

    public static final Item RUNE_HARM = TotalityRegistry.registerItem(
            "rune_harm",
            props -> new RuneItem(HarmEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Deals direct magical damage to the target."
                    ))
    );

    public static final Item RUNE_HEAL = TotalityRegistry.registerItem(
            "rune_heal",
            props -> new RuneItem(HealEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Restores health to the target."
                    ))
    );

    public static final Item RUNE_HEX = TotalityRegistry.registerItem(
            "rune_hex",
            props -> new RuneItem(HexEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.RARE))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Curses the target with a debilitating hex."
                    ))
    );

    public static final Item RUNE_LIGHTNING = TotalityRegistry.registerItem(
            "rune_lightning",
            props -> new RuneItem(LightningEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.RARE))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Calls a bolt of lightning down upon the target."
                    ))
    );

    public static final Item RUNE_CHAINING = TotalityRegistry.registerItem(
            "rune_chaining",
            props -> new RuneItem(ChainingEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.RARE))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Chains the spell's effect to nearby targets."
                    ))
    );

    public static final Item RUNE_GROW = TotalityRegistry.registerItem(
            "rune_grow",
            props -> new RuneItem(GrowEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Accelerates the growth of plants and crops."
                    ))
    );

    public static final Item RUNE_LINGER = TotalityRegistry.registerItem(
            "rune_linger",
            props -> new RuneItem(LingerEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.RARE))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Creates a lingering zone that repeatedly applies its paired effects."
                    ))
    );

    public static final Item RUNE_HARVEST = TotalityRegistry.registerItem(
            "rune_harvest",
            props -> new RuneItem(HarvestEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Harvests crops and plants at the target location."
                    ))
    );

    public static final Item RUNE_BURST = TotalityRegistry.registerItem(
            "rune_burst",
            props -> new RuneItem(BurstEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.RARE))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Releases a burst of energy outward from the target."
                    ))
    );

    public static final Item RUNE_SUMMON_UNDEAD = TotalityRegistry.registerItem(
            "rune_summon_undead",
            props -> new RuneItem(SummonUndeadEffect.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.EPIC))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An effect rune. Raises the dead to fight for the caster."
                    ))
    );

    // ── Augments ──────────────────────────────────────────────────────────────

    public static final Item RUNE_AMPLIFY = TotalityRegistry.registerItem(
            "rune_amplify",
            props -> new RuneItem(AmplifyAugment.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An augment rune. Increases the magnitude or power of the spell's effect."
                    ))
    );

    public static final Item RUNE_AOE = TotalityRegistry.registerItem(
            "rune_aoe",
            props -> new RuneItem(AoeAugment.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An augment rune. Expands the area of effect of the spell."
                    ))
    );

    public static final Item RUNE_REDUCE_TIME = TotalityRegistry.registerItem(
            "rune_reduce_time",
            props -> new RuneItem(ReduceTimeAugment.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An augment rune. Reduces the duration of the spell's effect."
                    ))
    );

    public static final Item RUNE_EXTEND_TIME = TotalityRegistry.registerItem(
            "rune_extend_time",
            props -> new RuneItem(ExtendTimeAugment.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An augment rune. Extends the duration of the spell's effect."
                    ))
    );

    public static final Item RUNE_DAMPEN = TotalityRegistry.registerItem(
            "rune_dampen",
            props -> new RuneItem(DampenAugment.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An augment rune. Reduces the magnitude or power of the spell's effect."
                    ))
    );

    public static final Item RUNE_SENSITIVE = TotalityRegistry.registerItem(
            "rune_sensitive",
            props -> new RuneItem(SensitiveAugment.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An augment rune. Makes the spell more sensitive, altering how it interacts with its target."
                    ))
    );

    public static final Item RUNE_PIERCE = TotalityRegistry.registerItem(
            "rune_pierce",
            props -> new RuneItem(PierceAugment.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An augment rune. Allows the spell to pierce through its target and affect what lies behind."
                    ))
    );

    public static final Item RUNE_FORTUNE = TotalityRegistry.registerItem(
            "rune_fortune",
            props -> new RuneItem(FortuneAugment.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.RARE))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An augment rune. Increases the yield or drops from the spell's effect."
                    ))
    );

    public static final Item RUNE_RANDOMIZE = TotalityRegistry.registerItem(
            "rune_randomize",
            props -> new RuneItem(RandomizeAugment.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An augment rune. Introduces an element of chance into the spell's effect."
                    ))
    );

    public static final Item RUNE_EXTRACT = TotalityRegistry.registerItem(
            "rune_extract",
            props -> new RuneItem(ExtractAugment.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.RARE))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An augment rune. Causes the spell to extract or preserve its target rather than destroy it."
                    ))
    );

    public static final Item RUNE_ACCELERATE = TotalityRegistry.registerItem(
            "rune_accelerate",
            props -> new RuneItem(AccelerateAugment.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An augment rune. Increases the speed at which the spell or its projectile travels."
                    ))
    );

    public static final Item RUNE_DECELERATE = TotalityRegistry.registerItem(
            "rune_decelerate",
            props -> new RuneItem(DecelerateAugment.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An augment rune. Reduces the speed at which the spell or its projectile travels."
                    ))
    );

    public static final Item RUNE_SPLIT = TotalityRegistry.registerItem(
            "rune_split",
            props -> new RuneItem(SplitAugment.INSTANCE, props),
            new Item.Properties()
                    .stacksTo(16)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.RARE))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "An augment rune. Splits the spell into multiple instances upon casting."
                    ))
    );

    public static void register() {}

    private RuneItems() {}
}