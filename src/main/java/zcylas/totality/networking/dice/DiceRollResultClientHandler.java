// networking/dice/DiceRollResultClientHandler.java
package zcylas.totality.networking.dice;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import zcylas.totality.api.dice.DiceRollResult;
import zcylas.totality.client.dice.DiceCheckSessions;
import zcylas.totality.client.dice.DiceRollTheme;
import zcylas.totality.screen.dice.DiceRollScreen;

import java.util.UUID;

/**
 * Client-side handler for the dice roll payloads. {@link DiceCheckSessions} decides everything: a request opens a
 * DiceRollScreen (over a dialogue that asked for it, as before), or — while another dice check is on screen — waits
 * in a queue that opens on the first tick with no screen at all, so a queued check never replaces the dialogue its
 * predecessor resumed and is never stranded behind it; a result reaches its own session's screen once, or —
 * if that screen was closed after rolling — the action bar once; foreign, repeated and stale results are ignored.
 */
public final class DiceRollResultClientHandler {

    private static final DiceCheckSessions SESSIONS = new DiceCheckSessions();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                DiceRollResultPayload.TYPE,
                (payload, ctx) -> {
                    ctx.client().execute(() -> {
                        Minecraft mc = Minecraft.getInstance();
                        DiceRollScreen screen = mc.gui.screen() instanceof DiceRollScreen s ? s : null;
                        boolean shows = screen != null && screen.sessionId().equals(payload.sessionId());
                        switch (SESSIONS.deliver(payload.sessionId(), shows)) {
                            case SCREEN -> screen.receiveResult(payload.sessionId(), payload.result());
                            case ACTION_BAR -> {
                                if (mc.player != null) mc.player.sendOverlayMessage(summary(payload.result()));
                            }
                            case IGNORE -> {}
                        }
                    });
                }
        );
    }

    public static void registerRequest() {
        ClientPlayNetworking.registerGlobalReceiver(
                DiceCheckRequestPayload.TYPE,
                (payload, ctx) -> {
                    ctx.client().execute(() -> {
                        if (SESSIONS.request(payload, Minecraft.getInstance().gui.screen() instanceof DiceRollScreen)) open(payload);
                    });
                }
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DiceCheckRequestPayload next = SESSIONS.next(client.player != null && client.gui.screen() == null);
            if (next != null) open(next);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(SESSIONS::clear));
    }

    /** A dice screen closed (any way): recorded so a result still on its way is shown once on the action bar. */
    public static void closed(UUID session, boolean rolled, boolean resulted) {
        SESSIONS.closed(session, rolled, resulted);
    }

    private static void open(DiceCheckRequestPayload payload) {
        Minecraft.getInstance().gui.setScreen(new DiceRollScreen(payload.sessionId(), payload.context()));
    }

    private static Component summary(DiceRollResult r) {
        return Component.literal(r.context().checkName() + ": " + r.total() + " vs DC " + r.context().dc() + " - "
                + DiceRollTheme.outcomeText(r.outcome())).withColor(DiceRollTheme.outcomeColor(r.outcome()));
    }

    private DiceRollResultClientHandler() {}
}
