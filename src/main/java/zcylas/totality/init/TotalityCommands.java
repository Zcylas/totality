package zcylas.totality.init;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.economy.currency.CurrencyComponents;
import zcylas.totality.api.economy.currency.CurrencyHelper;
import zcylas.totality.api.rpg.mana.PlayerManaManager;
import zcylas.totality.api.rpg.skills.core.*;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.PlayerStats;
import zcylas.totality.api.rpg.stats.StatAttributeApplier;
import zcylas.totality.api.rpg.stats.StatsComponents;
import zcylas.totality.networking.mana.SyncManaPayload;
import zcylas.totality.networking.stamina.StaminaServerTick;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import zcylas.totality.networking.stats.OpenStatusScreenPayload;

public class TotalityCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    Commands.literal("totality")
                            .requires(source -> {
                                ServerPlayer p = source.getPlayer();
                                return p != null && source.getServer().getPlayerList().isOp(p.nameAndId());
                            })

                            // ── /totality resetskills ─────────────────────────────────
                            .then(Commands.literal("resetskills")
                                    .executes(ctx -> {
                                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                                        var component = SkillsComponents.get(player);
                                        for (Skill skill : Skill.values()) {
                                            SkillData data = component.getSkills().getData(skill);
                                            data.setLevelDirectly(10);
                                            data.setXpDirectly(0);
                                        }
                                        component.sync();
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("All skills reset to level 10."), false);
                                        return 1;
                                    })
                            )

                            // ── /totality resetstats ──────────────────────────────────
                            .then(Commands.literal("resetstats")
                                    .executes(ctx -> {
                                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                                        PlayerStats stats = StatsComponents.getStats(player);

                                        // Reset all ability scores and level
                                        stats.setLevelDirectly(1);
                                        stats.setCharacterXpDirectly(0);
                                        stats.setUnspentAttributePointsDirectly(0);
                                        for (AbilityScore score : AbilityScore.values()) {
                                            stats.setScore(score, PlayerStats.BASE_SCORE);
                                        }

                                        // Reapply attribute modifiers (CON → HP etc.)
                                        StatAttributeApplier.apply(player);
                                        StatsComponents.get(player).sync();

                                        // Clamp stamina to new max (END reset → lower max stamina)
                                        int newMaxStamina = PlayerStaminaManager.getMaxStamina(player);
                                        PlayerStaminaManager.setStamina(player, newMaxStamina);
                                        StaminaServerTick.syncStamina(player);

                                        // Clamp mana to new max (INT reset → lower max mana)
                                        int newMaxMana = PlayerManaManager.getMaxMana(player);
                                        PlayerManaManager.setMana(player, newMaxMana);
                                        ServerPlayNetworking.send(player, new SyncManaPayload(
                                                PlayerManaManager.getMana(player),
                                                newMaxMana));

                                        // Clamp HP to new max (CON reset → lower max HP)
                                        player.setHealth(player.getMaxHealth());

                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("Character stats reset to defaults."), false);
                                        return 1;
                                    })
                            )

                            // ── /totality resetlevel ──────────────────────────────────
                            .then(Commands.literal("resetlevel")
                                    .executes(ctx -> {
                                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                                        PlayerStats stats = StatsComponents.getStats(player);
                                        stats.setLevelDirectly(1);
                                        stats.setCharacterXpDirectly(0);
                                        stats.setUnspentAttributePointsDirectly(0);
                                        StatsComponents.get(player).sync();
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("Character level reset to 1."), false);
                                        return 1;
                                    })
                            )

                            // ── /totality addskillxp <skill> <amount> ─────────────────
                            .then(Commands.literal("addskillxp")
                                    .then(Commands.argument("skill", StringArgumentType.word())
                                            .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                    .executes(ctx -> {
                                                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                        String skillName = StringArgumentType.getString(ctx, "skill")
                                                                .toUpperCase();
                                                        int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                        try {
                                                            Skill skill = Skill.valueOf(skillName);
                                                            SkillsComponents.get(player).addSkillXp(skill, amount);
                                                            ctx.getSource().sendSuccess(() ->
                                                                    Component.literal("Added " + amount + " XP to " +
                                                                            skill.getDisplayName() + "."), false);
                                                        } catch (IllegalArgumentException e) {
                                                            ctx.getSource().sendFailure(
                                                                    Component.literal("Unknown skill: " + skillName));
                                                        }
                                                        return 1;
                                                    })
                                            )
                                    )
                            )

                            // ── /totality addcharxp <amount> ──────────────────────────
                            .then(Commands.literal("addcharxp")
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                            .executes(ctx -> {
                                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                PlayerStats stats = StatsComponents.getStats(player);
                                                boolean leveledUp = stats.addCharacterXp(amount);
                                                StatsComponents.get(player).sync();
                                                String msg = "Added " + amount + " character XP." +
                                                        (leveledUp ? " Level up! Now level " + stats.getLevel() : "");
                                                ctx.getSource().sendSuccess(() ->
                                                        Component.literal(msg), false);
                                                return 1;
                                            })
                                    )
                            )

                            // ── /totality setstat <score> <value> ─────────────────────
                            .then(Commands.literal("setstat")
                                    .then(Commands.argument("score", StringArgumentType.word())
                                            .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                                    .executes(ctx -> {
                                                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                        String scoreName = StringArgumentType.getString(ctx, "score")
                                                                .toUpperCase();
                                                        int value = IntegerArgumentType.getInteger(ctx, "value");
                                                        try {
                                                            AbilityScore score = AbilityScore.valueOf(scoreName);
                                                            StatsComponents.getStats(player).setScore(score, value);
                                                            StatAttributeApplier.apply(player);
                                                            StatsComponents.get(player).sync();
                                                            ctx.getSource().sendSuccess(() ->
                                                                    Component.literal("Set " + score.getDisplayName() +
                                                                            " to " + value + "."), false);
                                                        } catch (IllegalArgumentException e) {
                                                            ctx.getSource().sendFailure(
                                                                    Component.literal("Unknown score: " + scoreName));
                                                        }
                                                        return 1;
                                                    })
                                            )
                                    )
                            )

                            // ── /totality setlevel <level> ────────────────────────────
                            .then(Commands.literal("setlevel")
                                    .then(Commands.argument("level", IntegerArgumentType.integer(1, PlayerStats.MAX_LEVEL))
                                            .executes(ctx -> {
                                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                int level = IntegerArgumentType.getInteger(ctx, "level");
                                                StatsComponents.getStats(player).setLevelDirectly(level);
                                                StatsComponents.get(player).sync();
                                                ctx.getSource().sendSuccess(() ->
                                                        Component.literal("Set character level to " + level + "."), false);
                                                return 1;
                                            })
                                    )
                            )
                            .then(Commands.literal("stats")
                                    .executes(ctx -> {
                                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                                        ServerPlayNetworking.send(player, new OpenStatusScreenPayload());
                                        return 1;
                                    })
                            )
                            .then(Commands.literal("wallet")
                                    .executes(ctx -> {
                                        ServerPlayer player = ctx.getSource().getPlayerOrException();

                                        var wallet = CurrencyComponents.WALLET
                                                .maybeGet((ComponentProvider) player)
                                                .orElse(null);

                                        if (wallet == null) {
                                            ctx.getSource().sendFailure(
                                                    Component.literal("Wallet component not found on player."));
                                            return 0;
                                        }

                                        long raw = wallet.getValue();
                                        var breakdown = CurrencyHelper.breakdown(raw);

                                        if (breakdown.isEmpty()) {
                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("Wallet is empty. (0 copper raw)"), false);
                                            return 1;
                                        }

                                        StringBuilder sb = new StringBuilder("Wallet: ");
                                        for (int i = 0; i < breakdown.size(); i++) {
                                            var cc = breakdown.get(i);
                                            sb.append(cc.count())
                                                    .append(" ")
                                                    .append(cc.denomination().displayName);
                                            if (i < breakdown.size() - 1) sb.append(", ");
                                        }
                                        sb.append("  (").append(raw).append(" copper raw)");

                                        String msg = sb.toString();
                                        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                                        return 1;
                                    })
                            )
                            // ── /totality unlockability <id> ──────────────────────────────
                            .then(Commands.literal("unlockability")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .executes(ctx -> {
                                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                String idStr = StringArgumentType.getString(ctx, "id");
                                                Identifier abilityId = Identifier.tryParse("totality:" + idStr);
                                                if (abilityId == null) {
                                                    ctx.getSource().sendFailure(Component.literal("Invalid ability id: " + idStr));
                                                    return 0;
                                                }
                                                zcylas.totality.api.ability.AbilityComponent comp =
                                                        zcylas.totality.api.ability.AbilityComponents.ABILITIES.get(
                                                                (ComponentProvider) player);
                                                comp.unlock(abilityId);
                                                ctx.getSource().sendSuccess(() ->
                                                        Component.literal("Unlocked ability: " + abilityId), false);
                                                return 1;
                                            })
                                    )
                            )

// ── /totality forgetability <id> ──────────────────────────────
                            .then(Commands.literal("forgetability")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .executes(ctx -> {
                                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                String idStr = StringArgumentType.getString(ctx, "id");
                                                Identifier abilityId = Identifier.tryParse("totality:" + idStr);
                                                if (abilityId == null) {
                                                    ctx.getSource().sendFailure(Component.literal("Invalid ability id: " + idStr));
                                                    return 0;
                                                }
                                                zcylas.totality.api.ability.AbilityComponent comp =
                                                        zcylas.totality.api.ability.AbilityComponents.ABILITIES.get(
                                                                (ComponentProvider) player);
                                                comp.forget(abilityId);
                                                ctx.getSource().sendSuccess(() ->
                                                        Component.literal("Forgot ability: " + abilityId), false);
                                                return 1;
                                            })
                                    )
                            )
                            // ── /totality addmasterypoint <amount> ───────────────────────
                            .then(Commands.literal("addmasterypoint")
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                            .executes(ctx -> {
                                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                var masteryComp = MasteriesComponents.get(player);
                                                masteryComp.getMasteries().addMasteryPoints(amount);
                                                masteryComp.sync();
                                                ctx.getSource().sendSuccess(() ->
                                                        Component.literal("Added " + amount + " mastery points."), false);
                                                return 1;
                                            })
                                    )
                            )

// ── /totality resetmasterypoints ─────────────────────────────
                            .then(Commands.literal("resetmasterypoints")
                                    .executes(ctx -> {
                                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                                        var masteryComp = MasteriesComponents.get(player);
                                        masteryComp.getMasteries().setMasteryPointsDirectly(0);
                                        masteryComp.sync();
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("Mastery points reset to 0."), false);
                                        return 1;
                                    })
                            )

// ── /totality learnmastery <id> ──────────────────────────────
                            .then(Commands.literal("learnmastery")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .executes(ctx -> {
                                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                String masteryId = StringArgumentType.getString(ctx, "id");
                                                var masteryComp = MasteriesComponents.get(player);

                                                // Find the mastery across all skills
                                                Mastery mastery = null;
                                                for (Skill skill : Skill.values()) {
                                                    var found = MasteryRegistry.get(skill, masteryId);
                                                    if (found.isPresent()) { mastery = found.get(); break; }
                                                }
                                                if (mastery == null) {
                                                    ctx.getSource().sendFailure(Component.literal("Unknown mastery: " + masteryId));
                                                    return 0;
                                                }

                                                // Unlock all ranks directly
                                                for (int rank = 1; rank <= mastery.getRankCount(); rank++) {
                                                    masteryComp.getMasteries().setRankDirectly(masteryId, rank);
                                                }
                                                masteryComp.sync();

                                                // Unlock associated ability if any
                                                if (mastery.getAbilityId() != null) {
                                                    Identifier abilityId = Identifier.tryParse(mastery.getAbilityId());
                                                    if (abilityId != null) {
                                                        zcylas.totality.api.ability.AbilityComponents.ABILITIES.get(
                                                                (ComponentProvider) player).unlock(abilityId);
                                                    }
                                                }

                                                ctx.getSource().sendSuccess(() ->
                                                        Component.literal("Learned mastery: " + masteryId), false);
                                                return 1;
                                            })
                                    )
                            )

// ── /totality forgetmastery <id> ─────────────────────────────
                            .then(Commands.literal("forgetmastery")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .executes(ctx -> {
                                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                String masteryId = StringArgumentType.getString(ctx, "id");
                                                var masteryComp = MasteriesComponents.get(player);
                                                masteryComp.getMasteries().setRankDirectly(masteryId, 0);
                                                masteryComp.sync();

                                                // Also forget associated ability if any
                                                Mastery mastery = null;
                                                for (Skill skill : Skill.values()) {
                                                    var found = MasteryRegistry.get(skill, masteryId);
                                                    if (found.isPresent()) { mastery = found.get(); break; }
                                                }
                                                if (mastery != null && mastery.getAbilityId() != null) {
                                                    Identifier abilityId = Identifier.tryParse(mastery.getAbilityId());
                                                    if (abilityId != null) {
                                                        zcylas.totality.api.ability.AbilityComponents.ABILITIES.get(
                                                                (ComponentProvider) player).forget(abilityId);
                                                    }
                                                }

                                                ctx.getSource().sendSuccess(() ->
                                                        Component.literal("Forgot mastery: " + masteryId), false);
                                                return 1;
                                            })
                                    )
                            )
                            // ── /totality resetall ────────────────────────────────────────
                            .then(Commands.literal("resetall")
                                    .executes(ctx -> {
                                        ServerPlayer player = ctx.getSource().getPlayerOrException();

                                        // Reset character stats
                                        PlayerStats stats = StatsComponents.getStats(player);
                                        stats.setLevelDirectly(1);
                                        stats.setCharacterXpDirectly(0);
                                        stats.setUnspentAttributePointsDirectly(0);
                                        for (AbilityScore score : AbilityScore.values()) {
                                            stats.setScore(score, PlayerStats.BASE_SCORE);
                                        }
                                        StatAttributeApplier.apply(player);
                                        StatsComponents.get(player).sync();

                                        // Reset all skills
                                        var skillsComp = SkillsComponents.get(player);
                                        for (Skill skill : Skill.values()) {
                                            SkillData data = skillsComp.getSkills().getData(skill);
                                            data.setLevelDirectly(10);
                                            data.setXpDirectly(0);
                                        }
                                        skillsComp.sync();

                                        // Reset all masteries and mastery points
                                        var masteryComp = MasteriesComponents.get(player);
                                        for (Skill skill : Skill.values()) {
                                            for (Mastery mastery : MasteryRegistry.getMasteries(skill)) {
                                                masteryComp.getMasteries().setRankDirectly(mastery.getId(), 0);
                                            }
                                        }
                                        masteryComp.getMasteries().setMasteryPointsDirectly(0);
                                        masteryComp.sync();

                                        // Reset all abilities — keep only defaults
                                        var abilityComp = AbilityComponents.ABILITIES.get(
                                                (ComponentProvider) player);
                                        for (Identifier id : new java.util.HashSet<>(abilityComp.getUnlocked())) {
                                            Ability ability = AbilityRegistry.get(id);
                                            if (ability != null && !ability.isDefault()) {
                                                abilityComp.forget(id);
                                            }
                                        }

                                        // Reset stamina and mana to new max
                                        int newMaxStamina = PlayerStaminaManager.getMaxStamina(player);
                                        PlayerStaminaManager.setStamina(player, newMaxStamina);
                                        StaminaServerTick.syncStamina(player);

                                        int newMaxMana = PlayerManaManager.getMaxMana(player);
                                        PlayerManaManager.setMana(player, newMaxMana);
                                        ServerPlayNetworking.send(player, new SyncManaPayload(newMaxMana, newMaxMana));

                                        player.setHealth(player.getMaxHealth());

                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("All RPG progress reset."), false);
                                        return 1;
                                    })
                            )


            );
        });
    }

    private TotalityCommands() {}
}