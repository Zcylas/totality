package zcylas.totality.api.entitlement.integration;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.dialogue.DialogueComponents;
import zcylas.totality.api.entitlement.AuthorizationPath;
import zcylas.totality.api.entitlement.EntitlementActions;
import zcylas.totality.api.entitlement.EntitlementAuditEntry;
import zcylas.totality.api.entitlement.EntitlementDecision;
import zcylas.totality.api.entitlement.EntitlementGrant;
import zcylas.totality.api.entitlement.EntitlementKey;
import zcylas.totality.api.entitlement.EntitlementLedger;
import zcylas.totality.api.entitlement.EntitlementMutationResult;
import zcylas.totality.api.entitlement.EntitlementQueryContext;
import zcylas.totality.api.entitlement.EntitlementService;
import zcylas.totality.api.entitlement.GrantSourceRef;
import zcylas.totality.api.entitlement.GrantSourceTypes;
import zcylas.totality.api.entitlement.PermanentEntitlementFact;
import zcylas.totality.api.entitlement.PlayerEntitlementState;
import zcylas.totality.api.entitlement.requirement.RequirementEvaluation;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;
import zcylas.totality.api.rpg.classes.ClassComponents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@code /totality entitlement ...} — operator diagnostics and debug tooling (canonical §13 M0/M10).
 * Debug grants are session-only and non-progression; the permanent admin unlock is a separate, audited
 * administrative mutation that is never a debug-grant conversion.
 */
public final class EntitlementCommands {

    public static final Identifier ADMIN_COMMAND = Identifier.fromNamespaceAndPath("totality", "admin_command");
    public static final GrantSourceRef ADMIN_SOURCE = GrantSourceRef.of(GrantSourceTypes.ADMIN, ADMIN_COMMAND);
    public static final Identifier DEBUG_COMMAND = Identifier.fromNamespaceAndPath("totality", "debug_command");

    private EntitlementCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("entitlement")
                .then(Commands.literal("audit")
                        .executes(ctx -> audit(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> audit(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("explain")
                        .then(Commands.argument("type", IdentifierArgument.id())
                                .then(Commands.argument("content", IdentifierArgument.id())
                                        .then(Commands.argument("action", IdentifierArgument.id())
                                                .executes(EntitlementCommands::explain)))))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("type", IdentifierArgument.id())
                                .then(Commands.argument("content", IdentifierArgument.id())
                                        .executes(ctx -> adminUnlock(ctx, key(ctx))))))
                .then(Commands.literal("revoke")
                        .then(Commands.argument("type", IdentifierArgument.id())
                                .then(Commands.argument("content", IdentifierArgument.id())
                                        .executes(ctx -> adminRevoke(ctx, key(ctx))))))
                .then(Commands.literal("debuggrant")
                        .then(Commands.argument("type", IdentifierArgument.id())
                                .then(Commands.argument("content", IdentifierArgument.id())
                                        .executes(ctx -> debugGrant(ctx, key(ctx))))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("universal_spells")
                                .executes(ctx -> reply(ctx, "Debug universal spell access: " + DebugSpellAccessProvider.isEnabled()))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                            DebugSpellAccessProvider.setEnabled(ctx.getSource().getServer(), enabled);
                                            return reply(ctx, "Debug universal spell access " + (enabled ? "ENABLED" : "disabled")
                                                    + " — debug-only, non-progression, not saved.");
                                        }))));
    }

    private static EntitlementKey key(CommandContext<CommandSourceStack> ctx) {
        return EntitlementKey.of(IdentifierArgument.getId(ctx, "type"), IdentifierArgument.getId(ctx, "content"));
    }

    // ── Admin / debug mutations ───────────────────────────────────────────────

    /** Permanent admin unlock: audited, marked non-progression. */
    public static int adminUnlock(CommandContext<CommandSourceStack> ctx, EntitlementKey key) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EntitlementMutationResult result = EntitlementService.INSTANCE.grantPermanentFact(player, key,
                PermanentEntitlementFact.UNLOCKED, ADMIN_SOURCE, ADMIN_COMMAND, false);
        if (result.isRejected()) {
            return fail(ctx, "Rejected: " + result.message() + " — use /totality entitlement debuggrant for session-only test access.");
        }
        Totality.LOGGER.warn("[Entitlement] Admin permanent unlock of {} for {} by {}", key, player.getName().getString(),
                ctx.getSource().getTextName());
        return reply(ctx, (result.changed() ? "Permanently unlocked " : "Already unlocked ") + key
                + " (admin, audited, non-progression).");
    }

    /** Revokes permanent facts (audited) and this command's debug grants. Source-bound grants are untouched. */
    public static int adminRevoke(CommandContext<CommandSourceStack> ctx, EntitlementKey key) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EntitlementService service = EntitlementService.INSTANCE;
        boolean changed = false;
        for (PermanentEntitlementFact fact : PermanentEntitlementFact.values()) {
            changed |= service.revokePermanentFact(player, key, fact, ADMIN_SOURCE, ADMIN_COMMAND).changed();
        }
        changed |= service.removeGrant(player, EntitlementGrant.debugSession(key, DEBUG_COMMAND).grantId(),
                GrantSourceRef.of(GrantSourceTypes.DEBUG, DEBUG_COMMAND)).changed();
        EntitlementDecision decision = service.query(player, key, EntitlementActions.USE, EntitlementQueryContext.Purpose.DEBUG_EXPLAIN);
        List<String> remaining = decision.snapshot().sources().stream().map(s -> s.source().toString()).toList();
        return reply(ctx, (changed ? "Revoked " : "Nothing to revoke for ") + key
                + (remaining.isEmpty() ? "." : ". Still granted by: " + String.join(", ", remaining)));
    }

    public static int debugGrant(CommandContext<CommandSourceStack> ctx, EntitlementKey key) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EntitlementMutationResult result = EntitlementService.INSTANCE.addGrant(player, EntitlementGrant.debugSession(key, DEBUG_COMMAND));
        if (result.isRejected()) return fail(ctx, "Rejected: " + result.message());
        return reply(ctx, "Session debug grant for " + key + " (non-progression, ends at logout).");
    }

    // ── Diagnostics ───────────────────────────────────────────────────────────

    private static int explain(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EntitlementKey key = key(ctx);
        Identifier action = IdentifierArgument.getId(ctx, "action");
        EntitlementDecision d = EntitlementService.INSTANCE.query(player, key, action, EntitlementQueryContext.Purpose.DEBUG_EXPLAIN);
        List<String> lines = new ArrayList<>();
        lines.add(key + " " + action + " → " + d.kind() + " (" + d.primaryReasonCode() + ", display " + d.displayState()
                + ", revision " + d.playerEntitlementRevision() + ")");
        for (AuthorizationPath path : d.successfulPaths()) {
            lines.add("  path " + path.pathTypeId() + path.source().map(s -> " from " + s).orElse("")
                    + (path.durable() ? " [durable]" : "") + (path.temporary() ? " [source-bound]" : "")
                    + (path.debug() ? " [DEBUG]" : ""));
        }
        for (RequirementEvaluation failure : d.failures()) {
            for (RequirementEvaluation leaf : failure.failures()) {
                lines.add("  failed " + leaf.conditionId().map(Identifier::toString).orElse("(composite)") + " "
                        + leaf.reasonCode() + " [" + leaf.disclosurePolicy() + "]");
            }
        }
        d.snapshot().sources().forEach(s -> lines.add("  source " + s.source() + (s.temporary() ? " (grant)" : " (permanent)")));
        lines.forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal(line), false));
        return 1;
    }

    /** Read-only M0 report: legacy ability data, selections, class/ancestry, phone flags, ledger, quarantine. */
    private static int audit(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        Consumer<String> out = line -> ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        EntitlementService service = EntitlementService.INSTANCE;
        PlayerEntitlementState state = service.state(player);
        EntitlementLedger ledger = state.ledger();
        AbilityComponent abilities = AbilityComponents.ABILITIES.get((ComponentProvider) player);

        out.accept("Entitlement audit — " + player.getName().getString() + " (revision " + state.revision()
                + ", data v" + ledger.dataVersion() + ", debug universal spells " + DebugSpellAccessProvider.isEnabled() + ")");
        out.accept("Legacy AbilityComponent.unlocked: " + abilities.getLegacyUnlocked());
        out.accept("Legacy ids processed: " + ledger.processedLegacyAbilityIds());
        out.accept("Accessible abilities/spells: " + abilities.getAccessibleAbilities());
        out.accept("Equipped: " + abilities.getEquippedAbility() + ", selected spell: " + abilities.getSelectedSpell()
                + ", favorites: " + abilities.getFavorites());
        out.accept("Classes: " + ClassComponents.get(player).getAllClassLevels() + ", subclasses: "
                + ClassComponents.get(player).getAllSubclassIds());
        out.accept("Species: " + AncestryComponents.get(player).getSpeciesId() + ", origin: " + AncestryComponents.get(player).getOriginId());
        out.accept("Phone flags: bank_app_unlocked=" + DialogueComponents.FLAGS.get((ComponentProvider) player).getFlag("bank_app_unlocked")
                + ", has_account=" + DialogueComponents.FLAGS.get((ComponentProvider) player).getFlag("has_account"));
        for (EntitlementKey key : ledger.keysWithFacts()) {
            ledger.facts(key).forEach((fact, record) -> out.accept("  fact " + fact + " " + key + " from " + record.primarySource()
                    + " via " + record.acquisitionMethodId() + (record.progressionEligible() ? "" : " [non-progression]")));
        }
        state.providerGrants().forEach((provider, grants) -> grants.values().forEach(g ->
                out.accept("  grant " + g.key() + " from " + g.source() + " (provider " + provider + ")")));
        state.sessionGrants().values().forEach(g -> out.accept("  session grant " + g.key() + " from " + g.source()
                + (g.isDebug() ? " [DEBUG]" : "")));
        ledger.persistentGrants().values().forEach(g -> out.accept("  persisted grant " + g.key() + " from " + g.source()
                + " " + g.lifetime()));
        state.allSuspensions().forEach(s -> out.accept("  suspension " + s.key() + " " + s.reasonCode() + " from " + s.source()));
        state.failedProviders().forEach((id, f) -> out.accept("  UNVERIFIED provider " + id + " (" + f.count()
                + " failures since " + f.firstFailureUtc() + "): " + f.error()));
        state.withheldGrants().forEach(g -> out.accept("  withheld grant " + g.key() + " from " + g.source()));
        state.contributorFailures().forEach((id, f) -> out.accept("  contributor failure " + id + " (" + f.count()
                + "x, last " + f.lastFailureUtc() + "): " + f.error()));
        ledger.orphaned().values().forEach(o -> out.accept("  QUARANTINED " + o.key() + " (" + o.originId() + "): " + o.note()));
        ledger.orphanedGrants().values().forEach(g -> out.accept("  QUARANTINED grant " + g.key() + " from " + g.source()));
        if (ledger.unreadableCount() > 0) out.accept("  unreadable preserved records: " + ledger.unreadableCount());
        out.accept("Migrations applied: " + ledger.appliedMigrations());
        for (EntitlementAuditEntry entry : ledger.audit()) {
            out.accept("  audit r" + entry.revision() + " " + entry.operation() + " " + entry.key() + " by " + entry.actor()
                    + " (" + entry.reasonCode() + ") " + entry.detail());
        }
        return 1;
    }

    private static int reply(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendFailure(Component.literal(message));
        return 0;
    }
}
