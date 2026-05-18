package zcylas.totality.blockentity.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.ritual.RitualRecipe;
import zcylas.totality.api.ritual.RitualState;
import zcylas.totality.block.ritual.RitualAltarBlock;
import zcylas.totality.init.ModBlockEntities;

import java.util.ArrayList;
import java.util.List;

public class RitualAltarBlockEntity extends BlockEntity {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int ACTIVATING_TICKS   = 60;  // 3 seconds
    private static final int TICKS_PER_CONSUME  = 20;  // 1 second per dais
    private static final int ALTAR_TRANSFORM_TICKS = 60; // 3 seconds
    private static final int CANCELLING_TICKS   = 40;  // 2 seconds

    // ── State ─────────────────────────────────────────────────────────────────
    private ItemStack heldItem = ItemStack.EMPTY;
    private RitualState ritualState = RitualState.IDLE;
    private int animTick = 0;
    private int consumedCount = 0;
    public int tickCount = 0;
    public float rotation = 0f;
    private RitualState lastKnownState = RitualState.IDLE;
    private RitualState cancelledFromState = RitualState.IDLE;
    private int cancelledAtAnimTick = 0;
    // Server-only — not synced, rebuilt on activation
    @Nullable private transient RitualRecipe activeRecipe = null;
    private final List<BlockPos> activeDaisPositions = new ArrayList<>();

    public RitualAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RITUAL_ALTAR, pos, state);
    }
    public RitualState getCancelledFromState() { return cancelledFromState; }
    public int getCancelledAtAnimTick() { return cancelledAtAnimTick; }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public static void tick(Level level, BlockPos pos, BlockState state,
                            RitualAltarBlockEntity be) {
        be.tickCount++;

        if (level.isClientSide()) {
            if (be.ritualState != be.lastKnownState) {
                be.animTick = 0;
                be.lastKnownState = be.ritualState;
            }
            if (be.ritualState == RitualState.ALTAR_TRANSFORM ||
                    be.ritualState == RitualState.COMPLETING ||
                    be.ritualState == RitualState.CANCELLING) {
                be.animTick++;
            }

            float t, t2, spinSpeed;
            switch (be.ritualState) {
                case ALTAR_TRANSFORM -> {
                    t = Math.min(be.animTick / 40f, 1.0f);
                    t2 = Math.max(0f, (be.animTick - 40) / 40f);
                    spinSpeed = 3.0f + (30.0f - 3.0f) * t + (60.0f - 30.0f) * t2;
                }
                case COMPLETING -> {
                    t = Math.max(0f, 1.0f - be.animTick / 40f);
                    spinSpeed = 3.0f + (57.0f) * t;
                }
                case CANCELLING -> {
                    if (be.cancelledFromState == RitualState.ALTAR_TRANSFORM) {
                        float fromT = Math.min(be.cancelledAtAnimTick / 40f, 1.0f);
                        float fromT2 = Math.max(0f, (be.cancelledAtAnimTick - 40) / 40f);
                        float fromSpeed = 3.0f + (30.0f - 3.0f) * fromT + (60.0f - 30.0f) * fromT2;
                        float cancelT = Math.max(0f, 1.0f - be.animTick / 40f);
                        spinSpeed = 3.0f + (fromSpeed - 3.0f) * cancelT;
                    } else {
                        spinSpeed = 3.0f;
                    }
                }
                default -> spinSpeed = 3.0f;
            }
            be.rotation = (be.rotation + spinSpeed) % 360f;
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) return;
        if (be.ritualState == RitualState.IDLE) return;

        be.animTick++;

        switch (be.ritualState) {
            case ACTIVATING -> {
                if (be.animTick >= ACTIVATING_TICKS) {
                    be.animTick = 0;
                    be.ritualState = RitualState.CONSUMING;
                    be.syncToClients(serverLevel);
                }
            }
            case CONSUMING -> {
                if (be.animTick >= TICKS_PER_CONSUME) {
                    be.animTick = 0;
                    if (be.activeRecipe != null && be.consumedCount < be.activeRecipe.dais().size()) {
                        RitualRecipe.DaisEntry entry = be.activeRecipe.dais().get(be.consumedCount);
                        BlockPos daisPos = entry.resolve(pos);
                        var daisBe = level.getBlockEntity(daisPos);
                        if (daisBe instanceof RitualDaisBlockEntity dais) {
                            dais.setHeldItem(ItemStack.EMPTY);
                            serverLevel.playSound(null, daisPos,
                                    SoundEvents.ITEM_PICKUP,
                                    SoundSource.PLAYERS, 0.3f,
                                    0.8f + level.getRandom().nextFloat() * 0.4f);
                            dais.setRitualActive(false);
                            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                                    daisPos.getX() + 0.5, daisPos.getY() + 1.5, daisPos.getZ() + 0.5,
                                    20, 0.3, 0.3, 0.3, 0.1);
                            serverLevel.playSound(null, daisPos,
                                    SoundEvents.AMETHYST_BLOCK_CHIME,
                                    SoundSource.BLOCKS, 0.8f,
                                    1.0f + level.getRandom().nextFloat() * 0.4f);
                        }
                        be.consumedCount++;
                        be.syncToClients(serverLevel);
                    } else {
                        be.ritualState = RitualState.ALTAR_TRANSFORM;
                        be.animTick = 0;
                        be.syncToClients(serverLevel);
                    }
                }
            }
            case ALTAR_TRANSFORM -> {
                if (be.animTick >= ALTAR_TRANSFORM_TICKS) {
                    if (be.activeRecipe != null) {
                        be.heldItem = be.activeRecipe.result().copy();
                    }
                    serverLevel.sendParticles(ParticleTypes.ENCHANT,
                            pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5,
                            40, 0.5, 0.5, 0.5, 0.1);
                    serverLevel.playSound(null, pos,
                            SoundEvents.ENCHANTMENT_TABLE_USE,
                            SoundSource.BLOCKS, 1.0f, 1.0f);
                    be.ritualState = RitualState.COMPLETING;
                    be.animTick = 0;
                    be.syncToClients(serverLevel);
                }
            }
            case COMPLETING -> {
                if (be.animTick >= ALTAR_TRANSFORM_TICKS) {
                    be.finishRitual(serverLevel, pos, state);
                }
            }
            case CANCELLING -> {
                if (be.animTick >= CANCELLING_TICKS) {
                    for (BlockPos daisPos : be.activeDaisPositions) {
                        var daisBe = level.getBlockEntity(daisPos);
                        if (daisBe instanceof RitualDaisBlockEntity dais) {
                            dais.setRitualActive(false);
                        }
                    }
                    be.resetState(serverLevel, pos, state);
                }
            }
            default -> {}
        }
    }

    // ── Activation ────────────────────────────────────────────────────────────

    public void tryActivate(Level level, BlockPos pos, BlockState state, Player player) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (ritualState != RitualState.IDLE) {
            // Cancel if already running
            cancel(serverLevel, pos, state, player);
            return;
        }

        RitualRecipe recipe = zcylas.totality.api.ritual.RitualMatcher.match(level, pos, this);
        if (recipe == null) {
            level.playSound(null, pos,
                    SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.BLOCKS, 1.0f, 0.5f);
            return;
        }

        // Start ritual
        activeRecipe = recipe;
        consumedCount = 0;
        animTick = 0;
        ritualState = RitualState.ACTIVATING;

        // Mark all dais as active
        activeDaisPositions.clear();
        for (RitualRecipe.DaisEntry entry : recipe.dais()) {
            BlockPos daisPos = entry.resolve(pos);
            activeDaisPositions.add(daisPos);
            var daisBe = level.getBlockEntity(daisPos);
            if (daisBe instanceof RitualDaisBlockEntity dais) {
                dais.setRitualActive(true);
            }
        }

        // Consume chalk
        for (RitualRecipe.ChalkEntry entry : recipe.pattern()) {
            BlockPos chalkPos = entry.resolve(pos);
            if (level.getBlockState(chalkPos).is(zcylas.totality.init.blocks.RitualBlocks.CHALK)) {
                level.removeBlock(chalkPos, false);
            }
        }

        serverLevel.setBlock(pos, state.setValue(RitualAltarBlock.LIT, true), 3);
        syncToClients(serverLevel);

    }

    public void cancel(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        if (ritualState == RitualState.IDLE) return;
        cancelledFromState = ritualState;
        cancelledAtAnimTick = animTick;
        ritualState = RitualState.CANCELLING;
        animTick = 0;
        syncToClients(level);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void finishRitual(ServerLevel level, BlockPos pos, BlockState state) {
        activeRecipe = null;
        activeDaisPositions.clear();
        ritualState = RitualState.IDLE;
        animTick = 0;
        consumedCount = 0;
        level.setBlock(pos, state.setValue(RitualAltarBlock.LIT, false), 3);
        syncToClients(level);
    }

    private void resetState(ServerLevel level, BlockPos pos, BlockState state) {
        activeRecipe = null;
        activeDaisPositions.clear();
        ritualState = RitualState.IDLE;
        animTick = 0;
        consumedCount = 0;
        // heldItem stays — item remains on altar after cancellation
        level.setBlock(pos, state.setValue(RitualAltarBlock.LIT, false), 3);
        syncToClients(level);
    }

    private void syncToClients(ServerLevel level) {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ── Item holding ──────────────────────────────────────────────────────────

    public ItemStack getHeldItem() { return heldItem; }
    public RitualState getRitualState() { return ritualState; }
    public int getAnimTick() { return animTick; }
    public int getConsumedCount() { return consumedCount; }

    public void setHeldItem(ItemStack stack) {
        this.heldItem = stack.copy();
        setChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            syncToClients(serverLevel);
        }
    }

    public boolean isEmpty() { return heldItem.isEmpty(); }

    // ── Serialization ─────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!heldItem.isEmpty()) {
            output.store("HeldItem", ItemStack.CODEC, heldItem);
        }
        output.putString("RitualState", RitualState.IDLE.name());
        output.putInt("AnimTick", 0);
        output.putInt("ConsumedCount", 0);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        heldItem = input.read("HeldItem", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        try {
            ritualState = RitualState.valueOf(input.getStringOr("RitualState", "IDLE"));
        } catch (Exception e) {
            ritualState = RitualState.IDLE;
        }
        try {
            cancelledFromState = RitualState.valueOf(input.getStringOr("CancelledFromState", "IDLE"));
        } catch (Exception e) {
            cancelledFromState = RitualState.IDLE;
        }
        cancelledAtAnimTick = input.getIntOr("CancelledAtAnimTick", 0);
        animTick = input.getIntOr("AnimTick", 0);
        consumedCount = input.getIntOr("ConsumedCount", 0);
    }

    // ── Client sync ───────────────────────────────────────────────────────────

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (var reporter = new ProblemReporter.ScopedCollector(problemPath(), Totality.LOGGER)) {
            var output = TagValueOutput.createWithContext(reporter, registries);
            if (!heldItem.isEmpty()) {
                output.store("HeldItem", ItemStack.CODEC, heldItem);
            }
            output.putString("RitualState", ritualState.name());
            output.putInt("AnimTick", animTick);
            output.putInt("ConsumedCount", consumedCount);
            output.putString("CancelledFromState", cancelledFromState.name());
            output.putInt("CancelledAtAnimTick", cancelledAtAnimTick);
            return output.buildResult();
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}