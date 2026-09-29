package zcylas.totality.client.voice;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import zcylas.totality.client.hologram.HologramIcon;
import zcylas.totality.client.hologram.HologramPriority;
import zcylas.totality.client.hologram.HologramSpec;
import zcylas.totality.client.hologram.HologramStyle;
import zcylas.totality.init.ModKeybinds;

/**
 * System holograms for the speech model's lazy initialization. All three share one aggregation key,
 * so "initializing" turns into "ready" or "unavailable" in place. Presentation only: dismissing any
 * of them changes nothing about Voice Input (the model stays loaded, push-to-talk keeps working, and
 * a failure still needs {@code /totalityvoice retry}).
 */
final class VoiceHolograms {

    static final String KEY = "totality:voice/model_initialization";
    private static final String HEADER = "System — Voice Input";

    private VoiceHolograms() {}

    /** @param detail the controller's own sentence, shown for failures */
    static HologramSpec modelInitialization(VoiceUi.Notice notice, String detail) {
        return switch (notice) {
            case LOADING -> loading();
            case READY -> ready();
            case CRASH_SUSPECTED -> failed("Speech Engine Halted", detail);
            default -> failed("Recognition Model Unavailable", detail);
        };
    }

    private static HologramSpec loading() {
        return HologramSpec.builder("totality:voice/model_loading")
                .aggregationKey(KEY)
                .priority(HologramPriority.LOW)
                .style(HologramStyle.SYSTEM)
                .icon(HologramIcon.SPINNER)
                .size(HologramSpec.Size.COMPACT)
                .header(HEADER)
                .title("Initializing Recognition Model")
                .body("Preparing offline speech recognition.")
                .footnote(Component.literal("The microphone stays off."))
                .indeterminateProgress()
                // Replaced by ready/failed; the limit only guards against a load that is abandoned
                // (voice disabled mid-load) and so never reports back.
                .lifetimeTicks(20 * 90)
                .build();
    }

    private static HologramSpec ready() {
        HologramStyle style = HologramStyle.SUCCESS;
        MutableComponent key = ModKeybinds.VOICE_PUSH_TO_TALK.getTranslatedKeyMessage().copy().withColor(style.highlight);
        return HologramSpec.builder("totality:voice/model_ready")
                .aggregationKey(KEY)
                .priority(HologramPriority.NORMAL)
                .style(style)
                .icon(HologramIcon.CHECK)
                .header(HEADER)
                .title("Recognition Model Ready")
                .body("The offline English recognition model has been initialized successfully.")
                .body(Component.literal("Hold ").append(key).append(" to answer the System; hold ")
                        .append(ModKeybinds.RADIAL_MODIFIER.getTranslatedKeyMessage().copy().withColor(style.highlight))
                        .append("+").append(key.copy()).append(" to dictate."))
                .footnote(Component.literal("The microphone opens only while the key is held."))
                .dismissButton()
                .lifetimeTicks(20 * 20)
                .build();
    }

    private static HologramSpec failed(String title, String detail) {
        return HologramSpec.builder("totality:voice/model_failed")
                .aggregationKey(KEY)
                .priority(HologramPriority.HIGH)
                .style(HologramStyle.ERROR)
                .header(HEADER)
                .title(title)
                .body(detail)
                .footnote(Component.literal("Voice Input will not retry automatically."))
                .dismissButton()
                .lifetimeTicks(20 * 45)
                .build();
    }
}
