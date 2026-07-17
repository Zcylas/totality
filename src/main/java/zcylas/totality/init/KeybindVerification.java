package zcylas.totality.init;

import org.lwjgl.glfw.GLFW;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.VerificationReporter;

/**
 * Dev-environment-gated self-test for the Ability/Spell/Grimoire/Block/Radial Modifier key
 * mappings (Phase 4 correction pass, Parts B and D; radial correction pass, Parts A and B) — same
 * {@link VerificationReporter} convention as the server-side suites, but run synchronously from
 * {@code TotalityClient.onInitializeClient()} (client-only entry point): {@code
 * net.minecraft.client.KeyMapping} is a client-only type, so this can never run on a dedicated
 * server, mirroring why {@code ProvisionerRendererVerification} is registered the same way.
 *
 * <p>What this suite CANNOT verify (documented rather than silently skipped): the actual chord
 * behavior in {@link TotalityKeybindHandlers} — whether holding the Ability/Spell/Grimoire key
 * keeps a channeled ability active or a radial retained, whether Modifier+key opens the radial
 * without activating, whether a rebound key is honored during real play including RETAINING an
 * already-open radial across the rebind's own tick-by-tick lifetime (the radial correction pass's
 * actual bug — see {@code AbilityRadialScreen}/{@code SpellRadialScreen}/{@code
 * GrimoireRadialScreen}), and whether the old default key stops working once rebound — all require
 * driving real keyboard input against a live client window, which this environment has no tool to
 * do (the same standing limitation documented throughout the Provisioner phases). Those items
 * remain Stefan's manual checklist.
 */
public final class KeybindVerification {

    private KeybindVerification() {}

    public static void runIfDev() {
        if (!VerificationReporter.isDevEnvironment()) return;

        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "KeybindVerification");

        r.check("Radial Modifier key mapping is registered under the Totality category",
                ModKeybinds.RADIAL_MODIFIER.getCategory() == ModKeybinds.TOTALITY_CATEGORY,
                "category=" + ModKeybinds.RADIAL_MODIFIER.getCategory());

        r.check("Radial Modifier defaults to Left Alt",
                ModKeybinds.RADIAL_MODIFIER.getDefaultKey().getValue() == GLFW.GLFW_KEY_LEFT_ALT,
                "defaultKey=" + ModKeybinds.RADIAL_MODIFIER.getDefaultKey().getValue());

        r.check("Radial Modifier uses its own translation key, not a reused one",
                "key.totality.radial_modifier".equals(ModKeybinds.RADIAL_MODIFIER.getName()),
                "name=" + ModKeybinds.RADIAL_MODIFIER.getName());

        // Phase 4 correction pass, Part B — USE_ABILITY/USE_SPELL are the sole authoritative key
        // mappings TotalityKeybindHandlers reads (no more literal GLFW_KEY_Z/GLFW_KEY_X polling);
        // these checks confirm the registrations themselves are correct so a rebind actually has
        // something real to change. The chord/activation behavior itself is manual (see above).
        r.check("Ability key mapping is registered under the Totality category",
                ModKeybinds.USE_ABILITY.getCategory() == ModKeybinds.TOTALITY_CATEGORY,
                "category=" + ModKeybinds.USE_ABILITY.getCategory());

        r.check("Ability key mapping defaults to Z",
                ModKeybinds.USE_ABILITY.getDefaultKey().getValue() == GLFW.GLFW_KEY_Z,
                "defaultKey=" + ModKeybinds.USE_ABILITY.getDefaultKey().getValue());

        r.check("Spell key mapping is registered under the Totality category",
                ModKeybinds.USE_SPELL.getCategory() == ModKeybinds.TOTALITY_CATEGORY,
                "category=" + ModKeybinds.USE_SPELL.getCategory());

        r.check("Spell key mapping defaults to X",
                ModKeybinds.USE_SPELL.getDefaultKey().getValue() == GLFW.GLFW_KEY_X,
                "defaultKey=" + ModKeybinds.USE_SPELL.getDefaultKey().getValue());

        r.check("Ability, Spell, and Radial Modifier each use distinct translation keys",
                "key.totality.use_ability".equals(ModKeybinds.USE_ABILITY.getName())
                        && "key.totality.use_spell".equals(ModKeybinds.USE_SPELL.getName())
                        && !ModKeybinds.USE_ABILITY.getName().equals(ModKeybinds.USE_SPELL.getName())
                        && !ModKeybinds.USE_ABILITY.getName().equals(ModKeybinds.RADIAL_MODIFIER.getName())
                        && !ModKeybinds.USE_SPELL.getName().equals(ModKeybinds.RADIAL_MODIFIER.getName()),
                "ability=" + ModKeybinds.USE_ABILITY.getName() + ", spell=" + ModKeybinds.USE_SPELL.getName()
                        + ", modifier=" + ModKeybinds.RADIAL_MODIFIER.getName());

        // Radial correction pass, Part B — Grimoire now shares the Ability/Spell modifier-chord
        // model via the EXISTING ModKeybinds.OPEN_GRIMOIRE mapping (no second mapping created);
        // these checks confirm that registration is correct and that V remains exclusively
        // Block's key at the registration level (structural confirmation the audit already made —
        // no code path anywhere reads GLFW_KEY_V for the Grimoire radial).
        r.check("Grimoire key mapping is registered under the Totality category",
                ModKeybinds.OPEN_GRIMOIRE.getCategory() == ModKeybinds.TOTALITY_CATEGORY,
                "category=" + ModKeybinds.OPEN_GRIMOIRE.getCategory());

        r.check("Grimoire key mapping defaults to C",
                ModKeybinds.OPEN_GRIMOIRE.getDefaultKey().getValue() == GLFW.GLFW_KEY_C,
                "defaultKey=" + ModKeybinds.OPEN_GRIMOIRE.getDefaultKey().getValue());

        r.check("Block key mapping defaults to V, distinct from Grimoire's default C",
                ModKeybinds.BLOCK.getDefaultKey().getValue() == GLFW.GLFW_KEY_V
                        && ModKeybinds.BLOCK.getDefaultKey().getValue() != ModKeybinds.OPEN_GRIMOIRE.getDefaultKey().getValue(),
                "blockDefault=" + ModKeybinds.BLOCK.getDefaultKey().getValue()
                        + ", grimoireDefault=" + ModKeybinds.OPEN_GRIMOIRE.getDefaultKey().getValue());

        r.check("Grimoire and Block use distinct translation keys from each other and from Ability/Spell/Radial Modifier",
                "key.totality.open_grimoire".equals(ModKeybinds.OPEN_GRIMOIRE.getName())
                        && "key.totality.block".equals(ModKeybinds.BLOCK.getName())
                        && !ModKeybinds.OPEN_GRIMOIRE.getName().equals(ModKeybinds.BLOCK.getName())
                        && !ModKeybinds.OPEN_GRIMOIRE.getName().equals(ModKeybinds.USE_ABILITY.getName())
                        && !ModKeybinds.OPEN_GRIMOIRE.getName().equals(ModKeybinds.USE_SPELL.getName())
                        && !ModKeybinds.OPEN_GRIMOIRE.getName().equals(ModKeybinds.RADIAL_MODIFIER.getName())
                        && !ModKeybinds.BLOCK.getName().equals(ModKeybinds.RADIAL_MODIFIER.getName()),
                "grimoire=" + ModKeybinds.OPEN_GRIMOIRE.getName() + ", block=" + ModKeybinds.BLOCK.getName());

        r.summarize();
    }
}
