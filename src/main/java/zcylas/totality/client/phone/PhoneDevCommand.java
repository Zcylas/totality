package zcylas.totality.client.phone;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.equipment.PlayerEquipmentComponent;
import zcylas.totality.client.camera.CameraSession;
import zcylas.totality.client.equipment.ClientEquipmentManager;
import zcylas.totality.client.photo.PhotoCapture;
import zcylas.totality.screen.phone.GalleryScreen;
import zcylas.totality.screen.phone.PhoneAppGridScreen;
import zcylas.totality.screen.phone.PhoneFrame;
import zcylas.totality.screen.phone.PhoneNotificationShade;
import zcylas.totality.screen.phone.PhoneOrigin;
import zcylas.totality.screen.phone.PhonePrototype;

import java.util.function.Consumer;

/**
 * Development-only Phone testing command: {@code /totalityphone <option>}. Same conventions as
 * {@code /totalityhologram} ({@code HologramShowcase}): a client-side command (never sent to the server), registered
 * only in a Fabric development environment. It switches {@link PhonePrototype}'s synthetic test data and opens the
 * REAL Phone home screen ({@link PhoneAppGridScreen}) with its real interaction code. It grants nothing, touches no
 * player data, currency, entitlement, progression or genuine notification, and {@code off} restores the ordinary
 * Phone presentation.
 */
public final class PhoneDevCommand {

    static final String ROOT = "totalityphone";

    /** Options, in help order: name and description. */
    static final String[][] OPTIONS = {
            {"help", "Show these options."},
            {"home", "Open the home screen (current test state)."},
            {"pages", "Test data on; open the main page with a development page on each side."},
            {"normal", "Test notifications at normal urgency only (green), open home."},
            {"important", "Add important notifications (orange), open home."},
            {"critical", "Add a critical notification (red), open home."},
            {"shade", "Open the notification shade with the full test set."},
            {"expanded", "Open the shade with an expanded notification."},
            {"empty", "Open the shade with no notifications."},
            {"labels", "Open the long-label stress page."},
            {"bounds", "Toggle the interactive-bounds overlay, open home."},
            {"reset", "Restore the initial test state (test data on, full set), open home."},
            {"off", "Turn test data off: the ordinary Phone presentation."},
            {"camera", "Open the Camera viewfinder (returns to the main page)."},
            {"gallery", "Open the Gallery app (returns to the main page)."},
            {"camera-fullscreen", "Toggle development full-screen captures (HUD and overlays included)."},
    };

    /** The screen opens on the next client tick: the chat screen closes itself after the command runs. */
    private static Runnable pending;

    private PhoneDevCommand() {}

    public static void registerIfDevelopmentEnvironment() {
        if (!VerificationReporter.isDevEnvironment()) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Runnable r = pending;
            pending = null;
            if (r != null && client.player != null) r.run();
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal(ROOT).executes(PhoneDevCommand::help);
            root.then(ClientCommands.literal("help").executes(PhoneDevCommand::help));
            option(root, "home", "Home screen.", () -> { }, s -> { });
            option(root, "pages", "Development pages on either side of the main page.", () -> PhonePrototype.enabled = true, s -> { });
            option(root, "normal", "Normal-urgency test notifications.", () -> scenario(PhonePrototype.Scenario.NORMAL), s -> { });
            option(root, "important", "Important-urgency test notifications added.", () -> scenario(PhonePrototype.Scenario.IMPORTANT), s -> { });
            option(root, "critical", "Critical test notification added.", () -> scenario(PhonePrototype.Scenario.CRITICAL), s -> { });
            option(root, "shade", "Notification shade (full test set).", () -> scenario(PhonePrototype.Scenario.CRITICAL),
                    s -> s.shade().snap(true));
            option(root, "expanded", "Notification shade with an expanded notification.", () -> scenario(PhonePrototype.Scenario.CRITICAL),
                    s -> {
                        s.shade().snap(true);
                        expand(s.shade(), "quest");
                    });
            option(root, "empty", "Empty notification shade.", () -> scenario(PhonePrototype.Scenario.NONE), s -> s.shade().snap(true));
            option(root, "labels", "Long-label stress page (right development page).", () -> {
                PhonePrototype.enabled = true;
                PhonePrototype.labelStress = true;
            }, s -> s.showPage(s.pageCount() - 1));
            option(root, "bounds", "Interactive-bounds overlay toggled.", () -> PhonePrototype.showBounds = !PhonePrototype.showBounds, s -> { });
            option(root, "reset", "Test state reset.", PhonePrototype::reset, s -> { });
            option(root, "off", "Test data off: ordinary Phone presentation.", PhonePrototype::off, s -> { });
            // Camera and Gallery: the real apps, opened as from the main page (they hold only local photographs).
            app(root, "camera", "Camera.", () -> CameraSession.open(frame(), 0));
            app(root, "gallery", "Gallery.", () -> Minecraft.getInstance().gui.setScreen(new GalleryScreen(PhoneOrigin.home(frame(), 0))));
            root.then(ClientCommands.literal("camera-fullscreen").executes(ctx -> {
                PhotoCapture.setFullScreen(!PhotoCapture.fullScreen());
                ctx.getSource().sendFeedback(Component.literal("[Phone test] Camera captures: "
                        + (PhotoCapture.fullScreen() ? "FULL SCREEN (HUD and overlays, development only)." : "clean photographs.")));
                return 1;
            }));
            dispatcher.register(root);
        });
    }

    private static void option(LiteralArgumentBuilder<FabricClientCommandSource> root, String name, String done,
                               Runnable change, Consumer<PhoneAppGridScreen> after) {
        root.then(ClientCommands.literal(name).executes(ctx -> {
            change.run();
            pending = () -> {
                PhoneAppGridScreen screen = new PhoneAppGridScreen(frame());
                Minecraft.getInstance().gui.setScreen(screen);
                after.accept(screen);
            };
            ctx.getSource().sendFeedback(Component.literal("[Phone test] " + done + " Synthetic data only; nothing was granted."));
            return 1;
        }));
    }

    private static void app(LiteralArgumentBuilder<FabricClientCommandSource> root, String name, String done, Runnable open) {
        root.then(ClientCommands.literal(name).executes(ctx -> {
            pending = open;
            ctx.getSource().sendFeedback(Component.literal("[Phone test] " + done));
            return 1;
        }));
    }

    private static int help(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Component.literal("/" + ROOT + " <option>  (development only, synthetic data)"));
        for (String[] o : OPTIONS) ctx.getSource().sendFeedback(Component.literal("  " + o[0] + " - " + o[1]));
        return 1;
    }

    /** Test data on with the given notification set (label stress off, so the normal pages show). */
    static void scenario(PhonePrototype.Scenario set) {
        PhonePrototype.enabled = true;
        PhonePrototype.labelStress = false;
        PhonePrototype.use(set);
    }

    static void expand(PhoneNotificationShade shade, String id) {
        shade.ordered().stream().filter(e -> e.id().equals(id)).forEach(e -> shade.setExpanded(e, true));
    }

    /** The equipped (or held) phone's frame; the Basic Copper Phone when none is at hand. */
    private static PhoneFrame frame() {
        ItemStack equipped = ClientEquipmentManager.getStack(PlayerEquipmentComponent.IDX_PHONE);
        if (!equipped.isEmpty()) return PhoneFrame.forStack(equipped);
        return PhoneFrame.forStack(Minecraft.getInstance().player.getMainHandItem());
    }
}
