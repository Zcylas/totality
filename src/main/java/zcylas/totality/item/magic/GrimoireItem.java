package zcylas.totality.item.magic;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import zcylas.totality.api.magic.GrimoireCaster;
import zcylas.totality.api.magic.MagicComponents;
import zcylas.totality.api.magic.context.FormulaContext;
import zcylas.totality.api.magic.context.FormulaResolver;
import zcylas.totality.api.magic.formula.ArcaneFormula;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractFormRune;
import zcylas.totality.api.magic.rune.AbstractRune;
import zcylas.totality.api.mana.PlayerManaManager;
import zcylas.totality.item.magic.rune.effect.BreakEffect;
import zcylas.totality.item.magic.rune.effect.augment.AmplifyAugment;
import zcylas.totality.item.magic.rune.form.ProjectileForm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GrimoireItem extends Item {

    private final int maxTier;

    public GrimoireItem(Properties properties, int maxTier) {
        super(properties.stacksTo(1)
                .component(MagicComponents.GRIMOIRE_CASTER, GrimoireCaster.EMPTY));
        this.maxTier = maxTier;
    }

    public int getMaxTier() {
        return maxTier;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Shift+right-click on interactive block — skip casting
        if (player.isSecondaryUseActive() && !level.isClientSide()) {
            net.minecraft.world.phys.HitResult check = player.pick(
                    player.getAttributeValue(
                            net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE),
                    0f, false);
            if (check.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                net.minecraft.core.BlockPos checkPos =
                        ((net.minecraft.world.phys.BlockHitResult) check).getBlockPos();
                if (level.getBlockState(checkPos).getMenuProvider(level, checkPos) != null) {
                    return InteractionResult.PASS;
                }
            }
        }

        GrimoireCaster caster = stack.getOrDefault(
                MagicComponents.GRIMOIRE_CASTER, GrimoireCaster.EMPTY);

        if (!caster.formula().isValid()) return InteractionResult.PASS;

        if (level.isClientSide()) return InteractionResult.SUCCESS;

        ArcaneFormula formula = caster.formula();
        AbstractFormRune form = formula.getForm();
        if (form == null) return InteractionResult.PASS;

        int cost = formula.getCost();
        if (!player.isCreative() && !PlayerManaManager.hasMana(player, cost)) {
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("Not enough mana!")
                            .withStyle(net.minecraft.ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        // ── Raycast — entities first, then blocks ──
        double range = player.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE);

        net.minecraft.world.phys.HitResult blockHit = player.pick(range, 0f, false);

        net.minecraft.world.phys.Vec3 eyePos  = player.getEyePosition();
        net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle();
        net.minecraft.world.phys.Vec3 reach   = eyePos.add(lookVec.scale(range));

        net.minecraft.world.phys.AABB searchBox = player.getBoundingBox()
                .expandTowards(lookVec.scale(range)).inflate(1.0);

        net.minecraft.world.entity.Entity hitEntity = null;
        double closestDist = range * range;

        for (net.minecraft.world.entity.Entity entity :
                level.getEntities(player, searchBox)) {
            if (!entity.isPickable()) continue;
            net.minecraft.world.phys.AABB entityBox = entity.getBoundingBox().inflate(0.3);
            java.util.Optional<net.minecraft.world.phys.Vec3> entityHit =
                    entityBox.clip(eyePos, reach);
            if (entityHit.isPresent()) {
                double dist = eyePos.distanceToSqr(entityHit.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    hitEntity   = entity;
                }
            }
        }

        HitResult hit;
        if (hitEntity != null && (blockHit.getType() == HitResult.Type.MISS
                || closestDist < eyePos.distanceToSqr(blockHit.getLocation()))) {
            hit = new net.minecraft.world.phys.EntityHitResult(hitEntity);
        } else {
            hit = blockHit;
        }

        FormulaContext context  = new FormulaContext(level, formula, player, stack);
        FormulaResolver resolver = new FormulaResolver(context);

        FormulaStats stats = new FormulaStats.Builder()
                .setAugments(formula.getAugments(0), form)
                .build();

        AbstractFormRune.CastResult result;
        if (hit.getType() == HitResult.Type.BLOCK) {
            result = form.onCastOnBlock((BlockHitResult) hit, player, stats, context, resolver);
        } else if (hit.getType() == HitResult.Type.ENTITY) {
            result = form.onCastOnEntity(stack, player,
                    ((net.minecraft.world.phys.EntityHitResult) hit).getEntity(),
                    hand, stats, context, resolver);
        } else {
            result = form.onCast(stack, player, level, stats, context, resolver);
        }

        if (result == AbstractFormRune.CastResult.SUCCESS && !player.isCreative()) {
            PlayerManaManager.removeMana(player, cost);
            if (level instanceof net.minecraft.server.level.ServerLevel) {
                zcylas.totality.networking.mana.ManaServerTick.syncMana(
                        (net.minecraft.server.level.ServerPlayer) player);
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        builder.accept(Component.literal("Tier " + maxTier + " Grimoire")
                .withStyle(style -> style.withColor(0x9966ff)));
        GrimoireCaster caster = stack.getOrDefault(MagicComponents.GRIMOIRE_CASTER, GrimoireCaster.EMPTY);
        if (!caster.spellName().isEmpty()) {
            builder.accept(Component.literal("Active: " + caster.spellName())
                    .withStyle(style -> style.withColor(0xaaaaff)));
        }
    }
}