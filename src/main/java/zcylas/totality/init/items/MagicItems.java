package zcylas.totality.init.items;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import zcylas.totality.api.core.rpgutils.rarity.*;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.equipment.RingOfProtectionItem;
import zcylas.totality.item.magic.GrimoireItem;
import zcylas.totality.item.spell_material.ArcaneFocusItem;

public class MagicItems {
//Girmoires
    public static final GrimoireItem NOVICE_GRIMOIRE = TotalityRegistry.registerItem(
            "novice_grimoire",
            properties -> new GrimoireItem(properties, 1),
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.RARE))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A simple tome given to those who have just begun to hear the whisper of the arcane. Most never open a second one."
                    ))
    );

    public static final GrimoireItem APPRENTICE_GRIMOIRE = TotalityRegistry.registerItem(
            "apprentice_grimoire",
            properties -> new GrimoireItem(properties, 2),
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.EPIC))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "The runes within grow more complex, more demanding. Only those who survived the first grimoire deserve to hold this one."
                    ))
    );

    public static final GrimoireItem ARCHMAGE_GRIMOIRE = TotalityRegistry.registerItem(
            "archmage_grimoire",
            properties -> new GrimoireItem(properties, 3),
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.LEGENDARY))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "Reserved for those who have mastered the arcane arts. This grimoire unlocks the most powerful runes known to mankind, capable of reshaping reality itself."
                    ))
    );
// Rings
    public static final RingOfProtectionItem RING_OF_PROTECTION = TotalityRegistry.registerItem(
            "ring_of_protection",
            props -> new RingOfProtectionItem(props),
            new Item.Properties()
                    .component(ItemComponents.getRarity(),   new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.getItemType(), new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A simple band of protection. Requires attunement to grant its boon."
                    ))
    );

//Arcane Foci
    public static final ArcaneFocusItem ARCANE_ORB = TotalityRegistry.registerItem(
            "arcane_orb",
            props -> new ArcaneFocusItem(props, SoundEvents.AMETHYST_BLOCK_CHIME),
            new Item.Properties()
                    .stacksTo(1)
                    .component(ItemComponents.getRarity(),   new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.getItemType(), new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A polished orb of faintly luminous crystal. Mages channel their will through its surface, sharpening intention into spell."
                    ))
    );
    public static final ArcaneFocusItem BARD_GUITAR = TotalityRegistry.registerItem(
            "bard_guitar",
            props -> new ArcaneFocusItem(props, SoundEvents.NOTE_BLOCK_GUITAR.value()),
            new Item.Properties()
                    .stacksTo(1)
                    .component(ItemComponents.getRarity(),   new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.getItemType(), new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "Every string carries a word of power. In the hands of a true Bard, music and magic are the same breath."
                    ))
    );
    public static void register() {}

    private MagicItems() {}

}
