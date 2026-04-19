package zcylas.totality.init.settings;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.TooltipDisplay;
import zcylas.totality.Totality;

import java.util.LinkedHashSet;
import java.util.Set;

public class TotalItemSettings {

    private static final TooltipDisplay UNBREAKABLE_HIDE = new TooltipDisplay(
            false, new LinkedHashSet<>(Set.of(DataComponents.UNBREAKABLE))
    );

    public static Item.Properties defaultSettings(String name) {
        return new Item.Properties().setId(keyOf(name));
    }

    public static Item.Properties unbreakable(String name) {
        return new Item.Properties()
                .setId(keyOf(name))
                .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                .component(DataComponents.TOOLTIP_DISPLAY, UNBREAKABLE_HIDE);
    }

    // Keep the nameless versions for cases where setId is handled externally
    public static Item.Properties defaultSettings() {
        return new Item.Properties();
    }

    public static Item.Properties unbreakable() {
        return new Item.Properties()
                .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                .component(DataComponents.TOOLTIP_DISPLAY, UNBREAKABLE_HIDE);
    }

    private static ResourceKey<Item> keyOf(String name) {
        return ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, name)
        );
    }

    private TotalItemSettings() {}
}