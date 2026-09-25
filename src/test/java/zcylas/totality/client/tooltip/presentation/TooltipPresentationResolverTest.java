package zcylas.totality.client.tooltip.presentation;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.core.rpgutils.rarity.TooltipCompanionPreview;
import zcylas.totality.api.core.rpgutils.rarity.TooltipDividerStyle;
import zcylas.totality.api.core.rpgutils.rarity.TooltipPreviewMode;
import zcylas.totality.api.core.rpgutils.rarity.TooltipPreviewMotion;
import zcylas.totality.api.core.rpgutils.rarity.TooltipProfileComponent;
import zcylas.totality.api.core.rpgutils.rarity.TooltipVignetteStyle;
import zcylas.totality.client.tooltip.presentation.TooltipPresentationResolver.AutoFacts;

import static org.junit.jupiter.api.Assertions.*;

class TooltipPresentationResolverTest {

    private static final AutoFacts FLAT_SPRITE = new AutoFacts(true, false, false);
    private static final AutoFacts ITEM_3D = new AutoFacts(false, false, false);
    private static final AutoFacts CUBE_BLOCK = new AutoFacts(false, true, false);
    private static final AutoFacts FLAT_ARMOR = new AutoFacts(true, false, true);

    private static TooltipPresentation auto(AutoFacts facts) {
        return TooltipPresentationResolver.resolve(TooltipProfileComponent.STANDARD, facts);
    }

    @Test
    void autoPreviewModeFollowsTheItemsOwnGuiModel() {
        assertEquals(TooltipPreviewMode.SPRITE, auto(FLAT_SPRITE).previewMode());
        assertEquals(TooltipPreviewMode.ITEM_MODEL, auto(ITEM_3D).previewMode());
    }

    @Test
    void autoNeverPicksTheBlockModelForABlockItem() {
        // A block's default placed state can differ from its inventory model (a fence's is a bare post).
        assertEquals(TooltipPreviewMode.ITEM_MODEL, auto(CUBE_BLOCK).previewMode());
        assertEquals(TooltipPreviewMode.SPRITE, auto(new AutoFacts(true, true, false)).previewMode(),
                "a block item with a flat inventory sprite (e.g. a door) keeps its sprite");
    }

    @Test
    void autoMotionIsAlwaysStatic() {
        for (AutoFacts facts : new AutoFacts[]{FLAT_SPRITE, ITEM_3D, CUBE_BLOCK, FLAT_ARMOR}) {
            assertEquals(TooltipPreviewMotion.STATIC, auto(facts).previewMotion());
        }
        TooltipProfileComponent blockAutoMotion = TooltipProfileComponent.STANDARD.withPreview(TooltipPreviewMode.BLOCK_MODEL);
        assertEquals(TooltipPreviewMotion.STATIC, TooltipPresentationResolver.resolve(blockAutoMotion, CUBE_BLOCK).previewMotion());
    }

    @Test
    void explicitPreviewModeOverridesAutoInference() {
        // The Iron Ingot example: authored SPRITE today, switchable to ITEM_MODEL by authoring alone.
        TooltipProfileComponent sprite = TooltipProfileComponent.STANDARD.withPreview(TooltipPreviewMode.SPRITE);
        TooltipProfileComponent model = TooltipProfileComponent.STANDARD.withPreview(TooltipPreviewMode.ITEM_MODEL);
        TooltipProfileComponent block = TooltipProfileComponent.STANDARD.withPreview(TooltipPreviewMode.BLOCK_MODEL);
        assertEquals(TooltipPreviewMode.SPRITE, TooltipPresentationResolver.resolve(sprite, CUBE_BLOCK).previewMode());
        assertEquals(TooltipPreviewMode.ITEM_MODEL, TooltipPresentationResolver.resolve(model, FLAT_SPRITE).previewMode());
        assertEquals(TooltipPreviewMode.BLOCK_MODEL, TooltipPresentationResolver.resolve(block, CUBE_BLOCK).previewMode());
        assertEquals(TooltipPreviewMode.BLOCK_MODEL, TooltipPresentationResolver.resolve(block, new AutoFacts(true, true, false)).previewMode(),
                "authored BLOCK_MODEL wins even when the inventory model is a flat sprite");
    }

    @Test
    void theBlockModelFactIsOnlyNeededWhenBlockModelIsExplicitlyAuthored() {
        // AUTO never picks BLOCK_MODEL, so ordinary AUTO items must not pay for a block-model resolution.
        assertFalse(TooltipPresentationResolver.needsBlockModelFact(TooltipProfileComponent.STANDARD));
        for (TooltipPreviewMode mode : TooltipPreviewMode.values()) {
            assertEquals(mode == TooltipPreviewMode.BLOCK_MODEL, TooltipPresentationResolver.needsBlockModelFact(
                    TooltipProfileComponent.STANDARD.withPreview(mode)), mode.name());
        }
    }

    @Test
    void whenTheBlockModelFactIsNotNeededItsValueCannotChangeTheResult() {
        for (TooltipPreviewMode mode : TooltipPreviewMode.values()) {
            TooltipProfileComponent authored = TooltipProfileComponent.STANDARD.withPreview(mode);
            if (TooltipPresentationResolver.needsBlockModelFact(authored)) continue;
            for (boolean flat : new boolean[]{true, false}) {
                assertEquals(TooltipPresentationResolver.resolve(authored, new AutoFacts(flat, true, false)),
                        TooltipPresentationResolver.resolve(authored, new AutoFacts(flat, false, false)), mode.name());
            }
        }
    }

    @Test
    void authoredBlockModelWithoutABlockModelToDrawResolvesToTheItemModel() {
        TooltipProfileComponent block = TooltipProfileComponent.STANDARD.withPreview(TooltipPreviewMode.BLOCK_MODEL);
        assertEquals(TooltipPreviewMode.ITEM_MODEL, TooltipPresentationResolver.resolve(block, ITEM_3D).previewMode());
    }

    @Test
    void explicitMotionOverridesAutoForModels() {
        TooltipProfileComponent staticModel = TooltipProfileComponent.STANDARD.withMotion(TooltipPreviewMotion.STATIC);
        assertEquals(TooltipPreviewMotion.STATIC, TooltipPresentationResolver.resolve(staticModel, CUBE_BLOCK).previewMotion());
        TooltipProfileComponent rotatingModel = TooltipProfileComponent.STANDARD
                .withPreview(TooltipPreviewMode.ITEM_MODEL).withMotion(TooltipPreviewMotion.SLOW_ROTATE);
        assertTrue(TooltipPresentationResolver.resolve(rotatingModel, FLAT_SPRITE).rotates());
    }

    @Test
    void spritePreviewsNeverRotateBecauseTheGuiAtlasImageIsFlat() {
        TooltipProfileComponent rotatingSprite = TooltipProfileComponent.STANDARD
                .withPreview(TooltipPreviewMode.SPRITE).withMotion(TooltipPreviewMotion.SLOW_ROTATE);
        TooltipPresentation resolved = TooltipPresentationResolver.resolve(rotatingSprite, ITEM_3D);
        assertEquals(TooltipPreviewMode.SPRITE, resolved.previewMode());
        assertEquals(TooltipPreviewMotion.STATIC, resolved.previewMotion());
    }

    @Test
    void companionPreviewIsOptInOnly() {
        assertEquals(TooltipCompanionPreview.NONE, auto(FLAT_ARMOR).companionPreview(),
                "armor must not get a player card just for being armor");
        TooltipProfileComponent optedIn = TooltipProfileComponent.STANDARD.withCompanion(TooltipCompanionPreview.EQUIPPED_PLAYER);
        assertEquals(TooltipCompanionPreview.EQUIPPED_PLAYER,
                TooltipPresentationResolver.resolve(optedIn, FLAT_ARMOR).companionPreview());
    }

    @Test
    void companionPreviewIsDroppedForItemsThatCannotBeWornInAnArmorSlot() {
        TooltipProfileComponent optedIn = TooltipProfileComponent.STANDARD.withCompanion(TooltipCompanionPreview.EQUIPPED_PLAYER);
        assertEquals(TooltipCompanionPreview.NONE, TooltipPresentationResolver.resolve(optedIn, ITEM_3D).companionPreview());
    }

    @Test
    void radialIsTheDefaultVignetteForEveryUnauthoredItem() {
        assertEquals(TooltipVignetteStyle.RADIAL, TooltipPresentationResolver.DEFAULT_VIGNETTE);
        for (AutoFacts facts : new AutoFacts[]{FLAT_SPRITE, ITEM_3D, CUBE_BLOCK, FLAT_ARMOR}) {
            assertEquals(TooltipVignetteStyle.RADIAL, auto(facts).vignetteStyle());
        }
    }

    @Test
    void gradientOrnamentIsTheDefaultDivider() {
        assertEquals(TooltipDividerStyle.GRADIENT_ORNAMENT, TooltipPresentationResolver.DEFAULT_DIVIDER);
        assertEquals(TooltipDividerStyle.GRADIENT_ORNAMENT, auto(CUBE_BLOCK).dividerStyle());
    }

    @Test
    void everyVignetteAndDividerStyleCanBeExplicitlyAuthored() {
        for (TooltipVignetteStyle style : TooltipVignetteStyle.values()) {
            assertEquals(style, TooltipPresentationResolver.resolve(
                    TooltipProfileComponent.STANDARD.withVignette(style), FLAT_SPRITE).vignetteStyle());
        }
        for (TooltipDividerStyle style : TooltipDividerStyle.values()) {
            assertEquals(style, TooltipPresentationResolver.resolve(
                    TooltipProfileComponent.STANDARD.withDivider(style), FLAT_SPRITE).dividerStyle());
        }
    }

    @Test
    void theV2VignetteAndDividerStyleSetsAreExactlyTheAgreedOnes() {
        assertArrayEquals(new TooltipVignetteStyle[]{TooltipVignetteStyle.RADIAL, TooltipVignetteStyle.BOTTOM_GLOW,
                TooltipVignetteStyle.DIAGONAL_SWEEP, TooltipVignetteStyle.EDGE_FRAME}, TooltipVignetteStyle.values());
        assertArrayEquals(new TooltipDividerStyle[]{TooltipDividerStyle.GRADIENT, TooltipDividerStyle.ORNAMENT,
                TooltipDividerStyle.GRADIENT_ORNAMENT, TooltipDividerStyle.NONE}, TooltipDividerStyle.values());
    }

    @Test
    void aResolvedPresentationNeverContainsAuto() {
        assertThrows(IllegalArgumentException.class, () -> new TooltipPresentation(TooltipPreviewMode.AUTO,
                TooltipPreviewMotion.STATIC, TooltipCompanionPreview.NONE, TooltipVignetteStyle.RADIAL, TooltipDividerStyle.NONE));
        assertThrows(IllegalArgumentException.class, () -> new TooltipPresentation(TooltipPreviewMode.SPRITE,
                TooltipPreviewMotion.AUTO, TooltipCompanionPreview.NONE, TooltipVignetteStyle.RADIAL, TooltipDividerStyle.NONE));
    }
}
