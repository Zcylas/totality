package zcylas.totality.api.mining;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.mining.BlockProfile.Classification;
import zcylas.totality.api.mining.BlockProfile.Form;
import zcylas.totality.api.mining.BlockProfile.Layer;
import zcylas.totality.api.mining.BlockProfile.Material;
import zcylas.totality.api.mining.BlockProfile.Ownership;
import zcylas.totality.api.mining.BlockProfile.Tool;
import zcylas.totality.api.mining.BlockProfile.ToolAffinity;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Block/Material Profile precedence and field-wise deferral (Block Breaking V2, Pass 1), over a fake state type. */
class BlockProfileResolverTest {

    /** Stand-in for a block state: the block id, one state flag, and the tags it is in. */
    record FakeState(String block, boolean flag, Set<String> tags) {
        static FakeState of(String block, String... tags) { return new FakeState(block, false, Set.of(tags)); }
        FakeState flagged() { return new FakeState(block, true, tags); }
    }

    private static final Material STONE = new Material("test:stone");
    private static final Material WOOD = new Material("test:wood");
    private static final Form SLAB = new Form("slab");

    private BlockProfileResolver<FakeState, String> resolver;

    /** Compatibility fallback mirroring the real one: hardness-derived max and classification, tier 1, pickaxe. */
    private static BlockProfile fallback(FakeState state, float hardness) {
        Classification c = hardness < 0 ? Classification.UNBREAKABLE : hardness == 0 ? Classification.SPECIAL : Classification.ORDINARY;
        return new BlockProfile(null, null, c, hardness * 100f / 1.5f, ToolAffinity.of(Tool.PICKAXE), 1, Ownership.POSITION);
    }

    @BeforeEach
    void setUp() {
        resolver = new BlockProfileResolver<>(FakeState::block, BlockProfileResolverTest::fallback);
    }

    @Test
    void unauthoredBlockResolvesEntirelyFromTheCompatibilityFallback() {
        var r = resolver.resolve(FakeState.of("granite"), 1.5f);
        assertEquals(100f, r.maxDurability(), 0.001f);
        assertEquals(Layer.FALLBACK, r.durabilityLayer());
        assertEquals(1, r.requiredTier());
        assertEquals(Classification.ORDINARY, r.classification());
        assertNull(r.material());
        assertNull(r.form());
    }

    @Test
    void fullPrecedenceOverrideThenBlockThenMaterialFormThenMaterialThenFallback() {
        resolver.assign("stone_slab", STONE, SLAB);
        resolver.material(STONE, BlockProfile.durability(400f));
        assertEquals(Layer.MATERIAL, resolver.resolve(FakeState.of("stone_slab"), 2f).durabilityLayer());

        resolver.materialForm(STONE, SLAB, BlockProfile.durability(300f));
        var formLevel = resolver.resolve(FakeState.of("stone_slab"), 2f);
        assertEquals(300f, formLevel.maxDurability());
        assertEquals(Layer.MATERIAL_FORM, formLevel.durabilityLayer());

        resolver.block("stone_slab", BlockProfile.durability(250f));
        var blockLevel = resolver.resolve(FakeState.of("stone_slab"), 2f);
        assertEquals(250f, blockLevel.maxDurability());
        assertEquals(Layer.BLOCK, blockLevel.durabilityLayer());

        resolver.stateOverride("stone_slab", FakeState::flag, BlockProfile.durability(500f));
        var overridden = resolver.resolve(FakeState.of("stone_slab").flagged(), 2f);
        assertEquals(500f, overridden.maxDurability());
        assertEquals(Layer.STATE_OVERRIDE, overridden.durabilityLayer());
        // the override applies only to states its predicate matches
        assertEquals(250f, resolver.resolve(FakeState.of("stone_slab"), 2f).maxDurability());
    }

    @Test
    void eachFieldDefersIndependentlyToTheNextLayer() {
        resolver.assign("oak_log", WOOD, Form.FULL_BLOCK);
        resolver.material(WOOD, BlockProfile.EMPTY.withRequiredTier(0).withTools(ToolAffinity.of(Tool.AXE)));
        resolver.block("oak_log", BlockProfile.durability(120f));   // durability only
        var r = resolver.resolve(FakeState.of("oak_log"), 2f);
        assertEquals(120f, r.maxDurability());                     // exact block
        assertEquals(0, r.requiredTier());                         // material default
        assertEquals(ToolAffinity.of(Tool.AXE), r.tools());        // material default
        assertEquals(Classification.ORDINARY, r.classification()); // fallback
        assertEquals(Ownership.POSITION, r.ownership());           // fallback
    }

    @Test
    void exactBlockProfileBeatsABroadTagFamily() {
        resolver.assign(s -> s.tags().contains("logs"), WOOD, Form.FULL_BLOCK);
        resolver.material(WOOD, BlockProfile.durability(100f));
        resolver.block("ancient_log", BlockProfile.durability(900f));
        assertEquals(100f, resolver.resolve(FakeState.of("oak_log", "logs"), 2f).maxDurability());
        assertEquals(900f, resolver.resolve(FakeState.of("ancient_log", "logs"), 2f).maxDurability());
    }

    @Test
    void exactMaterialAssignmentBeatsATagAssignmentAndTheFirstMatchingTagWins() {
        resolver.assign(s -> s.tags().contains("a"), STONE, Form.FULL_BLOCK);
        resolver.assign(s -> s.tags().contains("b"), WOOD, Form.FULL_BLOCK);
        assertEquals(STONE, resolver.resolve(FakeState.of("x", "a", "b"), 1f).material());
        assertEquals(WOOD, resolver.resolve(FakeState.of("y", "b"), 1f).material());
        resolver.assign("x", WOOD, SLAB);
        var r = resolver.resolve(FakeState.of("x", "a", "b"), 1f);
        assertEquals(WOOD, r.material());
        assertEquals(SLAB, r.form());
    }

    @Test
    void formValuesAreAuthoredNeverDerivedFromTheFullBlock() {
        resolver.assign("stone", STONE, Form.FULL_BLOCK);
        resolver.assign("stone_slab", STONE, SLAB);
        resolver.materialForm(STONE, Form.FULL_BLOCK, BlockProfile.durability(100f));
        // no authored slab value: the slab falls through to its own (compatibility) value, not 100 x some ratio
        var slab = resolver.resolve(FakeState.of("stone_slab"), 2f);
        assertEquals(Layer.FALLBACK, slab.durabilityLayer());
        assertEquals(2f * 100f / 1.5f, slab.maxDurability(), 0.001f);
    }

    @Test
    void overrideAndExactProfilesCanReassignMaterialAndForm() {
        resolver.assign("stone_slab", STONE, SLAB);
        resolver.materialForm(WOOD, Form.FULL_BLOCK, BlockProfile.durability(77f));
        resolver.stateOverride("stone_slab", FakeState::flag, BlockProfile.EMPTY.withMaterial(WOOD, Form.FULL_BLOCK));
        var r = resolver.resolve(FakeState.of("stone_slab").flagged(), 2f);
        assertEquals(WOOD, r.material());
        assertEquals(77f, r.maxDurability());
        assertEquals(Layer.MATERIAL_FORM, r.durabilityLayer());
    }

    @Test
    void firstRegisteredMatchingStateOverrideWins() {
        resolver.stateOverride("candle", FakeState::flag, BlockProfile.durability(10f));
        resolver.stateOverride("candle", s -> true, BlockProfile.durability(20f));
        assertEquals(10f, resolver.resolve(FakeState.of("candle").flagged(), 1f).maxDurability());
        assertEquals(20f, resolver.resolve(FakeState.of("candle"), 1f).maxDurability());
    }

    // ── classification ──────────────────────────────────────────────────────────────────────

    @Test
    void fallbackClassificationFollowsHardness() {
        assertEquals(Classification.UNBREAKABLE, resolver.resolve(FakeState.of("bedrock"), -1f).classification());
        assertEquals(Classification.SPECIAL, resolver.resolve(FakeState.of("torch"), 0f).classification());
        assertEquals(Classification.ORDINARY, resolver.resolve(FakeState.of("stone"), 1.5f).classification());
    }

    @Test
    void explicitClassificationIsABehaviourClassNotAnHpValue() {
        resolver.block("spawner", BlockProfile.EMPTY.withClassification(Classification.SPECIAL));
        var r = resolver.resolve(FakeState.of("spawner"), 5f);
        assertEquals(Classification.SPECIAL, r.classification());
        assertFalse(r.ordinary());
        // its numeric durability is untouched: SPECIAL is not encoded as 0 or -1 HP
        assertEquals(5f * 100f / 1.5f, r.maxDurability(), 0.001f);

        resolver.block("vault", BlockProfile.EMPTY.withClassification(Classification.UNBREAKABLE));
        assertEquals(Classification.UNBREAKABLE, resolver.resolve(FakeState.of("vault"), 50f).classification());
    }

    @Test
    void explicitOrdinaryOverridesAFamilyClassification() {
        resolver.assign("crystal", STONE, Form.FULL_BLOCK);
        resolver.material(STONE, BlockProfile.EMPTY.withClassification(Classification.SPECIAL));
        assertEquals(Classification.ORDINARY, resolver.resolve(FakeState.of("other"), 1f).classification(),
                "an unassigned block is unaffected by the family");
        assertEquals(Classification.SPECIAL, resolver.resolve(FakeState.of("crystal"), 1f).classification());
        resolver.block("crystal", BlockProfile.EMPTY.withClassification(Classification.ORDINARY));
        assertEquals(Classification.ORDINARY, resolver.resolve(FakeState.of("crystal"), 1f).classification());
    }

    // ── tools, tier, ownership ───────────────────────────────────────────────────────────────

    @Test
    void toolNeutralAffinityIsEffectiveForEveryToolAndDistinctFromNoTool() {
        assertTrue(ToolAffinity.NEUTRAL.effective(Tool.PICKAXE));
        assertTrue(ToolAffinity.NEUTRAL.effective(Tool.AXE));
        assertTrue(ToolAffinity.NEUTRAL.effective(Tool.SHOVEL));
        assertFalse(ToolAffinity.NONE.effective(Tool.PICKAXE));
        assertNotEquals(ToolAffinity.NEUTRAL, ToolAffinity.NONE);
        var pick = ToolAffinity.of(Tool.PICKAXE);
        assertTrue(pick.effective(Tool.PICKAXE));
        assertFalse(pick.effective(Tool.SHOVEL));
        assertEquals(ToolAffinity.of(Tool.SHOVEL, Tool.PICKAXE).tools().iterator().next(), Tool.PICKAXE, "enum order");

        resolver.block("scaffold", BlockProfile.EMPTY.withTools(ToolAffinity.NEUTRAL));
        assertTrue(resolver.resolve(FakeState.of("scaffold"), 1f).tools().neutral());
    }

    @Test
    void requiredTierKeepsTheVanillaDerivedFallbackUnlessAuthored() {
        assertEquals(1, resolver.resolve(FakeState.of("iron_ore"), 3f).requiredTier());
        resolver.block("iron_ore", BlockProfile.EMPTY.withRequiredTier(3));
        assertEquals(3, resolver.resolve(FakeState.of("iron_ore"), 3f).requiredTier());
    }

    @Test
    void ownershipIsProfileDataWithExactOverridesWinning() {
        resolver.block("chest", BlockProfile.EMPTY.withOwnership(Ownership.POSITION));
        resolver.block("oak_door", BlockProfile.EMPTY.withOwnership(Ownership.DOOR_LOWER_HALF));
        assertEquals(Ownership.POSITION, resolver.resolve(FakeState.of("chest"), 2.5f).ownership());
        assertEquals(Ownership.DOOR_LOWER_HALF, resolver.resolve(FakeState.of("oak_door"), 3f).ownership());
    }

    // ── Pass 3 dataset patterns ─────────────────────────────────────────────────────────────

    @Test
    void doubleSlabStateSwitchesToTheAuthoredDoubleSlabFormOfItsMaterial() {
        Form doubleSlab = new Form("double_slab");
        resolver.material(STONE, BlockProfile.durability(100f));
        resolver.materialForm(STONE, SLAB, BlockProfile.durability(50f));
        resolver.materialForm(STONE, doubleSlab, BlockProfile.durability(100f));
        resolver.assign("stone_slab", STONE, SLAB);
        resolver.stateOverride("stone_slab", FakeState::flag, BlockProfile.EMPTY.withMaterial(STONE, doubleSlab));
        var single = resolver.resolve(FakeState.of("stone_slab"), 2f);
        var dbl = resolver.resolve(FakeState.of("stone_slab").flagged(), 2f);
        assertEquals(50f, single.maxDurability());
        assertEquals(SLAB, single.form());
        assertEquals(100f, dbl.maxDurability());
        assertEquals(doubleSlab, dbl.form());
        assertEquals(Layer.MATERIAL_FORM, dbl.durabilityLayer(), "the value is the material's authored double-slab form, not a formula");
    }

    @Test
    void provenanceIsTrackedPerFieldForCoverageReporting() {
        resolver.block("torch", BlockProfile.EMPTY.withClassification(Classification.SPECIAL));
        var torch = resolver.resolve(FakeState.of("torch"), 0f);
        assertEquals(Layer.BLOCK, torch.classificationLayer());
        assertEquals(Layer.FALLBACK, torch.durabilityLayer());
        assertEquals(Layer.FALLBACK, torch.toolsLayer());
        var fallbackSpecial = resolver.resolve(FakeState.of("flower"), 0f);
        assertEquals(Classification.SPECIAL, fallbackSpecial.classification());
        assertEquals(Layer.FALLBACK, fallbackSpecial.classificationLayer(), "hardness-derived SPECIAL is NOT an authored decision");
    }

    @Test
    void tagBackedSpecialGroupIsAClassificationOnlyMaterialAndExactProfilesStillWin() {
        Material crops = new Material("totality:special/crops");
        resolver.material(crops, BlockProfile.EMPTY.withClassification(Classification.SPECIAL));
        resolver.assign(s -> s.tags().contains("crops"), crops, Form.FULL_BLOCK);
        assertEquals(Classification.SPECIAL, resolver.resolve(FakeState.of("wheat", "crops"), 0f).classification());
        resolver.block("cocoa", BlockProfile.EMPTY.withClassification(Classification.ORDINARY).withMaxDurability(25f));
        var cocoa = resolver.resolve(FakeState.of("cocoa", "crops"), 0.2f);
        assertEquals(Classification.ORDINARY, cocoa.classification());
        assertEquals(25f, cocoa.maxDurability());
    }

    // ── transformations ──────────────────────────────────────────────────────────────────────

    @Test
    void transformationsAreExplicitDirectionalAndRemovable() {
        assertFalse(resolver.transformsTo("oak_log", "stripped_oak_log"));
        resolver.transformation("oak_log", "stripped_oak_log");
        assertTrue(resolver.transformsTo("oak_log", "stripped_oak_log"));
        assertFalse(resolver.transformsTo("stripped_oak_log", "oak_log"), "not implied in reverse");
        assertFalse(resolver.transformsTo("oak_log", "dirt"), "never an unrelated block");
        resolver.removeTransformation("oak_log", "stripped_oak_log");
        assertFalse(resolver.transformsTo("oak_log", "stripped_oak_log"));
    }
}
