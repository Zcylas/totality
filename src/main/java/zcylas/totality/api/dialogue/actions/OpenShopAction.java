package zcylas.totality.api.dialogue.actions;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import zcylas.totality.api.dialogue.DialogueAction;
import zcylas.totality.api.dialogue.DialogueSessionManager;
import zcylas.totality.api.shop.TradeSessionManager;

/**
 * Server-authoritative bridge between a trade-oriented dialogue choice and Economy's
 * existing {@link TradeSessionManager}/Trading screen — this is the minimal
 * {@code OpenShopAction(shopId)} closing audit finding ECON-13 (trading previously
 * bypassed Dialogue entirely via {@code TotalityNpcEntity.shopId}).
 *
 * <p>{@code shopId} comes from the authored dialogue data, never from client-supplied
 * state, and {@link TradeSessionManager#startTrade} already validates it against
 * {@link zcylas.totality.api.shop.ShopRegistry} and fails safely (logs, no-op) if
 * unknown — this action does not duplicate that validation, only reuses it.
 */
public record OpenShopAction(Identifier shopId) implements DialogueAction {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final MapCodec<OpenShopAction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("shop_id").forGetter(OpenShopAction::shopId)
    ).apply(i, OpenShopAction::new));

    @Override
    public void execute(ServerPlayer player) {
        // Dialogue actions only ever fire from DialogueSessionManager.handleChoice, which
        // already requires an active session — this check is defense-in-depth against any
        // future/alternate caller invoking the action outside that context.
        if (!DialogueSessionManager.isInDialogue(player)) {
            LOGGER.warn("OpenShopAction({}) fired for {} outside an active dialogue session — ignoring.",
                    shopId, player.getName().getString());
            return;
        }
        Entity npc = DialogueSessionManager.getActiveNpc(player);
        TradeSessionManager.startTrade(player, shopId, npc);
    }

    @Override
    public String type() { return "open_shop"; }
}
