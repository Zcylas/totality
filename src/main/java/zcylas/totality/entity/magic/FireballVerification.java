package zcylas.totality.entity.magic;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.Totality;
import zcylas.totality.api.combat.condition.ConditionServerTick;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.magic.spell.destruction.FireballSpell;
import zcylas.totality.init.events.VanillaDamageInterceptor;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Dev-environment-gated, opt-in (live-world) self-test of the Fireball VFX pass (Creative Test G) on the server, on a
 * stone stage of its own (y 100 in chunk 48, 48): a real cast through {@link FireballSpell#onActivate} flies to a wall
 * and detonates at the true impact point (not up to a tick's move short of it), damages the pig beside the wall, still
 * ignites the area, and leaves no projectile behind; a fireball flying into the open sky expires after its 100-tick
 * lifetime without exploding; a hit on a mob detonates where the path meets it. The blast-centre rule itself is also
 * checked directly ({@link FireballProjectileEntity#impactPoint}).
 */
public final class FireballVerification {

    private static final int CHUNK = 48;
    private static final int X0 = CHUNK * 16;
    private static final int FLOOR_Y = 100;
    private static final int WALL_Z = X0 + 14;

    private static VerificationReporter reporter;
    private static int stage;
    private static int dueTick;
    private static FireballProjectileEntity wallShot;
    private static FireballProjectileEntity skyShot;
    private static FireballProjectileEntity pigShot;
    private static Pig wallPig;
    private static Pig targetPig;
    private static ServerPlayer caster;

    private FireballVerification() {}

    public static void register() {
        if (!VerificationReporter.liveWorldVerificationEnabled()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!VerificationReporter.isDevEnvironment()) return;
            server.overworld().setChunkForced(CHUNK, CHUNK, true);
            stage = 1;
            dueTick = server.getTickCount() + 400;
        });
        ServerTickEvents.END_SERVER_TICK.register(FireballVerification::onServerTick);
        registerDamageRecorder();
    }

    private static void onServerTick(MinecraftServer server) {
        if (stage == 1 && server.getTickCount() >= dueTick) {
            stage = 2;
            dueTick = server.getTickCount() + 40;
            launch(server.overworld());
        } else if (stage == 2 && server.getTickCount() >= dueTick) {
            stage = 3;
            dueTick = server.getTickCount() + 80;          // the sky shot lives 100 ticks
            wallChecks(server.overworld());
        } else if (stage == 3 && server.getTickCount() >= dueTick) {
            stage = 4;
            dueTick = server.getTickCount() + DAMAGE_WATCH_TICKS;
            expiryChecks(server.overworld());
            detonate(server.overworld(), true);                 // D1: saves fail, the caster inside the sphere
        } else if (stage == 4 && server.getTickCount() >= dueTick) {
            stage = 5;
            dueTick = server.getTickCount() + DAMAGE_WATCH_TICKS;
            detonationChecks(server.overworld(), true);
            detonate(server.overworld(), false);                // D2: saves succeed, the caster outside
        } else if (stage == 5 && server.getTickCount() >= dueTick) {
            stage = 0;
            detonationChecks(server.overworld(), false);
            server.overworld().setChunkForced(CHUNK, CHUNK, false);
            reporter.summarize();
        }
    }

    // ── Controlled detonations: exactly one Fire damage event per creature in the sphere, nothing else ──────────
    //
    // A fireball is set 1 block in front of the wall, flying into it, so it detonates on the next tick at a known
    // centre. Every damage event a watched entity receives over the next DAMAGE_WATCH_TICKS is recorded. The recorder
    // runs before VanillaDamageInterceptor and before vanilla's invulnerability check, so a vanilla hit arrives twice
    // (the raw event, then the interceptor's Totality re-issue nested inside it), and a creature standing in fire
    // produces events every tick. Each event is therefore attributed by who issued it (see origin): the fireball's
    // explosion, vanilla environmental fire and its re-issue, a condition tick, or anything else. Saving throws roll
    // on the target's own RNG with natural 20 / 1 always saving / failing, so the spell save DC is set to +100 (all
    // fail) or -100 (all save), and the save effect is checked on the same seeded rolls in both runs. The creatures
    // are given enough health to survive the blast, so the ones left standing in the ignition's fire are observed
    // every run (a pig dying at the detonation used to hide it), and a bystander outside the sphere always stands in
    // a fire of its own.

    private static final int DAMAGE_WATCH_TICKS = 60;
    private static final int TARGET_DC_FAIL = 100;
    private static final int TARGET_DC_SAVE = -100;
    private static final double IMPACT_X = X0 + 6.5;
    private static final double IMPACT_Y = FLOOR_Y + 2.0;
    /** Pig positions (dx, dz) from the impact point; each pig stands on the floor, a block under the centre. */
    private static final double[][] RING = {{-1.5, 1.5}, {1.5, 1.5}, {-3.0, 2.5}, {3.0, 2.5}, {0.0, 4.5}, {-2.0, 4.6}};

    private static final double STURDY_HEALTH = 60.0;

    /**
     * Who issued a damage event: the fireball's detonation; vanilla fire the creature stands in (in_fire / on_fire)
     * or VanillaDamageInterceptor's re-issue of it; a Totality condition tick; or anything else (never expected).
     */
    private enum Origin { SPELL, ENVIRONMENT, CONVERTED_ENVIRONMENT, CONDITION, OTHER }

    private record Hit(UUID entity, int tick, String type, Entity attacker, float amount, Origin origin) {}

    private static final List<Hit> HITS = new ArrayList<>();
    private static final Set<UUID> WATCHED = new HashSet<>();
    /** The origin of the last raw (not re-issued) vanilla event per entity: what a nested re-issue converts. */
    private static final Map<UUID, Origin> LAST_RAW = new HashMap<>();
    private static boolean recording;
    private static int detonationTick;
    private static List<Pig> ringPigs = List.of();
    private static Pig casterPig;
    private static Pig outerPig;
    private static Pig burningPig;
    private static BlockPos burningPigFloor;
    private static Arrow restingArrow;
    private static Vec3 outerPigAt;
    private static Vec3 arrowAt;
    private static long ringSeed;
    private static double[] failRunTaken;

    /** Records every damage a watched entity takes while a controlled detonation is being observed. */
    private static void registerDamageRecorder() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (recording && WATCHED.contains(entity.getUUID())) {
                HITS.add(new Hit(entity.getUUID(), entity.level().getServer().getTickCount(), source.getMsgId(), source.getEntity(), amount,
                        origin(entity.getUUID(), source.getMsgId())));
            }
            return true;
        });
    }

    /**
     * Attributes an event by the code that issued it, read from the call stack: FireballProjectileEntity (the
     * detonation's damage, and anything else it caused, e.g. a vanilla explosion), ConditionServerTick (a condition
     * tick), VanillaDamageInterceptor (the re-issue of the raw vanilla event recorded just before for this entity,
     * inside the same dispatch), or else a raw vanilla event, environmental only if it is fire the creature stands in.
     */
    private static Origin origin(UUID entity, String type) {
        Set<String> callers = StackWalker.getInstance().walk(frames -> frames.map(StackWalker.StackFrame::getClassName).collect(Collectors.toSet()));
        if (callers.contains(FireballProjectileEntity.class.getName())) return Origin.SPELL;
        if (callers.contains(ConditionServerTick.class.getName())) return Origin.CONDITION;
        if (callers.contains(VanillaDamageInterceptor.class.getName())) {
            return LAST_RAW.get(entity) == Origin.ENVIRONMENT ? Origin.CONVERTED_ENVIRONMENT : Origin.OTHER;
        }
        Origin raw = isEnvironmentalFire(type) ? Origin.ENVIRONMENT : Origin.OTHER;
        LAST_RAW.put(entity, raw);
        return raw;
    }

    private static void detonate(ServerLevel level, boolean failSaves) {
        clearFire(level);
        level.getEntitiesOfClass(Pig.class, new AABB(X0, FLOOR_Y, X0, X0 + 16, FLOOR_Y + 6, X0 + 16)).forEach(Pig::discard);
        HITS.clear();
        WATCHED.clear();
        LAST_RAW.clear();
        Vec3 centre = new Vec3(IMPACT_X, IMPACT_Y, WALL_Z - 0.25);
        List<Pig> pigs = new ArrayList<>();
        // Both runs seed each ring position's pig alike, so it rolls the same 8d6 and the same d20 in D1 and D2; the
        // seed is new each launch, so the rolls differ from launch to launch.
        if (failSaves) ringSeed = level.getRandom().nextLong();
        for (int i = 0; i < RING.length; i++) {
            Pig pig = sturdy(spawnPig(level, IMPACT_X + RING[i][0], FLOOR_Y + 1, WALL_Z - 0.25 - RING[i][1]));
            pig.getRandom().setSeed(ringSeed + i);
            pigs.add(pig);
        }
        ringPigs = pigs;
        // D1: the caster stands inside the sphere (3.2 blocks); D2: 12 blocks out, well clear of it.
        casterPig = sturdy(failSaves ? spawnPig(level, IMPACT_X + 2.5, FLOOR_Y + 1, WALL_Z - 1.75)
                : spawnPig(level, IMPACT_X, FLOOR_Y + 1, WALL_Z - 12.25));
        // 7.1 blocks out: outside the 6-block sphere, inside the old vanilla explosion's 9.6-block reach.
        outerPig = sturdy(spawnPig(level, IMPACT_X + 4.0, FLOOR_Y + 1, WALL_Z - 6.0));
        outerPigAt = outerPig.position();
        // 9.8 blocks out (and outside the 6-block fire search around the centre): a bystander in a fire that never
        // goes out (on netherrack), so environmental fire and its Burning are observed every run.
        burningPig = sturdy(spawnPig(level, IMPACT_X - 4.0, FLOOR_Y + 1, WALL_Z - 9.25));
        burningPigFloor = BlockPos.containing(burningPig.position()).below();
        level.setBlockAndUpdate(burningPigFloor, Blocks.NETHERRACK.defaultBlockState());
        level.setBlockAndUpdate(burningPigFloor.above(), BaseFireBlock.getState(level, burningPigFloor.above()));
        // A motionless, weightless arrow 3.2 blocks from the centre: a vanilla explosion would push it.
        restingArrow = EntityTypes.ARROW.create(level, EntitySpawnReason.COMMAND);
        restingArrow.snapTo(IMPACT_X - 2.0, IMPACT_Y + 0.5, WALL_Z - 2.75, 0.0F, 0.0F);
        restingArrow.setNoGravity(true);
        restingArrow.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(restingArrow);
        arrowAt = restingArrow.position();
        for (Pig pig : pigs) WATCHED.add(pig.getUUID());
        WATCHED.add(casterPig.getUUID());
        WATCHED.add(outerPig.getUUID());
        WATCHED.add(burningPig.getUUID());
        FireballProjectileEntity shot = FireballProjectileEntity.create(level, casterPig, failSaves ? TARGET_DC_FAIL : TARGET_DC_SAVE);
        shot.setPos(centre.x, centre.y, WALL_Z - 1.0);
        shot.setDeltaMovement(0, 0, 1.2);
        level.addFreshEntity(shot);
        detonationTick = level.getServer().getTickCount() + 1;
        recording = true;
    }

    private static void detonationChecks(ServerLevel level, boolean failSaves) {
        recording = false;
        VerificationReporter r = reporter;
        String run = failSaves ? "D1 (saves fail, caster inside)" : "D2 (saves succeed, caster outside)";
        Vec3 centre = new Vec3(IMPACT_X, IMPACT_Y, WALL_Z - 0.25);
        List<Hit> fireballHits = HITS.stream().filter(h -> h.origin() == Origin.SPELL).toList();
        // Environmental fire, its re-issue, and condition ticks that follow it (Burning, applied by the re-issue with no
        // applier) are the ignition's, not the blast's. Everything else is unexplained.
        List<Hit> unexplained = HITS.stream().filter(h -> h.origin() == Origin.OTHER
                || (h.origin() == Origin.CONDITION && (h.attacker() != null || !burnedBefore(h)))).toList();
        check(r, run + ": the fireball detonated", () -> fireballs(level).isEmpty() && fireNear(level, BlockPos.containing(centre), 6) > 0);
        check(r, run + ": ignition kept (fire placed within the sphere)", () -> fireNear(level, BlockPos.containing(centre), 6) > 0);
        for (Pig pig : ringPigs) {
            List<Hit> own = fireballHits.stream().filter(h -> h.entity().equals(pig.getUUID())).toList();
            double dist = pig.position().distanceTo(centre);
            check(r, String.format(Locale.ROOT, "%s: a creature %.1f blocks in takes exactly one damage event, from the caster, at the detonation: %s",
                    run, dist, describe(own)), () ->
                    dist <= FireballProjectileEntity.BLAST_RADIUS && own.size() == 1 && own.get(0).tick() == detonationTick
                            && own.get(0).attacker() == casterPig && own.get(0).amount() >= 4 && own.get(0).amount() <= 48);
        }
        // Per ring position, in the order of RING (the same seeded rolls in both runs).
        double[] taken = ringPigs.stream().mapToDouble(p -> fireballHits.stream().filter(h -> h.entity().equals(p.getUUID()))
                .mapToDouble(Hit::amount).sum()).toArray();
        if (failSaves) {
            failRunTaken = taken;
            List<Hit> self = fireballHits.stream().filter(h -> h.entity().equals(casterPig.getUUID())).toList();
            check(r, run + ": the caster inside the sphere takes one normal Fire hit (no attacker, not a self-attack): " + describe(self), () ->
                    self.size() == 1 && self.get(0).attacker() == null && self.get(0).tick() == detonationTick
                            && self.get(0).amount() >= 4 && self.get(0).amount() <= 48);
        } else {
            // Against DC +100 a save fails, against -100 it succeeds, except on a natural 20 / 1, which the shared d20
            // then gives both runs alike (the same amount twice). Otherwise the failed save took the whole 8d6 and the
            // successful one exactly half of it. Saves having no effect would leave nothing halved.
            int halved = 0;
            boolean paired = true;
            StringBuilder pairs = new StringBuilder();
            for (int i = 0; i < taken.length; i++) {
                double full = failRunTaken[i], half = taken[i];
                pairs.append(String.format(Locale.ROOT, " %.1f/%.1f", full, half));
                if (half * 2 == full && full == Math.rint(full) && full >= 8 && full <= 48) halved++;
                else if (half != full) paired = false;
            }
            int halvedCount = halved;
            boolean allPaired = paired;
            check(r, String.format(Locale.ROOT, "%s: a successful save takes exactly half the same 8d6 a failed save takes in full"
                    + " (seed %d, fail/save per creature:%s; %d halved, the rest a shared natural 1 or 20)", run, ringSeed, pairs, halved), () ->
                    allPaired && halvedCount >= 1);
            check(r, run + ": a caster outside the sphere takes nothing", () ->
                    fireballHits.stream().noneMatch(h -> h.entity().equals(casterPig.getUUID())));
        }
        check(r, run + ": a creature 7.1 blocks out (inside the old explosion's reach) takes no damage", () ->
                fireballHits.stream().noneMatch(h -> h.entity().equals(outerPig.getUUID())));
        check(r, String.format(Locale.ROOT, "%s: ... and is not knocked back (moved %.4f)", run, outerPig.position().distanceTo(outerPigAt)), () ->
                outerPig.position().distanceTo(outerPigAt) < 1e-3);
        check(r, String.format(Locale.ROOT, "%s: a resting projectile in the blast is not displaced (moved %.4f, speed %.4f)", run,
                restingArrow.position().distanceTo(arrowAt), restingArrow.getDeltaMovement().length()), () ->
                restingArrow.position().distanceTo(arrowAt) < 1e-3 && restingArrow.getDeltaMovement().length() < 1e-3);
        List<Hit> bystander = HITS.stream().filter(h -> h.entity().equals(burningPig.getUUID())).toList();
        check(r, run + ": a bystander standing in fire outside the sphere: its fire, the re-issue and Burning are attributed to the "
                + "environment, none to the fireball: " + count(bystander), () ->
                bystander.stream().anyMatch(h -> h.origin() == Origin.ENVIRONMENT)
                        && bystander.stream().anyMatch(h -> h.origin() == Origin.CONVERTED_ENVIRONMENT)
                        && bystander.stream().anyMatch(h -> h.origin() == Origin.CONDITION)
                        && bystander.stream().noneMatch(h -> h.origin() == Origin.SPELL || h.origin() == Origin.OTHER));
        check(r, run + ": no damage event of any other kind (no explosion or Force hit): " + HITS.size() + " recorded, "
                + count(HITS) + ", unexplained: " + describe(unexplained), () ->
                unexplained.isEmpty()
                        && fireballHits.stream().allMatch(h -> h.tick() == detonationTick)
                        && fireballHits.stream().map(Hit::entity).distinct().count() == fireballHits.size());
        restingArrow.discard();
        level.getEntitiesOfClass(Pig.class, new AABB(X0, FLOOR_Y - 2, X0 - 4, X0 + 16, FLOOR_Y + 8, X0 + 16)).forEach(Pig::discard);
        clearFire(level);
        level.setBlockAndUpdate(burningPigFloor, Blocks.STONE.defaultBlockState());
    }

    /** A condition tick is the environment's only if that creature already stood in environmental fire. */
    private static boolean burnedBefore(Hit tick) {
        return HITS.stream().anyMatch(h -> h.entity().equals(tick.entity()) && h.tick() <= tick.tick()
                && (h.origin() == Origin.ENVIRONMENT || h.origin() == Origin.CONVERTED_ENVIRONMENT));
    }

    private static String count(List<Hit> hits) {
        Map<Origin, Long> n = hits.stream().collect(Collectors.groupingBy(Hit::origin, Collectors.counting()));
        return "spell " + n.getOrDefault(Origin.SPELL, 0L) + ", environmental fire " + n.getOrDefault(Origin.ENVIRONMENT, 0L)
                + " (+" + n.getOrDefault(Origin.CONVERTED_ENVIRONMENT, 0L) + " re-issued), condition ticks "
                + n.getOrDefault(Origin.CONDITION, 0L) + ", other " + n.getOrDefault(Origin.OTHER, 0L);
    }

    /** Burning in the fire the blast set (in_fire / on_fire) is the ignition's, not the blast's. */
    private static boolean isEnvironmentalFire(String type) {
        return type.equals("inFire") || type.equals("onFire") || type.equals("in_fire") || type.equals("on_fire");
    }

    private static String describe(List<Hit> hits) {
        StringBuilder b = new StringBuilder(hits.size() + " event(s)");
        for (Hit h : hits) {
            b.append(String.format(Locale.ROOT, " [%s %.1f%s]", h.type(), h.amount(), h.attacker() == null ? ", no attacker" : ""));
        }
        return b.toString();
    }

    private static void launch(ServerLevel level) {
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "FireballVerification");
        reporter = r;
        buildStage(level);
        ServerPlayer caster = TotalityFakePlayer.create(level, "FireballCaster");
        caster.setGameMode(GameType.SURVIVAL);

        // The blast-centre rule, directly.
        Vec3 start = new Vec3(X0 + 8.5, FLOOR_Y + 1.5, WALL_Z - 1.0), velocity = new Vec3(0, 0, 1.2);
        BlockHitResult wallHit = new BlockHitResult(new Vec3(X0 + 8.5, FLOOR_Y + 1.5, WALL_Z), Direction.NORTH,
                new BlockPos(X0 + 8, FLOOR_Y + 1, WALL_Z), false);
        check(r, "impact rule: a block hit detonates a quarter block in front of the hit point, not at the tick's start", () ->
                FireballProjectileEntity.impactPoint(wallHit, start, velocity).distanceTo(new Vec3(X0 + 8.5, FLOOR_Y + 1.5, WALL_Z - 0.25)) < 1e-6);

        // A real cast at the wall (12.5 blocks away), a pig standing against the wall beside the impact.
        wallPig = spawnPig(level, X0 + 9.5, FLOOR_Y + 1, WALL_Z - 1.5);
        aim(caster, X0 + 8.5, FLOOR_Y + 1, X0 + 1.5, 0.0F, 0.0F);
        new FireballSpell().onActivate(caster, null);
        List<FireballProjectileEntity> cast = fireballs(level);
        check(r, "a cast releases exactly one fireball, owned by the caster, flying at 1.2 blocks a tick", () ->
                cast.size() == 1 && cast.get(0).getOwner() == caster && Math.abs(cast.get(0).getDeltaMovement().length() - 1.2) < 1e-4);
        wallShot = cast.isEmpty() ? null : cast.get(0);

        // A shot straight up into the open sky (nothing to hit) from the stage's far corner.
        aim(caster, X0 + 2.5, FLOOR_Y + 1, X0 + 2.5, 0.0F, -90.0F);
        new FireballSpell().onActivate(caster, null);
        skyShot = fireballs(level).stream().filter(f -> f != wallShot).findFirst().orElse(null);

        check(r, "two fireballs in flight, independent of each other", () -> fireballs(level).size() == 2);
        FireballVerification.caster = caster;
    }

    private static void wallChecks(ServerLevel level) {
        VerificationReporter r = reporter;
        check(r, "the wall shot detonated and was removed", () -> wallShot != null && wallShot.isRemoved());
        check(r, "... at the true impact point: " + (wallShot == null ? "-" : fmt(wallShot.position()))
                + " (wall face at z " + WALL_Z + ", centre expected 0.25 in front)", () ->
                Math.abs(wallShot.getZ() - (WALL_Z - 0.25)) < 1e-3 && Math.abs(wallShot.getX() - (X0 + 8.5)) < 1e-3);
        check(r, "the pig beside the impact was hurt by the blast", () ->
                wallPig.isRemoved() || wallPig.isDeadOrDying() || wallPig.getHealth() < wallPig.getMaxHealth());
        check(r, "ignition kept: the blast set fire around the impact", () -> fireNear(level, BlockPos.containing(wallShot.position()), 6) > 0);
        check(r, "the sky shot is still flying (no false detonation in the open)", () -> skyShot != null && !skyShot.isRemoved());

        // Fired only once the wall shot has landed, so the two blasts stay apart: a shot at a pig in the open,
        // 6 blocks ahead: eye 1.52 above the floor, pig centre 0.45 -> ~10 degrees down.
        clearFire(level);
        targetPig = spawnPig(level, X0 + 14.5, FLOOR_Y + 1, X0 + 8.5);
        aim(caster, X0 + 14.5, FLOOR_Y + 1, X0 + 2.5, 0.0F, (float) Math.toDegrees(Math.atan2(1.07, 6.0)));
        new FireballSpell().onActivate(caster, null);
        pigShot = fireballs(level).stream().filter(f -> f != skyShot).findFirst().orElse(null);
    }

    private static void expiryChecks(ServerLevel level) {
        VerificationReporter r = reporter;
        check(r, "the pig shot detonated where its path meets the pig: " + (pigShot == null ? "-" : fmt(pigShot.position())), () ->
                pigShot != null && pigShot.isRemoved() && targetPig.getBoundingBox().inflate(0.26).contains(pigShot.position()));
        check(r, "after its 100-tick lifetime the sky shot expired and was removed", () -> skyShot.isRemoved());
        check(r, "... without exploding (nothing lit where it was)", () -> fireNear(level, BlockPos.containing(skyShot.position()), 4) == 0);
        check(r, "no Fireball projectile is left anywhere near the stage", () ->
                level.getEntitiesOfClass(FireballProjectileEntity.class, new AABB(X0 - 64, 0, X0 - 64, X0 + 80, 400, X0 + 80)).isEmpty());
        for (Pig pig : new Pig[]{wallPig, targetPig}) if (!pig.isRemoved()) pig.discard();
        clearFire(level);
    }

    private static List<FireballProjectileEntity> fireballs(ServerLevel level) {
        return level.getEntitiesOfClass(FireballProjectileEntity.class, new AABB(X0 - 16, FLOOR_Y - 8, X0 - 16, X0 + 32, FLOOR_Y + 300, X0 + 32),
                f -> !f.isRemoved());
    }

    private static void aim(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.snapTo(x, y, z, yaw, pitch);
        p.setYHeadRot(yaw);
    }

    private static Pig spawnPig(ServerLevel level, double x, double y, double z) {
        Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
        pig.snapTo(x, y, z, 0.0F, 0.0F);
        pig.setNoAi(true);
        level.addFreshEntity(pig);
        return pig;
    }

    private static Pig sturdy(Pig pig) {
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(STURDY_HEALTH);
        pig.setHealth((float) STURDY_HEALTH);
        return pig;
    }

    private static int fireNear(ServerLevel level, BlockPos at, int r) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -r, -r), at.offset(r, r, r))) {
            if (level.getBlockState(p).getBlock() instanceof BaseFireBlock) n++;
        }
        return n;
    }

    private static void clearFire(ServerLevel level) {
        for (BlockPos p : BlockPos.betweenClosed(X0, FLOOR_Y, X0, X0 + 15, FLOOR_Y + 6, X0 + 15)) {
            if (level.getBlockState(p).getBlock() instanceof BaseFireBlock) level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        }
    }

    private static void buildStage(ServerLevel level) {
        for (int x = X0; x < X0 + 16; x++) {
            for (int z = X0; z < X0 + 16; z++) {
                level.setBlockAndUpdate(new BlockPos(x, FLOOR_Y, z), Blocks.STONE.defaultBlockState());
                // clear up to the world's surface: the sky shot must have open sky (the random verification world
                // once had terrain over the stage, and the shot hit it)
                int top = Math.max(FLOOR_Y + 8, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
                for (int y = FLOOR_Y + 1; y <= top; y++) {
                    boolean wall = z == WALL_Z && x <= X0 + 11 && y <= FLOOR_Y + 4;
                    level.setBlockAndUpdate(new BlockPos(x, y, z), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static String fmt(Vec3 v) {
        return String.format("(%.3f, %.3f, %.3f)", v.x, v.y, v.z);
    }

    private static void check(VerificationReporter r, String label, Supplier<Boolean> body) {
        try {
            r.check(label, body.get(), "");
        } catch (RuntimeException e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
