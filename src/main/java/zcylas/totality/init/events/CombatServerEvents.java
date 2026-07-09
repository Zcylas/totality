package zcylas.totality.init.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.rpg.combat.CombatStateManager;
import zcylas.totality.api.rpg.combat.PowerAttackManager;
import zcylas.totality.api.rpg.combat.weapon.TotalityMeleeWeaponItem;
import zcylas.totality.init.ModEffects;

public class CombatServerEvents {

    // Per-player tick of last successful sword block — prevents slime/rapid-hit spam
    private static final Map<UUID, Integer> SWORD_BLOCK_COOLDOWN = new HashMap<>();
    private static final int SWORD_BLOCK_COOLDOWN_TICKS = 10;

    public static void register() {
        // ── Combat state: player attacks something ────────────────────────────
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer sp)
                CombatStateManager.onDamage(sp);
            return InteractionResult.PASS;
        });

        // ── Power attack: mark advantage for interceptor ──────────────────────
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(source.getEntity() instanceof ServerPlayer player)) return true;
            if (!PowerAttackManager.consumePowerAttack(player)) return true;
            // Mark advantage — VanillaDamageInterceptor reads and clears this
            PowerAttackManager.markPowerAttack(player.getUUID());
            return true;
        });

        // ── Rage inactivity: taking damage keeps rage alive ───────────────────
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer sp && BarbarianRageAbility.isRaging(sp)) {
                BarbarianRageAbility.refreshCombatTimer(sp);
            }
            return true;
        });

        // ── Sword blocking: negate frontal attacks while sword is raised ─────────
        // isBlocking() in this MC version requires DataComponents.BLOCKS_ATTACKS (shield-only).
        // We own the check: cancel frontal directional attacks, with a per-player cooldown to
        // prevent rapid-hit enemies (slimes) from draining durability and spamming sounds.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer sp)) return true;
            if (!isSwordBlocking(sp)) return true;
            // Skip damage types that bypass shields (explosions, fire, magic, fall, etc.)
            if (source.is(DamageTypeTags.BYPASSES_SHIELD)) return true;
            // Only block directional attacks
            Vec3 sourcePos = source.getSourcePosition();
            if (sourcePos == null) return true;
            // Block if attacker is within the front hemisphere
            Vec3 toAttacker = sourcePos.subtract(sp.position()).normalize();
            if (toAttacker.dot(sp.getViewVector(1.0f)) > 0) {
                int lastBlock = SWORD_BLOCK_COOLDOWN.getOrDefault(sp.getUUID(), 0);
                if (sp.tickCount - lastBlock < SWORD_BLOCK_COOLDOWN_TICKS) return false;
                SWORD_BLOCK_COOLDOWN.put(sp.getUUID(), sp.tickCount);
                sp.getUseItem().hurtAndBreak(1, sp, EquipmentSlot.MAINHAND);
                // Dual wield: the raised offhand sword takes the same wear as the blocking mainhand one
                ItemStack offhand = sp.getOffhandItem();
                if (offhand.is(ItemTags.SWORDS) || offhand.getItem() instanceof TotalityMeleeWeaponItem) {
                    offhand.hurtAndBreak(1, sp, EquipmentSlot.OFFHAND);
                }
                sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                        SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS,
                        1.0f, 1.6f + sp.getRandom().nextFloat() * 0.2f);
                return false;
            }
            return true;
        });

        // ── Rage reconnect: restore toggle state if effect is still active ────
        // readData doesn't save activeToggles, so the effect (saved by vanilla)
        // outlives the toggle state. Re-sync on join with the remaining duration.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (!player.hasEffect(ModEffects.RAGE)) return;
            AbilityComponent abilities = AbilityComponents.ABILITIES.get((ComponentProvider) player);
            if (abilities.isToggleActive(BarbarianRageAbility.ID)) return;
            MobEffectInstance inst = player.getEffect(ModEffects.RAGE);
            int remaining = inst != null ? inst.getDuration() : BarbarianRageAbility.RAGE_DURATION_TICKS;
            abilities.activateToggle(BarbarianRageAbility.ID, remaining);
        });
    }

    private static boolean isSwordBlocking(ServerPlayer player) {
        if (!player.isUsingItem() || player.getTicksUsingItem() < 5) return false;
        ItemStack used = player.getUseItem();
        return (used.is(ItemTags.SWORDS) || used.getItem() instanceof TotalityMeleeWeaponItem)
                && !(player.getOffhandItem().getItem() instanceof ShieldItem);
    }
}