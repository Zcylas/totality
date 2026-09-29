package zcylas.totality.client.hologram.dev;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.client.hologram.HologramAction;
import zcylas.totality.client.hologram.HologramIcon;
import zcylas.totality.client.hologram.HologramManager;
import zcylas.totality.client.hologram.HologramPriority;
import zcylas.totality.client.hologram.HologramSpec;
import zcylas.totality.client.hologram.HologramStyle;
import zcylas.totality.client.renderer.hud.notification.NotificationManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Development-only visual showcase of Notification V2: {@code /totalityhologram <sample>}. Client-side
 * command (never sent to the server), registered only in a Fabric development environment. Every
 * sample is presentation only — it grants nothing, issues no quest and touches no player data; the
 * interactive sample just reports the chosen button in the feed.
 */
public final class HologramShowcase {

    static final String ROOT = "totalityhologram";
    private static final String PREVIEW = "Showcase preview — nothing was issued or granted.";
    private static final List<Scheduled> SCHEDULED = new ArrayList<>();
    /** Every showcase action performed (click or voice), for the capture run's checks. */
    public static final List<String> PERFORMED = new ArrayList<>();
    private static int tick;

    private record Scheduled(int at, Runnable action) {}

    private HologramShowcase() {}

    public static void registerIfDevelopmentEnvironment() {
        if (!VerificationReporter.isDevEnvironment()) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> runScheduled());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal(ROOT);
            sample(root, "feed", HologramShowcase::feed);
            sample(root, "system", () -> HologramManager.show(dailyQuest()));
            sample(root, "success", () -> HologramManager.show(questComplete()));
            sample(root, "warning", () -> HologramManager.show(warning()));
            sample(root, "error", () -> HologramManager.show(urgent()));
            sample(root, "confirm", () -> HologramManager.show(confirm()));
            sample(root, "loading", () -> HologramManager.show(loading()));
            sample(root, "queue", HologramShowcase::queue);
            sample(root, "all", HologramShowcase::all);
            sample(root, "clear", HologramManager::clear);
            dispatcher.register(root);
        });
    }

    private static void sample(LiteralArgumentBuilder<FabricClientCommandSource> root, String name, Runnable action) {
        root.then(ClientCommands.literal(name).executes(ctx -> {
            action.run();
            return 1;
        }));
    }

    // ── Samples ───────────────────────────────────────────────────────────────

    /** Routine feed lines (the evolved V1 path, unchanged API). */
    public static void feed() {
        NotificationManager.add("Showcase · Quest updated: Gather Resources (2/5)", 0xFF6FE0FF);
        NotificationManager.add("Showcase · Spell learned: Fire Bolt", 0xFFB58CFF);
        NotificationManager.add("Showcase · You would receive 3x Iron Ingot", 0xFFFFC857);
        NotificationManager.add("Showcase · Level up preview: 15", 0xFF7CF08A);
    }

    public static HologramSpec dailyQuest() {
        return HologramSpec.builder("totality:showcase/daily_quest")
                .style(HologramStyle.SYSTEM)
                .header("System — Daily Quest")
                .title("A New Daily Quest Has Arrived")
                .body(Component.literal("Complete all ").append(highlight("four objectives", HologramStyle.SYSTEM))
                        .append(" before the day ends to claim every reward."))
                .footnote(Component.literal(PREVIEW))
                .dismissButton()
                .voiceCommands()
                .lifetimeTicks(20 * 30)
                .build();
    }

    public static HologramSpec questComplete() {
        return HologramSpec.builder("totality:showcase/daily_complete")
                .style(HologramStyle.SUCCESS)
                .header("System — Daily Quest")
                .title("Daily Quest Complete")
                .body("You have completed all objectives.")
                .body("Long Rest, Random Reward Box and Reward Choice are ready to claim.")
                .footnote(Component.literal(PREVIEW))
                .dismissButton()
                .lifetimeTicks(20 * 30)
                .build();
    }

    public static HologramSpec warning() {
        HologramStyle s = HologramStyle.WARNING;
        return HologramSpec.builder("totality:showcase/warning")
                .priority(HologramPriority.HIGH)
                .style(s)
                .header("System — Warning")
                .title("Daily Quest Incomplete")
                .body(Component.literal("If you do not complete this Daily Quest, there is a ")
                        .append(highlight("chance", s)).append(" that a ")
                        .append(highlight("Punishment Quest", HologramStyle.ERROR)).append(" may be issued."))
                .footnote(Component.literal(PREVIEW))
                .dismissButton()
                .lifetimeTicks(20 * 30)
                .build();
    }

    public static HologramSpec urgent() {
        return HologramSpec.builder("totality:showcase/urgent")
                .priority(HologramPriority.CRITICAL)
                .style(HologramStyle.ERROR)
                .header("System — Urgent")
                .title("A Powerful Enemy Has Appeared")
                .body("Multiple hostile signatures detected nearby.")
                .footnote(Component.literal(PREVIEW))
                .action(new HologramAction("acknowledge", Component.literal("Acknowledge"), true))
                .untilDismissed()
                .onAction((spec, id) -> NotificationManager.add("Showcase · Urgent warning acknowledged.", 0xFFFF8A97))
                .build();
    }

    public static HologramSpec confirm() {
        return HologramSpec.builder("totality:showcase/confirm")
                .style(HologramStyle.SYSTEM)
                .icon(HologramIcon.DIAMOND)
                .header("System — Confirmation")
                .title("Accept This Quest?")
                .body("The System only forwards your choice; the owning system decides what it means.")
                .body("Touch a button (left click) or hold push-to-talk and say it.")
                .footnote(Component.literal(PREVIEW))
                .action(new HologramAction("confirm", Component.literal("Confirm"), true))
                .action(new HologramAction("cancel", Component.literal("Cancel"), false))
                .voiceCommands()
                .untilDismissed()
                .onAction((spec, id) -> {
                    PERFORMED.add(id);
                    NotificationManager.add("Showcase · '" + id + "' selected — no gameplay effect.", 0xFF6FE0FF);
                })
                .build();
    }

    public static HologramSpec loading() {
        return HologramSpec.builder("totality:showcase/loading")
                .priority(HologramPriority.LOW)
                .style(HologramStyle.SYSTEM)
                .icon(HologramIcon.SPINNER)
                .size(HologramSpec.Size.COMPACT)
                .header("System — Synchronizing")
                .title("Establishing Link")
                .body("Indeterminate activity; no percentage is invented.")
                .indeterminateProgress()
                .lifetimeTicks(20 * 12)
                .build();
    }

    /** NORMAL quest notice, interrupted by a CRITICAL warning; dismissing the warning resumes it. */
    public static void queue() {
        HologramManager.clear();
        HologramManager.show(dailyQuest());
        schedule(50, () -> HologramManager.show(urgent()));
        schedule(55, () -> HologramManager.show(loading()));
    }

    /** One of each, queued in priority order. */
    public static void all() {
        HologramManager.clear();
        feed();
        HologramManager.show(loading());
        HologramManager.show(dailyQuest());
        HologramManager.show(questComplete());
        HologramManager.show(confirm());
        HologramManager.show(warning());
    }

    private static Component highlight(String text, HologramStyle style) {
        return Component.literal(text).withColor(style.highlight);
    }

    // ── Tiny client-tick scheduler for timed samples ─────────────────────────

    static void schedule(int delayTicks, Runnable action) {
        SCHEDULED.add(new Scheduled(tick + delayTicks, action));
    }

    private static void runScheduled() {
        tick++;
        for (Iterator<Scheduled> it = SCHEDULED.iterator(); it.hasNext(); ) {
            Scheduled s = it.next();
            if (s.at() <= tick) {
                it.remove();
                s.action().run();
            }
        }
    }
}
