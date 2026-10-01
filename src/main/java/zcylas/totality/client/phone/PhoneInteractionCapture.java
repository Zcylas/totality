package zcylas.totality.client.phone;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.api.entitlement.client.ClientEntitlementView;
import zcylas.totality.api.entitlement.integration.PhoneAppEntitlements;
import zcylas.totality.client.hologram.dev.HologramCapture;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.networking.currency.ClientWalletManager;
import zcylas.totality.screen.inventory.TotalityInventoryScreen;
import zcylas.totality.screen.phone.PhoneAppGridScreen;
import zcylas.totality.screen.phone.PhoneFrame;
import zcylas.totality.screen.phone.PhoneHomeGeometry;
import zcylas.totality.screen.phone.PhoneNotificationShade;
import zcylas.totality.screen.phone.PhonePrototype;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Phase 1 revision checks for capture scene 64: REAL interactions (press/release/drag through the screen's own input
 * handlers, then the resulting screen and state are checked — a screenshot alone proves nothing), hover versus
 * keyboard focus, the interactive-bounds overlay, per-GUI-scale hit and label checks, and the {@code /totalityphone}
 * development command executed through the real registered client command tree.
 */
final class PhoneInteractionCapture {

    private PhoneInteractionCapture() {}

    private static PhoneAppGridScreen grid() {
        return PhoneCaptureStates.grid();
    }

    private static PhoneNotificationShade shade() {
        return grid() == null ? null : grid().shade();
    }

    /** App {@code i}'s frame rectangle {x, y, w, h} on the current page. */
    private static int[] frame(int i) {
        return grid().appBounds(i)[0];
    }

    private static double midY(int i) {
        int[] f = frame(i);
        return f[1] + f[3] / 2.0;
    }

    /** Points that are visibly empty: between Codex and Map, the Codex/Map/Spells/Abilities corner, around Inventory. */
    private static double[] gapCodexMap() {
        int[] a = frame(0), b = frame(1);
        return new double[] {(a[0] + a[2] + b[0]) / 2.0, midY(0)};
    }

    private static double[] cornerCodexMapSpellsAbilities() {
        PhoneHomeGeometry g = grid().homeGeometry();
        return new double[] {g.cellX(0) + g.cellW(), g.cellY(0) + g.cellH() + 0.5};
    }

    private static double[] rightOfInventory() {
        int[] f = frame(2);
        return new double[] {f[0] + f[2] + 2.5, midY(2)};
    }

    private static double[] leftOfInventory() {
        int[] f = frame(2);
        return new double[] {f[0] - 3.5, midY(2)};
    }

    private static double[] belowInventoryLabel() {
        int[][] b = grid().appBounds(2);
        int[] label = b[b.length - 1];
        return new double[] {label[0] + label[2] / 2.0, label[1] + label[3] + 2.5};
    }

    private static double[] inventoryLabel() {
        int[][] b = grid().appBounds(2);
        int[] label = b[b.length - 1];
        return new double[] {label[0] + label[2] / 2.0, label[1] + label[3] / 2.0};
    }

    private static double[] betweenDock(int i) {
        PhoneHomeGeometry g = grid().homeGeometry();
        return new double[] {(g.dockIconX(i) + g.dockIcon() + g.dockIconX(i + 1)) / 2.0, g.dockIconY() + g.dockIcon() / 2.0};
    }

    private static boolean nothingPressed() {
        for (int i = 0; i < 12; i++) if (grid().isPressed(i)) return false;
        return true;
    }

    /** Clicks an empty point for real and checks that nothing activated (same screen, no press feedback). */
    private static void emptyClick(List<Step> s, String name, Supplier<double[]> at) {
        Screen[] before = new Screen[1];
        s.add(HologramCapture.check(name + ": no app's bounds contain it", () -> {
            double[] p = at.get();
            return grid().appAt(p[0], p[1]) == -1 && grid().dockIndexAt(p[0], p[1]) == -1;
        }));
        s.add(HologramCapture.run("remember screen", () -> before[0] = Minecraft.getInstance().gui.screen()));
        s.add(PhoneCaptureStates.clickAt(name, at));
        s.add(HologramCapture.check(name + ": a real click activates nothing (same screen, no press feedback)",
                () -> Minecraft.getInstance().gui.screen() == before[0] && nothingPressed()));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.check(name + ": still the same home screen afterwards", () -> Minecraft.getInstance().gui.screen() == before[0]));
    }

    private static Step reopen() {
        return HologramCapture.run("reopen the phone", () -> Minecraft.getInstance().gui.setScreen(new PhoneAppGridScreen(PhoneFrame.COPPER)));
    }

    /** Hit areas on the main page, with real clicks and drags. Prototype on, shade closed. */
    static List<Step> hitboxes() {
        List<Step> s = new ArrayList<>();
        s.add(PhoneCapture.mouse("gap between Codex and Map", PhoneInteractionCapture::gapCodexMap));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.screenshot("p1_50_hover_gap_codex_map"));
        emptyClick(s, "gap between Codex and Map", PhoneInteractionCapture::gapCodexMap);
        s.add(PhoneCapture.mouse("Codex/Map/Spells/Abilities corner", PhoneInteractionCapture::cornerCodexMapSpellsAbilities));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.screenshot("p1_50b_hover_corner_codex_map_spells_abilities"));
        emptyClick(s, "Codex/Map/Spells/Abilities corner", PhoneInteractionCapture::cornerCodexMapSpellsAbilities);
        s.add(PhoneCapture.mouse("right of Inventory", PhoneInteractionCapture::rightOfInventory));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.screenshot("p1_50c_hover_right_of_inventory"));
        emptyClick(s, "right of Inventory (inside its old cell)", PhoneInteractionCapture::rightOfInventory);
        emptyClick(s, "left of Inventory (inside its old cell)", PhoneInteractionCapture::leftOfInventory);
        emptyClick(s, "below Inventory's label", PhoneInteractionCapture::belowInventoryLabel);
        emptyClick(s, "between dock icons 1 and 2", () -> betweenDock(0));
        emptyClick(s, "between dock icons 3 and 4", () -> betweenDock(2));

        // Dragging from an app never opens it: vertical (no gesture) and a short sideways drag (page springs back).
        s.add(PhoneCaptureStates.dragHold("from the Inventory icon, 14 px down", () -> grid().tileCentre(2), 0, 14, 4));
        s.add(PhoneCaptureStates.release("drag"));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.check("a drag that starts on Inventory does not open it", () -> grid() != null && nothingPressed()));
        s.add(PhoneCaptureStates.dragHold("from the Codex icon, 10 px left", () -> grid().tileCentre(0), -10, 0, 4));
        s.add(PhoneCaptureStates.release("drag"));
        s.add(HologramCapture.waitTicks(8));
        s.add(HologramCapture.check("a short sideways drag from Codex neither presses it nor leaves the main page",
                () -> grid() != null && nothingPressed() && grid().page() == grid().mainPage()));

        // A valid click on Inventory's LABEL opens Inventory (press and release at the same point).
        s.add(HologramCapture.check("Inventory's label is inside its bounds", () -> {
            double[] p = inventoryLabel();
            return grid().appAt(p[0], p[1]) == 2;
        }));
        s.add(PhoneCaptureStates.clickAt("Inventory label", PhoneInteractionCapture::inventoryLabel));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.check("a valid click opened Inventory", () -> Minecraft.getInstance().gui.screen() instanceof TotalityInventoryScreen));
        s.add(HologramCapture.screenshot("p1_51_inventory_opened_by_valid_click"));
        s.add(reopen());
        s.add(HologramCapture.waitTicks(12));

        // Hover versus keyboard focus (different frames), and the locked frame clearing its padlock.
        s.add(PhoneCapture.mouse("Inventory icon (hover)", () -> grid().tileCentre(2)));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.check("mouse hover is not keyboard focus", () -> grid().keyboardFocus() == -1 && grid().appAt(grid().tileCentre(2)[0], grid().tileCentre(2)[1]) == 2));
        s.add(HologramCapture.screenshot("p1_52_hover_inventory"));
        s.add(PhoneCapture.key("Right (keyboard focus)", GLFW.GLFW_KEY_RIGHT));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.check("the keyboard takes focus", () -> grid().keyboardFocus() >= 0));
        s.add(HologramCapture.screenshot("p1_53_keyboard_focus"));
        s.add(PhoneCapture.key("Right (focus Inventory, unlocked)", GLFW.GLFW_KEY_RIGHT));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.check("keyboard focus moved to Inventory", () -> grid().keyboardFocus() == 2));
        s.add(HologramCapture.screenshot("p1_53b_keyboard_focus_unlocked"));
        s.add(HologramCapture.check("locked (Map) and unlocked (Inventory) apps have identically sized frames", () -> {
            int[] locked = frame(1), open = frame(2);
            return locked[2] == open[2] && locked[3] == open[3] && locked[1] == open[1];
        }));
        s.add(PhoneCapture.mouse("Map icon (locked hover)", () -> grid().tileCentre(1)));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.check("moving the mouse ends keyboard mode", () -> grid().keyboardFocus() == -1));
        s.add(HologramCapture.screenshot("p1_54_locked_hover_map"));
        s.add(PhoneCapture.mouse("Technology icon (now locked)", () -> grid().tileCentre(6)));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.screenshot("p1_54b_locked_hover_technology"));
        s.add(PhoneCapture.mouse("dock: Skills", () -> grid().dockCentre(1)));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.screenshot("p1_54c_dock_hover"));
        s.add(PhoneCaptureStates.clickAt("Technology (unavailable)", () -> grid().tileCentre(6)));
        s.add(HologramCapture.check("Technology is unavailable: nothing opens, no press feedback",
                () -> grid() != null && !grid().isPressed(6)));

        // The development bounds overlay: exactly what hit-testing uses.
        s.add(PhoneCapture.mouse("away", () -> new double[] {2, 2}));
        s.add(HologramCapture.run("bounds overlay on", () -> PhonePrototype.showBounds = true));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.screenshot("p1_55_bounds_home"));
        s.add(HologramCapture.run("shade open", () -> shade().snap(true)));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.screenshot("p1_56_bounds_shade"));
        s.add(HologramCapture.run("shade closed, overlay off", () -> {
            shade().snap(false);
            PhonePrototype.showBounds = false;
        }));
        s.add(HologramCapture.waitTicks(3));
        return s;
    }

    /** With the Quests notification expanded: its body text is not a control. */
    static List<Step> shadeControls() {
        List<Step> s = new ArrayList<>();
        s.add(PhoneCaptureStates.clickAt("expanded Quests body text", () -> {
            double[] c = shade().cardCentre("quest");
            return new double[] {c[0] - 15, c[1] + 4};
        }));
        s.add(HologramCapture.waitTicks(2));
        s.add(HologramCapture.check("clicking an expanded card's body text changes nothing", () -> shade().count() == 9
                && shade().ordered().stream().anyMatch(e -> e.id().equals("quest") && shade().isExpanded(e))));
        return s;
    }

    /** At one GUI scale: every icon hits its own app, gaps hit nothing, the dock is symmetric, no label is cut short. */
    static List<Step> perScale(int scale) {
        List<Step> s = new ArrayList<>();
        s.add(HologramCapture.check("GUI " + scale + ": each icon centre hits its own app", () -> {
            for (int i = 0; i < 12; i++) {
                double[] c = grid().tileCentre(i);
                if (grid().appAt(c[0], c[1]) != i) return false;
            }
            return true;
        }));
        s.add(HologramCapture.check("GUI " + scale + ": empty points between apps hit nothing", () -> {
            double[][] points = {gapCodexMap(), cornerCodexMapSpellsAbilities(), rightOfInventory(), leftOfInventory(),
                    belowInventoryLabel(), betweenDock(0), betweenDock(1), betweenDock(2)};
            for (double[] p : points) if (grid().appAt(p[0], p[1]) != -1 || grid().dockIndexAt(p[0], p[1]) != -1) return false;
            return true;
        }));
        s.add(HologramCapture.check("GUI " + scale + ": dock padding equal on both sides and between icons", () -> {
            PhoneHomeGeometry g = grid().homeGeometry();
            int left = g.dockIconX(0) - g.dockX();
            int right = g.dockX() + g.dockW() - (g.dockIconX(3) + g.dockIcon());
            boolean even = true;
            for (int i = 1; i < 4; i++) even &= g.dockIconX(i) - (g.dockIconX(i - 1) + g.dockIcon()) == left;
            HologramCapture.log("GUI " + scale + " dock: x=" + g.dockX() + " w=" + g.dockW() + " icon=" + g.dockIcon()
                    + " gap=" + g.dockGap() + " outer L/R=" + left + "/" + right + " display x=" + g.device().dx() + " w=" + g.device().dw());
            return left == right && even;
        }));
        s.add(HologramCapture.check("GUI " + scale + ": no app label is cut short", () -> {
            StringBuilder shown = new StringBuilder();
            boolean ok = true;
            for (int i = 0; i < 12; i++) {
                List<String> lines = grid().shownLabelLines(i);
                shown.append(i == 0 ? "" : ", ").append(String.join(" / ", lines));
                for (String line : lines) ok &= !line.endsWith("...");
            }
            HologramCapture.log("GUI " + scale + " labels: " + shown);
            return ok;
        }));
        return s;
    }

    // ── /totalityphone ────────────────────────────────────────────────────────

    private static final int[] COMMAND_RESULT = {-1};

    /** Executes {@code /totalityphone <args>} through the active client command dispatcher (local only, no chat). */
    private static Step command(String args) {
        return HologramCapture.run("/" + PhoneDevCommand.ROOT + " " + args, () -> {
            Minecraft mc = Minecraft.getInstance();
            FabricClientCommandSource source = (FabricClientCommandSource) mc.getConnection().getSuggestionsProvider();
            try {
                COMMAND_RESULT[0] = ClientCommands.getActiveDispatcher().execute(PhoneDevCommand.ROOT + " " + args, source);
            } catch (CommandSyntaxException e) {
                COMMAND_RESULT[0] = -1;
                // Expected for invalid options; the following check decides PASS/FAIL.
                HologramCapture.log("command rejected: /" + PhoneDevCommand.ROOT + " " + args + ": " + e.getMessage());
            }
        });
    }

    /** Runs a command, waits for its screen (opened on the next client tick) and checks the result. */
    private static void option(List<Step> s, String args, String shot, String check, Supplier<Boolean> condition) {
        s.add(HologramCapture.run("close", () -> Minecraft.getInstance().gui.setScreen(null)));
        s.add(command(args));
        s.add(HologramCapture.waitTicks(15));
        s.add(HologramCapture.check("/totalityphone " + args + ": " + check,
                () -> COMMAND_RESULT[0] == 1 && grid() != null && Boolean.TRUE.equals(condition.get())));
        if (shot != null) s.add(HologramCapture.screenshot(shot));
    }

    static List<Step> devCommand() {
        List<Step> s = new ArrayList<>();
        Object[] before = new Object[2];
        s.add(HologramCapture.run("remember wallet and entitlement", () -> {
            before[0] = ClientWalletManager.getValue();
            before[1] = ClientEntitlementView.isSelectable(PhoneAppEntitlements.BANK_APP);
        }));
        s.add(HologramCapture.run("close", () -> Minecraft.getInstance().gui.setScreen(null)));
        s.add(command("help"));
        s.add(HologramCapture.check("/totalityphone help runs", () -> COMMAND_RESULT[0] == 1));
        s.add(command("nonsense"));
        s.add(HologramCapture.check("an unknown option is rejected (no screen opened)", () -> COMMAND_RESULT[0] == -1));
        option(s, "off", "p1_60_cmd_off", "ordinary presentation (main + Gallery page, no notifications, no badges)",
                () -> !PhonePrototype.enabled && grid().pageCount() == 2 && shade().count() == 0 && PhonePrototype.badge("Mail") == 0);
        option(s, "pages", "p1_61_cmd_pages", "development pages around the main page",
                () -> grid().pageCount() == 4 && grid().page() == grid().mainPage());
        option(s, "normal", "p1_62_cmd_normal", "green, normal only",
                () -> shade().highest() == PhoneNotificationShade.Severity.NORMAL && shade().count() == 6);
        option(s, "important", "p1_63_cmd_important", "orange",
                () -> shade().highest() == PhoneNotificationShade.Severity.IMPORTANT && shade().count() == 8);
        option(s, "critical", "p1_64_cmd_critical", "red",
                () -> shade().highest() == PhoneNotificationShade.Severity.CRITICAL && shade().count() == 9);
        option(s, "shade", "p1_65_cmd_shade", "shade open, full set", () -> shade().openAmount() == 1f && shade().count() == 9);
        option(s, "expanded", "p1_66_cmd_expanded", "an expanded notification",
                () -> shade().ordered().stream().anyMatch(shade()::isExpanded));
        option(s, "empty", "p1_67_cmd_empty", "empty shade", () -> shade().openAmount() == 1f && shade().count() == 0);
        option(s, "labels", "p1_68_cmd_labels", "label stress page", () -> PhonePrototype.labelStress && grid().page() == grid().pageCount() - 1);
        option(s, "bounds", "p1_69_cmd_bounds", "bounds overlay on", () -> PhonePrototype.showBounds);
        option(s, "bounds", null, "bounds overlay off again", () -> !PhonePrototype.showBounds);
        option(s, "reset", "p1_70_cmd_reset", "initial test state",
                () -> PhonePrototype.enabled && !PhonePrototype.labelStress && shade().count() == 9 && grid().page() == grid().mainPage());
        option(s, "home", null, "home screen", () -> grid().page() == grid().mainPage());
        option(s, "off", "p1_71_cmd_off_again", "ordinary presentation restored",
                () -> !PhonePrototype.enabled && grid().pageCount() == 2 && shade().count() == 0 && shade().highest() == null);
        s.add(HologramCapture.check("the command changed no wallet balance and no Bank entitlement",
                () -> Objects.equals(before[0], ClientWalletManager.getValue())
                        && Objects.equals(before[1], ClientEntitlementView.isSelectable(PhoneAppEntitlements.BANK_APP))));
        s.add(HologramCapture.run("close", () -> Minecraft.getInstance().gui.setScreen(null)));
        return s;
    }
}
