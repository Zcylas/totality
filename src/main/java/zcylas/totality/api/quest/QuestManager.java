package zcylas.totality.api.quest;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import zcylas.totality.Totality;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.dialogue.DialogueComponents;
import zcylas.totality.api.dialogue.NarrativeFlagsComponent;
import zcylas.totality.api.equipment.EquipmentComponents;
import zcylas.totality.api.equipment.PlayerEquipmentComponent;
import zcylas.totality.api.item.TotalityItemComponents;
import zcylas.totality.init.items.CurrencyItems;
import zcylas.totality.init.items.EnergyItems;
import zcylas.totality.item.energy.PhoneItem;
import zcylas.totality.networking.notification.SendNotificationPayload;
import zcylas.totality.networking.quest.QuestEntryDisplayData;
import zcylas.totality.networking.quest.ShowQuestStatePayload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Server-side quest logic — ties {@link QuestTemplate} (static, data-driven) to
 * {@link QuestProgressComponent} (per-player, persistent). Objective-completion triggers
 * are hardcoded per quest for this first pass (First Signal only) rather than a generic
 * event/trigger system — revisit if/when more quests need programmatic objectives.
 */
public final class QuestManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Identifier FIRST_SIGNAL =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "first_signal");
    public static final Identifier MOBILE_BANKING =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "mobile_banking");

    // First Signal's objective indices — matches data/totality/quests/first_signal.json's order.
    private static final int OBJ_SETUP = 0;
    private static final int OBJ_EQUIP = 1;
    private static final int OBJ_OPEN_APP = 2;

    // Mobile Banking's (only) objective index — matches data/totality/quests/mobile_banking.json.
    private static final int OBJ_LINK_PHONE = 0;

    private QuestManager() {}

    public static void grantIfMissing(ServerPlayer player, Identifier questId) {
        QuestTemplate template = QuestRegistry.INSTANCE.get(questId);
        if (template == null) {
            LOGGER.warn("[Quest] grantIfMissing({}) — no template found in QuestRegistry", questId);
            return;
        }
        QuestProgressComponent progress = QuestComponents.PROGRESS.get((ComponentProvider) player);
        boolean already = progress.has(questId);
        if (already) {
            progress.resizeObjectives(questId, template.objectives().size());
        } else {
            progress.grant(questId, template.objectives().size());
            progress.setTracked(questId, true);
            SendNotificationPayload.send(player, "New Quest: " + template.name(), SendNotificationPayload.GOLD);
            if (questId.equals(FIRST_SIGNAL)) {
                grantStarterPhoneIfMissing(player);
            }
        }
        LOGGER.info("[Quest] grantIfMissing({}) for {} — alreadyHad={}, nowHas={}",
                questId, player.getName().getString(), already, progress.has(questId));

        // Pushes the fresh tracked=true state to the client immediately — without this, a
        // grant that doesn't happen to be followed by some other push (e.g. onPlayerJoin's
        // own unconditional push) leaves the HUD tracker showing stale/no data until the
        // player manually opens the Quests app or re-toggles tracking. Found via
        // GrantQuestAction's mid-dialogue grant, which had no such follow-up push.
        pushUpdate(player);
    }

    /** Only actually completes {@code index} if every prior objective is already done —
     *  objectives must be finished in order. If the player did the action for a later
     *  objective early (e.g. equipped the phone before finishing setup), this call is
     *  ignored; once the blocking prior objective completes, {@code cascadeFirstSignal}
     *  re-checks whatever real-world state condition backs the next objective, so the
     *  player isn't forced to physically redo the action. */
    public static void completeObjective(ServerPlayer player, Identifier questId, int index) {
        QuestTemplate template = QuestRegistry.INSTANCE.get(questId);
        if (template == null) return;
        QuestProgressComponent progress = QuestComponents.PROGRESS.get((ComponentProvider) player);
        if (!progress.has(questId)) {
            LOGGER.warn("[Quest] completeObjective({}, {}) for {} — player doesn't have this quest",
                    questId, index, player.getName().getString());
            return;
        }
        QuestProgressComponent.QuestState state = progress.get(questId);
        if (state.status != QuestProgressComponent.Status.ACTIVE) return;

        int nextRequired = nextObjectiveIndex(state.objectives);
        if (index != nextRequired) {
            LOGGER.info("[Quest] completeObjective({}, {}) for {} — skipped, objectives must be done in order (next required is {})",
                    questId, index, player.getName().getString(), nextRequired);
            return;
        }

        boolean readyNow = progress.setObjective(questId, index, true);
        LOGGER.info("[Quest] completeObjective({}, {}) for {} — questReadyNow={}",
                questId, index, player.getName().getString(), readyNow);
        if (readyNow) {
            SendNotificationPayload.send(player, "Quest Ready: " + template.name(), SendNotificationPayload.GOLD);
        } else if (questId.equals(FIRST_SIGNAL)) {
            cascadeFirstSignal(player);
        }
    }

    private static int nextObjectiveIndex(boolean[] objectives) {
        for (int i = 0; i < objectives.length; i++) if (!objectives[i]) return i;
        return objectives.length;
    }

    /** After an objective completes, checks whether the next objective's real-world
     *  condition is already true (e.g. the phone was equipped before setup finished)
     *  and completes it immediately instead of waiting for a fresh trigger event. */
    private static void cascadeFirstSignal(ServerPlayer player) {
        QuestProgressComponent progress = QuestComponents.PROGRESS.get((ComponentProvider) player);
        if (!progress.has(FIRST_SIGNAL)) return;
        QuestProgressComponent.QuestState state = progress.get(FIRST_SIGNAL);
        if (state.status != QuestProgressComponent.Status.ACTIVE) return;

        int next = nextObjectiveIndex(state.objectives);
        if (next == OBJ_SETUP && playerHasSetUpPhone(player)) {
            completeObjective(player, FIRST_SIGNAL, OBJ_SETUP);
        } else if (next == OBJ_EQUIP && playerHasPhoneEquipped(player)) {
            completeObjective(player, FIRST_SIGNAL, OBJ_EQUIP);
        }
    }

    /** Player pressed "Finish Quest" in the Quests app. Only grants rewards and marks the quest
     *  COMPLETED if every objective was already done (status READY) — completion is an explicit
     *  player action, not automatic, so the player actually sees the "all done" state first. */
    public static void finishQuest(ServerPlayer player, Identifier questId) {
        QuestTemplate template = QuestRegistry.INSTANCE.get(questId);
        if (template == null) return;
        QuestProgressComponent progress = QuestComponents.PROGRESS.get((ComponentProvider) player);

        boolean finished = progress.finish(questId);
        LOGGER.info("[Quest] finishQuest({}) for {} — finished={}", questId, player.getName().getString(), finished);
        if (finished) {
            grantReward(player, template.reward());
            SendNotificationPayload.send(player, "Quest Complete: " + template.name(), SendNotificationPayload.GREEN);
        }
    }

    /** Clears a quest's progress AND every narrative flag it declares under its
     *  {@code reset_flags} JSON list — the "start completely from scratch" reset that
     *  {@code /totality quest reset} alone doesn't give, since that only touches
     *  {@code QuestProgressComponent} and leaves any flags the quest itself set (e.g.
     *  "already offered") stale. Generic by design so any quest — including future daily
     *  quests — can declare whichever flags its own dialogue/logic needs cleared, without
     *  new code here. */
    public static void fullReset(ServerPlayer player, Identifier questId) {
        QuestTemplate template = QuestRegistry.INSTANCE.get(questId);
        QuestComponents.PROGRESS.get((ComponentProvider) player).remove(questId);
        if (template != null) {
            NarrativeFlagsComponent flags = DialogueComponents.FLAGS.get((ComponentProvider) player);
            for (String flag : template.resetFlags()) flags.clearFlag(flag);
        }
        pushUpdate(player);
    }

    public static void setTracked(ServerPlayer player, Identifier questId, boolean tracked) {
        QuestComponents.PROGRESS.get((ComponentProvider) player).setTracked(questId, tracked);
    }

    /** Resolves every quest the player knows about (active or completed) into display data. */
    public static List<QuestEntryDisplayData> buildDisplay(ServerPlayer player) {
        QuestProgressComponent progress = QuestComponents.PROGRESS.get((ComponentProvider) player);
        List<QuestEntryDisplayData> result = new ArrayList<>();
        for (var entry : progress.all().entrySet()) {
            QuestTemplate template = QuestRegistry.INSTANCE.get(entry.getKey());
            if (template == null) continue;
            QuestProgressComponent.QuestState state = entry.getValue();

            // Defensive: never let a stale/mismatched objectives array reach the network codec
            // (it expects exactly template.objectives().size() booleans) — a template whose
            // objective count changed after a player already had the quest caused a client
            // disconnect (DecoderException) here before this clamp existed. resizeObjectives()
            // in grantIfMissing() is the primary fix; this is a second line of defense.
            boolean[] objectivesDone = state.objectives;
            if (objectivesDone.length != template.objectives().size()) {
                objectivesDone = Arrays.copyOf(objectivesDone, template.objectives().size());
            }

            result.add(new QuestEntryDisplayData(
                    entry.getKey().toString(),
                    template.name(),
                    template.description(),
                    template.type(),
                    template.objectives(),
                    objectivesDone,
                    state.tracked,
                    state.status == QuestProgressComponent.Status.READY,
                    state.status == QuestProgressComponent.Status.COMPLETED,
                    template.reward().credits(),
                    template.reward().xp()
            ));
        }
        LOGGER.info("[Quest] buildDisplay for {} — {} quest(s)", player.getName().getString(), result.size());
        return result;
    }

    /** Pushes a fresh snapshot to the client without opening/forcing any screen — the client
     *  only opens {@code QuestScreen} in response to an explicit request it made itself.
     *  This is what keeps the HUD tracker's cache current after background changes
     *  (join, equip, phone setup) that don't happen from inside the Quest screen. */
    public static void pushUpdate(ServerPlayer player) {
        ServerPlayNetworking.send(player, new ShowQuestStatePayload(buildDisplay(player)));
    }

    private static void grantReward(ServerPlayer player, QuestReward reward) {
        if (reward.credits() > 0) {
            for (ItemStack stack : CurrencyItems.CREDITS.createStacks(reward.credits())) {
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            }
        }
        if (reward.xp() > 0) {
            // Character XP (the RPG level system, cap 150), not vanilla orb XP —
            // ExperienceFunctions.addPlayerXP is for the unrelated skill-cost XP system.
            zcylas.totality.api.rpg.stats.PlayerStats stats = zcylas.totality.api.rpg.stats.StatsComponents.getStats(player);
            stats.addCharacterXp(reward.xp());
            zcylas.totality.api.rpg.stats.StatsComponents.get(player).sync();
        }
    }

    // ── Mobile Banking hooks ─────────────────────────────────────────────────

    /** True if the player has Mobile Banking active and hasn't yet completed its (only)
     *  objective — gates {@code BankerNpcEntity}'s phone-hand-off interaction so it only
     *  fires while the quest is actually waiting on it. */
    public static boolean isPhoneLinkPending(ServerPlayer player) {
        QuestProgressComponent progress = QuestComponents.PROGRESS.get((ComponentProvider) player);
        if (!progress.has(MOBILE_BANKING)) return false;
        QuestProgressComponent.QuestState state = progress.get(MOBILE_BANKING);
        return state.status == QuestProgressComponent.Status.ACTIVE && !state.objectives[OBJ_LINK_PHONE];
    }

    /** Objective 0 ("Give your phone to the Banker") — completed via {@code BankerNpcEntity}'s
     *  phone-hand-off interaction. Unlocks the Bank app immediately (not gated behind the
     *  manual Quest-app turn-in) — the functional payoff of "you can now bank remotely"
     *  shouldn't wait behind an unrelated menu click, even though the XP reward still does. */
    public static void onPhoneLinkedWithBanker(ServerPlayer player) {
        completeObjective(player, MOBILE_BANKING, OBJ_LINK_PHONE);
        DialogueComponents.FLAGS.get((ComponentProvider) player).setFlag("bank_app_unlocked", 1);
        pushUpdate(player);
    }

    // ── First Signal hooks ───────────────────────────────────────────────────

    /** Objective 0 ("Equip the phone") — completed via {@code PlayerEquipmentComponent.setItem}. */
    public static void onPhoneEquipped(ServerPlayer player) {
        completeObjective(player, FIRST_SIGNAL, OBJ_EQUIP);
        pushUpdate(player);
    }

    /** Objective 1 ("Check the device") — completed via {@code PhoneSetupHandler}. */
    public static void onPhoneSetupComplete(ServerPlayer player) {
        completeObjective(player, FIRST_SIGNAL, OBJ_SETUP);
        pushUpdate(player);
    }

    /** Objective 2 ("Open the Quest app") — completed via the Quests app tile.
     *  Not pushed here — {@code OpenQuestAppHandler} already sends its own snapshot
     *  right after calling this, opening the screen with it. */
    public static void onQuestAppOpened(ServerPlayer player) {
        completeObjective(player, FIRST_SIGNAL, OBJ_OPEN_APP);
    }

    /** Grants First Signal on join if missing. Also retroactively completes objectives
     *  for players who already equipped/set up their phone before ever being granted the quest.
     *  Always pushes a snapshot afterward so the HUD tracker has data without the player
     *  ever having opened the phone. */
    public static void onPlayerJoin(ServerPlayer player) {
        grantIfMissing(player, FIRST_SIGNAL);
        if (playerHasSetUpPhone(player)) {
            completeObjective(player, FIRST_SIGNAL, OBJ_SETUP);
        }
        if (playerHasPhoneEquipped(player)) {
            completeObjective(player, FIRST_SIGNAL, OBJ_EQUIP);
        }
        pushUpdate(player);
    }

    /** Fresh First Signal grants used to assume the player already had a phone from some other
     *  "starter flow" — no such flow actually exists, so a genuinely new player had nothing to
     *  set up or equip. Gives one Basic Copper Phone directly, but only if the player doesn't
     *  already have one anywhere (equipped or in inventory), so this never duplicates. */
    private static void grantStarterPhoneIfMissing(ServerPlayer player) {
        if (playerHasAnyPhone(player)) return;
        ItemStack phone = new ItemStack(EnergyItems.BASIC_COPPER_PHONE);
        if (!player.getInventory().add(phone)) {
            player.drop(phone, false);
        }
    }

    public static boolean playerHasAnyPhone(ServerPlayer player) {
        if (EquipmentComponents.get(player).getItem(PlayerEquipmentComponent.IDX_PHONE).getItem() instanceof PhoneItem) {
            return true;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof PhoneItem) return true;
        }
        return false;
    }

    private static boolean playerHasPhoneEquipped(ServerPlayer player) {
        return EquipmentComponents.get(player).getItem(PlayerEquipmentComponent.IDX_PHONE).getItem() instanceof PhoneItem;
    }

    private static boolean playerHasSetUpPhone(ServerPlayer player) {
        ItemStack equipped = EquipmentComponents.get(player).getItem(PlayerEquipmentComponent.IDX_PHONE);
        if (isSetUpPhone(equipped)) return true;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isSetUpPhone(player.getInventory().getItem(i))) return true;
        }
        return false;
    }

    private static boolean isSetUpPhone(ItemStack stack) {
        return stack.getItem() instanceof PhoneItem
                && Boolean.TRUE.equals(stack.get(TotalityItemComponents.PHONE_SETUP_COMPLETE));
    }
}
