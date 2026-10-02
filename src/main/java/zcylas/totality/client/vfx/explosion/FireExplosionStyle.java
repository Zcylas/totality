package zcylas.totality.client.vfx.explosion;

/**
 * Tunable numbers of a {@link FireExplosion}: timing, element counts and look. Spell-specific values live with the
 * spell (Fireball: {@code FireballV2.STYLE}); the primitive itself is generic.
 *
 * @param expandSeconds    time for the fire mass to reach its full size (ease-out)
 * @param holdSeconds      the fire stays near full heat for this long (it loses about a fifth of it), then cools
 * @param coolingSeconds   temperature decay constant after the hold: heat × e^(−(age − hold) / coolingSeconds)
 * @param minLife          shortest life of a body volume, seconds
 * @param maxLife          longest life of a body volume, seconds (the shell is gone by about 1.15 × this)
 * @param bodyVolumes      billowing fire volumes forming the mass
 * @param tongues          flame tongues thrown out of the mass early
 * @param groundLicks      flame licks running out across the ground (only when the sphere reaches it)
 * @param texelsPerBlock   pixel quantisation of the procedural shading (Minecraft-scale texels)
 * @param buoyancy         upward drift of cooling soot, blocks per s² after the fire phase
 * @param emissive         strength of the contribution to the Emissive Rendering Layer (0 = none)
 */
public record FireExplosionStyle(double expandSeconds, double holdSeconds, double coolingSeconds, double minLife,
                                 double maxLife, int bodyVolumes, int tongues, int groundLicks, double texelsPerBlock, double buoyancy,
                                 double emissive) {

    /** Total visible duration of an explosion in this style (last volume fully faded). */
    public double totalSeconds() {
        return maxLife * FireExplosion.FADE_END + FireExplosion.MAX_DELAY;
    }
}
