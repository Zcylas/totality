package zcylas.totality.api.dialogue.actions;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import zcylas.totality.api.dialogue.DialogueAction;
import zcylas.totality.api.dialogue.DialogueSessionManager;
import zcylas.totality.api.shop.TradeSessionManager;

/**
 * Server-authoritative bridge between a Provisioner's trade-oriented dialogue choice and
 * {@link TradeSessionManager#startEntityBackedTrade} (design document Phase 3, Part D/H) — the
 * entity-backed counterpart to {@link OpenShopAction}, which resolves a SHARED
 * {@code shop_id}/{@code ShopTemplate} and is deliberately NOT used for Provisioners (a
 * Provisioner has no shop id at all — its BUY entries come from its own per-entity
 * {@link zcylas.totality.api.shop.MerchantStockProvider}, not a datapack-authored catalog).
 * Takes no parameters: the NPC the active dialogue session is already with (via
 * {@link DialogueSessionManager#getActiveNpc}) carries all the state this action needs.
 */
public record OpenProvisionerShopAction() implements DialogueAction {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final MapCodec<OpenProvisionerShopAction> MAP_CODEC =
            MapCodec.unit(OpenProvisionerShopAction::new);

    @Override
    public void execute(ServerPlayer player) {
        // Dialogue actions only ever fire from DialogueSessionManager.handleChoice, which
        // already requires an active session — this check is defense-in-depth against any
        // future/alternate caller invoking the action outside that context.
        if (!DialogueSessionManager.isInDialogue(player)) {
            LOGGER.warn("OpenProvisionerShopAction fired for {} outside an active dialogue session — ignoring.",
                    player.getName().getString());
            return;
        }
        Entity npc = DialogueSessionManager.getActiveNpc(player);
        if (npc == null) {
            LOGGER.warn("OpenProvisionerShopAction fired for {} with no active dialogue NPC — ignoring.",
                    player.getName().getString());
            return;
        }
        TradeSessionManager.startEntityBackedTrade(player, npc);
    }

    @Override
    public String type() { return "open_provisioner_shop"; }
}
