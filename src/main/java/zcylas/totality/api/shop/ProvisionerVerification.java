package zcylas.totality.api.shop;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.ServerScheduler;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.dialogue.DialogueRegistry;
import zcylas.totality.api.dialogue.DialogueState;
import zcylas.totality.api.dialogue.DialogueTemplate;
import zcylas.totality.api.shop.assortment.AssortmentItemEntry;
import zcylas.totality.api.shop.assortment.AssortmentSelectionGroup;
import zcylas.totality.api.shop.assortment.ChanceAssortmentEntry;
import zcylas.totality.api.shop.assortment.ProvisionerAssortmentPool;
import zcylas.totality.api.shop.assortment.ProvisionerAssortmentRegistry;
import zcylas.totality.api.shop.assortment.WeightedAssortmentEntry;
import zcylas.totality.entity.npc.ProvisionerNpcEntity;
import zcylas.totality.init.ModEntities;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Dev-environment-gated self-test for Phase 3 (Provisioner assortment/stock/entity/transaction
 * integration), same {@link VerificationReporter}-based convention as {@code ItemValueVerification}
 * and {@link MerchantSellVerification}.
 *
 * <p>Assortment-pool checks run against a hand-built, isolated {@link #testPool()} fixture
 * (mirroring the REAL {@code generic_provisioner.json} data exactly) rather than the datapack-
 * loaded registry, so seed-driven probabilistic assertions never depend on production data
 * staying byte-for-byte identical to this suite's expectations — except {@link
 * #checkRealPoolLoadsAndValidates}, which deliberately DOES exercise the real, datapack-loaded
 * {@link ProvisionerAssortmentRegistry}, the same way {@code MerchantSellVerification} exercises
 * real {@code item_values}/{@code provisioner_buys} data.
 *
 * <p>Entity-based checks use isolated {@link ProvisionerNpcEntity} instances, most never added to
 * a level (pure state checks) — a few ARE added to the level and interact with the real {@link
 * TradeSessionManager} for transaction-integration checks, following {@code
 * MerchantSellVerification}'s existing entity-backed-session precedent exactly (spawn, use,
 * discard in {@code finally}).
 */
public final class ProvisionerVerification {

    private ProvisionerVerification() {}

    /** Ticks to wait before the delayed smoke test resolves its spawned Provisioner (Phase 3
     *  hardening pass, Section 9) — long enough that {@code Level#getEntity(int)} has genuinely
     *  processed the entity through normal tick-driven bookkeeping, unlike the rest of this suite
     *  which runs at {@code SERVER_STARTED}, before a single tick has occurred. ~2 seconds is far
     *  more than needed in practice; picked generously rather than tightly. */
    private static final int SMOKE_TEST_DELAY_TICKS = 40;

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(ProvisionerVerification::runSelfTestIfDev);
        ServerLifecycleEvents.SERVER_STARTED.register(ProvisionerVerification::scheduleDelayedEntityBackedSmokeTest);
    }

    static void runSelfTestIfDev(MinecraftServer server) {
        if (!VerificationReporter.isDevEnvironment()) return;

        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "ProvisionerVerification");
        ServerPlayer player = TotalityFakePlayer.create(server.overworld(), "[ProvisionerVerification]");

        // ── Phase 3 hardening pass: default configuration ───────────────────
        checkNewProvisionerDefaultsDialogueIdWhenAbsent(r, server);
        checkNewProvisionerHasNonNullMerchantProfileId(r, server);
        checkNewProvisionerDefaultsAssortmentIdWhenAbsent(r, server);
        checkExplicitCustomIdsNotOverwritten(r, server);
        checkDefaultDialogueIdSurvivesPersistenceRoundTrip(r, server);
        checkProvisionerDialogueHasValidTradeOption(r);

        // ── Phase 3 hardening pass: merchant Credits hardening ──────────────
        checkNegativePersistedCreditsSanitizedToZero(r, server);
        checkSetCurrentCreditsZeroMarksInitialized(r, server);
        checkInvalidNegativeMerchantStateCannotBuy(r, player, server);
        checkTemplateBackedBuyAlsoRejectsInvalidMerchantState(r, player);

        // ── Phase 3 hardening pass: assortment weight overflow / roll bound ─
        checkWeightSumExceedingIntMaxRejected(r);
        checkExcessiveGroupRollCountRejected(r);

        // ── Phase 3 hardening pass: assortment immutability ─────────────────
        checkMutatingReturnedItemStackDoesNotAffectFutureRolls(r);
        checkMutatingOriginalConstructorListDoesNotAffectPool(r);
        checkPoolInternalListsCannotBeMutatedByConsumers(r);

        // ── Phase 3 hardening pass: explicit BUY quantity validation ────────
        checkInvalidStockBuyQuantitiesRejectedWithoutMutation(r, player, server);
        checkInvalidTemplateBuyQuantitiesRejectedWithoutMutation(r, player, server);

        // ── Phase 3 hardening pass: all-or-nothing persisted stock recovery ─
        checkPersistedStockEmptyItemEntryInvalid(r);
        checkPersistedStockDuplicateEntryInvalid(r);
        checkPersistedStockMixedValidInvalidRejectedEntirely(r);
        checkCorruptDuplicateStockDiscardedButStaysInitialized(r, server);

        // ── Phase 3 hardening pass: missing-pool retry throttling ───────────
        checkMissingPoolRetryThrottledThenEventuallySucceeds(r, server);

        // ── Assortment validation and generation ────────────────────────────
        checkRealPoolLoadsAndValidates(r);
        checkWaterBottleRetainsComponent(r);
        checkHealingPotionRetainsComponent(r);
        checkGuaranteedEntriesAlwaysAppear(r);
        checkExactlyFourDistinctCommonEntries(r);
        checkExactlyOneCookedFoodEntry(r);
        checkExactlyOneToolEntry(r);
        checkToolTiersReachBothBranchesWithCorrectStock(r);
        checkRareHealingPotionCanBeAbsentOrPresent(r);
        checkNoAccidentalDuplicatesAcrossSeeds(r);
        checkInvalidStockRejected(r);
        checkInvalidChanceRejected(r);
        checkInvalidDistinctRollCountRejected(r);
        checkNonPositiveWeightRejected(r);
        checkDuplicateTemplateAcrossPoolRejected(r);

        // ── Runtime stock (MerchantStockProvider contract) ──────────────────
        checkStockDecrementRemovesExactQuantity(r, server);
        checkPartialPurchaseLeavesCorrectStock(r, server);
        checkExactFinalPurchaseLeavesZeroStock(r, server);
        checkZeroStockEntryStaysInPosition(r, server);
        checkBuyingBeyondStockRejectedWithoutMutation(r, server);
        checkNonPositiveQuantityRejected(r, server);
        checkExposedItemStacksCannotMutateInternalTemplate(r, server);
        checkComponentDistinctItemsRemainDistinct(r, server);

        // ── Persistence ───────────────────────────────────────────────────
        checkStockRoundTripPreservesOrder(r, server);
        checkStockRoundTripPreservesWaterBottleComponents(r, server);
        checkStockRoundTripPreservesHealingPotionComponents(r, server);
        checkZeroStockSurvivesRoundTrip(r, server);
        checkZeroCreditsSurviveRoundTrip(r, server);
        checkAboveBaselineCreditsSurviveRoundTrip(r, server);
        checkInitializationStateSurvivesRoundTrip(r, server);
        checkReloadedInitializedStockDoesNotReroll(r, server);
        checkSoldOutMerchantDoesNotReroll(r, server);
        checkMissingPoolDoesNotMarkStockInitialized(r, server);

        // ── Per-entity independence ──────────────────────────────────────
        checkTwoProvisionersIndependentCredits(r, server);
        checkTwoProvisionersIndependentStock(r, server);
        checkBuyingFromOneDoesNotChangeOther(r, server);
        checkSameAssortmentIdDoesNotShareRuntimeState(r, server);

        // ── Correction pass — persistence / no natural despawn (Part B) ──────
        checkProvisionerIsPersistenceRequired(r, server);
        checkProvisionerSurvivesCheckDespawnFarFromPlayer(r, server);
        checkGenericNpcDespawnsFarFromPlayerControl(r, server);
        checkFullSaveLoadRoundTripPreservesCompleteMerchantState(r, server);

        // ── Transaction integration ──────────────────────────────────────
        checkEntityBackedSessionResolvesEntityRuntime(r, player, server);
        checkStockBuyDecreasesOnlyThatEntitysStock(r, player, server);
        checkStockBuyIncreasesOnlyThatEntitysCredits(r, player, server);
        checkOutOfStockBuyChangesNothing(r, player, server);
        checkEntityBackedSellDecreasesCreditsAndSkipsStock(r, player, server);
        checkStaleIndexRejected(r, player, server);
        checkShopEntryDisplayDataSoldOutRepresentation(r);
        checkExistingNonEntitySessionStillWorks(r, player);

        r.summarize();
    }

    // ═════════════════════════════════════════════════════════════════════
    // Phase 3 hardening pass — default configuration (Section 1)
    // ═════════════════════════════════════════════════════════════════════

    private static void checkNewProvisionerDefaultsDialogueIdWhenAbsent(VerificationReporter r, MinecraftServer server) {
        safe(r, "A fresh Provisioner defaults dialogueId when NBT genuinely has no DialogueId key "
                + "(the real /summon-with-no-NBT bug: readAdditionalSaveData previously nulled the constructor's default)", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            npc.simulateFullLoadForTest(emptyInput(server));
            Identifier expected = Identifier.fromNamespaceAndPath("totality", "provisioner_greeting");
            boolean pass = expected.equals(npc.getDialogueId());
            return result(pass, "dialogueId=" + npc.getDialogueId());
        });
    }

    private static void checkNewProvisionerHasNonNullMerchantProfileId(VerificationReporter r, MinecraftServer server) {
        safe(r, "A fresh Provisioner always has a well-formed merchant/profile id (merchantId(), UUID-derived, never absent — "
                + "Provisioner's entity-backed trade never needs a separate ShopRegistry shop_id at all)", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            boolean pass = npc.merchantId() != null && npc.merchantId().toString().contains(npc.getUUID().toString());
            return result(pass, "merchantId=" + npc.merchantId() + ", uuid=" + npc.getUUID());
        });
    }

    private static void checkNewProvisionerDefaultsAssortmentIdWhenAbsent(VerificationReporter r, MinecraftServer server) {
        safe(r, "A fresh Provisioner defaults AssortmentPoolId to generic_provisioner when NBT has no such key", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            npc.simulateFullLoadForTest(emptyInput(server));
            boolean pass = ProvisionerNpcEntity.DEFAULT_ASSORTMENT_POOL_ID.equals(npc.getAssortmentPoolId());
            return result(pass, "assortmentPoolId=" + npc.getAssortmentPoolId());
        });
    }

    private static void checkExplicitCustomIdsNotOverwritten(VerificationReporter r, MinecraftServer server) {
        safe(r, "Explicitly authored dialogueId/AssortmentPoolId are never overwritten by defaults", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            Identifier customDialogue = Identifier.fromNamespaceAndPath("totality", "example_trader");
            Identifier customPool = Identifier.fromNamespaceAndPath("totality", "selftest/custom_pool");

            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
            output.putString("DialogueId", customDialogue.toString());
            output.putString("AssortmentPoolId", customPool.toString());
            ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), output.buildResult());
            npc.simulateFullLoadForTest(input);

            boolean pass = customDialogue.equals(npc.getDialogueId()) && customPool.equals(npc.getAssortmentPoolId());
            return result(pass, "dialogueId=" + npc.getDialogueId() + ", assortmentPoolId=" + npc.getAssortmentPoolId());
        });
    }

    private static void checkDefaultDialogueIdSurvivesPersistenceRoundTrip(VerificationReporter r, MinecraftServer server) {
        safe(r, "A defaulted dialogueId, once established, survives a real save/load round-trip", () -> {
            ProvisionerNpcEntity original = isolatedProvisioner(server);
            original.simulateFullLoadForTest(emptyInput(server)); // establishes the default
            Identifier expected = Identifier.fromNamespaceAndPath("totality", "provisioner_greeting");

            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
            original.simulateFullSaveForTest(output);
            CompoundTag tag = output.buildResult();

            ProvisionerNpcEntity reloaded = isolatedProvisioner(server);
            reloaded.simulateFullLoadForTest(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), tag));

            boolean pass = expected.equals(reloaded.getDialogueId());
            return result(pass, "reloadedDialogueId=" + reloaded.getDialogueId());
        });
    }

    private static void checkProvisionerDialogueHasValidTradeOption(VerificationReporter r) {
        safe(r, "provisioner_greeting dialogue's start state has a Trade choice using open_provisioner_shop", () -> {
            Identifier dialogueId = Identifier.fromNamespaceAndPath("totality", "provisioner_greeting");
            DialogueTemplate template = DialogueRegistry.INSTANCE.get(dialogueId);
            if (template == null) return result(false, "dialogue not found: " + dialogueId);
            DialogueState start = template.startState();
            if (start == null) return result(false, "no start state for " + dialogueId);
            boolean hasTradeOption = start.choices().stream()
                    .anyMatch(c -> c.action().isPresent() && "open_provisioner_shop".equals(c.action().get().type()));
            return result(hasTradeOption, "choices=" + start.choices().size());
        });
    }

    /** A genuinely empty {@link ValueInput} — no keys at all — exactly what {@code Entity#load}
     *  sees for a plain {@code /summon} with no explicit NBT. */
    private static ValueInput emptyInput(MinecraftServer server) {
        return TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), new CompoundTag());
    }

    // ═════════════════════════════════════════════════════════════════════
    // Phase 3 hardening pass — merchant Credits hardening (Section 2)
    // ═════════════════════════════════════════════════════════════════════

    private static void checkNegativePersistedCreditsSanitizedToZero(VerificationReporter r, MinecraftServer server) {
        safe(r, "Negative persisted CurrentCredits sanitizes to 0 without restoring the 300 baseline", () -> {
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
            output.putLong("CurrentCredits", -50L);
            output.putBoolean("CreditsInitialized", true);
            output.putBoolean("StockInitialized", true);
            output.store("Stock", MerchantStockEntry.CODEC.listOf(), List.of());

            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            npc.readPhase3State(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), output.buildResult()));

            boolean pass = npc.currentCredits() == 0L && npc.isCreditsInitialized();
            return result(pass, "credits=" + npc.currentCredits() + ", initialized=" + npc.isCreditsInitialized());
        });
    }

    private static void checkSetCurrentCreditsZeroMarksInitialized(VerificationReporter r, MinecraftServer server) {
        safe(r, "setCurrentCredits(0) marks Credits initialized and blocks the later 300 baseline", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            npc.setCurrentCredits(0L);
            boolean initializedImmediately = npc.isCreditsInitialized();

            npc.finalizeSpawn(server.overworld(), server.overworld().getCurrentDifficultyAt(npc.blockPosition()),
                    EntitySpawnReason.COMMAND, null);
            boolean pass = initializedImmediately && npc.currentCredits() == 0L;
            return result(pass, "initializedImmediately=" + initializedImmediately + ", creditsAfterFinalizeSpawn=" + npc.currentCredits());
        });
    }

    private static void checkInvalidNegativeMerchantStateCannotBuy(VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "A stock-backed BUY against a merchant with corrupt negative Credits is rejected with "
                + "INVALID_MERCHANT_STATE, changing nothing", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            npc.setCreditsForTest(-10L); // simulates corrupt state directly — test-only, bypasses normal validation
            npc.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 16)));
            long walletBefore = wallet(player);
            try {
                giveWallet(player, 10_000);
                long afterGive = wallet(player);
                TradeSessionManager.startStockBackedTradeForVerification(player, npc, npc);

                BuyResult buyResult = TradeSessionManager.handleBuy(player, 0, 1);

                boolean pass = !buyResult.success() && buyResult.reason() == BuyResult.Reason.INVALID_MERCHANT_STATE
                        && wallet(player) == afterGive && npc.currentCredits() == -10L
                        && npc.stockEntries().get(0).currentStock() == 16;
                return result(pass, "buyResult=" + buyResult + ", credits=" + npc.currentCredits() + ", stock=" + npc.stockEntries());
            } finally {
                TradeSessionManager.endTrade(player);
                setWallet(player, walletBefore);
            }
        });
    }

    private static void checkTemplateBackedBuyAlsoRejectsInvalidMerchantState(VerificationReporter r, ServerPlayer player) {
        safe(r, "A template-backed BUY against a merchant with corrupt negative Credits is also rejected with "
                + "INVALID_MERCHANT_STATE, changing nothing", () -> {
            MerchantRuntime corrupt = new MerchantRuntime() {
                @Override public Identifier merchantId() {
                    return Identifier.fromNamespaceAndPath("totality", "selftest/corrupt_merchant");
                }
                @Override public long currentCredits() { return -5L; }
                @Override public void setCurrentCredits(long value) { throw new UnsupportedOperationException("not used by this check"); }
                @Override public Set<net.minecraft.tags.TagKey<Item>> acceptedTags() { return Set.of(); }
            };
            ShopTemplate shop = new ShopTemplate("Verification Corrupt Shop",
                    List.of(new ShopEntry(new ItemStack(Items.IRON_SWORD), 100)));
            long walletBefore = wallet(player);
            try {
                TradeSessionManager.startTradeForVerification(
                        player, Identifier.fromNamespaceAndPath("totality", "selftest/corrupt_merchant_shop"), shop, corrupt);
                giveWallet(player, 10_000);
                long afterGive = wallet(player);

                BuyResult buyResult = TradeSessionManager.handleBuy(player, 0, 1);

                boolean pass = !buyResult.success() && buyResult.reason() == BuyResult.Reason.INVALID_MERCHANT_STATE
                        && wallet(player) == afterGive && corrupt.currentCredits() == -5L;
                return result(pass, "buyResult=" + buyResult);
            } finally {
                TradeSessionManager.endTrade(player);
                setWallet(player, walletBefore);
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // Phase 3 hardening pass — assortment weight overflow / roll bound (Section 4)
    // ═════════════════════════════════════════════════════════════════════

    private static void checkWeightSumExceedingIntMaxRejected(VerificationReporter r) {
        safe(r, "A selection group whose total weight exceeds Integer.MAX_VALUE is rejected by validate()", () -> {
            ProvisionerAssortmentPool pool = new ProvisionerAssortmentPool(List.of(), List.of(
                    new AssortmentSelectionGroup(1, true, List.of(
                            new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.TORCH), 1), Integer.MAX_VALUE),
                            new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.BREAD), 1), Integer.MAX_VALUE)
                    ))
            ), List.of());
            boolean pass = !pool.validate().valid();
            return result(pass, "errors=" + pool.validate().errors());
        });
    }

    private static void checkExcessiveGroupRollCountRejected(VerificationReporter r) {
        safe(r, "An excessive group roll count is rejected as a datapack safety bound, not gameplay balance", () -> {
            // distinct=false so a huge rolls count could otherwise loop unboundedly generating an
            // enormous list — the exact malformed-data shape the bound guards against.
            ProvisionerAssortmentPool pool = new ProvisionerAssortmentPool(List.of(), List.of(
                    new AssortmentSelectionGroup(1_000_000, false, List.of(
                            new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.TORCH), 1), 1)
                    ))
            ), List.of());
            boolean pass = !pool.validate().valid();
            return result(pass, "errors=" + pool.validate().errors());
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // Phase 3 hardening pass — assortment immutability (Section 5)
    // ═════════════════════════════════════════════════════════════════════

    private static void checkMutatingReturnedItemStackDoesNotAffectFutureRolls(VerificationReporter r) {
        safe(r, "Mutating an ItemStack returned from AssortmentItemEntry.item() does not affect later rolls", () -> {
            AssortmentItemEntry entry = new AssortmentItemEntry(new ItemStack(Items.TORCH), 16);
            ItemStack exposed = entry.item();
            exposed.setCount(99);
            ItemStack again = entry.item();
            boolean pass = again.getCount() == 1 && again.getItem() == Items.TORCH;
            return result(pass, "exposedCount=" + exposed.getCount() + ", againCount=" + again.getCount());
        });
    }

    private static void checkMutatingOriginalConstructorListDoesNotAffectPool(VerificationReporter r) {
        safe(r, "Clearing/changing the original constructor list does not affect an already-built pool", () -> {
            List<AssortmentItemEntry> mutableGuaranteed = new ArrayList<>();
            mutableGuaranteed.add(new AssortmentItemEntry(new ItemStack(Items.TORCH), 16));
            ProvisionerAssortmentPool pool = new ProvisionerAssortmentPool(mutableGuaranteed, List.of(), List.of());

            mutableGuaranteed.clear();
            mutableGuaranteed.add(new AssortmentItemEntry(new ItemStack(Items.BREAD), 6));

            boolean pass = pool.guaranteed().size() == 1 && pool.guaranteed().get(0).item().is(Items.TORCH);
            return result(pass, "poolGuaranteed=" + pool.guaranteed());
        });
    }

    private static void checkPoolInternalListsCannotBeMutatedByConsumers(VerificationReporter r) {
        safe(r, "A pool's exposed lists (guaranteed/selection_groups/entries) reject consumer mutation", () -> {
            AssortmentSelectionGroup group = new AssortmentSelectionGroup(1, true, List.of(
                    new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.TORCH), 1), 1)));
            ProvisionerAssortmentPool pool = new ProvisionerAssortmentPool(
                    List.of(new AssortmentItemEntry(new ItemStack(Items.TORCH), 16)), List.of(group), List.of());

            boolean guaranteedRejects = throwsUnsupported(() -> pool.guaranteed().add(new AssortmentItemEntry(new ItemStack(Items.BREAD), 1)));
            boolean groupsRejects = throwsUnsupported(() -> pool.selectionGroups().add(group));
            boolean entriesRejects = throwsUnsupported(() -> group.entries().add(group.entries().get(0)));

            boolean pass = guaranteedRejects && groupsRejects && entriesRejects;
            return result(pass, "guaranteedRejects=" + guaranteedRejects + ", groupsRejects=" + groupsRejects + ", entriesRejects=" + entriesRejects);
        });
    }

    private static boolean throwsUnsupported(Runnable action) {
        try {
            action.run();
            return false;
        } catch (UnsupportedOperationException e) {
            return true;
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // Phase 3 hardening pass — explicit BUY quantity validation (Section 6)
    // ═════════════════════════════════════════════════════════════════════

    private static void checkInvalidStockBuyQuantitiesRejectedWithoutMutation(
            VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "Stock-backed BUY rejects quantity 0, negative, 101, and Integer.MAX_VALUE with INVALID_QUANTITY, changing nothing", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            npc.setCreditsForTest(300);
            npc.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 16)));
            long walletBefore = wallet(player);
            try {
                giveWallet(player, 10_000);
                long afterGive = wallet(player);
                TradeSessionManager.startStockBackedTradeForVerification(player, npc, npc);

                int[] invalidQuantities = { 0, -1, 101, Integer.MAX_VALUE };
                StringBuilder detail = new StringBuilder();
                boolean allRejected = true;
                for (int q : invalidQuantities) {
                    BuyResult result = TradeSessionManager.handleBuy(player, 0, q);
                    boolean rejected = !result.success() && result.reason() == BuyResult.Reason.INVALID_QUANTITY;
                    allRejected &= rejected;
                    detail.append("q=").append(q).append(" -> ").append(result).append("; ");
                }
                boolean noMutation = wallet(player) == afterGive && npc.currentCredits() == 300
                        && npc.stockEntries().get(0).currentStock() == 16;
                boolean pass = allRejected && noMutation;
                return result(pass, detail + "noMutation=" + noMutation);
            } finally {
                TradeSessionManager.endTrade(player);
                setWallet(player, walletBefore);
            }
        });
    }

    private static void checkInvalidTemplateBuyQuantitiesRejectedWithoutMutation(
            VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "Template-backed BUY rejects quantity 0, negative, 101, and Integer.MAX_VALUE with INVALID_QUANTITY, changing nothing", () -> {
            ShopTemplate shop = new ShopTemplate("Verification Quantity Shop",
                    List.of(new ShopEntry(new ItemStack(Items.IRON_SWORD), 450)));
            MerchantRuntime merchant = new InMemoryMerchantRuntime(
                    Identifier.fromNamespaceAndPath("totality", "selftest/quantity_merchant"),
                    300, Set.of(zcylas.totality.init.ModTags.PROVISIONER_BUYS));
            long walletBefore = wallet(player);
            try {
                TradeSessionManager.startTradeForVerification(
                        player, Identifier.fromNamespaceAndPath("totality", "selftest/quantity_shop"), shop, merchant);
                giveWallet(player, 100_000);
                long afterGive = wallet(player);

                int[] invalidQuantities = { 0, -1, 101, Integer.MAX_VALUE };
                StringBuilder detail = new StringBuilder();
                boolean allRejected = true;
                for (int q : invalidQuantities) {
                    BuyResult result = TradeSessionManager.handleBuy(player, 0, q);
                    boolean rejected = !result.success() && result.reason() == BuyResult.Reason.INVALID_QUANTITY;
                    allRejected &= rejected;
                    detail.append("q=").append(q).append(" -> ").append(result).append("; ");
                }
                boolean noMutation = wallet(player) == afterGive && merchant.currentCredits() == 300;
                boolean pass = allRejected && noMutation;
                return result(pass, detail + "noMutation=" + noMutation);
            } finally {
                TradeSessionManager.endTrade(player);
                setWallet(player, walletBefore);
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // Phase 3 hardening pass — all-or-nothing persisted stock recovery (Section 7)
    // ═════════════════════════════════════════════════════════════════════

    private static void checkPersistedStockEmptyItemEntryInvalid(VerificationReporter r) {
        safe(r, "isValidPersistedStock rejects a list containing an empty item template", () -> {
            List<MerchantStockEntry> withEmpty = List.of(new MerchantStockEntry(ItemStack.EMPTY, 5));
            boolean pass = !ProvisionerNpcEntity.isValidPersistedStock(withEmpty);
            return result(pass, "list=" + withEmpty);
        });
    }

    private static void checkPersistedStockDuplicateEntryInvalid(VerificationReporter r) {
        safe(r, "isValidPersistedStock rejects duplicate item+component entries, but accepts distinct ones", () -> {
            List<MerchantStockEntry> duplicated = List.of(
                    new MerchantStockEntry(new ItemStack(Items.TORCH), 16),
                    new MerchantStockEntry(new ItemStack(Items.TORCH), 4));
            List<MerchantStockEntry> distinct = List.of(
                    new MerchantStockEntry(new ItemStack(Items.TORCH), 16),
                    new MerchantStockEntry(new ItemStack(Items.BREAD), 6));
            boolean pass = !ProvisionerNpcEntity.isValidPersistedStock(duplicated)
                    && ProvisionerNpcEntity.isValidPersistedStock(distinct);
            return result(pass, "duplicated=" + duplicated + ", distinct=" + distinct);
        });
    }

    private static void checkPersistedStockMixedValidInvalidRejectedEntirely(VerificationReporter r) {
        safe(r, "A list mixing valid and invalid entries is rejected in its entirety, not partially recovered", () -> {
            List<MerchantStockEntry> mixed = List.of(
                    new MerchantStockEntry(new ItemStack(Items.BREAD), 6),
                    new MerchantStockEntry(ItemStack.EMPTY, 5));
            boolean pass = !ProvisionerNpcEntity.isValidPersistedStock(mixed);
            return result(pass, "mixed=" + mixed);
        });
    }

    private static void checkCorruptDuplicateStockDiscardedButStaysInitialized(VerificationReporter r, MinecraftServer server) {
        safe(r, "Corrupt (duplicate) persisted stock is discarded entirely on load, but an initialized "
                + "merchant stays initialized (degrades to sold-out, never rerolls)", () -> {
            ProvisionerNpcEntity original = isolatedProvisioner(server);
            original.setCreditsForTest(300);
            // setStockForTest bypasses normal roll-time duplicate validation (test-only), letting
            // us fabricate a corrupt-but-codec-encodable persisted state — two ordinary, distinct
            // ItemStack.CODEC-safe items with an accidental duplicate, unlike an empty item stack,
            // which a real codec round-trip could never produce in the first place.
            original.setStockForTest(List.of(
                    new MerchantStockEntry(new ItemStack(Items.TORCH), 16),
                    new MerchantStockEntry(new ItemStack(Items.TORCH), 4)
            ));
            ProvisionerNpcEntity reloaded = roundTrip(server, original);
            boolean pass = reloaded.isStockInitialized() && reloaded.stockEntries().isEmpty();
            return result(pass, "stockInitialized=" + reloaded.isStockInitialized() + ", stock=" + reloaded.stockEntries());
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // Phase 3 hardening pass — missing-pool retry throttling (Section 3)
    // ═════════════════════════════════════════════════════════════════════

    private static void checkMissingPoolRetryThrottledThenEventuallySucceeds(VerificationReporter r, MinecraftServer server) {
        safe(r, "Missing-pool retry is throttled (does not retry every tick) and a later valid pool initializes exactly once", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            Identifier badId = Identifier.fromNamespaceAndPath("totality", "selftest/no_such_pool_throttle_check");
            npc.setAssortmentPoolId(badId);
            npc.finalizeSpawn(server.overworld(), server.overworld().getCurrentDifficultyAt(npc.blockPosition()),
                    EntitySpawnReason.COMMAND, null);
            boolean uninitializedAfterFirstAttempt = !npc.isStockInitialized() && npc.stockEntries().isEmpty();

            // Simulate several ticks while still throttled (far from the 200-tick retry interval)
            // — must NOT have re-attempted and succeeded yet, proving the retry isn't per-tick.
            for (int i = 0; i < 5; i++) npc.runAiStepForTest(server.overworld());
            boolean stillUninitializedWhileThrottled = !npc.isStockInitialized();

            // Now make the pool valid and advance through the remaining throttle window.
            npc.setAssortmentPoolId(ProvisionerNpcEntity.DEFAULT_ASSORTMENT_POOL_ID);
            for (int i = 0; i < 200; i++) npc.runAiStepForTest(server.overworld());
            boolean nowInitializedExactlyOnce = npc.isStockInitialized() && !npc.stockEntries().isEmpty();

            boolean pass = uninitializedAfterFirstAttempt && stillUninitializedWhileThrottled && nowInitializedExactlyOnce;
            return result(pass, "uninitializedAfterFirstAttempt=" + uninitializedAfterFirstAttempt
                    + ", stillUninitializedWhileThrottled=" + stillUninitializedWhileThrottled
                    + ", nowInitialized=" + nowInitializedExactlyOnce + ", stock=" + npc.stockEntries());
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // Delayed real entity-backed transaction smoke test (Phase 3 hardening pass, Section 9)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Every other check in this suite runs at {@code SERVER_STARTED}, before the server has
     * processed a single tick — at which point a freshly {@code addFreshEntity}'d entity is
     * provably NOT yet visible to {@code Level#getEntity(int)} (documented at length elsewhere in
     * this class and in {@code MerchantSellVerification}). That is fine for pure transaction logic
     * (exercised instead via {@link TradeSessionManager#startStockBackedTradeForVerification}), but
     * it means nothing in the suite above actually proves the full, real, production path:
     * right-click -> Dialogue -> {@code open_provisioner_shop} -> {@link
     * TradeSessionManager#startEntityBackedTrade} -> resolve the LIVE NPC -> use THAT entity's
     * runtime/stock -> BUY/SELL. This schedules a genuine one-shot check, via the existing {@link
     * ServerScheduler}, {@value #SMOKE_TEST_DELAY_TICKS} ticks after server start — long enough
     * that a spawned entity is resolvable the normal way — which spawns a real Provisioner,
     * confirms it resolves through the same {@code Level#getEntity(int)} call {@code
     * DialogueSessionManager#getActiveNpc} uses in real play, and drives one real BUY and one real
     * SELL against it via the actual production {@link TradeSessionManager} entry points (not the
     * verification-only stock-backed shortcut). This does NOT simulate an actual mouse click or
     * dialogue UI — it starts the session the same way {@code OpenProvisionerShopAction} does,
     * which is the full extent of what can be verified without a real client input-injection tool
     * (this environment has none). The still-manual step is Stefan physically right-clicking the
     * NPC and watching Dialogue/Trading open on screen.
     */
    private static void scheduleDelayedEntityBackedSmokeTest(MinecraftServer server) {
        if (!VerificationReporter.isDevEnvironment()) return;
        ServerScheduler.getInstance().queue(
                ProvisionerVerification::runDelayedEntityBackedSmokeTest, SMOKE_TEST_DELAY_TICKS);
    }

    private static void runDelayedEntityBackedSmokeTest(MinecraftServer server) {
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "ProvisionerEntityBackedSmokeTest");
        ServerPlayer player = TotalityFakePlayer.create(server.overworld(), "[ProvisionerEntityBackedSmokeTest]");
        ProvisionerNpcEntity npc = null;
        int slot = 8;
        ItemStack slotBefore = player.getInventory().getItem(slot).copy();
        long walletBefore = wallet(player);
        try {
            npc = new ProvisionerNpcEntity(ModEntities.PROVISIONER, server.overworld());
            npc.setPos(player.getX(), player.getY(), player.getZ());
            server.overworld().addFreshEntity(npc);
            npc.setCreditsForTest(300);
            npc.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 16)));

            Entity resolved = server.overworld().getEntity(npc.getId());
            r.check("Freshly spawned Provisioner resolves via the real production entity lookup after real ticks",
                    resolved == npc, "resolved=" + resolved);

            TradeSessionManager.startEntityBackedTrade(player, npc);
            r.check("startEntityBackedTrade opens a real session against the live entity",
                    TradeSessionManager.isTrading(player), "isTrading=" + TradeSessionManager.isTrading(player));

            giveWallet(player, 10_000);
            BuyResult buyResult = TradeSessionManager.handleBuy(player, 0, 3);
            boolean buyOk = buyResult.success() && npc.currentCredits() == 300 + 12
                    && npc.stockEntries().get(0).currentStock() == 13;
            r.check("One real BUY against the live entity changes its Credits and stock", buyOk,
                    "buyResult=" + buyResult + ", credits=" + npc.currentCredits() + ", stock=" + npc.stockEntries());

            player.getInventory().setItem(slot, new ItemStack(Items.BREAD, 4));
            long creditsBeforeSell = npc.currentCredits();
            SellResult sellResult = TradeSessionManager.handleSell(player, slot, 4);
            boolean sellOk = sellResult.success() && npc.currentCredits() == creditsBeforeSell - 24
                    && npc.stockEntries().stream().noneMatch(e -> e.item().is(Items.BREAD));
            r.check("One real SELL against the live entity changes its Credits and never adds to ordinary stock",
                    sellOk, "sellResult=" + sellResult + ", credits=" + npc.currentCredits() + ", stock=" + npc.stockEntries());
        } catch (RuntimeException e) {
            r.check("Delayed entity-backed smoke test completed without throwing", false,
                    "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            TradeSessionManager.endTrade(player);
            player.getInventory().setItem(slot, slotBefore);
            setWallet(player, walletBefore);
            if (npc != null && !npc.isRemoved()) npc.discard();
        }
        r.summarize();
    }

    // ═════════════════════════════════════════════════════════════════════
    // Assortment validation and generation
    // ═════════════════════════════════════════════════════════════════════

    private static void checkRealPoolLoadsAndValidates(VerificationReporter r) {
        safe(r, "Real, datapack-loaded generic_provisioner pool exists and validates", () -> {
            Optional<ProvisionerAssortmentPool> pool =
                    ProvisionerAssortmentRegistry.INSTANCE.get(ProvisionerNpcEntity.DEFAULT_ASSORTMENT_POOL_ID);
            boolean pass = pool.isPresent() && pool.get().validate().valid();
            return result(pass, "pool present=" + pool.isPresent());
        });
    }

    private static void checkWaterBottleRetainsComponent(VerificationReporter r) {
        safe(r, "Guaranteed Water Bottle entry retains its potion_contents component", () -> {
            List<MerchantStockEntry> rolled = testPool().roll(RandomSource.create(1));
            ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            boolean pass = findMatching(rolled, water).isPresent();
            return result(pass, "rolled=" + rolled);
        });
    }

    private static void checkHealingPotionRetainsComponent(VerificationReporter r) {
        safe(r, "Rare Healing Potion entry (when rolled) retains its potion_contents component", () -> {
            ItemStack healing = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
            boolean sawPresent = false;
            for (long seed = 0; seed < 200 && !sawPresent; seed++) {
                List<MerchantStockEntry> rolled = testPool().roll(RandomSource.create(seed));
                if (findMatching(rolled, healing).isPresent()) sawPresent = true;
            }
            return result(sawPresent, "no seed in [0,200) produced a matching Healing Potion entry");
        });
    }

    private static void checkGuaranteedEntriesAlwaysAppear(VerificationReporter r) {
        safe(r, "Guaranteed Torch, Bread, and Water Bottle entries always appear", () -> {
            ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            for (long seed = 0; seed < 20; seed++) {
                List<MerchantStockEntry> rolled = testPool().roll(RandomSource.create(seed));
                boolean hasTorch = findMatching(rolled, new ItemStack(Items.TORCH)).isPresent();
                boolean hasBread = findMatching(rolled, new ItemStack(Items.BREAD)).isPresent();
                boolean hasWater = findMatching(rolled, water).isPresent();
                if (!(hasTorch && hasBread && hasWater)) {
                    return result(false, "seed=" + seed + ": torch=" + hasTorch + ", bread=" + hasBread + ", water=" + hasWater);
                }
            }
            return result(true, "all 20 seeds carried all three guaranteed entries");
        });
    }

    private static final Set<Item> COMMON_GROUP_ITEMS = Set.of(
            Items.OAK_PLANKS, Items.COAL, Items.STRING, Items.LEATHER, Items.GLASS_BOTTLE, Items.APPLE);

    private static void checkExactlyFourDistinctCommonEntries(VerificationReporter r) {
        safe(r, "Exactly four distinct common-material entries appear per roll", () -> {
            for (long seed = 0; seed < 20; seed++) {
                List<MerchantStockEntry> rolled = testPool().roll(RandomSource.create(seed));
                List<Item> matched = new ArrayList<>();
                for (MerchantStockEntry entry : rolled) {
                    Item item = entry.item().getItem();
                    if (COMMON_GROUP_ITEMS.contains(item)) matched.add(item);
                }
                boolean distinct = matched.size() == Set.copyOf(matched).size();
                if (matched.size() != 4 || !distinct) {
                    return result(false, "seed=" + seed + ": matched=" + matched);
                }
            }
            return result(true, "all 20 seeds produced exactly 4 distinct common entries");
        });
    }

    private static void checkExactlyOneCookedFoodEntry(VerificationReporter r) {
        safe(r, "Exactly one cooked-food entry appears per roll", () -> {
            for (long seed = 0; seed < 20; seed++) {
                List<MerchantStockEntry> rolled = testPool().roll(RandomSource.create(seed));
                long count = rolled.stream()
                        .filter(e -> e.item().is(Items.COOKED_BEEF) || e.item().is(Items.COOKED_PORKCHOP))
                        .count();
                if (count != 1) return result(false, "seed=" + seed + ": cooked-food count=" + count);
            }
            return result(true, "all 20 seeds produced exactly 1 cooked-food entry");
        });
    }

    private static final Set<Item> STONE_TOOLS =
            Set.of(Items.STONE_SHOVEL, Items.STONE_SWORD, Items.STONE_PICKAXE, Items.STONE_AXE);
    private static final Set<Item> IRON_TOOLS =
            Set.of(Items.IRON_SHOVEL, Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE);

    private static void checkExactlyOneToolEntry(VerificationReporter r) {
        safe(r, "Exactly one tool entry appears per roll", () -> {
            for (long seed = 0; seed < 20; seed++) {
                List<MerchantStockEntry> rolled = testPool().roll(RandomSource.create(seed));
                long count = rolled.stream()
                        .filter(e -> STONE_TOOLS.contains(e.item().getItem()) || IRON_TOOLS.contains(e.item().getItem()))
                        .count();
                if (count != 1) return result(false, "seed=" + seed + ": tool count=" + count);
            }
            return result(true, "all 20 seeds produced exactly 1 tool entry");
        });
    }

    private static void checkToolTiersReachBothBranchesWithCorrectStock(VerificationReporter r) {
        safe(r, "Tool tier reaches both Stone (stock 2) and Iron (stock 1) branches across seeds", () -> {
            boolean sawStone = false, sawIron = false;
            for (long seed = 0; seed < 300 && !(sawStone && sawIron); seed++) {
                List<MerchantStockEntry> rolled = testPool().roll(RandomSource.create(seed));
                for (MerchantStockEntry entry : rolled) {
                    Item item = entry.item().getItem();
                    if (STONE_TOOLS.contains(item)) {
                        if (entry.currentStock() != 2) return result(false, "seed=" + seed + ": Stone tool stock=" + entry.currentStock() + ", expected 2");
                        sawStone = true;
                    } else if (IRON_TOOLS.contains(item)) {
                        if (entry.currentStock() != 1) return result(false, "seed=" + seed + ": Iron tool stock=" + entry.currentStock() + ", expected 1");
                        sawIron = true;
                    }
                }
            }
            return result(sawStone && sawIron, "sawStone=" + sawStone + ", sawIron=" + sawIron + " across 300 seeds");
        });
    }

    private static void checkRareHealingPotionCanBeAbsentOrPresent(VerificationReporter r) {
        safe(r, "Rare Healing Potion entry can be both absent and present across seeds", () -> {
            ItemStack healing = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
            boolean sawAbsent = false, sawPresent = false;
            for (long seed = 0; seed < 200 && !(sawAbsent && sawPresent); seed++) {
                List<MerchantStockEntry> rolled = testPool().roll(RandomSource.create(seed));
                if (findMatching(rolled, healing).isPresent()) sawPresent = true;
                else sawAbsent = true;
            }
            return result(sawAbsent && sawPresent, "sawAbsent=" + sawAbsent + ", sawPresent=" + sawPresent + " across 200 seeds");
        });
    }

    private static void checkNoAccidentalDuplicatesAcrossSeeds(VerificationReporter r) {
        safe(r, "Generated entries contain no accidental duplicate item templates, across many seeds", () -> {
            for (long seed = 0; seed < 50; seed++) {
                List<MerchantStockEntry> rolled = testPool().roll(RandomSource.create(seed));
                for (int a = 0; a < rolled.size(); a++) {
                    for (int b = a + 1; b < rolled.size(); b++) {
                        if (ItemStack.isSameItemSameComponents(rolled.get(a).item(), rolled.get(b).item())) {
                            return result(false, "seed=" + seed + ": duplicate " + rolled.get(a).item() + " at indices " + a + "," + b);
                        }
                    }
                }
            }
            return result(true, "no duplicates across 50 seeds");
        });
    }

    private static void checkInvalidStockRejected(VerificationReporter r) {
        safe(r, "A non-positive stock entry is rejected by validate()", () -> {
            ProvisionerAssortmentPool pool = new ProvisionerAssortmentPool(
                    List.of(new AssortmentItemEntry(new ItemStack(Items.TORCH), 0)), List.of(), List.of());
            boolean pass = !pool.validate().valid();
            return result(pass, "errors=" + pool.validate().errors());
        });
    }

    private static void checkInvalidChanceRejected(VerificationReporter r) {
        safe(r, "An out-of-range chance is rejected by validate()", () -> {
            ProvisionerAssortmentPool tooHigh = new ProvisionerAssortmentPool(List.of(), List.of(),
                    List.of(new ChanceAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.TORCH), 1), 1.5)));
            ProvisionerAssortmentPool negative = new ProvisionerAssortmentPool(List.of(), List.of(),
                    List.of(new ChanceAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.TORCH), 1), -0.1)));
            ProvisionerAssortmentPool notFinite = new ProvisionerAssortmentPool(List.of(), List.of(),
                    List.of(new ChanceAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.TORCH), 1), Double.NaN)));
            boolean pass = !tooHigh.validate().valid() && !negative.validate().valid() && !notFinite.validate().valid();
            return result(pass, "tooHigh=" + tooHigh.validate().valid() + ", negative=" + negative.validate().valid()
                    + ", notFinite=" + notFinite.validate().valid());
        });
    }

    private static void checkInvalidDistinctRollCountRejected(VerificationReporter r) {
        safe(r, "A distinct group requesting more rolls than it has entries is rejected", () -> {
            ProvisionerAssortmentPool pool = new ProvisionerAssortmentPool(List.of(), List.of(
                    new AssortmentSelectionGroup(5, true, List.of(
                            new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.TORCH), 1), 1),
                            new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.BREAD), 1), 1)
                    ))
            ), List.of());
            boolean pass = !pool.validate().valid();
            return result(pass, "errors=" + pool.validate().errors());
        });
    }

    private static void checkNonPositiveWeightRejected(VerificationReporter r) {
        safe(r, "A non-positive weight is rejected by validate()", () -> {
            ProvisionerAssortmentPool pool = new ProvisionerAssortmentPool(List.of(), List.of(
                    new AssortmentSelectionGroup(1, true, List.of(
                            new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.TORCH), 1), 0)
                    ))
            ), List.of());
            boolean pass = !pool.validate().valid();
            return result(pass, "errors=" + pool.validate().errors());
        });
    }

    private static void checkDuplicateTemplateAcrossPoolRejected(VerificationReporter r) {
        safe(r, "A duplicate item template appearing twice across the pool is rejected", () -> {
            ProvisionerAssortmentPool pool = new ProvisionerAssortmentPool(
                    List.of(new AssortmentItemEntry(new ItemStack(Items.TORCH), 16)),
                    List.of(new AssortmentSelectionGroup(1, true, List.of(
                            new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.TORCH), 4), 1),
                            new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.BREAD), 4), 1)
                    ))),
                    List.of());
            boolean pass = !pool.validate().valid();
            return result(pass, "errors=" + pool.validate().errors());
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // Runtime stock (MerchantStockProvider contract, via an isolated entity)
    // ═════════════════════════════════════════════════════════════════════

    private static void checkStockDecrementRemovesExactQuantity(VerificationReporter r, MinecraftServer server) {
        safe(r, "decrementStock removes exactly the requested quantity", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            npc.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 16)));
            boolean decremented = npc.decrementStock(0, 6);
            boolean pass = decremented && npc.stockEntries().get(0).currentStock() == 10;
            return result(pass, "stock=" + npc.stockEntries());
        });
    }

    private static void checkPartialPurchaseLeavesCorrectStock(VerificationReporter r, MinecraftServer server) {
        safe(r, "Partial purchase leaves the correct remaining stock", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            npc.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.BREAD), 6)));
            npc.decrementStock(0, 4);
            boolean pass = npc.stockEntries().get(0).currentStock() == 2;
            return result(pass, "stock=" + npc.stockEntries());
        });
    }

    private static void checkExactFinalPurchaseLeavesZeroStock(VerificationReporter r, MinecraftServer server) {
        safe(r, "Buying the exact final quantity leaves zero stock", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            npc.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.IRON_SWORD), 1)));
            boolean decremented = npc.decrementStock(0, 1);
            boolean pass = decremented && npc.stockEntries().get(0).currentStock() == 0;
            return result(pass, "stock=" + npc.stockEntries());
        });
    }

    private static void checkZeroStockEntryStaysInPosition(VerificationReporter r, MinecraftServer server) {
        safe(r, "A zero-stock entry remains in its stable list position, never removed or reordered", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            npc.setStockForTest(List.of(
                    new MerchantStockEntry(new ItemStack(Items.TORCH), 1),
                    new MerchantStockEntry(new ItemStack(Items.BREAD), 6)
            ));
            npc.decrementStock(0, 1);
            List<MerchantStockEntry> after = npc.stockEntries();
            boolean pass = after.size() == 2 && after.get(0).item().is(Items.TORCH) && after.get(0).currentStock() == 0
                    && after.get(1).item().is(Items.BREAD) && after.get(1).currentStock() == 6;
            return result(pass, "stock=" + after);
        });
    }

    private static void checkBuyingBeyondStockRejectedWithoutMutation(VerificationReporter r, MinecraftServer server) {
        safe(r, "Buying beyond current stock is rejected without mutation", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            npc.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 3)));
            boolean canPurchase = npc.canPurchaseStock(0, 10);
            boolean decremented = npc.decrementStock(0, 10);
            boolean pass = !canPurchase && !decremented && npc.stockEntries().get(0).currentStock() == 3;
            return result(pass, "canPurchase=" + canPurchase + ", decremented=" + decremented + ", stock=" + npc.stockEntries());
        });
    }

    private static void checkNonPositiveQuantityRejected(VerificationReporter r, MinecraftServer server) {
        safe(r, "Zero or negative purchase quantity is rejected", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            npc.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 5)));
            boolean zeroRejected = !npc.canPurchaseStock(0, 0) && !npc.decrementStock(0, 0);
            boolean negativeRejected = !npc.canPurchaseStock(0, -1) && !npc.decrementStock(0, -1);
            boolean pass = zeroRejected && negativeRejected && npc.stockEntries().get(0).currentStock() == 5;
            return result(pass, "zeroRejected=" + zeroRejected + ", negativeRejected=" + negativeRejected);
        });
    }

    private static void checkExposedItemStacksCannotMutateInternalTemplate(VerificationReporter r, MinecraftServer server) {
        safe(r, "Exposed item stacks cannot mutate internal stock templates", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            npc.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 16)));
            ItemStack exposed = npc.stockEntries().get(0).item();
            exposed.setCount(99);
            boolean pass = npc.stockEntries().get(0).item().getCount() == 1;
            return result(pass, "exposedCount=" + exposed.getCount() + ", internalCount=" + npc.stockEntries().get(0).item().getCount());
        });
    }

    private static void checkComponentDistinctItemsRemainDistinct(VerificationReporter r, MinecraftServer server) {
        safe(r, "Component-distinct items (Water Bottle vs. Healing Potion) remain distinct stock entries", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            ItemStack healing = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
            npc.setStockForTest(List.of(new MerchantStockEntry(water, 4), new MerchantStockEntry(healing, 1)));
            List<MerchantStockEntry> stock = npc.stockEntries();
            boolean pass = stock.size() == 2 && !ItemStack.isSameItemSameComponents(stock.get(0).item(), stock.get(1).item());
            return result(pass, "stock=" + stock);
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // Persistence
    // ═════════════════════════════════════════════════════════════════════

    private static void checkStockRoundTripPreservesOrder(VerificationReporter r, MinecraftServer server) {
        safe(r, "Stock NBT round-trip preserves entry order", () -> {
            ProvisionerNpcEntity original = isolatedProvisioner(server);
            original.setCreditsForTest(300);
            original.setStockForTest(List.of(
                    new MerchantStockEntry(new ItemStack(Items.TORCH), 16),
                    new MerchantStockEntry(new ItemStack(Items.BREAD), 6),
                    new MerchantStockEntry(new ItemStack(Items.COAL), 8)
            ));
            ProvisionerNpcEntity reloaded = roundTrip(server, original);
            List<MerchantStockEntry> before = original.stockEntries();
            List<MerchantStockEntry> after = reloaded.stockEntries();
            boolean pass = before.size() == after.size();
            for (int i = 0; pass && i < before.size(); i++) {
                pass = ItemStack.isSameItemSameComponents(before.get(i).item(), after.get(i).item())
                        && before.get(i).currentStock() == after.get(i).currentStock();
            }
            return result(pass, "before=" + before + ", after=" + after);
        });
    }

    private static void checkStockRoundTripPreservesWaterBottleComponents(VerificationReporter r, MinecraftServer server) {
        safe(r, "Stock NBT round-trip preserves Water Bottle's potion_contents component", () -> {
            ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            ProvisionerNpcEntity original = isolatedProvisioner(server);
            original.setCreditsForTest(300);
            original.setStockForTest(List.of(new MerchantStockEntry(water, 4)));
            ProvisionerNpcEntity reloaded = roundTrip(server, original);
            boolean pass = findMatching(reloaded.stockEntries(), water).isPresent();
            return result(pass, "reloaded=" + reloaded.stockEntries());
        });
    }

    private static void checkStockRoundTripPreservesHealingPotionComponents(VerificationReporter r, MinecraftServer server) {
        safe(r, "Stock NBT round-trip preserves Healing Potion's potion_contents component", () -> {
            ItemStack healing = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
            ProvisionerNpcEntity original = isolatedProvisioner(server);
            original.setCreditsForTest(300);
            original.setStockForTest(List.of(new MerchantStockEntry(healing, 1)));
            ProvisionerNpcEntity reloaded = roundTrip(server, original);
            boolean pass = findMatching(reloaded.stockEntries(), healing).isPresent();
            return result(pass, "reloaded=" + reloaded.stockEntries());
        });
    }

    private static void checkZeroStockSurvivesRoundTrip(VerificationReporter r, MinecraftServer server) {
        safe(r, "Zero stock survives round-trip (not dropped, not rerolled)", () -> {
            ProvisionerNpcEntity original = isolatedProvisioner(server);
            original.setCreditsForTest(300);
            original.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 0)));
            ProvisionerNpcEntity reloaded = roundTrip(server, original);
            List<MerchantStockEntry> after = reloaded.stockEntries();
            boolean pass = after.size() == 1 && after.get(0).item().is(Items.TORCH) && after.get(0).currentStock() == 0;
            return result(pass, "reloaded=" + after);
        });
    }

    private static void checkZeroCreditsSurviveRoundTrip(VerificationReporter r, MinecraftServer server) {
        safe(r, "currentCredits = 0 survives round-trip", () -> {
            ProvisionerNpcEntity original = isolatedProvisioner(server);
            original.setCreditsForTest(0);
            original.setStockForTest(List.of());
            ProvisionerNpcEntity reloaded = roundTrip(server, original);
            boolean pass = reloaded.currentCredits() == 0L && reloaded.isCreditsInitialized();
            return result(pass, "reloadedCredits=" + reloaded.currentCredits() + ", initialized=" + reloaded.isCreditsInitialized());
        });
    }

    private static void checkAboveBaselineCreditsSurviveRoundTrip(VerificationReporter r, MinecraftServer server) {
        safe(r, "currentCredits > 300 survives round-trip", () -> {
            ProvisionerNpcEntity original = isolatedProvisioner(server);
            original.setCreditsForTest(12_345L);
            original.setStockForTest(List.of());
            ProvisionerNpcEntity reloaded = roundTrip(server, original);
            boolean pass = reloaded.currentCredits() == 12_345L;
            return result(pass, "reloadedCredits=" + reloaded.currentCredits());
        });
    }

    private static void checkInitializationStateSurvivesRoundTrip(VerificationReporter r, MinecraftServer server) {
        safe(r, "Credits/stock initialization markers survive round-trip", () -> {
            ProvisionerNpcEntity original = isolatedProvisioner(server);
            original.setCreditsForTest(300);
            original.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 16)));
            ProvisionerNpcEntity reloaded = roundTrip(server, original);
            boolean pass = reloaded.isCreditsInitialized() && reloaded.isStockInitialized();
            return result(pass, "creditsInitialized=" + reloaded.isCreditsInitialized() + ", stockInitialized=" + reloaded.isStockInitialized());
        });
    }

    private static void checkReloadedInitializedStockDoesNotReroll(VerificationReporter r, MinecraftServer server) {
        safe(r, "A reloaded, already-initialized Provisioner's stock does not reroll", () -> {
            ProvisionerNpcEntity original = spawnedProvisioner(server);
            List<MerchantStockEntry> firstRoll = original.stockEntries();
            ProvisionerNpcEntity reloaded = roundTrip(server, original);
            List<MerchantStockEntry> afterReload = reloaded.stockEntries();
            boolean pass = reloaded.isStockInitialized() && stockEquals(firstRoll, afterReload);
            return result(pass, "firstRoll=" + firstRoll + ", afterReload=" + afterReload);
        });
    }

    private static void checkSoldOutMerchantDoesNotReroll(VerificationReporter r, MinecraftServer server) {
        safe(r, "A fully sold-out merchant does not reroll on reload", () -> {
            ProvisionerNpcEntity original = isolatedProvisioner(server);
            original.setCreditsForTest(300);
            original.setStockForTest(List.of(
                    new MerchantStockEntry(new ItemStack(Items.TORCH), 0),
                    new MerchantStockEntry(new ItemStack(Items.BREAD), 0)
            ));
            ProvisionerNpcEntity reloaded = roundTrip(server, original);
            List<MerchantStockEntry> after = reloaded.stockEntries();
            boolean pass = reloaded.isStockInitialized() && after.size() == 2
                    && after.get(0).currentStock() == 0 && after.get(1).currentStock() == 0;
            return result(pass, "reloaded=" + after);
        });
    }

    private static void checkMissingPoolDoesNotMarkStockInitialized(VerificationReporter r, MinecraftServer server) {
        safe(r, "A missing/invalid assortment pool leaves stock uninitialized, with no partial stock, and does not crash", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            npc.setAssortmentPoolId(Identifier.fromNamespaceAndPath("totality", "selftest/no_such_pool"));
            npc.finalizeSpawn(server.overworld(), server.overworld().getCurrentDifficultyAt(npc.blockPosition()),
                    EntitySpawnReason.COMMAND, null);
            boolean pass = !npc.isStockInitialized() && npc.stockEntries().isEmpty() && npc.isCreditsInitialized();
            return result(pass, "stockInitialized=" + npc.isStockInitialized() + ", stock=" + npc.stockEntries()
                    + ", creditsInitialized=" + npc.isCreditsInitialized());
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // Correction pass — persistence / no natural despawn (Part B)
    // ═════════════════════════════════════════════════════════════════════

    private static void checkProvisionerIsPersistenceRequired(VerificationReporter r, MinecraftServer server) {
        safe(r, "A Provisioner is always persistence-required, regardless of persisted NBT", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            boolean pass = npc.isPersistenceRequired();
            return result(pass, "isPersistenceRequired=" + npc.isPersistenceRequired());
        });
    }

    /**
     * Drives the REAL {@code Mob#checkDespawn()} (public, unmodified vanilla method) against a
     * genuinely level-added Provisioner positioned far beyond any mob category's despawn distance
     * from the only player present — this is the exact code path that discarded a Provisioner when
     * Stefan died and respawned far away (correction pass, Part B root cause). Distance is 100,000
     * blocks specifically so this passes regardless of the entity's {@code MobCategory}'s exact
     * despawn-distance constant (deliberately not hardcoding vanilla's current value here).
     *
     * <p>{@code checkDespawn()}'s distance branch only runs at all once {@code Level#getNearestPlayer}
     * finds SOMEONE — which requires a fake player genuinely registered in the level's own player
     * list ({@code ServerLevel#players()}), not merely constructed. Deliberately uses its OWN
     * dedicated fake player (added and removed within this single check), never the suite-wide
     * {@code player} fixture every other check shares — registering/unregistering a player from a
     * level is exactly the kind of state mutation that must not leak into the dozens of unrelated
     * checks that run after this one in the same suite.
     */
    private static void checkProvisionerSurvivesCheckDespawnFarFromPlayer(VerificationReporter r, MinecraftServer server) {
        safe(r, "A Provisioner survives checkDespawn() even 100,000 blocks from the only player online", () -> {
            ServerPlayer despawnTestPlayer = TotalityFakePlayer.create(server.overworld(), "[ProvisionerVerification-despawn]");
            ProvisionerNpcEntity npc = addedProvisioner(server, despawnTestPlayer);
            try {
                server.overworld().addNewPlayer(despawnTestPlayer);
                npc.setPos(despawnTestPlayer.getX() + 100_000, despawnTestPlayer.getY(), despawnTestPlayer.getZ());
                npc.checkDespawn();
                boolean pass = !npc.isRemoved();
                return result(pass, "removed=" + npc.isRemoved());
            } finally {
                discard(npc);
                server.overworld().removePlayerImmediately(despawnTestPlayer, Entity.RemovalReason.DISCARDED);
            }
        });
    }

    /**
     * Negative control for {@link #checkProvisionerSurvivesCheckDespawnFarFromPlayer}: a plain
     * {@code totality:totality_npc} (not persistence-required) genuinely DOES despawn under the
     * identical conditions — proving the positive check above is actually exercising real
     * despawn logic, not passing vacuously (e.g. because no player is registered as a level player,
     * or {@code checkDespawn} no-ops in this harness for some unrelated reason). Uses its own
     * dedicated fake player, same reasoning as the positive check above.
     */
    private static void checkGenericNpcDespawnsFarFromPlayerControl(VerificationReporter r, MinecraftServer server) {
        safe(r, "Control: a non-persistence-required generic NPC DOES despawn under the identical far-away condition", () -> {
            ServerPlayer despawnTestPlayer = TotalityFakePlayer.create(server.overworld(), "[ProvisionerVerification-despawn-control]");
            zcylas.totality.entity.npc.TotalityNpcEntity npc =
                    new zcylas.totality.entity.npc.TotalityNpcEntity(ModEntities.TOTALITY_NPC, server.overworld());
            npc.setPos(despawnTestPlayer.getX(), despawnTestPlayer.getY(), despawnTestPlayer.getZ());
            server.overworld().addFreshEntity(npc);
            try {
                server.overworld().addNewPlayer(despawnTestPlayer);
                npc.setPos(despawnTestPlayer.getX() + 100_000, despawnTestPlayer.getY(), despawnTestPlayer.getZ());
                npc.checkDespawn();
                boolean pass = npc.isRemoved();
                return result(pass, "removed=" + npc.isRemoved());
            } finally {
                if (!npc.isRemoved()) npc.discard();
                server.overworld().removePlayerImmediately(despawnTestPlayer, Entity.RemovalReason.DISCARDED);
            }
        });
    }

    private static void checkFullSaveLoadRoundTripPreservesCompleteMerchantState(VerificationReporter r, MinecraftServer server) {
        safe(r, "A full save/load round-trip (gender, dialogue id, assortment pool id, Credits, and stock together, "
                + "not just the Phase 3 fields in isolation) preserves the complete merchant state", () -> {
            ProvisionerNpcEntity original = isolatedProvisioner(server);
            // Rolls identity (gender/name) + assortment + Credits together, exactly like a real spawn.
            original.finalizeSpawn(server.overworld(), server.overworld().getCurrentDifficultyAt(original.blockPosition()),
                    EntitySpawnReason.COMMAND, null);
            zcylas.totality.entity.npc.NpcGender originalGender = original.getGender();
            Identifier originalDialogue = original.getDialogueId();
            Identifier originalPool = original.getAssortmentPoolId();
            long originalCredits = original.currentCredits();
            List<MerchantStockEntry> originalStock = original.stockEntries();

            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
            original.simulateFullSaveForTest(output);
            CompoundTag tag = output.buildResult();

            ProvisionerNpcEntity reloaded = isolatedProvisioner(server);
            reloaded.simulateFullLoadForTest(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), tag));

            boolean pass = reloaded.getGender() == originalGender
                    && originalDialogue.equals(reloaded.getDialogueId())
                    && originalPool.equals(reloaded.getAssortmentPoolId())
                    && reloaded.currentCredits() == originalCredits
                    && reloaded.isCreditsInitialized() && reloaded.isStockInitialized()
                    && stockEquals(originalStock, reloaded.stockEntries());
            return result(pass, "gender=" + originalGender + "->" + reloaded.getGender()
                    + ", dialogue=" + originalDialogue + "->" + reloaded.getDialogueId()
                    + ", pool=" + originalPool + "->" + reloaded.getAssortmentPoolId()
                    + ", credits=" + originalCredits + "->" + reloaded.currentCredits()
                    + ", stock=" + originalStock + "->" + reloaded.stockEntries());
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // Per-entity independence
    // ═════════════════════════════════════════════════════════════════════

    private static void checkTwoProvisionersIndependentCredits(VerificationReporter r, MinecraftServer server) {
        safe(r, "Two Provisioners have independent Credits", () -> {
            ProvisionerNpcEntity a = isolatedProvisioner(server);
            ProvisionerNpcEntity b = isolatedProvisioner(server);
            a.setCreditsForTest(100);
            b.setCreditsForTest(999);
            boolean pass = a.currentCredits() == 100 && b.currentCredits() == 999;
            return result(pass, "a=" + a.currentCredits() + ", b=" + b.currentCredits());
        });
    }

    private static void checkTwoProvisionersIndependentStock(VerificationReporter r, MinecraftServer server) {
        safe(r, "Two Provisioners have independent stock lists (distinct backing objects)", () -> {
            ProvisionerNpcEntity a = isolatedProvisioner(server);
            ProvisionerNpcEntity b = isolatedProvisioner(server);
            a.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 16)));
            b.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.BREAD), 6)));
            a.decrementStock(0, 16);
            boolean pass = a.stockEntries().get(0).currentStock() == 0 && b.stockEntries().get(0).currentStock() == 6;
            return result(pass, "a=" + a.stockEntries() + ", b=" + b.stockEntries());
        });
    }

    private static void checkBuyingFromOneDoesNotChangeOther(VerificationReporter r, MinecraftServer server) {
        safe(r, "Mutating one Provisioner's Credits/stock never changes another's", () -> {
            ProvisionerNpcEntity a = isolatedProvisioner(server);
            ProvisionerNpcEntity b = isolatedProvisioner(server);
            a.setCreditsForTest(300);
            b.setCreditsForTest(300);
            a.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 16)));
            b.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 16)));

            a.setCurrentCredits(a.currentCredits() + 24);
            a.decrementStock(0, 4);

            boolean pass = a.currentCredits() == 324 && a.stockEntries().get(0).currentStock() == 12
                    && b.currentCredits() == 300 && b.stockEntries().get(0).currentStock() == 16;
            return result(pass, "a=" + a.currentCredits() + "/" + a.stockEntries() + ", b=" + b.currentCredits() + "/" + b.stockEntries());
        });
    }

    private static void checkSameAssortmentIdDoesNotShareRuntimeState(VerificationReporter r, MinecraftServer server) {
        safe(r, "Two entities rolling against the same assortment id do not share runtime state", () -> {
            ProvisionerNpcEntity a = spawnedProvisioner(server);
            ProvisionerNpcEntity b = spawnedProvisioner(server);
            boolean sameConfiguredPool = a.getAssortmentPoolId().equals(b.getAssortmentPoolId());

            int bFirstBefore = b.stockEntries().get(0).currentStock();
            int aFirstStock = a.stockEntries().get(0).currentStock();
            if (aFirstStock > 0) {
                a.decrementStock(0, aFirstStock); // fully drain a's first entry
            }
            boolean aDrained = a.stockEntries().get(0).currentStock() == 0;
            boolean bUnaffected = b.stockEntries().get(0).currentStock() == bFirstBefore;

            boolean pass = sameConfiguredPool && aDrained && bUnaffected;
            return result(pass, "sameConfiguredPool=" + sameConfiguredPool + ", aDrained=" + aDrained
                    + ", bFirstBefore=" + bFirstBefore + ", bFirstAfter=" + b.stockEntries().get(0).currentStock());
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // Transaction integration — real TradeSessionManager, entity-backed
    // ═════════════════════════════════════════════════════════════════════

    private static void checkEntityBackedSessionResolvesEntityRuntime(
            VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "Entity-backed session resolves the entity's own MerchantRuntime, not a shared runtime", () -> {
            ProvisionerNpcEntity npc = addedProvisioner(server, player);
            try {
                npc.setCreditsForTest(777);
                npc.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 5)));
                TradeSessionManager.startEntityBackedTrade(player, npc);
                boolean pass = TradeSessionManager.isTrading(player) && npc.currentCredits() == 777;
                return result(pass, "isTrading=" + TradeSessionManager.isTrading(player) + ", npcCredits=" + npc.currentCredits());
            } finally {
                TradeSessionManager.endTrade(player);
                discard(npc);
            }
        });
    }

    private static void checkStockBuyDecreasesOnlyThatEntitysStock(
            VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "Successful stock-backed BUY decreases only that entity's stock", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            ProvisionerNpcEntity other = isolatedProvisioner(server);
            long walletBefore = wallet(player);
            try {
                npc.setCreditsForTest(300);
                npc.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 16)));
                other.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 16)));
                giveWallet(player, 10_000);
                TradeSessionManager.startStockBackedTradeForVerification(player, npc, npc);

                BuyResult buyResult = TradeSessionManager.handleBuy(player, 0, 3);

                boolean pass = buyResult.success() && npc.stockEntries().get(0).currentStock() == 13
                        && other.stockEntries().get(0).currentStock() == 16;
                return result(pass, "buyResult=" + buyResult + ", npcStock=" + npc.stockEntries() + ", otherStock=" + other.stockEntries());
            } finally {
                TradeSessionManager.endTrade(player);
                setWallet(player, walletBefore);
            }
        });
    }

    private static void checkStockBuyIncreasesOnlyThatEntitysCredits(
            VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "Successful stock-backed BUY increases only that entity's Credits", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            ProvisionerNpcEntity other = isolatedProvisioner(server);
            long walletBefore = wallet(player);
            try {
                npc.setCreditsForTest(300);
                other.setCreditsForTest(300);
                npc.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.BREAD), 6))); // base value 12 -> retail 12
                giveWallet(player, 10_000);
                TradeSessionManager.startStockBackedTradeForVerification(player, npc, npc);

                BuyResult buyResult = TradeSessionManager.handleBuy(player, 0, 2);

                boolean pass = buyResult.success() && npc.currentCredits() == 300 + 24 && other.currentCredits() == 300;
                return result(pass, "buyResult=" + buyResult + ", npcCredits=" + npc.currentCredits() + ", otherCredits=" + other.currentCredits());
            } finally {
                TradeSessionManager.endTrade(player);
                setWallet(player, walletBefore);
            }
        });
    }

    private static void checkOutOfStockBuyChangesNothing(
            VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "A failed out-of-stock BUY changes no player or merchant state", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            long walletBefore = wallet(player);
            try {
                npc.setCreditsForTest(300);
                npc.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 2)));
                giveWallet(player, 10_000);
                long afterGive = wallet(player);
                TradeSessionManager.startStockBackedTradeForVerification(player, npc, npc);

                BuyResult buyResult = TradeSessionManager.handleBuy(player, 0, 5);

                boolean pass = !buyResult.success() && buyResult.reason() == BuyResult.Reason.OUT_OF_STOCK
                        && wallet(player) == afterGive && npc.currentCredits() == 300
                        && npc.stockEntries().get(0).currentStock() == 2;
                return result(pass, "buyResult=" + buyResult + ", npcCredits=" + npc.currentCredits() + ", npcStock=" + npc.stockEntries());
            } finally {
                TradeSessionManager.endTrade(player);
                setWallet(player, walletBefore);
            }
        });
    }

    private static void checkEntityBackedSellDecreasesCreditsAndSkipsStock(
            VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "Entity-backed SELL decreases that entity's Credits and never adds the item to sale stock", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            int slot = 8;
            ItemStack slotBefore = player.getInventory().getItem(slot).copy();
            long walletBefore = wallet(player);
            try {
                npc.setCreditsForTest(300);
                npc.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 16)));
                player.getInventory().setItem(slot, new ItemStack(Items.BREAD, 4)); // accepted tag + base value 12 -> sell payout 6
                TradeSessionManager.startStockBackedTradeForVerification(player, npc, npc);

                SellResult sellResult = TradeSessionManager.handleSell(player, slot, 4);

                int stockSizeAfter = npc.stockEntries().size();
                boolean breadInStock = npc.stockEntries().stream().anyMatch(e -> e.item().is(Items.BREAD));
                boolean pass = sellResult.success() && npc.currentCredits() == 300 - 24
                        && stockSizeAfter == 1 && !breadInStock;
                return result(pass, "sellResult=" + sellResult + ", npcCredits=" + npc.currentCredits() + ", stock=" + npc.stockEntries());
            } finally {
                TradeSessionManager.endTrade(player);
                player.getInventory().setItem(slot, slotBefore);
                setWallet(player, walletBefore);
            }
        });
    }

    private static void checkStaleIndexRejected(VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "A stale/out-of-range stock index is rejected without mutation", () -> {
            ProvisionerNpcEntity npc = isolatedProvisioner(server);
            long walletBefore = wallet(player);
            try {
                npc.setCreditsForTest(300);
                npc.setStockForTest(List.of(new MerchantStockEntry(new ItemStack(Items.TORCH), 16)));
                giveWallet(player, 10_000);
                long afterGive = wallet(player);
                TradeSessionManager.startStockBackedTradeForVerification(player, npc, npc);

                BuyResult buyResult = TradeSessionManager.handleBuy(player, 7, 1);

                boolean pass = !buyResult.success() && buyResult.reason() == BuyResult.Reason.INVALID_INDEX
                        && wallet(player) == afterGive && npc.currentCredits() == 300;
                return result(pass, "buyResult=" + buyResult);
            } finally {
                TradeSessionManager.endTrade(player);
                setWallet(player, walletBefore);
            }
        });
    }

    private static void checkShopEntryDisplayDataSoldOutRepresentation(VerificationReporter r) {
        safe(r, "ShopEntryDisplayData represents limited/unlimited/sold-out stock correctly", () -> {
            zcylas.totality.networking.shop.ShopEntryDisplayData unlimited =
                    new zcylas.totality.networking.shop.ShopEntryDisplayData(new ItemStack(Items.IRON_SWORD), 450, true);
            zcylas.totality.networking.shop.ShopEntryDisplayData limitedAvailable =
                    new zcylas.totality.networking.shop.ShopEntryDisplayData(new ItemStack(Items.TORCH), 4, true, true, 5);
            zcylas.totality.networking.shop.ShopEntryDisplayData soldOut =
                    new zcylas.totality.networking.shop.ShopEntryDisplayData(new ItemStack(Items.TORCH), 4, false, true, 0);
            boolean pass = !unlimited.limitedStock() && !unlimited.soldOut()
                    && limitedAvailable.limitedStock() && !limitedAvailable.soldOut()
                    && soldOut.limitedStock() && soldOut.soldOut();
            return result(pass, "unlimited=" + unlimited + ", limitedAvailable=" + limitedAvailable + ", soldOut=" + soldOut);
        });
    }

    private static void checkExistingNonEntitySessionStillWorks(VerificationReporter r, ServerPlayer player) {
        safe(r, "Existing non-entity/test-trader style sessions still work after the Phase 3 branching changes", () -> {
            ShopTemplate shop = new ShopTemplate("Verification Shop",
                    List.of(new ShopEntry(new ItemStack(Items.IRON_SWORD), 450)));
            MerchantRuntime merchant = new InMemoryMerchantRuntime(
                    Identifier.fromNamespaceAndPath("totality", "selftest/provisioner_regression_merchant"),
                    300, Set.of(zcylas.totality.init.ModTags.PROVISIONER_BUYS));
            long walletBefore = wallet(player);
            try {
                TradeSessionManager.startTradeForVerification(
                        player, Identifier.fromNamespaceAndPath("totality", "selftest/provisioner_regression_shop"), shop, merchant);
                giveWallet(player, 10_000);
                BuyResult buyResult = TradeSessionManager.handleBuy(player, 0, 1);
                boolean pass = buyResult.success() && merchant.currentCredits() == 300 + 450;
                return result(pass, "buyResult=" + buyResult + ", merchantCredits=" + merchant.currentCredits());
            } finally {
                TradeSessionManager.endTrade(player);
                setWallet(player, walletBefore);
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // Fixtures and helpers
    // ═════════════════════════════════════════════════════════════════════

    /** Mirrors {@code data/totality/provisioner_assortments/generic_provisioner.json} exactly, as
     *  a hand-built, isolated fixture — so seed-driven probabilistic assertions never depend on
     *  production data staying byte-for-byte identical to this suite's expectations. */
    private static ProvisionerAssortmentPool testPool() {
        List<AssortmentItemEntry> guaranteed = List.of(
                new AssortmentItemEntry(new ItemStack(Items.TORCH), 16),
                new AssortmentItemEntry(new ItemStack(Items.BREAD), 6),
                new AssortmentItemEntry(PotionContents.createItemStack(Items.POTION, Potions.WATER), 4)
        );
        List<WeightedAssortmentEntry> common = List.of(
                new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.OAK_PLANKS), 32), 1),
                new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.COAL), 16), 1),
                new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.STRING), 8), 1),
                new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.LEATHER), 6), 1),
                new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.GLASS_BOTTLE), 8), 1),
                new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.APPLE), 8), 1)
        );
        List<WeightedAssortmentEntry> cookedFood = List.of(
                new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.COOKED_BEEF), 6), 1),
                new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.COOKED_PORKCHOP), 6), 1)
        );
        List<WeightedAssortmentEntry> tools = List.of(
                new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.STONE_SHOVEL), 2), 9),
                new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.STONE_SWORD), 2), 9),
                new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.STONE_PICKAXE), 2), 9),
                new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.STONE_AXE), 2), 9),
                new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.IRON_SHOVEL), 1), 1),
                new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.IRON_SWORD), 1), 1),
                new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.IRON_PICKAXE), 1), 1),
                new WeightedAssortmentEntry(new AssortmentItemEntry(new ItemStack(Items.IRON_AXE), 1), 1)
        );
        List<AssortmentSelectionGroup> groups = List.of(
                new AssortmentSelectionGroup(4, true, common),
                new AssortmentSelectionGroup(1, true, cookedFood),
                new AssortmentSelectionGroup(1, true, tools)
        );
        List<ChanceAssortmentEntry> chance = List.of(new ChanceAssortmentEntry(
                new AssortmentItemEntry(PotionContents.createItemStack(Items.POTION, Potions.HEALING), 1), 0.125));
        return new ProvisionerAssortmentPool(guaranteed, groups, chance);
    }

    private static Optional<MerchantStockEntry> findMatching(List<MerchantStockEntry> stock, ItemStack template) {
        return stock.stream().filter(e -> ItemStack.isSameItemSameComponents(e.item(), template)).findFirst();
    }

    private static boolean stockEquals(List<MerchantStockEntry> a, List<MerchantStockEntry> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.isSameItemSameComponents(a.get(i).item(), b.get(i).item())) return false;
            if (a.get(i).currentStock() != b.get(i).currentStock()) return false;
        }
        return true;
    }

    /** A never-added-to-a-level Provisioner — used for pure state checks that don't need a real
     *  trade session, following {@code MerchantSellVerification}'s existing precedent for
     *  never-added entity fixtures. */
    private static ProvisionerNpcEntity isolatedProvisioner(MinecraftServer server) {
        return new ProvisionerNpcEntity(ModEntities.PROVISIONER, server.overworld());
    }

    /** An isolated Provisioner that has actually rolled against the REAL, datapack-loaded default
     *  assortment pool (via {@code finalizeSpawn}, the real roll-once code path) — used by checks
     *  that need a genuinely non-empty, non-hand-seeded stock/Credits pair. */
    private static ProvisionerNpcEntity spawnedProvisioner(MinecraftServer server) {
        ProvisionerNpcEntity npc = isolatedProvisioner(server);
        npc.finalizeSpawn(server.overworld(), server.overworld().getCurrentDifficultyAt(npc.blockPosition()),
                EntitySpawnReason.COMMAND, null);
        return npc;
    }

    /** A Provisioner added to the level, positioned at the fake player, for transaction-
     *  integration checks that need a real {@link TradeSessionManager} entity-backed session. */
    private static ProvisionerNpcEntity addedProvisioner(MinecraftServer server, ServerPlayer player) {
        ProvisionerNpcEntity npc = new ProvisionerNpcEntity(ModEntities.PROVISIONER, server.overworld());
        npc.setPos(player.getX(), player.getY(), player.getZ());
        server.overworld().addFreshEntity(npc);
        return npc;
    }

    private static void discard(ProvisionerNpcEntity npc) {
        if (!npc.isRemoved()) npc.discard();
    }

    /** Writes {@code original}'s Phase 3 state to a real NBT tag and reads it back into a BRAND
     *  NEW {@link ProvisionerNpcEntity} instance — deliberately a different Java object, exactly
     *  mirroring what a real chunk-unload/reload cycle produces (same conceptual entity, new
     *  object), so this exercises the exact "reload never rerolls" guarantee production code
     *  relies on. */
    private static ProvisionerNpcEntity roundTrip(MinecraftServer server, ProvisionerNpcEntity original) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
        original.writePhase3State(output);
        CompoundTag tag = output.buildResult();

        ProvisionerNpcEntity reloaded = new ProvisionerNpcEntity(ModEntities.PROVISIONER, server.overworld());
        ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), tag);
        reloaded.readPhase3State(input);
        return reloaded;
    }

    private static long wallet(ServerPlayer player) {
        return zcylas.totality.api.economy.currency.CurrencyComponents.WALLET
                .get((zcylas.totality.api.core.component.ComponentProvider) player).getValue();
    }

    private static void setWallet(ServerPlayer player, long value) {
        zcylas.totality.api.economy.currency.CurrencyComponents.WALLET
                .get((zcylas.totality.api.core.component.ComponentProvider) player).setValue(value);
    }

    private static void giveWallet(ServerPlayer player, long amount) {
        zcylas.totality.api.economy.currency.CurrencyComponents.WALLET
                .get((zcylas.totality.api.core.component.ComponentProvider) player).modify(amount);
    }

    private record CheckResult(boolean pass, String detail) {}

    private static CheckResult result(boolean pass, String detail) {
        return new CheckResult(pass, detail);
    }

    private static void safe(VerificationReporter r, String label, Supplier<CheckResult> body) {
        try {
            CheckResult checkResult = body.get();
            r.check(label, checkResult.pass(), checkResult.detail());
        } catch (RuntimeException e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
