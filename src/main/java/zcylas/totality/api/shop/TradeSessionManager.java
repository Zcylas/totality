package zcylas.totality.api.shop;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.economy.currency.CreditPaymentHelper;
import zcylas.totality.api.economy.currency.CurrencyComponents;
import zcylas.totality.entity.npc.TotalityNpcEntity;
import zcylas.totality.networking.shop.ShopEntryDisplayData;
import zcylas.totality.networking.shop.ShowShopStatePayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side trade session tracking, mirroring {@link zcylas.totality.api.dialogue.DialogueSessionManager}'s
 * shape but without a branching state machine — a shop's catalog is static per session,
 * only the player's Credits/afford status changes between updates.
 */
public final class TradeSessionManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private record ActiveTrade(Identifier shopId, ShopTemplate template, int npcEntityId, Component npcName) {}

    private static final Map<UUID, ActiveTrade> SESSIONS = new HashMap<>();

    private TradeSessionManager() {}

    public static void startTrade(ServerPlayer player, Identifier shopId, @Nullable Entity npc) {
        ShopTemplate template = ShopRegistry.INSTANCE.get(shopId);
        if (template == null) {
            LOGGER.error("Shop not found: {}", shopId);
            return;
        }
        Component npcName = npc != null ? npc.getName() : Component.empty();
        int npcId = npc != null ? npc.getId() : -1;
        SESSIONS.put(player.getUUID(), new ActiveTrade(shopId, template, npcId, npcName));
        if (npc instanceof TotalityNpcEntity totNpc) {
            totNpc.setDialoguePartner(player);
        }
        sendState(player, false);
    }

    public static void handleBuy(ServerPlayer player, int index, int quantity) {
        ActiveTrade trade = SESSIONS.get(player.getUUID());
        if (trade == null) return;

        List<ShopEntry> sells = trade.template().sells();
        if (index < 0 || index >= sells.size()) return;

        quantity = Math.max(1, Math.min(100, quantity));

        ShopEntry entry = sells.get(index);
        long total = entry.price() * quantity;
        if (!CreditPaymentHelper.pay(player, total)) return;

        // Individual copies, not one stack multiplied by count — a full inventory drops
        // the remainder on the ground instead of blocking/rolling back the purchase.
        for (int i = 0; i < quantity; i++) {
            ItemStack gift = entry.stack().copy();
            if (!player.getInventory().add(gift)) {
                player.drop(gift, false);
            }
        }

        sendState(player, false);
    }

    public static void endTrade(ServerPlayer player) {
        ActiveTrade removed = SESSIONS.remove(player.getUUID());
        releaseTradePartner(player, removed);
        ServerPlayNetworking.send(player, new ShowShopStatePayload(
                -1, Component.empty(), List.of(), 0, 0, true
        ));
    }

    public static boolean isTrading(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    private static void releaseTradePartner(ServerPlayer player, @Nullable ActiveTrade trade) {
        if (trade == null || trade.npcEntityId() == -1) return;
        if (player.level().getEntity(trade.npcEntityId()) instanceof TotalityNpcEntity totNpc) {
            totNpc.setDialoguePartner(null);
        }
    }

    private static void sendState(ServerPlayer player, boolean ended) {
        ActiveTrade trade = SESSIONS.get(player.getUUID());
        if (trade == null) return;

        long walletBalance = CurrencyComponents.WALLET.get((ComponentProvider) player).getValue();
        long physicalCredits = CreditPaymentHelper.physicalCredits(player);

        List<ShopEntryDisplayData> display = new ArrayList<>();
        for (ShopEntry entry : trade.template().sells()) {
            boolean affordable = CreditPaymentHelper.canAfford(player, entry.price());
            display.add(new ShopEntryDisplayData(entry.stack(), entry.price(), affordable));
        }

        ServerPlayNetworking.send(player, new ShowShopStatePayload(
                trade.npcEntityId(),
                Component.literal(trade.template().name()),
                display,
                walletBalance,
                physicalCredits,
                ended
        ));
    }
}
