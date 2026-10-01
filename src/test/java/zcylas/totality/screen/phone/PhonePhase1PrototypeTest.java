package zcylas.totality.screen.phone;

import org.junit.jupiter.api.Test;
import zcylas.totality.screen.phone.PhoneNotificationShade.Entry;
import zcylas.totality.screen.phone.PhoneNotificationShade.Severity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Basic Copper Phone Phase 1: home-screen layout rules, shade data rules, and the Phone equipment-slot icon. */
class PhonePhase1PrototypeTest {

    private static final Path GRID = Path.of("src/main/java/zcylas/totality/screen/phone/PhoneAppGridScreen.java");
    private static final Path SLOTS = Path.of("src/main/resources/assets/totality/textures/gui/sprites/container/slot");

    @Test
    void relativeTimestampsAreCompact() {
        long m = 60_000L, h = 60 * m;
        assertEquals("now", PhoneNotificationShade.relativeTime(20_000));
        assertEquals("2m ago", PhoneNotificationShade.relativeTime(2 * m + 5_000));
        assertEquals("59m ago", PhoneNotificationShade.relativeTime(59 * m));
        assertEquals("1h ago", PhoneNotificationShade.relativeTime(h + 4 * m));
        assertEquals("23h ago", PhoneNotificationShade.relativeTime(23 * h + 59 * m));
        assertEquals("Yesterday", PhoneNotificationShade.relativeTime(30 * h));
        assertEquals("3d ago", PhoneNotificationShade.relativeTime(72 * h));
    }

    @Test
    void shadeListsNewestFirstAndIndicatesHighestUrgencyAndTotalCount() {
        List<Entry> list = new ArrayList<>(List.of(
                entry("a", Severity.NORMAL, 100), entry("b", Severity.CRITICAL, 50), entry("c", Severity.IMPORTANT, 300)));
        PhoneNotificationShade shade = new PhoneNotificationShade(list);
        assertEquals(List.of("c", "a", "b"), shade.ordered().stream().map(Entry::id).toList());
        assertEquals(Severity.CRITICAL, shade.highest());
        assertEquals(3, shade.count());
        shade.dismiss(list.get(1));
        assertEquals(Severity.IMPORTANT, shade.highest(), "the colour follows the highest UNREAD urgency");
        shade.clearAll();
        assertNull(shade.highest(), "no unread notifications: the status-bar indicator is hidden");
        assertEquals(0, shade.count());
    }

    @Test
    void shadeOpensAndClosesBySnapAndExpandsPerEntry() {
        Entry e = entry("x", Severity.NORMAL, 1);
        PhoneNotificationShade shade = new PhoneNotificationShade(new ArrayList<>(List.of(e)));
        assertFalse(shade.isOpen());
        shade.snap(true);
        assertTrue(shade.isOpen());
        assertEquals(1f, shade.openAmount());
        shade.setExpanded(e, true);
        assertTrue(shade.isExpanded(e));
        shade.dismiss(e);
        assertFalse(shade.isExpanded(e), "Mark as Read / dismiss forgets the entry");
        shade.snap(false);
        assertFalse(shade.isOpen());
    }

    @Test
    void prototypeTypesAreIndependentOfUrgency() {
        boolean was = PhonePrototype.enabled;
        try {
            PhonePrototype.reset();
            List<Entry> all = PhonePrototype.notifications();
            assertEquals(9, all.size());
            // One type at three urgencies, with one colour.
            List<Entry> system = all.stream().filter(e -> e.app().equals("System")).toList();
            assertEquals(3, system.stream().map(Entry::severity).distinct().count(), "System at normal, important and critical");
            assertEquals(1, system.stream().map(Entry::typeColor).distinct().count(), "a type keeps its look at every urgency");
            // Several types sharing one urgency, each with its own look.
            List<Entry> normal = all.stream().filter(e -> e.severity() == Severity.NORMAL).toList();
            assertTrue(normal.stream().map(Entry::typeColor).distinct().count() >= 5, "normal urgency spans many types");
            PhonePrototype.use(PhonePrototype.Scenario.NORMAL);
            assertEquals(Severity.NORMAL, new PhoneNotificationShade(PhonePrototype.notifications()).highest());
            PhonePrototype.use(PhonePrototype.Scenario.IMPORTANT);
            assertEquals(Severity.IMPORTANT, new PhoneNotificationShade(PhonePrototype.notifications()).highest());
            PhonePrototype.off();
            assertTrue(PhonePrototype.notifications().isEmpty(), "off: no test notifications");
            assertEquals(0, PhonePrototype.badge("Mail"), "off: no test badges");
            assertFalse(PhonePrototype.labelStress || PhonePrototype.showBounds);
        } finally {
            PhonePrototype.off();
            PhonePrototype.enabled = was;
        }
    }

    @Test
    void devCommandIsDevelopmentOnlyAndSynthetic() throws Exception {
        String src = Files.readString(Path.of("src/main/java/zcylas/totality/client/phone/PhoneDevCommand.java"));
        assertTrue(src.contains("if (!VerificationReporter.isDevEnvironment()) return;"), "registered only in a development environment");
        assertTrue(src.contains("ClientCommandRegistrationCallback.EVENT.register"), "a client-side command, like /totalityhologram");
        assertTrue(src.contains("static final String ROOT = \"totalityphone\";"));
        for (String forbidden : new String[] {"ClientPlayNetworking", "Payload", "EntitlementService", "ClientEntitlementView",
                "Wallet", "Currency", "NotificationManager", "HologramManager", "PHONE_SETUP_COMPLETE", "addSkillXp"}) {
            assertFalse(src.contains(forbidden), "the test command never touches " + forbidden);
        }
        for (String option : new String[] {"home", "pages", "normal", "important", "critical", "shade", "expanded", "empty",
                "labels", "bounds", "reset", "off"}) {
            assertTrue(src.contains("option(root, \"" + option + "\""), "option " + option + " registered");
            assertTrue(src.contains("{\"" + option + "\", "), "option " + option + " listed in help");
        }
        String client = Files.readString(Path.of("src/main/java/zcylas/totality/TotalityClient.java"));
        assertTrue(client.contains("PhoneDevCommand.registerIfDevelopmentEnvironment();"));
    }

    @Test
    void badgesCapAtNinetyNinePlus() {
        assertEquals("7", PhoneUi.badgeText(7));
        assertEquals("99", PhoneUi.badgeText(99));
        assertEquals("99+", PhoneUi.badgeText(128));
    }

    @Test
    void prototypeDataIsOffInNormalPlay() {
        assertFalse(Boolean.getBoolean(PhonePrototype.PROPERTY));
        assertFalse(PhonePrototype.enabled);
        assertTrue(PhonePrototype.notifications().isEmpty(), "no test notifications in normal play");
        assertEquals(0, PhonePrototype.badge("Mail"), "no test badges in normal play");
    }

    @Test
    void homeScreenOrderDockAndEntitlementWiring() throws Exception {
        String src = Files.readString(GRID);
        String[] grid = {"Codex", "Map", "Inventory", "Spells", "Abilities", "Classes",
                "Technology", "Bank", "Store", "System", "Mail", "Settings"};
        int last = -1;
        for (String app : grid) {
            int at = src.indexOf("all.add(new App(\"" + app + "\"");
            assertTrue(at > last, app + " in its row-major position");
            last = at;
        }
        for (String fav : new String[] {"Character", "Skills", "Quests", "Camera"}) {
            assertTrue(src.contains("dock.add(new App(\"" + fav + "\""), fav + " is a favourite");
            assertFalse(src.contains("all.add(new App(\"" + fav + "\""), fav + " is not repeated in the grid");
        }
        assertTrue(src.contains("ClientEntitlementView.isSelectable(PhoneAppEntitlements.BANK_APP)"), "Bank stays on the Entitlement API");
        assertFalse(src.contains("\"TOTALITY\""), "no carrier name in the status bar");
        int tech = src.indexOf("all.add(new App(\"Technology\"");
        String techLine = src.substring(tech, src.indexOf('\n', tech));
        assertTrue(techLine.contains("\"Tech\", \"Te\", false, \"Not available yet.\""), "Technology: visible, unavailable, short label");
        assertFalse(techLine.contains("Entitlement"), "no invented entitlement for Technology");
        assertTrue(src.contains("all.add(new App(\"Inventory\",  \"Inv.\""), "Inventory's short display label");
        assertFalse(Files.readString(Path.of("src/main/java/zcylas/totality/screen/phone/PhoneDeviceStyle.java")).contains("Display"),
                "the device style carries no OS colours");
    }

    @Test
    void phoneSlotIconMatchesTheOtherEquipmentIcons() throws Exception {
        BufferedImage phone = ImageIO.read(SLOTS.resolve("phone.png").toFile());
        assertEquals(16, phone.getWidth());
        assertEquals(16, phone.getHeight());
        int ink = ImageIO.read(SLOTS.resolve("ring.png").toFile()).getRGB(10, 1);
        int opaque = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int argb = phone.getRGB(x, y);
                assertTrue(argb >>> 24 == 0 || argb == ink, "single-colour line art like ring/pouch/back");
                if (argb >>> 24 != 0) opaque++;
            }
        }
        assertTrue(opaque > 30 && opaque < 80, "an outline illustration, not a filled item: " + opaque);
    }

    private static Entry entry(String id, Severity s, long time) {
        return new Entry(id, "App", "Ap", null, 0xFF3CC4EE, s, "Message", List.of(), time);
    }
}
