package zcylas.totality.api.ability.kryptonian;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.client.particles.TotalityParticles;
import zcylas.totality.api.combat.damage.DamageFlags;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.combat.damage.TotalityDamage;
import zcylas.totality.api.rpg.ancestry.OriginData;
import zcylas.totality.api.rpg.ancestry.OriginRegistry;
import zcylas.totality.api.rpg.mana.PlayerManaManager;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;
import zcylas.totality.networking.mana.SyncManaPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class HeatVisionAbility extends Ability {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final float RANGE           = 20.0f;
    private static final int   MANA_PER_TICK   = 2;
    private static final float DAMAGE_PER_TICK = 2.0f; // vanilla = 2.5 display HP per tick

    public HeatVisionAbility() {
        super(
                Identifier.fromNamespaceAndPath("totality", "heat_vision"),
                "Heat Vision",
                "Channel concentrated solar energy from your eyes, burning targets in a straight line.",
                Type.CHANNELED,
                0, // no cooldown — limited by mana drain
                Identifier.fromNamespaceAndPath("totality", "textures/ability/heat_vision.png"),
                Source.SPECIES,
                "Kryptonian",
                "The sun burns within your gaze."
        );
    }

    @Override
    public boolean isDefault() {
        return false; // temporary — will be false when Origins are data-driven
    }

    // ── Activation guard ──────────────────────────────────────────────────────

    @Override
    public boolean canActivate(ServerPlayer player, @Nullable AbilityContext context) {
        // Ancestry check
        var ancestry = AncestryComponents.get(player);
        if (!ancestry.hasAncestry()) return false;
        var origin = ancestry.getOriginData();
        if (origin != OriginRegistry.KRYPTONIAN) return false;

        // Mana check
        return PlayerManaManager.getMana(player) >= MANA_PER_TICK;
    }

    // ── Channel lifecycle ─────────────────────────────────────────────────────

    @Override
    public void onChannelStart(ServerPlayer player, @Nullable AbilityContext context) {
        // Nothing special on start — tick handles everything
    }

    @Override
    public void onChannel(ServerPlayer player, @Nullable AbilityContext context) {
        // Drain mana — stop if empty
        int currentMana = PlayerManaManager.getMana(player);
        if (currentMana < MANA_PER_TICK) {
            stopChanneling(player);
            return;
        }

        PlayerManaManager.removeMana(player, MANA_PER_TICK);
        ServerPlayNetworking.send(player, new SyncManaPayload(
                PlayerManaManager.getMana(player),
                PlayerManaManager.getMaxMana(player)
        ));

        // Raycast from eyes
        Vec3 eyePos   = player.getEyePosition();
        Vec3 lookDir  = player.getLookAngle();
        Vec3 endPos   = eyePos.add(lookDir.scale(RANGE));

        ServerLevel level = player.level();

        // Check entity hit first
        LivingEntity hitEntity = raycastEntity(player, level, eyePos, endPos);

        if (hitEntity != null) {
            // Damage the hit entity
            TotalityDamage.hurt(hitEntity, player,
                    DamageTypes.RADIANT, DAMAGE_PER_TICK, DamageFlags.MAGICAL);

            Vec3 hitPos = hitEntity.getEyePosition();

        } else {
            // Raycast blocks
            BlockHitResult blockHit = level.clip(new ClipContext(
                    eyePos, endPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));

            Vec3 hitPos = blockHit.getType() == HitResult.Type.MISS
                    ? endPos
                    : blockHit.getLocation();


            // Scorch block on hit
            if (blockHit.getType() != HitResult.Type.MISS) {
                scorchBlock(level, blockHit);
            }
        }
    }

    @Override
    public void onChannelStop(ServerPlayer player, @Nullable AbilityContext context) {
        // Nothing on stop for now — future: cooldown, sound etc.
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Nullable
    private LivingEntity raycastEntity(ServerPlayer player,
                                       ServerLevel level,
                                       Vec3 start, Vec3 end) {
        AABB searchBox = player.getBoundingBox()
                .expandTowards(player.getLookAngle().scale(RANGE))
                .inflate(1.0);

        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (LivingEntity entity : level.getEntitiesOfClass(
                LivingEntity.class, searchBox,
                e -> e != player && e.isAlive())) {

            AABB entityBox = entity.getBoundingBox().inflate(0.1);
            var result = entityBox.clip(start, end);

            if (result.isPresent()) {
                double dist = start.distanceTo(result.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = entity;
                }
            }
        }

        return closest;
    }

    private void scorchBlock(ServerLevel level, BlockHitResult hit) {
        var pos   = hit.getBlockPos();
        var state = level.getBlockState(pos);

        if (state.is(BlockTags.ICE)) {
            level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
            return;
        }

        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
            level.removeBlock(pos, false);
            return;
        }

        var above = pos.above();
        if (level.getBlockState(above).isAir()) {
            if (state.is(BlockTags.LOGS_THAT_BURN)
                    || state.is(BlockTags.LEAVES)
                    || state.is(BlockTags.PLANKS)
                    || state.is(BlockTags.WOOL)
                    || state.is(BlockTags.WOOL_CARPETS)) {
                level.setBlockAndUpdate(above, Blocks.FIRE.defaultBlockState());
            }
        }
    }

    private void stopChanneling(ServerPlayer player) {
        var comp = zcylas.totality.api.ability.AbilityComponents.ABILITIES.get(
                (zcylas.totality.api.core.component.ComponentProvider) player
        );
        comp.stopChanneling();
        // Sync clears channelingAbility on client via ClientAbilityManager.sync()
        zcylas.totality.api.ability.AbilityComponents.ABILITIES.sync(
                (zcylas.totality.api.core.component.ComponentProvider) player
        );
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext context) {
        // Channeled ability — activation handled by onChannelStart/onChannel/onChannelStop
    }
}