package zcylas.totality.client.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A looping, tickable sound instance that continuously positions itself at the
 * nearest point on a target entity's bounding box.
 *
 * <p>When the listener's camera is <em>inside</em> the entity's AABB (e.g. inside a
 * large boss hitbox), the sound is anchored to the camera position so it stays at
 * full volume without any directional bias.
 *
 * <p>The sound stops automatically if the level's camera entity is null (e.g. the
 * player has disconnected mid-animation).
 *
 * <p>Typical uses:
 * <ul>
 *   <li>Channeling ability sounds (Heat Vision, Freeze Breath) that must track the caster.</li>
 *   <li>Persistent creature sounds (Blood Moon ambience on a specific mob).</li>
 *   <li>Block-entity hum sounds that move with a carried structure.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * EntitySound sound = new EntitySound(ModSounds.HEAT_VISION_LOOP, player,
 *                                     0.8f, 1.0f, SoundSource.PLAYERS);
 * Minecraft.getInstance().getSoundManager().play(sound);
 * // When the ability ends:
 * sound.stop();
 * }</pre>
 *
 * Ported from CreativeCore {@code EntitySound} (team.creative.creativecore).
 */
@Environment(EnvType.CLIENT)
public class EntitySound extends AbstractTickableSoundInstance {

    private final Entity entity;

    public EntitySound(SoundEvent event, Entity entity, float volume, float pitch, SoundSource source) {
        super(event, source, SoundInstance.createUnseededRandom());
        this.entity = entity;
        this.volume = volume;
        this.pitch  = pitch;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity();

        if (camera == null) {
            stop();
            return;
        }

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3  camPos      = camera.getEyePosition(partialTick);
        AABB  bb          = entity.getBoundingBox();

        // If the listener is inside the bounding box, pin the sound to the camera
        // so it stays at full volume without strange directional behaviour.
        if (bb.contains(camPos)) {
            x = (float) camPos.x;
            y = (float) camPos.y;
            z = (float) camPos.z;
            return;
        }

        // Otherwise: clamp each axis to the nearest face of the AABB.
        x = (float) Mth_clamp(camPos.x, bb.minX, bb.maxX);
        y = (float) Mth_clamp(camPos.y, bb.minY, bb.maxY);
        z = (float) Mth_clamp(camPos.z, bb.minZ, bb.maxZ);
    }

    // Inline clamp avoids importing Mth just for one primitive helper.
    private static double Mth_clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }
}
