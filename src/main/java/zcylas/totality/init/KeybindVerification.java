package zcylas.totality.init;

import org.lwjgl.glfw.GLFW;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.VerificationReporter;

/**
 * Dev-environment-gated self-test for the Radial Modifier key mapping (Phase 4 correction pass,
 * Part D) — same {@link VerificationReporter} convention as the server-side suites, but run
 * synchronously from {@code TotalityClient.onInitializeClient()} (client-only entry point):
 * {@code net.minecraft.client.KeyMapping} is a client-only type, so this can never run on a
 * dedicated server, mirroring
 * why {@code ProvisionerRendererVerification} is registered the same way.
 *
 * <p>What this suite CANNOT verify (documented rather than silently skipped): the actual chord
 * behavior in {@link TotalityKeybindHandlers} — whether holding Z keeps a channeled ability
 * active, whether Modifier+Z opens the radial without activating, whether releasing keys clears
 * state correctly, and whether a rebound key is honored during real play — all require driving
 * real keyboard input against a live client window, which this environment has no tool to do
 * (the same standing limitation documented throughout the Provisioner phases). Those items remain
 * Stefan's manual checklist, items 16-25.
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

        r.summarize();
    }
}
