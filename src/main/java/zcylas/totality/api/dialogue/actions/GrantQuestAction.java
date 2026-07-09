package zcylas.totality.api.dialogue.actions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.dialogue.DialogueAction;
import zcylas.totality.api.quest.QuestManager;

/** Grants a quest via {@link QuestManager#grantIfMissing} — idempotent if the player
 *  already has it. */
public record GrantQuestAction(Identifier questId) implements DialogueAction {
    public static final MapCodec<GrantQuestAction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("quest").forGetter(GrantQuestAction::questId)
    ).apply(i, GrantQuestAction::new));

    @Override
    public void execute(ServerPlayer player) {
        QuestManager.grantIfMissing(player, questId);
    }

    @Override
    public String type() { return "grant_quest"; }
}
