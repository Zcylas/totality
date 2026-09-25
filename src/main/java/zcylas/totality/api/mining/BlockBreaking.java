package zcylas.totality.api.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.mining.BlockDamageStorage.Entry;

/**
 * The single entry point through which any source damages a block. Source-agnostic: the actor is
 * optional, damage is an absolute quantity, and state is owned by the block position.
 *
 * <p>Terminal break: a {@link ServerPlayer} actor breaks through
 * {@code ServerPlayerGameMode.destroyBlock} (loot, Silk Touch/Fortune, tool damage, XP, stats and
 * Fabric {@code PlayerBlockBreakEvents}); anything else uses {@code Level.destroyBlock}. If that
 * path refuses, the refusal is final — there is no force fallback.
 */
public final class BlockBreaking {

    private BlockBreaking() {}

    public static MiningResult applyImpact(ServerLevel level, MiningImpact impact) {
        BlockPos pos = impact.pos();
        BlockState state = level.getBlockState(pos);
        BlockDamageStorage storage = BlockDamageStorage.get(level);
        if (state.isAir()) {
            purge(storage, pos, state);
            return MiningResult.of(MiningResult.Outcome.INVALID);
        }
        // Integrity is owned by the assembly owner (e.g. a door's lower half), whose profile is the assembly's.
        BlockPos owner = BlockProfiles.integrityOwner(level, pos, state);
        BlockState ownerState = owner.equals(pos) ? state : level.getBlockState(owner);
        BlockProfile.Resolved profile = BlockProfiles.resolve(level, owner, ownerState);
        // Only ORDINARY blocks hold finite Integrity; SPECIAL/UNBREAKABLE/NOT_APPLICABLE are never struck.
        if (!profile.ordinary()) return MiningResult.of(MiningResult.Outcome.INVALID);
        BlockDurability.Resolved durability = new BlockDurability.Resolved(profile.maxDurability(), profile.requiredTier());

        Entity actor = impact.source().actor();
        ServerPlayer player = actor instanceof ServerPlayer sp ? sp : null;

        if (player != null) {
            if (!MiningPermissions.mayStrike(level, player, pos, impact.face())) {
                return MiningResult.of(MiningResult.Outcome.DENIED);
            }
            // Vanilla runs these once at START; with START disabled they run per impact.
            EnchantmentHelper.onHitBlock(level, player.getMainHandItem(), player, player, EquipmentSlot.MAINHAND,
                    Vec3.atCenterOf(pos), state, item -> player.onEquippedItemBroken(item, EquipmentSlot.MAINHAND));
            state.attack(level, pos, player);
        }

        if (impact.tier() < durability.requiredTier()) {
            MiningFeedback.contact(level, state, impact.hitLocation());
            MiningFeedback.ineffectiveText(level, impact.hitLocation());
            return new MiningResult(MiningResult.Outcome.INEFFECTIVE, 0, currentOrMax(storage, owner, ownerState, durability, level), durability.max());
        }

        long now = level.getGameTime();
        Entry entry = storage.get(owner, ownerState);
        float before = entry != null ? entry.currentIntegrity(now) : durability.max();
        float applied = Math.min(impact.damage(), before);
        float after = before - impact.damage();

        MiningFeedback.contact(level, state, impact.hitLocation());
        MiningFeedback.damageText(level, impact.hitLocation(), impact.damage(), impact.presentationBand());

        if (after > 0f) {
            if (entry == null) entry = storage.create(owner, ownerState, durability.max(), now);
            storage.update(entry, after, now);
            return new MiningResult(MiningResult.Outcome.DAMAGED, applied, after, durability.max());
        }

        boolean handsQualified = impact.source().kind() == MiningSource.Kind.BARE_HANDS;
        boolean destroyed = destroy(level, player, actor, pos, state, handsQualified);
        BlockState afterState = level.getBlockState(pos);
        if (destroyed || afterState.getBlock() != state.getBlock()) {
            // Also covers a BEFORE-listener (e.g. Veinminer) that removed the block itself and returned false.
            if (entry != null) storage.remove(entry);
            return new MiningResult(MiningResult.Outcome.BROKEN, applied, 0, durability.max());
        }
        // Refused: keep the block, leave it one impact from breaking. No bypass.
        if (entry == null) entry = storage.create(owner, ownerState, durability.max(), now);
        storage.update(entry, Math.min(MiningTuning.DENIED_BREAK_INTEGRITY, durability.max()), now);
        return new MiningResult(MiningResult.Outcome.DENIED, applied, entry.integrity, durability.max());
    }

    /**
     * Terminal break of a tool source: vanilla charges {@code Tool.damagePerBlock} inside {@code destroyBlock}
     * (and nothing at all when it is 0). Totality's rule is exactly 1 durability per successful impact, terminal
     * one included, so a tool whose component says anything other than 1 (0, 2, ...) is normalised to 1 for
     * this synchronous call only and always restored.
     */
    private static boolean destroyCharging1(ServerPlayer player, BlockPos pos) {
        ItemStack held = player.getMainHandItem();
        Tool original = held.get(DataComponents.TOOL);
        boolean normalise = original != null && original.damagePerBlock() != MiningTuning.BASE_WEAR_PER_IMPACT;   // 0 would make the terminal hit free, 2+ too costly
        if (normalise) held.set(DataComponents.TOOL, new Tool(original.rules(), original.defaultMiningSpeed(),
                MiningTuning.BASE_WEAR_PER_IMPACT, original.canDestroyBlocksInCreative()));
        try {
            return player.gameMode.destroyBlock(pos);
        } finally {
            if (normalise && !held.isEmpty()) held.set(DataComponents.TOOL, original);   // empty = the tool broke; nothing to restore
        }
    }

    /**
     * @param handsQualified the impact came from a non-tool source whose Tier already passed this block's gate
     *                       (an ineffective impact never reaches the terminal break), so vanilla must treat that
     *                       source as harvest-eligible for exactly this break — see {@link HarvestGrant}.
     */
    private static boolean destroy(ServerLevel level, ServerPlayer player, Entity actor, BlockPos pos,
                                   BlockState state, boolean handsQualified) {
        if (player != null) {
            if (!handsQualified) return destroyCharging1(player, pos);
            try (HarvestGrant ignored = HarvestGrant.open(player, level, pos, state.getBlock())) {
                return player.gameMode.destroyBlock(pos);   // hands: no tool component to normalise
            }
        }
        return level.destroyBlock(pos, true, actor);
    }

    private static float currentOrMax(BlockDamageStorage storage, BlockPos pos, BlockState state,
                                      BlockDurability.Resolved durability, ServerLevel level) {
        Entry e = storage.get(pos, state);
        return e != null ? e.currentIntegrity(level.getGameTime()) : durability.max();
    }

    private static void purge(BlockDamageStorage storage, BlockPos pos, BlockState state) {
        Entry e = storage.get(pos, state);
        if (e != null) storage.remove(e);
    }
}
