package zcylas.totality.api.mining;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.client.combat.CombatTextEntry;
import zcylas.totality.networking.combat.CombatTextPayload;

/** Impact feedback for any source: contact sound, particles and floating structural damage. */
public final class MiningFeedback {

    private MiningFeedback() {}

    /** Sound + particles for a strike on {@code state}. Ineffective hits still get material feedback. */
    public static void contact(ServerLevel level, BlockState state, Vec3 at) {
        var sound = state.getSoundType();
        level.playSound(null, at.x, at.y, at.z, sound.getHitSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.05);
    }

    /** Floating structural damage (existing Combat Text system, shown at the actual hit position). */
    public static void damageText(ServerLevel level, Vec3 at, float amount, int style) {
        send(level, at, CombatTextEntry.TextType.BLOCK_DAMAGE, amount, null, style);
    }

    public static void ineffectiveText(ServerLevel level, Vec3 at) {
        send(level, at, CombatTextEntry.TextType.INEFFECTIVE, 0f, "INEFFECTIVE", 0);
    }

    private static void send(ServerLevel level, Vec3 at, CombatTextEntry.TextType type, float amount, String label, int style) {
        CombatTextPayload payload = new CombatTextPayload(type, null, amount, label,
                at.x, at.y, at.z, false, false, -1, -1, style);
        for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(at) < 1024)) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
