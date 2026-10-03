package zcylas.totality.client.vfx.eldritch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Eldritch Blast V2 wiring (source and resource checks): gameplay unchanged, the client presents and voices the spell,
 * the original sounds are mono Vorbis (positional in Minecraft), the private reference audio cannot be committed.
 */
class EldritchBlastV2WiringTest {

    private static final String J = "src/main/java/zcylas/totality/";
    private static final String A = "src/main/resources/assets/totality/";

    private static String read(String p) throws IOException {
        return Files.readString(Path.of(p)).replace("\r\n", "\n");
    }

    @Test
    void gameplayIsUnchanged() throws IOException {
        String spell = read(J + "api/magic/spell/destruction/EldritchBlast.java");
        assertTrue(spell.contains("private static final int    DICE_COUNT = 1;"));
        assertTrue(spell.contains("private static final Dice   DAMAGE_DIE = Dice.D10;"));
        assertTrue(spell.contains("DamageTypes.FORCE"));
        assertTrue(spell.contains("20,                      // 1 second cooldown"));
        assertTrue(spell.contains("getSpellcastingAbility(player)"));
        assertTrue(spell.contains("return devBeamOverride > 0 ? devBeamOverride : 1;"), "one beam per cast outside the dev override");
        assertTrue(spell.contains("if (!VerificationReporter.isDevEnvironment()) return;"), "the override is development only");
        String bolt = read(J + "entity/magic/SpellBoltEntity.java");
        assertTrue(bolt.contains("MAX_LIFETIME_TICKS = 60;"));
        assertTrue(bolt.contains("BOLT_SPEED          = 2.5f;"));
        assertTrue(bolt.contains("CombatResolver.resolveSpellAttack("));
    }

    @Test
    void theClientPresentsAndVoicesTheSpell() throws IOException {
        String spell = read(J + "api/magic/spell/destruction/EldritchBlast.java");
        assertTrue(spell.contains(".withVisualStyle(SpellBoltEntity.VisualStyle.ELDRITCH)"));
        assertFalse(spell.contains("withSounds("), "no server-played Evoker sounds any more");
        String bolt = read(J + "entity/magic/SpellBoltEntity.java");
        assertTrue(bolt.contains("serverLevel.sendParticles(ModParticles.ELDRITCH_IMPACT, at.x, at.y, at.z, 0, normal.x, normal.y, normal.z, 1.0);"),
                "the impact event carries the exact hit point and normal");
        assertTrue(bolt.contains("if (level().isClientSide() && !firebolt && !eldritch) {"), "no V1 dust trail for V2");
        assertTrue(read(J + "init/ModParticles.java").contains("ELDRITCH_IMPACT = register(\"eldritch_impact\", true);"));
        String client = read(J + "TotalityClient.java");
        assertTrue(client.contains("EldritchBlastVfx.register();"));
        assertTrue(client.contains("EldritchImpactParticle.register();"));
        assertTrue(Files.exists(Path.of(A + "particles/eldritch_impact.json")));
        assertTrue(Files.exists(Path.of(A + "shaders/core/vfx_eldritch.fsh")));
        assertTrue(read(J + "client/vfx/eldritch/EldritchBlastVfx.java").contains("System.getProperty(\"totality.eldritch.palette\"), Palette.TEAL);"),
                "teal (BG3) is the default palette; violet stays a development option");
        for (String common : new String[]{bolt, spell}) {
            assertFalse(common.contains("zcylas.totality.client."), "common code must not reference Totality client classes");
        }
    }

    @Test
    void soundEventsAndOriginalSoundFilesAreMonoVorbis() throws IOException {
        JsonObject sounds = JsonParser.parseString(read(A + "sounds.json")).getAsJsonObject();
        String mod = read(J + "init/ModSounds.java");
        for (String event : new String[]{"spell.eldritch_blast.cast", "spell.eldritch_blast.impact"}) {
            assertTrue(sounds.has(event), event);
            assertTrue(mod.contains("register(\"" + event + "\")"), event);
        }
        for (String event : new String[]{"spell.eldritch_blast.cast_reference", "spell.eldritch_blast.impact_reference"}) {
            assertFalse(sounds.has(event), event + " is defined only by the private pack (no missing-file warnings on a clean checkout)");
            assertTrue(mod.contains("unregistered(\"" + event + "\")"), event + " is not registered (no missing-definition warning)");
        }
        for (String kind : new String[]{"cast", "impact"}) {
            assertEquals(3, sounds.getAsJsonObject("spell.eldritch_blast." + kind).getAsJsonArray("sounds").size(), "three variants");
            for (int i = 1; i <= 3; i++) {
                byte[] ogg = Files.readAllBytes(Path.of(A + "sounds/spell/eldritch_blast/" + kind + i + ".ogg"));
                assertEquals("OggS", new String(ogg, 0, 4));
                int packet = 27 + (ogg[26] & 0xFF);
                assertEquals("vorbis", new String(ogg, packet + 1, 6));
                assertEquals(1, ogg[packet + 11], kind + i + " must be mono (Minecraft only attenuates and pans mono sounds)");
            }
        }
    }

    @Test
    void thePrivateReferenceAudioIsGitIgnored() throws IOException {
        assertTrue(read(".gitignore").contains("src/main/resources/resourcepacks/eldritch_reference_private/"),
                "audio extracted from a third-party clip must never be committed");
        assertTrue(read(J + "client/vfx/eldritch/EldritchBlastVfx.java").contains(".filter(mod -> mod.findPath(\"resourcepacks/\" + REFERENCE_PACK).isPresent())"),
                "the private pack is registered only when its folder exists");
        assertTrue(read(J + "init/ModSounds.java").contains("PRIVATE REVIEW MATERIAL ONLY"));
    }
}
