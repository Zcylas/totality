package zcylas.totality.item.magic.rune;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import zcylas.totality.api.magic.rune.AbstractRune;
import zcylas.totality.api.magic.rune.RuneComponents;
import zcylas.totality.api.magic.rune.RuneKnowledgeComponent;
import zcylas.totality.networking.notification.SendNotificationPayload;

public class RuneItem extends Item {

    private final AbstractRune rune;

    public RuneItem(AbstractRune rune, Properties properties) {
        super(properties);
        this.rune = rune;
    }

    public AbstractRune getRune() { return rune; }

    @Override
    public InteractionResult use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        ServerPlayer serverPlayer = (ServerPlayer) player;
        RuneKnowledgeComponent knowledge = RuneComponents.KNOWLEDGE.get(
                (zcylas.totality.api.core.component.ComponentProvider) serverPlayer
        );

        String runeId = rune.getId().getPath();

        if (knowledge.knowsRune(runeId)) {
            SendNotificationPayload.send(
                    serverPlayer,
                    "You already know the " + rune.getName() + " rune.",
                    0xFF5599BB
            );
            return InteractionResult.FAIL;
        }

        knowledge.learnRune(runeId);
        knowledge.sync();

        player.getItemInHand(hand).shrink(1);

        SendNotificationPayload.send(
                serverPlayer,
                "Rune learned: " + rune.getName(),
                0xFF00CCFF
        );

        return InteractionResult.SUCCESS;
    }
}