package zcylas.totality.networking.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import zcylas.totality.api.magic.spell.Spell;
import zcylas.totality.api.magic.spell.SpellRegistry;
import zcylas.totality.item.spell_material.ArcaneFocusItem;
import zcylas.totality.networking.notification.SendNotificationPayload;

public final class CastFocusHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CastFocusPayload.TYPE,
                (payload, ctx) -> ctx.server().execute(() ->
                        handle(ctx.player(), payload)));
    }

    public static void handle(ServerPlayer player, CastFocusPayload payload) {
        Spell spell = SpellRegistry.get(Identifier.parse(payload.spellId()));
        if (spell == null) {
            SendNotificationPayload.send(player, "Unknown spell selected.", 0xFFFF4444);
            return;
        }

        if (!spell.canActivate(player, null)) return;

        // Play the focus item's custom cast sound (if any) before activating
        var mainHand = player.getMainHandItem();
        var offHand  = player.getOffhandItem();
        ArcaneFocusItem focus = null;
        if (mainHand.getItem() instanceof ArcaneFocusItem f) focus = f;
        else if (offHand.getItem() instanceof ArcaneFocusItem f) focus = f;

        if (focus != null && focus.getCastSound() != null) {
            player.level().playSound(null,
                    player.getX(), player.getY(), player.getZ(),
                    focus.getCastSound(), SoundSource.PLAYERS,
                    1.0f, 1.0f);
        }

        spell.onActivate(player, null);
    }

    private CastFocusHandler() {}
}