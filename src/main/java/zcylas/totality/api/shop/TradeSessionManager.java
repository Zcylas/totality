package zcylas.totality.api.shop;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.economy.currency.CreditPaymentHelper;
import zcylas.totality.api.economy.currency.CurrencyComponents;
import zcylas.totality.api.economy.value.ItemPricingService;
import zcylas.totality.api.economy.value.ItemValueRegistry;
import zcylas.totality.api.economy.value.PriceQuote;
import zcylas.totality.api.economy.value.PricingContext;
import zcylas.totality.api.economy.value.PricingDirection;
import zcylas.totality.entity.npc.TotalityNpcEntity;
import zcylas.totality.networking.shop.SellQuoteResultPayload;
import zcylas.totality.networking.shop.ShopEntryDisplayData;
import zcylas.totality.networking.shop.ShowShopStatePayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side trade session tracking, mirroring {@link zcylas.totality.api.dialogue.DialogueSessionManager}'s
 * shape but without a branching state machine — a shop's catalog is static per session,
 * only the player's Credits/afford status changes between updates.
 *
 * <p>Phase 3 extension (design document Parts H/I): a session may be either TEMPLATE-backed (the
 * original, shared {@link ShopRegistry}/{@link MerchantRuntimeRegistry} path — {@code test_trader}
 * and any future non-entity shop) or STOCK-backed (an entity's own {@link MerchantRuntime}/
 * {@link MerchantStockProvider} — the Provisioner path, started via
 * {@link #startEntityBackedTrade}). {@code ActiveTrade.template()} is {@code null} for the latter.
 * Every BUY/SELL commit RE-RESOLVES the live NPC entity via {@link #currentNpc} rather than
 * trusting a {@link MerchantRuntime}/{@link MerchantStockProvider} reference captured at session
 * start — a chunk unload/reload cycle deserializes a NEW Java object with the SAME UUID, so a
 * stored reference would silently go stale and write to a discarded entity instance. Re-resolving
 * every time (cheap: a level/entity-id lookup) avoids that without needing any long-lived mutable
 * entity reference in this class's own static state.
 */
public final class TradeSessionManager {

    private record ActiveTrade(
            Identifier shopId, @Nullable ShopTemplate template, MerchantRuntime merchant,
            @Nullable MerchantStockProvider stockProvider,
            int npcEntityId, @Nullable UUID npcUuid, @Nullable ResourceKey<Level> npcDimension, Component npcName) {

        /** False for sessions deliberately opened without an NPC (verification, a future remote
         *  shop) — represented explicitly via {@code npcEntityId == -1}, never confused with a
         *  missing/removed NPC on an otherwise entity-backed session. */
        boolean isEntityBacked() { return npcEntityId != -1; }
    }

    private static final Map<UUID, ActiveTrade> SESSIONS = new HashMap<>();

    private TradeSessionManager() {}

    public static void register() {
        // No client connection remains to notify by the time the logical server has fully
        // stopped — just drop every session so nothing leaks into whatever world opens next in
        // the same client JVM (economy hardening pass, Part 3).
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
    }

    public static void startTrade(ServerPlayer player, Identifier shopId, @Nullable Entity npc) {
        ShopTemplate template = ShopRegistry.INSTANCE.get(shopId);
        if (template == null) return;
        MerchantRuntime merchant = MerchantRuntimeRegistry.getOrCreate(player.level().getServer(), shopId);
        beginSession(player, shopId, template, merchant, null, npc);
    }

    /**
     * Entity-backed trade using the NPC's OWN {@link MerchantRuntime} (and, if implemented,
     * {@link MerchantStockProvider}) instead of a shared {@code shop_id}-keyed runtime (design
     * document Phase 3 Part H) — the Provisioner path. {@code npc} must implement
     * {@link MerchantRuntime}; if it doesn't, this is a silent no-op, matching
     * {@link #startTrade}'s existing "unknown shop -> no-op" behavior for a missing template.
     * {@code stockProvider} is always resolved LIVE from {@code npc} on every subsequent
     * transaction (see {@link #currentStockProvider}) — never captured here.
     */
    public static void startEntityBackedTrade(ServerPlayer player, Entity npc) {
        if (!(npc instanceof MerchantRuntime merchant)) return;
        beginSession(player, merchant.merchantId(), null, merchant, null, npc);
    }

    /**
     * Verification-only entry point: starts a session against an explicitly supplied, isolated
     * {@link ShopTemplate} and {@link MerchantRuntime} instead of the shared, datapack-loaded
     * {@link ShopRegistry}/{@link MerchantRuntimeRegistry}-backed ones, so self-tests can never
     * create or mutate production shop/merchant state, and can freely fabricate fixtures (e.g. a
     * malformed negative-priced entry) that no real datapack authors (economy hardening pass,
     * Part 2). Non-entity-backed.
     */
    static void startTradeForVerification(ServerPlayer player, Identifier shopId, ShopTemplate template, MerchantRuntime runtime) {
        startTradeForVerification(player, shopId, template, runtime, null);
    }

    /** Entity-backed variant, for verifying {@link #revalidateNpc} itself (economy hardening
     *  pass, Part 4). */
    static void startTradeForVerification(
            ServerPlayer player, Identifier shopId, ShopTemplate template, MerchantRuntime runtime, @Nullable Entity npc) {
        beginSession(player, shopId, template, runtime, null, npc);
    }

    /**
     * Verification-only entry point for STOCK-backed sessions (Phase 3 Part L): binds directly to
     * an explicitly supplied, isolated {@link MerchantRuntime}/{@link MerchantStockProvider} pair
     * with NO entity at all ({@code npcEntityId == -1}), so {@link #handleBuy}/{@link #handleSell}
     * exercise the exact same stock-aware production logic a real Provisioner uses, without
     * requiring a live {@code Level#getEntity} resolution — self-tests run at
     * {@code SERVER_STARTED}, before the server has processed a single tick, at which point a
     * freshly {@code addFreshEntity}'d entity is provably NOT yet visible to that lookup (the
     * same documented limitation {@code MerchantSellVerification}'s entity-backed checks already
     * flag). A real, entity-backed Provisioner session is unaffected by this — it always goes
     * through {@link #startEntityBackedTrade} and always re-resolves its stock provider from the
     * LIVE entity, never from this field.
     */
    static void startStockBackedTradeForVerification(
            ServerPlayer player, MerchantRuntime merchant, MerchantStockProvider stockProvider) {
        beginSession(player, merchant.merchantId(), null, merchant, stockProvider, null);
    }

    private static void beginSession(
            ServerPlayer player, Identifier shopId, @Nullable ShopTemplate template, MerchantRuntime merchant,
            @Nullable MerchantStockProvider stockProvider, @Nullable Entity npc) {
        // Replacing an existing session must release ITS npc lock first — otherwise the previous
        // partner stays frozen/staring forever if a second trade opens without the first cleanly
        // ending (economy hardening pass, Part 4).
        releaseTradePartner(player, SESSIONS.get(player.getUUID()));

        Component npcName = npc != null ? npc.getName() : Component.empty();
        int npcId = npc != null ? npc.getId() : -1;
        UUID npcUuid = npc != null ? npc.getUUID() : null;
        ResourceKey<Level> npcDimension = npc != null ? npc.level().dimension() : null;

        SESSIONS.put(player.getUUID(),
                new ActiveTrade(shopId, template, merchant, stockProvider, npcId, npcUuid, npcDimension, npcName));
        if (npc instanceof TotalityNpcEntity totNpc) {
            totNpc.acquireInteractionLock(player);
        }
        sendState(player, false);
    }

    public static BuyResult handleBuy(ServerPlayer player, int index, int quantity) {
        ActiveTrade trade = SESSIONS.get(player.getUUID());
        if (trade == null) return BuyResult.rejected(BuyResult.Reason.NO_SESSION);
        if (!revalidateNpc(player, trade)) return BuyResult.rejected(BuyResult.Reason.NPC_INVALID);

        Entity liveNpc = currentNpc(player, trade);
        MerchantStockProvider stockProvider = currentStockProvider(trade, liveNpc);
        if (stockProvider != null) {
            return handleStockBuy(player, currentMerchant(trade, liveNpc), stockProvider, index, quantity);
        }

        // Template-backed path (unchanged from Phase 2). A session is normally either
        // stock-backed (handled above) or template-backed, but defends against a hypothetical
        // future MerchantRuntime-only entity implementing neither ShopTemplate nor
        // MerchantStockProvider, which would otherwise NPE here.
        if (trade.template() == null) return BuyResult.rejected(BuyResult.Reason.NPC_INVALID);
        List<ShopEntry> sells = trade.template().sells();
        if (index < 0 || index >= sells.size()) return BuyResult.rejected(BuyResult.Reason.INVALID_INDEX);

        // Explicit range check, not a silent clamp (Phase 3 hardening pass, Section 6) — 0,
        // negative, and >100 are all rejected outright rather than being coerced into 1 or 100,
        // and this happens before any price calculation, affordability check, payment, item
        // delivery, or merchant/stock mutation.
        if (quantity < 1 || quantity > 100) return BuyResult.rejected(BuyResult.Reason.INVALID_QUANTITY);

        ShopEntry entry = sells.get(index);
        if (entry.price() < 0) return BuyResult.rejected(BuyResult.Reason.INVALID_PRICE);

        long total;
        try {
            total = Math.multiplyExact(entry.price(), (long) quantity);
        } catch (ArithmeticException overflow) {
            return BuyResult.rejected(BuyResult.Reason.OVERFLOW);
        }

        // Validate the merchant's balance can safely absorb this BEFORE charging the player —
        // a failed BUY must never charge the player, and merchant Credits must never change on
        // a failed attempt (Section 11 step 4 / Part F).
        MerchantRuntime merchant = currentMerchant(trade, liveNpc);
        // A corrupt (negative) merchant balance must refuse to transact rather than silently
        // charging the player against nonsense state (Phase 3 hardening pass, Section 2) — should
        // never actually occur post-hardening (negative persisted Credits are sanitized on load,
        // and setCurrentCredits itself rejects negative), kept as defense-in-depth.
        if (merchant.currentCredits() < 0) return BuyResult.rejected(BuyResult.Reason.INVALID_MERCHANT_STATE);
        long newMerchantCredits;
        try {
            newMerchantCredits = Math.addExact(merchant.currentCredits(), total);
        } catch (ArithmeticException overflow) {
            return BuyResult.rejected(BuyResult.Reason.OVERFLOW);
        }

        if (!CreditPaymentHelper.pay(player, total)) return BuyResult.rejected(BuyResult.Reason.CANNOT_AFFORD);

        merchant.setCurrentCredits(newMerchantCredits);

        // Individual copies, not one stack multiplied by count — a full inventory drops
        // the remainder on the ground instead of blocking/rolling back the purchase.
        for (int i = 0; i < quantity; i++) {
            ItemStack gift = entry.stack().copy();
            if (!player.getInventory().add(gift)) {
                player.drop(gift, false);
            }
        }

        sendState(player, false);
        return BuyResult.success(total, quantity);
    }

    /**
     * Stock-aware BUY (design document Phase 3 Part I) — the Provisioner path. Validates, in
     * order: valid entry index; sufficient current stock ({@link MerchantStockProvider#canPurchaseStock});
     * a resolvable live retail price ({@link ItemPricingService#quoteRetail} against the entry's
     * OWN item template — never a persisted price, per Part B); the merchant's balance can safely
     * absorb the total; and the player can pay. Only once every check has passed does anything
     * mutate: pay the player, credit the merchant, decrement stock, then deliver the items —
     * mirroring the template-backed path's existing "validate everything first" discipline, so no
     * rollback machinery is needed here either.
     */
    private static BuyResult handleStockBuy(
            ServerPlayer player, MerchantRuntime merchant, MerchantStockProvider stockProvider, int index, int quantity) {
        // See the identical check in the template-backed path above (Phase 3 hardening pass,
        // Section 2) — refuses to transact against a corrupt (negative) merchant balance rather
        // than silently charging the player.
        if (merchant.currentCredits() < 0) return BuyResult.rejected(BuyResult.Reason.INVALID_MERCHANT_STATE);

        List<MerchantStockEntry> entries = stockProvider.stockEntries();
        if (index < 0 || index >= entries.size()) return BuyResult.rejected(BuyResult.Reason.INVALID_INDEX);

        // Explicit range check, not a silent clamp (Phase 3 hardening pass, Section 6) — see the
        // identical rationale in the template-backed path above.
        if (quantity < 1 || quantity > 100) return BuyResult.rejected(BuyResult.Reason.INVALID_QUANTITY);

        // The entry at `index` never changes identity except by its stock count decreasing —
        // MerchantStockProvider implementations never remove/reorder entries — so re-reading it
        // here already IS "the entry still represents the server's current stock item" (Part I
        // step 5); no separate staleness check is needed beyond the fresh stockEntries() read.
        MerchantStockEntry entry = entries.get(index);
        if (!stockProvider.canPurchaseStock(index, quantity)) return BuyResult.rejected(BuyResult.Reason.OUT_OF_STOCK);

        PricingContext context = new PricingContext(
                player, Optional.of(merchant.merchantId()), PricingDirection.RETAIL, Optional.empty());
        Optional<PriceQuote> quote;
        try {
            quote = ItemPricingService.quoteRetail(entry.item(), quantity, context);
        } catch (ArithmeticException overflow) {
            return BuyResult.rejected(BuyResult.Reason.OVERFLOW);
        }
        if (quote.isEmpty()) return BuyResult.rejected(BuyResult.Reason.INVALID_PRICE);
        long total = quote.get().total();

        long newMerchantCredits;
        try {
            newMerchantCredits = Math.addExact(merchant.currentCredits(), total);
        } catch (ArithmeticException overflow) {
            return BuyResult.rejected(BuyResult.Reason.OVERFLOW);
        }

        if (!CreditPaymentHelper.pay(player, total)) return BuyResult.rejected(BuyResult.Reason.CANNOT_AFFORD);

        merchant.setCurrentCredits(newMerchantCredits);
        stockProvider.decrementStock(index, quantity);

        for (int i = 0; i < quantity; i++) {
            ItemStack gift = entry.item();
            if (!player.getInventory().add(gift)) {
                player.drop(gift, false);
            }
        }

        sendState(player, false);
        return BuyResult.success(total, quantity);
    }

    /**
     * Server-authoritative SELL — the client only ever supplies a slot index and a quantity;
     * every other fact (the real stack, its count, the merchant's acceptance/value/Credits, and
     * the resulting quote) is re-read and revalidated here, never trusted from the client. See
     * design document Section D for the full validation order this method implements:
     *
     * <ol>
     *   <li>An active trade session exists for the player.
     *   <li>An entity-backed session's NPC is still present, alive, the same entity, in the same
     *       dimension, in range, and still owns the interaction lock ({@link #revalidateNpc}).
     *   <li>{@code quantity} is positive.
     *   <li>{@code slotIndex} is a valid inventory slot.
     *   <li>The real stack in that slot is non-empty.
     *   <li>The real stack holds at least {@code quantity}.
     *   <li>The merchant accepts the item ({@link MerchantSellQuoteView}).
     *   <li>The item has a resolvable central base value ({@link MerchantSellQuoteView}).
     *   <li>A SELL quote can be produced ({@link MerchantSellQuoteView}).
     *   <li>The quote total is valid and does not overflow ({@link MerchantSellQuoteView}).
     *   <li>The merchant has enough {@code currentCredits} ({@link MerchantSellQuoteView}).
     *   <li>The player can safely receive the payout ({@link CreditPaymentHelper#canReceive}).
     * </ol>
     *
     * <p>Every precondition is confirmed before any mutation. The commit itself (remove item,
     * pay player, decrement merchant Credits) uses operations already proven safe by the
     * preceding validation, so no explicit rollback machinery is needed — nothing in the commit
     * step can fail after validation has passed.
     *
     * <p>A successful SELL never adds the sold item to any merchant's BUY stock (Phase 3 Part I)
     * — this method's only stock/stack mutation is the player's own inventory; a
     * {@link MerchantStockProvider}, if the merchant has one, is never touched here.
     */
    public static SellResult handleSell(ServerPlayer player, int slotIndex, int quantity) {
        ActiveTrade trade = SESSIONS.get(player.getUUID());
        if (trade == null) return SellResult.rejected(SellResult.Reason.NO_SESSION);
        if (!revalidateNpc(player, trade)) return SellResult.rejected(SellResult.Reason.NPC_INVALID);

        if (quantity <= 0) return SellResult.rejected(SellResult.Reason.INVALID_QUANTITY);

        Inventory inventory = player.getInventory();
        if (slotIndex < 0 || slotIndex >= inventory.getContainerSize()) {
            return SellResult.rejected(SellResult.Reason.INVALID_SLOT);
        }

        ItemStack real = inventory.getItem(slotIndex);
        if (real.isEmpty()) return SellResult.rejected(SellResult.Reason.EMPTY_STACK);
        if (real.getCount() < quantity) return SellResult.rejected(SellResult.Reason.INSUFFICIENT_STACK);

        MerchantRuntime merchant = currentMerchant(trade, currentNpc(player, trade));
        MerchantSellQuoteView quote = MerchantSellQuoteView.compute(player, merchant, real, real.getCount(), quantity);
        if (!quote.sellable()) {
            return SellResult.rejected(SellResult.Reason.NOT_SELLABLE, quote.rejectionReason().orElse("unknown"));
        }

        long payout = quote.totalPayout();
        // A player with no open bank account has nowhere for a Wallet credit to represent —
        // physical Credits are the only thing they can actually hold/use (Phase 4 correction
        // pass, Part A). Never inferred from WalletComponent's mere existence (every player has
        // one regardless of account status) — CreditPaymentHelper.hasOpenAccount reads the same
        // has_account narrative flag the Banker's own account-opening dialogue sets, and this
        // SELL path never sets that flag itself (no implicit account opening).
        boolean hasAccount = CreditPaymentHelper.hasOpenAccount(player);
        boolean canDeliver = hasAccount
                ? CreditPaymentHelper.canReceive(player, payout)
                : CreditPaymentHelper.canReceivePhysical(payout);
        if (!canDeliver) {
            return SellResult.rejected(SellResult.Reason.CANNOT_RECEIVE);
        }

        inventory.removeItem(slotIndex, quantity);
        if (hasAccount) {
            CreditPaymentHelper.receive(player, payout);
        } else {
            CreditPaymentHelper.receivePhysical(player, payout);
        }
        merchant.setCurrentCredits(merchant.currentCredits() - payout);

        sendState(player, false);
        return SellResult.success(payout, quantity);
    }

    public static void endTrade(ServerPlayer player) {
        ActiveTrade removed = SESSIONS.remove(player.getUUID());
        releaseTradePartner(player, removed);
        ServerPlayNetworking.send(player, new ShowShopStatePayload(
                -1, Component.empty(), Component.empty(), List.of(), 0, 0, 0, List.of(), true
        ));
    }

    /**
     * Phase 4: computes a live, server-authoritative SELL quote for {@code slotIndex} WITHOUT
     * committing anything — the SELL detail panel's unit payout / stock count / max-quantity
     * fields (design document Part D) need real server data ({@code ItemValueRegistry} has no
     * client-side equivalent), but requesting a quote must never mutate any state itself. Reuses
     * the exact same {@link #revalidateNpc}/{@link MerchantSellQuoteView#compute} machinery
     * {@link #handleSell} commits against, so the live preview and the eventual commit can never
     * silently disagree about how sellability/payout is computed. Also piggybacks a full {@link
     * #sendState} refresh (Credits, valued-inventory-slot snapshot) so browsing SELL mode keeps
     * the whole screen reasonably current without a dedicated inventory-change watcher.
     */
    public static void requestSellQuote(ServerPlayer player, int slotIndex) {
        ActiveTrade trade = SESSIONS.get(player.getUUID());
        if (trade == null) return;
        if (!revalidateNpc(player, trade)) return;

        Inventory inventory = player.getInventory();
        SellQuoteResultPayload result;
        if (slotIndex < 0 || slotIndex >= inventory.getContainerSize()) {
            result = SellQuoteResultPayload.empty(slotIndex);
        } else {
            ItemStack real = inventory.getItem(slotIndex);
            if (real.isEmpty()) {
                result = SellQuoteResultPayload.empty(slotIndex);
            } else {
                MerchantRuntime merchant = currentMerchant(trade, currentNpc(player, trade));
                // requestedQuantity=1 only to obtain the quantity-INDEPENDENT fields (unitPayout,
                // maxQuantityByStack, maxQuantityByMerchant, effectiveMaxQuantity) — the client
                // computes its own DISPLAY total as unitPayout * selectedQuantity, still fully
                // revalidated server-side at actual SELL commit time regardless.
                MerchantSellQuoteView quote = MerchantSellQuoteView.compute(player, merchant, real, real.getCount(), 1);
                result = new SellQuoteResultPayload(slotIndex, quote.accepted(), quote.hasValue(), quote.unitPayout(),
                        real.getCount(), quote.maxQuantityByStack(), quote.maxQuantityByMerchant(), quote.effectiveMaxQuantity());
            }
        }
        ServerPlayNetworking.send(player, result);
        sendState(player, false);
    }

    /** The player's own inventory slot indices whose current stack has a resolvable central
     *  {@code ItemValueRegistry} value (Phase 4) — {@code ItemValueRegistry} is server-only data
     *  with no client-side equivalent, so the SELL inventory grid's "no known value" state cannot
     *  be determined any other way. Recomputed on every {@link #sendState} call, so it degrades
     *  gracefully (never stale-authoritative) as the player's inventory changes between refreshes —
     *  the actual SELL commit always re-reads and revalidates the real stack regardless. */
    private static List<Integer> computeValuedInventorySlots(ServerPlayer player) {
        List<Integer> slots = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && ItemValueRegistry.INSTANCE.resolveBaseValue(stack).isPresent()) {
                slots.add(i);
            }
        }
        return slots;
    }

    public static boolean isTrading(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /**
     * Re-validates an entity-backed session's NPC immediately before a BUY/SELL commits (economy
     * hardening pass, Part 4): the entity must still exist, be alive and not removed, be the SAME
     * entity (UUID match, not merely a reused numeric id), share the player's current dimension,
     * be within the existing NPC interaction range, and — for a {@link TotalityNpcEntity} — still
     * have this player holding its interaction lock. Non-entity-backed sessions ({@code npcEntityId
     * == -1}) are exempt — there is deliberately no NPC to go stale for those.
     *
     * <p>On failure, ends the session (releasing any interaction lock and notifying the client)
     * so the caller can simply refuse to mutate anything and return.
     */
    private static boolean revalidateNpc(ServerPlayer player, ActiveTrade trade) {
        if (!trade.isEntityBacked()) return true;

        Entity npc = resolveRecordedNpc(player, trade);
        boolean valid = npc != null
                && npc.isAlive()
                && !npc.isRemoved()
                && player.level() == npc.level()
                && player.distanceToSqr(npc) <= TotalityNpcEntity.INTERACTION_RANGE_SQR
                && (!(npc instanceof TotalityNpcEntity totNpc) || totNpc.isInteractionLockOwnedBy(player));

        if (!valid) {
            endTrade(player);
        }
        return valid;
    }

    /**
     * Re-resolves the LIVE entity for an entity-backed session, for callers (BUY/SELL/state
     * updates) that need the CURRENT {@link MerchantRuntime}/{@link MerchantStockProvider}
     * instance rather than whatever was captured at session start (Phase 3 Part H) — a chunk
     * unload/reload cycle deserializes a NEW Java object sharing the old one's UUID, and a stored
     * reference would silently keep mutating the discarded instance instead. Callers must only
     * use this AFTER {@link #revalidateNpc} has already confirmed the session is still valid;
     * this performs the identical resolution again (cheap: a level/entity-id lookup, not worth
     * threading a return value through two call sites for this rarely-invoked, human-paced path).
     * Returns {@code null} for a non-entity-backed session — there is no entity to resolve.
     */
    @Nullable
    private static Entity currentNpc(ServerPlayer player, ActiveTrade trade) {
        return trade.isEntityBacked() ? resolveRecordedNpc(player, trade) : null;
    }

    /** The merchant to use for THIS transaction: the live-resolved entity itself if it implements
     *  {@link MerchantRuntime} (the Provisioner path), otherwise the session's originally captured
     *  {@link MerchantRuntime} (the shared/registry-backed path, which has no staleness problem —
     *  it isn't tied to a Java entity object at all). */
    private static MerchantRuntime currentMerchant(ActiveTrade trade, @Nullable Entity liveNpc) {
        return liveNpc instanceof MerchantRuntime liveMerchant ? liveMerchant : trade.merchant();
    }

    /** The stock provider to use for THIS transaction: the live-resolved entity's
     *  {@link MerchantStockProvider} if it has one (the real Provisioner path — always
     *  re-resolved live, never a captured reference), otherwise the session's originally captured
     *  {@code stockProvider} (the non-entity verification-only path,
     *  {@link #startStockBackedTradeForVerification}, which has no staleness problem since it
     *  isn't tied to a Java entity object at all). {@code null} for an ordinary template-backed
     *  session (including an entity-backed one whose NPC doesn't implement stock, e.g. the plain
     *  {@code test_trader} {@link TotalityNpcEntity}). */
    @Nullable
    private static MerchantStockProvider currentStockProvider(ActiveTrade trade, @Nullable Entity liveNpc) {
        if (liveNpc instanceof MerchantStockProvider provider) return provider;
        return trade.stockProvider();
    }

    /**
     * Resolves the entity an entity-backed {@link ActiveTrade} recorded — but ONLY if the
     * resolved entity's UUID still matches {@code trade.npcUuid()} (economy hardening
     * correction pass, Part 1). A numeric entity id can be reused by an unrelated entity after
     * the original NPC is removed; without this check, {@link #releaseTradePartner} could
     * resolve that REPLACEMENT entity by id alone and release ITS interaction lock instead of
     * correctly finding nothing. Centralizing UUID-safe resolution here means both
     * {@link #revalidateNpc} and {@link #releaseTradePartner} share the same guarantee.
     */
    @Nullable
    private static Entity resolveRecordedNpc(ServerPlayer player, ActiveTrade trade) {
        if (trade.npcDimension() == null || trade.npcUuid() == null) return null;
        Level npcLevel = player.level().getServer().getLevel(trade.npcDimension());
        if (npcLevel == null) return null;
        Entity npc = npcLevel.getEntity(trade.npcEntityId());
        return matchesUuid(npc, trade.npcUuid()) ? npc : null;
    }

    /**
     * True only if {@code candidate} is non-null and its UUID equals {@code expectedUuid} — the
     * exact guarantee {@link #resolveRecordedNpc} enforces before letting a caller (
     * {@link #revalidateNpc}, {@link #releaseTradePartner}) act on a resolved entity at all.
     * Extracted as its own package-visible predicate (economy hardening correction pass, Part 1)
     * so it can be verified directly against two real, distinct entity objects without depending
     * on a numeric id ever genuinely resolving to the wrong one through live level lookup — a
     * real id collision cannot be forced through the public entity-spawning API (ids come from a
     * monotonically increasing counter, never reused within one running server), and this suite
     * runs at {@code SERVER_STARTED}, before any tick has processed a freshly spawned entity into
     * {@code Level#getEntity(int)}'s lookup, so a live end-to-end resolution cannot be exercised
     * deterministically here either. Testing this predicate directly is sound because it is the
     * ONLY gate in {@code resolveRecordedNpc} that depends on comparing two UUIDs — the null and
     * level-lookup guards around it are simple, low-risk delegation to well-established APIs.
     */
    static boolean matchesUuid(@Nullable Entity candidate, UUID expectedUuid) {
        return candidate != null && expectedUuid.equals(candidate.getUUID());
    }

    private static void releaseTradePartner(ServerPlayer player, @Nullable ActiveTrade trade) {
        if (trade == null || !trade.isEntityBacked()) return;
        if (resolveRecordedNpc(player, trade) instanceof TotalityNpcEntity totNpc) {
            totNpc.releaseInteractionLock();
        }
    }

    private static void sendState(ServerPlayer player, boolean ended) {
        ActiveTrade trade = SESSIONS.get(player.getUUID());
        if (trade == null) return;

        long walletBalance = CurrencyComponents.WALLET.get((ComponentProvider) player).getValue();
        long physicalCredits = CreditPaymentHelper.physicalCredits(player);

        Entity liveNpc = currentNpc(player, trade);
        MerchantRuntime merchant = currentMerchant(trade, liveNpc);
        MerchantStockProvider stockProvider = currentStockProvider(trade, liveNpc);

        Component shopName = trade.template() != null ? Component.literal(trade.template().name()) : trade.npcName();
        List<ShopEntryDisplayData> display = new ArrayList<>();

        if (stockProvider != null) {
            PricingContext context = new PricingContext(
                    player, Optional.of(merchant.merchantId()), PricingDirection.RETAIL, Optional.empty());
            for (MerchantStockEntry entry : stockProvider.stockEntries()) {
                Optional<PriceQuote> quote;
                try {
                    quote = ItemPricingService.quoteRetail(entry.item(), 1, context);
                } catch (ArithmeticException overflow) {
                    quote = Optional.empty();
                }
                long unitPrice = quote.map(PriceQuote::unitPrice).orElse(0L);
                boolean affordable = quote.isPresent() && entry.currentStock() > 0
                        && CreditPaymentHelper.canAfford(player, unitPrice);
                display.add(new ShopEntryDisplayData(entry.item(), unitPrice, affordable, true, entry.currentStock()));
            }
        } else if (trade.template() != null) {
            for (ShopEntry entry : trade.template().sells()) {
                boolean affordable = CreditPaymentHelper.canAfford(player, entry.price());
                display.add(new ShopEntryDisplayData(entry.stack(), entry.price(), affordable));
            }
        }

        ServerPlayNetworking.send(player, new ShowShopStatePayload(
                trade.npcEntityId(),
                shopName,
                Component.translatable(merchant.archetypeTranslationKey()),
                display,
                walletBalance,
                physicalCredits,
                merchant.currentCredits(),
                computeValuedInventorySlots(player),
                ended
        ));
    }
}
