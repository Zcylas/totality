package zcylas.totality.entity.animal;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.resources.ResourceKey;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.ServerScheduler;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.init.ModEntities;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.Map;
import java.util.TreeMap;

/**
 * Dev-only, live-world opt-in: the Forest Boar on a real (dedicated) server — it is created with its
 * attributes, spawns naturally only in forests, and its real loot table rolls only 1-2 Raw Meat (more with
 * Looting). Then live, ticking boars (the verification server has no players): they stay peaceful undisturbed,
 * environmental damage does not startle them, a living attacker makes one alert then flee (no charge: it is not a
 * player), a fleeing boar is still there before 5 s and is then silently removed (not killed, nothing dropped),
 * and a named boar flees but is never removed. Common code only: nothing here touches the client model or renderer.
 */
public final class ForestBoarVerification {

    private static final int SUITE_DELAY_TICKS = 5;
    private static final int ROLLS = 400;
    /** The live boars' stage: chunks held entity-ticking for the test only (the boar itself never force-loads). */
    private static final int STAGE_CHUNK = 20;
    private static final int STAGE_RADIUS = 3;

    private ForestBoarVerification() {}

    public static void register() {
        if (!VerificationReporter.liveWorldVerificationEnabled()) return; // opt-in: runs against the live world
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                ServerScheduler.getInstance().queue(ForestBoarVerification::runSelfTest, SUITE_DELAY_TICKS));
    }

    private static void runSelfTest(MinecraftServer server) {
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "ForestBoarVerification");
        ServerLevel level = server.overworld();

        ForestBoarEntity boar = ModEntities.FOREST_BOAR.create(level, EntitySpawnReason.COMMAND);
        r.check("the entity is created on the server", boar != null, "create returned null");
        if (boar == null) {
            r.summarize();
            return;
        }
        boar.setPos(0.5, 320, 0.5); // never added to the world: only its attributes and loot are exercised
        r.check("attributes: 16 health, 3 attack", boar.getMaxHealth() == 16.0F
                        && boar.getAttributeValue(Attributes.ATTACK_DAMAGE) == 3.0,
                "health " + boar.getMaxHealth() + ", attack " + boar.getAttributeValue(Attributes.ATTACK_DAMAGE));
        r.check("a creature (counts against the passive-mob cap)", ModEntities.FOREST_BOAR.getCategory() == MobCategory.CREATURE,
                String.valueOf(ModEntities.FOREST_BOAR.getCategory()));

        var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        for (ResourceKey<Biome> forest : new ResourceKey[]{Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.DARK_FOREST}) {
            r.check("natural spawn entry in " + forest.identifier(), spawnsIn(biomes.getOrThrow(forest).value()), "missing");
        }
        for (ResourceKey<Biome> other : new ResourceKey[]{Biomes.PLAINS, Biomes.DESERT, Biomes.TAIGA, Biomes.JUNGLE}) {
            r.check("no natural spawn in " + other.identifier(), !spawnsIn(biomes.getOrThrow(other).value()), "present");
        }

        // The real loot table, through the entity's own vanilla drop path (the consumer collects instead of spawning).
        ServerPlayer hunter = TotalityFakePlayer.create(level, "[ForestBoarVerification-hunter]");
        try {
            Map<Integer, Integer> plain = roll(level, boar, hunter, 0);
            r.check("no Looting: every death drops only Raw Meat, 1 or 2", plain.keySet().stream().allMatch(n -> n == 1 || n == 2)
                    && plain.containsKey(1) && plain.containsKey(2), "counts " + plain);
            Map<Integer, Integer> looting = roll(level, boar, hunter, 3);
            r.check("Looting III: only Raw Meat, 1 to 5, more on average", looting.keySet().stream().allMatch(n -> n >= 1 && n <= 5)
                    && mean(looting) > mean(plain) + 1.0, "counts " + looting + " vs " + plain);
            if (VerificationReporter.verbose()) {
                Totality.LOGGER.info("[ForestBoarVerification] Raw Meat per death over {} rolls: plain {}, Looting III {}", ROLLS, plain, looting);
            }
        } catch (IllegalStateException e) {
            r.check("loot rolls", false, e.getMessage());
        } finally {
            hunter.discard();
            boar.discard();
        }
        liveBehaviour(r, level);
    }

    /** Live boars on a player-less server; summarizes the suite when done. */
    private static void liveBehaviour(VerificationReporter r, ServerLevel level) {
        java.util.List<Long> forced = new java.util.ArrayList<>();
        for (int cx = STAGE_CHUNK - STAGE_RADIUS; cx <= STAGE_CHUNK + STAGE_RADIUS; cx++) {
            for (int cz = STAGE_CHUNK - STAGE_RADIUS; cz <= STAGE_CHUNK + STAGE_RADIUS; cz++) {
                long key = net.minecraft.world.level.ChunkPos.pack(cx, cz);
                if (!level.getForceLoadedChunks().contains(key)) {
                    level.setChunkForced(cx, cz, true);
                    forced.add(key);
                }
            }
        }
        int x = STAGE_CHUNK * 16 + 8, z = STAGE_CHUNK * 16 + 8;
        ForestBoarEntity calm = spawn(level, x, z, null);
        ForestBoarEntity bumped = spawn(level, x + 3, z, null);
        ForestBoarEntity attacked = spawn(level, x - 3, z, null);
        ForestBoarEntity named = spawn(level, x, z + 3, "Kept");
        net.minecraft.world.entity.animal.pig.Pig attacker = net.minecraft.world.entity.EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
        if (calm == null || bumped == null || attacked == null || named == null || attacker == null) {
            r.check("live boars spawned", false, "spawn failed");
            release(level, forced);
            r.summarize();
            return;
        }
        attacker.setPos(x - 5, calm.getY(), z);
        r.check("never despawned for distance (the escape is its only disappearance)", !calm.removeWhenFarAway(1.0e6), "removeWhenFarAway");
        bumped.hurtServer(level, level.damageSources().generic(), 1.0F);          // no attacker: not a startle
        attacked.hurtServer(level, level.damageSources().mobAttack(attacker), 1.0F);
        named.hurtServer(level, level.damageSources().mobAttack(attacker), 1.0F);
        ForestBoarEntity.Behavior startled = attacked.getBehavior();
        ServerScheduler sched = ServerScheduler.getInstance();
        sched.queue(s -> {
            r.check("undisturbed boar stays peaceful (" + calm.getBehavior() + ")", calm.getBehavior().peaceful(), String.valueOf(calm.getBehavior()));
            r.check("environmental damage does not startle it (" + bumped.getBehavior() + ")", bumped.getBehavior().peaceful(), String.valueOf(bumped.getBehavior()));
            r.check("a living attacker startles it: alert, then flee", startled == ForestBoarEntity.Behavior.ALERT
                    && attacked.getBehavior() == ForestBoarEntity.Behavior.FLEE, startled + " -> " + attacked.getBehavior());
            r.check("no charge at a non-player attacker", !attacked.hasCharged(), "charged");
        }, 40);
        sched.queue(s -> r.check("a fleeing boar is still present before 5 s of fleeing (" + attacked.ticksInBehavior() + " ticks)",
                !attacked.isRemoved() && attacked.getBehavior() == ForestBoarEntity.Behavior.FLEE
                        && attacked.ticksInBehavior() < ForestBoarRules.ESCAPE_MIN_FLEE_TICKS, "removed early or not fleeing"), 90);
        sched.queue(s -> {
            net.minecraft.world.phys.Vec3 at = attacked.position();
            int items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(8)).size();
            r.check("with no player anywhere it escapes: removed silently, not killed", attacked.isRemoved() && attacked.hasEscaped()
                            && attacked.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.DISCARDED && !attacked.isDeadOrDying(),
                    "removed " + attacked.isRemoved() + ", reason " + attacked.getRemovalReason() + ", escaped " + attacked.hasEscaped());
            r.check("an escape drops nothing (" + items + " items where it vanished)", items == 0, items + " items");
            r.check("a named boar flees but is never removed", !named.isRemoved() && named.getBehavior() == ForestBoarEntity.Behavior.FLEE,
                    "removed " + named.isRemoved() + ", " + named.getBehavior());
            for (ForestBoarEntity b : new ForestBoarEntity[]{calm, bumped, named}) b.discard();
            release(level, forced);
            r.summarize();
        }, 150);
    }

    private static ForestBoarEntity spawn(ServerLevel level, int x, int z, String name) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        ForestBoarEntity b = ModEntities.FOREST_BOAR.create(level, EntitySpawnReason.COMMAND);
        if (b == null) return null;
        b.snapTo(x + 0.5, y, z + 0.5, 0.0F, 0.0F);
        if (name != null) b.setCustomName(net.minecraft.network.chat.Component.literal(name));
        level.addFreshEntity(b);
        return b;
    }

    private static void release(ServerLevel level, java.util.List<Long> forced) {
        for (long key : forced) level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(key), net.minecraft.world.level.ChunkPos.getZ(key), false);
    }

    private static boolean spawnsIn(Biome biome) {
        return biome.getMobSettings().getMobs(MobCategory.CREATURE).unwrap().stream()
                .anyMatch(w -> w.value().type() == ModEntities.FOREST_BOAR);
    }

    /** Raw Meat per death -> number of deaths; throws if anything else drops. */
    private static Map<Integer, Integer> roll(ServerLevel level, ForestBoarEntity boar, ServerPlayer hunter, int lootingLevel) {
        ItemStack weapon = new ItemStack(Items.IRON_SWORD);
        if (lootingLevel > 0) {
            weapon.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING), lootingLevel);
        }
        hunter.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon);
        var table = boar.getLootTable().orElseThrow(() -> new IllegalStateException("no loot table"));
        Map<Integer, Integer> counts = new TreeMap<>();
        for (int i = 0; i < ROLLS; i++) {
            int[] meat = {0};
            boar.dropFromLootTable(level, level.damageSources().playerAttack(hunter), true, table, stack -> {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (!id.equals("totality:raw_meat")) throw new IllegalStateException("unexpected drop " + id);
                meat[0] += stack.getCount();
            });
            counts.merge(meat[0], 1, Integer::sum);
        }
        return counts;
    }

    private static double mean(Map<Integer, Integer> counts) {
        int n = 0, sum = 0;
        for (var e : counts.entrySet()) {
            n += e.getValue();
            sum += e.getKey() * e.getValue();
        }
        return n == 0 ? 0 : (double) sum / n;
    }
}
