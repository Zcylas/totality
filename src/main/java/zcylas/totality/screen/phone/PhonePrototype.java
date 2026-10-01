package zcylas.totality.screen.phone;

import net.minecraft.resources.Identifier;
import zcylas.totality.screen.phone.PhoneNotificationShade.Entry;
import zcylas.totality.screen.phone.PhoneNotificationShade.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DEVELOPMENT-ONLY test data for the Basic Copper Phone Phase 1 visual prototype. Off in normal play: then the
 * home screen has one page, no badges, no status-bar notification indicator, and the shade is empty.
 *
 * <p>Turn it on with {@code /totalityphone} (development client command, see {@code client/phone/PhoneDevCommand}),
 * or {@code -Dtotality.phone.prototype=true} (e.g. through {@code JAVA_TOOL_OPTIONS} for {@code runClient}); the
 * capture run sets the fields directly. Nothing here is saved or sent anywhere, and none of it reaches Notification
 * API V2.
 */
public final class PhonePrototype {

    public static final String PROPERTY = "totality.phone.prototype";

    /** Test badges, test notifications and a development page on either side of the main home page. */
    public static volatile boolean enabled = Boolean.getBoolean(PROPERTY);
    /** Fills the right development page with increasingly long test labels (label-fit check). */
    public static volatile boolean labelStress;
    /** Outlines every interactive region exactly as hit-testing sees it. */
    public static volatile boolean showBounds;

    /** Which test notifications the shade and status bar show; the name is the highest urgency present. */
    public enum Scenario { NONE, NORMAL, IMPORTANT, CRITICAL }

    /**
     * PROVISIONAL visual types for the prototype only (icon tile, abbreviation and source-name colour). They show that
     * a notification's look is independent of its urgency; they are not Notification V3 categories.
     */
    enum Look {
        SYSTEM(0xFF9FB0C4), QUESTS(0xFFA98BF5), CODEX(0xFF3CC4EE), BANK(0xFF2FC9B0), MAIL(0xFF6F8DFF), SKILLS(0xFFE070C0);

        final int color;

        Look(int color) {
            this.color = color;
        }
    }

    private static final List<Entry> NOTIFICATIONS = new ArrayList<>();
    private static boolean filled;

    private PhonePrototype() {}

    /** Neutral test badge counts (unread notifications + unresolved actions, each item counted once). */
    private static final Map<String, Integer> BADGES = Map.of(
            "Codex", 3, "Inventory", 128, "Bank", 1, "System", 2, "Mail", 12, "Skills", 4, "Quests", 1);

    public static int badge(String app) {
        return enabled ? BADGES.getOrDefault(app, 0) : 0;
    }

    /** The live list the shade shows and dismisses from (empty unless the prototype is enabled). */
    public static List<Entry> notifications() {
        if (!enabled) return new ArrayList<>();
        if (!filled) use(Scenario.CRITICAL);
        return NOTIFICATIONS;
    }

    /**
     * Replaces the test notifications (ages are relative to now, so timestamps read the same every run). Every set
     * mixes several types at normal urgency; IMPORTANT adds Quests and System at important urgency; CRITICAL adds a
     * critical System alert — so System appears at all three urgencies and Quests at two.
     */
    public static void use(Scenario set) {
        filled = true;
        NOTIFICATIONS.clear();
        long now = System.currentTimeMillis();
        long m = 60_000L, h = 60 * m;
        if (set == Scenario.NONE) return;
        NOTIFICATIONS.add(entry("codex", "Codex", "Cx", PhoneAppGridScreen.CODEX_ICON, Look.CODEX, Severity.NORMAL,
                "New entry unlocked: Forest Boar.", List.of("Open the Codex to read the full entry."), now - h - 4 * m));
        NOTIFICATIONS.add(entry("bank", "Bank", "Bk", null, Look.BANK, Severity.NORMAL,
                "Deposit received: ₵ 250.", List.of("Current balance is shown in the Bank app."), now - 3 * h - 2 * m));
        NOTIFICATIONS.add(entry("mail", "Mail", "Ma", null, Look.MAIL, Severity.NORMAL,
                "You have 2 new messages.", List.of(), now - 5 * h - 10 * m));
        NOTIFICATIONS.add(entry("skills", "Skills", "Sk", null, Look.SKILLS, Severity.NORMAL,
                "Mining reached level 5.", List.of(), now - 20 * h));
        NOTIFICATIONS.add(entry("setup", "System", "Sy", null, Look.SYSTEM, Severity.NORMAL,
                "Phone setup complete.", List.of(), now - 30 * h));
        NOTIFICATIONS.add(entry("quest2", "Quests", "Qu", null, Look.QUESTS, Severity.NORMAL,
                "Quest completed.", List.of(), now - 3 * 24 * h));
        if (set == Scenario.NORMAL) return;
        NOTIFICATIONS.add(entry("quest", "Quests", "Qu", null, Look.QUESTS, Severity.IMPORTANT,
                "Quest updated: speak with the Banker.",
                List.of("Development test notification: Quests type, important urgency, expanded layout."), now - 15 * m));
        NOTIFICATIONS.add(entry("system_notice", "System", "Sy", null, Look.SYSTEM, Severity.IMPORTANT,
                "Signal is weak in this area.",
                List.of("Development test notification: System type, important urgency."), now - 40 * m));
        if (set == Scenario.IMPORTANT) return;
        NOTIFICATIONS.add(entry("system", "System", "Sy", null, Look.SYSTEM, Severity.CRITICAL,
                "Unknown signal detected near your location.",
                List.of("Development test notification: System type, critical urgency."), now - 2 * m));
    }

    private static Entry entry(String id, String app, String abbreviation, Identifier icon, Look look,
                               Severity severity, String message, List<String> details, long time) {
        return new Entry(id, app, abbreviation, icon, look.color, severity, message, details, time);
    }

    /** Restores the initial testing state: prototype on, full test notifications, no label stress, no bounds. */
    public static void reset() {
        enabled = true;
        labelStress = false;
        showBounds = false;
        use(Scenario.CRITICAL);
    }

    /** Back to the ordinary Phone presentation (test data off). */
    public static void off() {
        enabled = false;
        labelStress = false;
        showBounds = false;
        filled = false;
        NOTIFICATIONS.clear();
    }

    /** Test labels for the label-fit page, shortest to longest (not apps: nothing opens). */
    public static final List<String> STRESS_LABELS = List.of(
            "Map", "Classes", "Settings", "Inventory", "Technology", "Achievements",
            "Notifications", "World Map", "Quest Journal", "Trading Post", "Player Statistics",
            "Very Long Application Name");
}
