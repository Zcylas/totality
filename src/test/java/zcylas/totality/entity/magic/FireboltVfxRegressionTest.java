package zcylas.totality.entity.magic;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Firebolt VFX: the redesign is visual only (gameplay values and the other bolt spells untouched), every particle
 * definition points at real sprites, and rendering code stays on the client.
 */
class FireboltVfxRegressionTest {

    private static final String J = "src/main/java/zcylas/totality/";
    private static final Path ASSETS = Path.of("src/main/resources/assets/totality");

    private static String read(String p) throws Exception {
        return Files.readString(Path.of(p));
    }

    @Test
    void fireboltGameplayIsUnchanged() throws Exception {
        String spell = read(J + "api/magic/spell/destruction/FireboltSpell.java");
        assertTrue(spell.contains("private static final int  DICE_COUNT = 1;"));
        assertTrue(spell.contains("private static final Dice DAMAGE_DIE = Dice.D10;"));
        assertTrue(spell.contains("DamageTypes.FIRE"));
        assertTrue(spell.contains("20,                      // 1 second cooldown"));
        assertTrue(spell.contains("0,                       // cantrip"));
        assertTrue(spell.contains(".withSounds(SoundEvents.FIRECHARGE_USE, SoundEvents.BLAZE_SHOOT)"));
        assertTrue(spell.contains(".withVisualStyle(SpellBoltEntity.VisualStyle.FIREBOLT)"));
        String bolt = read(J + "entity/magic/SpellBoltEntity.java");
        assertTrue(bolt.contains("MAX_LIFETIME_TICKS = 60;"));
        assertTrue(bolt.contains("BOLT_SPEED          = 2.5f;"));
        assertTrue(bolt.contains("CombatResolver.resolveSpellAttack("), "the spell attack path is unchanged");
        assertTrue(bolt.contains("BaseFireBlock.canBePlacedAt(serverLevel, firePos, hit.getDirection())"), "ignition unchanged");
    }

    @Test
    void onlyFireboltUsesTheNewVisuals() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of(J + "api/magic/spell"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                boolean uses = Files.readString(p).contains("VisualStyle.FIREBOLT");
                assertEquals(p.getFileName().toString().equals("FireboltSpell.java"), uses, p.toString());
            }
        }
        String bolt = read(J + "entity/magic/SpellBoltEntity.java");
        assertTrue(bolt.contains("builder.define(VISUAL_STYLE, (byte) VisualStyle.DEFAULT.ordinal());"), "other bolts keep the dust trail");
    }

    @Test
    void everyParticleDefinitionPointsAtRealSprites() throws Exception {
        for (String name : new String[]{"ember", "wisp", "spark", "streak", "flash", "impact"}) {
            Path def = ASSETS.resolve("particles/firebolt_" + name + ".json");
            assertTrue(Files.exists(def), def.toString());
            var textures = JsonParser.parseString(Files.readString(def)).getAsJsonObject().getAsJsonArray("textures");
            if (!name.equals("impact")) assertFalse(textures.isEmpty(), name + " has frames");
            for (var t : textures) {
                String path = t.getAsString().replace("totality:", "");
                BufferedImage im = ImageIO.read(ASSETS.resolve("textures/particle/" + path + ".png").toFile());
                assertNotNull(im, path);
                assertEquals(Integer.bitCount(im.getWidth()), 1, path + " is a power-of-two sprite");
            }
        }
    }

    @Test
    void projectileTexturesAtMinecraftTexelDensity() throws Exception {
        Path dir = ASSETS.resolve("textures/entity/firebolt");
        BufferedImage core = ImageIO.read(dir.resolve("core.png").toFile());
        BufferedImage tail = ImageIO.read(dir.resolve("tail.png").toFile());
        BufferedImage halo = ImageIO.read(dir.resolve("halo.png").toFile());
        assertEquals(64, core.getWidth());   // 4 frames of 16x16
        assertEquals(16, core.getHeight());
        assertEquals(32, tail.getWidth());   // 4 frames of 32x16
        assertEquals(64, tail.getHeight());
        assertEquals(32, halo.getWidth());
    }

    @Test
    void renderingStaysOnTheClient() throws Exception {
        String vfx = read(J + "entity/magic/FireboltVfx.java");
        String bolt = read(J + "entity/magic/SpellBoltEntity.java");
        String particles = read(J + "init/ModParticles.java");
        for (String common : new String[]{vfx, bolt, particles}) {
            assertFalse(common.contains("net.minecraft.client."), "common code must not load client classes");
            assertFalse(common.contains("zcylas.totality.client."), "common code must not reference Totality client classes");
        }
        assertTrue(Files.exists(Path.of(J + "client/particle/firebolt/FireboltParticle.java")));
        assertTrue(read(J + "TotalityClient.java").contains("FireboltParticles.register()"));
    }
}
