package zcylas.totality.api.mining;

import net.minecraft.world.level.block.Blocks;
import zcylas.totality.init.ModTags;

/**
 * First Block Durability test set (V2 balance pass, §4; Dirt/Grass Block added in the
 * playtest-correction pass, §4, for the Shovel vertical slice) — everything else still runs on the
 * hardness-derived fallback in {@link BlockDurability#resolve}. Required Mining Tier is left to
 * that same fallback in every entry here (the {@code null} tier), so it is untouched: Dirt/Grass
 * Block don't require the correct tool for drops in vanilla, so {@link MiningTier#fallbackRequiredTier}
 * already resolves them to Tier 0 (any source, including bare hands, can work them) — the existing
 * legacy value was already correct, nothing to override. Sand/Gravel/Clay/Mud/Snow/Soul
 * Sand/Soul Soil/Path/Farmland are deliberately NOT included here — a future pass balances those
 * deliberately.
 */
public final class BlockDurabilityDefinitions {

    /** Material family of ordinary overworld Wood: logs/wood/stripped via the tag here, building forms in {@link VanillaBlockProfiles}. */
    public static final BlockProfile.Material OVERWORLD_LOG = new BlockProfile.Material("totality:overworld_wood");

    private BlockDurabilityDefinitions() {}

    public static void register() {
        BlockDurability.Definition oneHundred = new BlockDurability.Definition(100f, null);
        BlockDurability.register(Blocks.STONE, oneHundred);
        BlockDurability.register(Blocks.COBBLESTONE, oneHundred);
        BlockDurability.register(Blocks.DIORITE, oneHundred);
        BlockDurability.register(Blocks.ANDESITE, oneHundred);
        // Dirt + Grass Block = 100 Durability (Shovel vertical-slice pass, §4) — the Shovel's own
        // matching-tool test target, exactly as Stone/Cobblestone/Diorite/Andesite are the Pickaxe's.
        BlockDurability.register(Blocks.DIRT, oneHundred);
        BlockDurability.register(Blocks.GRASS_BLOCK, oneHundred);
        // Ordinary overworld logs/wood + stripped equivalents (no Crimson/Warped stems, no bamboo) — the
        // first Axe test target. MC 26.2 removed this as a BlockTags constant (see ModTags), so it's
        // referenced by id via the existing ModTags.VANILLA_LOGS_THAT_BURN. Block Breaking V2 Pass 1: the
        // tag is now a material family (full-block form) whose family default carries the same 100.
        BlockProfiles.assign(ModTags.VANILLA_LOGS_THAT_BURN, OVERWORLD_LOG, BlockProfile.Form.FULL_BLOCK);
        BlockProfiles.material(OVERWORLD_LOG, BlockProfile.durability(100f));
        // Block Breaking V2 Pass 3: the complete accepted vanilla dataset (ledger 2026-09-25).
        VanillaBlockProfiles.register();
        // Block Breaking V2 Pass 4A: the accepted in-place transformation pairs (used only inside BlockDamageStorage.transaction).
        VanillaTransformations.register();
    }
}
