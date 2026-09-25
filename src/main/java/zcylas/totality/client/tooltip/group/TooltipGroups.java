package zcylas.totality.client.tooltip.group;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry of semantic body groups. Totality's built-in groups are registered here; any future API or
 * content integration registers its own with {@link #register} and targets it from a contributor's
 * {@code bodyGroup}. Registration order is preserved; ids are unique.
 *
 * <p>Every built-in group has a texture icon at a stable resource path — {@code
 * totality:textures/gui/tooltip/groups/<group>.png}, except Properties, which reuses the existing
 * {@code totality:textures/font/icons/properties.png} parchment rather than duplicating it. The artwork at
 * those paths is temporary and can be replaced (or resource-pack overridden) without code changes. Each texture
 * keeps a glyph fallback for when its PNG is missing.
 *
 * <p>Ordinary groups leave priority gaps of 100 so an integration can slot a group between two existing ones;
 * the example order for a mining tool is Mining, Combat, Properties, Enchantments. Requirements, Energy and
 * Durability are {@link TooltipGroup.Placement#BOTTOM} sections, always in that order after every ordinary group.
 */
public final class TooltipGroups {

    private static final Map<Identifier, TooltipGroup> REGISTERED = Collections.synchronizedMap(new LinkedHashMap<>());

    /** Directory of the built-in group icon textures. */
    public static final String ICON_DIRECTORY = "textures/gui/tooltip/groups/";

    public static final TooltipGroup MINING = register(ordinary("mining", 100, "⛏", "*", ItemType.TOOL));
    /* Glyph fallback: crossed swords (U+2694) render as a halftone blur in the default font at GUI scales 1-2. */
    public static final TooltipGroup COMBAT = register(ordinary("combat", 200, "†", "x", ItemType.WEAPON));
    public static final TooltipGroup MAGIC = register(ordinary("magic", 300, "✦", "*",
            ItemType.MAGICAL, ItemType.RITUAL, ItemType.REAGENT));
    public static final TooltipGroup EFFECTS = register(ordinary("effects", 500, "✚", "+",
            ItemType.POTION, ItemType.CONSUMABLE, ItemType.FOOD, ItemType.INGREDIENT));
    /** The one universal Properties group — block properties (Block Durability, ...) included. */
    public static final TooltipGroup PROPERTIES = register(new TooltipGroup(totality("properties"), 700,
            new TooltipGroupIcon.Texture(totality("textures/font/icons/properties.png"), new TooltipGroupIcon.Glyph("☰", "=")),
            Set.of(ItemType.BLOCK, ItemType.DECORATIVE, ItemType.MATERIAL, ItemType.FUEL)));
    public static final TooltipGroup ABILITIES = register(ordinary("abilities", 800, "★", "*"));
    public static final TooltipGroup ENCHANTMENTS = register(ordinary("enchantments", 900, "✧", "*"));
    public static final TooltipGroup REQUIREMENTS = register(bottom("requirements", 1000, "⚠", "!"));
    public static final TooltipGroup ENERGY = register(bottom("energy", 1100, "⚡", "~"));
    /** Physical item wear. Block Durability (how much a placed block withstands) is a Properties entry instead. */
    public static final TooltipGroup DURABILITY = register(bottom("durability", 1200, "♥", "+"));

    /** Registers a group and returns it for use as a constant. Duplicate ids are a programming error. */
    public static TooltipGroup register(TooltipGroup group) {
        synchronized (REGISTERED) {
            if (REGISTERED.putIfAbsent(group.id(), group) != null) {
                throw new IllegalStateException("Tooltip group already registered: " + group.id());
            }
        }
        return group;
    }

    public static @Nullable TooltipGroup get(Identifier id) {
        return REGISTERED.get(id);
    }

    /** Every registered group, in registration order. */
    public static List<TooltipGroup> registered() {
        synchronized (REGISTERED) {
            return List.copyOf(REGISTERED.values());
        }
    }

    /** The stable icon texture of a built-in group, {@code totality:textures/gui/tooltip/groups/<path>.png}. */
    public static Identifier iconTexture(String path) {
        return totality(ICON_DIRECTORY + path + ".png");
    }

    private static TooltipGroup ordinary(String path, int priority, String glyph, String fallback, ItemType... leadsFor) {
        return new TooltipGroup(totality(path), priority, icon(path, glyph, fallback), Set.of(leadsFor));
    }

    private static TooltipGroup bottom(String path, int priority, String glyph, String fallback) {
        return new TooltipGroup(totality(path), priority, icon(path, glyph, fallback), Set.of(), TooltipGroup.Placement.BOTTOM);
    }

    private static TooltipGroupIcon.Texture icon(String path, String glyph, String fallback) {
        return new TooltipGroupIcon.Texture(iconTexture(path), new TooltipGroupIcon.Glyph(glyph, fallback));
    }

    private static Identifier totality(String path) {
        return Identifier.fromNamespaceAndPath(Totality.MOD_ID, path);
    }

    private TooltipGroups() {}
}
