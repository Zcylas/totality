package zcylas.totality.entity.npc;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.dialogue.DialogueSessionManager;

public class TotalityNpcEntity extends PathfinderMob {

    // Synced (not just persisted) so the client — which never runs finalizeSpawn — actually
    // sees the gender the server picked. NBT-only fields don't propagate to freshly spawned
    // entities on the client, only across save/load; this bit fixes the "name is female but
    // skin is always male" bug.
    private static final EntityDataAccessor<Integer> DATA_GENDER =
            SynchedEntityData.defineId(TotalityNpcEntity.class, EntityDataSerializers.INT);

    @Nullable private Identifier dialogueId = null;
    @Nullable private Identifier shopId = null;
    @Nullable private Player dialoguePartner = null;

    public TotalityNpcEntity(EntityType<? extends TotalityNpcEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GENDER, NpcGender.MALE.ordinal());
    }

    // Kept as fields (not local to registerGoals) so setDialoguePartner can remove/re-add them —
    // removing outright, rather than just cancelling navigation each tick, is the only way to
    // be sure the wander/look goals can never re-issue a path or fight our forced look target.
    // Assigned INSIDE registerGoals(), not via field initializer — registerGoals() is called
    // from the Mob superclass constructor, which runs before this class's own field
    // initializers do; an inline initializer here would still be null at that point.
    @Nullable private Goal wanderGoal;
    @Nullable private Goal lookAtPlayerGoal;

    @Override
    protected void registerGoals() {
        wanderGoal = new WaterAvoidingRandomStrollGoal(this, 1.0);
        lookAtPlayerGoal = new LookAtPlayerGoal(this, Player.class, 8.0f);
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, wanderGoal);
        this.goalSelector.addGoal(2, lookAtPlayerGoal);
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    /**
     * Whether this NPC should get a random gender + name on spawn. Unique named characters
     * (Gilmore, Pumat Sol, etc., built later as their own entity classes) override this to
     * false and set their own fixed name instead — random identity only applies to generic
     * "background" NPCs filling a profession/role, like Banker instances.
     */
    protected boolean usesRandomIdentity() { return true; }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
        if (usesRandomIdentity() && getCustomName() == null) {
            NpcGender randomGender = random.nextBoolean() ? NpcGender.MALE : NpcGender.FEMALE;
            setGender(randomGender);
            setCustomName(Component.literal(NpcNameRegistry.INSTANCE.getRandomName(random, randomGender)));
            setCustomNameVisible(true);
        }
        return data;
    }

    /** Called by DialogueSessionManager on start/end of a dialogue session with this NPC —
     *  freezes wandering and forces a look-at-partner for the duration. */
    public void setDialoguePartner(@Nullable Player player) {
        this.dialoguePartner = player;
        if (player != null) {
            getNavigation().stop();
            // Remove outright rather than just cancelling navigation each tick — cancelling
            // alone raced with these goals re-issuing a path/look target and lost, which is why
            // the NPC kept wandering/not facing the player despite the per-tick stop() call.
            if (wanderGoal != null) goalSelector.removeGoal(wanderGoal);
            if (lookAtPlayerGoal != null) goalSelector.removeGoal(lookAtPlayerGoal);
        } else {
            if (wanderGoal != null) goalSelector.addGoal(1, wanderGoal);
            if (lookAtPlayerGoal != null) goalSelector.addGoal(2, lookAtPlayerGoal);
        }
    }

    @Override
    protected void customServerAiStep(net.minecraft.server.level.ServerLevel level) {
        super.customServerAiStep(level);
        if (dialoguePartner == null) return;
        // Safety net: if the dialogue-end hook is ever missed (e.g. the player disconnects
        // mid-conversation), don't leave the NPC frozen/staring forever.
        if (!dialoguePartner.isAlive() || dialoguePartner.distanceToSqr(this) > 64.0) {
            setDialoguePartner(null);
            return;
        }
        getNavigation().stop();
        getLookControl().setLookAt(dialoguePartner.getX(), dialoguePartner.getEyeY(), dialoguePartner.getZ());
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && hand == InteractionHand.MAIN_HAND
                && player instanceof ServerPlayer sp) {
            if (shopId != null) {
                zcylas.totality.api.shop.TradeSessionManager.startTrade(sp, shopId, this);
                return InteractionResult.SUCCESS;
            }
            if (dialogueId != null) {
                DialogueSessionManager.startDialogue(sp, dialogueId, this);
                return InteractionResult.SUCCESS;
            }
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (dialogueId != null) output.putString("DialogueId", dialogueId.toString());
        if (shopId != null) output.putString("ShopId", shopId.toString());
        output.putString("Gender", getGender().name());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        String id = input.getStringOr("DialogueId", "");
        dialogueId = id.isEmpty() ? null : Identifier.tryParse(id);
        String shop = input.getStringOr("ShopId", "");
        shopId = shop.isEmpty() ? null : Identifier.tryParse(shop);
        String genderName = input.getStringOr("Gender", NpcGender.MALE.name());
        try {
            setGender(NpcGender.valueOf(genderName));
        } catch (IllegalArgumentException e) {
            setGender(NpcGender.MALE);
        }
    }

    @Nullable public Identifier getDialogueId() { return dialogueId; }
    public void setDialogueId(@Nullable Identifier id) { this.dialogueId = id; }

    @Nullable public Identifier getShopId() { return shopId; }
    public void setShopId(@Nullable Identifier id) { this.shopId = id; }

    public NpcGender getGender() { return NpcGender.values()[entityData.get(DATA_GENDER)]; }
    public void setGender(NpcGender gender) { entityData.set(DATA_GENDER, gender.ordinal()); }
}
