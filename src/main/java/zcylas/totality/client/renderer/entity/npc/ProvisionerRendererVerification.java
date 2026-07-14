package zcylas.totality.client.renderer.entity.npc;

import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.entity.npc.NpcGender;

/**
 * Dev-environment-gated self-test for the Provisioner male/female texture-selection logic (Phase
 * 4, Part A/I) — same {@link VerificationReporter}-based convention as the server-side suites, but
 * registered from {@code TotalityClient.onInitializeClient()} (client-only entry point) rather
 * than a {@code ServerLifecycleEvents} hook: {@link ProvisionerNpcRenderer} extends a client-only
 * {@code HumanoidMobRenderer}, and referencing it from a class that ALSO runs on
 * {@code SERVER_STARTED} would risk a {@code NoClassDefFoundError} on a dedicated server if that
 * environment's separate, pre-existing classloading issue (explicitly out of scope this phase) is
 * ever fixed independently. Runs once, synchronously, right after client renderer registration —
 * no server/world needs to exist yet, since {@link ProvisionerNpcRenderer#textureFor} is pure.
 */
public final class ProvisionerRendererVerification {

    private ProvisionerRendererVerification() {}

    public static void runIfDev() {
        if (!VerificationReporter.isDevEnvironment()) return;

        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "ProvisionerRendererVerification");

        Identifier male = ProvisionerNpcRenderer.textureFor(NpcGender.MALE);
        r.check("Male Provisioner resolves to the male texture",
                male.getPath().equals("textures/entity/npc/provisioner/male.png"), "resolved=" + male);

        Identifier female = ProvisionerNpcRenderer.textureFor(NpcGender.FEMALE);
        r.check("Female Provisioner resolves to the female texture",
                female.getPath().equals("textures/entity/npc/provisioner/female.png"), "resolved=" + female);

        boolean distinctFromBanker = !male.getPath().contains("banker") && !female.getPath().contains("banker");
        r.check("Provisioner textures are its own, never the Banker's", distinctFromBanker,
                "male=" + male + ", female=" + female);

        Identifier fallback = ProvisionerNpcRenderer.textureFor(null);
        r.check("A null/unavailable gender falls back to a safe default texture instead of throwing",
                fallback != null, "fallback=" + fallback);

        r.summarize();
    }
}
