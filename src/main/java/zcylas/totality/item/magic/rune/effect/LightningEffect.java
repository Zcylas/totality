package zcylas.totality.item.magic.rune.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.context.FormulaContext;
import zcylas.totality.api.magic.context.FormulaResolver;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractEffectRune;
import zcylas.totality.client.gui.TotalityGuiSprites;
import zcylas.totality.init.ModEffects;

import java.util.Map;
import java.util.Set;

public class LightningEffect extends AbstractEffectRune {

    public static final LightningEffect INSTANCE = new LightningEffect();

    private static final float BASE_DAMAGE = 5.0f;
    private static final float AMP_DAMAGE  = 3.0f;
    private static final float WET_BONUS   = 2.0f;

    private LightningEffect() { super("lightning", "Lightning"); }

    @Override public int getManaCost() { return 100; }
    @Override public int getTier()     { return 2; }
    @Override public Identifier getIcon() { return TotalityGuiSprites.RUNE_LIGHTNING; }

    @Override
    public String getDescription() {
        return "Strikes lightning at the target. Deals bonus damage to wet entities.";
    }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("amplify", "dampen", "extend_time", "reduce_time");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("amplify",     "Increases lightning damage.");
        map.put("dampen",      "Decreases lightning damage.");
        map.put("extend_time", "Increases the duration of the lightning bolt.");
        map.put("reduce_time", "Decreases the duration of the lightning bolt.");
    }

    @Override
    public void onResolveBlock(BlockHitResult hit, Level level,
                               LivingEntity caster, FormulaStats stats,
                               FormulaContext context, FormulaResolver resolver) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        spawnLightning(serverLevel, hit.getLocation(), caster, stats);
    }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level,
                                LivingEntity caster, FormulaStats stats,
                                FormulaContext context, FormulaResolver resolver) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        spawnLightning(serverLevel, hit.getEntity().position(), caster, stats);
    }

    private void spawnLightning(ServerLevel level, Vec3 pos,
                                LivingEntity caster, FormulaStats stats) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level,
                net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (bolt == null) return;

        bolt.snapTo(pos.x, pos.y, pos.z);
        bolt.setVisualOnly(false);

        if (caster instanceof ServerPlayer serverPlayer) {
            bolt.setCause(serverPlayer);
        }

        level.addFreshEntity(bolt);

        level.getEntities(bolt, bolt.getBoundingBox().inflate(3.0),
                        e -> e instanceof LivingEntity && e.isAlive())
                .forEach(e -> {
                    if (!(e instanceof LivingEntity living)) return;

                    float bonus = AMP_DAMAGE * stats.getAmpCount()
                            + (living.isInWaterOrRain() ? WET_BONUS : 0);

                    // UE energy item multiplier — like AN's RF check
                    int energyMultiplier = 0;
                    for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                            net.minecraft.world.entity.EquipmentSlot.HEAD,
                            net.minecraft.world.entity.EquipmentSlot.CHEST,
                            net.minecraft.world.entity.EquipmentSlot.LEGS,
                            net.minecraft.world.entity.EquipmentSlot.FEET}) {
                        net.minecraft.world.item.ItemStack armorStack = living.getItemBySlot(slot);
                        if (zcylas.totality.api.energy.UEStorageUtil.isEnergyItem(armorStack))
                            energyMultiplier++;
                    }
                    if (zcylas.totality.api.energy.UEStorageUtil.isEnergyItem(living.getMainHandItem()))
                        energyMultiplier++;
                    if (zcylas.totality.api.energy.UEStorageUtil.isEnergyItem(living.getOffhandItem()))
                        energyMultiplier++;

                    if (energyMultiplier > 0) bonus *= energyMultiplier;

                    // Shocked bonus damage
                    if (living.hasEffect(ModEffects.SHOCKED)) {
                        int amp = living.getEffect(ModEffects.SHOCKED).getAmplifier();
                        bonus += 3.0f * (amp + 1);
                    }

                    if (bonus > 0) {
                        living.hurtServer(level,
                                level.damageSources().lightningBolt(), bonus);
                    }

                    // Apply/upgrade Shocked
                    int currentAmp = living.hasEffect(ModEffects.SHOCKED)
                            ? living.getEffect(ModEffects.SHOCKED).getAmplifier() : -1;
                    int newAmp     = Math.min(2, currentAmp + 1);
                    int duration   = 200 + (int)(stats.getDurationModifier() * 200);
                    living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            ModEffects.SHOCKED, duration, newAmp, false, true, true));
                });
    }
}