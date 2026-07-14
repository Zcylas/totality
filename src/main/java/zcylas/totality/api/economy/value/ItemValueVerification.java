package zcylas.totality.api.economy.value;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lightweight in-mod self-test for the central item-value/pricing system, run once at
 * {@code SERVER_STARTED} in a development environment only.
 *
 * <p>The project has no JUnit/test source set at all (confirmed: no {@code src/test}, no test
 * dependency anywhere in {@code build.gradle}) — adding one would be a disproportionate new
 * framework for this implementation phase. This runs as a real in-game self-check instead,
 * giving the same "does this actually work" verification without new build infrastructure.
 *
 * <p>Concise by default via {@link VerificationReporter} — only a one-line summary prints
 * unless {@code -Dtotality.verboseVerification=true} is set, in which case every individual PASS
 * also prints. Failures always print individually either way.
 *
 * <p>The registry-validation checks in {@link #checkRegistryValidation} call
 * {@link ItemValueRegistry#validate} directly — a PURE function — so feeding it synthetic
 * conflicting data never produces real {@code ERROR}-level log lines; the structured
 * {@code ValidationResult} is inspected silently instead.
 */
final class ItemValueVerification {

    private ItemValueVerification() {}

    static void runSelfTestIfDev(MinecraftServer server) {
        if (!VerificationReporter.isDevEnvironment()) return;

        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "ItemValueVerification");
        ServerPlayer fakePlayer = TotalityFakePlayer.create(server.overworld(), "[ItemValueVerification]");

        checkResolves(r, "Bread -> 12", new ItemStack(Items.BREAD), 12L);
        checkResolves(r, "Stone Axe -> 36", new ItemStack(Items.STONE_AXE), 36L);

        ItemStack waterBottle = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        ItemStack healingPotion = PotionContents.createItemStack(Items.POTION, Potions.HEALING);

        checkResolves(r, "Water Bottle -> 8", waterBottle, 8L);
        checkResolves(r, "Potion of Healing -> 80", healingPotion, 80L);

        checkNotResolves(r, "Water Bottle does not resolve as Healing Potion (80)", waterBottle, 80L);
        checkNotResolves(r, "Healing Potion does not resolve as Water Bottle (8)", healingPotion, 8L);

        ItemStack namedHealingPotion = healingPotion.copy();
        namedHealingPotion.set(DataComponents.CUSTOM_NAME, Component.literal("Grandma's Remedy"));
        checkResolves(r, "Custom-named Healing Potion still -> 80", namedHealingPotion, 80L);

        ItemStack unrelatedPotion = PotionContents.createItemStack(Items.POTION, Potions.NIGHT_VISION);
        checkEmpty(r, "Unauthored potion variant (Night Vision) has no resolved value", unrelatedPotion);

        checkQuantityOverflow(r, fakePlayer);
        checkInvalidQuantity(r, fakePlayer);
        checkPairwiseConflictHelpers(r);
        checkRegistryValidation(r);
        checkAuthoredOverride(r, fakePlayer);

        r.summarize();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Resolution against the real, datapack-loaded registry
    // ─────────────────────────────────────────────────────────────────────

    private static void checkResolves(VerificationReporter r, String label, ItemStack stack, long expected) {
        Optional<Long> resolved = ItemValueRegistry.INSTANCE.resolveBaseValue(stack);
        boolean pass = resolved.isPresent() && resolved.get() == expected;
        r.check(label, pass, "expected " + expected + ", got " + resolved);
    }

    private static void checkNotResolves(VerificationReporter r, String label, ItemStack stack, long forbidden) {
        Optional<Long> resolved = ItemValueRegistry.INSTANCE.resolveBaseValue(stack);
        boolean pass = resolved.isEmpty() || resolved.get() != forbidden;
        r.check(label, pass, "resolved to forbidden value " + forbidden);
    }

    private static void checkEmpty(VerificationReporter r, String label, ItemStack stack) {
        Optional<Long> resolved = ItemValueRegistry.INSTANCE.resolveBaseValue(stack);
        r.check(label, resolved.isEmpty(), "expected no resolved value, got " + resolved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // ItemPricingService: overflow, invalid quantity
    // ─────────────────────────────────────────────────────────────────────

    private static void checkQuantityOverflow(VerificationReporter r, ServerPlayer player) {
        String label = "Quantity overflow is detected (ArithmeticException), not wrapped";
        PricingContext overflowContext = new PricingContext(
                player, Optional.empty(), PricingDirection.RETAIL, Optional.of(Long.MAX_VALUE));
        boolean pass;
        try {
            ItemPricingService.quoteRetail(new ItemStack(Items.BREAD), 2, overflowContext);
            pass = false;
        } catch (ArithmeticException expected) {
            pass = true;
        }
        r.check(label, pass, "expected ArithmeticException, none thrown");
    }

    private static void checkInvalidQuantity(VerificationReporter r, ServerPlayer player) {
        String label = "Invalid quantity (0 and negative) is rejected";
        PricingContext context = PricingContext.retail(player);
        boolean zeroRejected = throwsIllegalArgument(() -> ItemPricingService.quoteRetail(new ItemStack(Items.BREAD), 0, context));
        boolean negativeRejected = throwsIllegalArgument(() -> ItemPricingService.quoteRetail(new ItemStack(Items.BREAD), -1, context));
        r.check(label, zeroRejected && negativeRejected,
                "zero rejected=" + zeroRejected + ", negative rejected=" + negativeRejected);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Pairwise ItemValueRuleConflicts checks (isDuplicate/isAmbiguous in isolation)
    // ─────────────────────────────────────────────────────────────────────

    private static void checkPairwiseConflictHelpers(VerificationReporter r) {
        String label = "Pairwise duplicate/ambiguity helpers (ItemValueRuleConflicts)";

        ItemValueRule plainBreadA = new ItemValueRule(new ItemStack(Items.BREAD), 12);
        ItemValueRule plainBreadB = new ItemValueRule(new ItemStack(Items.BREAD), 99);
        boolean duplicatePlainFallbacksDetected = ItemValueRuleConflicts.isDuplicate(plainBreadA, plainBreadB);

        ItemStack waterStack = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        ItemStack healingStack = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
        ItemValueRule waterRule = new ItemValueRule(waterStack, 8);
        ItemValueRule healingRule = new ItemValueRule(healingStack, 80);
        boolean waterVsHealingNotDuplicate = !ItemValueRuleConflicts.isDuplicate(waterRule, healingRule);
        boolean waterVsHealingNotAmbiguous = !ItemValueRuleConflicts.isAmbiguous(waterRule, healingRule);

        ItemStack customNameOnlyStack = new ItemStack(Items.POTION);
        customNameOnlyStack.set(DataComponents.CUSTOM_NAME, Component.literal("Foo"));
        ItemValueRule customNameOnlyRule = new ItemValueRule(customNameOnlyStack, 999);
        boolean genuineAmbiguityDetected = ItemValueRuleConflicts.isAmbiguous(healingRule, customNameOnlyRule);

        boolean pass = duplicatePlainFallbacksDetected && waterVsHealingNotDuplicate
                && waterVsHealingNotAmbiguous && genuineAmbiguityDetected;
        r.check(label, pass,
                "duplicatePlainFallbacksDetected=" + duplicatePlainFallbacksDetected
                        + ", waterVsHealingNotDuplicate=" + waterVsHealingNotDuplicate
                        + ", waterVsHealingNotAmbiguous=" + waterVsHealingNotAmbiguous
                        + ", genuineAmbiguityDetected=" + genuineAmbiguityDetected);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Registry-level ItemValueRegistry.validate checks — the actual grouping/rejection
    // algorithm, exercised on isolated, hand-built maps via the PURE validate() method, so no
    // real ERROR-level log lines are produced for this synthetic data.
    // ─────────────────────────────────────────────────────────────────────

    private static void checkRegistryValidation(VerificationReporter r) {
        checkThreeIdenticalComponentAwareRulesAllRejected(r);
        checkThreeIdenticalPlainFallbacksAllRejected(r);
        checkOneValidRuleSurvivesAmongMutualConflicts(r);
        checkDifferentSpecificityRulesCoexistAndMostSpecificWins(r);
        checkEqualSpecificityMutuallyExclusiveRulesBothKept(r);
        checkEqualSpecificityOverlappingRulesBothRejected(r);
    }

    private static void checkThreeIdenticalComponentAwareRulesAllRejected(VerificationReporter r) {
        String label = "Registry: three identical component-aware rules -> all three rejected";
        Map<Identifier, ItemValueRule> map = new LinkedHashMap<>();
        map.put(id("dup_component_a"), new ItemValueRule(PotionContents.createItemStack(Items.POTION, Potions.HEALING), 80));
        map.put(id("dup_component_b"), new ItemValueRule(PotionContents.createItemStack(Items.POTION, Potions.HEALING), 81));
        map.put(id("dup_component_c"), new ItemValueRule(PotionContents.createItemStack(Items.POTION, Potions.HEALING), 82));

        Map<Item, List<ItemValueRule>> accepted = ItemValueRegistry.validate(map).accepted();
        boolean pass = !accepted.containsKey(Items.POTION);
        r.check(label, pass, "expected no surviving Potion rules, got " + accepted.get(Items.POTION));
    }

    private static void checkThreeIdenticalPlainFallbacksAllRejected(VerificationReporter r) {
        String label = "Registry: three identical plain item-ID fallback rules -> all three rejected";
        Map<Identifier, ItemValueRule> map = new LinkedHashMap<>();
        map.put(id("dup_plain_a"), new ItemValueRule(new ItemStack(Items.STRING), 10));
        map.put(id("dup_plain_b"), new ItemValueRule(new ItemStack(Items.STRING), 15));
        map.put(id("dup_plain_c"), new ItemValueRule(new ItemStack(Items.STRING), 20));

        Map<Item, List<ItemValueRule>> accepted = ItemValueRegistry.validate(map).accepted();
        boolean pass = !accepted.containsKey(Items.STRING);
        r.check(label, pass, "expected no surviving String rules, got " + accepted.get(Items.STRING));
    }

    private static void checkOneValidRuleSurvivesAmongMutualConflicts(VerificationReporter r) {
        String label = "Registry: one valid rule plus three mutually conflicting rules -> only the valid rule survives";
        Map<Identifier, ItemValueRule> map = new LinkedHashMap<>();
        map.put(id("valid_healing"), new ItemValueRule(PotionContents.createItemStack(Items.POTION, Potions.HEALING), 80));
        map.put(id("conflict_poison_a"), new ItemValueRule(PotionContents.createItemStack(Items.POTION, Potions.POISON), 1));
        map.put(id("conflict_poison_b"), new ItemValueRule(PotionContents.createItemStack(Items.POTION, Potions.POISON), 2));
        map.put(id("conflict_poison_c"), new ItemValueRule(PotionContents.createItemStack(Items.POTION, Potions.POISON), 3));

        List<ItemValueRule> survivors = ItemValueRegistry.validate(map).accepted().get(Items.POTION);
        boolean pass = survivors != null && survivors.size() == 1 && survivors.get(0).baseValue() == 80L;
        r.check(label, pass, "expected exactly one surviving rule with base value 80, got " + survivors);
    }

    private static void checkDifferentSpecificityRulesCoexistAndMostSpecificWins(VerificationReporter r) {
        String label = "Registry: lower- and higher-specificity compatible rules both load, higher wins when both match";
        Map<Identifier, ItemValueRule> map = new LinkedHashMap<>();
        map.put(id("low_specificity"), new ItemValueRule(PotionContents.createItemStack(Items.POTION, Potions.HEALING), 80));

        ItemStack highSpecificityStack = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
        highSpecificityStack.set(DataComponents.CUSTOM_NAME, Component.literal("Foo"));
        map.put(id("high_specificity"), new ItemValueRule(highSpecificityStack, 999));

        List<ItemValueRule> survivors = ItemValueRegistry.validate(map).accepted().get(Items.POTION);
        boolean bothLoaded = survivors != null && survivors.size() == 2;

        boolean mostSpecificWins = false;
        if (bothLoaded) {
            ItemStack matchesBoth = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
            matchesBoth.set(DataComponents.CUSTOM_NAME, Component.literal("Foo"));
            Optional<ItemValueRule> winner = ItemValueRegistry.pickMostSpecificMatch(survivors, matchesBoth);
            mostSpecificWins = winner.isPresent() && winner.get().baseValue() == 999L;
        }

        r.check(label, bothLoaded && mostSpecificWins,
                "bothLoaded=" + bothLoaded + " (survivors=" + survivors + "), mostSpecificWins=" + mostSpecificWins);
    }

    private static void checkEqualSpecificityMutuallyExclusiveRulesBothKept(VerificationReporter r) {
        String label = "Registry: equal-specificity mutually exclusive rules (Water vs. Healing) both load";
        Map<Identifier, ItemValueRule> map = new LinkedHashMap<>();
        map.put(id("water"), new ItemValueRule(PotionContents.createItemStack(Items.POTION, Potions.WATER), 8));
        map.put(id("healing"), new ItemValueRule(PotionContents.createItemStack(Items.POTION, Potions.HEALING), 80));

        List<ItemValueRule> survivors = ItemValueRegistry.validate(map).accepted().get(Items.POTION);
        r.check(label, survivors != null && survivors.size() == 2, "expected both rules to survive, got " + survivors);
    }

    private static void checkEqualSpecificityOverlappingRulesBothRejected(VerificationReporter r) {
        String label = "Registry: equal-specificity overlapping rules that could both match one stack -> both rejected";
        Map<Identifier, ItemValueRule> map = new LinkedHashMap<>();
        map.put(id("by_potion_contents"), new ItemValueRule(PotionContents.createItemStack(Items.POTION, Potions.HEALING), 80));

        ItemStack customNameOnlyStack = new ItemStack(Items.POTION);
        customNameOnlyStack.set(DataComponents.CUSTOM_NAME, Component.literal("Foo"));
        map.put(id("by_custom_name"), new ItemValueRule(customNameOnlyStack, 999));

        Map<Item, List<ItemValueRule>> accepted = ItemValueRegistry.validate(map).accepted();
        r.check(label, !accepted.containsKey(Items.POTION), "expected no surviving Potion rules, got " + accepted.get(Items.POTION));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", "selftest/" + path);
    }

    // ─────────────────────────────────────────────────────────────────────
    // PricingContext authored-override validation
    // ─────────────────────────────────────────────────────────────────────

    private static void checkAuthoredOverride(VerificationReporter r, ServerPlayer player) {
        checkNegativeOverrideRejected(r, player);
        checkZeroAndPositiveOverrideAccepted(r, player);
    }

    private static void checkNegativeOverrideRejected(VerificationReporter r, ServerPlayer player) {
        String label = "Negative authoredOverride is rejected at PricingContext construction";
        boolean rejected = throwsIllegalArgument(() ->
                new PricingContext(player, Optional.empty(), PricingDirection.RETAIL, Optional.of(-1L)));
        r.check(label, rejected, "expected IllegalArgumentException, none thrown");
    }

    private static void checkZeroAndPositiveOverrideAccepted(VerificationReporter r, ServerPlayer player) {
        String label = "Zero override is accepted; a positive override becomes the exact unit price";

        PricingContext zeroOverride = new PricingContext(player, Optional.empty(), PricingDirection.RETAIL, Optional.of(0L));
        Optional<PriceQuote> zeroQuote = ItemPricingService.quoteRetail(new ItemStack(Items.DIAMOND), 5, zeroOverride);
        boolean zeroOk = zeroQuote.isPresent() && zeroQuote.get().unitPrice() == 0L && zeroQuote.get().total() == 0L;

        PricingContext positiveOverride = new PricingContext(player, Optional.empty(), PricingDirection.RETAIL, Optional.of(500L));
        Optional<PriceQuote> positiveQuote = ItemPricingService.quoteRetail(new ItemStack(Items.DIAMOND), 3, positiveOverride);
        boolean positiveOk = positiveQuote.isPresent() && positiveQuote.get().unitPrice() == 500L && positiveQuote.get().total() == 1500L;

        r.check(label, zeroOk && positiveOk, "zeroQuote=" + zeroQuote + ", positiveQuote=" + positiveQuote);
    }

    // ─────────────────────────────────────────────────────────────────────

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }
}
