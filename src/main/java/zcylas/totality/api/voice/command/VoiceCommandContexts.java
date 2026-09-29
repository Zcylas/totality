package zcylas.totality.api.voice.command;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Where Voice Input asks "is anything listening for a command right now?" when push-to-talk starts.
 * One source for now (System holograms); when a later owner needs voice commands this becomes an
 * ordered list of sources. Without a context, push-to-talk is ordinary dictation, unchanged.
 */
public final class VoiceCommandContexts {

    private static volatile Supplier<VoiceCommandContext> source = () -> null;
    private static volatile Supplier<String> absence = () -> "no command source registered";

    private VoiceCommandContexts() {}

    /**
     * @param supplier the current context, or null
     * @param whyNone  a short explanation of why nothing is offered right now (development diagnostics)
     */
    public static void setSource(Supplier<VoiceCommandContext> supplier, Supplier<String> whyNone) {
        source = supplier == null ? () -> null : supplier;
        absence = whyNone == null ? () -> "unknown" : whyNone;
    }

    /** Why {@link #current()} is null right now (development diagnostics only). */
    public static String describeAbsence() {
        return absence.get();
    }

    /** The active context, or null when push-to-talk should dictate as usual. Client thread. */
    public static @Nullable VoiceCommandContext current() {
        VoiceCommandContext context = source.get();
        return context == null || context.phrases().isEmpty() || !context.isCurrent() ? null : context;
    }
}
