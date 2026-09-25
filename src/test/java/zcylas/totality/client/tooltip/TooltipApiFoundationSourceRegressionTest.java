package zcylas.totality.client.tooltip;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source/import regression sentinels for the Tooltip API foundation pass, following the
 * established convention in {@code DndPotionOfHealingSourceRegressionTest}: the classes under
 * test here are {@code Item} subclasses, their registration sites, or client-only renderer code
 * that cannot be exercised end-to-end under plain JUnit (an {@code Item} cannot be constructed
 * once the registry is frozen — confirmed by {@code HealingPotionItemContractTest}'s class
 * Javadoc — and a client renderer needs a bootstrapped/GL-initialized {@code Font}, which is
 * equally unavailable here).
 *
 * <p><b>Evidentiary limits:</b> every test in this file is a source-text regression
 * <i>sentinel</i>, not proof of runtime behavior. It can confirm a particular token exists (or
 * is absent) in the current source text; it cannot execute the code or observe actual rendering.
 * Runtime confirmation for these is the manual visual validation recorded in the implementation
 * report.
 */
class TooltipApiFoundationSourceRegressionTest {

    private static final Path ENERGY_ITEMS =
            Path.of("src/main/java/zcylas/totality/init/items/EnergyItems.java");
    private static final Path RENDERER =
            Path.of("src/main/java/zcylas/totality/client/tooltip/TotalityTooltipRenderer.java");
    private static final Path GRIMOIRE_ITEM =
            Path.of("src/main/java/zcylas/totality/item/magic/GrimoireItem.java");
    private static final Path RING_OF_PROTECTION =
            Path.of("src/main/java/zcylas/totality/item/equipment/RingOfProtectionItem.java");
    private static final Path BATTERY_ITEM =
            Path.of("src/main/java/zcylas/totality/item/energy/BatteryItem.java");
    private static final Path BASIC_WEAPON_ITEMS =
            Path.of("src/main/java/zcylas/totality/init/items/BasicWeaponItems.java");
    private static final Path MIXIN =
            Path.of("src/main/java/zcylas/totality/mixin/client/AbstractContainerScreenMixin.java");
    private static final Path ITEM_COMPONENTS =
            Path.of("src/main/java/zcylas/totality/api/core/rpgutils/rarity/ItemComponents.java");
    private static final Path ATTUNEMENT_CONTRIBUTOR =
            Path.of("src/main/java/zcylas/totality/client/tooltip/contributor/AttunementContributor.java");
    private static final Path METADATA_CONTRIBUTOR =
            Path.of("src/main/java/zcylas/totality/client/tooltip/contributor/MetadataContributor.java");
    private static final Path GRIMOIRE_CONTRIBUTOR =
            Path.of("src/main/java/zcylas/totality/client/tooltip/contributor/GrimoireContributor.java");
    private static final Path WEAPON_CONTRIBUTOR =
            Path.of("src/main/java/zcylas/totality/client/tooltip/contributor/WeaponContributor.java");
    private static final Path EXTERNAL_CONTENT_CONTRIBUTOR =
            Path.of("src/main/java/zcylas/totality/client/tooltip/contributor/ExternalContentContributor.java");
    private static final Path ENERGY_CONTRIBUTOR =
            Path.of("src/main/java/zcylas/totality/client/tooltip/contributor/EnergyContributor.java");
    private static final Path HEALING_POTION_CONTRIBUTOR =
            Path.of("src/main/java/zcylas/totality/client/tooltip/contributor/HealingPotionContributor.java");
    private static final Path TECHNICAL_INFO_CONTRIBUTOR =
            Path.of("src/main/java/zcylas/totality/client/tooltip/contributor/TechnicalInfoContributor.java");
    private static final Path FUEL_CONTRIBUTOR =
            Path.of("src/main/java/zcylas/totality/client/tooltip/contributor/FuelContributor.java");
    /** Weight moved from its body contributor to the footer (Tooltip V2 bottom-presentation slice). */
    private static final Path WEIGHT_FOOTER =
            Path.of("src/main/java/zcylas/totality/client/tooltip/footer/TooltipFooter.java");
    private static final Path TOTALITY_ITEM =
            Path.of("src/main/java/zcylas/totality/api/item/TotalityItem.java");
    private static final Path TRADING_SCREEN =
            Path.of("src/main/java/zcylas/totality/screen/shop/TradingScreen.java");
    private static final Path SHURIKEN_ITEM =
            Path.of("src/main/java/zcylas/totality/item/base_weapons/ShurikenItem.java");
    private static final Path TOOLTIP_KNOWLEDGE_VIEW =
            Path.of("src/main/java/zcylas/totality/client/tooltip/TooltipKnowledgeView.java");
    private static final Path MOUSE_HANDLER_MIXIN =
            Path.of("src/main/java/zcylas/totality/mixin/MouseHandlerMixin.java");
    private static final Path TOOLTIP_SCROLL_HANDLER =
            Path.of("src/main/java/zcylas/totality/client/tooltip/TotalityTooltipScrollHandler.java");
    private static final Path TOOLTIP_SCROLL_CONTROLLER =
            Path.of("src/main/java/zcylas/totality/client/tooltip/TooltipScrollController.java");
    private static final Path ABSTRACT_CONTAINER_SCREEN_MIXIN =
            Path.of("src/main/java/zcylas/totality/mixin/client/AbstractContainerScreenMixin.java");
    private static final Path TOOLTIP_PAINTER =
            Path.of("src/main/java/zcylas/totality/client/tooltip/renderer/TooltipPainter.java");
    private static final Path MIXINS_JSON =
            Path.of("src/main/resources/totality.mixins.json");
    private static final Path MINING_TOOL_CONTRIBUTOR =
            Path.of("src/main/java/zcylas/totality/client/tooltip/contributor/MiningToolContributor.java");
    private static final Path BLOCK_DURABILITY_CONTRIBUTOR =
            Path.of("src/main/java/zcylas/totality/client/tooltip/contributor/BlockDurabilityContributor.java");
    private static final Path TOOLTIP_PROFILE_COMPONENT =
            Path.of("src/main/java/zcylas/totality/api/core/rpgutils/rarity/TooltipProfileComponent.java");
    private static final Path VANILLA_ITEM_PRESENTATION =
            Path.of("src/main/java/zcylas/totality/init/VanillaItemPresentation.java");
    private static final Path TOTALITY_MAIN =
            Path.of("src/main/java/zcylas/totality/Totality.java");
    private static final Path TARGET_EFFECTIVENESS =
            Path.of("src/main/java/zcylas/totality/api/mining/TargetEffectiveness.java");
    private static final Path MINING_DAMAGE_CALCULATOR =
            Path.of("src/main/java/zcylas/totality/api/mining/MiningDamageCalculator.java");
    private static final Path MINING_SPEED_CALCULATOR =
            Path.of("src/main/java/zcylas/totality/api/mining/MiningSpeedCalculator.java");
    private static final Path PLAYER_MINING_POWER =
            Path.of("src/main/java/zcylas/totality/api/mining/PlayerMiningPower.java");
    private static final Path MINING_SOURCE_PROFILE =
            Path.of("src/main/java/zcylas/totality/api/mining/MiningSourceProfile.java");
    private static final Path RESOLVED_MINING_SOURCE =
            Path.of("src/main/java/zcylas/totality/api/mining/ResolvedMiningSource.java");
    private static final Path MINING_TIER =
            Path.of("src/main/java/zcylas/totality/api/mining/MiningTier.java");
    private static final Path MINING_TUNING =
            Path.of("src/main/java/zcylas/totality/api/mining/MiningTuning.java");
    private static final Path BLOCK_DURABILITY_DEFINITIONS =
            Path.of("src/main/java/zcylas/totality/api/mining/BlockDurabilityDefinitions.java");
    private static final Path COMBAT_TEXT_RENDERER =
            Path.of("src/main/java/zcylas/totality/client/combat/CombatTextRenderer.java");
    private static final Path POWER_MINING_METER_HUD =
            Path.of("src/main/java/zcylas/totality/client/mining/PowerMiningMeterHud.java");
    private static final Path GUI_GRAPHICS_EXTRACTOR_MIXIN =
            Path.of("src/main/java/zcylas/totality/mixin/client/GuiGraphicsExtractorMixin.java");
    private static final Path TOTALITY_GUI_GRAPHICS =
            Path.of("src/main/java/zcylas/totality/client/renderer/gui/TotalityGuiGraphics.java");

    private static String read(Path path) throws Exception {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    // ── Battery parity — the direct regression test for the Copper/Netherite mismatch ──────

    @Test
    void allFiveBatteryTiersExplicitlyOptIntoTooltipPresentation() throws Exception {
        String source = read(ENERGY_ITEMS);
        List<String> tiers = List.of(
                "COPPER_BATTERY", "IRON_BATTERY", "GOLD_BATTERY", "DIAMOND_BATTERY", "NETHERITE_BATTERY");

        for (String tier : tiers) {
            int declStart = source.indexOf("public static final BatteryItem " + tier);
            assertTrue(declStart >= 0, "expected to find the " + tier + " registration");
            int nextDecl = source.indexOf("public static final", declStart + 1);
            String block = nextDecl >= 0 ? source.substring(declStart, nextDecl) : source.substring(declStart);
            assertTrue(block.contains("getTooltipProfile()"),
                    tier + " must explicitly opt into Totality tooltip presentation — this is exactly "
                            + "the fix for the Copper-vs-Netherite mismatch the audit found");
            assertTrue(block.contains("ClassificationsComponent.of(ItemType.BATTERY, ItemType.ENERGY)"),
                    tier + " must carry the ordered [BATTERY, ENERGY] classification");
        }
    }

    @Test
    void batteryTiersAuthorRarityButNoInventedWeightOrLore() throws Exception {
        // Tooltip V2 Pass 1 (+ final corrections): every battery tier authors the canonical Industrial construction
        // progression. The earlier "do not invent missing data" decision still holds for weight and lore: none is
        // fabricated for Iron/Gold/Diamond/Netherite. (Real registered values are asserted in the client game test.)
        String source = read(ENERGY_ITEMS);
        String[][] tiers = {{"COPPER_BATTERY", "IRON_BATTERY", "CRUDE"}, {"IRON_BATTERY", "GOLD_BATTERY", "CALIBRATED"},
                {"GOLD_BATTERY", "DIAMOND_BATTERY", "PROTOTYPE"}, {"DIAMOND_BATTERY", "NETHERITE_BATTERY", "OVERCHARGED"},
                {"NETHERITE_BATTERY", "UMBRA_VISOR", "MASTERWORK"}};
        for (String[] t : tiers) {
            String block = source.substring(source.indexOf(t[0]), source.indexOf(t[1]));
            assertTrue(block.contains("RarityComponent(ItemRarity." + t[2] + ")"), t[0] + " must author " + t[2]);
        }

        int netheriteStart = source.indexOf("NETHERITE_BATTERY");
        int umbraStart = source.indexOf("UMBRA_VISOR");
        String netheriteBlock = source.substring(netheriteStart, umbraStart);
        assertFalse(netheriteBlock.contains("WeightComponent"), "Netherite Battery must not have an invented weight");
        assertFalse(netheriteBlock.contains("LoreComponent"), "Netherite Battery must not have invented lore");
    }

    // ── Renderer architecture constraints ────────────────────────────────────────

    @Test
    void rendererContainsNoDirectUEItemOrTotalityWeaponItemCapabilityCheck() throws Exception {
        String source = read(RENDERER);
        assertFalse(source.contains("instanceof UEItem"),
                "the UEItem capability check must live inside EnergyContributor, not the renderer");
        assertFalse(source.contains("instanceof TotalityWeaponItem"),
                "the TotalityWeaponItem capability check must live inside WeaponContributor, not the renderer");
    }

    @Test
    void rendererRunsContributorsThroughTheOrderedRegistryRatherThanHardcodingCalls() throws Exception {
        String source = read(RENDERER);
        assertTrue(source.contains("TooltipContributorRegistry.ordered()"),
                "the renderer must discover contributors through the registry, not a hardcoded call sequence");
    }

    // ── Legacy migration: superseded classes no longer duplicate contributor logic ─────────

    @Test
    void grimoireItemNoLongerImplementsTooltipExtensionDirectly() throws Exception {
        String source = read(GRIMOIRE_ITEM);
        assertFalse(source.contains("implements TooltipExtension"),
                "GrimoireItem's tier/spell tooltip content is now supplied by GrimoireContributor");
        assertFalse(source.contains("addTooltipLines"),
                "the old addTooltipLines override should be fully removed, not left dead");
    }

    @Test
    void ringOfProtectionNoLongerHandAuthorsItsAttunementOrBonusText() throws Exception {
        String source = read(RING_OF_PROTECTION);
        assertFalse(source.contains("addTooltipLines"),
                "Ring of Protection's tooltip lines now come from AttunementContributor, "
                        + "derived from getAcBonus()/getSaveBonus() rather than a literal string");
        assertTrue(source.contains("getAcBonus()") && source.contains("getSaveBonus()"),
                "the underlying bonus getters must still exist for AttunementContributor to read");
    }

    @Test
    void batteryItemNoLongerOverridesAppendHoverText() throws Exception {
        String source = read(BATTERY_ITEM);
        assertFalse(source.contains("appendHoverText"),
                "the legacy battery tooltip is retired now that all five tiers explicitly opt in");
        assertFalse(source.contains("isShiftDown"),
                "the duplicated raw GLFW shift poll must be gone from BatteryItem");
    }

    @Test
    void netheriteShurikenExplicitlyOptsInWhileOtherShurikensRelyOnTheDocumentedCompatibilityFallback() throws Exception {
        // Documents the deliberate scope of this pass: Netherite Shuriken is the representative
        // weapon migration; the other four shurikens and both Skyrim swords keep working through
        // ItemComponents.hasTooltipPresentation's temporary rarity-based fallback, not through an
        // explicit profile — a fact this test pins down rather than leaving implicit.
        String source = read(BASIC_WEAPON_ITEMS);
        int netheriteStart = source.indexOf("NETHERITE_SHURIKEN");
        int swordStart = source.indexOf("Skyrim Swords");
        String netheriteBlock = source.substring(netheriteStart, swordStart);
        assertTrue(netheriteBlock.contains("getTooltipProfile()"));

        int copperStart = source.indexOf("COPPER_SHURIKEN");
        int ironStart = source.indexOf("IRON_SHURIKEN");
        String copperBlock = source.substring(copperStart, ironStart);
        assertFalse(copperBlock.contains("getTooltipProfile()"),
                "Copper Shuriken is intentionally left on the temporary compatibility fallback in this pass");
    }

    // ── Explicit opt-in gate ──────────────────────────────────────────────────────

    @Test
    void mixinGatesOnTotalityTooltipRendererIsEligibleNotRarityDirectly() throws Exception {
        String source = read(MIXIN);
        assertTrue(source.contains("TotalityTooltipRenderer.isEligible(stack)"),
                "the mixin must delegate the render-vs-vanilla decision to the explicit opt-in check");
        assertFalse(source.contains("ItemComponents.getRarity()"),
                "the mixin itself must no longer branch on rarity directly");
    }

    @Test
    void itemComponentsDocumentsThatHasTooltipPresentationIsNoLongerTheEligibilityGate() throws Exception {
        // Superseded by the tooltip-eligibility review-fix pass: the renderer's isEligible check is
        // now content-driven (any registered contributor with real content), not rarity/lore-gated —
        // this replaces the old "temporary rarity fallback" test, which documented the mechanism
        // this pass deliberately retired as the eligibility gate.
        String source = read(ITEM_COMPONENTS);
        assertTrue(source.contains("hasTooltipPresentation"));
        assertTrue(source.contains("TooltipProfileComponent"));
        assertTrue(source.contains("No longer the Totality tooltip renderer's eligibility"),
                "hasTooltipPresentation's Javadoc must document that it is no longer consulted by isEligible");
    }

    @Test
    void mixinFallsBackToVanillaForStacksCarryingAStructuredTooltipComponent() throws Exception {
        String source = read(MIXIN);
        assertTrue(source.contains("data.isEmpty()"),
                "a non-empty structured TooltipComponent must fall back to vanilla rendering rather than being discarded");
    }

    // ── Correction pass: disclosure-capability declarations (independent of contribute()'s own gate) ──

    @Test
    void energyHealingAndTechnicalContributorsDeclareAvailableDisclosureLevelsExplicitly() throws Exception {
        for (Path path : List.of(ENERGY_CONTRIBUTOR, HEALING_POTION_CONTRIBUTOR, TECHNICAL_INFO_CONTRIBUTOR)) {
            String source = read(path);
            assertTrue(source.contains("availableDisclosureLevels"),
                    path + " must declare its Details/Technical capability explicitly, independent of "
                            + "whatever contribute() happens to emit at the currently-selected level");
        }
    }

    @Test
    void technicalInfoContributorDeclaresTechnicalAvailableUnconditionally() throws Exception {
        String source = read(TECHNICAL_INFO_CONTRIBUTOR);
        int declStart = source.indexOf("public Set<TooltipDisclosureLevel> availableDisclosureLevels");
        assertTrue(declStart >= 0, "expected to find the availableDisclosureLevels override");
        String body = source.substring(declStart, Math.min(source.length(), declStart + 200));
        assertFalse(body.contains("ctx.disclosure()"),
                "must not require Ctrl to already be held merely for the document to know Technical exists");
    }

    // ── Correction pass: identification visibility applied to the actual migrated contributors ──

    @Test
    void metadataContributorGatesRarityAndLoreBehindIdentification() throws Exception {
        String source = read(METADATA_CONTRIBUTOR);
        long gateCount = source.lines().filter(l -> l.contains("WHEN_IDENTIFIED")).count();
        assertTrue(gateCount >= 2, "expected both the RarityBadge and the Description(lore) to be gated WHEN_IDENTIFIED");
        assertTrue(source.contains("new TooltipSection.ClassificationBadges"),
                "classification badges must still be constructed (and, per the class Javadoc, left ungated — an obvious physical fact)");
    }

    @Test
    void attunementContributorGatesAllAttunementContentBehindIdentification() throws Exception {
        String source = read(ATTUNEMENT_CONTRIBUTOR);
        long gateCount = source.lines().filter(l -> l.contains("WHEN_IDENTIFIED")).count();
        assertTrue(gateCount >= 3,
                "expected the AC/save-bonus line, the Requires-Attunement line, and the Attuned/Not-Attuned/Free "
                        + "status line to all be gated WHEN_IDENTIFIED — found only " + gateCount);
    }

    @Test
    void grimoireContributorGatesSpellStateButNotTierBehindIdentification() throws Exception {
        String source = read(GRIMOIRE_CONTRIBUTOR);
        int tierStart = source.indexOf("\"Tier\"");
        int activeStart = source.indexOf("\"Active\"");
        assertTrue(tierStart >= 0 && activeStart >= 0, "expected to find both the Tier and Active rows");

        String tierLineBlock = source.substring(Math.max(0, tierStart - 200), tierStart + 50);
        assertFalse(tierLineBlock.contains("WHEN_IDENTIFIED"),
                "Tier is an obvious physical fact (a thicker, more ornate tome) and must stay ungated");

        String spellBlock = source.substring(activeStart, Math.min(source.length(), activeStart + 400));
        assertTrue(spellBlock.contains("WHEN_IDENTIFIED"),
                "selected-spell state is magical internal state and must be identified-only");
    }

    @Test
    void weaponContributorDoesNotGateAnyBasicFactBehindIdentification() throws Exception {
        String source = read(WEAPON_CONTRIBUTOR);
        assertFalse(source.contains("WHEN_IDENTIFIED"),
                "damage dice, damage type, physical properties, category, range, and stamina cost are all "
                        + "obvious/basic facts and must remain visible on an unidentified weapon");
    }

    @Test
    void externalContentContributorGatesPreservedLinesBehindIdentification() throws Exception {
        String source = read(EXTERNAL_CONTENT_CONTRIBUTOR);
        assertTrue(source.contains("WHEN_IDENTIFIED"),
                "preserved vanilla/third-party lines must not bypass identification just because they "
                        + "arrived through the generic pass-through instead of a semantic section");
    }

    // ── Correction pass: dedupe investigation for known migrated legacy lines ──────────────

    @Test
    void totalityItemAddTooltipLinesDefaultIsIntentionallyKeptBecauseTradingScreenStillUsesIt() throws Exception {
        // Correction-pass finding: this default is NOT dead code. It is bypassed within the hover
        // tooltip pipeline (LegacyExtensionAdapterContributor explicitly skips `instanceof
        // TotalityItem`, confirmed elsewhere in this file), but TradingScreen's shop-preview panel
        // calls it directly and independently for an unrelated feature outside the Tooltip API's
        // scope. Removing or emptying it would silently regress that panel, so it is left as-is —
        // this sentinel pins down *why* so a future pass does not "clean it up" by mistake.
        String totalityItemSource = read(TOTALITY_ITEM);
        assertTrue(totalityItemSource.contains("default void addTooltipLines"),
                "the default attunement-line implementation must remain — it is still a live dependency");

        String tradingScreenSource = read(TRADING_SCREEN);
        assertTrue(tradingScreenSource.contains("instanceof TooltipExtension"),
                "TradingScreen's shop-preview panel must still be the confirmed live caller justifying this");
        assertTrue(tradingScreenSource.contains("ext.addTooltipLines"));
    }

    @Test
    void shurikenFlavorLineIsNotDuplicatedByAnyContributor() throws Exception {
        String shurikenSource = read(SHURIKEN_ITEM);
        assertTrue(shurikenSource.contains("item.totality.shuriken.tooltip"),
                "the generic Shuriken flavor line should still exist, flowing through as preserved external content");
        String weaponContributorSource = read(WEAPON_CONTRIBUTOR);
        assertFalse(weaponContributorSource.contains("shuriken.tooltip"),
                "WeaponContributor must not duplicate the flavor line — it only contributes combat stats");
    }

    // ── Correction pass: prefer TooltipContext over independently fetching Minecraft.getInstance() ──

    @Test
    void attunementContributorUsesContextPlayerRatherThanFetchingMinecraftInstanceDirectly() throws Exception {
        String source = read(ATTUNEMENT_CONTRIBUTOR);
        assertTrue(source.contains("ctx.player()"), "expected AttunementContributor to read the player from TooltipContext");
        assertFalse(source.contains("Minecraft.getInstance()"),
                "must not independently fetch Minecraft.getInstance().player when TooltipContext already carries it");
    }

    @Test
    void fuelContributorUsesContextLevelRatherThanFetchingMinecraftInstanceDirectly() throws Exception {
        String source = read(FUEL_CONTRIBUTOR);
        assertTrue(source.contains("ctx.level()"), "expected FuelContributor to read the level from TooltipContext");
        assertFalse(source.contains("Minecraft.getInstance()"),
                "must not independently fetch Minecraft.getInstance().level when TooltipContext already carries it");
    }

    // ── Correction pass: deterministic (Locale.ROOT) decimal formatting ─────────────────────

    @Test
    void everyStringFormatCallInHealingPotionAndWeightContributorsUsesLocaleRoot() throws Exception {
        for (Path path : List.of(HEALING_POTION_CONTRIBUTOR, WEIGHT_FOOTER)) {
            String source = read(path);
            List<String> badCalls = source.lines()
                    .filter(l -> l.contains("String.format(") && !l.contains("Locale.ROOT"))
                    .toList();
            assertTrue(badCalls.isEmpty(),
                    path + " has String.format(...) call(s) not using Locale.ROOT: " + badCalls);
        }
    }

    // ── Correction pass: documentation accuracy ──────────────────────────────────────────────

    @Test
    void tooltipProfileComponentDocumentsThatItIsNoLongerTheEligibilityGate() throws Exception {
        // Updated for the tooltip-eligibility review-fix pass — see the matching update to
        // itemComponentsDocumentsThatHasTooltipPresentationIsNoLongerTheEligibilityGate above.
        Path path = Path.of("src/main/java/zcylas/totality/api/core/rpgutils/rarity/TooltipProfileComponent.java");
        String source = read(path);
        assertTrue(source.contains("Superseded as the eligibility gate"),
                "TooltipProfileComponent's own Javadoc must acknowledge it is no longer the renderer's "
                        + "eligibility gate — a reader of this file alone should not be misled");
    }

    // ── Final correction pass, Finding 1: TooltipKnowledgeView.of() default-identified compat ──

    @Test
    void tooltipKnowledgeViewOfReadsTheRawComponentInsteadOfGetIdentificationStatus() throws Exception {
        String source = read(TOOLTIP_KNOWLEDGE_VIEW);
        int ofStart = source.indexOf("public static TooltipKnowledgeView of(ItemStack stack)");
        assertTrue(ofStart >= 0, "expected to find the of(ItemStack) factory method");
        String ofBody = source.substring(ofStart, Math.min(source.length(), ofStart + 400));

        assertTrue(ofBody.contains("stack.get(TotalityItemComponents.IDENTIFICATION_STATUS)"),
                "of() must read the raw component directly rather than going through the "
                        + "gameplay-facing getIdentificationStatus() default");
        assertFalse(ofBody.contains("getIdentificationStatus(stack)"),
                "of() must NOT call TotalityItem.getIdentificationStatus(stack) — that method defaults a "
                        + "missing component to UNIDENTIFIED, which is exactly the bug this finding fixes");
    }

    @Test
    void tooltipKnowledgeViewOfDefaultsAMissingComponentToIdentifiedNotUnidentified() throws Exception {
        String source = read(TOOLTIP_KNOWLEDGE_VIEW);
        int ofStart = source.indexOf("public static TooltipKnowledgeView of(ItemStack stack)");
        String ofBody = source.substring(ofStart, Math.min(source.length(), ofStart + 400));

        assertTrue(ofBody.contains("explicit != null ? new TooltipKnowledgeView(explicit) : IDENTIFIED"),
                "an absent IDENTIFICATION_STATUS component must resolve to IDENTIFIED for tooltip purposes — "
                        + "this is the exact fix for existing Netherite Shuriken/Ring of Protection stacks "
                        + "being incorrectly treated as unidentified");
    }

    @Test
    void tooltipKnowledgeViewOfNeverWritesTheIdentificationComponent() throws Exception {
        String source = read(TOOLTIP_KNOWLEDGE_VIEW);
        assertFalse(source.contains(".set(TotalityItemComponents.IDENTIFICATION_STATUS"),
                "reading a tooltip must never create persistent identification state on the stack");
    }

    @Test
    void tooltipKnowledgeViewDocumentsTheFutureIdentificationApiResponsibility() throws Exception {
        String source = read(TOOLTIP_KNOWLEDGE_VIEW);
        String lower = source.toLowerCase(java.util.Locale.ROOT);
        assertTrue(lower.contains("future") && lower.contains("identification"),
                "the class Javadoc must document that a future Identification/Knowledge API becomes "
                        + "responsible for explicitly authoring UNIDENTIFIED/PARTIALLY states");
    }

    @Test
    void totalityItemGetIdentificationStatusGameplayDefaultIsUnchangedByThisCorrectionPass() throws Exception {
        // Finding 1 explicitly scopes the fix to the tooltip-compatibility read in
        // TooltipKnowledgeView.of() — TotalityItem's own gameplay-facing default (used by
        // isIdentified()/setIdentificationStatus() and any future gameplay check) must stay
        // UNIDENTIFIED, unchanged, since this pass does not touch TotalityItem at all.
        String source = read(TOTALITY_ITEM);
        int declStart = source.indexOf("default IdentificationStatus getIdentificationStatus(ItemStack stack)");
        assertTrue(declStart >= 0, "expected to find TotalityItem's own getIdentificationStatus default");
        String body = source.substring(declStart, Math.min(source.length(), declStart + 300));
        assertTrue(body.contains("IdentificationStatus.UNIDENTIFIED"),
                "TotalityItem's own gameplay-facing default must remain UNIDENTIFIED — only the tooltip "
                        + "read in TooltipKnowledgeView.of() is deliberately narrower");
    }

    @Test
    void noProductionCodeExplicitlyAuthorsIdentificationStatusYet() throws Exception {
        // Pins down the fact this whole finding relies on: nothing in the repository grants
        // IDENTIFIED/UNIDENTIFIED/PARTIALLY explicitly today, so of()'s "absent component ->
        // identified" default is what every current Shuriken/Ring/etc. stack actually sees.
        Path itemsRoot = Path.of("src/main/java/zcylas/totality");
        try (var stream = Files.walk(itemsRoot)) {
            List<Path> offenders = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains("setIdentificationStatus(");
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .filter(p -> !p.equals(TOTALITY_ITEM))
                    .toList();
            assertTrue(offenders.isEmpty(),
                    "expected no production caller of setIdentificationStatus(...) yet (only the default "
                            + "method declaration itself) — found: " + offenders);
        }
    }

    // ── Final correction pass, Finding 3: complete label and footer wrapping ────────────────

    @Test
    void statRowLayoutWrapsBothTheLabelAndTheValue() throws Exception {
        String source = read(RENDERER);
        int layoutMethodStart = source.indexOf("private static LaidOutSection layout(TooltipSection section, Font font, int width)");
        assertTrue(layoutMethodStart >= 0, "expected to find the layout(TooltipSection, Font, int) method");
        int caseStart = source.indexOf("case TooltipSection.StatRow r ->", layoutMethodStart);
        int nextCase = source.indexOf("case TooltipSection.StatBlock", caseStart);
        String caseBody = source.substring(caseStart, nextCase);

        assertTrue(caseBody.contains("font.split(Component.literal(r.label())"),
                "StatRow layout must wrap the label, not just the value — a long label must not "
                        + "remain an unbounded raw string");
        assertTrue(caseBody.contains("font.split(Component.literal(r.value())"),
                "StatRow layout must still wrap the value");
    }

    @Test
    void statBlockLayoutWrapsBothTheLabelAndTheValueOfEachLine() throws Exception {
        String source = read(RENDERER);
        int layoutMethodStart = source.indexOf("private static LaidOutSection layout(TooltipSection section, Font font, int width)");
        int caseStart = source.indexOf("case TooltipSection.StatBlock b ->", layoutMethodStart);
        int nextCase = source.indexOf("case TooltipSection.ProgressBar", caseStart);
        String caseBody = source.substring(caseStart, nextCase);

        assertTrue(caseBody.contains("font.split(Component.literal(line.label())"),
                "StatBlock layout must wrap each line's label, not just its value");
        assertTrue(caseBody.contains("font.split(Component.literal(line.value())"),
                "StatBlock layout must still wrap each line's value");
    }

    @Test
    void modifierHintsAreDetachedPanelsNotFooterText() throws Exception {
        // Tooltip V2 bottom-presentation slice: the SHIFT/CTRL hints moved out of the footer into detached panels.
        String source = read(RENDERER);
        assertFalse(source.contains("\"SHIFT: Details\"") || source.contains("\"CTRL: Technical\""),
                "no SHIFT/CTRL instruction text inside the main tooltip any more");
        assertTrue(source.contains("TooltipModifierPanels.draw("), "the detached modifier panels are drawn");
    }

    @Test
    void drawFooterPlacesEveryFieldFromThePanelsInnerPaddingViaTheTestedLayout() throws Exception {
        // Tooltip V2 final corrections: the right-aligned hint line this used to guard is gone. Every remaining
        // footer field is offset from the inner padding by TooltipFooter.layout's placement, which clamps each
        // x to >= 0 (see TooltipBottomPresentationTest), so nothing can sit left of the panel's padding.
        String source = read(RENDERER);
        int footerStart = source.indexOf("private static void drawFooter");
        String footerBody = source.substring(footerStart, source.indexOf("\n    }\n", footerStart));
        assertTrue(footerBody.contains("int innerX = panelX + PADDING;"));
        assertTrue(footerBody.contains("int x = innerX + placement.x()"));
        assertFalse(footerBody.contains("Scroll"), "no scrolling hint row in the footer");
    }

    // ── Micro-correction: AttunementContributor's missing sectionGroup() override ───────────

    @Test
    void attunementContributorActuallyOverridesSectionGroupToReturnAttunement() throws Exception {
        // A prior pass added the TooltipSectionGroup import and reported that AttunementContributor
        // declared ATTUNEMENT, but never actually wrote the @Override — it silently inherited
        // TooltipContributor's default PRIMARY. This sentinel proves the override now exists in
        // production source text (complementing the direct pure-test proof in
        // TooltipContributorRegistryTest#attunementContributorSectionGroupIsAttunement).
        String source = read(ATTUNEMENT_CONTRIBUTOR);
        int declStart = source.indexOf("public TooltipSectionGroup sectionGroup()");
        assertTrue(declStart >= 0, "expected AttunementContributor to declare a sectionGroup() override");

        String beforeDecl = source.substring(Math.max(0, declStart - 40), declStart);
        assertTrue(beforeDecl.contains("@Override"),
                "sectionGroup() must be annotated @Override, confirming it is not merely a "
                        + "same-named method that fails to actually override the interface default");

        String body = source.substring(declStart, Math.min(source.length(), declStart + 120));
        assertTrue(body.contains("return TooltipSectionGroup.ATTUNEMENT;"),
                "AttunementContributor's sectionGroup() must return ATTUNEMENT, not the inherited PRIMARY default");
    }

    // ── Visual correction pass, Finding 1: shrink-to-content width, named constants ─────────

    @Test
    void rendererDeclaresTheNamedCompactWidthConstants() throws Exception {
        String source = read(RENDERER);
        assertTrue(source.contains("private static final int MIN_WIDTH"));
        assertTrue(source.contains("private static final int PREFERRED_MAX_WIDTH"));
        assertTrue(source.contains("private static final float MAX_SCREEN_WIDTH_FRACTION"));
    }

    @Test
    void naturalRowWidthExcludesLongFormSectionsFromDrivingPanelWidth() throws Exception {
        // Description (lore), ExternalContent, TechnicalInfo, and ProgressBar must never force
        // the panel wider — they wrap vertically at whatever width the rest of the content (and
        // the compact ceiling) already decided. Confirmed by the shared `default -> 0` arm of the
        // per-section-kind switch, rather than one of these types having its own explicit case.
        String source = read(RENDERER);
        int methodStart = source.indexOf("private static int naturalRowWidth(TooltipSection section, Font font)");
        assertTrue(methodStart >= 0, "expected the naturalRowWidth measurement method to exist");
        // Tooltip V2 presentation slice: the old end marker (naturalBadgeRowWidth) went away with the
        // badge header; the method's own closing brace bounds the body just as tightly.
        String body = source.substring(methodStart, source.indexOf("\n    }\n", methodStart));

        assertFalse(body.contains("case TooltipSection.Description"),
                "Description (lore) must not have its own naturalRowWidth case — it must fall through to the default 0");
        assertFalse(body.contains("case TooltipSection.ExternalContent"),
                "ExternalContent must not have its own naturalRowWidth case — it must fall through to the default 0");
        assertFalse(body.contains("case TooltipSection.TechnicalInfo"),
                "TechnicalInfo (registry id / component count / long identifiers) must not have its own "
                        + "naturalRowWidth case — it must fall through to the default 0, so a long technical line "
                        + "never forces the panel wider");
        assertTrue(body.contains("default -> 0"),
                "expected an explicit default -> 0 arm excluding every unlisted (long-form) section kind");
    }

    // ── Visual correction pass, Finding 2: flowing badge row — superseded ───────────────────
    // Tooltip V2 presentation slice replaced the rarity/classification badge row with centred text
    // lines under the large preview. The two badge-row source checks that lived here
    // (badgeSpecsPlaceRarityBeforeClassificationsPreservingAuthoredOrder,
    // rarityNeverGetsAMandatorySeparateBadgeRow) are superseded by behavioural tests of the same
    // intent — rarity first, then classifications in authored order, never a fabricated second
    // value — in presentation/TooltipIdentityLinesTest.

    // ── Visual correction pass, Finding 4/8: scrollbar gutter — superseded ──────────────────
    // Tooltip V2 final corrections: the visible scrollbar (track + thumb) and the "Scroll: More" footer row
    // were removed; scrolling stays wheel-only, so no gutter is reserved and the body spans the inner width.

    @Test
    void noVisibleScrollIndicatorOrScrollHintIsDrawn() throws Exception {
        String source = read(RENDERER);
        assertFalse(source.contains("drawScrollIndicator"), "no scrollbar track/thumb");
        assertFalse(source.contains("SCROLLBAR_GUTTER"), "no width reserved for a scrollbar that is no longer drawn");
        assertFalse(source.contains("Scroll: More"), "no scrolling hint text");
        assertTrue(source.contains("int bodyContentW = Math.max(1, innerW);"), "the body spans the full inner width");
    }

    @Test
    void wheelScrollingAndBodyClippingArePreserved() throws Exception {
        String source = read(RENDERER);
        assertTrue(source.contains("TooltipScrollController.onRender(screen, slot, stack, disclosure, bodyContentH, bodyViewportH,"),
                "the scroll target (content height vs viewport) is still registered every frame");
        assertTrue(source.contains("int cursorY = bodyTop - scrollOffset;"), "the body is still drawn at the scroll offset");
        assertTrue(source.contains("new CloseableScissor(graphics, bodyLeft - 2, bodyTop, bodyContentW + 4, bodyViewportH)"),
                "the body is still clipped to its viewport");
    }

    // ── Visual correction pass, Finding 6: visual hierarchy cleanup ─────────────────────────

    @Test
    void decorativeFooterDotsWereRemoved() throws Exception {
        String rendererSource = read(RENDERER);
        assertFalse(rendererSource.contains("drawFooterDots"),
                "the purely decorative three-dot footer marker must no longer be drawn — it carried no information");

        String painterSource = read(TOOLTIP_PAINTER);
        assertFalse(painterSource.contains("drawFooterDots"),
                "the now-unused drawFooterDots method must be removed from TooltipPainter too, not left dead");
    }

    @Test
    void panelPaddingWasTightened() throws Exception {
        String source = read(RENDERER);
        assertTrue(source.contains("private static final int PADDING = 8"),
                "PADDING must be reduced from the old 10px for a less heavy panel");
        // The header-to-body separator band (10 -> 7 -> 9 -> 8px) was removed entirely in the Tooltip V2 Pass 1
        // final corrections — see universalHeaderDividerIsRemovedEntirely.
    }

    @Test
    void backgroundIsBlendedTowardNeutralRatherThanFullySaturated() throws Exception {
        String source = read(TOOLTIP_PAINTER);
        assertTrue(source.contains("blendTowardNeutral"),
                "expected the panel background to blend rarity colors toward a neutral ground, "
                        + "keeping rarity identity primarily in the border/title/badges rather than the whole panel");
    }

    @Test
    void secondaryLabelTextUsesTheSharedLighterColorConstant() throws Exception {
        String source = read(RENDERER);
        assertTrue(source.contains("private static final int SECONDARY_TEXT_COLOR"),
                "expected a single named secondary-text color constant, replacing the old inline 0xFF888888");
        long usages = source.lines().filter(l -> l.contains("SECONDARY_TEXT_COLOR") && l.contains(",")).count();
        assertTrue(usages >= 2, "expected the shared constant to actually be used by more than one draw call");
    }

    // ── Visual correction pass, Finding 7: scroll wiring root-cause fix ─────────────────────

    @Test
    void deadScreenMixinWasRemovedEntirely() throws Exception {
        assertFalse(java.nio.file.Files.exists(
                        Path.of("src/main/java/zcylas/totality/mixin/client/ScreenMixin.java")),
                "the old GuiEventListener-targeting ScreenMixin must be deleted — confirmed by decompiling vanilla "
                        + "that AbstractContainerScreen's own concrete mouseScrolled override made it unreachable");

        String mixinsJson = read(MIXINS_JSON);
        assertFalse(mixinsJson.contains("\"client.ScreenMixin\""),
                "the deleted ScreenMixin must no longer be registered in totality.mixins.json "
                        + "(note: this checks the exact quoted entry, not a bare substring, since "
                        + "\"client.AbstractContainerScreenMixin\" legitimately contains \"ScreenMixin\" as a substring)");
    }

    @Test
    void mouseHandlerMixinRoutesScrollToTheTooltipHandlerBeforeFluidTank() throws Exception {
        String source = read(MOUSE_HANDLER_MIXIN);
        int fluidIdx = source.indexOf("FluidTankScrollHandler.onScroll");
        int tooltipIdx = source.indexOf("TotalityTooltipScrollHandler.onMouseScroll");
        assertTrue(fluidIdx >= 0 && tooltipIdx >= 0,
                "expected MouseHandlerMixin to check both the pre-existing FluidTankScrollHandler and the new "
                        + "TotalityTooltipScrollHandler");
        assertTrue(fluidIdx < tooltipIdx, "FluidTankScrollHandler must be checked first, unchanged from before this pass");
    }

    @Test
    void scrollHandlerReadsHoveredSlotFromTheAccessorRatherThanRecomputingHitTest() throws Exception {
        String source = read(TOOLTIP_SCROLL_HANDLER);
        assertTrue(source.contains("totality$getHoveredSlot()"),
                "expected the handler to read the screen's already-computed hoveredSlot via the existing accessor");
        assertTrue(source.contains("TotalityTooltipRenderer.isEligible(stack)"),
                "expected the handler to only claim slots holding a Totality-eligible item, leaving every other "
                        + "slot's scroll behavior completely untouched");
    }

    @Test
    void abstractContainerScreenMixinNoLongerRegistersAnyItemSlotMouseAction() throws Exception {
        // An earlier design attempt registered a per-screen ItemSlotMouseAction; the final
        // approach hooks MouseHandler.onScroll instead (see TooltipScrollController's class
        // Javadoc), which needs no screen-side registration at all.
        String source = read(ABSTRACT_CONTAINER_SCREEN_MIXIN);
        assertFalse(source.contains("addItemSlotMouseAction"),
                "the final Finding 7 fix does not register an ItemSlotMouseAction from this mixin");
    }

    @Test
    void tooltipScrollControllerDocumentsTheConfirmedRootCause() throws Exception {
        String source = read(TOOLTIP_SCROLL_CONTROLLER);
        String lower = source.toLowerCase(java.util.Locale.ROOT);
        assertTrue(lower.contains("itemslotmouseactions"),
                "the class Javadoc must document the confirmed root cause: AbstractContainerScreen's own "
                        + "mouseScrolled override routes through itemSlotMouseActions and never calls super");
        assertTrue(source.contains("record ActiveTarget"),
                "expected the explicit active scroll target record (screen, slot, maxScroll, viewport bounds, "
                        + "frame marker) called for by Finding 7");
    }

    @Test
    void activeTargetRecordCarriesEveryFieldFinding7RequiresAtMinimum() throws Exception {
        String source = read(TOOLTIP_SCROLL_CONTROLLER);
        int declStart = source.indexOf("record ActiveTarget(");
        assertTrue(declStart >= 0);
        int declEnd = source.indexOf(") {}", declStart);
        assertTrue(declEnd >= 0, "expected the ActiveTarget record's parameter list to end with ') {}'");
        String decl = source.substring(declStart, declEnd);
        assertTrue(decl.contains("Screen"), "must carry the current screen identity");
        assertTrue(decl.contains("Slot slot"), "must carry the source hovered-slot identity as a Slot reference, "
                + "not a Slot.index int (micro-correction Finding 2 — index alone is not unique across a menu)");
        assertTrue(decl.contains("maxScroll"), "must carry the maximum scroll offset");
        assertTrue(decl.contains("viewportX") && decl.contains("viewportWidth"), "must carry the current viewport bounds");
        assertTrue(decl.contains("frameMarker"), "must carry a render-frame freshness marker");
    }

    // ── Visual-correction micro-correction, Finding 1: consume only when offset actually changes ──

    @Test
    void onMouseScrollOnlyConsumesWhenTheAppliedDeltaActuallyChangesTheOffset() throws Exception {
        String source = read(TOOLTIP_SCROLL_CONTROLLER);
        int methodStart = source.indexOf("public static boolean onMouseScroll(");
        assertTrue(methodStart >= 0);
        String body = source.substring(methodStart, Math.min(source.length(), methodStart + 900));

        assertTrue(body.contains("applyScrollDelta(scrollOffset, scrollY, SCROLL_STEP, activeTarget.maxScroll())"),
                "onMouseScroll must compute the candidate new offset via the extracted, directly-testable "
                        + "applyScrollDelta helper");
        assertTrue(body.contains("if (newOffset == scrollOffset) return false;"),
                "onMouseScroll must return false — leaving the event unconsumed for normal screen scrolling — "
                        + "whenever the clamped result would not actually change the offset (top/bottom "
                        + "boundaries, or a delta that rounds to zero)");
    }

    // ── Visual-correction micro-correction, Finding 2: genuinely unique slot identity ────────

    @Test
    void noProductionScrollFileUsesSlotIndexForIdentity() throws Exception {
        // Slot.index is only unique within that slot's own backing Container — a player-inventory
        // slot and an external-container slot can legitimately share the same value. Every file in
        // the scroll-wiring path must identify "which slot" by the Slot object's own reference,
        // never by reading its .index field.
        for (Path path : List.of(TOOLTIP_SCROLL_CONTROLLER, TOOLTIP_SCROLL_HANDLER, ABSTRACT_CONTAINER_SCREEN_MIXIN)) {
            String source = read(path);
            assertFalse(source.contains(".index"),
                    path + " must not read Slot.index anywhere — slot identity must be the Slot reference itself");
        }
    }

    @Test
    void scrollControllerAndHandlerUseSlotTypedIdentityThroughout() throws Exception {
        String controllerSource = read(TOOLTIP_SCROLL_CONTROLLER);
        assertTrue(controllerSource.contains("@Nullable Slot slot"),
                "onRender/onMouseScroll/targetMatches must all take a Slot reference, not an int slot index");
        assertFalse(controllerSource.contains("int slotIndex"),
                "no int-based slot-index parameter should remain in production code");

        String handlerSource = read(TOOLTIP_SCROLL_HANDLER);
        assertTrue(handlerSource.contains("TooltipScrollController.onMouseScroll(screen, hoveredSlot, stack, scrollDelta)"),
                "the handler must forward the hoveredSlot object itself, not hoveredSlot.index");
    }

    // ── Visual-correction micro-correction, Finding 3: language-independent component-count line ──

    @Test
    void componentCountRecognitionUsesTheTranslationKeyNotAnEnglishRegex() throws Exception {
        String source = read(EXTERNAL_CONTENT_CONTRIBUTOR);
        assertFalse(source.contains("component\\\\(s\\\\)"),
                "the old English-only regex (\\\\d+ component\\\\(s\\\\)) must be gone");
        assertFalse(source.contains("Pattern.compile"),
                "no regex should remain in this contributor once recognition is key-based");
        assertTrue(source.contains("TranslatableContents"),
                "expected recognition via Component's own TranslatableContents, not its rendered text");
        assertTrue(source.contains("\"item.components\""),
                "expected the exact vanilla translation key literal (confirmed by decompiling "
                        + "ItemStack.addDetailsToTooltip in minecraft-merged.jar for MC 26.2)");
        assertTrue(source.contains("tc.getKey()"),
                "recognition must compare the TranslatableContents' own key, not line.getString()");
    }

    @Test
    void registryIdRecognitionRemainsAnExactStringMatch() throws Exception {
        // The registry id line is never translated by vanilla (Component.literal, confirmed by the
        // same decompilation), so an exact string match is already language-independent — this
        // ownership rule must survive the Finding 3 correction unchanged.
        String source = read(EXTERNAL_CONTENT_CONTRIBUTOR);
        assertTrue(source.contains("line.getString().equals(registryId)"),
                "the registry-id line must still be recognized by an exact string match");
        assertTrue(source.contains("BuiltInRegistries.ITEM.getKey(ctx.stack().getItem())"),
                "the registry id itself must still come from BuiltInRegistries, never guessed");
    }

    // ── Visual-correction micro-correction, Finding 4: no stale deleted-class references ─────

    @Test
    void noProductionFileReferencesTheDeletedTotalityItemSlotScrollAction() throws Exception {
        Path srcRoot = Path.of("src/main/java/zcylas/totality");
        try (var stream = Files.walk(srcRoot)) {
            List<Path> offenders = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains("TotalityItemSlotScrollAction");
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .toList();
            assertTrue(offenders.isEmpty(),
                    "the intermediate TotalityItemSlotScrollAction design was deleted — no production file, "
                            + "including stale Javadoc links, may still reference it: " + offenders);
        }
    }

    // ── Presentation-cleanup pass, Finding 1: icon-to-label spacing ──────────────────────────

    @Test
    void iconLabelGapIsANamedCompactButVisibleConstant() throws Exception {
        String source = read(RENDERER);
        assertTrue(source.contains("private static final int ICON_LABEL_GAP = 5;"),
                "expected a named, compact-but-visible icon-to-label gap constant (previously an unnamed + 3)");
    }

    @Test
    void everyIconThenLabelSiteUsesTheSharedGapConstantNotARawLiteral() throws Exception {
        // Every measurement/layout/draw site that places an icon immediately before a label
        // (Damage, Range, Stamina Cost, and any similar StatRow/StatBlock line) must use the same
        // named constant — confirmed here rather than trusting each of the eight call sites
        // individually stayed in sync by hand.
        String source = read(RENDERER);
        long iconThenGapConstantUses = source.lines()
                .filter(l -> l.contains("iconGlyph()") && l.contains("ICON_LABEL_GAP"))
                .count();
        assertEquals(8, iconThenGapConstantUses,
                "expected all 8 icon-then-label call sites (naturalRowWidth x2, layout() x2, "
                        + "drawStatRow x2, drawStatBlock x2) to use ICON_LABEL_GAP");
        assertFalse(source.contains("iconGlyph()) + 3"),
                "no icon-to-label site should still use the old unnamed + 3 literal");
    }

    // ── Presentation-cleanup pass, Finding 2: light vertical breathing room ──────────────────

    @Test
    void universalHeaderDividerIsRemovedEntirely() throws Exception {
        // Tooltip V2 Pass 1 final corrections (supersedes the separatorH 10 -> 7 -> 9 -> 8 history): no universal
        // header/body divider — not merely hidden for bodiless items. Semantic group headings draw their own divider
        // lines, so the first group heading is the only divider under the header, and no band is reserved for it.
        String source = read(RENDERER);
        assertFalse(source.contains("TooltipDividerPainter.draw("), "the universal divider must not be drawn");
        assertFalse(source.contains("separatorH") || source.contains("separatorY"), "no divider band may be reserved");
        assertTrue(source.contains("int chromeH = headerH + footerH;"), "chrome is header + footer only");
        assertTrue(source.contains("int bodyTop = panelY + headerH;"), "the body starts directly after the header");
        assertTrue(source.contains("private static final int UNHEADED_BODY_LEAD = TooltipGroupHeadingPainter.GAP_ABOVE;"),
                "a body starting with unheaded content (lore only) gets the same lead a group heading reserves — spacing, not a divider");
    }

    @Test
    void bodyToFooterGapIsANamedConstantAppliedToBothFooterHeightAndItsDrawPosition() throws Exception {
        // Tooltip-layout-cleanup pass (2026-09-22): tightened from 4 to 3.
        String source = read(RENDERER);
        assertTrue(source.contains("private static final int BODY_FOOTER_GAP = 3;"),
                "expected a named body-to-footer breathing-room constant");

        // Must be reserved in the footer's own height calculation...
        // Tooltip V2 final corrections: with the "Scroll: More" row gone there is a single sizing pass.
        assertTrue(source.contains("int footerH = BODY_FOOTER_GAP + footerPadding"),
                "expected BODY_FOOTER_GAP reserved in the footer height");

        // ...and actually applied to where the footer's own text is drawn, not just reserved as
        // dead space nobody uses.
        assertTrue(source.contains("panelY + panelH - footerH + BODY_FOOTER_GAP"),
                "expected drawFooter to offset its own text position down by BODY_FOOTER_GAP, "
                        + "turning the reserved space into actual visible breathing room");
    }

    // ── Presentation-cleanup pass, Finding 3: Potion of Healing rarity/classification ────────

    @Test
    void potionOfHealingIsRegisteredAsUncommonRarityWithThePotionClassification() throws Exception {
        Path dndPotionItems = Path.of("src/main/java/zcylas/totality/init/items/DndPotionItems.java");
        String source = read(dndPotionItems);
        String normalized = source.replaceAll("\\s+", " ");
        assertTrue(normalized.contains("new RarityComponent(ItemRarity.UNCOMMON)"),
                "expected the D&D Potion of Healing to be authored as UNCOMMON rarity");
        assertTrue(normalized.contains("ClassificationsComponent.of(ItemType.POTION)"),
                "expected the D&D Potion of Healing to keep its POTION classification");
    }

    @Test
    void potionOfHealingRarityChangeDoesNotTouchHealingMechanicsOrAlchemy() throws Exception {
        // Guards against scope creep: this finding is presentation/metadata only.
        Path dndPotionItems = Path.of("src/main/java/zcylas/totality/init/items/DndPotionItems.java");
        String source = read(dndPotionItems);
        assertTrue(source.contains("HealingAmount.dice(2, Dice.D4, 2), 32"),
                "the healing formula and use duration must remain exactly as before this presentation-only change");
        List<String> importLines = source.lines().map(String::trim).filter(l -> l.startsWith("import ")).toList();
        for (String importLine : importLines) {
            assertFalse(importLine.toLowerCase(java.util.Locale.ROOT).contains("alchemy"),
                    "must still import no Alchemy class: " + importLine);
        }
    }

    // ── Tooltip scrolling event-consumption fix ──────────────────────────────────────────────

    @Test
    void mouseHandlerMixinInjectionIsCancellableAtHead() throws Exception {
        String source = read(MOUSE_HANDLER_MIXIN);
        int injectStart = source.indexOf("@Inject(method = \"onScroll\"");
        assertTrue(injectStart >= 0, "expected the @Inject annotation on the onScroll handler method");
        // Fixed-length window rather than indexOf(")") — the annotation contains a nested paren
        // (@At("HEAD")) whose own closing paren would otherwise truncate the substring early.
        String injectDecl = source.substring(injectStart, Math.min(source.length(), injectStart + 100));
        assertTrue(injectDecl.contains("at = @At(\"HEAD\")"),
                "must inject at HEAD so cancelling skips onScroll's entire original body, "
                        + "including its call to Screen.mouseScrolled partway through");
        assertTrue(injectDecl.contains("cancellable = true"),
                "the injection must be cancellable — this is the only way to stop the raw wheel "
                        + "event from continuing into the screen");
    }

    @Test
    void mouseHandlerMixinOnlyCancelsInsideTheHandlerConditionalsNeverUnconditionally() throws Exception {
        // The exact bug shape this fix guards against: ci.cancel() called outside of, or
        // regardless of, the handler's own return value — which would either always swallow
        // scroll input (breaking every other screen) or never actually gate on whether the
        // tooltip's offset genuinely changed.
        String source = read(MOUSE_HANDLER_MIXIN);
        int methodStart = source.indexOf("private void onScroll(long handle, double xoffset, double yoffset, CallbackInfo ci)");
        assertTrue(methodStart >= 0);
        String body = source.substring(methodStart, source.indexOf("}", source.lastIndexOf("ci.cancel();", source.length())) + 1);

        long cancelCalls = body.lines().filter(l -> l.contains("ci.cancel();")).count();
        assertEquals(2, cancelCalls, "expected exactly two ci.cancel() call sites — one for FluidTankScrollHandler, "
                + "one for TotalityTooltipScrollHandler — each inside its own if-block");

        assertTrue(body.contains("if (FluidTankScrollHandler.onScroll(yoffset)) {"),
                "FluidTankScrollHandler's cancel must be gated behind its own return value");
        assertTrue(body.contains("if (TotalityTooltipScrollHandler.onMouseScroll(yoffset)) {"),
                "TotalityTooltipScrollHandler's cancel must be gated behind its own return value");
    }

    @Test
    void tooltipScrollHandlerWasRenamedToOnMouseScrollForClarity() throws Exception {
        // Aligns naming across the whole call chain: MouseHandlerMixin.onScroll (matching
        // vanilla's own method name) -> TotalityTooltipScrollHandler.onMouseScroll ->
        // TooltipScrollController.onMouseScroll — previously the middle link was still named
        // onScroll, easy to confuse with the raw GLFW-level entry point above it.
        String handlerSource = read(TOOLTIP_SCROLL_HANDLER);
        assertTrue(handlerSource.contains("public static boolean onMouseScroll(double scrollDelta)"),
                "expected TotalityTooltipScrollHandler's entry point to be named onMouseScroll");
        assertFalse(handlerSource.contains("public static boolean onScroll("),
                "the old onScroll name must be fully gone, not left as a second overload");
    }

    @Test
    void tooltipScrollHandlerDocumentsWhyBoundaryFallthroughCanLookLikeSimultaneousScrolling() throws Exception {
        // Pins down that the deferred-execution/event-burst explanation for the reported
        // "both scroll at once" symptom is recorded with its evidentiary basis, not asserted
        // without support.
        String source = read(TOOLTIP_SCROLL_HANDLER);
        assertTrue(source.contains("Minecraft.execute("),
                "expected the documented root cause: GLFW's scroll callback defers actual handling "
                        + "via Minecraft.execute(), which can batch several onScroll calls per gesture");
        assertTrue(source.contains("lambda$setup$4"),
                "expected the Javadoc to cite the specific decompiled lambda that performs the deferral");
    }

    // ── Tooltip-eligibility + wrong-tool review-fix pass ─────────────────────────────────────

    @Test
    void isEligibleNoLongerDelegatesToHasTooltipPresentation() throws Exception {
        String source = read(RENDERER);
        assertFalse(source.contains("ItemComponents.hasTooltipPresentation"),
                "isEligible must no longer gate on Rarity/Lore presence — it must be content-driven");
        assertFalse(source.contains("import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;"),
                "the now-unused ItemComponents import must be removed, not left dangling");
    }

    // Tooltip V2 Pass 1 superseded the content-driven gate (universal routing): the three checks that pinned it
    // (walks the contributor registry / excludes the Header / reuses isVisible) are replaced below. Behavioural proof
    // with real registered items and real SHIFT/CTRL holds lives in the client game test
    // (src/gametest/.../TooltipV2Pass1ClientGameTest), not in these source-text checks.

    @Test
    void isEligibleIsTheUniversalStackOnlyRoutingDecision() throws Exception {
        String source = read(RENDERER);
        int methodStart = source.indexOf("public static boolean isEligible(ItemStack stack)");
        assertTrue(methodStart >= 0, "expected to find the isEligible(ItemStack) method");
        String body = source.substring(methodStart, source.indexOf("\n    }\n", methodStart));
        assertTrue(body.contains("TooltipRouting.of(stack) == TooltipRouting.TOTALITY"));
        assertFalse(body.contains("TooltipDisclosureLevel") || body.contains("contribute("),
                "renderer selection must not depend on disclosure (SHIFT/CTRL) or on contributor content");
    }

    @Test
    void routingKeepsFunctionalVanillaTooltipComponentsAndHiddenTooltips() throws Exception {
        String source = read(Path.of("src/main/java/zcylas/totality/client/tooltip/TooltipRouting.java"));
        assertTrue(source.contains("stack.getTooltipImage().isPresent()"), "bundle-style tooltip images keep vanilla's path");
        assertTrue(source.contains("display.hideTooltip()"), "a hidden tooltip must stay hidden");
        assertTrue(source.contains("return \"minecraft\".equals(namespace) || Totality.MOD_ID.equals(namespace);"),
                "Universal V2 is scoped to the minecraft and totality item namespaces; third-party items keep their own tooltip");
        assertFalse(source.contains("TotalityKeyHelper") || source.contains("TooltipDisclosureLevel.resolve"),
                "routing must not read modifier keys");
    }

    @Test
    void metadataContributorAlwaysEmitsAnUnconditionalHeader() throws Exception {
        // Documents the exact fact isEligibleExcludesTheUnconditionalHeaderSectionFromCountingAsContent
        // depends on: MetadataContributor.contribute() adds a Header before any gated check.
        String source = read(METADATA_CONTRIBUTOR);
        int contributeStart = source.indexOf("public List<TooltipSection> contribute(TooltipContext ctx)");
        int headerAdd = source.indexOf("sections.add(new TooltipSection.Header(stack.getHoverName()));", contributeStart);
        int firstGate = source.indexOf("if (", contributeStart);
        assertTrue(headerAdd >= 0, "expected MetadataContributor to unconditionally add a Header");
        assertTrue(firstGate < 0 || headerAdd < firstGate,
                "the Header add must happen before any rarity/lore/classification gate, confirming it really "
                        + "is unconditional for every item");
    }

    @Test
    void miningToolContributorGatesOnAuthoredProfileNotRarityOrLore() throws Exception {
        String source = read(MINING_TOOL_CONTRIBUTOR);
        assertTrue(source.contains("MiningSourceProfile.resolve(stack).isEmpty()) return List.of()"),
                "MiningToolContributor's only applicability gate must be the authored profile, "
                        + "so a vanilla Diamond Pickaxe with no Rarity/Lore still gets mining rows");
        assertFalse(source.contains("ItemComponents.RARITY") && source.contains("getRarity"),
                "MiningToolContributor must not additionally require a Rarity component");
    }

    @Test
    void blockDurabilityContributorGatesOnBlockItemNotRarityOrLore() throws Exception {
        String source = read(BLOCK_DURABILITY_CONTRIBUTOR);
        assertTrue(source.contains("instanceof BlockItem blockItem"),
                "BlockDurabilityContributor's only applicability gate must be BlockItem-ness, "
                        + "so a vanilla Stone block with no Rarity/Lore still gets Durability rows");
        assertFalse(source.contains("ItemComponents.RARITY"),
                "BlockDurabilityContributor must not additionally require a Rarity component");
    }

    @Test
    void tooltipProfileComponentIsNoLongerReferencedByTheEligibilityCheck() throws Exception {
        String source = read(RENDERER);
        assertFalse(source.contains("ItemComponents.TOOLTIP_PROFILE") && source.contains("isEligible"),
                "the eligibility check must not reference the old explicit-opt-in marker component");
    }

    // ── Wrong-tool / target effectiveness architecture ───────────────────────────────────────

    @Test
    void targetEffectivenessUsesVanillaMineableTagsNotAHardcodedBlockList() throws Exception {
        String source = read(TARGET_EFFECTIVENESS);
        assertTrue(source.contains("BlockTags.MINEABLE_WITH_PICKAXE"));
        assertTrue(source.contains("BlockTags.MINEABLE_WITH_AXE"));
        assertFalse(source.contains("Blocks.STONE") || source.contains("Blocks.DIRT")
                        || source.contains("Blocks.GRASS"),
                "must resolve effectiveness from the semantic mineable tags, never a hand-enumerated "
                        + "per-block list");
    }

    @Test
    void targetEffectivenessExposesExactlyTheRequiredThreeFields() throws Exception {
        String source = read(TARGET_EFFECTIVENESS);
        int declStart = source.indexOf("public record TargetEffectiveness(");
        assertTrue(declStart >= 0);
        int declEnd = source.indexOf(") {", declStart);
        String decl = source.substring(declStart, declEnd);
        assertTrue(decl.contains("damageMultiplier"));
        assertTrue(decl.contains("speedMultiplier"));
        assertTrue(decl.contains("preferred"));
    }

    @Test
    void targetEffectivenessConstantsMatchTheAuthored10And50PercentRule() throws Exception {
        String source = read(TARGET_EFFECTIVENESS);
        assertTrue(source.contains("new TargetEffectiveness(1.00f, 1.00f, true)"),
                "matching/effective target must be 100% damage and 100% speed");
        assertTrue(source.contains("new TargetEffectiveness(0.10f, 0.50f, false)"),
                "wrong-tool-but-breakable target must be exactly 10% damage and 50% speed "
                        + "(tightened from 25% in the playtest-correction pass)");
    }

    @Test
    void miningDamageCalculatorHasNoTargetOrBlockStateSpecificLogic() throws Exception {
        // §6 of the fix pass: do not bake Pickaxe-vs-Stone conditionals into the damage calculator.
        String source = read(MINING_DAMAGE_CALCULATOR);
        assertFalse(source.contains("BlockState"), "MiningDamageCalculator must stay target-independent");
        assertFalse(source.contains("TargetEffectiveness"),
                "target effectiveness must be applied by the caller (PlayerMiningPower), never inside "
                        + "the damage calculator itself");
    }

    @Test
    void miningSpeedCalculatorHasNoTargetOrBlockStateSpecificLogic() throws Exception {
        String source = read(MINING_SPEED_CALCULATOR);
        assertFalse(source.contains("BlockState"), "MiningSpeedCalculator must stay target-independent");
        assertFalse(source.contains("TargetEffectiveness"),
                "target effectiveness must be applied by the caller (PlayerMiningPower), never inside "
                        + "the speed calculator itself");
    }

    @Test
    void playerMiningPowerAppliesTargetEffectivenessAfterImpactAndPowerStr() throws Exception {
        String source = read(PLAYER_MINING_POWER);
        int impactIdx = source.indexOf("MiningDamageCalculator.compute(source, impactLevel)");
        int powerIdx = source.indexOf("powerZoneDamageBonus(band, strModifier)");
        int effectivenessIdx = source.indexOf("TargetEffectiveness.resolve(held, state).damageMultiplier()");
        assertTrue(impactIdx >= 0 && powerIdx >= 0 && effectivenessIdx >= 0,
                "expected to find Impact, Power STR, and target-effectiveness application in compute()");
        assertTrue(impactIdx < effectivenessIdx && powerIdx < effectivenessIdx,
                "target effectiveness must be applied AFTER both Impact and the Power STR contribution "
                        + "have already resolved into the pre-effectiveness damage value");
    }

    @Test
    void effectiveMiningSpeedAppliesTargetEffectivenessAfterTheSpeedCap() throws Exception {
        String source = read(PLAYER_MINING_POWER);
        int methodStart = source.indexOf("public static float effectiveMiningSpeed(");
        assertTrue(methodStart >= 0);
        String body = source.substring(methodStart, Math.min(source.length(), methodStart + 700));
        int capIdx = body.indexOf("MiningSpeedCalculator.compute(");
        int effectivenessIdx = body.indexOf("TargetEffectiveness.resolve(tool, state).speedMultiplier()");
        assertTrue(capIdx >= 0 && effectivenessIdx >= 0);
        assertTrue(capIdx < effectivenessIdx,
                "the target speed multiplier must be applied to the already-capped usable speed — "
                        + "'final mining cadence = effective authored speed x target effectiveness' — "
                        + "never before the cap, so a wrong tool can never retain the same capped rate "
                        + "a matching tool would");
    }

    @Test
    void wrongToolStillUsesTheOrdinaryWearPolicyNoExtraPenalty() throws Exception {
        // §5: wrong-tool successful impacts must still be ordinary DAMAGED/BROKEN outcomes that flow
        // through the existing, unmodified base-wear/Power-zone-wear rules — no new penalty path.
        String managerSource = read(Path.of("src/main/java/zcylas/totality/api/mining/PlayerMiningManager.java"));
        assertFalse(managerSource.toLowerCase(java.util.Locale.ROOT).contains("wrong tool") &&
                        managerSource.contains("wear ="),
                "no separate wrong-tool wear calculation should exist — wear stays outcome-based only");
        assertTrue(managerSource.contains("MiningTuning.baseWear(result.outcome(), tool)"),
                "the ordinary outcome-based base wear policy must be unchanged");
    }

    // ── COMMON Rarity + Lore for six vanilla blocks ───────────────────────────────────────────

    @Test
    void sixVanillaBlocksAreRegisteredWithCommonRarityAndLoreViaDefaultItemComponentEvents() throws Exception {
        String source = read(VANILLA_ITEM_PRESENTATION);
        assertTrue(source.contains("DefaultItemComponentEvents.MODIFY"),
                "must use Fabric's standard mechanism for modifying an already-registered vanilla item's "
                        + "default components, not a second lore/rarity system");
        for (String item : List.of("Items.OAK_LOG", "Items.STONE", "Items.DIORITE", "Items.ANDESITE",
                "Items.GRANITE", "Items.COBBLESTONE")) {
            assertTrue(source.contains(item), "expected " + item + " to be registered");
        }
        assertTrue(source.contains("ItemRarity.COMMON"), "all six items must use COMMON rarity");
        long loreLines = source.lines().filter(l -> l.contains("Items.") && l.contains(",")
                && !l.contains("DefaultItemComponentEvents") && !l.contains("import")).count();
        assertTrue(loreLines >= 6, "expected each of the six items to carry its own lore string");
    }

    @Test
    void vanillaItemPresentationIsRegisteredFromMainInit() throws Exception {
        String source = read(TOTALITY_MAIN);
        assertTrue(source.contains("VanillaItemPresentation.register();"),
                "expected VanillaItemPresentation.register() to actually be called from mod init");
    }

    @Test
    void vanillaItemPresentationLoreContainsNoGameplayStatStrings() throws Exception {
        // §9: lore must be flavor text, not a restatement of a structural tooltip row.
        String source = read(VANILLA_ITEM_PRESENTATION);
        String lower = source.toLowerCase(java.util.Locale.ROOT);
        assertFalse(lower.contains("durability: 100") || lower.contains("100 durability"),
                "lore must not restate the Block Durability stat, which already has its own tooltip row");
        assertFalse(lower.contains("required mining tier"),
                "lore must not restate the Required Mining Tier stat either");
    }

    // ── Mining Tier vs Target Effectiveness fix pass ─────────────────────────────────────────

    @Test
    void miningSourceProfileComputesTierFromEachMaterialsOwnPickaxeNeverHardcoded() throws Exception {
        // Playtest-correction pass: the probe moved from an eager static-init-time factory method
        // to a lazy call inside resolve() (a real datagen-bootstrap crash fix — see
        // miningSourceProfileTierProbeIsLazyNotEagerAtClassLoadTime) — still MiningTier.ofTool
        // against the material's own Pickaxe, never a hand-typed number, just no longer eager.
        String source = read(MINING_SOURCE_PROFILE);
        assertTrue(source.contains("MiningTier.ofTool(new ItemStack(m.pickaxe()))"),
                "each material's authored Tier must be probed from its own Pickaxe via the "
                        + "existing MiningTier.ofTool, never a hand-typed number — this is what "
                        + "guarantees it matches the pre-existing, already-authoritative progression");
        int declStart = source.indexOf("public record Entry(");
        assertTrue(declStart >= 0);
        String declLine = source.substring(declStart, source.indexOf(")", declStart) + 1);
        assertTrue(declLine.contains("tier"), "Entry must now expose tier alongside miningDamage/miningSpeed");
    }

    @Test
    void resolvedMiningSourceReadsAuthoredTierForProfiledAndLegacyOfToolOtherwise() throws Exception {
        String source = read(RESOLVED_MINING_SOURCE);
        int methodStart = source.indexOf("public static ResolvedMiningSource of(");
        assertTrue(methodStart >= 0);
        String body = source.substring(methodStart, Math.min(source.length(), methodStart + 700));
        assertTrue(body.contains("profile.map(MiningSourceProfile.Entry::tier)"),
                "a profiled source's Tier must come from the authored MiningSourceProfile entry");
        assertTrue(body.contains(".orElseGet(() -> MiningTier.ofTool(tool))"),
                "a non-profiled (legacy) source must still fall back to MiningTier.ofTool, unchanged");
        // Exactly one Tier value is ever constructed for the returned record — never two competing ones.
        long tierAssignments = body.lines().filter(l -> l.trim().startsWith("int tier =")).count();
        assertEquals(1, tierAssignments, "expected exactly one local Tier resolution, not two competing values");
    }

    @Test
    void miningTierOfToolItselfIsUnchangedButDocumentsItIsNotForProfiledSources() throws Exception {
        // ofTool's own probing logic (Stone/Iron Ore/Diamond Ore/Obsidian) must be byte-for-byte
        // unchanged — this pass corrects WHO calls it for a profiled source, never the function itself.
        String source = read(MINING_TIER);
        assertTrue(source.contains("if (!tool.isCorrectToolForDrops(Blocks.STONE.defaultBlockState())) return 0;"));
        assertTrue(source.contains("if (!tool.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState())) return 1;"));
        assertTrue(source.contains("if (!tool.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState())) return 2;"));
        assertTrue(source.contains("if (!tool.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())) return 3;"));
        assertTrue(source.contains("return 4;"));
        assertTrue(source.contains("Do not call this directly for a PROFILED Pickaxe/Axe"),
                "ofTool's Javadoc must steer callers toward ResolvedMiningSource for profiled sources");
    }

    @Test
    void resolvedMiningSourceNoLongerClaimsProfiledToolsHaveNoWrongToolPenalty() throws Exception {
        // The stale TODO this pass removes: it predates TargetEffectiveness (added in PASS3) and
        // claimed no wrong-tool-category penalty existed at all.
        String source = read(RESOLVED_MINING_SOURCE);
        assertFalse(source.contains("there is currently NO wrong-tool-category penalty"),
                "the stale pre-PASS3 TODO claiming no wrong-tool penalty exists must be removed");
        assertFalse(source.contains("TODO (tracked, not decided this pass)"),
                "the stale TODO marker itself must be gone — target effectiveness is now implemented, not pending");
        assertTrue(source.contains("TargetEffectiveness"),
                "the class Javadoc must reference the now-implemented TargetEffectiveness, not merely "
                        + "note its absence");
    }

    @Test
    void miningVerificationNoLongerClaimsIronAxeVsStoneIsAlwaysIneffective() throws Exception {
        // §4 of the Tier fix pass: the manual-test/review documentation must not simultaneously
        // claim "Axe vs Stone uses the wrong-tool multiplier" and "Axe vs Stone is always
        // Tier-INEFFECTIVE" — after this fix, Iron Axe vs Stone is a genuine wrong-tool DAMAGED hit.
        Path miningVerification = Path.of("src/main/java/zcylas/totality/api/mining/MiningVerification.java");
        String source = read(miningVerification);
        assertFalse(source.contains("always INEFFECTIVE, confirming the note above"),
                "the old 'Iron Axe vs Stone is always INEFFECTIVE' assertion must be gone");
        assertTrue(source.contains("Tier passes -> wrong-tool DAMAGED, 50 x 0.10 = 5 (not INEFFECTIVE)"),
                "expected the corrected live assertion proving Iron Axe vs Stone is now a reachable "
                        + "wrong-tool DAMAGED case (playtest-correction pass: wrong-tool Damage is now x0.10, not x0.25)");
    }

    @Test
    void vanillaItemPresentationLoreAvoidsBedrockCollisionAndImpliedProcessing() throws Exception {
        // §5: "bedrock material" collides with the distinct Bedrock block; "squared" implies
        // already-hewn timber rather than a plain cut log.
        String source = read(VANILLA_ITEM_PRESENTATION);
        String lower = source.toLowerCase(java.util.Locale.ROOT);
        assertFalse(lower.contains("bedrock material"), "Stone's lore must not say \"bedrock material\"");
        assertFalse(lower.contains("squared"), "Oak Log's lore must not say \"squared\"");
        assertTrue(source.contains("ItemRarity.COMMON"), "rarity must remain COMMON for all six items");
    }

    // ── Playtest-correction pass: wrong-tool x0.10, Dirt/Grass=100, Shovels, four Power bands,
    //    floating-text colors, tooltip width fix, JEI z-order ──────────────────────────────────

    @Test
    void targetEffectivenessResolvesShovelViaTheSemanticShovelTags() throws Exception {
        String source = read(TARGET_EFFECTIVENESS);
        assertTrue(source.contains("ItemTags.SHOVELS"), "Shovel must be resolved via the semantic ItemTags.SHOVELS tag");
        assertTrue(source.contains("BlockTags.MINEABLE_WITH_SHOVEL"),
                "Shovel matching must be resolved via the semantic BlockTags.MINEABLE_WITH_SHOVEL tag");
    }

    @Test
    void miningSourceProfileIncludesShovelInTheAuthoredMaterialTable() throws Exception {
        String source = read(MINING_SOURCE_PROFILE);
        assertTrue(source.contains("Item shovel"), "the Material record must carry a Shovel item alongside Pickaxe/Axe");
        assertTrue(source.contains("Items.COPPER_SHOVEL"), "Copper Shovel must be included, exactly like the other Copper tools");
        assertTrue(source.contains("Items.NETHERITE_SHOVEL") && source.contains("Items.WOODEN_SHOVEL"),
                "every material's Shovel must be included, not just a subset");
    }

    @Test
    void miningSourceProfileTierProbeIsLazyNotEagerAtClassLoadTime() throws Exception {
        // A real regression found and fixed this pass: probing MiningTier.ofTool(new ItemStack(...))
        // at MATERIALS' static-init time crashes during datagen bootstrap ("Components not bound
        // yet") — the probe must happen lazily inside resolve(), never at class-load time.
        String source = read(MINING_SOURCE_PROFILE);
        assertFalse(source.contains("private static Material material("),
                "the eager tier-probing factory method must be gone — MATERIALS now stores plain "
                        + "Material records with no tier field");
        int resolveIdx = source.indexOf("public static Optional<Entry> resolve(");
        assertTrue(resolveIdx >= 0, "resolve(ItemStack) must still exist");
        assertTrue(source.indexOf("MiningTier.ofTool(new ItemStack(m.pickaxe()))", resolveIdx) > resolveIdx,
                "the tier probe must happen inside resolve(), lazily, on every real call");
    }

    @Test
    void blockDurabilityDefinitionsRegistersDirtAndGrassBlockAt100() throws Exception {
        String source = read(BLOCK_DURABILITY_DEFINITIONS);
        assertTrue(source.contains("BlockDurability.register(Blocks.DIRT, oneHundred)"), "Dirt must be registered at 100 Durability");
        assertTrue(source.contains("BlockDurability.register(Blocks.GRASS_BLOCK, oneHundred)"), "Grass Block must be registered at 100 Durability");
        assertFalse(source.contains("Blocks.SAND") || source.contains("Blocks.GRAVEL") || source.contains("Blocks.CLAY")
                        || source.contains("Blocks.FARMLAND"),
                "Sand/Gravel/Clay/Farmland must NOT be registered this pass — deliberately deferred");
    }

    @Test
    void targetEffectivenessWrongToolDamageIsTightenedToTenPercent() throws Exception {
        String source = read(TARGET_EFFECTIVENESS);
        assertTrue(source.contains("new TargetEffectiveness(0.10f, 0.50f, false)"),
                "wrong-tool damage must be exactly x0.10 (tightened from x0.25), speed unchanged at x0.50");
    }

    @Test
    void miningTuningHasFourRealPowerBandsWithWhiteGivingNoStrBonus() throws Exception {
        String source = read(MINING_TUNING);
        assertTrue(source.contains("BAND_WHITE") && source.contains("BAND_GREEN")
                        && source.contains("BAND_ORANGE") && source.contains("BAND_RED"),
                "all four real Power bands (WHITE/GREEN/ORANGE/RED) must be named constants");
        assertTrue(source.contains("STR_ZONE_WHITE = 0"), "WHITE must contribute zero STR bonus");
        assertTrue(source.contains("case BAND_GREEN -> STR_ZONE_GREEN"),
                "GREEN must be its own switch case now, no longer collapsed into WHITE's default");
    }

    @Test
    void combatTextRendererNormalAndWhiteBandFloatingDamageIsWhite() throws Exception {
        String source = read(COMBAT_TEXT_RENDERER);
        assertTrue(source.contains("case 1 -> 0xFFFFFF"), "the WHITE Power band (1) must render white");
        assertTrue(source.contains("default -> 0xFFFFFF"), "an ordinary, non-Power hit (band 0) must now render white, not the old yellow-ish default");
    }

    @Test
    void powerMiningMeterHudDrawsFourDistinctZonesFromSharedTuningConstants() throws Exception {
        String source = read(POWER_MINING_METER_HUD);
        assertTrue(source.contains("MiningTuning.WHITE_ZONE_MAX") && source.contains("MiningTuning.GREEN_ZONE_MAX")
                        && source.contains("MiningTuning.RED_ZONE"),
                "the four meter zones must be split at the same tuning constants gameplay resolves bands from, never invented literals");
    }

    @Test
    void naturalRowWidthMeasurementMatchesTheGutterlessBodyWidth() throws Exception {
        // §16: the natural-width measurement must reserve exactly what bodyContentW reserves at layout
        // time, or rows wrap unnecessarily. Since the scrollbar's removal that is nothing: the visual
        // (scale-adjusted) row width alone decides the panel width.
        String source = read(RENDERER);
        int methodIdx = source.indexOf("static int measureNaturalContentWidth(");
        assertTrue(methodIdx >= 0, "measureNaturalContentWidth must still exist");
        assertTrue(source.indexOf("Math.min(visualRowW, PREFERRED_MAX_WIDTH)", methodIdx) > methodIdx);
    }

    @Test
    void jeiZOrderFixDefersTotalityTooltipToTheSameStratumVanillaTooltipsUse() throws Exception {
        // §18: painting immediately inside onSetTooltip put the Totality panel in the same render
        // stratum an overlay mod's ingredient panel paints into. The fix defers the actual draw to
        // GuiGraphicsExtractor#extractDeferredElements — the exact point/stratum vanilla's own
        // tooltip already renders from — via a small Runnable handoff, never a hard JEI dependency.
        String mixinSource = read(ABSTRACT_CONTAINER_SCREEN_MIXIN);
        assertTrue(mixinSource.contains("totality$deferTooltip("),
                "the Totality tooltip render call must be deferred, not painted immediately in onSetTooltip");
        assertFalse(mixinSource.contains("TotalityTooltipRenderer.render(graphics, font, stack, x, y, text, data, (Screen) (Object) this, this.hoveredSlot);"),
                "the old immediate render call must be gone");

        String extractorSource = read(GUI_GRAPHICS_EXTRACTOR_MIXIN);
        assertTrue(extractorSource.contains("extractDeferredElements"),
                "the flush must be injected into extractDeferredElements — the same method that flushes vanilla's own deferred tooltip");
        assertTrue(extractorSource.contains("nextStratum()"),
                "the deferred Totality tooltip must push a new stratum, exactly like vanilla's own deferred tooltip does");

        String interfaceSource = read(TOTALITY_GUI_GRAPHICS);
        assertTrue(interfaceSource.contains("void totality$deferTooltip(Runnable render)"),
                "the defer entry point must be exposed on the shared TotalityGuiGraphics interface");

        // No hard JEI dependency: "JEI" may appear in explanatory comments (why this fix exists),
        // but never an import of, or a call into, an actual JEI class/package.
        assertFalse(mixinSource.contains("import mezz.jei") || extractorSource.contains("import mezz.jei"),
                "the fix must not introduce a hard dependency on JEI's classes");
    }

    // ── Tooltip layout/presentation cleanup pass (2026-09-22) ────────────────────────────────

    @Test
    void bodyTextScaleConstantExistsAndIsWithinTheRequestedEightyFiveToNinetyPercentRange() throws Exception {
        String source = read(RENDERER);
        assertTrue(source.contains("private static final float BODY_TEXT_SCALE = 0.875f;"),
                "expected a single named body-text-scale constant at 87.5%, within the requested 85-90% range");
    }

    @Test
    void statIconsScaleWithBodyTextViaTheSameTransformNoSeparateIconConstant() throws Exception {
        // §10: icon size must be proportional to the smaller body text, without a second,
        // independently-tuned icon-size constant to keep in sync — achieved by scaling the whole
        // row (icon included) through one shared pose transform, never a dedicated icon scale.
        String source = read(RENDERER);
        assertFalse(source.contains("ICON_SCALE") || source.contains("iconScale"),
                "no separate icon-specific scale constant should exist — icons scale via the same "
                        + "row-level BODY_TEXT_SCALE transform as their row's text");
        int drawLoopIdx = source.indexOf("boolean scaled = isScaledSection(laid.section());");
        assertTrue(drawLoopIdx >= 0, "expected the scale-aware body draw loop");
        String drawLoopRegion = source.substring(drawLoopIdx, Math.min(source.length(), drawLoopIdx + 1200));
        assertTrue(drawLoopRegion.contains("graphics.pose().pushMatrix()")
                        && drawLoopRegion.contains("graphics.pose().scale(bodyScale, bodyScale)")
                        && drawLoopRegion.contains("graphics.pose().popMatrix()"),
                "the whole row (icon + label + value, via the single draw(...) call) must be "
                        + "wrapped in one pose scale transform, the same pushMatrix/scale/popMatrix "
                        + "idiom already used extensively elsewhere in this codebase");
    }

    @Test
    void naturalWidthMeasurementAccountsForScaledTextAndTheScrollbarGutterTogether() throws Exception {
        String source = read(RENDERER);
        int methodIdx = source.indexOf("static int measureNaturalContentWidth(");
        assertTrue(methodIdx >= 0);
        assertTrue(source.indexOf("isScaledSection(section) ? visualForScale(rowW, bodyScale) : rowW", methodIdx) > methodIdx,
                "a scaled row's contribution to the panel's natural width must be its VISUAL "
                        + "(scaled-down) width, never the larger logical one — otherwise the panel "
                        + "would be sized wider than the scaled content actually needs");
    }

    @Test
    void preservedVanillaAndTechnicalContentKeepRealSizeWhileLoreUsesTheQuieterBodyScale() throws Exception {
        // §9/§14: preserved vanilla/third-party lines stay at real vanilla size so they read exactly as vanilla
        // shows them. Tooltip V2 bottom-presentation slice: lore was too large next to the compact rows, so it now
        // uses the (pixel-safe) body text scale too — it is no longer in the excluded list.
        String source = read(RENDERER);
        int methodIdx = source.indexOf("private static boolean isScaledSection(");
        assertTrue(methodIdx >= 0, "expected the isScaledSection classifier");
        String methodBody = source.substring(methodIdx, source.indexOf("\n    }\n", methodIdx));
        assertTrue(methodBody.contains("case TooltipSection.ExternalContent ignored -> false")
                        && methodBody.contains("case TooltipSection.TechnicalInfo ignored -> false"),
                "ExternalContent (preserved vanilla lines) and TechnicalInfo must stay excluded from the body text scale");
        assertFalse(methodBody.contains("case TooltipSection.Description ignored -> false"),
                "lore now uses the quieter body scale");
    }

    @Test
    void titleAndRarityBadgesAreNeverScaledTheyAreNotTooltipSectionsAtAll() throws Exception {
        // §9: "Keep the ITEM NAME/title at its normal intended prominence" — title/badges are
        // handled entirely outside the body TooltipSection list (drawAnimatedTitle/drawBadgeRow),
        // so isScaledSection (which only ever examines body-list TooltipSections) structurally
        // cannot reach them — confirmed here rather than merely assumed.
        String source = read(RENDERER);
        int titleCallIdx = source.indexOf("drawAnimatedTitle(graphics, font, titleText,");
        assertTrue(titleCallIdx >= 0, "expected the title draw call");
        String beforeTitle = source.substring(Math.max(0, titleCallIdx - 200), titleCallIdx);
        assertFalse(beforeTitle.contains("pose().scale(BODY_TEXT_SCALE"),
                "the title must never be drawn inside a BODY_TEXT_SCALE pose transform");
    }

    @Test
    void miningToolContributorStillNeverAppliesTargetEffectivenessToTheStaticTooltip() throws Exception {
        // Unrelated to this pass's layout changes, but explicitly re-confirmed per this pass's own
        // "preserve the content-driven eligibility / do not regress prior tooltip fixes" scope —
        // MiningToolContributor itself was not touched, so this is a straightforward carry-over
        // check, not new behavior.
        String source = read(MINING_TOOL_CONTRIBUTOR);
        assertFalse(source.contains("TargetEffectiveness"),
                "the tool tooltip must remain target-independent — MiningToolContributor must never reference TargetEffectiveness");
        assertTrue(source.contains("\"/s\""), "Mining Speed's provenance must still carry /s units");
        assertTrue(source.contains("TooltipDisclosureLevel.DETAILS"),
                "the Shift provenance rows must remain DETAILS-gated");
        // Block Breaking V2 Pass 2: the misleading numeric Force Tolerance row is hidden entirely (field preserved).
        assertFalse(source.contains("\"Force Tolerance\""), "Force Tolerance must not be displayed as a number");
    }

    @Test
    void tooltipEligibilityRemainsContentDrivenNeverGatedOnAuthoredRarityOrLore() throws Exception {
        String source = read(RENDERER);
        int methodIdx = source.indexOf("public static boolean isEligible(");
        assertTrue(methodIdx >= 0);
        String methodBody = source.substring(methodIdx, source.indexOf("\n    }\n", methodIdx));
        assertFalse(methodBody.contains("hasTooltipPresentation") || methodBody.contains("RarityComponent")
                        || methodBody.contains("LoreComponent"),
                "isEligible must stay content-driven (any visible contributor section) and never "
                        + "reintroduce Rarity/Lore as a mandatory gate — future universal Item "
                        + "Durability will also be a plain contributor, relying on this staying true");
    }
}
