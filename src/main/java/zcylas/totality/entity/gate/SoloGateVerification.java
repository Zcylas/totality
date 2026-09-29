package zcylas.totality.entity.gate;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.ServerScheduler;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.init.ModEntities;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.List;
import java.util.function.Supplier;

/**
 * Dev-environment-gated, opt-in (live-world) self-test of the visual-only Solo Leveling gate (Creative Test K) on the
 * server, on a stage of its own (chunk 52, 52): a blue and a red gate side by side keep independent palettes; the
 * lifecycle runs opening → stable → closing → removed on time; the entry pulse is visual only (a player standing in the
 * gate is not moved or hurt); the gate cannot be hit, pushed or picked; nothing is left behind.
 */
public final class SoloGateVerification {

    private static final int CHUNK = 52;
    private static final double X0 = CHUNK * 16 + 8, Y0 = 120, Z0 = CHUNK * 16 + 8;

    private SoloGateVerification() {}

    public static void register() {
        if (!VerificationReporter.liveWorldVerificationEnabled()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!VerificationReporter.isDevEnvironment()) return;
            // force the stage now and start once it is entity-ticking (as the other stage suites do)
            server.overworld().setChunkForced(CHUNK, CHUNK, true);
            ServerScheduler.getInstance().queue(SoloGateVerification::start, 300);
        });
    }

    private static void start(MinecraftServer server) {
        ServerLevel level = server.overworld();
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "SoloGateVerification");
        check(r, "totality:solo_gate is registered", () ->
                BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "solo_gate")) == ModEntities.SOLO_GATE);
        SoloGateEntity blue = SoloGateCommands.spawn(level, new Vec3(X0 - 2.8, Y0 + SoloGateEntity.CENTRE_HEIGHT, Z0), 180, GatePalette.BLUE);
        SoloGateEntity red = SoloGateCommands.spawn(level, new Vec3(X0 + 2.8, Y0 + SoloGateEntity.CENTRE_HEIGHT, Z0), 180, GatePalette.RED);
        check(r, "a blue and a red gate side by side, each with its own palette, both opening", () ->
                blue.palette() == GatePalette.BLUE && red.palette() == GatePalette.RED && blue.isAlive() && red.isAlive()
                        && blue.phase() == SoloGateEntity.Phase.OPENING && red.phase() == SoloGateEntity.Phase.OPENING);
        check(r, "the palette is per instance: a third gate turned red leaves the blue one blue", () -> {
            SoloGateEntity third = SoloGateCommands.spawn(level, new Vec3(X0, Y0 + 10, Z0 + 6), 0, GatePalette.BLUE);
            third.setPalette(GatePalette.RED);
            boolean ok = third.palette() == GatePalette.RED && blue.palette() == GatePalette.BLUE;
            third.discard();
            return ok;
        });
        check(r, "visual only: not pickable, not pushable, cannot be hurt", () ->
                !blue.isPickable() && !blue.isPushable() && !blue.hurtServer(level, level.damageSources().generic(), 100));
        ServerPlayer walker = TotalityFakePlayer.create(level, "GateWalker");
        walker.setGameMode(GameType.SURVIVAL);
        walker.snapTo(X0 - 2.8, Y0 + 0.5, Z0, 0, 0);
        ServerScheduler.getInstance().queue(s -> stable(s, r, blue, red, walker), SoloGateEntity.OPEN_TICKS + 10);
    }

    private static void stable(MinecraftServer server, VerificationReporter r, SoloGateEntity blue, SoloGateEntity red, ServerPlayer walker) {
        ServerLevel level = server.overworld();
        r.check("after " + SoloGateEntity.OPEN_TICKS + " ticks both gates are stable",
                blue.phase() == SoloGateEntity.Phase.STABLE && red.phase() == SoloGateEntity.Phase.STABLE,
                "blue " + blue.phase() + "/" + blue.phaseAge() + ", red " + red.phase() + "/" + red.phaseAge());
        Vec3 before = walker.position(), gateBefore = blue.position();
        float health = walker.getHealth();
        blue.pulse();
        check(r, "the entry pulse is visual: the player standing in the gate is not moved, hurt or sent anywhere", () ->
                walker.position().equals(before) && walker.getHealth() == health && walker.level() == level
                        && blue.position().equals(gateBefore) && blue.pulseAge() == 0 && blue.phase() == SoloGateEntity.Phase.STABLE);
        blue.close();
        red.close();
        check(r, "closing starts on both", () -> blue.phase() == SoloGateEntity.Phase.CLOSING && red.phase() == SoloGateEntity.Phase.CLOSING);
        ServerScheduler.getInstance().queue(s -> closed(s, r, blue, red, walker), SoloGateEntity.CLOSE_TICKS + 5);
    }

    private static void closed(MinecraftServer server, VerificationReporter r, SoloGateEntity blue, SoloGateEntity red, ServerPlayer walker) {
        ServerLevel level = server.overworld();
        try {
            check(r, "after " + SoloGateEntity.CLOSE_TICKS + " ticks both gates removed themselves; no gate is left on the stage", () -> {
                List<SoloGateEntity> left = level.getEntitiesOfClass(SoloGateEntity.class, AABB.ofSize(new Vec3(X0, Y0, Z0), 40, 40, 40));
                return blue.isRemoved() && red.isRemoved() && left.isEmpty();
            });
        } finally {
            walker.discard();
            level.setChunkForced(CHUNK, CHUNK, false);
            r.summarize();
        }
    }

    private static void check(VerificationReporter r, String label, Supplier<Boolean> body) {
        try {
            r.check(label, body.get(), "");
        } catch (RuntimeException e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
