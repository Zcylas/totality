package zcylas.totality.networking.classes;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.classes.*;
import zcylas.totality.api.rpg.stats.StatsComponents;
import zcylas.totality.api.rpg.classes.ClassLevelUpRegistry;
import zcylas.totality.networking.notification.SendNotificationPayload;

public final class AddClassLevelHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                AddClassLevelPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        handle(context.player(), payload)));
    }

    private static void handle(ServerPlayer player, AddClassLevelPayload payload) {
        Identifier classId = Identifier.parse(payload.classId());

        // Validate class exists
        if (ClassRegistry.get(classId).isEmpty()) {
            Totality.LOGGER.warn("AddClassLevelHandler: unknown class {}", classId);
            return;
        }

        PlayerClassComponent comp = ClassComponents.get(player);
        int playerLevel = StatsComponents.getStats(player).getLevel();

        // Check available points
        int available = comp.getAvailableClassPoints(playerLevel);
        int spent     = comp.getSpentClassPoints();
        if (spent >= available) {
            SendNotificationPayload.send(player,
                    "No class points available to spend.", 0xFFFF4444);
            return;
        }

        // Correction pass (2026-09-16): captured before the mutation so ClassChangeReconciler can
        // grant newly unlocked Generic Resource capacity (e.g. a new Standard Spell Slot tier) while
        // preserving already-spent capacity on a resource that stays continuously granted across the
        // level-up — see ClassChangeReconciler's class Javadoc for the full root-cause trace.
        var resolvedMaximumsBeforeLevelUp = ClassChangeReconciler.captureResolvedMaximums(player);

        // Add one level to the target class
        comp.addClassLevel(classId);
        comp.sync();

        // Fire class level-up registry so features (charge pools etc.) are updated
        int classLevel = comp.getClassLevel(classId);
        ClassLevelUpRegistry.fire(player, classId, playerLevel);
        ClassChangeReconciler.reconcile(player, resolvedMaximumsBeforeLevelUp);

        String className = ClassRegistry.get(classId)
                .map(cd -> cd.displayName()).orElse(classId.getPath());

        Totality.LOGGER.info("AddClassLevel: {} leveled {} to class level {}",
                player.getName().getString(), classId, classLevel);

        SendNotificationPayload.send(player,
                className + " leveled up to class level " + classLevel + "!",
                0xFFFFD700);
    }

    private AddClassLevelHandler() {}
}