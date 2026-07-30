package zcylas.totality.api.core.rpgutils.rarity;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import net.minecraft.network.codec.ByteBufCodecs;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tests for the Artifact -> Ancient rarity migration. Uses {@link RarityComponent#STREAM_CODEC}
 * with a plain Netty {@link ByteBuf} rather than a Minecraft-registry-backed codec path — no
 * bootstrap required.
 */
class ItemRarityAncientMigrationTest {

    @Test
    void standardLadderEndsInAncientNotArtifact() {
        assertTrue(Set.of(ItemRarity.values()).contains(ItemRarity.ANCIENT));
        assertEquals(RarityFamily.STANDARD, ItemRarity.ANCIENT.family());
    }

    @Test
    void artifactIsAbsentAsAStandardRarityConstant() {
        for (ItemRarity rarity : ItemRarity.values()) {
            assertNotEquals("ARTIFACT", rarity.name(), "Artifact must no longer exist as a rarity enum constant");
        }
    }

    @Test
    void ancientResolvesToTheStandardFamilyLikeTheRestOfTheLadder() {
        assertEquals(RarityFamily.STANDARD, ItemRarity.COMMON.family());
        assertEquals(RarityFamily.STANDARD, ItemRarity.MYTHICAL.family());
        assertEquals(RarityFamily.STANDARD, ItemRarity.ANCIENT.family());
    }

    @Test
    void industrialFamilyIsIntact() {
        assertEquals(RarityFamily.INDUSTRIAL, ItemRarity.CRUDE.family());
        assertEquals(RarityFamily.INDUSTRIAL, ItemRarity.CALIBRATED.family());
        assertEquals(RarityFamily.INDUSTRIAL, ItemRarity.REINFORCED.family());
        assertEquals(RarityFamily.INDUSTRIAL, ItemRarity.PROTOTYPE.family());
        assertEquals(RarityFamily.INDUSTRIAL, ItemRarity.OVERCHARGED.family());
        assertEquals(RarityFamily.INDUSTRIAL, ItemRarity.MASTERWORK.family());
    }

    @Test
    void religiousFamilyIsIntact() {
        assertEquals(RarityFamily.RELIGIOUS, ItemRarity.BLESSED.family());
        assertEquals(RarityFamily.RELIGIOUS, ItemRarity.SACRED.family());
        assertEquals(RarityFamily.RELIGIOUS, ItemRarity.CELESTIAL.family());
        assertEquals(RarityFamily.RELIGIOUS, ItemRarity.DIVINE.family());
        assertEquals(RarityFamily.RELIGIOUS, ItemRarity.GODFORGED.family());
    }

    @Test
    void specialFamilyIsIntact() {
        assertEquals(RarityFamily.SPECIAL, ItemRarity.FORBIDDEN.family());
        assertEquals(RarityFamily.SPECIAL, ItemRarity.CURSED.family());
        assertEquals(RarityFamily.SPECIAL, ItemRarity.QUEST.family());
    }

    @Test
    void getSerializedNameNeverProducesTheLegacyArtifactString() {
        for (ItemRarity rarity : ItemRarity.values()) {
            assertNotEquals("artifact", rarity.getSerializedName());
        }
    }

    @Test
    void streamCodecDecodesLegacyArtifactBytesAsAncient() {
        ByteBuf buf = Unpooled.buffer();
        ByteBufCodecs.STRING_UTF8.encode(buf, "artifact");
        RarityComponent decoded = RarityComponent.STREAM_CODEC.decode(buf);
        assertEquals(ItemRarity.ANCIENT, decoded.rarity(),
                "a stack persisted before the migration must still decode to Ancient, not throw or silently invalidate");
    }

    @Test
    void streamCodecStillDecodesEveryCurrentRarityNormally() {
        for (ItemRarity rarity : ItemRarity.values()) {
            ByteBuf buf = Unpooled.buffer();
            ByteBufCodecs.STRING_UTF8.encode(buf, rarity.getSerializedName());
            RarityComponent decoded = RarityComponent.STREAM_CODEC.decode(buf);
            assertEquals(rarity, decoded.rarity());
        }
    }

    @Test
    void streamCodecRoundTripsAncientWithoutGoingThroughTheLegacyAlias() {
        ByteBuf buf = Unpooled.buffer();
        RarityComponent.STREAM_CODEC.encode(buf, new RarityComponent(ItemRarity.ANCIENT));
        RarityComponent decoded = RarityComponent.STREAM_CODEC.decode(buf);
        assertEquals(ItemRarity.ANCIENT, decoded.rarity());
    }
}
