package zcylas.totality.entity.magic;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Creative Test G and its correction, Fireball: the spell's identity, damage, save, radius, speed, lifetime and ignition
 * are unchanged; the blast is presentation-only (no vanilla explosion: its packet carries the sound and the detonation
 * emitter, no knockback) and deals one Fire calculation to every creature in the sphere, the caster included; every
 * particle definition points at real sprites; rendering stays on the client. The live checks are FireballVerification
 * (server: controlled detonations count each damage event) and capture scene 58 (client).
 */
class FireballVfxRegressionTest {

    private static final String J = "src/main/java/zcylas/totality/";
    private static final Path ASSETS = Path.of("src/main/resources/assets/totality");

    /** Source text with LF line endings (the Fireball sources are CRLF). */
    private static String read(String p) throws Exception {
        return Files.readString(Path.of(p)).replace("\r\n", "\n");
    }

    @Test
    void spellAndProjectileGameplayIsUnchanged() throws Exception {
        String spell = read(J + "api/magic/spell/destruction/FireballSpell.java");
        assertTrue(spell.contains("                3,\n                SpellSchool.DESTRUCTION,"), "3rd-level Destruction");
        assertTrue(spell.contains("100,  // 5s cooldown"));
        assertTrue(spell.contains("SoundEvents.FIRECHARGE_USE"));
        String e = read(J + "entity/magic/FireballProjectileEntity.java");
        assertTrue(e.contains("BOLT_SPEED  = 1.2f;"));
        assertTrue(e.contains("public static final float BLAST_RADIUS = 6.0f;"));
        assertTrue(e.contains("DICE_COUNT  = 8;") && e.contains("DAMAGE_DIE  = Dice.D6;"));
        assertTrue(e.contains("MAX_LIFETIME = 100;"));
        assertTrue(e.contains("SavingThrow.roll(target, AbilityScore.DEX, (int) dc, RollType.NORMAL)"));
        assertTrue(e.contains("float damage = outcome.isSuccess() ? raw / 2f : raw;"));
        assertTrue(e.contains("BaseFireBlock.canBePlacedAt("), "ignition kept");
    }

    @Test
    void theBlastIsPresentationOnlyWithOneFireCalculationPerCreatureCasterIncluded() throws Exception {
        String e = read(J + "entity/magic/FireballProjectileEntity.java");
        assertFalse(e.contains("serverLevel.explode(") || e.contains(".explode("), "no vanilla explosion: it would add Force damage, knockback and pushes");
        assertTrue(e.contains("new ClientboundExplodePacket(centre, BLAST_RADIUS * 0.8f, 0, Optional.empty(),\n"
                + "                ModParticles.FIREBALL_DETONATION, SoundEvents.GENERIC_EXPLODE, WeightedList.of());"),
                "the explosion packet only presents: no knockback, no blocks, the detonation emitter and the sound");
        assertTrue(e.contains("if (player.distanceToSqr(centre) < 4096.0) player.connection.send(packet);"), "sent like vanilla's (64 blocks)");
        assertTrue(e.contains("serverLevel.gameEvent(null, GameEvent.EXPLODE, centre);"));
        assertTrue(e.contains("e -> e.distanceTo(this) <= BLAST_RADIUS);"), "every creature in the sphere, the caster included");
        assertFalse(e.contains("e != caster"), "no caster immunity");
        assertTrue(e.contains("TotalityDamage.hurt(target, target == caster ? null : caster, DamageTypes.FIRE, damage,"),
                "one Fire damage call per creature; the caster's own hit has no attacker (no PvP gate, no self-shove)");
        assertFalse(e.contains("ParticleTypes.FLAME") || e.contains("ParticleTypes.LARGE_SMOKE"), "the old vanilla trail is gone");
        assertTrue(e.contains("Vec3 impact = impactPoint(hit, start, velocity);"), "detonates at the true impact point");
    }

    @Test
    void everyParticleDefinitionPointsAtRealSprites() throws Exception {
        for (String name : new String[]{"blast", "smoke", "fragment", "spark", "ember", "streak"}) {
            JsonArray textures = JsonParser.parseString(Files.readString(ASSETS.resolve("particles/fireball_" + name + ".json")))
                    .getAsJsonObject().getAsJsonArray("textures");
            assertTrue(textures.size() >= 4, name);
            for (var t : textures) {
                Path png = ASSETS.resolve("textures/particle/" + t.getAsString().substring("totality:".length()) + ".png");
                assertTrue(Files.exists(png), png.toString());
            }
        }
        assertEquals(0, JsonParser.parseString(Files.readString(ASSETS.resolve("particles/fireball_detonation.json"))).getAsJsonObject().size(),
                "the detonation is an emitter without sprites");
        BufferedImage core = ImageIO.read(ASSETS.resolve("textures/entity/fireball/core.png").toFile());
        assertEquals(128, core.getWidth());
        assertEquals(32, core.getHeight());
        assertTrue(Files.exists(ASSETS.resolve("textures/entity/fireball/tail.png")) && Files.exists(ASSETS.resolve("textures/entity/fireball/halo.png")));
    }

    @Test
    void renderingIsClientOnlyAndRegistered() throws Exception {
        String client = read(J + "TotalityClient.java");
        assertTrue(client.contains("ModEntities.FIREBALL_PROJECTILE,\n                FireballProjectileRenderer::new);"));
        assertTrue(client.contains("FireballParticles.register();"));
        String vfx = read(J + "entity/magic/FireballVfx.java");
        assertFalse(vfx.contains("net.minecraft.client"), "common code only adds particles");
        assertTrue(read(J + "Totality.java").contains("FireballVerification.register();"));
    }
}
