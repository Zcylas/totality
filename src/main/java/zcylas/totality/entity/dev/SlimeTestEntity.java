package zcylas.totality.entity.dev;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * DEVELOPMENT TEST ENTITY (Small Slime visual tests), not the production Slime. It only displays the shared Small
 * Slime base model (Context/Assets/Models/slime_base): no goals, so it stands still, falls and collides like any mob,
 * never despawns and drops nothing. {@code Variant} ({@link SlimeTestVariant}, e.g. "hydro") picks the appearance,
 * {@code Mode} ({@link SlimeTestRenderMode}: "palette", "plain" or "tint") the colouring method and {@code Eyes}
 * ({@link SlimeTestEyeStyle}: "plain" or "element") the eye look; set them with summon or {@code /data merge} NBT.
 */
public class SlimeTestEntity extends Mob {

    private static final EntityDataAccessor<Byte> DATA_VARIANT =
            SynchedEntityData.defineId(SlimeTestEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_MODE =
            SynchedEntityData.defineId(SlimeTestEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_EYES =
            SynchedEntityData.defineId(SlimeTestEntity.class, EntityDataSerializers.BYTE);

    public SlimeTestEntity(EntityType<? extends SlimeTestEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 4.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, (byte) SlimeTestVariant.GRAYSCALE.ordinal());
        builder.define(DATA_MODE, (byte) SlimeTestRenderMode.PALETTE.ordinal());
        builder.define(DATA_EYES, (byte) SlimeTestEyeStyle.PLAIN.ordinal());
    }

    public SlimeTestVariant getVariant() {
        return SlimeTestVariant.byId(this.entityData.get(DATA_VARIANT));
    }

    public void setVariant(SlimeTestVariant variant) {
        this.entityData.set(DATA_VARIANT, (byte) variant.ordinal());
    }

    public SlimeTestRenderMode getRenderMode() {
        return SlimeTestRenderMode.byId(this.entityData.get(DATA_MODE));
    }

    public void setRenderMode(SlimeTestRenderMode mode) {
        this.entityData.set(DATA_MODE, (byte) mode.ordinal());
    }

    public SlimeTestEyeStyle getEyeStyle() {
        return SlimeTestEyeStyle.byId(this.entityData.get(DATA_EYES));
    }

    public void setEyeStyle(SlimeTestEyeStyle style) {
        this.entityData.set(DATA_EYES, (byte) style.ordinal());
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Variant", getVariant().serializedName());
        output.putString("Mode", getRenderMode().serializedName());
        output.putString("Eyes", getEyeStyle().serializedName());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setVariant(SlimeTestVariant.byName(input.getStringOr("Variant", SlimeTestVariant.GRAYSCALE.serializedName())));
        setRenderMode(SlimeTestRenderMode.byName(input.getStringOr("Mode", SlimeTestRenderMode.PALETTE.serializedName())));
        setEyeStyle(SlimeTestEyeStyle.byName(input.getStringOr("Eyes", SlimeTestEyeStyle.PLAIN.serializedName())));
    }
}
