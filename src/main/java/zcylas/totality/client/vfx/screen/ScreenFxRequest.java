package zcylas.totality.client.vfx.screen;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One Shared Screen FX request. Purely cosmetic and client-side.
 *
 * @param channel   the channel it contributes to
 * @param origin    world position, or null for a non-spatial request (no distance attenuation, never "behind")
 * @param intensity 0..1 strength at the origin, before attenuation, merging and caps
 * @param envelope  time-based shape
 * @param falloff   distance attenuation
 * @param priority  higher priorities suppress lower ones on the same channel
 * @param audience  who perceives it
 * @param subject   the player for {@link ScreenFxAudience#SUBJECT_ONLY}
 * @param colour    flash tint (0xRRGGBB); ignored by other channels
 * @param owner     optional owner, for {@link ScreenFx#cancelOwner}
 */
public record ScreenFxRequest(ScreenFxChannel channel, @Nullable Vec3 origin, float intensity, ScreenFxEnvelope envelope,
                              ScreenFxFalloff falloff, ScreenFxPriority priority, ScreenFxAudience audience,
                              @Nullable UUID subject, int colour, @Nullable Object owner) {

    public ScreenFxRequest {
        intensity = Math.clamp(intensity, 0.0f, 1.0f);
    }

    /** A spatial request heard by everyone in range, at NORMAL priority. */
    public static ScreenFxRequest at(ScreenFxChannel channel, Vec3 origin, float intensity, ScreenFxEnvelope envelope,
                                     ScreenFxFalloff falloff, int colour, @Nullable Object owner) {
        return new ScreenFxRequest(channel, origin, intensity, envelope, falloff, ScreenFxPriority.NORMAL,
                ScreenFxAudience.EVERYONE_IN_RANGE, null, colour, owner);
    }

    public ScreenFxRequest withPriority(ScreenFxPriority p) {
        return new ScreenFxRequest(channel, origin, intensity, envelope, falloff, p, audience, subject, colour, owner);
    }
}
