package zcylas.totality.entity.animal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Forest Boar (Astra hybrid): Astra's texture and geometry are what ships (not the discarded independent art), the
 * hitbox and eye height agree with the rendered model, the behaviour is one explicit state machine with no melee
 * fight, the only removal besides death is the silent escape, drops are 1-2 Raw Meat on death only (Looting-
 * compatible), spawning is forest-only, and rendering/animation code stays client-only.
 */
class ForestBoarRegressionTest {

    /** Context/References/Other/forest_boar_texture.png, Astra's approved texture. */
    static final String ASTRA_TEXTURE_SHA256 = "a4bb5a5d9896c349351a0d251763de918e051bfb1ff2b8a007d47e6f57f67144";
    private static final String CLIENT = "src/main/java/zcylas/totality/client/entity/forestboar/";

    private static String read(String path) throws Exception {
        Path p = Path.of(path);
        assertTrue(Files.exists(p), "expected " + p);
        return Files.readString(p);
    }

    private static float constant(String source, String name) {
        Matcher m = Pattern.compile(name + " = ([0-9.]+)F").matcher(source);
        assertTrue(m.find(), name);
        return Float.parseFloat(m.group(1));
    }

    @Test
    void shipsAstrasTextureUnchanged() throws Exception {
        Path png = Path.of("src/main/resources/assets/totality/textures/entity/forest_boar/forest_boar.png");
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(png)));
        assertEquals(ASTRA_TEXTURE_SHA256, sha, "the live texture must be Astra's file, byte for byte");
        BufferedImage tex = ImageIO.read(png.toFile());
        assertEquals(128, tex.getWidth());
        assertEquals(128, tex.getHeight());
        String astra = "Context/References/Other/forest_boar_texture.png";
        if (Files.exists(Path.of(astra))) {
            assertArrayEquals(Files.readAllBytes(Path.of(astra)), Files.readAllBytes(png));
        }
    }

    @Test
    void geometryIsGeneratedFromTheHybridBlockbenchProject() throws Exception {
        String geometry = read(CLIENT + "ForestBoarGeometry.java");
        assertTrue(geometry.contains("GENERATED from Forest_Boar_Hybrid.bbmodel"), "generated from Astra's hybrid project");
        assertEquals(63, geometry.split("\\bbox\\(\"", -1).length - 1, "Astra's 63 cuboids");
        assertEquals(17, geometry.split("= part\\(|return part\\(", -1).length - 1, "Astra's 16 groups plus the model root");
        for (String group : new String[]{"\"body\"", "\"head\"", "\"snout\"", "\"tail\"", "\"tail_tip\"", "\"ear_left\"", "\"ear_right\"",
                "\"brow_left\"", "\"brow_right\"", "\"tusk_left\"", "\"tusk_right\"", "\"leg_front_left\"", "\"leg_front_right\"",
                "\"leg_rear_left\"", "\"leg_rear_right\""}) {
            assertTrue(geometry.contains(group), group);
        }
        assertTrue(geometry.contains("TEXTURE_WIDTH = 128;") && geometry.contains("TEXTURE_HEIGHT = 128;"), "Astra's 128x128 UV space");
        // Not the discarded independent model (box-UV CubeListBuilder, 1.1x mesh scaling).
        assertFalse(geometry.contains("CubeListBuilder") || geometry.contains("MeshTransformer"));
    }

    @Test
    void hitboxAndEyeHeightMatchTheRenderedModel() throws Exception {
        String geometry = read(CLIENT + "ForestBoarGeometry.java");
        float scale = constant(geometry, "RENDER_SCALE");
        assertEquals(1.3F, constant(geometry, "HEIGHT_BLOCKS"), 0.01F, "the reference's 1.3 blocks to the ear tips");
        assertEquals(1.3F / 11.4344F * 16.0F, scale, 0.001F, "scale derived from Astra's rest height (11.43 units)");
        String entities = read("src/main/java/zcylas/totality/init/ModEntities.java");
        Matcher sized = Pattern.compile("FOREST_BOAR =[\\s\\S]*?\\.sized\\(([0-9.]+)f, ([0-9.]+)f\\)[\\s\\S]*?\\.eyeHeight\\(([0-9.]+)f\\)").matcher(entities);
        assertTrue(sized.find());
        float width = Float.parseFloat(sized.group(1)), height = Float.parseFloat(sized.group(2)), eye = Float.parseFloat(sized.group(3));
        assertEquals(constant(geometry, "HEIGHT_BLOCKS"), height, 0.02F, "hitbox height = rendered height");
        assertEquals(constant(geometry, "BODY_WIDTH_BLOCKS"), width, 0.05F, "hitbox width = rendered body width");
        assertTrue(width <= 1.0F, "fits one-block gaps");
        assertEquals(constant(geometry, "EYE_HEIGHT_BLOCKS"), eye, 0.02F, "eye height = the painted eyes");
    }

    @Test
    void everyRequiredAnimationIsConvertedAndTheGaitsAreStrideLocked() throws Exception {
        String anim = read(CLIENT + "ForestBoarAnimation.java");
        for (String a : new String[]{"IDLE", "GRAZE", "SNIFF", "WALK", "RUN", "ALERT", "CHARGE_WINDUP", "CHARGE", "HURT"}) {
            assertTrue(anim.contains("static final AnimationDefinition " + a + " = "), a);
        }
        // The model samples both gaits at the shared phase in seconds: they must be exactly one second long.
        assertTrue(anim.contains("WALK = AnimationDefinition.Builder.withLength(1.0F).looping()"));
        assertTrue(anim.contains("RUN = AnimationDefinition.Builder.withLength(1.0F).looping()"));
        float walk = constant(anim, "WALK_STRIDE_BLOCKS"), run = constant(anim, "RUN_STRIDE_BLOCKS");
        assertTrue(walk > 0.3F && walk < 1.0F && run > walk, walk + " / " + run);
        String model = read(CLIENT + "ForestBoarModel.java");
        for (String a : new String[]{"WALK", "RUN", "IDLE", "GRAZE", "SNIFF", "ALERT", "CHARGE_WINDUP", "CHARGE", "HURT"}) {
            assertTrue(model.contains("ForestBoarAnimation." + a + ".bake(root)"), a + " is used");
        }
    }

    @Test
    void lootTableDropsOneToTwoRawMeatAndNothingElse() throws Exception {
        JsonObject table = JsonParser.parseString(read("src/main/resources/data/totality/loot_table/entities/forest_boar.json")).getAsJsonObject();
        assertEquals("minecraft:entity", table.get("type").getAsString());
        JsonArray pools = table.getAsJsonArray("pools");
        assertEquals(1, pools.size());
        JsonArray entries = pools.get(0).getAsJsonObject().getAsJsonArray("entries");
        assertEquals(1, entries.size(), "a single entry: no porkchops or other extras");
        JsonObject entry = entries.get(0).getAsJsonObject();
        assertEquals("totality:raw_meat", entry.get("name").getAsString());
        JsonObject setCount = null, looting = null;
        for (JsonElement f : entry.getAsJsonArray("functions")) {
            String fn = f.getAsJsonObject().get("function").getAsString();
            if (fn.equals("minecraft:set_count")) setCount = f.getAsJsonObject();
            if (fn.equals("minecraft:enchanted_count_increase")) looting = f.getAsJsonObject();
        }
        assertNotNull(setCount);
        assertEquals(1, setCount.getAsJsonObject("count").get("min").getAsInt());
        assertEquals(2, setCount.getAsJsonObject("count").get("max").getAsInt());
        assertNotNull(looting, "Looting-compatible");
        assertEquals("minecraft:looting", looting.get("enchantment").getAsString());
    }

    @Test
    void oneExplicitStateMachineAndNoMeleeFight() throws Exception {
        String entity = read("src/main/java/zcylas/totality/entity/animal/ForestBoarEntity.java");
        String brain = read("src/main/java/zcylas/totality/entity/animal/ForestBoarBrain.java");
        assertTrue(entity.contains("extends PathfinderMob"), "not an Animal: no breeding");
        assertTrue(entity.contains("this.goalSelector.addGoal(0, new FloatGoal(this));"));
        assertEquals(1, entity.split("addGoal\\(", -1).length - 1, "no goals competing with the state machine");
        for (String banned : new String[]{"MeleeAttackGoal", "HurtByTargetGoal", "NearestAttackableTargetGoal", "targetSelector",
                "TamableAnimal", "BreedGoal", "Saddleable", "ItemBasedSteering"}) {
            assertFalse(entity.contains(banned) || brain.contains(banned), banned);
        }
        assertTrue(entity.contains("this.brain.tick(level)") && entity.contains("customServerAiStep"), "server-side brain");
        assertTrue(brain.contains("hasCharged = true"), "a boar charges once");
        assertTrue(brain.contains("boar.doHurtTarget(level, p)"), "the charge damages through vanilla doHurtTarget (Totality's interceptor)");
    }

    @Test
    void theEscapeIsASilentRemovalAndTheOnlyOneBesidesDeath() throws Exception {
        String entity = read("src/main/java/zcylas/totality/entity/animal/ForestBoarEntity.java");
        String brain = read("src/main/java/zcylas/totality/entity/animal/ForestBoarBrain.java");
        Matcher escape = Pattern.compile("void escape\\(\\) \\{\\s*this\\.discard\\(\\);\\s*}").matcher(entity);
        assertTrue(escape.find(), "escape() only discards: no die(), kill or loot");
        assertTrue(entity.contains("public boolean removeWhenFarAway(double distSqr) {\n        return false;"), "no distance despawn");
        assertTrue(brain.contains("ForestBoarRules.mayEscape(") && brain.contains("anyPlayerCanSee(level)"), "escape gated by the rules");
        assertEquals(1, brain.split("boar\\.escape\\(\\)", -1).length - 1, "one escape path");
        for (String forbidden : new String[]{"setChunkForced", "addRegionTicket", "TicketType"}) {
            assertFalse(entity.contains(forbidden) || brain.contains(forbidden), "no chunk loading to keep an escape running");
        }
    }

    @Test
    void registeredWithRestrictedForestSpawning() throws Exception {
        String entities = read("src/main/java/zcylas/totality/init/ModEntities.java");
        assertTrue(entities.contains("\"forest_boar\"") && entities.contains("MobCategory.CREATURE"));
        String spawns = read("src/main/java/zcylas/totality/init/TotalityBiomeModifications.java");
        assertTrue(spawns.contains("BiomeSelectors.tag(BiomeTags.IS_FOREST)"));
        assertTrue(read("src/main/java/zcylas/totality/Totality.java").contains("ForestBoarEntity::checkForestBoarSpawnRules"));
        assertTrue(read("src/main/generated/assets/totality/lang/en_us.json").contains("\"entity.totality.forest_boar\": \"Forest Boar\""));
        assertTrue(read("src/main/java/zcylas/totality/init/items/CreatureItems.java").contains("FOREST_BOAR"), "spawn egg");
    }

    @Test
    void renderingAndAnimationStayClientOnly() throws Exception {
        Path client = Path.of(CLIENT);
        for (String f : new String[]{"ForestBoarModel.java", "ForestBoarRenderer.java", "ForestBoarGeometry.java", "ForestBoarAnimation.java",
                "ForestBoarMotion.java", "ForestBoarRenderState.java", "FaceUvParts.java"}) {
            assertTrue(Files.exists(client.resolve(f)), f);
        }
        // No common (non-client) source may reference the boar's client classes or the client animation API.
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/zcylas/totality"))) {
            for (Path p : files.filter(p -> p.toString().endsWith(".java") && !p.toString().contains("/client/")).toList()) {
                String name = p.getFileName().toString();
                if (name.equals("TotalityClient.java")) continue;
                String src = Files.readString(p);
                assertFalse(src.contains("client.entity.forestboar"), p + " references client-only boar classes");
                if (name.startsWith("ForestBoar")) {
                    assertFalse(src.contains("net.minecraft.client."), p + " uses client classes");
                }
            }
        }
    }
}
