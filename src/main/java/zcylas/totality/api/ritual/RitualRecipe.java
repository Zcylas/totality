package zcylas.totality.api.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record RitualRecipe(
        Identifier id,
        List<ChalkEntry> pattern,
        List<DaisEntry> dais,
        ItemStack altarInput,
        ItemStack result
) {

    public record ChalkEntry(ChalkColor color, ChalkSigil glyph, int offsetX, int offsetZ) {
        public BlockPos resolve(BlockPos altarPos) {
            return altarPos.offset(offsetX, 0, offsetZ);
        }
    }

    public record DaisEntry(Identifier item, int offsetX, int offsetZ) {
        public BlockPos resolve(BlockPos altarPos) {
            return altarPos.offset(offsetX, 0, offsetZ);
        }
    }
}