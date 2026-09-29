package zcylas.totality.entity.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import zcylas.totality.init.items.VehicleItems;

/**
 * Creative Test D: the default skateboard (V1), a one-rider vehicle built the way vanilla builds boats.
 * <p><b>Authority.</b> While a player rides it, that player's client moves the board (it reads the rider's own movement
 * input, {@code zza}/{@code xxa}, as vanilla horses do) and sends vanilla's vehicle-move packets; the server replays each
 * move with its own collision and snaps the board back if the two disagree ({@code handleMoveVehicle}). Unridden, the
 * server moves it (it coasts to a stop and falls); every other client interpolates it. Nothing else needs syncing: the
 * wheels' spin and the deck's lean are derived on each client from the synced position and heading.
 * <p><b>Riding.</b> The rider stands on the deck (feet on the grip, see {@link #GRIP_TOP}) side-on, the body turned
 * {@link #STANCE_YAW} from the heading; the camera turns with the board and may look {@link #LOOK_LIMIT} either side.
 * Forward pushes, back brakes, left/right steer, sneak steps off. While ridden the collision box grows to the rider's
 * height (so the pair cannot pass under a ceiling the rider would put their head into) and the board cannot be
 * targeted (hits and arrows reach the rider). The deck is longer than the box: before moving or turning, the nose and
 * tail are checked against blocks so they do not run into walls.
 * <p><b>Getting it back.</b> Sneak-use picks it up (one item, unridden boards only); breaking it drops it (vanilla
 * vehicle damage; creative players remove it).
 * <p>The board's visual pose (heading, lean, later tricks) belongs to the renderer alone; the rider is positioned from
 * the logical deck, never from the drawn one.
 */
public class SkateboardEntity extends VehicleEntity {

    /** Height of the grip surface above the ground (model: 6.05 px), where the rider's feet stand. */
    public static final float GRIP_TOP = 6.05F / 16.0F;
    /** The rider's body faces this far to the right of the heading (regular stance: left foot forward). */
    public static final float STANCE_YAW = 70.0F;
    /** How far the rider may look to either side of the heading. */
    public static final float LOOK_LIMIT = 100.0F;
    /** The collision box while ridden: the board plus a standing player. */
    private static final EntityDimensions RIDDEN = EntityDimensions.fixed(0.9F, GRIP_TOP + 1.8F);
    /** Nose/tail probes: centred 1.05 out, 0.4 wide (so they reach 1.25, past the deck tip at 1.23), above step height. */
    private static final double END_REACH = 1.05;
    private static final double END_PROBE_BOTTOM = 0.55;
    private static final double END_PROBE_TOP = 0.8;
    private static final double END_PROBE_WIDTH = 0.4;
    /** Wheel radius (2 px) in blocks, for their spin. */
    private static final float WHEEL_RADIUS = 2.0F / 16.0F;

    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);
    /** Authoritative instance: this tick's turn (degrees). */
    private float turnRate;
    /** Client visuals, from the synced motion: wheel spin (radians) and deck lean (degrees). */
    private float wheelSpin;
    private float wheelSpinO;
    private float lean;
    private float leanO;

    public SkateboardEntity(EntityType<? extends SkateboardEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    // ── Placement ─────────────────────────────────────────────────────────────────────────────────────────────────

    public void setInitialPos(double x, double y, double z, float yRot) {
        this.setPos(x, y, z);
        this.setYRot(yRot);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.yRotO = yRot;
    }

    // ── Movement ──────────────────────────────────────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        if (this.getHurtTime() > 0) this.setHurtTime(this.getHurtTime() - 1);
        if (this.getDamage() > 0.0F) this.setDamage(this.getDamage() - 1.0F);
        super.tick();
        this.interpolation.interpolate();
        if (this.isLocalInstanceAuthoritative()) {
            this.ride();
        } else {
            this.setDeltaMovement(Vec3.ZERO);
            this.turnRate = 0.0F;
        }
        this.applyEffectsFromBlocks();
        if (this.level().isClientSide()) this.animate();
    }

    /** One tick of motion on the instance that owns it (the rider's client, or the server when unridden). */
    private void ride() {
        float forwardInput = 0.0F;
        float strafeInput = 0.0F;
        if (this.getControllingPassenger() instanceof Player rider) {
            forwardInput = rider.zza;
            strafeInput = rider.xxa;
        }
        boolean onGround = this.onGround();
        float slipperiness = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction();
        Vec3 motion = this.getDeltaMovement();
        Vec3 heading = heading(this.getYRot());
        double speed = motion.x * heading.x + motion.z * heading.z;
        double side = motion.x * heading.z - motion.z * heading.x;
        speed = SkateboardMotion.speed(speed, forwardInput, onGround, slipperiness) * this.getBlockSpeedFactor();
        side = SkateboardMotion.sideSpeed(side, onGround);
        this.turnRate = SkateboardMotion.turnRate(this.turnRate, strafeInput, speed, onGround);

        // Turn, unless that swings the nose or tail into a block.
        float yaw = this.getYRot() + this.turnRate;
        if (!this.endsClear(this.position(), yaw) && this.endsClear(this.position(), this.getYRot())) {
            yaw = this.getYRot();
            this.turnRate = 0.0F;
        }
        this.setYRot(yaw);
        heading = heading(yaw);

        double fall = (motion.y - this.getGravity()) * 0.98;
        if (this.isInWater()) {
            speed *= 0.6;
            side *= 0.6;
            fall *= 0.6;
        }
        Vec3 move = new Vec3(heading.x * speed + heading.z * side, fall, heading.z * speed - heading.x * side);
        // Stop before the nose or tail runs into a wall (the collision box is shorter than the deck).
        if (!this.endsClear(this.position().add(move.x, 0.0, move.z), yaw) && this.endsClear(this.position(), yaw)) {
            move = new Vec3(0.0, move.y, 0.0);
        }
        this.setDeltaMovement(move);
        this.move(MoverType.SELF, move);
    }

    private static Vec3 heading(float yaw) {
        float rad = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(rad), 0.0, Mth.cos(rad));
    }

    /** Are the nose and tail free of blocks at this position and heading? */
    private boolean endsClear(Vec3 position, float yaw) {
        Vec3 heading = heading(yaw);
        for (int end = -1; end <= 1; end += 2) {
            double x = position.x + heading.x * END_REACH * end;
            double z = position.z + heading.z * END_REACH * end;
            AABB probe = new AABB(x - END_PROBE_WIDTH / 2, position.y + END_PROBE_BOTTOM, z - END_PROBE_WIDTH / 2,
                    x + END_PROBE_WIDTH / 2, position.y + END_PROBE_TOP, z + END_PROBE_WIDTH / 2);
            if (!this.level().noBlockCollision(this, probe)) return false;
        }
        return true;
    }

    /** Client: the wheels roll with the distance travelled along the heading, the deck leans into turns. */
    private void animate() {
        this.wheelSpinO = this.wheelSpin;
        this.leanO = this.lean;
        Vec3 heading = heading(this.getYRot());
        double rolled = (this.getX() - this.xo) * heading.x + (this.getZ() - this.zo) * heading.z;
        this.wheelSpin += (float) (rolled / WHEEL_RADIUS);
        float turned = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        this.lean += (SkateboardMotion.lean(turned) - this.lean) * 0.4F;
    }

    public float wheelSpin(float partialTick) {
        return Mth.lerp(partialTick, this.wheelSpinO, this.wheelSpin);
    }

    public float lean(float partialTick) {
        return Mth.lerp(partialTick, this.leanO, this.lean);
    }

    /** Rolls up slabs, carpets and paths (not full blocks). */
    @Override
    public float maxUpStep() {
        return 0.5F;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.08;
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }

    // ── Riding ────────────────────────────────────────────────────────────────────────────────────────────────────

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        InteractionResult result = super.interact(player, hand, location);
        if (result != InteractionResult.PASS) return result;
        if (this.isVehicle()) return InteractionResult.PASS;
        if (player.isSecondaryUseActive()) {
            if (this.level() instanceof ServerLevel) this.pickUp(player);
            return InteractionResult.SUCCESS;
        }
        if (this.level().isClientSide()) return InteractionResult.SUCCESS;
        return player.startRiding(this) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** Server: gives the board back as one item (or drops it at the player) and removes it. */
    private void pickUp(Player player) {
        if (this.isRemoved()) return;
        ItemStack stack = this.getPickResult();
        this.discard();
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.1F);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player rider ? rider : null;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        this.refreshDimensions();
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.isVehicle() ? RIDDEN : super.getDimensions(pose);
    }

    /** The rider's feet on the grip: their vehicle attachment (a seated player's hips) is cancelled out. */
    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return this.position().add(0.0, GRIP_TOP, 0.0).add(passenger.getVehicleAttachmentPoint(this));
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
        passenger.setYRot(passenger.getYRot() + this.turnRate);
        passenger.setYHeadRot(passenger.getYHeadRot() + this.turnRate);
        this.clampRotation(passenger);
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        this.clampRotation(passenger);
    }

    /** Side-on stance; the view stays within {@link #LOOK_LIMIT} of the heading (as boats clamp theirs). */
    private void clampRotation(Entity passenger) {
        passenger.setYBodyRot(this.getYRot() + STANCE_YAW);
        float delta = Mth.wrapDegrees(passenger.getYRot() - this.getYRot());
        float clamped = Mth.clamp(delta, -LOOK_LIMIT, LOOK_LIMIT);
        passenger.yRotO += clamped - delta;
        passenger.setYRot(passenger.getYRot() + clamped - delta);
        passenger.setYHeadRot(passenger.getYRot());
    }

    /** Steps off to either side of the deck (the rider's right first), else ahead or behind, else where it stands. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 heading = heading(this.getYRot());
        Vec3 right = new Vec3(-heading.z, 0.0, heading.x);
        for (Vec3 offset : new Vec3[]{right, right.reverse(), heading.scale(1.6), heading.scale(-1.6)}) {
            Vec3 target = this.position().add(offset);
            Vec3 safe = DismountHelper.findSafeDismountLocation(passenger.getType(), this.level(), BlockPos.containing(target.x, this.getY() + 0.5, target.z), false);
            if (safe != null && safe.distanceToSqr(target) < 1.0) return safe;
        }
        return this.position();
    }

    // ── Vehicle ───────────────────────────────────────────────────────────────────────────────────────────────────

    @Override
    public boolean isPickable() {
        return !this.isRemoved() && !this.isVehicle();
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    @Override
    protected Item getDropItem() {
        return VehicleItems.SKATEBOARD;
    }

    @Override
    public ItemStack getPickResult() {
        ItemStack stack = new ItemStack(VehicleItems.SKATEBOARD);
        if (this.hasCustomName()) stack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
        return stack;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
