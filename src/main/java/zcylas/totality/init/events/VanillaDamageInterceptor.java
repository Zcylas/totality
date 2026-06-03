package zcylas.totality.init.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.combat.damage.*;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.mob.stats.MobCombatStats;
import zcylas.totality.api.mob.stats.MobCombatStatsHolder;
import zcylas.totality.api.mob.stats.MobStatBlock;
import zcylas.totality.api.rpg.combat.ArmorClass;
import zcylas.totality.api.rpg.combat.CombatResolver;
import zcylas.totality.api.rpg.combat.PowerAttackManager;
import zcylas.totality.api.rpg.combat.weapon.VanillaWeaponStats;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.PlayerStats;
import zcylas.totality.api.rpg.stats.StatsComponents;
import zcylas.totality.client.combat.CombatTextEntry;
import zcylas.totality.networking.combat.CombatTextPayload;
import zcylas.totality.networking.notification.SendNotificationPayload;

public final class VanillaDamageInterceptor {

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {

            // ── Already processed by Totality — allow through ─────────────────
            if (TotalityDamage.isProcessing(entity)) return true;

            // ── Client side — skip ────────────────────────────────────────────
            if (!(entity.level() instanceof ServerLevel)) return true;

            // ── Player attacking with Totality weapon — already handled ───────
            if (isTotalityWeaponAttack(source)) return true;

            // ── Map vanilla source → TotalityDamageType ───────────────────────
            LivingEntity attacker = getAttacker(source);
            TotalityDamageType dmgType = mapDamageType(source, attacker);

            // ── Mob attacking player → roll attack vs AC ──────────────────────
            if (entity instanceof ServerPlayer player
                    && attacker != null
                    && !(attacker instanceof Player)) {

                if (attacker instanceof MobCombatStatsHolder holder
                        && holder.totality$getMobCombatStats().isInitialized()) {

                    MobCombatStats mobStats = holder.totality$getMobCombatStats();
                    int playerAC = ArmorClass.calculate(player);
                    int d20 = attacker.getRandom().nextInt(20) + 1;

                    if (d20 == 20) {
                        // Critical hit — double damage + bonus from STR
                        float critAmount = amount * 2
                                + MobStatBlock.modifier(mobStats.getStat(
                                mobStats.getStatBlock() != null
                                        ? mobStats.getStatBlock().getAttackAbilityScore()
                                        : AbilityScore.STR));
                        TotalityDamage.hurt(player, attacker, dmgType,
                                Math.max(1, critAmount), new DamageFlags[0]);
                        return false;
                    }

                    if (d20 == 1 || d20 + mobStats.getAttackBonus() < playerAC) {
                        sendMissAt(attacker, player); // ← add this
                        return false;
                    }

                    // Hit — base damage + STR modifier
                    float hitAmount = amount
                            + MobStatBlock.modifier(mobStats.getStat(
                            mobStats.getStatBlock() != null
                                    ? mobStats.getStatBlock().getAttackAbilityScore()
                                    : AbilityScore.STR));
                    TotalityDamage.hurt(player, attacker, dmgType,
                            Math.max(1, hitAmount), new DamageFlags[0]);
                    return false;
                }

                // Mob has no stat block — still route through TotalityDamage
                // so resistances and combat text apply
                TotalityDamage.hurt(player, attacker, dmgType, amount, new DamageFlags[0]);
                return false;
            }

            // ── Environmental damage on player (fall, fire, etc.) ─────────────
            if (entity instanceof ServerPlayer player && attacker == null) {
                TotalityDamage.hurt(player, null, dmgType, amount, new DamageFlags[0]);
                return false;
            }

            // ── Player taking damage from another player ──────────────────────
            if (entity instanceof ServerPlayer player
                    && attacker instanceof ServerPlayer) {
                TotalityDamage.hurt(player, attacker, dmgType, amount, new DamageFlags[0]);
                return false;
            }

            // ── Mob taking environmental damage ───────────────────────────────
            // Route through TotalityDamage so mob resistances/immunities apply
            // ── Mob taking environmental damage ───────────────────────────────
            if (!(entity instanceof Player) && attacker == null) {
                TotalityDamage.hurt(entity, null, dmgType, amount, new DamageFlags[0]);
                return false;
            }

            // ── Player attacking mob ──────────────────────────────────────────
            // ── Player attacking mob ──────────────────────────────────────────
            // ── Player attacking mob ──────────────────────────────────────────
            if (!(entity instanceof Player) && attacker instanceof ServerPlayer player) {
                ItemStack weapon = player.getMainHandItem();
                VanillaWeaponStats.WeaponData data = VanillaWeaponStats.get(weapon.getItem());
                if (data == null) data = VanillaWeaponStats.unarmed();

                PlayerStats stats = StatsComponents.getStats(player);
                AbilityScore ability = stats != null ? data.resolveAbility(stats) : data.ability();

                RollType rollType = PowerAttackManager.clearPowerAttack(player.getUUID())
                        ? RollType.ADVANTAGE : RollType.NORMAL;

                String weaponName = weapon.isEmpty()
                        ? "Unarmed Strike" : weapon.getHoverName().getString();

                CombatResolver.resolveAttack(player, entity,
                        ability, true, rollType,
                        1, data.damageDie(), data.damageType(), weaponName);
                return false;
            }

            return true; // Let vanilla handle everything else
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** True if damage came from a Totality weapon attack (already handled). */
    private static boolean isTotalityWeaponAttack(DamageSource source) {
        // Totality weapons call TotalityDamage.hurt() directly via CombatResolver.
        // Those calls set the PROCESSING flag, so they're caught above.
        // This check handles any weapon that slips through.
        return false; // PROCESSING flag handles this
    }

    private static LivingEntity getAttacker(DamageSource source) {
        if (source.getDirectEntity() instanceof LivingEntity le) return le;
        if (source.getEntity() instanceof LivingEntity le) return le;
        return null;
    }

    private static TotalityDamageType mapDamageType(DamageSource source,
                                                    LivingEntity attacker) {
        // Mob with stat block — use defined attack damage type
        if (attacker instanceof MobCombatStatsHolder holder
                && holder.totality$getMobCombatStats().isInitialized()) {
            MobStatBlock block = holder.totality$getMobCombatStats().getStatBlock();
            if (block != null) return getMobAttackDamageType(block);
        }

        // Map by vanilla damage type key
        String path = source.typeHolder().unwrapKey()
                .map(k -> k.identifier().getPath()).orElse("");

        return switch (path) {
            // Player unarmed / vanilla weapon fallback
            case "player_attack"             -> DamageTypes.BLUDGEONING;

            // Mob melee fallback — MobStatBlock overrides this above
            case "mob_attack"                -> DamageTypes.BLUDGEONING;

            // Projectiles
            case "mob_projectile"            -> DamageTypes.FORCE;    // Blaze/Ghast — not bludgeoning
            case "wind_charge"               -> DamageTypes.FORCE;    // Breeze attack
            case "arrow", "trident"          -> DamageTypes.PIERCING;
            case "fireball"                  -> DamageTypes.FIRE;

            // Physical/environmental
            case "cactus"                    -> DamageTypes.PIERCING;
            case "sweet_berry_bush"          -> DamageTypes.SLASHING; // thorns, not piercing
            case "fall", "fly_into_wall",
                 "cramming", "falling_block",
                 "anvil", "in_wall", "drown" -> DamageTypes.BLUDGEONING;

            // Elemental
            case "in_fire", "on_fire",
                 "lava", "hot_floor"         -> DamageTypes.FIRE;
            case "lightning_bolt"            -> DamageTypes.LIGHTNING;
            case "freeze"                    -> DamageTypes.FROST;

            // Magical
            case "magic", "indirect_magic"   -> DamageTypes.ARCANE;
            case "explosion",
                 "player_explosion"          -> DamageTypes.FORCE;
            case "sonic_boom"                -> DamageTypes.SONIC;

            // Necrotic
            case "wither", "wither_skull",
                 "starve"                    -> DamageTypes.NECROTIC;
            case "dragon_breath"             -> DamageTypes.VOID;     // Ender Dragon = void energy

            default                          -> DamageTypes.BLUDGEONING;
        };
    }

    private static TotalityDamageType getMobAttackDamageType(MobStatBlock block) {
        if (block.getAttackDamageTypeName() != null) {
            TotalityDamageType type = DamageTypeRegistry.get(
                    Identifier.fromNamespaceAndPath("totality", block.getAttackDamageTypeName()));
            return type != null ? type : DamageTypes.BLUDGEONING;
        }
        return block.isRanged() ? DamageTypes.PIERCING : DamageTypes.BLUDGEONING;
    }

    public static void sendMissAt(LivingEntity attacker, ServerPlayer victim) {
        Vec3 pos = attacker.position().add((Math.random() - 0.5) * 1.5, 1.0, 0);
        CombatTextPayload payload = new CombatTextPayload(
                CombatTextEntry.TextType.DAMAGE,
                DamageTypes.BLUDGEONING.getId(),
                0f, "Miss",
                pos.x, pos.y, pos.z,
                false, false,
                attacker.getId(), victim.getId()
        );
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(victim, payload);
    }

    private VanillaDamageInterceptor() {}
}