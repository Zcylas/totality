package zcylas.totality.client.voice;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import zcylas.totality.api.voice.capture.AudioLevel;
import zcylas.totality.api.voice.capture.CaptureException;
import zcylas.totality.api.voice.capture.MicrophoneManager;

import java.io.IOException;
import java.util.List;

/**
 * {@code /totalityvoice} — client-only commands (resolved by Fabric on the client, never sent to the
 * server) for checking and configuring Voice Input: status, devices, device choice, a short
 * microphone level test (no recognition), enable/disable, retry after a failure, and debug output.
 */
final class VoiceCommands {

    static final String ROOT = "totalityvoice";
    private static final int TEST_SECONDS = 3;

    private VoiceCommands() {}

    static void register(VoiceInputController controller, VoiceSettings settings) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                withDevelopmentDiagnostics(controller, ClientCommands.literal(ROOT))
                        .executes(ctx -> status(ctx, controller, settings))
                        .then(ClientCommands.literal("status").executes(ctx -> status(ctx, controller, settings)))
                        .then(ClientCommands.literal("devices").executes(ctx -> devices(ctx, controller, settings)))
                        .then(ClientCommands.literal("device")
                                .then(ClientCommands.literal("default").executes(ctx -> selectDevice(ctx, controller, settings, 0)))
                                .then(ClientCommands.argument("number", IntegerArgumentType.integer(1))
                                        .executes(ctx -> selectDevice(ctx, controller, settings,
                                                IntegerArgumentType.getInteger(ctx, "number")))))
                        .then(ClientCommands.literal("test").executes(ctx -> test(ctx, controller, settings)))
                        .then(ClientCommands.literal("enable").executes(ctx -> setEnabled(ctx, controller, settings, true)))
                        .then(ClientCommands.literal("disable").executes(ctx -> setEnabled(ctx, controller, settings, false)))
                        .then(ClientCommands.literal("retry").executes(ctx -> retry(ctx, controller)))
                        .then(ClientCommands.literal("debug")
                                .then(ClientCommands.literal("on").executes(ctx -> debug(ctx, settings, true)))
                                .then(ClientCommands.literal("off").executes(ctx -> debug(ctx, settings, false))))));
    }

    /** {@code commanddiag on|off}: development environments only (see {@link VoiceCommandDiagnostics}). */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> withDevelopmentDiagnostics(
            VoiceInputController controller, com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> root) {
        if (!VoiceCommandDiagnostics.available()) return root;
        return root.then(ClientCommands.literal("commanddiag")
                .then(ClientCommands.literal("on").executes(ctx -> commandDiagnostics(ctx, controller, true)))
                .then(ClientCommands.literal("off").executes(ctx -> commandDiagnostics(ctx, controller, false))));
    }

    private static int commandDiagnostics(CommandContext<FabricClientCommandSource> ctx, VoiceInputController c, boolean on) {
        VoiceCommandDiagnostics.setEnabled(c, on);
        line(ctx.getSource(), "Voice command diagnostics " + (on ? "ON — results go only to " + VoiceCommandDiagnostics.logFile()
                + " (no audio is saved)" : "OFF"));
        return 1;
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx, VoiceInputController c, VoiceSettings s) {
        FabricClientCommandSource src = ctx.getSource();
        src.sendFeedback(Component.literal("Totality Voice: " + c.state()).withStyle(ChatFormatting.GOLD));
        String defaultDevice;
        try {
            defaultDevice = c.microphone().defaultDevice();
        } catch (CaptureException e) {
            defaultDevice = "unavailable (" + e.getMessage() + ")";
        }
        line(src, "Microphone: " + (s.device() == null ? "system default (" + defaultDevice + ")" : s.device()));
        line(src, "Push-to-talk: " + zcylas.totality.init.ModKeybinds.VOICE_PUSH_TO_TALK.getTranslatedKeyMessage().getString()
                + ", Edit & Send: " + zcylas.totality.init.ModKeybinds.VOICE_EDIT_TRANSCRIPT.getTranslatedKeyMessage().getString());
        if (c.lastLoadMillis() >= 0) line(src, "Model load: " + c.lastLoadMillis() + " ms");
        if (c.lastFailure() != null) line(src, "Failure: " + c.lastFailure().detail());
        VoiceInputController.UtteranceStats u = c.lastStats();
        if (u != null) {
            line(src, String.format("Last phrase: %s, %.2f s, peak %.1f dBFS, finish %d ms, release→result %d ms",
                    u.outcome(), u.audioSeconds(), u.peakDbfs(), u.finishMillis(), u.releaseToResultMillis()));
        }
        line(src, "Debug: " + (s.debug() ? "on" : "off"));
        return 1;
    }

    private static int devices(CommandContext<FabricClientCommandSource> ctx, VoiceInputController c, VoiceSettings s) {
        FabricClientCommandSource src = ctx.getSource();
        try {
            List<String> devices = c.microphone().devices();
            String def = c.microphone().defaultDevice();
            if (devices.isEmpty()) {
                src.sendError(Component.literal("No microphones found."));
                return 0;
            }
            src.sendFeedback(Component.literal("Microphones:").withStyle(ChatFormatting.GOLD));
            for (int i = 0; i < devices.size(); i++) {
                String d = devices.get(i);
                String marks = (d.equals(def) ? " (system default)" : "")
                        + (d.equals(s.device()) || (s.device() == null && d.equals(def)) ? " ← in use" : "");
                line(src, (i + 1) + ". " + d + marks);
            }
            line(src, "Choose with /" + ROOT + " device <number>, or /" + ROOT + " device default.");
            return devices.size();
        } catch (CaptureException e) {
            src.sendError(Component.literal("Cannot list microphones: " + e.getMessage()));
            return 0;
        }
    }

    private static int selectDevice(CommandContext<FabricClientCommandSource> ctx, VoiceInputController c,
                                    VoiceSettings s, int number) {
        FabricClientCommandSource src = ctx.getSource();
        if (number == 0) {
            s.setDevice(null);
        } else {
            try {
                List<String> devices = c.microphone().devices();
                if (number > devices.size()) {
                    src.sendError(Component.literal("There is no microphone " + number + " (see /" + ROOT + " devices)."));
                    return 0;
                }
                s.setDevice(devices.get(number - 1));
            } catch (CaptureException e) {
                src.sendError(Component.literal("Cannot list microphones: " + e.getMessage()));
                return 0;
            }
        }
        save(src, s);
        line(src, "Microphone: " + (s.device() == null ? "system default" : s.device()));
        return 1;
    }

    /** Records a few seconds and reports the level only; no speech recognition, nothing kept. */
    private static int test(CommandContext<FabricClientCommandSource> ctx, VoiceInputController c, VoiceSettings s) {
        FabricClientCommandSource src = ctx.getSource();
        if (!s.enabled()) {
            src.sendError(Component.literal("Voice Input is disabled (/" + ROOT + " enable)."));
            return 0;
        }
        if (c.state() == VoiceState.LISTENING || c.state() == VoiceState.RECOGNIZING) {
            src.sendError(Component.literal("Voice Input is busy."));
            return 0;
        }
        Minecraft client = Minecraft.getInstance();
        long target = (long) TEST_SECONDS * MicrophoneManager.SAMPLE_RATE;
        MicrophoneManager.Capture[] handle = new MicrophoneManager.Capture[1];
        long[] samples = {0};
        MicrophoneManager.Capture started = c.microphone().start(s.device(), new MicrophoneManager.CaptureListener() {
            @Override
            public void onStarted(String deviceName) {
                client.execute(() -> line(src, "Testing '" + deviceName + "' for " + TEST_SECONDS + " s — speak now…"));
            }

            @Override
            public void onAudio(short[] chunk, int count) {
                samples[0] += count;
                MicrophoneManager.Capture h = handle[0];
                if (samples[0] >= target && h != null) h.stop();
            }

            @Override
            public void onEnded(MicrophoneManager.CaptureResult r) {
                client.execute(() -> reportTest(src, r));
            }
        });
        if (started == null) {
            src.sendError(Component.literal("The microphone is already in use."));
            return 0;
        }
        handle[0] = started;
        return 1;
    }

    private static void reportTest(FabricClientCommandSource src, MicrophoneManager.CaptureResult r) {
        String level = String.format("%.2f s captured from '%s', peak %.1f dBFS", r.seconds(), r.deviceName(),
                AudioLevel.toDbfs(r.peak()));
        switch (r.reason()) {
            case OPEN_FAILED, DEVICE_LOST -> src.sendError(Component.literal("Microphone test failed: " + r.error()));
            case NO_DATA -> src.sendError(Component.literal("Microphone test: the device delivered no audio. " + level));
            default -> {
                if (r.peak() == 0) {
                    src.sendError(Component.literal("Microphone test: digital silence — the microphone is muted or "
                            + "switched off (for a headset, check its mute switch / boom mic). " + level));
                } else if (r.peak() < VoiceInputController.QUIET_PEAK) {
                    src.sendError(Component.literal("Microphone test: signal very quiet. " + level));
                } else {
                    src.sendFeedback(Component.literal("Microphone test OK: " + level).withStyle(ChatFormatting.GREEN));
                }
            }
        }
    }

    private static int setEnabled(CommandContext<FabricClientCommandSource> ctx, VoiceInputController c,
                                  VoiceSettings s, boolean enabled) {
        s.setEnabled(enabled);
        c.setEnabled(enabled);
        save(ctx.getSource(), s);
        line(ctx.getSource(), "Voice Input " + (enabled ? "enabled — hold push-to-talk to load and speak." : "disabled; microphone and model released."));
        return 1;
    }

    private static int retry(CommandContext<FabricClientCommandSource> ctx, VoiceInputController c) {
        if (!c.retry()) {
            line(ctx.getSource(), "Nothing to retry: Voice Input is " + c.state() + ".");
            return 0;
        }
        return 1;
    }

    private static int debug(CommandContext<FabricClientCommandSource> ctx, VoiceSettings s, boolean on) {
        s.setDebug(on);
        save(ctx.getSource(), s);
        line(ctx.getSource(), "Voice debug output " + (on ? "on (transcripts appear in the log)" : "off") + ".");
        return 1;
    }

    private static void save(FabricClientCommandSource src, VoiceSettings s) {
        try {
            s.save();
        } catch (IOException e) {
            src.sendError(Component.literal("Could not save voice settings: " + e.getMessage()));
        }
    }

    private static void line(FabricClientCommandSource src, String text) {
        src.sendFeedback(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }
}
