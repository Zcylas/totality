package zcylas.totality.client.tooltip.group;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.client.tooltip.renderer.TooltipGroupHeadingPainter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TooltipGroupsTest {

    private static final List<TooltipGroup> BUILT_INS = List.of(TooltipGroups.MINING, TooltipGroups.COMBAT,
            TooltipGroups.MAGIC, TooltipGroups.EFFECTS, TooltipGroups.PROPERTIES, TooltipGroups.ABILITIES,
            TooltipGroups.ENCHANTMENTS, TooltipGroups.REQUIREMENTS, TooltipGroups.ENERGY, TooltipGroups.DURABILITY);

    /** Resource path of an Identifier inside this project's assets. */
    private static Path asset(Identifier id) {
        return Path.of("src/main/resources/assets", id.getNamespace(), id.getPath());
    }

    @Test
    void builtInGroupsHaveStableNamespacedIdsAndLocalizationKeys() {
        assertEquals(Identifier.fromNamespaceAndPath("totality", "mining"), TooltipGroups.MINING.id());
        assertEquals("tooltip_group.totality.mining", TooltipGroups.MINING.translationKey());
        assertEquals("tooltip_group.totality.durability", TooltipGroups.DURABILITY.translationKey());
        Set<Identifier> ids = new HashSet<>();
        for (TooltipGroup group : BUILT_INS) {
            assertEquals("totality", group.id().getNamespace());
            assertTrue(ids.add(group.id()), "unique id " + group.id());
        }
    }

    @Test
    void everyBuiltInGroupResolvesToAnExistingTexture() throws Exception {
        for (TooltipGroup group : BUILT_INS) {
            TooltipGroupIcon.Texture icon = assertInstanceOf(TooltipGroupIcon.Texture.class, group.icon(), group.id().toString());
            assertTrue(icon.texture().getPath().startsWith("textures/") && icon.texture().getPath().endsWith(".png"),
                    "a plain texture resource path, not an item or model: " + icon.texture());
            assertTrue(Files.isRegularFile(asset(icon.texture())), "texture exists: " + icon.texture());
            assertFalse(icon.tinted(), "texture icons keep their own colors");
        }
    }

    @Test
    void iconsUseStablePerGroupResourcePaths() {
        assertEquals(Identifier.fromNamespaceAndPath("totality", "textures/gui/tooltip/groups/combat.png"),
                ((TooltipGroupIcon.Texture) TooltipGroups.COMBAT.icon()).texture());
        for (TooltipGroup group : BUILT_INS) {
            if (group == TooltipGroups.PROPERTIES) continue;
            assertEquals(TooltipGroups.iconTexture(group.id().getPath()), ((TooltipGroupIcon.Texture) group.icon()).texture());
        }
    }

    @Test
    void propertiesReusesTheExistingParchmentIconRatherThanADuplicate() {
        assertEquals(Identifier.fromNamespaceAndPath("totality", "textures/font/icons/properties.png"),
                ((TooltipGroupIcon.Texture) TooltipGroups.PROPERTIES.icon()).texture());
        assertFalse(Files.exists(asset(TooltipGroups.iconTexture("properties"))), "no duplicate properties.png");
    }

    @Test
    void combatPngExistsWhileTheDamageStatIconIsKept() throws Exception {
        assertTrue(Files.isRegularFile(asset(TooltipGroups.iconTexture("combat"))));
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/totality/textures/font/icons/damage.png")),
                "damage.png stays for the Damage stat glyph");
        String font = Files.readString(Path.of("src/main/resources/assets/totality/font/icons.json"));
        assertTrue(font.contains("totality:font/icons/damage.png") && font.contains("totality:font/icons/properties.png"),
                "the stat-icon font still references its glyph images");
    }

    @Test
    void aMissingTextureFallsBackToItsGlyphNeverACheckerboard() {
        TooltipGroupIcon.Texture icon = (TooltipGroupIcon.Texture) TooltipGroups.COMBAT.icon();
        TooltipGroupHeadingPainter.DrawnIcon present = TooltipGlyphSupport.resolveIcon(icon, id -> true, cp -> true);
        assertEquals(icon.texture(), present.texture());
        assertFalse(present.tinted());
        TooltipGroupHeadingPainter.DrawnIcon missing = TooltipGlyphSupport.resolveIcon(icon, id -> false, cp -> true);
        assertNull(missing.texture());
        assertEquals("†", missing.glyph());
        assertTrue(missing.tinted(), "a glyph fallback is tinted like the heading text");
        assertEquals("x", TooltipGlyphSupport.resolveIcon(icon, id -> false, cp -> false).glyph(),
                "and the glyph's own ASCII fallback when the font lacks it");
    }

    @Test
    void textureIconsAreDrawnAtAnIntegerSizeThatKeepsSixteenPixelArtSharp() {
        int size = TooltipGroupIcon.Texture.SIZE;
        assertEquals(8, size, "16 px art -> exactly 0.5 / 1 / 2 screen px per texel at GUI 1 / 2 / 4");
        assertTrue(size <= 9, "fits inside the heading's text row");
    }

    @Test
    void thereIsOneUniversalPropertiesGroupAndNoBlockPropertiesGroup() {
        assertNull(TooltipGroups.get(Identifier.fromNamespaceAndPath("totality", "block_properties")));
        assertTrue(TooltipGroups.PROPERTIES.leadsFor().containsAll(Set.of(ItemType.BLOCK, ItemType.DECORATIVE)),
                "block items lead with the shared Properties group");
        long named = TooltipGroups.registered().stream().filter(g -> g.id().getPath().contains("properties")).count();
        assertEquals(1, named);
    }

    @Test
    void requirementsEnergyAndDurabilityAreBottomSectionsInThatOrder() {
        for (TooltipGroup g : List.of(TooltipGroups.REQUIREMENTS, TooltipGroups.ENERGY, TooltipGroups.DURABILITY)) {
            assertEquals(TooltipGroup.Placement.BOTTOM, g.placement(), g.id().toString());
        }
        assertTrue(TooltipGroups.REQUIREMENTS.priority() < TooltipGroups.ENERGY.priority());
        assertTrue(TooltipGroups.ENERGY.priority() < TooltipGroups.DURABILITY.priority());
        for (TooltipGroup g : List.of(TooltipGroups.MINING, TooltipGroups.COMBAT, TooltipGroups.PROPERTIES, TooltipGroups.MAGIC)) {
            assertEquals(TooltipGroup.Placement.ORDINARY, g.placement());
        }
    }

    @Test
    void everyGroupHasAnIcon() {
        for (TooltipGroup group : TooltipGroups.registered()) assertNotNull(group.icon(), group.id().toString());
        assertThrows(IllegalArgumentException.class, () -> new TooltipGroup(
                Identifier.fromNamespaceAndPath("test", "no_icon"), 1, null, Set.of()));
    }

    @Test
    void abilitiesEnchantmentsAndPropertiesAreDistinctIdentities() {
        assertNotEquals(TooltipGroups.ABILITIES.id(), TooltipGroups.ENCHANTMENTS.id());
        assertNotEquals(TooltipGroups.ABILITIES.id(), TooltipGroups.PROPERTIES.id());
        assertNotEquals(TooltipGroups.ENCHANTMENTS.id(), TooltipGroups.PROPERTIES.id());
    }

    @Test
    void builtInsAreRegisteredInOrderAndResolvableById() {
        assertEquals(BUILT_INS, TooltipGroups.registered().subList(0, BUILT_INS.size()));
        for (TooltipGroup group : BUILT_INS) assertSame(group, TooltipGroups.get(group.id()));
        assertNull(TooltipGroups.get(Identifier.fromNamespaceAndPath("totality", "does_not_exist")));
    }

    @Test
    void integrationsCanRegisterTheirOwnGroupButNotReuseAnId() {
        TooltipGroup custom = new TooltipGroup(Identifier.fromNamespaceAndPath("grouptest", "fluids"), 450,
                new TooltipGroupIcon.Texture(Identifier.fromNamespaceAndPath("grouptest", "textures/gui/fluids.png"),
                        new TooltipGroupIcon.Glyph("~", "~")), Set.of());
        assertSame(custom, TooltipGroups.register(custom));
        assertSame(custom, TooltipGroups.get(custom.id()));
        assertThrows(IllegalStateException.class, () -> TooltipGroups.register(new TooltipGroup(
                TooltipGroups.MINING.id(), 1, new TooltipGroupIcon.Glyph("*", "*"), Set.of())));
    }

    @Test
    void theMiningToolExampleOrderFollowsDefaultPriorities() {
        assertTrue(TooltipGroups.MINING.priority() < TooltipGroups.COMBAT.priority());
        assertTrue(TooltipGroups.COMBAT.priority() < TooltipGroups.PROPERTIES.priority());
        assertTrue(TooltipGroups.PROPERTIES.priority() < TooltipGroups.ENCHANTMENTS.priority());
    }

    @Test
    void primaryClassificationsLeadTheirNaturalGroup() {
        assertTrue(TooltipGroups.MINING.leadsFor().contains(ItemType.TOOL));
        assertTrue(TooltipGroups.COMBAT.leadsFor().contains(ItemType.WEAPON));
    }

    @Test
    void aGlyphIconFallsBackWhenTheFontCannotRenderItsSymbol() {
        TooltipGroupIcon.Glyph pick = new TooltipGroupIcon.Glyph("⛏", "*");
        assertTrue(pick.tinted());
        assertEquals("⛏", pick.resolve(cp -> true));
        assertEquals("*", pick.resolve(cp -> cp < 0x2000));
    }

    @Test
    void everyBuiltInTextureHasAnAsciiGlyphFallback() {
        for (TooltipGroup group : BUILT_INS) {
            TooltipGroupIcon.Glyph glyph = ((TooltipGroupIcon.Texture) group.icon()).fallback();
            assertTrue(glyph.fallback().chars().allMatch(c -> c >= 0x21 && c < 0x7F), group.id() + " fallback must be ASCII");
        }
    }
}
