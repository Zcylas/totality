package zcylas.totality.api.ability.impl;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.rpg.skills.core.Skill;
import zcylas.totality.api.rpg.skills.core.SkillsComponents;
import zcylas.totality.api.rpg.skills.mining.MiningXpTable;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;
import zcylas.totality.init.ModTags;
import zcylas.totality.networking.ability.veinminer.VeinminerKeyHandler;
import zcylas.totality.networking.notification.SendNotificationPayload;
import zcylas.totality.networking.stamina.StaminaServerTick;

import java.util.*;

public class VeinminerAbility extends Ability {

    private static final int MAX_BLOCKS   = 32;
    private static final int STAMINA_COST = 5;

    /** Per-player mining queue. */
    private static final Map<UUID, VeinminerState> STATES = new HashMap<>();

    public VeinminerAbility() {
        super(
                Identifier.fromNamespaceAndPath("totality", "veinminer"),
                "Veinminer",
                "Hold the ability key while breaking an ore to mine the entire connected vein. Costs 2 stamina per block.",
                Type.CHANNELED,
                0,
                Identifier.fromNamespaceAndPath("totality", "textures/ability/veinminer.png")
        );
    }

    @Override
    public boolean isDefault() { return false; }

    // -------------------------------------------------------------------------
    // Registration — hook into block break event
    // -------------------------------------------------------------------------

    public void registerEvents() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return true;
            if (level.isClientSide()) return true;

            // Must have Veinminer unlocked
            var comp = AbilityComponents.ABILITIES.get((ComponentProvider) serverPlayer);
            if (!comp.hasAbility(getId())) return true;

            // Must be holding the ability key
            if (!VeinminerKeyHandler.isHolding(serverPlayer)) return true;

            // Must be holding a pickaxe
            ItemStack tool = serverPlayer.getMainHandItem();
            if (!isSuitableTool(tool, state)) {
                SendNotificationPayload.send(serverPlayer,
                        "You need a pickaxe to use Veinminer.",
                        SendNotificationPayload.RED);
                return true;
            }

            // Must be an ore
            if (!isOre(state)) return true;

            // Must have stamina
            if (!PlayerStaminaManager.hasStamina(serverPlayer, STAMINA_COST)) {
                SendNotificationPayload.send(serverPlayer,
                        "Not enough stamina for Veinminer.",
                        SendNotificationPayload.RED);
                return true;
            }

            ServerLevel serverLevel = (ServerLevel) level;

            // Handle first block ourselves — drops go to inventory
            List<ItemStack> firstDrops = Block.getDrops(state, serverLevel, pos, blockEntity, serverPlayer, tool);
            level.removeBlock(pos, false);
            awardMiningXp(serverPlayer, state);
            serverLevel.playSound(null, pos,
                    SoundEvents.STONE_BREAK, SoundSource.BLOCKS,
                    0.8f, 0.9f + serverLevel.getRandom().nextFloat() * 0.2f);
            if (!tool.isEmpty() && tool.isDamageableItem()) {
                tool.hurtAndBreak(1, serverPlayer, EquipmentSlot.MAINHAND);
            }
            PlayerStaminaManager.removeStamina(serverPlayer, STAMINA_COST);
            StaminaServerTick.syncStamina(serverPlayer);
            for (ItemStack drop : firstDrops) {
                if (drop.isEmpty()) continue;
                if (!serverPlayer.getInventory().add(drop)) {
                    serverPlayer.drop(drop, false);
                }
            }

            // Build vein queue from neighbors (first block already broken)
            Queue<BlockPos> queue = buildVeinQueue(serverLevel, pos, state, MAX_BLOCKS - 1);
            if (!queue.isEmpty()) {
                STATES.put(serverPlayer.getUUID(),
                        new VeinminerState(queue, state.getBlock(), tool.copy()));
            }

            // Cancel vanilla break — we handled it
            return false;
        });
    }

    // -------------------------------------------------------------------------
    // Channeled tick — one block per tick
    // -------------------------------------------------------------------------

    @Override
    public void onChannel(ServerPlayer player, @Nullable AbilityContext context) {
        VeinminerState state = STATES.get(player.getUUID());
        if (state == null) return;

        // Stop if out of stamina
        if (!PlayerStaminaManager.hasStamina(player, STAMINA_COST)) {
            STATES.remove(player.getUUID());
            SendNotificationPayload.send(player,
                    "Out of stamina — Veinminer stopped.",
                    SendNotificationPayload.RED);
            return;
        }

        BlockPos next = state.queue().poll();
        if (next == null) {
            STATES.remove(player.getUUID());
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        BlockState blockState = level.getBlockState(next);

        if (!blockState.getBlock().equals(state.block())) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(next);
        List<ItemStack> drops = Block.getDrops(
                blockState, level, next, blockEntity, player, state.tool());

        level.removeBlock(next, false);
        awardMiningXp(player, blockState);

        level.playSound(null, next,
                SoundEvents.STONE_BREAK,
                SoundSource.BLOCKS,
                0.8f, 0.9f + level.getRandom().nextFloat() * 0.2f);

// Damage the actual held tool (not the copy in state)
        ItemStack heldTool = player.getMainHandItem();
        if (!heldTool.isEmpty() && heldTool.isDamageableItem()) {
            heldTool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }

        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            if (!player.getInventory().add(drop)) {
                player.drop(drop, false);
            }
        }

        PlayerStaminaManager.removeStamina(player, STAMINA_COST);
        StaminaServerTick.syncStamina(player);
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext context) {
        // Veinminer is triggered by block break, not direct activation
    }

    // -------------------------------------------------------------------------
    // BFS vein scanner
    // -------------------------------------------------------------------------

    private Queue<BlockPos> buildVeinQueue(ServerLevel level, BlockPos origin,
                                           BlockState originState, int maxBlocks) {
        Queue<BlockPos> result = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> toVisit = new LinkedList<>();

        toVisit.add(origin);
        visited.add(origin);

        while (!toVisit.isEmpty() && result.size() < maxBlocks) {
            BlockPos current = toVisit.poll();
            result.add(current);

            // Check all 6 neighbors
            for (BlockPos neighbor : neighbors(current)) {
                if (visited.contains(neighbor)) continue;
                visited.add(neighbor);

                BlockState neighborState = level.getBlockState(neighbor);
                // Same block type only
                if (neighborState.getBlock().equals(originState.getBlock())) {
                    toVisit.add(neighbor);
                }
            }
        }

        return result;
    }

    private List<BlockPos> neighbors(BlockPos pos) {
        return List.of(
                pos.above(), pos.below(),
                pos.north(), pos.south(),
                pos.east(),  pos.west()
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isSuitableTool(ItemStack tool, BlockState state) {
        if (tool.isEmpty()) return false;
        return tool.is(net.minecraft.tags.ItemTags.PICKAXES);
    }

    private boolean isOre(BlockState state) {
        return state.is(BlockTags.COAL_ORES)
                || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES)
                || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.REDSTONE_ORES)
                || state.is(Blocks.NETHER_QUARTZ_ORE)
                || state.is(Blocks.ANCIENT_DEBRIS)
                || state.is(ModTags.VEINMINABLE);
    }

    public static void tickPlayer(ServerPlayer player) {
        if (!STATES.containsKey(player.getUUID())) return;
        AbilityRegistry.VEINMINER.onChannel(player, null);
    }

    private void awardMiningXp(ServerPlayer player, BlockState state) {
        if (!player.gameMode.isSurvival()) return;
        int xp = MiningXpTable.getXp(state);
        if (xp <= 0) return;
        SkillsComponents.get(player).addSkillXp(Skill.MINING, xp);
    }

    // -------------------------------------------------------------------------
    // State cleanup
    // -------------------------------------------------------------------------

    public static void onPlayerLeave(ServerPlayer player) {
        STATES.remove(player.getUUID());
    }

    // -------------------------------------------------------------------------
    // State record
    // -------------------------------------------------------------------------

    private record VeinminerState(
            Queue<BlockPos> queue,
            Block block,
            ItemStack tool
    ) {}

    // -------------------------------------------------------------------------
    // Client side — no context prompt for Veinminer
    // -------------------------------------------------------------------------

    @Override
    public @Nullable AbilityContext getContext(Minecraft mc, LocalPlayer player) {
        return AbilityContext.NONE;
    }
}