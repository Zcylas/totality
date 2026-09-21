package zcylas.totality.init.items;

import net.minecraft.world.item.Item;
import zcylas.totality.api.bleach.zanpakuto.ZanpakutoType;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.core.rpgutils.rarity.*;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.item.TotalityArmorItem;
import zcylas.totality.api.item.TotalityItemComponents;
import zcylas.totality.api.rpg.combat.weapon.TotalityWeaponStats;
import zcylas.totality.api.rpg.combat.weapon.WeaponCategory;
import zcylas.totality.api.rpg.combat.weapon.WeaponType;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.equipment.ShinigamiRobeItem;
import zcylas.totality.item.weapon.ZanpakutoItem;

public class BleachItems {

    // ── Zanpakutō ─────────────────────────────────────────────────────────────

    public static final ZanpakutoItem ZANPAKUTO = TotalityRegistry.registerItem(
            "zanpakuto",
            props -> new ZanpakutoItem(props,
                    new TotalityWeaponStats(
                            Dice.D8, 1, DamageTypes.SLASHING, AbilityScore.STR,
                            WeaponCategory.MARTIAL_MELEE, WeaponType.ONE_HANDED,
                            true, false, false, false
                    )),
            new Item.Properties()
                    .durability(1024)
                    .component(TotalityItemComponents.ZANPAKUTO_TYPE, ZanpakutoType.ASAUCHI)
                    .component(ItemComponents.getRarity(),   new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.getItemType(), new ItemTypeComponent(ItemType.WEAPON))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A nameless Zanpakutō — an Asauchi — awaiting the soul strong enough to awaken it."
                    ))
    );

    // ── Shinigami Uniform ─────────────────────────────────────────────────────

    public static final ShinigamiRobeItem SHINIGAMI_ROBE = TotalityRegistry.registerItem(
            "shinigami_robe",
            ShinigamiRobeItem::new,
            new Item.Properties()
                    .component(ItemComponents.getRarity(),   new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.getItemType(), new ItemTypeComponent(ItemType.ARMOR))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A plain Shinigami uniform robe — worn by every soul reaper, awakened or not."
                    ))
    );

    private BleachItems() {}

    public static void register() {}
}
