package zcylas.totality.client.resource.parity;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.client.ClientResourceQueryResult;
import zcylas.totality.api.rpg.resources.client.ClientResourceService;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityObservation;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityReport;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityReportAssembler;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityReportInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Phase 3B-3 development-only on-demand parity inspection: {@code /totalitydebug resource parity}.
 * Registered only when {@link FabricLoader#isDevelopmentEnvironment()} is {@code true} — the same
 * dev-environment gate already used elsewhere in this codebase (see {@code VerificationReporter
 * .isDevEnvironment()}), never a permission/operator check, config flag, entitlement, debug item, or
 * keybind. In a normal (non-development) launch, this class's {@link
 * #registerIfDevelopmentEnvironment()} never calls {@code ClientCommandRegistrationCallback.EVENT
 * .register(...)} at all, so the command structurally does not exist in the client dispatcher — not
 * merely hidden or permission-gated.
 *
 * <h2>Command-root correction — manual-validation finding (third correction pass)</h2>
 * This command was originally registered under the shared {@code totality} literal (the same root as
 * the existing server-side {@code /totality ...} command tree, {@code TotalityCommands.java}). Manual
 * validation confirmed this was a genuine runtime defect, not merely an unverified claim: Fabric's
 * client-command dispatch ({@code ClientCommandInternals}) commits to a client-registered root literal
 * once it matches, and does not fall back to forwarding the remaining input to the server when no
 * client-side child matches — so registering anything at all under {@code totality} client-side
 * caused every *other* server-side {@code /totality ...} branch (e.g. {@code showancestry},
 * {@code ancestry}, {@code wallet}) to fail locally with a client-side Brigadier syntax exception
 * ("Incorrect argument for command at position 9: totality <--[HERE]") instead of ever reaching the
 * server. This is now registered under the entirely distinct root literal {@code totalitydebug},
 * which no server command begins with, so the client dispatcher never intercepts anything server-
 * side — confirmed by this package's own source-scan regression test that the production client
 * command registers only {@code totalitydebug} and never registers {@code totality} at all.
 *
 * <p>This class does not modify, and is never referenced by, {@code TotalityCommands.java} or any
 * other server command registration — confirmed by this package's own source-scan regression test —
 * so no server command file is altered by this addition. Whether the corrected {@code totalitydebug}
 * root and the server's own {@code /totality} tree coexist correctly at runtime, and whether every
 * existing server-side {@code /totality} branch now executes normally again, has not been re-verified
 * in a running client by this automated session — see the manual-validation checklist in the Phase
 * 3B-3 implementation report.
 *
 * <h2>Pure/impure boundary — external-review correction (Blocker 1)</h2>
 * This class is the <b>only</b> code in the Phase 3B-3 slice that ever touches {@link Identifier},
 * {@link PlayerResourceIds}, {@link ClientResourceService}, {@link ClientResourceParityObservations},
 * {@link ClientResourceQueryResult}, or {@link ClientResourceParityObservation}. Before calling the
 * pure {@link ClientResourceParityReportAssembler}, it converts each of those live, Minecraft-carrying
 * results into a plain {@link ClientResourceParityReportInput} — the assembler and every type it
 * consumes are Minecraft/Fabric-independent (see that package's own import-scan regression test).
 * This class also owns the canonical seven-Resource ordering ({@link #NATIVE_RESOURCE_IDS}/
 * {@link #SHADOW_PARITY_RESOURCE_IDS}) — the pure assembler holds no ordering knowledge of its own.
 *
 * <h2>Read-only, no second parity algorithm</h2>
 * {@link #gatherReport()} only ever reads two already-existing, already-tested boundaries —
 * {@link ClientResourceService#INSTANCE} (the Phase 3B-1 trusted client façade, for
 * Health/Food/Breath) and {@link ClientResourceParityObservations} (the Phase 3B-2B/2C read-only
 * shadow-parity snapshot, for Mana/Stamina/Standard Spell Slots/Rage) — and converts their already-
 * resolved, already-immutable results into the pure input vocabulary above. It never calls an apply/
 * clear/resync-request/mutate method on either boundary, never re-derives a comparison itself, and
 * never touches {@code ClientResourceParityLogObserver}'s persistent-mismatch episode memory at all —
 * repeated invocation cannot create or suppress a bounded logging entry/recovery event.
 *
 * <h2>Per-Resource access-failure containment — first correction pass (Blocker 3)</h2>
 * Each of the seven Resources' native query / shadow observation lookup is wrapped in its own narrow
 * {@code catch (RuntimeException)} — never {@code Throwable} — so a single failing reader or
 * observation accessor can never abort the report for the other six Resources. Only a bounded
 * exception simple-class name survives into the report — never a message, stack trace, coordinate,
 * UUID, path, or arbitrary object text. The narrow package-private {@link
 * #gatherReport(Function, Function)} overload exists specifically so this package's own tests can
 * inject a deliberately-throwing native-query/shadow-lookup function without needing a running client
 * or a real reader registered; production code always calls the zero-argument {@link #gatherReport()}
 * overload, which wires the real {@code ClientResourceService.INSTANCE::query} and
 * {@code ClientResourceParityObservations::latest} accessors.
 *
 * <h2>Access vs. conversion failure — second correction pass (Blocker 2)</h2>
 * {@link #gatherNative}/{@link #gatherShadow} each now use <b>two separate</b> {@code try}/
 * {@code catch (RuntimeException)} blocks, not one: the first wraps only the accessor call itself
 * (a failure there produces {@link ClientResourceParityReportInput.Native.Status.AccessError}/
 * {@link ClientResourceParityReportInput.ShadowParity.Status.AccessError}); the second wraps only the
 * separate step of converting that call's returned result into the pure input vocabulary (a failure
 * there produces the distinct {@link ClientResourceParityReportInput.Native.Status.ConversionError}/
 * {@link ClientResourceParityReportInput.ShadowParity.Status.ConversionError} instead). The first
 * correction pass's implementation used a single {@code catch} spanning both steps, which made the
 * report's own documented claim ("{@code ACCESS_ERROR} means the accessor threw") false whenever a
 * conversion failure actually occurred — this pass closes that gap structurally, not just in wording.
 */
@Environment(EnvType.CLIENT)
public final class ClientResourceParityInspectionCommand {

    /** Canonical, deterministic inspection order — Health, Food, Breath — matching the Phase 3B-3
     *  task's required ordering exactly. Owned here, not by the pure assembler (Blocker 1). */
    static final List<Identifier> NATIVE_RESOURCE_IDS = List.of(
            PlayerResourceIds.HEALTH, PlayerResourceIds.FOOD, PlayerResourceIds.BREATH);

    /** Canonical, deterministic inspection order — Mana, Stamina, Standard Spell Slots, Rage. */
    static final List<Identifier> SHADOW_PARITY_RESOURCE_IDS = List.of(
            PlayerResourceIds.MANA, PlayerResourceIds.STAMINA, PlayerResourceIds.SPELL_SLOTS, PlayerResourceIds.RAGE);

    private ClientResourceParityInspectionCommand() {}

    /** The command's own root literal — deliberately distinct from {@code totality} (the existing
     *  server-side command tree's root) so the client dispatcher never has any reason to intercept a
     *  server-side {@code /totality ...} command. See the class Javadoc's "Command-root correction"
     *  section for the confirmed manual-validation defect this fixes. */
    static final String ROOT_LITERAL = "totalitydebug";

    /** Registers {@code /totalitydebug resource parity} only in a Fabric development environment. Safe
     *  to call unconditionally from {@code TotalityClient.onInitializeClient()} — it is a no-op (no
     *  event listener is ever added) outside of development. Never registers anything under the
     *  {@code totality} literal — that root is exclusively the existing server-side command tree's. */
    public static void registerIfDevelopmentEnvironment() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        ClientCommands.literal(ROOT_LITERAL)
                                .then(ClientCommands.literal("resource")
                                        .then(ClientCommands.literal("parity")
                                                .executes(ClientResourceParityInspectionCommand::execute)))));
    }

    private static int execute(CommandContext<FabricClientCommandSource> context) {
        ClientResourceParityReport report = gatherReport();
        FabricClientCommandSource source = context.getSource();
        for (String line : report.chatLines()) {
            source.sendFeedback(Component.literal(line));
        }
        return 1;
    }

    /** Production entry point: wires the real trusted façade and the real parity observation
     *  snapshot into the testable {@link #gatherReport(Function, Function)} seam. */
    static ClientResourceParityReport gatherReport() {
        return gatherReport(ClientResourceService.INSTANCE::query, ClientResourceParityObservations::latest);
    }

    /**
     * The impure gathering step, parameterized over the two live accessors (Blocker 3's testable
     * seam). For each of the seven canonical Resources independently: invokes the corresponding
     * accessor inside its own {@code catch (RuntimeException)}, converts a successful result into the
     * pure input vocabulary, and converts a failure into a bounded {@code AccessError} input naming
     * only the exception's simple class name — never letting one Resource's failure prevent the other
     * six from being gathered. Neither accessor is ever called more than once per Resource, and
     * neither is ever a mutating call.
     */
    static ClientResourceParityReport gatherReport(
            Function<Identifier, ClientResourceQueryResult> nativeQuery,
            Function<Identifier, Optional<ClientResourceParityObservation>> shadowLookup
    ) {
        List<ClientResourceParityReportInput> inputs = new ArrayList<>(NATIVE_RESOURCE_IDS.size() + SHADOW_PARITY_RESOURCE_IDS.size());
        for (Identifier id : NATIVE_RESOURCE_IDS) {
            inputs.add(gatherNative(id, nativeQuery));
        }
        for (Identifier id : SHADOW_PARITY_RESOURCE_IDS) {
            inputs.add(gatherShadow(id, shadowLookup));
        }
        return ClientResourceParityReportAssembler.assemble(inputs);
    }

    /**
     * External-review correction (second pass, Blocker 2): the accessor call and the result-
     * conversion step are now two separate {@code try}/{@code catch (RuntimeException)} blocks, never
     * one catch spanning both — a failure in the first produces {@code AccessError}; a failure in the
     * second (which can only happen once a result has already been obtained — e.g. the accessor
     * returned {@code null} and {@link #toNativeInput} then threw a {@code NullPointerException}
     * converting it) produces the distinct {@code ConversionError} instead. The two must never share a
     * catch block, or the report could never truthfully distinguish "the accessor itself threw" from
     * "the accessor answered, but converting the answer threw."
     */
    private static ClientResourceParityReportInput gatherNative(Identifier id, Function<Identifier, ClientResourceQueryResult> nativeQuery) {
        String resourceId = id.toString();
        ClientResourceQueryResult result;
        try {
            result = nativeQuery.apply(id);
        } catch (RuntimeException accessFailure) {
            return new ClientResourceParityReportInput.Native(
                    resourceId, new ClientResourceParityReportInput.Native.Status.AccessError(boundedExceptionName(accessFailure)));
        }
        try {
            return toNativeInput(resourceId, result);
        } catch (RuntimeException conversionFailure) {
            return new ClientResourceParityReportInput.Native(
                    resourceId, new ClientResourceParityReportInput.Native.Status.ConversionError(boundedExceptionName(conversionFailure)));
        }
    }

    /** See {@link #gatherNative}'s Javadoc — the shadow-parity counterpart, same access-vs-conversion
     *  separation. */
    private static ClientResourceParityReportInput gatherShadow(Identifier id, Function<Identifier, Optional<ClientResourceParityObservation>> shadowLookup) {
        String resourceId = id.toString();
        boolean remainingLabel = id.equals(PlayerResourceIds.SPELL_SLOTS);
        Optional<ClientResourceParityObservation> observationLookup;
        try {
            observationLookup = shadowLookup.apply(id);
        } catch (RuntimeException accessFailure) {
            return new ClientResourceParityReportInput.ShadowParity(
                    resourceId, new ClientResourceParityReportInput.ShadowParity.Status.AccessError(boundedExceptionName(accessFailure)), remainingLabel);
        }
        try {
            return toShadowInput(resourceId, remainingLabel, observationLookup);
        } catch (RuntimeException conversionFailure) {
            return new ClientResourceParityReportInput.ShadowParity(
                    resourceId, new ClientResourceParityReportInput.ShadowParity.Status.ConversionError(boundedExceptionName(conversionFailure)), remainingLabel);
        }
    }

    private static ClientResourceParityReportInput.Native toNativeInput(String resourceId, ClientResourceQueryResult result) {
        return switch (result) {
            case ClientResourceQueryResult.Scalar scalar -> new ClientResourceParityReportInput.Native(
                    resourceId, new ClientResourceParityReportInput.Native.Status.Available(
                            scalar.currentUnits(), scalar.maximumUnits(), scalar.overflowUnits(), scalar.unitScale(), scalar.trust()));
            // A native Resource is never PARTITIONED_POOL-modeled; the façade's own defense-in-depth
            // (ClientResourceService.validateShape) already turns this into an Unavailable(MODEL_MISMATCH)
            // before this code ever runs — this arm exists only because ClientResourceQueryResult is a
            // sealed interface with three permitted shapes, not because production ever reaches it.
            case ClientResourceQueryResult.Partitioned partitioned -> new ClientResourceParityReportInput.Native(
                    resourceId, new ClientResourceParityReportInput.Native.Status.Unavailable(
                            zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason.MODEL_MISMATCH));
            case ClientResourceQueryResult.Unavailable unavailable -> new ClientResourceParityReportInput.Native(
                    resourceId, new ClientResourceParityReportInput.Native.Status.Unavailable(unavailable.reason()));
        };
    }

    private static ClientResourceParityReportInput.ShadowParity toShadowInput(
            String resourceId, boolean remainingLabel, Optional<ClientResourceParityObservation> observationLookup) {
        if (observationLookup.isEmpty()) {
            return new ClientResourceParityReportInput.ShadowParity(
                    resourceId, new ClientResourceParityReportInput.ShadowParity.Status.NoObservationYet(), remainingLabel);
        }
        ClientResourceParityObservation observation = observationLookup.get();
        return new ClientResourceParityReportInput.ShadowParity(
                resourceId,
                new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                        observation.classification(), observation.genericSummary(), observation.legacySummary(),
                        observation.firstMismatchTick(), observation.lastObservedTick()),
                remainingLabel);
    }

    /** Never a message, never a stack trace — only the exception's own simple class name, bounded to
     *  {@link ClientResourceParityReportInput#MAX_TOKEN_LENGTH} by the input record's own compact
     *  constructor. This truncation is redundant with that constructor's own bound but kept here too
     *  so a pathologically long class name is never even handed to the pure layer at full length. */
    private static String boundedExceptionName(RuntimeException exception) {
        String simpleName = exception.getClass().getSimpleName();
        if (simpleName.isEmpty()) {
            simpleName = exception.getClass().getName();
        }
        return simpleName.length() > ClientResourceParityReportInput.MAX_TOKEN_LENGTH
                ? simpleName.substring(0, ClientResourceParityReportInput.MAX_TOKEN_LENGTH)
                : simpleName;
    }
}
