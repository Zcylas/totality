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
import zcylas.totality.api.core.util.VerificationReporter;
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

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(ProvisionerVerification::runSelfTestIfDev);
    }

    static void runSelfTestIfDev(MinecraftServer server) {
        if (!VerificationReporter.isDevEnvironment()) return;

        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "ProvisionerVerification");
        ServerPlayer player = TotalityFakePlayer.create(server.overworld(), "[ProvisionerVerification]");

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
