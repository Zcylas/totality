package zcylas.totality.entity.rest;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.init.ModEntities;

/**
 * Invisible, physics-less mount used to seat a player during a Short Rest's Read/Meditate
 * activity. Riding any entity makes vanilla's own humanoid model bend the legs into a sitting
 * pose automatically — the same trick the "Sit" mod uses, and far more reliable than forcing
 * Pose.CROUCHING (which vanilla's own per-tick pose recalculation stomps within a tick).
 */
public class RestSeatEntity extends Entity {

    public RestSeatEntity(EntityType<RestSeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public RestSeatEntity(Level level, Vec3 pos) {
        this(ModEntities.REST_SEAT, level);
        setPos(pos.x, pos.y, pos.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // Stand up exactly where the seat was rather than vanilla's default nearby-space search.
        return position();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(ValueInput tag) {}
    @Override protected void addAdditionalSaveData(ValueOutput tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRender(double x, double y, double z) {
        return false;
    }
}
