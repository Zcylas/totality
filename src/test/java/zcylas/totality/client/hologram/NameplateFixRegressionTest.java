package zcylas.totality.client.hologram;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nameplate fix: while the custom holographic plate is on an entity its vanilla nametag is suppressed (and only then,
 * only for that entity); the plate no longer lifts itself over a vanilla tag. The live behaviour (Lark, a named armor
 * stand, another player standing and sneaking) is capture scene 62.
 */
class NameplateFixRegressionTest {

    private static String read(String p) throws Exception {
        return Files.readString(Path.of(p)).replace("\r\n", "\n");
    }

    @Test
    void theVanillaNametagIsSuppressedOnlyForThePlatesEntity() throws Exception {
        String mixin = read("src/main/java/zcylas/totality/mixin/client/LivingEntityRendererNameTagMixin.java");
        assertTrue(mixin.contains("@Mixin(LivingEntityRenderer.class)"));
        assertTrue(mixin.contains("method = \"shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z\", at = @At(\"HEAD\"), cancellable = true"));
        assertTrue(mixin.contains("if (TargetNameplate.replacesVanillaName(entity)) cir.setReturnValue(false);"),
                "only cancels for the plate's entity; everything else falls through to vanilla");
        assertEquals(1, mixin.split("setReturnValue").length - 1);
        String plate = read("src/main/java/zcylas/totality/client/hologram/TargetNameplate.java");
        assertTrue(plate.contains("return enabled && entity != null && (entity instanceof TotalityNpcEntity || entity == shown || entity == pending);"),
                "Totality NPCs: the plate is their only nameplate (no vanilla tag when not looked at); others only while plated");
        assertFalse(plate.contains("\"Lark\"") || plate.contains("BankerNpcEntity"), "no special-cased names or NPC kinds");
        assertTrue(plate.contains("|| e instanceof Player || e instanceof ArmorStand) return null;"),
                "players and armor stands never get the plate, so they always keep vanilla nametags");
        String mixins = read("src/main/resources/totality.mixins.json");
        assertTrue(mixins.contains("\"client.LivingEntityRendererNameTagMixin\""));
        assertEquals(mixins.indexOf("\"client\": ["), mixins.substring(0, mixins.indexOf("client.LivingEntityRendererNameTagMixin")).lastIndexOf("\"client\": ["),
                "registered on the client side only");
    }

    @Test
    void thePlateNoLongerStacksAboveAVanillaTag() throws Exception {
        String plate = read("src/main/java/zcylas/totality/client/hologram/TargetNameplate.java");
        assertFalse(plate.contains("shouldShowName() ? 0.3f"), "the lift that cleared the vanilla tag is gone");
        assertTrue(plate.contains("target.getPosition(partial).add(0, target.getBbHeight() + ABOVE_HEAD, 0);"));
        assertTrue(plate.contains("private static final float ABOVE_HEAD = 0.55f;"), "the plate's own offset is unchanged");
    }
}
