package zcylas.totality.init;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;

public class ModTags {

    // ── Weapon tags ───────────────────────────────────────────────────────────
    public static final TagKey<Item> ONE_HANDED_WEAPONS = item("one_handed_weapons");
    public static final TagKey<Item> TWO_HANDED_WEAPONS = item("two_handed_weapons");
    public static final TagKey<Item> THROWN_WEAPONS = item("thrown_weapons");

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static TagKey<Item> item(String path) {
        return TagKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath("totality", path));
    }

    private ModTags() {}
}