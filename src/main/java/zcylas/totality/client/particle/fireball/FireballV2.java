package zcylas.totality.client.particle.fireball;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.client.vfx.explosion.FireExplosionRenderer;
import zcylas.totality.client.vfx.explosion.FireExplosionStyle;
import zcylas.totality.client.vfx.glow.EmissiveGlow;
import zcylas.totality.client.vfx.projectile.FireballProjectileVfx;
import zcylas.totality.client.vfx.screen.ScreenFx;
import zcylas.totality.client.vfx.screen.ScreenFxChannel;
import zcylas.totality.client.vfx.screen.ScreenFxEnvelope;
import zcylas.totality.client.vfx.screen.ScreenFxFalloff;
import zcylas.totality.client.vfx.screen.ScreenFxRequest;
import zcylas.totality.entity.magic.FireballParticleBudget;
import zcylas.totality.entity.magic.FireballProjectileEntity;
import zcylas.totality.entity.magic.FireballVfx;

/**
 * Fireball V2's client presentation (VFX Experiment 3): registration of the projectile ({@link FireballProjectileVfx})
 * and explosion ({@link FireExplosionRenderer}) renderers, the emissive budget group shared by every Fireball, and the
 * detonation: the layered fire explosion at the server's explosion centre (the explode packet position, unchanged),
 * debris particles, and restrained Shared Screen FX. Gameplay is untouched: the server dealt the damage before this
 * runs, and nothing here lasts past the aftermath.
 */
public final class FireballV2 {

    /** Fireball's explosion look: 0.30 s expansion, ~0.4 s hot hold (per layer), then cooling (0.42 s); breaking up from ~0.9 s, gone by ~1.5 s. */
    public static final FireExplosionStyle STYLE = new FireExplosionStyle(0.30, 0.40, 0.42, 1.10, 1.28, 80, 12, 30, 10.0, 0.8, 0.45);

    /** Emissive budget group of every Fireball projectile and explosion. */
    public static final String GLOW_GROUP = "fireball";
    /**
     * All Fireballs together glow at most like 2.5 explosions at their brightest: one or two keep their full glow, twenty
     * overlapping ones do not saturate the screen. With Heat Vision (1 at most) this stays under the layer's default
     * global limit (6), so Fireballs never dim Heat Vision.
     */
    static final float GLOW_GROUP_LIMIT = 2.5f;

    /** Subtle shake: 0.45 at up to 6 blocks, fading out by 40 blocks. */
    static final float SHAKE = 0.45f;
    static final ScreenFxEnvelope SHAKE_ENVELOPE = new ScreenFxEnvelope(0.02, 0.06, 0.40);
    static final ScreenFxFalloff SHAKE_FALLOFF = new ScreenFxFalloff(6.0, 40.0);
    /** Controlled flash: 0.5 of the flash cap, 0.18 s, fading out by 48 blocks (30 % when behind the camera). */
    static final float FLASH = 0.5f;
    static final ScreenFxEnvelope FLASH_ENVELOPE = new ScreenFxEnvelope(0.0, 0.03, 0.15);
    static final ScreenFxFalloff FLASH_FALLOFF = new ScreenFxFalloff(8.0, 48.0);
    static final int FLASH_COLOUR = 0xFFD9A0;

    private FireballV2() {}

    public static void register() {
        FireExplosionRenderer.register();
        FireballProjectileVfx.register();
        EmissiveGlow.setGroupLimit(GLOW_GROUP, GLOW_GROUP_LIMIT);
        // A new world starts with an empty particle engine: nothing is alive any more.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != lastLevel) {
                lastLevel = client.level;
                FireballParticleBudget.reset();
            }
        });
    }

    private static ClientLevel lastLevel;

    static void detonate(ClientLevel level, Vec3 at) {
        Vec3 normal = FireballVfx.surfaceNormal(level, at);
        // surfaceNormal defaults to "up" in the open: only a real neighbouring block makes it a surface hit.
        BlockPos behind = BlockPos.containing(at.subtract(normal.scale(0.6)));
        boolean surface = level.getBlockState(behind).isCollisionShapeFullBlock(level, behind);
        FireballVfx.detonateDebris(level, at, normal);
        FireExplosionRenderer.spawn(level, at, surface ? normal : null, FireballProjectileEntity.BLAST_RADIUS, STYLE);
        ScreenFx.request(ScreenFxRequest.at(ScreenFxChannel.SHAKE, at, SHAKE, SHAKE_ENVELOPE, SHAKE_FALLOFF, 0, null));
        ScreenFx.request(ScreenFxRequest.at(ScreenFxChannel.FLASH, at, FLASH, FLASH_ENVELOPE, FLASH_FALLOFF, FLASH_COLOUR, null));
    }
}
