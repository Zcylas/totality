package zcylas.totality.item.magic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import zcylas.totality.api.mana.PlayerManaManager;
import zcylas.totality.item.magic.rune.effect.BreakEffect;
import zcylas.totality.item.magic.rune.effect.augment.AmplifyAugment;
import zcylas.totality.item.magic.rune.form.ProjectileForm;
import zcylas.totality.item.magic.rune.form.TouchForm;

public class GrimoireItem extends Item {

    public GrimoireItem(Properties properties) {
        super(properties.stacksTo(1)
                .component(MagicComponents.GRIMOIRE_CASTER, GrimoireCaster.EMPTY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {


        ItemStack stack = player.getItemInHand(hand);
        GrimoireCaster caster = stack.get(MagicComponents.GRIMOIRE_CASTER);

        // TEMP: hardcode Touch -> Break for testing
        if (caster.formula().isEmpty()) {
            ArcaneFormula testFormula = ArcaneFormula.EMPTY
                    .add(ProjectileForm.INSTANCE)
                    .add(BreakEffect.INSTANCE)
                    .add(AmplifyAugment.INSTANCE);
            caster = caster.withFormula(testFormula);
            stack.set(MagicComponents.GRIMOIRE_CASTER, caster);
        }

        if (caster == null || !caster.formula().isValid()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ArcaneFormula formula = caster.formula();
        AbstractFormRune form = formula.getForm();
        if (form == null) return InteractionResult.PASS;

        // Check mana
        int cost = formula.getCost();
        if (!PlayerManaManager.hasMana(player, cost)) {
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("Not enough mana!")
                            .withStyle(net.minecraft.ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        FormulaContext context = new FormulaContext(level, formula, player, stack);
        FormulaResolver resolver = new FormulaResolver(context);

        // Raytrace to find what the player is looking at
        HitResult hit = player.pick(
                player.getAttributeValue(
                        net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE),
                0f, false);

        FormulaStats stats = new FormulaStats.Builder()
                .setAugments(formula.getAugments(0), form)
                .build();

        AbstractFormRune.CastResult result;

        if (hit.getType() == HitResult.Type.BLOCK) {
            result = form.onCastOnBlock((BlockHitResult) hit, player,
                    stats, context, resolver);
        } else if (hit.getType() == HitResult.Type.ENTITY) {
            result = form.onCastOnEntity(stack, player,
                    ((EntityHitResult) hit).getEntity(),
                    hand, stats, context, resolver);
        } else {
            result = form.onCast(stack, player, level, stats, context, resolver);
        }

        if (result == AbstractFormRune.CastResult.SUCCESS) {
            PlayerManaManager.removeMana(player, cost);
            // Sync mana to client
            if (level instanceof ServerLevel) {
                zcylas.totality.networking.mana.ManaServerTick.syncMana(
                        (net.minecraft.server.level.ServerPlayer) player);
            }
        }

        return InteractionResult.SUCCESS;
    }
}