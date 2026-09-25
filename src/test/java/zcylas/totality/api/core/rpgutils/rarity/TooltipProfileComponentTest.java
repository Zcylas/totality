package zcylas.totality.api.core.rpgutils.rarity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TooltipProfileComponentTest {

    @Test
    void standardAuthorsNothing() {
        TooltipProfileComponent p = TooltipProfileComponent.STANDARD;
        assertEquals(TooltipPreviewMode.AUTO, p.previewMode());
        assertEquals(TooltipPreviewMotion.AUTO, p.previewMotion());
        assertEquals(TooltipCompanionPreview.NONE, p.companionPreview());
        assertEquals(Optional.empty(), p.vignetteStyle());
        assertEquals(Optional.empty(), p.dividerStyle());
    }

    @Test
    void withMethodsChangeOnlyTheirOwnFieldAndNeverMutateStandard() {
        TooltipProfileComponent p = TooltipProfileComponent.STANDARD.withPreview(TooltipPreviewMode.ITEM_MODEL);
        assertEquals(TooltipPreviewMode.ITEM_MODEL, p.previewMode());
        assertEquals(TooltipPreviewMotion.AUTO, p.previewMotion());
        assertEquals(TooltipPreviewMode.AUTO, TooltipProfileComponent.STANDARD.previewMode());
        TooltipProfileComponent q = p.withMotion(TooltipPreviewMotion.SLOW_ROTATE)
                .withCompanion(TooltipCompanionPreview.EQUIPPED_PLAYER)
                .withVignette(TooltipVignetteStyle.EDGE_FRAME)
                .withDivider(TooltipDividerStyle.ORNAMENT);
        assertEquals(TooltipPreviewMode.ITEM_MODEL, q.previewMode());
        assertEquals(TooltipPreviewMotion.SLOW_ROTATE, q.previewMotion());
        assertEquals(TooltipCompanionPreview.EQUIPPED_PLAYER, q.companionPreview());
        assertEquals(Optional.of(TooltipVignetteStyle.EDGE_FRAME), q.vignetteStyle());
        assertEquals(Optional.of(TooltipDividerStyle.ORNAMENT), q.dividerStyle());
    }

    @Test
    void anEmptyProfileSavedBeforeThisSliceStillDecodesAsStandard() {
        // The pre-V2 marker component encoded as an empty object.
        TooltipProfileComponent decoded = TooltipProfileComponent.CODEC
                .parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        assertEquals(TooltipProfileComponent.STANDARD, decoded);
    }

    @Test
    void authoredProfileRoundTripsThroughThePersistentCodec() {
        TooltipProfileComponent authored = TooltipProfileComponent.STANDARD
                .withPreview(TooltipPreviewMode.BLOCK_MODEL).withMotion(TooltipPreviewMotion.STATIC)
                .withCompanion(TooltipCompanionPreview.EQUIPPED_PLAYER).withVignette(TooltipVignetteStyle.BOTTOM_GLOW)
                .withDivider(TooltipDividerStyle.GRADIENT);
        var json = TooltipProfileComponent.CODEC.encodeStart(JsonOps.INSTANCE, authored).getOrThrow();
        assertEquals(authored, TooltipProfileComponent.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void jsonFieldNamesAreReadableForDataAuthoring() {
        var json = JsonParser.parseString("{\"preview\":\"item_model\",\"preview_motion\":\"slow_rotate\","
                + "\"companion_preview\":\"equipped_player\",\"vignette\":\"diagonal_sweep\",\"divider\":\"none\"}");
        TooltipProfileComponent decoded = TooltipProfileComponent.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(TooltipPreviewMode.ITEM_MODEL, decoded.previewMode());
        assertEquals(TooltipPreviewMotion.SLOW_ROTATE, decoded.previewMotion());
        assertEquals(TooltipCompanionPreview.EQUIPPED_PLAYER, decoded.companionPreview());
        assertEquals(Optional.of(TooltipVignetteStyle.DIAGONAL_SWEEP), decoded.vignetteStyle());
        assertEquals(Optional.of(TooltipDividerStyle.NONE), decoded.dividerStyle());
    }

    @Test
    void profilesSavedBeforeGroupOrderExistedDecodeWithTheDefaultOrder() {
        var json = JsonParser.parseString("{\"preview\":\"item_model\"}");
        assertEquals(java.util.List.of(), TooltipProfileComponent.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().groupOrder());
        assertEquals(java.util.List.of(), TooltipProfileComponent.STANDARD.groupOrder());
    }

    @Test
    void anAuthoredGroupOrderRoundTripsThroughBothCodecs() {
        var magic = net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "magic");
        var mining = net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "mining");
        TooltipProfileComponent authored = TooltipProfileComponent.STANDARD.withGroupOrder(magic, mining)
                .withPreview(TooltipPreviewMode.ITEM_MODEL);
        assertEquals(java.util.List.of(magic, mining), authored.groupOrder(), "other with* methods keep the order");
        var json = TooltipProfileComponent.CODEC.encodeStart(JsonOps.INSTANCE, authored).getOrThrow();
        assertEquals(JsonParser.parseString("[\"totality:magic\",\"totality:mining\"]"), json.getAsJsonObject().get("group_order"));
        assertEquals(authored, TooltipProfileComponent.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        var buf = io.netty.buffer.Unpooled.buffer();
        TooltipProfileComponent.STREAM_CODEC.encode(buf, authored);
        assertEquals(authored, TooltipProfileComponent.STREAM_CODEC.decode(buf));
    }
}
