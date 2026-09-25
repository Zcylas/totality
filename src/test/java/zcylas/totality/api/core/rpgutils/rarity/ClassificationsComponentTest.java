package zcylas.totality.api.core.rpgutils.rarity;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Paired classifications and their compatibility with the original flat category-list format. */
class ClassificationsComponentTest {

    private static final ClassificationsComponent NETHERITE_AXE = ClassificationsComponent.of(
            Classification.of(ItemType.TOOL, ClassificationTypes.AXE),
            Classification.of(ItemType.WEAPON, ClassificationTypes.TWO_HANDED));

    private static ClassificationsComponent decode(String json) {
        return ClassificationsComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static JsonElement encode(ClassificationsComponent component) {
        return ClassificationsComponent.CODEC.encodeStart(JsonOps.INSTANCE, component).getOrThrow();
    }

    @Test
    void legacyFlatListStillDecodesAsCategoryOnlyEntriesInOrder() {
        ClassificationsComponent decoded = decode("[\"battery\", \"energy\"]");
        assertEquals(List.of(Classification.of(ItemType.BATTERY), Classification.of(ItemType.ENERGY)), decoded.entries());
        assertEquals(List.of(ItemType.BATTERY, ItemType.ENERGY), decoded.ordered());
    }

    @Test
    void categoryOnlyEntriesReEncodeInTheOriginalFlatFormat() {
        assertEquals(JsonParser.parseString("[\"battery\",\"energy\"]"),
                encode(ClassificationsComponent.of(ItemType.BATTERY, ItemType.ENERGY)));
    }

    @Test
    void pairedEntriesEncodeAsCategoryTypeObjectsAndRoundTrip() {
        JsonElement json = encode(NETHERITE_AXE);
        assertEquals(JsonParser.parseString(
                "[{\"category\":\"tool\",\"type\":\"totality:axe\"},{\"category\":\"weapon\",\"type\":\"totality:two_handed\"}]"), json);
        assertEquals(NETHERITE_AXE, decode(json.toString()));
    }

    @Test
    void pairedAndLegacyEntriesMayBeMixed() {
        ClassificationsComponent decoded = decode("[{\"category\":\"tool\",\"type\":\"totality:axe\"}, \"magical\"]");
        assertEquals(List.of(Classification.of(ItemType.TOOL, ClassificationTypes.AXE), Classification.of(ItemType.MAGICAL)),
                decoded.entries());
    }

    @Test
    void aPairObjectWithoutATypeIsACategoryOnlyEntry() {
        assertEquals(List.of(Classification.of(ItemType.WEAPON)), decode("[{\"category\":\"weapon\"}]").entries());
    }

    @Test
    void anUnregisteredPersistedTypeIsKeptOnDecodeNotDropped() {
        // e.g. the integration that registered it is gone — the stack's data must survive.
        ClassificationsComponent decoded = decode("[{\"category\":\"tool\",\"type\":\"othermod:scythe\"}]");
        assertEquals(Optional.of(Identifier.fromNamespaceAndPath("othermod", "scythe")), decoded.entries().get(0).type());
    }

    @Test
    void networkCodecRoundTripsPairsAndCategoryOnlyEntries() {
        ClassificationsComponent mixed = new ClassificationsComponent(List.of(
                Classification.of(ItemType.TOOL, ClassificationTypes.AXE), Classification.of(ItemType.ENERGY)));
        ByteBuf buf = Unpooled.buffer();
        ClassificationsComponent.STREAM_CODEC.encode(buf, mixed);
        assertEquals(mixed, ClassificationsComponent.STREAM_CODEC.decode(buf));
    }

    @Test
    void authoringRejectsUnregisteredTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> Classification.of(ItemType.TOOL, Identifier.fromNamespaceAndPath("totality", "not_registered")));
    }

    @Test
    void integrationsCanRegisterAdditionalTypesButNotTwice() {
        Identifier scythe = Identifier.fromNamespaceAndPath("classificationtest", "scythe");
        assertFalse(ClassificationTypes.isRegistered(scythe));
        ClassificationTypes.register(scythe);
        assertTrue(ClassificationTypes.isRegistered(scythe));
        assertEquals(Optional.of(scythe), Classification.of(ItemType.TOOL, scythe).type());
        assertThrows(IllegalStateException.class, () -> ClassificationTypes.register(scythe));
    }

    @Test
    void builtInTypesAreNamespacedAndLocalizedUnderAStableKey() {
        assertEquals("totality", ClassificationTypes.TWO_HANDED.getNamespace());
        assertEquals("classification_type.totality.two_handed", ClassificationTypes.translationKey(ClassificationTypes.TWO_HANDED));
        assertEquals("two-handed", ClassificationTypes.fallbackName(ClassificationTypes.TWO_HANDED));
        assertTrue(ClassificationTypes.registered().containsAll(List.of(
                ClassificationTypes.AXE, ClassificationTypes.PICKAXE, ClassificationTypes.TWO_HANDED, ClassificationTypes.BATTERY)));
    }

    @Test
    void registeredTypesAreReturnedInRegistrationOrderAsAnUnmodifiableSnapshot() {
        List<Identifier> builtIns = List.of(ClassificationTypes.AXE, ClassificationTypes.PICKAXE,
                ClassificationTypes.TWO_HANDED, ClassificationTypes.BATTERY);
        List<Identifier> registered = List.copyOf(ClassificationTypes.registered());
        assertEquals(builtIns, registered.subList(0, builtIns.size()), "built-ins first, in registration order");
        Identifier later = Identifier.fromNamespaceAndPath("classificationtest", "zzz_registered_later");
        ClassificationTypes.register(later);
        List<Identifier> after = List.copyOf(ClassificationTypes.registered());
        assertEquals(later, after.get(after.size() - 1), "a later registration iterates last");
        assertThrows(UnsupportedOperationException.class, () -> ClassificationTypes.registered().add(later));
    }

    @Test
    void theLegacyCategoryViewIsUnchangedForCategoryBasedChecks() {
        assertEquals(List.of(ItemType.TOOL, ItemType.WEAPON), NETHERITE_AXE.ordered());
    }
}
