package zcylas.totality.api.core.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/**
 * Mobs created by live-world verification suites. They are real mobs of their real entity type (every
 * {@code instanceof}, type check, mixin and combat path is unchanged) except that their death never drops loot
 * or experience — so a suite that kills one cannot leave rotten flesh or experience orbs saved in the world.
 * Production mob drops and game rules are untouched.
 */
public final class VerificationMobs {

    private VerificationMobs() {}

    public static Zombie lootlessZombie(Level level) {
        return new Zombie(EntityTypes.ZOMBIE, level) {
            @Override
            protected boolean shouldDropLoot(ServerLevel serverLevel) { return false; }

            @Override
            public boolean shouldDropExperience() { return false; }
        };
    }
}
