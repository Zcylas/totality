package zcylas.totality.api.magic.spell.restoration;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.magic.spell.CastType;
import zcylas.totality.api.magic.spell.ConcentrationComponents;
import zcylas.totality.api.magic.spell.Spell;
import zcylas.totality.api.magic.spell.SpellActionType;
import zcylas.totality.api.magic.spell.SpellComponent;
import zcylas.totality.api.magic.spell.SpellMaterial;
import zcylas.totality.api.magic.spell.SpellSchool;
import zcylas.totality.init.ModEffects;
import zcylas.totality.networking.notification.SendNotificationPayload;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bless — Restoration, 1st level. V, S, M (a sprinkling of holy water).
 * Concentration, up to 1 minute.
 *
 * Bless up to 3 creatures within 30 feet. Each target adds +1d4 to every
 * attack roll and saving throw until the caster loses concentration or the
 * 1-minute duration expires.
 */
public class BlessSpell extends Spell {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("totality", "bless");

    private static final int    DURATION_TICKS = 1200; // 1 minute
    private static final double RANGE          = 9.0;  // 30 feet ≈ 9 blocks
    private static final int    MAX_TARGETS    = 3;

    /** caster UUID → active bless state */
    private static final Map<UUID, BlessEntry> ACTIVE = new HashMap<>();
    private static boolean listenersRegistered = false;

    private record BlessEntry(Set<UUID> targets, long expiryTick) {}

    // ── Constructor ───────────────────────────────────────────────────────────

    public BlessSpell() {
        super(
                ID,
                "Bless",
                "Bless up to 3 creatures within 30 feet. Each target adds +1d4 to " +
                        "attack rolls and saving throws for 1 minute. Requires concentration.",
                Type.ACTIVE,
                1,
                SpellSchool.RESTORATION,
                true,   // concentration
                false,
                SpellActionType.ACTION,
                EnumSet.of(SpellComponent.VERBAL, SpellComponent.SOMATIC, SpellComponent.MATERIAL),
                SpellMaterial.replaceable("a sprinkling of holy water"),
                CastType.INSTANT,
                0,
                0,
                Identifier.fromNamespaceAndPath("totality", "textures/ability/spell/bless.png"),
                "May the light of the gods guide your blade and steady your resolve."
        );
        ensureListenersRegistered();
    }

    // ── Spell API ─────────────────────────────────────────────────────────────

    @Override public boolean isDefault() { return true; }

    @Override
    public boolean canActivate(ServerPlayer player, @Nullable AbilityContext ctx) {
        return true;
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext ctx) {
        if (!(player.level() instanceof ServerLevel sl)) return;

        UUID casterUuid = player.getUUID();

        // Clean up any previous bless from this caster before re-applying
        BlessEntry previous = ACTIVE.remove(casterUuid);
        if (previous != null) removeBless(sl.getServer(), previous.targets());

        // Collect up to MAX_TARGETS nearby ServerPlayers — always include the caster
        List<ServerPlayer> targets = new ArrayList<>();
        targets.add(player);

        for (ServerPlayer nearby : sl.getServer().getPlayerList().getPlayers()) {
            if (targets.size() >= MAX_TARGETS) break;
            if (nearby == player) continue;
            if (nearby.level() == sl && nearby.distanceTo(player) <= RANGE) {
                targets.add(nearby);
            }
        }

        // Register +1d4 modifier on each target
        Set<UUID> targetUuids = new LinkedHashSet<>();
        for (ServerPlayer target : targets) {
            applyBless(target);
            targetUuids.add(target.getUUID());
        }

        ACTIVE.put(casterUuid, new BlessEntry(targetUuids, sl.getGameTime() + DURATION_TICKS));
        ConcentrationComponents.get(player).startConcentrating(ID);

        // Visual / audio feedback
        sl.sendParticles(ParticleTypes.GLOW,
                player.getX(), player.getY() + 1.5, player.getZ(), 24, 0.6, 0.6, 0.6, 0.0);
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5f, 1.8f);

        String names = targets.stream()
                .map(p -> p.getName().getString())
                .collect(Collectors.joining(", "));
        SendNotificationPayload.send(player, "✦ Bless — " + names, 0xFFFFCC33);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void applyBless(ServerPlayer target) {
        // BlessEffect.onEffectAdded registers the roll modifier and sends the notification.
        target.addEffect(new MobEffectInstance(ModEffects.BLESS, DURATION_TICKS, 0, false, false, true));
    }

    private static void removeBless(MinecraftServer server, Set<UUID> targetUuids) {
        for (UUID uuid : targetUuids) {
            ServerPlayer target = server.getPlayerList().getPlayer(uuid);
            if (target != null) {
                // BlessEffect.onEffectRemoved removes the modifier and sends "Bless faded".
                target.removeEffect(ModEffects.BLESS);
            }
        }
    }

    // ── Lifecycle listeners (registered once) ─────────────────────────────────

    private static void ensureListenersRegistered() {
        if (listenersRegistered) return;
        listenersRegistered = true;

        // Per-tick: expire when duration ends or caster drops concentration
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ACTIVE.isEmpty()) return;
            long now = server.overworld().getGameTime();

            ACTIVE.entrySet().removeIf(entry -> {
                UUID casterUuid = entry.getKey();
                BlessEntry bless = entry.getValue();

                boolean expired = now >= bless.expiryTick();
                ServerPlayer caster = server.getPlayerList().getPlayer(casterUuid);
                boolean lostConcentration = caster != null &&
                        !ConcentrationComponents.get(caster).isConcentratingOn(ID);

                if (expired || lostConcentration) {
                    removeBless(server, bless.targets());
                    return true;
                }
                return false;
            });
        });

        // Caster disconnects → remove bless from all targets immediately
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.player.getUUID();
            BlessEntry entry = ACTIVE.remove(uuid);
            if (entry != null) removeBless(server, entry.targets());
        });
    }
}
