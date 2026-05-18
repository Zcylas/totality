package zcylas.totality.api.rpg.skills.mining;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import zcylas.totality.api.rpg.skills.core.Skill;
import zcylas.totality.api.rpg.skills.core.SkillsComponents;

/**
 * Hooks into block-break events to award Mining XP.
 *
 * Rules:
 *   - Player must be in survival mode (no creative/spectator XP farming)
 *   - Player must be using a tool (not bare hands) — pickaxe, shovel, etc.
 *   - XP amount comes from MiningXpTable — 0 means no XP for that block
 *   - XP is awarded AFTER the block is actually broken (not cancelled)
 *
 * Call MiningSkillEvents.register() from your server-side initializer.
 */
public final class MiningSkillEvents {

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            // Server-side only
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            // Survival only — no creative/spectator farming
            if (!serverPlayer.gameMode.isSurvival()) return;

            // Must be holding a tool (not bare hands)
            // This prevents XP from punching dirt or sand
            ItemStack heldItem = serverPlayer.getMainHandItem();
            if (heldItem.isEmpty()) return;

            // Look up XP for this block
            int xp = MiningXpTable.getXp(state);
            if (xp <= 0) return;

            // Award XP — addSkillXp handles level-up logic and sync
            SkillsComponents.get(serverPlayer).addSkillXp(Skill.MINING, xp);
        });
    }

    private MiningSkillEvents() {}
}