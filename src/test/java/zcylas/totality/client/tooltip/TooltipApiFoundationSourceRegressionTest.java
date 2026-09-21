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
    private static final Path WEIGHT_CONTRIBUTOR =
            Path.of("src/main/java/zcylas/totality/client/tooltip/contributor/WeightContributor.java");
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
    void onlyCopperBatteryHasAnAuthoredRarityWeightAndLore() throws Exception {
        // Documents the deliberate "do not invent missing data" scope decision: Iron/Gold/Diamond/
        // Netherite have no canonical rarity/weight/lore anywhere in the repository, so none is
        // fabricated for them even though they now render through the custom panel.
        String source = read(ENERGY_ITEMS);
        int copperStart = source.indexOf("COPPER_BATTERY");
        int ironStart = source.indexOf("IRON_BATTERY");
        String copperBlock = source.substring(copperStart, ironStart);
        assertTrue(copperBlock.contains("RarityComponent(ItemRarity.CRUDE)"));

        int netheriteStart = source.indexOf("NETHERITE_BATTERY");
        int umbraStart = source.indexOf("UMBRA_VISOR");
        String netheriteBlock = source.substring(netheriteStart, umbraStart);
        assertFalse(netheriteBlock.contains("RarityComponent"),
                "Netherite Battery must not have an invented rarity");
        assertFalse(netheriteBlock.contains("WeightComponent"),
                "Netherite Battery must not have an invented weight");
        assertFalse(netheriteBlock.contains("LoreComponent"),
                "Netherite Battery must not have invented lore");
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
    void itemComponentsDocumentsTheRarityFallbackAsTemporary() throws Exception {
        String source = read(ITEM_COMPONENTS);
        assertTrue(source.contains("hasTooltipPresentation"));
        assertTrue(source.contains("TooltipProfileComponent"));
        assertTrue(source.toLowerCase(java.util.Locale.ROOT).contains("temporary"),
                "the rarity compatibility fallback must be documented as temporary, not a permanent hidden gate");
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
        for (Path path : List.of(HEALING_POTION_CONTRIBUTOR, WEIGHT_CONTRIBUTOR)) {
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
    void tooltipProfileComponentDocumentsTheTemporaryRarityFallback() throws Exception {
        Path path = Path.of("src/main/java/zcylas/totality/api/core/rpgutils/rarity/TooltipProfileComponent.java");
        String source = read(path);
        assertTrue(source.toLowerCase(java.util.Locale.ROOT).contains("temporary"),
                "TooltipProfileComponent's own Javadoc must acknowledge the rarity compatibility fallback, "
                        + "not just ItemComponents' — a reader of this file alone should not be misled");
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
    void footerHintLinesSplitsAnIndividualHintThatIsWiderThanMaxWidth() throws Exception {
        String source = read(RENDERER);
        assertTrue(source.contains("private static List<FormattedCharSequence> splitHintLine"),
                "expected a dedicated helper that runs a packed hint line through the native splitter");
        int helperStart = source.indexOf("private static List<FormattedCharSequence> splitHintLine");
        String helperBody = source.substring(helperStart, Math.min(source.length(), helperStart + 250));
        assertTrue(helperBody.contains("font.split(Component.literal(text), maxWidth)"),
                "splitHintLine must use the native Font#split splitter — the same mechanism used "
                        + "everywhere else in this file for safe wrapping");

        int packerStart = source.indexOf("static List<FormattedCharSequence> footerHintLines");
        int packerEnd = source.indexOf("private static List<FormattedCharSequence> splitHintLine");
        String packerBody = source.substring(packerStart, packerEnd);
        assertTrue(packerBody.contains("splitHintLine("),
                "every packed line — including a single hint too wide to pack with anything else — "
                        + "must be routed through splitHintLine before being added to the result");
    }

    @Test
    void drawFooterClampsHintLineXSoItNeverDrawsLeftOfThePanel() throws Exception {
        String source = read(RENDERER);
        int footerStart = source.indexOf("private static void drawFooter");
        String footerBody = source.substring(footerStart, Math.min(source.length(), footerStart + 900));
        assertTrue(footerBody.contains("Math.max(minX,"),
                "the footer hint line's right-aligned X must be clamped so it can never sit left of "
                        + "the panel's own inner padding, even for an (already-wrapped) line as wide as the panel");
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
        String body = source.substring(methodStart, source.indexOf("naturalBadgeRowWidth", methodStart));

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

    // ── Visual correction pass, Finding 2: flowing badge row ─────────────────────────────────

    @Test
    void badgeSpecsPlaceRarityBeforeClassificationsPreservingAuthoredOrder() throws Exception {
        String source = read(RENDERER);
        int methodStart = source.indexOf("private static List<BadgeSpec> buildBadgeSpecs");
        assertTrue(methodStart >= 0);
        String body = source.substring(methodStart, source.indexOf("private static int badgeWidth", methodStart));

        int rarityIdx = body.indexOf("authoredRarity != null");
        int classificationsIdx = body.indexOf("for (ItemType type : classifications)");
        assertTrue(rarityIdx >= 0 && classificationsIdx >= 0, "expected both a rarity and a classifications branch");
        assertTrue(rarityIdx < classificationsIdx,
                "the rarity badge must be appended before classification badges, preserving 'rarity first, "
                        + "then classifications in authored order'");
    }

    @Test
    void rarityNeverGetsAMandatorySeparateBadgeRow() throws Exception {
        // The old header drew the rarity badge on its own row, then classification rows below it
        // unconditionally. The new flow builds ONE combined badge list and wraps it together —
        // confirmed by the absence of any code path that draws rarity separately from wrapBadges.
        String source = read(RENDERER);
        assertFalse(source.contains("drawRarityBadge"),
                "the old dedicated drawRarityBadge method/call must be gone — rarity now flows through "
                        + "the same wrapBadges/drawBadgeRow path as every classification badge");
        assertFalse(source.contains("drawClassificationRow"),
                "the old dedicated per-classification-row draw method must be gone for the same reason");
        assertTrue(source.contains("wrapBadges(badgeSpecs, font, titleAreaW)"),
                "expected rarity and classifications to be wrapped together as one combined badge list");
    }

    // ── Visual correction pass, Finding 4/8: scrollbar gutter reservation ───────────────────

    @Test
    void bodyContentWidthReservesADedicatedScrollbarGutter() throws Exception {
        String source = read(RENDERER);
        assertTrue(source.contains("private static final int SCROLLBAR_GUTTER"),
                "expected a named constant reserving scrollbar width so text can never sit under it");
        assertTrue(source.contains("innerW - SCROLLBAR_GUTTER"),
                "body content width must be computed by subtracting the scrollbar gutter from the panel's inner width");
    }

    @Test
    void scrollIndicatorDrawsInsideTheReservedGutterNotOverTheBodyContentColumn() throws Exception {
        String source = read(RENDERER);
        int methodStart = source.indexOf("private static void drawScrollIndicator");
        assertTrue(methodStart >= 0);
        String body = source.substring(methodStart, Math.min(source.length(), methodStart + 700));
        assertTrue(body.contains("bodyLeft + bodyContentW + (SCROLLBAR_GUTTER - trackW) / 2"),
                "the scroll track must sit inside the gutter reserved outside the body content column, "
                        + "not overlapping it");
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
    void panelPaddingAndSeparatorHeightWereTightened() throws Exception {
        String source = read(RENDERER);
        assertTrue(source.contains("private static final int PADDING = 8"),
                "PADDING must be reduced from the old 10px for a less heavy panel");
        // separatorH: 10 (foundation) -> 7 (visual-correction pass) -> 9 (presentation-cleanup
        // pass, Finding 2 — a touch more header/body breathing room, still tighter than the
        // original 10px). See headerToBodySeparatorGapWasLightlyIncreased for the dedicated check.
        assertTrue(source.contains("int separatorH = 9"),
                "the header-to-body separator gap must still be tighter than the original 10px");
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
    void headerToBodySeparatorGapWasLightlyIncreased() throws Exception {
        String source = read(RENDERER);
        assertTrue(source.contains("int separatorH = 9;"),
                "expected separatorH raised from 7 to 9 for a touch more header/body breathing room");
    }

    @Test
    void bodyToFooterGapIsANamedConstantAppliedToBothFooterHeightAndItsDrawPosition() throws Exception {
        String source = read(RENDERER);
        assertTrue(source.contains("private static final int BODY_FOOTER_GAP = 4;"),
                "expected a named body-to-footer breathing-room constant");

        // Must be reserved in the footer's own height calculation (both sizing passes)...
        long footerHeightUses = source.lines()
                .filter(l -> l.contains("footerPadding + BODY_FOOTER_GAP"))
                .count();
        assertEquals(2, footerHeightUses,
                "expected BODY_FOOTER_GAP added to both the provisional (pass 1) and final footer height calculations");

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
}
