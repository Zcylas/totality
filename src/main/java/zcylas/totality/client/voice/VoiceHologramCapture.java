package zcylas.totality.client.voice;

import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.voice.audio.PcmAudio;
import zcylas.totality.api.voice.audio.WavPcmReader;
import zcylas.totality.api.voice.capture.CaptureDeviceProvider;
import zcylas.totality.client.hologram.HologramManager;
import zcylas.totality.client.hologram.HologramStack;
import zcylas.totality.client.hologram.dev.HologramCapture;
import zcylas.totality.client.hologram.dev.HologramShowcase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Voice scenes for the opt-in Notification V2 screenshot run ({@link HologramCapture}).
 *
 * <ul>
 *   <li>Drives the REAL lazy model initialization through {@link VoiceInputController#onPushToTalkPressed()}
 *       — the call the push-to-talk key makes while Voice Input is unloaded (no microphone).</li>
 *   <li>Voice commands: with {@code -Dtotality.hologram.capture.wavDir=<dir>} the next capture of the real
 *       controller plays a spoken WAV from that directory instead of the microphone
 *       ({@link CaptureWavInjector}); push-to-talk press/release, both recognitions on the bundled model,
 *       the matcher and the hologram routing are all the real code.</li>
 *   <li>The failure scene only previews the failure presentation (a real failure needs a broken model).</li>
 * </ul>
 */
final class VoiceHologramCapture {

    static final String WAV_DIR_PROPERTY = "totality.hologram.capture.wavDir";
    private static final String CONFIRM_KEY = "totality:showcase/confirm";
    private static volatile boolean driving;
    private static @Nullable CaptureWavInjector injector;

    private VoiceHologramCapture() {}

    /** True while a capture scene holds push-to-talk itself (the physical key is not down). */
    static boolean isDriving() {
        return driving;
    }

    /** The microphone provider for the runtime: the real one, wrapped only during a capture run. */
    static CaptureDeviceProvider wrapIfRequested(CaptureDeviceProvider real) {
        if (!HologramCapture.requested() || System.getProperty(WAV_DIR_PROPERTY) == null) return real;
        injector = new CaptureWavInjector(real);
        return injector;
    }

    static void registerIfRequested(VoiceInputController controller) {
        if (!HologramCapture.requested()) return;
        HologramCapture.addScene(10, initializationScene(controller));
        if (injector != null) HologramCapture.addScene(25, commandScene(controller));
        if (injector != null) HologramCapture.addScene(26, hudScene(controller));
        if (injector != null) HologramCapture.addScene(36, chordScene(controller));
        if (injector != null) HologramCapture.addScene(37, debugChordScene(controller));
        if (injector != null) HologramCapture.addScene(27, operatorScene(controller));
        if (injector != null && HologramCapture.multiplayer()) HologramCapture.addScene(28, dedicatedServerScene(controller));
        HologramCapture.addScene(30, List.of(
                HologramCapture.run("PREVIEW of the failure presentation (no real failure)", () -> HologramManager.show(
                        VoiceHolograms.modelInitialization(VoiceUi.Notice.BACKEND_FAILED,
                                "Voice Input is unavailable: the speech model could not be verified (/totalityvoice retry)"))),
                HologramCapture.waitTicks(30),
                HologramCapture.screenshot("13_voice_failure_presentation"),
                HologramCapture.run("clear", HologramManager::clear),
                HologramCapture.check("Voice Input unaffected by the failure preview", () -> controller.state() == VoiceState.READY)));
    }

    private static List<HologramCapture.Step> initializationScene(VoiceInputController controller) {
        List<HologramCapture.Step> s = new ArrayList<>(List.of(
                HologramCapture.run("real model initialization (push-to-talk entry point)", () -> {
                    if (controller.state() == VoiceState.UNLOADED) controller.onPushToTalkPressed();
                }),
                HologramCapture.waitTicks(14),
                HologramCapture.screenshot("01_voice_initializing"),
                HologramCapture.until("voice model finished loading",
                        () -> controller.state() == VoiceState.READY || controller.state() == VoiceState.FAILED,
                        () -> {}, 20 * 180),
                HologramCapture.waitTicks(30),
                HologramCapture.screenshot("02_voice_ready"),
                HologramCapture.check("voice state is READY", () -> controller.state() == VoiceState.READY),
                HologramCapture.check("model readiness did not open the microphone",
                        () -> !controller.microphone().isCapturing()),
                HologramCapture.check("ready hologram displayed",
                        () -> VoiceHolograms.KEY.equals(HologramManager.activeKey()))));
        s.addAll(HologramCapture.touch("dismiss"));
        s.addAll(List.of(
                HologramCapture.waitTicks(20),
                HologramCapture.check("ready hologram dismissed by left click", () -> HologramManager.activeKey() == null),
                HologramCapture.check("Voice Input still READY after dismissal", () -> controller.state() == VoiceState.READY),
                HologramCapture.waitTicks(20)));
        return s;
    }

    private static final VoiceInputController.PushToTalkMode COMMAND = VoiceInputController.PushToTalkMode.COMMAND;
    private static final VoiceInputController.PushToTalkMode DICTATION = VoiceInputController.PushToTalkMode.DICTATION;

    private static List<HologramCapture.Step> commandScene(VoiceInputController controller) {
        String[] before = new String[1];
        List<HologramCapture.Step> s = new ArrayList<>();
        // These scenes are the NON-operator behaviour (the single-player owner is an operator; Operator
        // Mode has its own scene below).
        s.add(HologramCapture.run("as a non-operator client", () -> zcylas.totality.client.operator.OperatorModeNetwork.captureOverride = false));
        s.add(HologramCapture.run("remember latest dictation, clear showcase log", () -> {
            before[0] = controller.latestTranscript();
            HologramShowcase.PERFORMED.clear();
        }));
        s.add(showConfirm());
        s.add(HologramCapture.screenshot("20_voice_hint"));

        // B (command): rejections — out of vocabulary, ordinary speech, two commands, look-alikes.
        s.addAll(utter(controller, "banana", "21_b_command_listening", COMMAND));
        s.add(HologramCapture.waitTicks(2));
        s.add(HologramCapture.screenshot("22_voice_rejected"));
        s.add(noAction("B 'banana' performed no action"));
        for (String wav : List.of("hello_there", "confirm_cancel", "fa_conform", "fa_castle", "fa_gone_from", "fa_yesterday")) {
            s.addAll(utter(controller, wav, null, COMMAND));
            s.add(noAction("B '" + wav + "' performed no action"));
        }
        s.add(HologramCapture.check("B command utterances never became dictation",
                () -> Objects.equals(controller.latestTranscript(), before[0])));

        // Stale: the prompt is interrupted while the utterance is being recognized.
        s.add(arm(controller, "confirmed", COMMAND));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.run("interrupt with CRITICAL while listening", () -> HologramManager.show(HologramShowcase.urgent())));
        s.add(HologramCapture.waitTicks(wavTicks("confirmed")));
        s.addAll(release(controller));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.screenshot("23_voice_stale_ignored"));
        s.add(HologramCapture.check("stale: 'confirmed' did not act on the interrupted prompt", () -> HologramShowcase.PERFORMED.isEmpty()
                && HologramManager.suspendedCount() == 1));

        // Paused prompt + B: nothing offers a command -> no action AND no dictation fallback.
        s.add(arm(controller, "confirmed", COMMAND));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.screenshot("24_b_no_command_available"));
        s.add(HologramCapture.run("stop driving", () -> driving = false));
        s.add(HologramCapture.check("B with no command context: no microphone, no action, no dictation",
                () -> controller.state() == VoiceState.READY && HologramShowcase.PERFORMED.isEmpty()
                        && HologramManager.suspendedCount() == 1 && Objects.equals(controller.latestTranscript(), before[0])));
        s.addAll(HologramCapture.touch("acknowledge"));
        s.add(HologramCapture.waitTicks(30));
        s.add(HologramCapture.check("confirm prompt resumed", () -> CONFIRM_KEY.equals(HologramManager.activeKey())));

        // Alt+B (dictation) while the prompt is up: a transcript, never a command.
        s.addAll(utter(controller, "hello_there", "27_altb_dictation_listening", DICTATION));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.screenshot("28_altb_dictation_result"));
        s.add(HologramCapture.check("Alt+B dictated \"Hello there\" and left the prompt alone",
                () -> "Hello there".equals(controller.latestTranscript()) && HologramShowcase.PERFORMED.isEmpty()
                        && CONFIRM_KEY.equals(HologramManager.activeKey())));
        s.addAll(utter(controller, "confirmed", null, DICTATION));
        s.add(HologramCapture.check("Alt+B 'confirmed' is dictation, not Confirm",
                () -> HologramShowcase.PERFORMED.isEmpty() && CONFIRM_KEY.equals(HologramManager.activeKey())));

        // B accepted: Confirm intent aliases, then Cancel, each through the click's action handler.
        for (String[] c : new String[][] {{"confirmed", "confirm"}, {"yes", "confirm"}, {"okay", "confirm"},
                {"confirm", "confirm"}, {"no", "cancel"}}) {
            String wav = c[0], action = c[1];
            String[] dictation = new String[1];
            s.add(HologramCapture.run("clear showcase log and chat", () -> {
                HologramShowcase.PERFORMED.clear();
                dictation[0] = controller.latestTranscript();
                net.minecraft.client.Minecraft.getInstance().gui.hud.getChat().clearMessages(false);
            }));
            s.addAll(utter(controller, wav, null, COMMAND));
            s.add(HologramCapture.screenshot("25_voice_accepted_" + wav));
            s.add(HologramCapture.check("B '" + wav + "' executed " + action + " exactly once, no transcript",
                    () -> HologramShowcase.PERFORMED.equals(List.of(action)) && Objects.equals(controller.latestTranscript(), dictation[0])));
            s.add(HologramCapture.waitTicks(20));
            s.add(showConfirm());
        }

        // Dismiss intent: only where a real Dismiss action exists (the Daily Quest notice).
        s.add(HologramCapture.run("clear", HologramManager::clear));
        s.add(HologramCapture.run("dismissable notice", () -> HologramManager.show(HologramShowcase.dailyQuest())));
        s.add(HologramCapture.waitTicks(30));
        s.add(HologramCapture.screenshot("29_dismiss_hint"));
        s.addAll(utter(controller, "minimize", null, COMMAND));
        s.add(HologramCapture.check("B 'minimize' dismissed the notice (no decision made)", () -> HologramManager.activeKey() == null
                || HologramManager.activePhase() == HologramStack.Phase.CLOSING));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.run("clear", HologramManager::clear));
        return s;
    }

    /**
     * Voice Input HUD V2: every phase in real frames — B command listening (waveform on real speech),
     * processing, recognized, not understood; B with nothing to command; Alt+B dictation over an open
     * hologram (listening, transcribing, transcribed); a near-silent capture (flat waveform).
     */
    private static List<HologramCapture.Step> hudScene(VoiceInputController controller) {
        List<HologramCapture.Step> s = new ArrayList<>();
        float[] peak = new float[2];
        s.add(HologramCapture.run("as a non-operator client", () -> zcylas.totality.client.operator.OperatorModeNetwork.captureOverride = false));
        s.add(HologramCapture.run("clear", HologramManager::clear));
        s.add(showConfirm());
        s.addAll(phases(controller, "confirmed", COMMAND, "40_hud_command", peak, 0));
        s.add(HologramCapture.check("HUD: command recognized only after the hologram ran it",
                () -> "command_recognized".equals(VoiceHud.lastLook) && HologramShowcase.PERFORMED.contains("confirm")));
        s.add(HologramCapture.waitTicks(40));
        s.add(showConfirm());
        s.addAll(phases(controller, "banana", COMMAND, "41_hud_rejected", peak, 0));
        s.add(HologramCapture.check("HUD: rejected command shows Not Understood", () -> "not_understood".equals(VoiceHud.lastLook)));
        s.add(HologramCapture.check("HUD: waveform followed real speech (peak level > 0.3)", () -> peak[0] > 0.3f));
        s.add(HologramCapture.waitTicks(40));
        // Alt+B dictation while the hologram is open.
        s.addAll(phases(controller, "hello_there", DICTATION, "42_hud_dictation", peak, 0));
        s.add(HologramCapture.check("HUD: dictation transcribed (violet), prompt untouched",
                () -> "dictation_done".equals(VoiceHud.lastLook) && CONFIRM_KEY.equals(HologramManager.activeKey())));
        s.add(HologramCapture.waitTicks(40));
        // Third person: where the widget sits relative to the character.
        s.add(HologramCapture.run("clear", HologramManager::clear));
        s.add(HologramCapture.run("third person", () -> net.minecraft.client.Minecraft.getInstance().options
                .setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK)));
        s.add(HologramCapture.waitTicks(20));
        s.addAll(phases(controller, "hello_there", DICTATION, "44_hud_third_person", peak, 0));
        s.add(HologramCapture.run("first person", () -> net.minecraft.client.Minecraft.getInstance().options
                .setCameraType(net.minecraft.client.CameraType.FIRST_PERSON)));
        s.add(HologramCapture.waitTicks(40));
        // B with nothing to command (no hologram).
        s.add(arm(controller, "confirmed", COMMAND));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.screenshot("45_hud_b_nothing_to_command"));
        s.add(HologramCapture.run("stop driving", () -> driving = false));
        s.add(HologramCapture.check("HUD: B with nothing to command shows the dictation hint, no microphone",
                () -> "no_command".equals(VoiceHud.lastLook) && controller.state() == VoiceState.READY));
        s.add(HologramCapture.waitTicks(40));
        s.add(showConfirm());
        // Near-silent room noise: the waveform stays flat.
        s.addAll(phases(controller, "room_quiet", DICTATION, "43_hud_quiet", peak, 1));
        s.add(HologramCapture.check("HUD: waveform stays flat without speech (peak level < 0.05)", () -> peak[1] < 0.05f));
        s.add(HologramCapture.run("clear", HologramManager::clear));
        s.add(HologramCapture.waitTicks(40));
        s.add(HologramCapture.run("real permission hint again", () -> zcylas.totality.client.operator.OperatorModeNetwork.captureOverride = null));
        return s;
    }

    /** Listening (two frames), processing (right after release) and result frames of one utterance. */
    private static List<HologramCapture.Step> phases(VoiceInputController controller, String wav,
                                                     VoiceInputController.PushToTalkMode mode, String prefix,
                                                     float[] peak, int slot) {
        List<HologramCapture.Step> s = new ArrayList<>();
        s.add(HologramCapture.run("reset level peak", () -> peak[slot] = 0));
        s.add(arm(controller, wav, mode));
        int ticks = wavTicks(wav);
        HologramCapture.Step track = mc -> {
            peak[slot] = Math.max(peak[slot], VoiceHud.levelNow());
            return true;
        };
        for (int i = 0; i < ticks; i++) {
            s.add(track);
            s.add(HologramCapture.waitTicks(1));
            if (i == ticks / 3) {
                s.add(HologramCapture.screenshot(prefix + "_listening_a"));
                s.add(clearsPanel(prefix));
            }
            if (i == ticks / 2) s.add(HologramCapture.screenshot(prefix + "_listening_b"));
        }
        s.add(HologramCapture.run("release push-to-talk", controller::onPushToTalkReleased));
        s.add(HologramCapture.screenshot(prefix + "_processing"));
        s.add(HologramCapture.until("utterance finished", () -> controller.state() == VoiceState.READY, () -> {}, 20 * 20));
        s.add(HologramCapture.run("stop driving", () -> driving = false));
        s.add(HologramCapture.waitTicks(5));
        s.add(HologramCapture.screenshot(prefix + "_result"));
        return s;
    }

    // ── Operator Mode (integrated server; the single-player owner is an operator) ──

    private static net.minecraft.server.@Nullable MinecraftServer server() {
        return net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
    }

    private static boolean serverRaining() {
        var sv = server();
        return sv != null && (sv.getWeatherData().isRaining() || sv.getWeatherData().isThundering());
    }

    private static net.minecraft.world.level.@Nullable GameType serverGameMode() {
        var sv = server();
        var me = net.minecraft.client.Minecraft.getInstance().player;
        if (sv == null || me == null) return null;
        var sp = sv.getPlayerList().getPlayer(me.getUUID());
        return sp == null ? null : sp.gameMode.getGameModeForPlayer();
    }

    /** What happened during one held Operator Mode utterance. */
    private static final class Hold {
        int ticks, requestingAt = -1, authorizedAt = -1;
        boolean micDropped;
    }

    /**
     * One continuous B hold with {@code wav}: records when the HUD switched to Operator Mode (interim) and
     * whether the microphone ever stopped before release; screenshots at the transitions, after release
     * and at the server's answer. {@code releaseAfterAuthorized}: release as soon as the server authorized
     * (i.e. before the command was spoken).
     */
    private static List<HologramCapture.Step> operatorUtter(VoiceInputController controller, String wav, @Nullable String prefix,
                                                            Hold hold, boolean releaseAfterAuthorized) {
        List<HologramCapture.Step> s = new ArrayList<>();
        s.add(arm(controller, wav, COMMAND));
        int total = wavTicks(wav);
        s.add(mc -> {
            hold.ticks++;
            VoiceInputController.OperatorPhase phase = controller.operatorPhase();
            if (phase == VoiceInputController.OperatorPhase.REQUESTING && hold.requestingAt < 0) {
                hold.requestingAt = hold.ticks;
                if (prefix != null) HologramCapture.screenshot(prefix + "_a_detected").tick(mc);
            }
            if (phase == VoiceInputController.OperatorPhase.AUTHORIZED && hold.authorizedAt < 0) hold.authorizedAt = hold.ticks;
            if (prefix != null && hold.authorizedAt > 0 && hold.ticks == hold.authorizedAt + 6) {
                HologramCapture.screenshot(prefix + "_b_operator_listening").tick(mc);   // after the colour transition
            }
            if (controller.state() != VoiceState.LISTENING || !controller.microphone().isCapturing()) hold.micDropped = true;
            if (releaseAfterAuthorized && hold.authorizedAt > 0) return true;
            return hold.ticks >= total;
        });
        s.add(HologramCapture.run("release push-to-talk", controller::onPushToTalkReleased));
        if (prefix != null) s.add(HologramCapture.screenshot(prefix + "_c_processing"));
        s.add(HologramCapture.until("utterance finished", () -> controller.state() == VoiceState.READY, () -> {}, 20 * 20));
        s.add(HologramCapture.run("stop driving", () -> driving = false));
        s.add(HologramCapture.until("server answered (if a request was sent)", () -> controller.pendingOperatorRequest() < 0, () -> {}, 60));
        s.add(HologramCapture.waitTicks(3));
        if (prefix != null) s.add(HologramCapture.screenshot(prefix + "_d_result"));
        return s;
    }

    private static List<HologramCapture.Step> operatorScene(VoiceInputController controller) {
        List<HologramCapture.Step> s = new ArrayList<>();
        s.add(HologramCapture.run("real permission hint", () -> zcylas.totality.client.operator.OperatorModeNetwork.captureOverride = null));
        s.add(HologramCapture.run("clear", HologramManager::clear));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("gamemode survival @s"));
        s.add(HologramCapture.command("weather rain"));
        s.add(HologramCapture.waitTicks(60));
        s.add(HologramCapture.check("Operator Mode offered: the client knows this player is an operator (hint only)",
                () -> zcylas.totality.api.voice.operator.OperatorChannels.CURRENT.mayOffer()));

        // 1. "Operator Mode" … pause … "Clear Rain" in ONE hold.
        Hold rain = new Hold();
        s.add(HologramCapture.screenshot("70_op_raining_before"));
        s.addAll(operatorUtter(controller, "op_mix_clear_rain", "71_op_clear_rain", rain, false));
        s.add(HologramCapture.check("one hold: the HUD switched to Operator Mode mid-hold (interim), then the server authorized, "
                + "the microphone never stopped before release", () -> rain.requestingAt > 0 && rain.authorizedAt >= rain.requestingAt
                && !rain.micDropped));
        s.add(HologramCapture.run("timing", () -> HologramCapture.log(String.format(java.util.Locale.ROOT,
                "info: clear rain hold — requesting at tick %d, authorized at tick %d, released at tick %d (50 ms ticks)",
                rain.requestingAt, rain.authorizedAt, rain.ticks))));
        s.add(HologramCapture.check("Operator Mode → Clear Rain: executed by the server (weather cleared), HUD 'executed'",
                () -> !serverRaining() && "operator_executed".equals(VoiceHud.lastLook)));
        s.add(HologramCapture.waitTicks(30));

        // 2./3. Creative and Survival: the speaking player's own game mode.
        s.addAll(operatorUtter(controller, "op_creative", "72_op_creative", new Hold(), false));
        s.add(HologramCapture.check("Operator Mode → Creative: own game mode is Creative (server)",
                () -> serverGameMode() == net.minecraft.world.level.GameType.CREATIVE));
        s.add(HologramCapture.waitTicks(30));
        s.addAll(operatorUtter(controller, "op_pause_survival", "73_op_survival", new Hold(), false));
        s.add(HologramCapture.check("Operator Mode → Survival (with a pause after the activation phrase): own game mode is Survival",
                () -> serverGameMode() == net.minecraft.world.level.GameType.SURVIVAL));
        s.add(HologramCapture.waitTicks(30));

        // 4. Negatives: nothing changes.
        s.add(HologramCapture.command("weather rain"));
        s.add(HologramCapture.waitTicks(10));
        String[] negatives = {"neg_operation_mode", "neg_opera_mode", "neg_operator", "neg_cooperate_mode", "neg_mode_creative",
                "neg_sentence", "op_two_commands", "op_extra_words", "op_only", "op_unknown_command", "cmd_without_prefix",
                "clear_rain_without_prefix_slt", "op_creative_slt", "op_then_yes"};
        for (String wav : negatives) {
            s.addAll(operatorUtter(controller, wav, wav.equals("neg_operation_mode") ? "74_op_rejected" : null, new Hold(), false));
            s.add(HologramCapture.check("'" + wav + "': no Operator action (still Survival, still raining, nothing sent)",
                    () -> serverGameMode() == net.minecraft.world.level.GameType.SURVIVAL && serverRaining()
                            && controller.pendingOperatorRequest() < 0 && !"operator_executed".equals(VoiceHud.lastLook)));
        }

        // 5. B released right after the activation phrase (before the command): the context ends, nothing runs.
        Hold early = new Hold();
        s.addAll(operatorUtter(controller, "op_pause_creative", "75_op_released_early", early, true));
        s.add(HologramCapture.check("released after 'Operator Mode' (authorized, no command yet): nothing executed",
                () -> early.authorizedAt > 0 && serverGameMode() == net.minecraft.world.level.GameType.SURVIVAL));

        // 6. A hologram open: Operator Mode never answers it; its own commands still work for operators.
        s.add(showConfirm());
        s.add(HologramCapture.run("clear showcase log", HologramShowcase.PERFORMED::clear));
        s.addAll(operatorUtter(controller, "op_creative", "76_op_with_hologram", new Hold(), false));
        s.add(HologramCapture.check("hologram open: Operator Mode → Creative executed, the prompt untouched",
                () -> serverGameMode() == net.minecraft.world.level.GameType.CREATIVE && HologramShowcase.PERFORMED.isEmpty()
                        && CONFIRM_KEY.equals(HologramManager.activeKey())));
        s.addAll(operatorUtter(controller, "op_then_yes", null, new Hold(), false));
        s.add(HologramCapture.check("'operator mode yes': neither confirms the prompt nor requests anything",
                () -> HologramShowcase.PERFORMED.isEmpty() && CONFIRM_KEY.equals(HologramManager.activeKey())));
        s.addAll(operatorUtter(controller, "confirmed", "77_op_hologram_command_still_works", new Hold(), false));
        s.add(HologramCapture.check("an operator's ordinary 'Confirmed' still answers the prompt", () -> HologramShowcase.PERFORMED.equals(List.of("confirm"))));
        s.add(HologramCapture.waitTicks(30));

        s.add(HologramCapture.run("clear", HologramManager::clear));
        s.add(HologramCapture.command("weather clear"));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(HologramCapture.waitTicks(20));
        return s;
    }

    // ── Operator Mode on a DEDICATED server (multiplayer capture run) ──

    private static boolean clientIsOperator() {
        var p = net.minecraft.client.Minecraft.getInstance().player;
        return p != null && p.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    private static net.minecraft.world.level.@Nullable GameType clientGameMode() {
        var gm = net.minecraft.client.Minecraft.getInstance().gameMode;
        return gm == null ? null : gm.getPlayerMode();
    }

    /**
     * A real client on a real dedicated server: permissions come from the server's operator list, changed
     * only from the server console. Non-operator (even when the client is forced to offer Operator Mode),
     * operator, and permission revoked between the activation phrase and the release.
     */
    private static List<HologramCapture.Step> dedicatedServerScene(VoiceInputController controller) {
        String me = "@a";   // the capture client is the only real player on the disposable server
        List<HologramCapture.Step> s = new ArrayList<>();
        s.add(HologramCapture.check("connected to a dedicated server (no integrated server)",
                () -> !net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer()));
        s.add(HologramCapture.console("deop " + me));
        s.add(HologramCapture.console("gamemode survival " + me));
        s.add(HologramCapture.console("weather rain"));
        s.add(HologramCapture.until("client sees: not an operator, survival", () -> !clientIsOperator()
                && clientGameMode() == net.minecraft.world.level.GameType.SURVIVAL, () -> {}, 200));
        s.add(HologramCapture.waitTicks(60));

        // Non-operator, real hint: B with nothing to command never opens the microphone.
        s.add(HologramCapture.run("real permission hint", () -> zcylas.totality.client.operator.OperatorModeNetwork.captureOverride = null));
        s.add(arm(controller, "op_creative", COMMAND));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.run("stop driving", () -> driving = false));
        s.add(HologramCapture.check("dedicated, non-operator: B opens no microphone (not offered), no dictation",
                () -> controller.state() == VoiceState.READY && "no_command".equals(VoiceHud.lastLook)));
        s.add(HologramCapture.waitTicks(40));

        // Non-operator, client FORCED to offer (as a modified client would): the SERVER refuses everything.
        s.add(HologramCapture.run("client forced to offer Operator Mode", () -> zcylas.totality.client.operator.OperatorModeNetwork.captureOverride = true));
        String[][] denied = {{"op_creative", "80_mp_nonop_creative"}, {"op_mix_clear_rain", "81_mp_nonop_clear_rain"},
                {"op_survival", null}};
        for (String[] d : denied) {
            Hold h = new Hold();
            s.addAll(operatorUtter(controller, d[0], d[1], h, false));
            s.add(HologramCapture.check("dedicated, non-operator '" + d[0] + "': interim authorization refused, action DENIED by the server",
                    () -> "operator_denied_result".equals(VoiceHud.lastLook) && clientGameMode() == net.minecraft.world.level.GameType.SURVIVAL));
            s.add(HologramCapture.waitTicks(30));
        }
        s.add(HologramCapture.check("dedicated, non-operator: still raining", () -> net.minecraft.client.Minecraft.getInstance().level.isRaining()));

        // Operator (granted on the server console only).
        s.add(HologramCapture.run("real permission hint", () -> zcylas.totality.client.operator.OperatorModeNetwork.captureOverride = null));
        s.add(HologramCapture.console("op " + me));
        s.add(HologramCapture.until("client sees: operator", VoiceHologramCapture::clientIsOperator, () -> {}, 200));
        s.addAll(operatorUtter(controller, "op_creative", "82_mp_op_creative", new Hold(), false));
        s.add(HologramCapture.until("client game mode Creative", () -> clientGameMode() == net.minecraft.world.level.GameType.CREATIVE, () -> {}, 60));
        s.add(HologramCapture.check("dedicated, operator: Creative executed by the server",
                () -> clientGameMode() == net.minecraft.world.level.GameType.CREATIVE));
        s.add(HologramCapture.waitTicks(30));
        s.addAll(operatorUtter(controller, "op_mix_clear_rain", "83_mp_op_clear_rain", new Hold(), false));
        s.add(HologramCapture.until("rain stops on the client", () -> !net.minecraft.client.Minecraft.getInstance().level.isRaining(), () -> {}, 300));
        s.add(HologramCapture.check("dedicated, operator: Clear Rain executed by the server",
                () -> !net.minecraft.client.Minecraft.getInstance().level.isRaining()));
        s.add(HologramCapture.waitTicks(30));
        s.addAll(operatorUtter(controller, "op_survival", null, new Hold(), false));
        s.add(HologramCapture.until("client game mode Survival", () -> clientGameMode() == net.minecraft.world.level.GameType.SURVIVAL, () -> {}, 60));
        s.add(HologramCapture.check("dedicated, operator: Survival executed", () -> clientGameMode() == net.minecraft.world.level.GameType.SURVIVAL));
        s.add(HologramCapture.waitTicks(30));

        // Revoked between activation and execution: authorized mid-hold, deopped before release. The player
        // starts in Creative, so the requested Survival would be a real change.
        s.add(HologramCapture.console("gamemode creative " + me));
        s.add(HologramCapture.until("client game mode Creative", () -> clientGameMode() == net.minecraft.world.level.GameType.CREATIVE, () -> {}, 100));
        s.add(HologramCapture.waitTicks(20));
        Hold revoked = new Hold();
        s.add(arm(controller, "op_pause_survival", COMMAND));
        int total = wavTicks("op_pause_survival");
        boolean[] requested = {false};
        s.add(mc -> {
            revoked.ticks++;
            if (controller.operatorPhase() == VoiceInputController.OperatorPhase.AUTHORIZED && revoked.authorizedAt < 0) {
                revoked.authorizedAt = revoked.ticks;
            }
            if (revoked.authorizedAt > 0 && !requested[0]) {
                requested[0] = true;
                HologramCapture.console("deop " + me).tick(mc);
                HologramCapture.screenshot("84_mp_authorized_then_revoked").tick(mc);
            }
            return revoked.ticks >= total + 10;
        });
        s.add(HologramCapture.run("release push-to-talk", controller::onPushToTalkReleased));
        s.add(HologramCapture.until("utterance finished", () -> controller.state() == VoiceState.READY, () -> {}, 20 * 20));
        s.add(HologramCapture.run("stop driving", () -> driving = false));
        s.add(HologramCapture.until("server answered", () -> controller.pendingOperatorRequest() < 0, () -> {}, 60));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.screenshot("85_mp_revoked_result"));
        s.add(HologramCapture.check("dedicated: permission revoked after the activation phrase → the completed request is DENIED",
                () -> revoked.authorizedAt > 0 && "operator_denied_result".equals(VoiceHud.lastLook)
                        && clientGameMode() == net.minecraft.world.level.GameType.CREATIVE));
        s.add(HologramCapture.console("weather clear"));
        s.add(HologramCapture.waitTicks(20));
        return s;
    }

    /**
     * Alt+B belongs to Dictation even though Alt is Power Mining's modifier: through the REAL key path
     * (modifier and push-to-talk reported as held), dictation starts and Power Mining neither shows its
     * idle reticle nor charges when Attack is held on a mineable block. Runs after the Power Mining scenes
     * (survival, pickaxe, stone wall in front).
     */
    private static List<HologramCapture.Step> chordScene(VoiceInputController controller) {
        List<HologramCapture.Step> s = new ArrayList<>();
        boolean[] powerSeen = new boolean[1];
        String[] before = new String[1];
        s.add(HologramCapture.look(0, 12));
        s.add(HologramCapture.run("arm the injected microphone; remember transcript", () -> {
            before[0] = controller.latestTranscript();
            try {
                Objects.requireNonNull(injector).arm(WavPcmReader.read(wavPath("hello_there")).samples());
            } catch (Exception e) {
                Totality.LOGGER.error("[Totality Hologram Capture] cannot arm", e);
            }
        }));
        s.add(HologramCapture.run("hold Alt", () -> zcylas.totality.init.ModKeybinds.simulatePhysicalPress(
                zcylas.totality.init.ModKeybinds.RADIAL_MODIFIER, true)));
        s.add(HologramCapture.run("hold B (Alt+B chord)", () -> zcylas.totality.init.ModKeybinds.simulatePhysicalPress(
                zcylas.totality.init.ModKeybinds.VOICE_PUSH_TO_TALK, true)));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.run("hold Attack during the chord", () ->
                net.minecraft.client.Minecraft.getInstance().options.keyAttack.setDown(true)));
        int ticks = wavTicks("hello_there");
        for (int i = 0; i < ticks; i++) {
            s.add(mc -> {
                String st = zcylas.totality.client.mining.PowerMiningMeterHud.lastState();
                if (zcylas.totality.client.mining.ClientMiningController.isMeterActive() || !"hidden".equals(st)) powerSeen[0] = true;
                return true;
            });
            if (i == ticks / 2) s.add(HologramCapture.screenshot("60_altb_chord_dictation_no_power"));
        }
        s.add(focusLog("Alt+B chord"));
        s.add(HologramCapture.check("Alt+B: the real key path started DICTATION",
                () -> controller.state() == VoiceState.LISTENING && controller.utteranceMode() == DICTATION));
        s.add(HologramCapture.check("Alt+B: Power Mining showed no reticle and never charged (Attack held on stone)",
                () -> !powerSeen[0]));
        s.add(HologramCapture.run("release B, Attack", () -> {
            zcylas.totality.init.ModKeybinds.simulatePhysicalPress(zcylas.totality.init.ModKeybinds.VOICE_PUSH_TO_TALK, false);
            net.minecraft.client.Minecraft.getInstance().options.keyAttack.setDown(false);
        }));
        s.add(HologramCapture.until("utterance finished", () -> controller.state() == VoiceState.READY, () -> {}, 20 * 20));
        s.add(HologramCapture.check("Alt+B: dictation transcribed normally",
                () -> "Hello there".equals(controller.latestTranscript())));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.check("control: Alt alone (B released) shows the idle reticle again",
                () -> "idle".equals(zcylas.totality.client.mining.PowerMiningMeterHud.lastState())));
        s.add(HologramCapture.run("release Alt", () -> zcylas.totality.init.ModKeybinds.simulatePhysicalPress(
                zcylas.totality.init.ModKeybinds.RADIAL_MODIFIER, false)));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(HologramCapture.waitTicks(20));
        return s;
    }

    /**
     * F3+B belongs to vanilla (entity hitboxes): through vanilla's REAL keyboard handler (synthetic F3/B key events)
     * and Totality's real push-to-talk path (F3 and B reported as physically held), voice must never start, an active
     * phrase is cancelled without running, releasing F3 with B still held never starts voice late, and a fresh B
     * press works normally again.
     */
    private static List<HologramCapture.Step> debugChordScene(VoiceInputController controller) {
        List<HologramCapture.Step> s = new ArrayList<>();
        var mc = net.minecraft.client.Minecraft.getInstance();
        boolean[] leftReady = new boolean[1];
        boolean[] hitboxesBefore = new boolean[1];
        String[] transcript = new String[1];
        java.util.function.Supplier<net.minecraft.client.KeyMapping> f3 = () -> net.minecraft.client.Minecraft.getInstance().options.keyDebugModifier;
        HologramCapture.Step watch = m -> {
            if (controller.state() != VoiceState.READY) leftReady[0] = true;
            return true;
        };
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(HologramCapture.command("execute at @s run summon minecraft:pig ^ ^ ^3 {NoAI:1b}"));
        s.add(HologramCapture.look(0, 20));
        s.add(HologramCapture.waitTicks(10));
        boolean[] overlayBefore = new boolean[1];
        s.add(HologramCapture.run("record", () -> {
            hitboxesBefore[0] = hitboxes();
            overlayBefore[0] = mc.getDebugOverlay().showDebugScreen();
            leftReady[0] = false;
        }));
        // 1. F3 held, then B: vanilla toggles hitboxes; voice stays idle.
        s.add(debugKey("F3 down", f3, org.lwjgl.glfw.GLFW.GLFW_KEY_F3, true));
        s.add(watch);
        s.add(debugKey("B down (F3+B)", () -> zcylas.totality.init.ModKeybinds.VOICE_PUSH_TO_TALK, org.lwjgl.glfw.GLFW.GLFW_KEY_B, true));
        for (int i = 0; i < 8; i++) s.add(watch);
        s.add(HologramCapture.screenshot("70_f3b_hitboxes_on_no_voice"));
        s.add(HologramCapture.check("F3+B: vanilla toggled entity hitboxes", () -> hitboxes() != hitboxesBefore[0]));
        s.add(HologramCapture.check("F3+B: voice never left READY (no capture, HUD, sound or command)",
                () -> !leftReady[0] && controller.state() == VoiceState.READY && !"command_listening".equals(VoiceHud.lastLook)));
        // 2. Release F3 while B stays held: no late activation.
        s.add(debugKey("F3 up (B still held)", f3, org.lwjgl.glfw.GLFW.GLFW_KEY_F3, false));
        for (int i = 0; i < 10; i++) s.add(watch);
        s.add(HologramCapture.check("F3 released with B still held: voice still idle (a fresh press is needed)",
                () -> !leftReady[0] && controller.state() == VoiceState.READY));
        s.add(debugKey("B up", () -> zcylas.totality.init.ModKeybinds.VOICE_PUSH_TO_TALK, org.lwjgl.glfw.GLFW.GLFW_KEY_B, false));
        s.add(HologramCapture.waitTicks(5));
        // 3. B first (listening), then F3: the phrase is cancelled, nothing is recognized or run.
        s.add(HologramCapture.run("arm the microphone; remember the transcript", () -> {
            transcript[0] = controller.latestTranscript();
            try {
                Objects.requireNonNull(injector).arm(WavPcmReader.read(wavPath("hello_there")).samples());
            } catch (Exception e) {
                Totality.LOGGER.error("[Totality Hologram Capture] cannot arm", e);
            }
        }));
        s.add(debugKey("B down (fresh press)", () -> zcylas.totality.init.ModKeybinds.VOICE_PUSH_TO_TALK, org.lwjgl.glfw.GLFW.GLFW_KEY_B, true));
        s.add(HologramCapture.waitTicks(6));
        s.add(focusLog("fresh B press"));
        s.add(HologramCapture.check("a fresh B press starts listening (ordinary push-to-talk)", () -> controller.state() == VoiceState.LISTENING));
        s.add(HologramCapture.screenshot("71_b_listening_before_f3"));
        s.add(debugKey("F3 down while listening", f3, org.lwjgl.glfw.GLFW.GLFW_KEY_F3, true));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.check("F3 pressed while listening: the phrase is cancelled", () -> controller.state() == VoiceState.READY));
        s.add(HologramCapture.screenshot("72_f3_cancelled_listening"));
        s.add(debugKey("F3 up", f3, org.lwjgl.glfw.GLFW.GLFW_KEY_F3, false));
        s.add(HologramCapture.run("reset", () -> leftReady[0] = false));
        for (int i = 0; i < 20; i++) s.add(watch);
        s.add(HologramCapture.check("after the cancel, with B still held: no restart and nothing recognized",
                () -> !leftReady[0] && java.util.Objects.equals(transcript[0], controller.latestTranscript())));
        s.add(debugKey("B up", () -> zcylas.totality.init.ModKeybinds.VOICE_PUSH_TO_TALK, org.lwjgl.glfw.GLFW.GLFW_KEY_B, false));
        s.add(HologramCapture.waitTicks(5));
        // 4. Another vanilla F3 shortcut (F3+G, chunk borders) still works, and voice stays idle.
        s.add(HologramCapture.run("record chunk borders", () -> hitboxesBefore[0] = chunkBorders()));
        s.add(debugKey("F3 down", f3, org.lwjgl.glfw.GLFW.GLFW_KEY_F3, true));
        s.add(vanillaKeyOnly("G down", org.lwjgl.glfw.GLFW.GLFW_KEY_G, true));
        s.add(vanillaKeyOnly("G up", org.lwjgl.glfw.GLFW.GLFW_KEY_G, false));
        s.add(debugKey("F3 up", f3, org.lwjgl.glfw.GLFW.GLFW_KEY_F3, false));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.check("F3+G (chunk borders) still toggles; voice idle",
                () -> chunkBorders() != hitboxesBefore[0] && controller.state() == VoiceState.READY));
        s.add(debugKey("F3 down", f3, org.lwjgl.glfw.GLFW.GLFW_KEY_F3, true));
        s.add(vanillaKeyOnly("G down", org.lwjgl.glfw.GLFW.GLFW_KEY_G, true));
        s.add(vanillaKeyOnly("G up", org.lwjgl.glfw.GLFW.GLFW_KEY_G, false));
        s.add(debugKey("F3 up", f3, org.lwjgl.glfw.GLFW.GLFW_KEY_F3, false));
        // 5. A fresh ordinary B hold after all that is transcribed normally.
        s.add(HologramCapture.run("arm again", () -> {
            try {
                Objects.requireNonNull(injector).arm(WavPcmReader.read(wavPath("hello_there")).samples());
            } catch (Exception e) {
                Totality.LOGGER.error("[Totality Hologram Capture] cannot arm", e);
            }
        }));
        s.add(HologramCapture.run("Alt for dictation", () -> zcylas.totality.init.ModKeybinds.simulatePhysicalPress(
                zcylas.totality.init.ModKeybinds.RADIAL_MODIFIER, true)));
        s.add(debugKey("B down (Alt+B)", () -> zcylas.totality.init.ModKeybinds.VOICE_PUSH_TO_TALK, org.lwjgl.glfw.GLFW.GLFW_KEY_B, true));
        int ticks = wavTicks("hello_there");
        for (int i = 0; i < ticks; i++) s.add(HologramCapture.waitTicks(1));
        s.add(HologramCapture.check("Alt+B after the F3 tests: dictation listening", () -> controller.state() == VoiceState.LISTENING
                && controller.utteranceMode() == DICTATION));
        s.add(debugKey("B up", () -> zcylas.totality.init.ModKeybinds.VOICE_PUSH_TO_TALK, org.lwjgl.glfw.GLFW.GLFW_KEY_B, false));
        s.add(HologramCapture.run("Alt up", () -> zcylas.totality.init.ModKeybinds.simulatePhysicalPress(
                zcylas.totality.init.ModKeybinds.RADIAL_MODIFIER, false)));
        s.add(HologramCapture.until("utterance finished", () -> controller.state() == VoiceState.READY, () -> {}, 20 * 20));
        s.add(HologramCapture.check("Alt+B after the F3 tests: transcribed normally", () -> "Hello there".equals(controller.latestTranscript())));
        s.add(HologramCapture.run("hitboxes and the F3 overlay back as they were", () -> {
            if (hitboxes()) mc.debugEntries.toggleStatus(net.minecraft.client.gui.components.debug.DebugScreenEntries.ENTITY_HITBOXES);
            // Pressing and releasing F3 on its own (the cancel test) toggles vanilla's debug overlay.
            if (mc.getDebugOverlay().showDebugScreen() != overlayBefore[0]) mc.debugEntries.toggleDebugOverlay();
        }));
        s.add(HologramCapture.command("kill @e[type=minecraft:pig]"));
        s.add(HologramCapture.waitTicks(10));
        return s;
    }

    /** Push-to-talk only starts while the game window has focus (an existing guard): record it next to the checks. */
    private static HologramCapture.Step focusLog(String label) {
        return m -> {
            HologramCapture.log("info: " + label + ": game window focused = " + m.isWindowActive());
            return true;
        };
    }

    private static boolean hitboxes() {
        return net.minecraft.client.Minecraft.getInstance().debugEntries.isCurrentlyEnabled(
                net.minecraft.client.gui.components.debug.DebugScreenEntries.ENTITY_HITBOXES);
    }

    private static boolean chunkBorders() {
        return net.minecraft.client.Minecraft.getInstance().debugEntries.isCurrentlyEnabled(
                net.minecraft.client.gui.components.debug.DebugScreenEntries.CHUNK_BORDERS);
    }

    /** A key both as vanilla's keyboard handler sees it (a real key event) and as physically held (Totality's reads). */
    private static HologramCapture.Step debugKey(String label, java.util.function.Supplier<net.minecraft.client.KeyMapping> mapping, int glfwKey, boolean down) {
        return m -> {
            zcylas.totality.init.ModKeybinds.simulatePhysicalPress(mapping.get(), down);
            vanillaKeyEvent(glfwKey, down);
            HologramCapture.log("key: " + label);
            return true;
        };
    }

    private static HologramCapture.Step vanillaKeyOnly(String label, int glfwKey, boolean down) {
        return m -> {
            vanillaKeyEvent(glfwKey, down);
            HologramCapture.log("key: " + label);
            return true;
        };
    }

    /** Dev capture only: feeds vanilla's private KeyboardHandler.keyPress the event a real key would. */
    private static void vanillaKeyEvent(int glfwKey, boolean down) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        try {
            var m = net.minecraft.client.KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, net.minecraft.client.input.KeyEvent.class);
            m.setAccessible(true);
            m.invoke(mc.keyboardHandler, mc.getWindow().handle(), down ? org.lwjgl.glfw.GLFW.GLFW_PRESS : org.lwjgl.glfw.GLFW.GLFW_RELEASE,
                    new net.minecraft.client.input.KeyEvent(glfwKey, org.lwjgl.glfw.GLFW.glfwGetKeyScancode(glfwKey), 0));
        } catch (ReflectiveOperationException e) {
            HologramCapture.log("FAIL: cannot send a key event: " + e);
        }
    }

    /** Logs where the widget sits against an open first-person panel (both in GUI px below the centre). */
    private static HologramCapture.Step clearsPanel(String label) {
        return mc -> {
            float bottom = zcylas.totality.client.hologram.HologramRenderer.firstPersonPanelBottom();
            if (Float.isNaN(bottom)) {
                HologramCapture.log("info: " + label + " — no first-person panel on screen; widget at its default place");
                return true;
            }
            float half = mc.getWindow().getGuiScaledHeight() / 2f;
            float panel = half * bottom, top = VoiceHud.lastTop();
            HologramCapture.log(String.format(java.util.Locale.ROOT, "%s: %s — panel lower edge %.1f, widget top %.1f GUI px below the centre (GUI height %.0f)",
                    top >= panel ? "PASS" : "FAIL", "HUD clears the open System panel (" + label + ")", panel, top, half * 2));
            return true;
        };
    }

    private static HologramCapture.Step showConfirm() {
        return new HologramCapture.Step() {
            int t;

            @Override
            public boolean tick(net.minecraft.client.Minecraft mc) {
                if (t++ == 0) HologramManager.show(HologramShowcase.confirm());
                return t > 30;
            }
        };
    }

    private static HologramCapture.Step noAction(String label) {
        return HologramCapture.check(label, () -> HologramShowcase.PERFORMED.isEmpty()
                && CONFIRM_KEY.equals(HologramManager.activeKey()));
    }

    /** Push-to-talk held for the WAV (plus a margin), then released; waits for the result. */
    private static List<HologramCapture.Step> utter(VoiceInputController controller, String wav, @Nullable String midShot,
                                                    VoiceInputController.PushToTalkMode mode) {
        List<HologramCapture.Step> s = new ArrayList<>();
        s.add(arm(controller, wav, mode));
        if (midShot != null) {
            s.add(HologramCapture.waitTicks(8));
            s.add(HologramCapture.screenshot(midShot));
            s.add(HologramCapture.waitTicks(Math.max(1, wavTicks(wav) - 8)));
        } else {
            s.add(HologramCapture.waitTicks(wavTicks(wav)));
        }
        s.addAll(release(controller));
        return s;
    }

    private static HologramCapture.Step arm(VoiceInputController controller, String wav,
                                            VoiceInputController.PushToTalkMode mode) {
        return HologramCapture.run((mode == COMMAND ? "B" : "Alt+B") + " + '" + wav + ".wav'", () -> {
            try {
                PcmAudio audio = WavPcmReader.read(wavPath(wav));
                Objects.requireNonNull(injector).arm(audio.samples());
                driving = true;
                controller.onPushToTalkPressed(mode);
            } catch (Exception e) {
                Totality.LOGGER.error("[Totality Hologram Capture] cannot inject {}", wav, e);
            }
        });
    }

    private static List<HologramCapture.Step> release(VoiceInputController controller) {
        return List.of(
                HologramCapture.run("release push-to-talk", controller::onPushToTalkReleased),
                HologramCapture.until("utterance finished", () -> controller.state() == VoiceState.READY, () -> {}, 20 * 20),
                HologramCapture.run("stop driving", () -> driving = false));
    }

    private static Path wavPath(String name) {
        return Path.of(System.getProperty(WAV_DIR_PROPERTY), name + ".wav");
    }

    private static int wavTicks(String name) {
        try {
            long bytes = Files.size(wavPath(name));
            return (int) Math.ceil((bytes - 44) / 2.0 / 16_000 * 20) + 6;
        } catch (Exception e) {
            return 40;
        }
    }
}
