package zcylas.totality.api.magic.spell.destruction;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.magic.spell.CastType;
import zcylas.totality.api.magic.spell.Spell;
import zcylas.totality.api.magic.spell.SpellActionType;
import zcylas.totality.api.magic.spell.SpellComponent;
import zcylas.totality.api.magic.spell.SpellMaterial;
import zcylas.totality.api.magic.spell.SpellSchool;
import zcylas.totality.entity.magic.SpellBoltEntity;
import zcylas.totality.networking.notification.SendNotificationPayload;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Crown of Stars — Evocation, 7th level. V, S.
 *
 * Seven motes of radiance orbit the caster.
 *
 * ● First cast (no active motes): spends a 7th-level slot and summons the crown.
 * ● Subsequent casts while motes remain: fires one mote as a ranged spell attack.
 *   Hit → 4d12 Radiant damage. No slot cost.
 * ● Cannot re-summon until all 7 motes are spent.
 * ● No concentration — switching spells freely does not dispel the crown.
 *
 * Classes: Cleric, Sorcerer, Warlock, Wizard.
 */
public class CrownOfStarsSpell extends Spell {

    // ── Constants ──────────────────────────────────────────────────────────────

    public  static final int   TOTAL_MOTES   = 7;
    private static final int   DICE_COUNT    = 4;
    private static final Dice  DAMAGE_DIE    = Dice.D12;
    /** RGB color for the bolt trail and mote impact burst — warm gold-white radiant. */
    private static final int   BOLT_COLOR    = 0xFFEE55;
    private static final double ORBIT_RADIUS = 1.2;   // blocks from player center
    /** Radians advanced per tick — full orbit in 2 seconds (40 ticks). */
    private static final double ANGLE_STEP   = Math.PI * 2.0 / 40.0;

    // ── Per-player state ───────────────────────────────────────────────────────

    /** Remaining motes per player UUID. Key absent = no active crown. */
    private static final Map<UUID, Integer> charges = new HashMap<>();
    /** Current orbit angle (radians) per player. */
    private static final Map<UUID, Double>  angles  = new HashMap<>();
    /** Tick counter for per-mote vertical bob variation. */
    private static final Map<UUID, Integer> ticks   = new HashMap<>();

    /** Prevents duplicate listener registration if the spell is instantiated more than once. */
    private static boolean listenersRegistered = false;

    // ── Constructor ────────────────────────────────────────────────────────────

    public CrownOfStarsSpell() {
        super(
                Identifier.fromNamespaceAndPath("totality", "crown_of_stars"),
                "Crown of Stars",
                "Seven motes of radiance orbit you until spent. " +
                        "Each cast while motes remain fires one as a ranged spell attack — " +
                        "4d12 Radiant on a hit. Cannot re-summon until all motes are expended.",
                Type.ACTIVE,
                7,
                SpellSchool.DESTRUCTION,
                false,   // no concentration
                false,   // not a ritual
                SpellActionType.ACTION,
                EnumSet.of(SpellComponent.VERBAL, SpellComponent.SOMATIC),
                null,    // no material components
                CastType.INSTANT,
                0,
                0,       // no cooldown — charge logic gates recasting
                Identifier.fromNamespaceAndPath("totality", "textures/ability/spell/crown_of_stars.png"),
                "Stars do not ask permission to shine."
        );
        ensureListenersRegistered();
    }

    // ── Listener setup (once per mod load) ────────────────────────────────────

    private static void ensureListenersRegistered() {
        if (listenersRegistered) return;
        listenersRegistered = true;

        // ── Orbit particle loop (server tick) ─────────────────────────────────
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (charges.isEmpty()) return;

            charges.entrySet().removeIf(entry -> entry.getValue() <= 0);

            for (var entry : charges.entrySet()) {
                UUID uuid     = entry.getKey();
                int remaining = entry.getValue();
                if (remaining <= 0) continue;

                var player = server.getPlayerList().getPlayer(uuid);
                if (player == null || !(player.level() instanceof ServerLevel sl)) continue;

                double angle = angles.merge(uuid, ANGLE_STEP, Double::sum);
                int    t     = ticks.merge(uuid, 1, Integer::sum);

                double baseY = player.getY() + player.getEyeHeight() * 0.85;

                for (int i = 0; i < remaining; i++) {
                    double theta = angle + (Math.PI * 2.0 * i / TOTAL_MOTES);
                    double mx    = player.getX() + Math.cos(theta) * ORBIT_RADIUS;
                    // each mote bobs at a slightly different phase for a lively look
                    double my    = baseY + Math.sin(t * 0.12 + i * 0.9) * 0.07;
                    double mz    = player.getZ() + Math.sin(theta) * ORBIT_RADIUS;

                    // bright star core
                    sl.sendParticles(ParticleTypes.END_ROD,
                            mx, my, mz, 1, 0.0, 0.0, 0.0, 0.0);
                    // warm radiant glow halo
                    sl.sendParticles(ParticleTypes.GLOW,
                            mx, my, mz, 1, 0.04, 0.04, 0.04, 0.0);
                }
            }
        });

        // ── Cleanup on disconnect ──────────────────────────────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.player.getUUID();
            charges.remove(uuid);
            angles.remove(uuid);
            ticks.remove(uuid);
        });
    }

    // ── Spell API ──────────────────────────────────────────────────────────────

    @Override public boolean isDefault() { return true; }

    @Override
    public boolean canActivate(ServerPlayer player, @Nullable AbilityContext ctx) {
        // Always activatable — onActivate decides whether this is a summon or a fire.
        // Slot cost check is intentionally deferred until the full SpellSlotComponent
        // system is wired up.
        return true;
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext ctx) {
        if (!(player.level() instanceof ServerLevel sl)) return;

        UUID    uuid      = player.getUUID();
        Integer remaining = charges.get(uuid);

        if (remaining != null && remaining > 0) {
            fireMote(player, sl, uuid, remaining);
        } else {
            summonCrown(player, sl, uuid);
        }
    }

    // ── Crown summon ───────────────────────────────────────────────────────────

    private void summonCrown(ServerPlayer player, ServerLevel sl, UUID uuid) {
        charges.put(uuid, TOTAL_MOTES);
        angles.put(uuid, 0.0);
        ticks.put(uuid, 0);

        // Activation sound and burst
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.7f, 1.6f);

        Vec3 eye = player.getEyePosition();
        sl.sendParticles(ParticleTypes.END_ROD,
                eye.x, eye.y, eye.z, 28, 1.1, 0.4, 1.1, 0.08);
        sl.sendParticles(ParticleTypes.GLOW,
                eye.x, eye.y, eye.z, 20, 0.8, 0.3, 0.8, 0.0);

        SendNotificationPayload.send(player, "✦ Crown of Stars — 7 motes", 0xFFEEAA22);
    }

    // ── Fire a mote ───────────────────────────────────────────────────────────

    private void fireMote(ServerPlayer player, ServerLevel sl, UUID uuid, int remaining) {
        int newRemaining = remaining - 1;

        if (newRemaining <= 0) {
            charges.remove(uuid);
            angles.remove(uuid);
            ticks.remove(uuid);
            SendNotificationPayload.send(player, "Crown of Stars — last mote fired", 0xFFEEAA22);
        } else {
            charges.put(uuid, newRemaining);
            String label = newRemaining == 1 ? "1 mote remaining" : newRemaining + " motes remaining";
            SendNotificationPayload.send(player, "Crown of Stars — " + label, 0xFFEEAA22);
        }

        // Spawn bolt
        SpellBoltEntity bolt = SpellBoltEntity.create(
                sl, player,
                "Crown of Stars",
                DamageTypes.RADIANT,
                DICE_COUNT, DAMAGE_DIE,
                getSpellcastingAbility(player),
                BOLT_COLOR,
                null
        ).withSounds(SoundEvents.EVOKER_CAST_SPELL, SoundEvents.AMETHYST_CLUSTER_BREAK);
        sl.addFreshEntity(bolt);

        // Starburst at launch position
        sl.sendParticles(ParticleTypes.END_ROD,
                bolt.getX(), bolt.getY(), bolt.getZ(), 12, 0.12, 0.12, 0.12, 0.12);
        sl.sendParticles(ParticleTypes.GLOW,
                bolt.getX(), bolt.getY(), bolt.getZ(),  6, 0.08, 0.08, 0.08, 0.0);
    }

    // ── Static helpers (accessible from outside, e.g. HUD rendering) ──────────

    /** True if the player currently has at least one active mote. */
    public static boolean hasMotes(UUID uuid) {
        Integer c = charges.get(uuid);
        return c != null && c > 0;
    }

    /** Remaining mote count for the player, or 0 if the crown is not active. */
    public static int getCharges(UUID uuid) {
        return charges.getOrDefault(uuid, 0);
    }
}