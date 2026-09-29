package zcylas.totality.client.hologram.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.api.dialogue.DialogueSessionManager;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.DiceBonus;
import zcylas.totality.api.dice.DiceRollContext;
import zcylas.totality.api.dice.DiceRollResult;
import zcylas.totality.api.dice.PendingDiceRollManager;
import zcylas.totality.api.dice.RollOutcome;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.combat.SavingThrow;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.client.dice.D20Geometry;
import zcylas.totality.client.dice.DiceRollPresentation;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.screen.dialogue.DialogueScreen;
import zcylas.totality.screen.dice.DiceRollScreen;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Creative Test J footage for the opt-in capture run (inert in normal play): scene 61, Dice Roll UI V2 ({@code DR_}).
 * Each scenario asks the integrated server for a real player-facing check (a real NPC dialogue Persuasion check, or
 * {@link PendingDiceRollManager} / {@link SavingThrow} requests), rolls it through the screen's own input handlers (a
 * click on the die, E, Esc), photographs the whole sequence, and logs PASS/FAIL comparisons of the screen with the
 * server's result. The natural values are chosen by seeding the player's server-side random just before the click;
 * the roll itself is the unchanged {@code DiceRoller}.
 */
final class DiceRollCapture {

    private DiceRollCapture() {}

    private static final AtomicInteger CALLBACKS = new AtomicInteger();
    private static final AtomicReference<DiceRollResult> SERVER_RESULT = new AtomicReference<>();

    private enum Drive { CLICK, SKIP_MID_ROLL, ESC_BEFORE_ROLL }

    static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        s.add(HologramCapture.command("gamerule spawn_mobs false"));
        s.add(HologramCapture.command("gamerule send_command_feedback false"));
        s.add(HologramCapture.command("time set 13000"));
        s.add(HologramCapture.command("weather clear"));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(HologramCapture.look(35f, 8f));
        s.add(HologramCapture.run("allow screens", () -> HologramCapture.allowScreens = true));
        s.add(HologramCapture.waitTicks(20));

        DiceRollContext fail = context("Athletics", "Strength Check", 15, RollType.NORMAL, new DiceBonus("Strength", 2, "⚔"), new DiceBonus("Proficiency", 2));
        DiceRollContext nat = context("Deception", "Charisma Check", 14, RollType.NORMAL, new DiceBonus("Charisma", 3, "★"), new DiceBonus("Proficiency", 2));

        dialogueScenario(s, "A_dialogue_success", 16);
        scenario(s, "B_failure", server -> request(server, fail), new int[]{6}, Drive.CLICK, 2);
        continueAndCheck(s, "B_failure");
        scenario(s, "C_natural_20", server -> request(server, nat), new int[]{20}, Drive.CLICK, 2);
        duplicateDelivery(s);
        continueAndCheck(s, "C_natural_20");
        scenario(s, "D_natural_1", server -> request(server, nat), new int[]{1}, Drive.CLICK, 2);
        continueAndCheck(s, "D_natural_1");
        scenario(s, "E_advantage", server -> save(server, RollType.ADVANTAGE), new int[]{8, 17}, Drive.CLICK, 2);
        continueAndCheck(s, "E_advantage");
        scenario(s, "F_disadvantage", server -> save(server, RollType.DISADVANTAGE), new int[]{14, 5}, Drive.CLICK, 2);
        continueAndCheck(s, "F_disadvantage");
        scenario(s, "G_skip_mid_roll", server -> request(server, fail), new int[]{11}, Drive.SKIP_MID_ROLL, 1);
        continueAndCheck(s, "G_skip_mid_roll");
        scenario(s, "H_esc_before_roll", server -> request(server, nat), new int[]{9}, Drive.ESC_BEFORE_ROLL, 1);
        continueAndCheck(s, "H_esc_before_roll");
        queuedBehindDialogue(s, fail);

        s.add(HologramCapture.run("screens back to normal", () -> HologramCapture.allowScreens = false));
        s.add(HologramCapture.command("time set 6000"));
        return s;
    }

    private static DiceRollContext context(String name, String subtype, int dc, RollType type, DiceBonus... bonuses) {
        return new DiceRollContext(name, subtype, Dice.D20, dc, type, List.of(bonuses));
    }

    private static void request(MinecraftServer server, DiceRollContext ctx) {
        PendingDiceRollManager.request(me(server), ctx, result -> {
            CALLBACKS.incrementAndGet();
            SERVER_RESULT.set(result);
        });
    }

    private static void save(MinecraftServer server, RollType type) {
        SavingThrow.request(me(server), AbilityScore.WIS, 13, "Ancient Curse", type, result -> {
            CALLBACKS.incrementAndGet();
            SERVER_RESULT.set(result);
        });
    }

    // ── scenarios ───────────────────────────────────────────────────────────────────────────────────────────────

    /** A real NPC dialogue: the example trader's "[Persuasion DC 12] Could I get a small discount?" choice. */
    private static void dialogueScenario(List<Step> s, String key, int natural) {
        s.add(reset());
        s.add(onServer(server -> DialogueSessionManager.startDialogue(me(server), Identifier.fromNamespaceAndPath("totality", "example_trader"), null)));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.screenshot("DR_" + key + "_000_dialogue"));
        s.add(onServer(server -> DialogueSessionManager.handleChoice(me(server), 2)));
        s.add(HologramCapture.until("dice screen opened from the dialogue", () -> screen() != null, () -> {}, 60));
        rollAndRecord(s, key, new int[]{natural}, Drive.CLICK, 1, true);
        s.add(HologramCapture.check(key + ": the dialogue check rolled natural " + natural + " on the server (seeded) and the screen shows it",
                () -> screen() != null && screen().presentation().result() != null && screen().presentation().result().usedRoll() == natural));
        s.add(pressKey(GLFW.GLFW_KEY_E));
        s.add(HologramCapture.waitTicks(15));
        s.add(HologramCapture.check(key + ": Continue closed the roll and the dialogue went on (its success node)", () ->
                Minecraft.getInstance().gui.screen() instanceof DialogueScreen));
        s.add(HologramCapture.screenshot("DR_" + key + "_999_dialogue_after"));
        s.add(onServer(server -> DialogueSessionManager.endDialogue(me(server))));
        s.add(HologramCapture.run("close", () -> Minecraft.getInstance().gui.setScreen(null)));
        s.add(HologramCapture.waitTicks(10));
    }

    private static void scenario(List<Step> s, String key, Consumer<MinecraftServer> request, int[] naturals, Drive drive, int every) {
        s.add(reset());
        s.add(onServer(request));
        s.add(HologramCapture.until("dice screen opened", () -> screen() != null, () -> {}, 60));
        rollAndRecord(s, key, naturals, drive, every, false);
        s.add(HologramCapture.check(key + ": the server rolled " + Arrays.toString(naturals) + " (seeded) and fired its callback exactly once",
                () -> CALLBACKS.get() == 1 && SERVER_RESULT.get() != null && SERVER_RESULT.get().roll1() == naturals[0]
                        && (naturals.length == 1 || SERVER_RESULT.get().roll2() == naturals[1])));
        s.add(HologramCapture.check(key + ": the screen shows exactly the server's result (dice faces, kept natural, total, DC, outcome)", () -> {
            DiceRollScreen screen = screen();
            DiceRollResult server = SERVER_RESULT.get();
            if (screen == null || server == null) return false;
            DiceRollPresentation p = screen.presentation();
            long now = Util.getMillis();
            DiceRollResult shown = p.result();
            boolean faces = D20Geometry.frontNumber(p.dice()[0].orientation(now)) == server.roll1()
                    && (p.dice().length == 1 || D20Geometry.frontNumber(p.dice()[1].orientation(now)) == server.roll2());
            HologramCapture.log(String.format(Locale.ROOT, "%s server: roll1 %d roll2 %d kept %d bonus %d total %d DC %d %s | shown: kept %d total %d %s",
                    key, server.roll1(), server.roll2(), server.usedRoll(), server.totalBonus(), server.total(), server.context().dc(),
                    server.outcome(), p.naturalShown(now), p.totalShown(now), shown == null ? "-" : shown.outcome()));
            return shown != null && shown.equals(server) && faces && p.naturalShown(now) == server.usedRoll()
                    && p.totalShown(now) == server.total() && p.canContinue(now);
        }));
    }

    /** Opening, idle, the roll (driven as asked), then frames until Continue is offered. */
    private static void rollAndRecord(List<Step> s, String key, int[] naturals, Drive drive, int every, boolean dialogue) {
        int[] frame = {1};
        for (int i = 0; i < 7; i++) s.add(shot(key, frame));
        s.add(hoverDie());
        for (int i = 0; i < 6; i++) {
            s.add(HologramCapture.waitTicks(every));
            s.add(shot(key, frame));
        }
        Step seed = seed(naturals);
        switch (drive) {
            case CLICK, SKIP_MID_ROLL -> s.add(mc -> {
                seed.tick(mc);
                int[] die = dieCentre();
                screen().mouseClicked(new MouseButtonEvent(die[0], die[1], new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)), false);
                HologramCapture.log("click: the die at " + die[0] + "," + die[1]);
                return true;
            });
            case ESC_BEFORE_ROLL -> s.add(mc -> {
                seed.tick(mc);
                screen().keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                HologramCapture.log("press: Esc before rolling");
                return true;
            });
        }
        if (drive == Drive.SKIP_MID_ROLL) {
            for (int i = 0; i < 6; i++) s.add(shot(key, frame));
            s.add(pressKey(GLFW.GLFW_KEY_E));
            s.add(HologramCapture.check(key + ": E mid-tumble skipped to the final frame at once", () -> {
                DiceRollScreen screen = screen();
                return screen != null && (screen.presentation().canContinue(Util.getMillis()) || screen.presentation().awaitingServer());
            }));
        }
        int[] ticks = {0};
        int[] after = {0};
        s.add(mc -> {
            DiceRollScreen screen = screen();
            if (screen == null) return true;
            if (ticks[0]++ % every == 0) shot(key, frame).tick(mc);
            if (screen.presentation().canContinue(Util.getMillis())) after[0]++;
            return after[0] > 16 || ticks[0] > 400;
        });
        s.add(shot(key, frame));
        s.add(mc -> {
            HologramCapture.log(key + ": " + (frame[0] - 1) + " frames" + (dialogue ? " (dialogue)" : ""));
            return true;
        });
    }

    /**
     * Fix A: a dialogue check is open; a second check arrives and is queued; the first completes and resumes the
     * dialogue; the queued check waits behind the dialogue (never replacing it), then opens once the dialogue is left,
     * and resolves exactly once.
     */
    private static void queuedBehindDialogue(List<Step> s, DiceRollContext second) {
        String key = "I_queued_behind_dialogue";
        s.add(reset());
        s.add(onServer(server -> DialogueSessionManager.startDialogue(me(server), Identifier.fromNamespaceAndPath("totality", "example_trader"), null)));
        s.add(HologramCapture.waitTicks(20));
        s.add(onServer(server -> DialogueSessionManager.handleChoice(me(server), 2)));
        s.add(HologramCapture.until("first (dialogue) check opened", () -> screen() != null, () -> {}, 60));
        UUID[] first = new UUID[1];
        s.add(mc -> {
            first[0] = screen().sessionId();
            return true;
        });
        s.add(onServer(server -> request(server, second)));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.check(key + ": the second check was queued behind the open one (the first stays on screen)", () ->
                screen() != null && screen().sessionId().equals(first[0])));
        s.add(HologramCapture.screenshot("DR_" + key + "_01_first_open_second_queued"));
        s.add(mc -> {
            seed(new int[]{15}).tick(mc);
            screen().keyPressed(new KeyEvent(GLFW.GLFW_KEY_E, 0, 0));
            return true;
        });
        s.add(HologramCapture.until("first check final", () -> screen() != null && screen().presentation().canContinue(Util.getMillis()), () -> {}, 200));
        s.add(pressKey(GLFW.GLFW_KEY_E));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.check(key + ": the first check completed and resumed the dialogue; the queued check did not replace it", () ->
                Minecraft.getInstance().gui.screen() instanceof DialogueScreen && CALLBACKS.get() == 0));
        s.add(HologramCapture.screenshot("DR_" + key + "_02_dialogue_resumed_check_waiting"));
        s.add(HologramCapture.waitTicks(40));
        s.add(HologramCapture.check(key + ": still waiting behind the dialogue two seconds later (not lost, not forced)", () ->
                Minecraft.getInstance().gui.screen() instanceof DialogueScreen));
        s.add(onServer(server -> DialogueSessionManager.endDialogue(me(server))));
        s.add(HologramCapture.run("the player leaves the dialogue", () -> Minecraft.getInstance().gui.setScreen(null)));
        s.add(HologramCapture.until("queued check opened", () -> screen() != null, () -> {}, 40));
        s.add(HologramCapture.check(key + ": once no screen was open, the queued check opened (a new session)", () ->
                screen() != null && !screen().sessionId().equals(first[0]) && screen().presentation().context().checkName().equals(second.checkName())));
        s.add(HologramCapture.screenshot("DR_" + key + "_03_queued_check_opened"));
        s.add(mc -> {
            seed(new int[]{12}).tick(mc);
            screen().keyPressed(new KeyEvent(GLFW.GLFW_KEY_E, 0, 0));
            return true;
        });
        s.add(HologramCapture.until("queued check final", () -> screen() != null && screen().presentation().canContinue(Util.getMillis()), () -> {}, 200));
        s.add(HologramCapture.screenshot("DR_" + key + "_04_queued_check_resolved"));
        s.add(HologramCapture.check(key + ": the queued check rolled once on the server, and the screen shows that result", () ->
                CALLBACKS.get() == 1 && SERVER_RESULT.get() != null && SERVER_RESULT.get().roll1() == 12
                        && screen().presentation().result().equals(SERVER_RESULT.get())));
        continueAndCheck(s, key);
    }

    /** After the final frame: a repeated and a foreign result reach the screen; it keeps the server's first result. */
    private static void duplicateDelivery(List<Step> s) {
        s.add(HologramCapture.check("C_natural_20: a repeated delivery (a different result, same session) and another session's result are ignored", () -> {
            DiceRollScreen screen = screen();
            DiceRollResult server = SERVER_RESULT.get();
            if (screen == null || server == null) return false;
            DiceRollResult forged = new DiceRollResult(server.context(), 2, -1, 2, server.totalBonus(), 2 + server.totalBonus(),
                    RollOutcome.FAILURE);
            boolean again = screen.receiveResult(screen.sessionId(), forged);
            boolean foreign = screen.receiveResult(UUID.randomUUID(), forged);
            return !again && !foreign && screen.presentation().result().equals(server)
                    && screen.presentation().totalShown(Util.getMillis()) == server.total();
        }));
        s.add(HologramCapture.screenshot("DR_C_natural_20_998_after_duplicate_delivery"));
    }

    private static void continueAndCheck(List<Step> s, String key) {
        s.add(pressKey(GLFW.GLFW_KEY_E));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.check(key + ": Continue closed the screen and the callback still fired exactly once", () ->
                screen() == null && CALLBACKS.get() == 1));
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────────────────────

    private static Step reset() {
        return mc -> {
            CALLBACKS.set(0);
            SERVER_RESULT.set(null);
            Minecraft.getInstance().gui.setScreen(null);
            return true;
        };
    }

    /** Seeds the player's server-side random so DiceRoller's next d20s are {@code naturals} (queued before the click). */
    private static Step seed(int[] naturals) {
        return onServer(server -> {
            RandomSource random = me(server).getRandom();
            for (long seed = 0; seed < 10_000_000L; seed++) {
                random.setSeed(seed);
                boolean ok = true;
                for (int n : naturals) ok &= random.nextInt(20) + 1 == n;
                if (ok) {
                    random.setSeed(seed);
                    return;
                }
            }
        });
    }

    /** A numbered frame; its wall-clock time is logged so recordings can be timed like the (time-based) animation. */
    private static Step shot(String key, int[] frame) {
        return mc -> {
            String name = String.format(Locale.ROOT, "DR_%s_%03d", key, frame[0]++);
            HologramCapture.log("frame " + name + " t=" + Util.getMillis());
            HologramCapture.screenshot(name).tick(mc);
            return true;
        };
    }

    private static Step hoverDie() {
        return HologramCapture.run("hover the die", () -> {
            Minecraft mc = Minecraft.getInstance();
            int[] die = dieCentre();
            double scale = mc.getWindow().getGuiScale();
            try {
                var fx = MouseHandler.class.getDeclaredField("xpos");
                var fy = MouseHandler.class.getDeclaredField("ypos");
                fx.setAccessible(true);
                fy.setAccessible(true);
                fx.setDouble(mc.mouseHandler, die[0] * scale);
                fy.setDouble(mc.mouseHandler, die[1] * scale);
            } catch (ReflectiveOperationException e) {
                HologramCapture.log("FAIL: cannot move the mouse: " + e);
            }
        });
    }

    private static Step pressKey(int key) {
        return mc -> {
            DiceRollScreen screen = screen();
            if (screen != null) screen.keyPressed(new KeyEvent(key, 0, 0));
            HologramCapture.log("press: key " + key);
            return true;
        };
    }

    /** The die's centre in GUI coordinates (the screen's layout: arena at panel top + 92). */
    private static int[] dieCentre() {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        return new int[]{w / 2, Math.max(4, (h - 216) / 2) + 92};
    }

    private static DiceRollScreen screen() {
        return Minecraft.getInstance().gui.screen() instanceof DiceRollScreen screen ? screen : null;
    }

    private static Step onServer(Consumer<MinecraftServer> action) {
        return mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> action.accept(server));
            return true;
        };
    }

    private static ServerPlayer me(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (!(p instanceof TotalityFakePlayer)) return p;
        return server.getPlayerList().getPlayers().getFirst();
    }
}
