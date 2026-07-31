package zcylas.totality.client.renderer.hud;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinels for {@code TotalityHudRenderer.shouldRenderPersistentPlayerHud} —
 * part 4 of the final player-HUD and vanilla-chat compatibility correction (see
 * {@code TOTALITY_SMALL_HUD_CLEANUP_IMPLEMENTATION_REPORT.md}'s newest addendum). Live testing
 * found the persistent Totality player HUD remained visible behind {@code InventoryScreen} and
 * other ordinary menu screens, unlike vanilla's own hotbar/health/food.
 *
 * <p><b>Evidentiary limits:</b> every test in this file is a source-text regression sentinel, not
 * runtime proof — {@code TotalityHudRenderer}'s render lambda requires a bootstrapped, GL-
 * initialized {@code Minecraft}/{@code Screen} to exercise for real, unavailable under plain
 * JUnit, the same constraint documented by every other {@code *SourceRegressionTest} in this
 * suite. The vanilla behavior these sentinels assume — no dedicated "hide HUD for this screen"
 * flag exists; opaque screens simply draw over the already-rendering hotbar — was confirmed once,
 * out-of-band, by decompiling MC 26.2's real {@code minecraft-merged-deobf-26.2.jar}
 * ({@code GameRenderer.extract}, {@code Gui.extractRenderState}); these sentinels only guard
 * against the resulting predicate's own source regressing afterward.
 */
class PersistentHudVisibilitySourceRegressionTest {

    private static final Path HUD_RENDERER =
            Path.of("src/main/java/zcylas/totality/client/renderer/hud/TotalityHudRenderer.java");

    private static String read(Path path) throws Exception {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    private static String methodBody(String source, String methodSignatureStart, String nextMethodOrMarker) {
        int start = source.indexOf(methodSignatureStart);
        assertTrue(start >= 0, "expected to find method starting with: " + methodSignatureStart);
        int end = source.indexOf(nextMethodOrMarker, start + methodSignatureStart.length());
        assertTrue(end > start, "expected to find the next marker after the method: " + nextMethodOrMarker);
        return source.substring(start, end);
    }

    // ── 35: one shared predicate exists ──────────────────────────────────────────────────────

    @Test
    void sharedPersistentHudEligibilityPredicateExists() throws Exception {
        String source = read(HUD_RENDERER);
        assertTrue(source.contains("private static boolean shouldRenderPersistentPlayerHud(Minecraft client) {"));
    }

    // 41. F1 hidden-GUI state remains ineligible (preserved from the pre-existing check).
    // 39/40 (partially): no player / no world remains ineligible too.
    @Test
    void predicateStillRequiresAPlayerAndAnUnhiddenGui() throws Exception {
        String source = read(HUD_RENDERER);
        String body = methodBody(source,
                "private static boolean shouldRenderPersistentPlayerHud(Minecraft client) {", "\n    }");
        assertTrue(body.contains("client.player == null || client.gui.hud.isHidden()"),
                "must preserve the original no-player / F1-hidden guard, not just add a new screen check on top");
    }

    // 37/38/39/40. ChatScreen remains eligible; every other screen (InventoryScreen, creative
    // inventory, container/workstation screens, etc.) is ineligible — the predicate does not
    // enumerate every blocking screen subclass individually, it allows null-or-ChatScreen and
    // rejects everything else, which is the narrowest correct implementation.
    @Test
    void onlyNullScreenOrChatScreenAreEligible() throws Exception {
        String source = read(HUD_RENDERER);
        String body = methodBody(source,
                "private static boolean shouldRenderPersistentPlayerHud(Minecraft client) {", "\n    }");
        assertTrue(body.contains("Screen screen = client.gui.screen();"));
        assertTrue(body.contains("return screen == null || screen instanceof ChatScreen;"),
                "normal gameplay (no screen) and ChatScreen must be the only eligible states — every other screen is ineligible by construction, without needing to enumerate InventoryScreen/CreativeModeInventoryScreen/AbstractContainerScreen/etc. individually");
    }

    // 46. Vanilla's own hotbar rendering is never touched — this predicate only gates Totality's
    // own render lambda, it does not modify or mixin into Hud/Gui/GameRenderer at all.
    @Test
    void noVanillaHudOrGuiClassIsMixedIntoOrModified() throws Exception {
        String source = read(HUD_RENDERER);
        assertFalse(source.contains("@Mixin"), "TotalityHudRenderer must remain a plain HudElementRegistry consumer, not a mixin, for this gating");
    }

    // 36/42/43/44. The predicate actually gates the render lambda's early return, which covers
    // Health/Mana/Stamina/Food/AC/Rage (all drawn later in the same lambda body).
    @Test
    void registerLambdaEarlyReturnsUsingTheSharedPredicate() throws Exception {
        String source = read(HUD_RENDERER);
        String registerBody = methodBody(source, "public static void register() {", "private static boolean shouldRenderPersistentPlayerHud");
        assertTrue(registerBody.contains("if (!shouldRenderPersistentPlayerHud(client)) return;"),
                "the render lambda must early-return using the shared predicate, gating every element drawn afterward (Health/Mana/Stamina/Food/AC/Rage/offhand indicator/context huds)");
        // Confirms the old, narrower pre-existing check no longer stands alone unguarded by screen state.
        assertFalse(registerBody.contains("if (client.player == null || client.gui.hud.isHidden()) return;"),
                "the old two-condition early return must be fully replaced by the predicate call, not left duplicated alongside it");
    }

    // Health/Mana/Stamina/AC/Food/Rage draw calls all sit textually after the predicate-gated
    // early return inside the same lambda — confirms they cannot execute when the predicate is
    // false (single early-return gate, not per-element checks).
    @Test
    void allSixPlayerFacingElementsSitAfterTheSharedGuard() throws Exception {
        String source = read(HUD_RENDERER);
        String registerBody = methodBody(source, "public static void register() {", "private static boolean shouldRenderPersistentPlayerHud");
        int guardIdx = registerBody.indexOf("if (!shouldRenderPersistentPlayerHud(client)) return;");
        assertTrue(guardIdx >= 0);
        String afterGuard = registerBody.substring(guardIdx);
        assertTrue(afterGuard.contains("drawBarSmooth(graphics, client, leftX, hpY,"), "Health must be gated");
        assertTrue(afterGuard.contains("drawBarSmooth(graphics, client, leftX, staminaY,"), "Stamina must be gated");
        assertTrue(afterGuard.contains("drawBarSmooth(graphics, client, leftX, manaY,"), "Mana must be gated");
        assertTrue(afterGuard.contains("drawBarMirroredSmooth(graphics, client, rightX, hpY,"), "Food must be gated");
        assertTrue(afterGuard.contains("graphics.text(client.font, \"AC \" + ac, acX, acY, 0xFF00CCFF, true);"), "AC must be gated");
        assertTrue(afterGuard.contains("drawSecondaryResources(graphics, client,"), "Rage/secondary resources must be gated");
        assertTrue(afterGuard.contains("renderOffhandAttackIndicator(graphics, client, screenW, screenH);"), "the offhand indicator must be gated");
        assertTrue(afterGuard.contains("MagicContextHud.render(graphics, client, screenW, screenH);"), "MagicContextHud must be gated");
        assertTrue(afterGuard.contains("AbilityContextHud.render(graphics, client, screenW, screenH);"), "AbilityContextHud must be gated");
    }

    // 45. Separately registered overlays are not touched by this change at all — TotalityHudRenderer
    // never references them, confirming they retain their own independent screen rules.
    @Test
    void separatelyRegisteredOverlaysAreNotReferencedByTotalityHudRenderer() throws Exception {
        String source = read(HUD_RENDERER);
        for (String independentOverlay : new String[] {
                "NotificationManager", "MobHealthBarHud", "CombatTextRenderer",
                "RestHud", "QuestTrackerHud", "CastBarHud"
        }) {
            assertFalse(source.contains(independentOverlay),
                    independentOverlay + " must not be referenced by TotalityHudRenderer — it has its own independent screen rules and must not be gated by this new predicate");
        }
    }

    // ChatScreen import is present (required for the instanceof check) without any broader
    // chat-screen behavior change in this file — the actual chat repositioning lives entirely in
    // the separate zcylas.totality.mixin.client.chat mixins, untouched by this part.
    @Test
    void chatScreenIsOnlyUsedForTheInstanceofCheck() throws Exception {
        String source = read(HUD_RENDERER);
        assertTrue(source.contains("import net.minecraft.client.gui.screens.ChatScreen;"));
        assertTrue(source.contains("import net.minecraft.client.gui.screens.Screen;"));
        assertTrue(source.contains("screen instanceof ChatScreen"));
        // No chat-message/input/suggestion repositioning logic belongs in this file — that lives
        // entirely in the separate zcylas.totality.mixin.client.chat mixins.
        assertFalse(source.contains("ChatComponent"));
        assertFalse(source.contains("CommandSuggestions"));
    }
}
