package zcylas.totality.client.hologram;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Everything a System hologram shows and how it behaves. Immutable; build with {@link #builder}.
 *
 * <p><b>Identity.</b> {@link #id} names this particular message ("totality:voice/model_ready").
 * {@link #aggregationKey}, when set, groups messages that supersede one another
 * ("totality:voice/model_initialization": loading → ready/failed). Showing a spec whose {@link #key()}
 * matches one already on display updates it in place; one waiting in the queue or suspended is
 * replaced. Without an aggregation key the id is the key, so re-showing the same id never stacks
 * duplicates.
 *
 * <p><b>Voice.</b> {@link #voiceCommands} opts a hologram into spoken commands: its actions that carry a
 * voice intent (Confirm, Cancel, Dismiss — see {@link HologramVoice}) can then be triggered by command
 * push-to-talk. Off by default; currently used only by development showcases.
 *
 * <p><b>Lifetime.</b> {@link #lifetimeTicks} counts client ticks while the hologram is fully shown and
 * the player can see it (no screen open, HUD visible, game not paused, not being aimed at). 0 keeps
 * it until an action, a dismissal or a replacement.
 */
public record HologramSpec(
        String id,
        @Nullable String aggregationKey,
        HologramPriority priority,
        HologramStyle style,
        HologramIcon icon,
        Size size,
        Component header,
        Component title,
        List<Component> body,
        @Nullable Component footnote,
        List<HologramAction> actions,
        int lifetimeTicks,
        boolean voiceCommands,
        boolean indeterminateProgress,
        @Nullable HologramSound openSound,
        @Nullable ActionHandler actionHandler) {

    /** Panel width class, in hologram units (1 unit ≈ 1 GUI pixel at the default size). */
    public enum Size {
        COMPACT(200),
        STANDARD(248);

        public final int width;

        Size(int width) {
            this.width = width;
        }
    }

    /** Receives the id of the activated {@link HologramAction}; always on the client thread. */
    @FunctionalInterface
    public interface ActionHandler {
        void onAction(HologramSpec spec, String actionId);
    }

    public HologramSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(title, "title");
        body = List.copyOf(body);
        actions = List.copyOf(actions);
        if (lifetimeTicks < 0) throw new IllegalArgumentException("lifetimeTicks < 0");
    }

    /** What makes two holograms "the same message" for update/replace/dedup. */
    public String key() {
        return aggregationKey != null ? aggregationKey : id;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private @Nullable String aggregationKey;
        private HologramPriority priority = HologramPriority.NORMAL;
        private HologramStyle style = HologramStyle.SYSTEM;
        private @Nullable HologramIcon icon;
        private Size size = Size.STANDARD;
        private Component header = Component.literal("System");
        private Component title = Component.empty();
        private final List<Component> body = new ArrayList<>();
        private @Nullable Component footnote;
        private final List<HologramAction> actions = new ArrayList<>();
        private int lifetimeTicks = 200;
        private boolean voiceCommands;
        private boolean indeterminateProgress;
        private @Nullable HologramSound openSound;
        private boolean silent;
        private @Nullable ActionHandler actionHandler;

        private Builder(String id) {
            this.id = id;
        }

        public Builder aggregationKey(@Nullable String key) { this.aggregationKey = key; return this; }
        public Builder priority(HologramPriority priority) { this.priority = priority; return this; }
        public Builder style(HologramStyle style) { this.style = style; return this; }
        public Builder icon(HologramIcon icon) { this.icon = icon; return this; }
        public Builder size(Size size) { this.size = size; return this; }
        public Builder header(Component header) { this.header = header; return this; }
        public Builder header(String header) { return header(Component.literal(header)); }
        public Builder title(Component title) { this.title = title; return this; }
        public Builder title(String title) { return title(Component.literal(title)); }
        public Builder body(Component line) { this.body.add(line); return this; }
        public Builder body(String line) { return body(Component.literal(line)); }
        public Builder footnote(@Nullable Component footnote) { this.footnote = footnote; return this; }
        public Builder action(HologramAction action) { this.actions.add(action); return this; }
        public Builder dismissButton() { return action(HologramAction.dismiss()); }
        public Builder lifetimeTicks(int ticks) { this.lifetimeTicks = ticks; return this; }
        public Builder untilDismissed() { this.lifetimeTicks = 0; return this; }
        public Builder voiceCommands() { this.voiceCommands = true; return this; }
        public Builder indeterminateProgress() { this.indeterminateProgress = true; return this; }
        public Builder openSound(HologramSound sound) { this.openSound = sound; this.silent = false; return this; }
        public Builder silent() { this.silent = true; return this; }
        public Builder onAction(ActionHandler handler) { this.actionHandler = handler; return this; }

        public HologramSpec build() {
            HologramSound sound = silent ? null : openSound != null ? openSound : style.defaultSound;
            return new HologramSpec(id, aggregationKey, priority, style, icon != null ? icon : style.defaultIcon, size,
                    header, title, body, footnote, actions, lifetimeTicks, voiceCommands,
                    indeterminateProgress, sound, actionHandler);
        }
    }
}
