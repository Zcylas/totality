package zcylas.totality.api.dialogue;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.DiceBonus;
import zcylas.totality.api.dice.DiceRollContext;
import zcylas.totality.api.dice.PendingDiceRollManager;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.check.SkillAbilityMap;
import zcylas.totality.api.rpg.combat.RollModifierRegistry;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.StatsComponents;
import zcylas.totality.networking.dialogue.ChoiceDisplayData;
import zcylas.totality.networking.dialogue.ShowDialogueStatePayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DialogueSessionManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private record ActiveDialogue(
            Identifier templateId,
            DialogueTemplate template,
            String currentStateKey,
            int npcEntityId,
            Component npcName
    ) {
        @Nullable DialogueState currentState() { return template.getState(currentStateKey); }

        ActiveDialogue withState(String key) {
            return new ActiveDialogue(templateId, template, key, npcEntityId, npcName);
        }
    }

    private static final Map<UUID, ActiveDialogue> SESSIONS = new HashMap<>();

    private DialogueSessionManager() {}

    public static void startDialogue(ServerPlayer player, Identifier dialogueId, @Nullable Entity npc) {
        DialogueTemplate template = DialogueRegistry.INSTANCE.get(dialogueId);
        if (template == null) {
            LOGGER.error("Dialogue not found: {}", dialogueId);
            return;
        }
        Component npcName = npc != null ? npc.getName() : Component.empty();
        int npcId = npc != null ? npc.getId() : -1;
        ActiveDialogue dialogue = new ActiveDialogue(dialogueId, template, template.start(), npcId, npcName);
        SESSIONS.put(player.getUUID(), dialogue);
        if (npc instanceof zcylas.totality.entity.npc.TotalityNpcEntity totNpc) {
            totNpc.setDialoguePartner(player);
        }
        sendState(player, dialogue, false);
    }

    public static void handleChoice(ServerPlayer player, int choiceIndex) {
        ActiveDialogue dialogue = SESSIONS.get(player.getUUID());
        if (dialogue == null) return;

        DialogueState state = dialogue.currentState();
        if (state == null) { endDialogue(player); return; }

        NarrativeFlagsComponent flags = DialogueComponents.FLAGS.get((ComponentProvider) player);
        List<IndexedChoice> visible = buildVisibleChoices(state, player, flags);

        if (choiceIndex < 0 || choiceIndex >= visible.size()) return;

        IndexedChoice chosen = visible.get(choiceIndex);
        DialogueChoice choice = chosen.choice();

        if (chosen.locked()) return;

        choice.action().ifPresent(a -> a.execute(player));

        if (choice.roll().isPresent()) {
            DiceRollSpec spec = choice.roll().get();
            AbilityScore governingScore = spec.ability()
                    .map(name -> {
                        try {
                            return AbilityScore.valueOf(name);
                        } catch (IllegalArgumentException e) {
                            LOGGER.warn("Dialogue roll for skill '{}' has invalid ability override '{}'", spec.skill(), name);
                            return null;
                        }
                    })
                    .orElseGet(() -> SkillAbilityMap.resolve(spec.skill()));
            AbilityScore scoreForModifiers = governingScore != null ? governingScore : AbilityScore.WIS;

            List<DiceBonus> bonuses = new ArrayList<>();
            if (governingScore != null) {
                int modifier = StatsComponents.getStats(player).getModifier(governingScore);
                bonuses.add(new DiceBonus(governingScore.getDisplayName(), modifier, governingScore.getIcon()));
            }
            // Collect active flat bonuses (e.g. Bless +1d4) for the roll context
            bonuses.addAll(RollModifierRegistry.resolveSaveBonusList(player, scoreForModifiers));

            DiceRollContext ctx = new DiceRollContext(
                    spec.skill(),
                    spec.subtype().isEmpty() ? spec.skill() + " Check" : spec.subtype(),
                    Dice.D20, spec.dc(), RollType.NORMAL, bonuses
            );
            PendingDiceRollManager.request(player, ctx, result -> {
                String nextKey = spec.resolveNext(result.outcome());
                advanceDialogue(player, dialogue, nextKey);
            });
        } else {
            choice.next().ifPresentOrElse(
                    key -> advanceDialogue(player, dialogue, key),
                    () -> endDialogue(player)
            );
        }
    }

    public static void endDialogue(ServerPlayer player) {
        ActiveDialogue removed = SESSIONS.remove(player.getUUID());
        releaseDialoguePartner(player, removed);
        ServerPlayNetworking.send(player, new ShowDialogueStatePayload(
                -1, Component.empty(), Component.empty(), List.of(), false, true
        ));
    }

    public static boolean isInDialogue(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    private static void advanceDialogue(ServerPlayer player, ActiveDialogue dialogue, String nextKey) {
        DialogueState nextState = dialogue.template().getState(nextKey);
        if (nextState == null) {
            LOGGER.warn("Dialogue {} has no state '{}'", dialogue.templateId(), nextKey);
            endDialogue(player);
            return;
        }
        if (nextState.type() == StateType.END) {
            SESSIONS.remove(player.getUUID());
            releaseDialoguePartner(player, dialogue);
            sendState(player, dialogue.withState(nextKey), true);
            return;
        }
        ActiveDialogue updated = dialogue.withState(nextKey);
        SESSIONS.put(player.getUUID(), updated);
        sendState(player, updated, false);
    }

    /** Stops forcing the NPC's look-at/freeze once its dialogue with this player is over. */
    private static void releaseDialoguePartner(ServerPlayer player, @Nullable ActiveDialogue dialogue) {
        if (dialogue == null || dialogue.npcEntityId() == -1) return;
        if (player.level().getEntity(dialogue.npcEntityId())
                instanceof zcylas.totality.entity.npc.TotalityNpcEntity totNpc) {
            totNpc.setDialoguePartner(null);
        }
    }

    private static void sendState(ServerPlayer player, ActiveDialogue dialogue, boolean ended) {
        DialogueState state = dialogue.currentState();
        if (state == null && !ended) { endDialogue(player); return; }

        NarrativeFlagsComponent flags = DialogueComponents.FLAGS.get((ComponentProvider) player);
        List<ChoiceDisplayData> displayChoices = new ArrayList<>();

        if (state != null) {
            List<IndexedChoice> visible = buildVisibleChoices(state, player, flags);
            for (IndexedChoice ic : visible) {
                displayChoices.add(new ChoiceDisplayData(ic.choice().text(), ic.locked(), ic.lockReason()));
            }
        }

        ServerPlayNetworking.send(player, new ShowDialogueStatePayload(
                dialogue.npcEntityId(),
                dialogue.npcName(),
                state != null ? state.text() : Component.empty(),
                displayChoices,
                dialogue.template().unskippable(),
                ended
        ));
    }

    private static List<IndexedChoice> buildVisibleChoices(
            DialogueState state, ServerPlayer player, NarrativeFlagsComponent flags) {
        List<IndexedChoice> result = new ArrayList<>();
        for (DialogueChoice choice : state.choices()) {
            boolean conditionsMet = choice.conditions().stream().allMatch(c -> c.test(player, flags));
            boolean locked = !conditionsMet;
            if (locked && choice.hidden()) continue;
            String lockReason = locked ? buildLockReason(choice, player, flags) : "";
            result.add(new IndexedChoice(choice, locked, lockReason));
        }
        return result;
    }

    private static String buildLockReason(DialogueChoice choice, ServerPlayer player, NarrativeFlagsComponent flags) {
        for (DialogueCondition condition : choice.conditions()) {
            if (!condition.test(player, flags)) return condition.lockReason();
        }
        return "";
    }

    private record IndexedChoice(DialogueChoice choice, boolean locked, String lockReason) {}
}
