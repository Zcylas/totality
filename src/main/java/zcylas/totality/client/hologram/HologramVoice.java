package zcylas.totality.client.hologram;

import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.voice.command.VoiceCommandContext;
import zcylas.totality.api.voice.command.VoiceCommandContexts;
import zcylas.totality.api.voice.command.VoiceCommandMatcher;
import zcylas.totality.client.renderer.hud.notification.NotificationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spoken commands for System holograms. When push-to-talk starts, Voice Input asks for a context;
 * this offers one only for the DISPLAYED hologram, only once it is fully shown, only if it opted in
 * ({@link HologramSpec#voiceCommands()}), and only for the command words its own buttons carry — a
 * hologram without a "Confirm" button never listens for "confirm". A paused (suspended) hologram is
 * never offered.
 *
 * <p>The context is bound to that exact entry and spec: if the hologram closes, is updated, or is
 * interrupted while the utterance is being recognized, {@link Context#isCurrent()} fails and nothing
 * happens. A recognized command goes through {@link HologramManager#activate} — the same path as
 * clicking the button — so voice can never do more than a click could.
 */
final class HologramVoice {

    /**
     * Voice INTENTS and the spoken phrases each accepts. An intent is offered only when the displayed
     * hologram actually has the matching action ({@link #intentOf}). Every phrase still needs the full
     * dual-recognition check; "confirmed" leads Confirm because it was the phrase the bundled model
     * recognized reliably with a real microphone (10/10), where "confirm" alone was heard as
     * "come from"/"coffin"/"can feel".
     */
    static final Map<String, List<String>> SPOKEN = orderedMap(
            "confirm", List.of("confirmed", "confirm", "yes", "okay", "ok", "sure", "accept"),
            "cancel", List.of("cancel", "no", "decline", "reject"),
            "dismiss", List.of("dismiss", "close", "hide", "minimize", "minimise"));
    static final Set<String> SUPPORTED = SPOKEN.keySet();
    /** What the on-panel hint suggests saying for each intent (the most reliable phrase). */
    static final Map<String, String> HINT = Map.of("confirm", "Confirmed", "cancel", "Cancel", "dismiss", "Dismiss");

    /**
     * The intent an action carries, from its stable id. Dismiss is only the real
     * {@link HologramAction#DISMISS} action — closing a popup without making the negative decision —
     * never a stand-in for Cancel. (A "minimize but keep the decision pending" state does not exist yet,
     * so decision prompts without a Dismiss action do not offer it.)
     */
    static @Nullable String intentOf(HologramAction action) {
        return switch (action.id()) {
            case "confirm", "accept", "yes" -> "confirm";
            case "cancel", "decline", "no", "reject" -> "cancel";
            case HologramAction.DISMISS -> "dismiss";
            default -> null;
        };
    }

    private static Map<String, List<String>> orderedMap(Object... pairs) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked") List<String> phrases = (List<String>) pairs[i + 1];
            map.put((String) pairs[i], phrases);
        }
        return java.util.Collections.unmodifiableMap(map);
    }

    static final long FEEDBACK_NANOS = 2_200_000_000L;

    enum Status { LISTENING, ACCEPTED, REJECTED }

    private HologramVoice() {}

    static void register() {
        VoiceCommandContexts.setSource(HologramVoice::currentContext, HologramVoice::whyNoContext);
    }

    /** Intent → action id for the spec's actions that carry a voice intent (opted-in holograms only). */
    static Map<String, String> commands(HologramSpec spec) {
        Map<String, String> map = new LinkedHashMap<>();
        if (!spec.voiceCommands()) return map;
        for (HologramAction action : spec.actions()) {
            String intent = intentOf(action);
            if (intent != null) map.putIfAbsent(intent, action.id());
        }
        return map;
    }

    private static @Nullable VoiceCommandContext currentContext() {
        HologramStack.Entry active = HologramManager.stack().active();
        if (active == null || active.phase() != HologramStack.Phase.SHOWN) return null;
        Map<String, String> commands = commands(active.spec());
        return commands.isEmpty() ? null : new Context(active, active.spec(), commands);
    }

    private static String whyNoContext() {
        HologramStack.Entry active = HologramManager.stack().active();
        if (active == null) return "no hologram displayed" + (HologramManager.stack().suspendedCount() > 0 ? " (only paused ones)" : "");
        if (active.phase() != HologramStack.Phase.SHOWN) return "hologram '" + active.spec().id() + "' is " + active.phase();
        if (!active.spec().voiceCommands()) return "hologram '" + active.spec().id() + "' does not take voice commands";
        return "hologram '" + active.spec().id() + "' has no action with a voice intent";
    }

    static void show(HologramStack.Entry entry, Status status, String text) {
        entry.voiceStatus = status;
        entry.voiceText = text;
        entry.voiceNanos = HologramManager.stack().now();
    }

    static String describe(VoiceCommandMatcher.Rejection reason) {
        return switch (reason) {
            case NOT_RECOGNIZED -> "Not recognized — no action";
            case NOT_CORROBORATED, LOW_CONFIDENCE -> "Unclear — no action";
            case AMBIGUOUS -> "Ambiguous — no action";
            case TOO_LONG -> "Say a single command — no action";
            case NO_SPEECH -> "No speech heard — no action";
            case STALE -> "Prompt changed — no action";
            case CANCELLED -> "Voice cancelled";
        };
    }

    private record Context(HologramStack.Entry entry, HologramSpec spec, Map<String, String> commands)
            implements VoiceCommandContext {

        @Override
        public Set<String> phrases() {
            Set<String> spoken = new java.util.LinkedHashSet<>();
            for (String command : commands.keySet()) spoken.addAll(SPOKEN.get(command));
            return spoken;
        }

        @Override
        public String commandOf(String phrase) {
            for (Map.Entry<String, List<String>> e : SPOKEN.entrySet()) if (e.getValue().contains(phrase)) return e.getKey();
            return phrase;
        }

        @Override
        public boolean isCurrent() {
            return HologramManager.stack().active() == entry && entry.spec() == spec
                    && entry.phase() == HologramStack.Phase.SHOWN;
        }

        @Override
        public boolean execute(String command) {
            String actionId = commands.get(command);
            if (actionId == null || !isCurrent()) return false;
            show(entry, Status.ACCEPTED, "Heard: " + command.toUpperCase(java.util.Locale.ROOT));
            HologramManager.activate(entry, actionId);
            return true;
        }

        @Override
        public void listening() {
            if (isCurrent()) show(entry, Status.LISTENING, "Listening…");
        }

        @Override
        public void notAddressed() {
            if (isCurrent()) show(entry, Status.REJECTED, "Operator Mode — this prompt unchanged");
        }

        @Override
        public void rejected(VoiceCommandMatcher.Rejection reason) {
            if (HologramManager.stack().active() == entry && entry.spec() == spec
                    && entry.phase() != HologramStack.Phase.CLOSING) {
                show(entry, Status.REJECTED, describe(reason));
            } else if (reason != VoiceCommandMatcher.Rejection.CANCELLED) {
                // The prompt is gone or paused: a small feed line instead, never an action.
                NotificationManager.add("Voice command ignored: " + describe(reason).toLowerCase(java.util.Locale.ROOT), 0xFF8FB8D8);
            }
        }
    }
}
