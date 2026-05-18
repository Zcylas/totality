package zcylas.totality.blockentity.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.init.ModBlockEntities;

public class RitualDaisBlockEntity extends BlockEntity {

    private ItemStack heldItem = ItemStack.EMPTY;
    private boolean ritualActive = false;
    private int ritualActiveTick = 0;
    public int tickCount = 0;
    public float rotation = 0f;

    public RitualDaisBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RITUAL_DAIS, pos, state);
    }

    // ── Item holding ─────────────────────────────────────────────────────────

    public ItemStack getHeldItem() { return heldItem; }
    public boolean isRitualActive() { return ritualActive; }
    public int getRitualActiveTick() { return ritualActiveTick; }


    public void setHeldItem(ItemStack stack) {
        this.heldItem = stack.copy();
        setChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isEmpty() { return heldItem.isEmpty(); }

    // ── Serialization ────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!heldItem.isEmpty()) {
            output.store("HeldItem", ItemStack.CODEC, heldItem);
        }
        output.putBoolean("RitualActive", ritualActive);
        output.putInt("RitualActiveTick", ritualActiveTick);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        heldItem = input.read("HeldItem", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        ritualActive = input.getBooleanOr("RitualActive", false);
        ritualActiveTick = input.getIntOr("RitualActiveTick", 0);
    }

    // ── Client sync ──────────────────────────────────────────────────────────

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (var reporter = new ProblemReporter.ScopedCollector(problemPath(), Totality.LOGGER)) {
            var output = TagValueOutput.createWithContext(reporter, registries);
            if (!heldItem.isEmpty()) {
                output.store("HeldItem", ItemStack.CODEC, heldItem);
            }
            output.putBoolean("RitualActive", ritualActive);
            output.putInt("RitualActiveTick", ritualActiveTick);
            return output.buildResult();
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    public void setRitualActive(boolean active) {
        this.ritualActive = active;
        setChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
    public static void tick(Level level, BlockPos pos, BlockState state, RitualDaisBlockEntity be) {
        be.tickCount++; // both sides, no sync needed
        if (level.isClientSide()) {
            if (be.ritualActive) {
                be.ritualActiveTick++;
            } else if (be.ritualActiveTick > 0) {
                be.ritualActiveTick--;
            }
            float t = Math.min(be.ritualActiveTick / 40f, 1.0f);
            float t2 = Math.max(0f, (be.ritualActiveTick - 40) / 40f);
            float spinSpeed = 3.0f + (30.0f - 3.0f) * t + (60.0f - 30.0f) * t2;
            be.rotation = (be.rotation + spinSpeed) % 360f;
            be.tickCount++;
            return;
        }

        // Server side
        if (be.ritualActive) {
            be.ritualActiveTick++;
            if (be.ritualActiveTick % 5 == 0) {
                be.syncToClients((ServerLevel) level);
            }
        } else if (be.ritualActiveTick > 0) {
            be.ritualActiveTick--;
            if (be.ritualActiveTick % 5 == 0) {
                be.syncToClients((ServerLevel) level);
            }
        }
    }

    private void syncToClients(ServerLevel level) {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
}