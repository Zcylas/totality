package zcylas.totality.client.phone;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.client.hologram.dev.HologramCapture;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.menu.equipment.AccessoryInventoryMenu;
import zcylas.totality.screen.phone.PhoneAppGridScreen;
import zcylas.totality.screen.phone.PhoneFrame;
import zcylas.totality.screen.phone.PhoneNotificationShade;
import zcylas.totality.screen.phone.PhonePrototype;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Basic Copper Phone Phase 1 visual prototype, capture scene 64 (opt-in development capture run; inert in normal
 * play). Turns on {@link PhonePrototype}'s test data, then photographs: the main home page with badges, the page
 * slide and both development pages, a part-way page drag, green/orange/red status-bar indicators, the shade
 * (opening, compact, scrolled, expanded, swipes, pull open/close, Mark as Read, Clear All, empty), long-label fit,
 * every GUI scale the window allows (prototype and normal play), and the Phone equipment-slot icon.
 * Interactions go through the screen's real mouse/keyboard handlers.
 */
final class PhonePrototypeCapture {

    private PhonePrototypeCapture() {}

    private static PhoneAppGridScreen grid() {
        return PhoneCaptureStates.grid();
    }

    private static PhoneNotificationShade shade() {
        PhoneAppGridScreen g = grid();
        return g == null ? null : g.shade();
    }

    private static Supplier<double[]> at(Supplier<double[]> p) {
        return () -> {
            double[] v = p.get();
            return v == null ? new double[] {0, 0} : v;
        };
    }

    private static Step open() {
        return HologramCapture.run("open the phone home screen", () -> Minecraft.getInstance().gui.setScreen(new PhoneAppGridScreen(PhoneFrame.COPPER)));
    }

    private static Step close() {
        return HologramCapture.run("close", () -> Minecraft.getInstance().gui.setScreen(null));
    }

    private static Step guiScale(int scale) {
        return HologramCapture.run("GUI scale " + scale, () -> {
            Minecraft.getInstance().options.guiScale().set(scale);
            Minecraft.getInstance().resizeGui();
        });
    }

    /** The integrated server opens the Equipment menu, as the screen button's payload handler does (no packets sent here). */
    private static void openEquipmentOnServer() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p instanceof TotalityFakePlayer) continue;
                p.openMenu(new SimpleMenuProvider((id, inventory, player) -> new AccessoryInventoryMenu(id, inventory), Component.empty()));
            }
        });
    }

    private static int maxGuiScale() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getWindow().calculateScale(0, mc.isEnforceUnicode());
    }

    /** Runs {@code steps} (one per tick, like the runner) only if {@code condition} holds when reached. */
    private static Step when(BooleanSupplier condition, String label, List<Step> steps) {
        int[] next = {-1};
        return mc -> {
            if (next[0] < 0) {
                if (!condition.getAsBoolean()) {
                    HologramCapture.log("skip: " + label + " (not available in this window)");
                    return true;
                }
                next[0] = 0;
            }
            if (next[0] < steps.size() && steps.get(next[0]).tick(mc)) next[0]++;
            return next[0] >= steps.size();
        };
    }

    /** One screenshot per tick, for transitions. */
    private static void frames(List<Step> s, String name, int count) {
        for (int i = 1; i <= count; i++) s.add(HologramCapture.screenshot(name + "_t" + i));
    }

    static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        int[] originalScale = new int[1];
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("item replace entity @s weapon.mainhand with minecraft:air"));
        s.add(HologramCapture.look(-35, 8));
        s.add(PhoneCapture.mouse("a corner", () -> new double[] {2, 2}));
        s.add(HologramCapture.run("remember GUI scale", () -> originalScale[0] = Minecraft.getInstance().options.guiScale().get()));
        s.add(HologramCapture.run("allow screens", () -> HologramCapture.allowScreens = true));
        s.add(HologramCapture.run("prototype data on", () -> {
            PhonePrototype.enabled = true;
            PhonePrototype.labelStress = false;
            PhonePrototype.use(PhonePrototype.Scenario.CRITICAL);
        }));
        s.add(HologramCapture.waitTicks(20));

        // Main home page (opening slide-in first).
        s.add(open());
        frames(s, "p1_00_open", 4);
        s.add(HologramCapture.waitTicks(15));
        s.add(HologramCapture.check("prototype: dev page, main page, Gallery page, dev page; the phone opens on the main page",
                () -> grid() != null && grid().pageCount() == 4 && grid().page() == 1 && grid().mainPage() == 1));
        s.add(HologramCapture.screenshot("p1_01_home_main"));

        // Pages: click the right dot, then the left one (slides two pages), frames of each slide.
        s.add(PhoneCaptureStates.clickAt("right page dot", () -> grid().pageMarkerCentre(3)));
        frames(s, "p1_02_slide_to_right", 5);
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.check("right development page", () -> grid().page() == 3));
        s.add(HologramCapture.screenshot("p1_03_dev_page_right"));
        s.add(PhoneCaptureStates.clickAt("left page dot", () -> grid().pageMarkerCentre(0)));
        frames(s, "p1_04_slide_to_left", 5);
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.check("left development page", () -> grid().page() == 0));
        s.add(HologramCapture.screenshot("p1_05_dev_page_left"));
        s.add(PhoneCaptureStates.clickAt("main page marker", () -> grid().pageMarkerCentre(1)));
        s.add(HologramCapture.waitTicks(8));
        // A sideways drag part-way to the right page, then released short of the threshold: springs back.
        s.add(PhoneCaptureStates.dragHold("grid part-way left", () -> grid().displayCentre(), -18, 2, 4));
        s.add(HologramCapture.screenshot("p1_06_page_drag_partial"));
        s.add(PhoneCaptureStates.release("short drag"));
        s.add(HologramCapture.waitTicks(8));
        s.add(HologramCapture.check("a short drag springs back to the main page", () -> grid().page() == 1));
        s.add(PhoneCaptureStates.dragHold("grid swipe left", () -> grid().displayCentre(), -45, 0, 6));
        s.add(PhoneCaptureStates.release("swipe"));
        s.add(HologramCapture.waitTicks(8));
        s.add(HologramCapture.check("a long swipe turns to the next page (the Gallery page)", () -> grid().page() == 2));
        s.add(PhoneCapture.key("Left (back to main)", GLFW.GLFW_KEY_LEFT));
        s.add(HologramCapture.waitTicks(8));
        s.add(HologramCapture.check("keyboard Left returns to the main page", () -> grid().page() == 1));
        s.addAll(PhoneInteractionCapture.hitboxes());

        // Status-bar notification indicator: green, orange, red (highest unread urgency, total count).
        s.add(HologramCapture.run("normal notifications only", () -> PhonePrototype.use(PhonePrototype.Scenario.NORMAL)));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.check("green indicator, 6", () -> shade().highest() == PhoneNotificationShade.Severity.NORMAL && shade().count() == 6));
        s.add(HologramCapture.screenshot("p1_07_status_green"));
        s.add(HologramCapture.run("plus an important one", () -> PhonePrototype.use(PhonePrototype.Scenario.IMPORTANT)));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.check("orange indicator, 8", () -> shade().highest() == PhoneNotificationShade.Severity.IMPORTANT && shade().count() == 8));
        s.add(HologramCapture.screenshot("p1_08_status_orange"));
        s.add(HologramCapture.run("full set (critical)", () -> PhonePrototype.use(PhonePrototype.Scenario.CRITICAL)));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.check("red indicator, 9", () -> shade().highest() == PhoneNotificationShade.Severity.CRITICAL && shade().count() == 9));
        s.add(HologramCapture.screenshot("p1_09_status_red"));

        // Shade: open by tapping the status bar, list, scroll, expand.
        s.add(PhoneCaptureStates.clickAt("status bar", () -> grid().statusBarPoint()));
        frames(s, "p1_10_shade_opening", 4);
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.check("the shade is open", () -> shade().openAmount() == 1f));
        s.add(HologramCapture.screenshot("p1_11_shade_compact"));
        s.add(HologramCapture.run("wheel down", () -> grid().mouseScrolled(grid().displayCentre()[0], grid().displayCentre()[1], 0, -20)));
        s.add(HologramCapture.waitTicks(2));
        s.add(HologramCapture.screenshot("p1_12_shade_scrolled_to_end"));
        s.add(HologramCapture.run("wheel up", () -> grid().mouseScrolled(grid().displayCentre()[0], grid().displayCentre()[1], 0, 20)));
        s.add(HologramCapture.waitTicks(2));
        s.add(PhoneCaptureStates.clickAt("Quests card (tap expands)", at(() -> shade().cardCentre("quest"))));
        s.add(HologramCapture.waitTicks(2));
        s.add(HologramCapture.check("tap expanded the Quests notification", () -> shade().ordered().stream()
                .anyMatch(e -> e.id().equals("quest") && shade().isExpanded(e))));
        s.add(PhoneCapture.mouse("Mark as Read (hover)", at(() -> shade().markAsReadCentre("quest"))));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.screenshot("p1_13_shade_expanded"));
        s.addAll(PhoneInteractionCapture.shadeControls());
        s.add(PhoneCapture.mouse("away", () -> new double[] {2, 2}));

        // Swipes: right expands, left dismisses (part-way frames show the revealed hints).
        s.add(PhoneCaptureStates.dragHold("System card right", at(() -> shade().cardCentre("system")), 40, 1, 4));
        s.add(HologramCapture.screenshot("p1_14_swipe_right_partial"));
        s.add(PhoneCaptureStates.release("swipe right"));
        s.add(HologramCapture.waitTicks(2));
        s.add(HologramCapture.check("swipe right expanded the System notification", () -> shade().ordered().stream()
                .anyMatch(e -> e.id().equals("system") && shade().isExpanded(e))));
        s.add(HologramCapture.screenshot("p1_15_swipe_right_expanded"));
        s.add(PhoneCaptureStates.clickAt("System header (collapse)", at(() -> {
            double[] c = shade().cardCentre("system");
            return c == null ? null : new double[] {c[0] - 10, c[1] - 6};
        })));
        s.add(HologramCapture.waitTicks(2));
        s.add(HologramCapture.check("clicking the expanded header collapsed it", () -> shade().ordered().stream()
                .noneMatch(e -> e.id().equals("system") && shade().isExpanded(e))));
        s.add(PhoneCaptureStates.clickAt("Mark as Read on Quests", at(() -> shade().markAsReadCentre("quest"))));
        s.add(HologramCapture.waitTicks(2));
        s.add(HologramCapture.check("Mark as Read dismissed the Quests notification (8 left)", () -> shade().count() == 8
                && shade().ordered().stream().noneMatch(e -> e.id().equals("quest"))));
        s.add(PhoneCaptureStates.dragHold("Codex card left", at(() -> shade().cardCentre("codex")), -40, 1, 4));
        s.add(HologramCapture.screenshot("p1_16_swipe_left_partial"));
        s.add(PhoneCaptureStates.release("swipe left"));
        s.add(HologramCapture.waitTicks(2));
        s.add(HologramCapture.check("swipe left dismissed the Codex notification (7 left)", () -> shade().count() == 7
                && shade().ordered().stream().noneMatch(e -> e.id().equals("codex"))));
        s.add(HologramCapture.screenshot("p1_17_after_dismiss"));

        // Drag up past the end of the list closes; a pull from the status bar opens again.
        s.add(HologramCapture.run("wheel to the end", () -> grid().mouseScrolled(0, 0, 0, -40)));
        s.add(HologramCapture.waitTicks(2));
        s.add(PhoneCaptureStates.dragHold("list upward", at(() -> shade().listCentre()), 0, -70, 7));
        s.add(HologramCapture.screenshot("p1_18_drag_up_closing"));
        s.add(PhoneCaptureStates.release("drag up"));
        s.add(HologramCapture.waitTicks(8));
        s.add(HologramCapture.check("dragging up past the end closed the shade", () -> !shade().isOpen()));
        s.add(PhoneCaptureStates.dragHold("status bar downward", () -> grid().statusBarPoint(), 0, 70, 7));
        s.add(HologramCapture.screenshot("p1_19_pull_down_opening"));
        s.add(PhoneCaptureStates.release("pull"));
        s.add(HologramCapture.waitTicks(8));
        s.add(HologramCapture.check("a pull from the status bar opened the shade", () -> shade().openAmount() == 1f));

        // Clear All, then the empty state; ESC closes the shade but not the phone.
        s.add(PhoneCaptureStates.clickAt("Clear All", at(() -> shade().clearAllCentre())));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.check("Clear All dismissed everything; no status indicator", () -> shade().count() == 0 && shade().highest() == null));
        s.add(HologramCapture.screenshot("p1_20_shade_empty"));
        s.add(PhoneCapture.key("Esc (closes the shade)", GLFW.GLFW_KEY_ESCAPE));
        s.add(HologramCapture.waitTicks(8));
        s.add(HologramCapture.check("Esc closed the shade, the phone stays open", () -> grid() != null && !shade().isOpen()));
        s.add(HologramCapture.screenshot("p1_21_home_no_notifications"));
        s.add(close());

        // Long labels on the right development page.
        s.add(HologramCapture.run("label stress on", () -> PhonePrototype.labelStress = true));
        s.add(open());
        s.add(HologramCapture.waitTicks(15));
        s.add(PhoneCaptureStates.clickAt("right page dot", () -> grid().pageMarkerCentre(3)));
        s.add(HologramCapture.waitTicks(8));
        s.add(HologramCapture.screenshot("p1_22_label_stress"));
        s.add(close());
        s.add(HologramCapture.run("label stress off", () -> PhonePrototype.labelStress = false));

        // Every GUI scale this window allows: prototype home, expanded shade, and normal play.
        for (int scale = 4; scale >= 1; scale--) {
            int sc = scale;
            List<Step> per = new ArrayList<>();
            per.add(guiScale(sc));
            per.add(HologramCapture.waitTicks(4));
            per.add(HologramCapture.run("prototype data (full)", () -> {
                PhonePrototype.enabled = true;
                PhonePrototype.use(PhonePrototype.Scenario.CRITICAL);
            }));
            per.add(open());
            per.add(HologramCapture.waitTicks(15));
            per.add(HologramCapture.screenshot("p1_30_home_gui" + sc));
            per.addAll(PhoneInteractionCapture.perScale(sc));
            per.add(HologramCapture.run("shade open, Quests expanded", () -> {
                shade().snap(true);
                shade().ordered().stream().filter(e -> e.id().equals("quest")).forEach(e -> shade().setExpanded(e, true));
            }));
            per.add(HologramCapture.waitTicks(3));
            per.add(HologramCapture.screenshot("p1_31_shade_gui" + sc));
            per.add(close());
            per.add(HologramCapture.run("prototype data off (normal play)", () -> PhonePrototype.enabled = false));
            per.add(open());
            per.add(HologramCapture.waitTicks(15));
            per.add(HologramCapture.screenshot("p1_32_home_normal_gui" + sc));
            per.add(close());
            s.add(when(() -> sc <= maxGuiScale(), "GUI scale " + sc, per));
        }

        // The development command, through the real registered client command tree (at GUI 4, or the window's
        // largest scale if 4 is not available).
        s.add(guiScale(4));
        s.add(HologramCapture.waitTicks(4));
        s.addAll(PhoneInteractionCapture.devCommand());

        // The Phone equipment-slot icon in its real slot (phone slot empty), at each GUI scale.
        s.add(guiScale(4));
        s.add(HologramCapture.until("Equipment screen open", () ->
                        Minecraft.getInstance().player.containerMenu instanceof AccessoryInventoryMenu,
                () -> {
                    if (!(Minecraft.getInstance().player.containerMenu instanceof AccessoryInventoryMenu)) openEquipmentOnServer();
                }, 60));
        for (int scale = 4; scale >= 2; scale--) {
            int sc = scale;
            s.add(guiScale(sc));
            s.add(HologramCapture.waitTicks(6));
            s.add(HologramCapture.screenshot("p1_40_equipment_slot_gui" + sc));
        }
        s.add(HologramCapture.run("close equipment", () -> Minecraft.getInstance().player.closeContainer()));
        s.add(HologramCapture.run("restore GUI scale", () -> {
            Minecraft.getInstance().options.guiScale().set(originalScale[0]);
            Minecraft.getInstance().resizeGui();
        }));
        s.add(HologramCapture.run("prototype data off", () -> {
            PhonePrototype.enabled = false;
            PhonePrototype.labelStress = false;
        }));
        s.add(HologramCapture.run("screens back to normal", () -> HologramCapture.allowScreens = false));
        s.add(HologramCapture.waitTicks(10));
        return s;
    }
}
