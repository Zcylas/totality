package zcylas.totality.api.rpg.classes;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.rest.RestEventBus;
import zcylas.totality.api.rpg.rest.RestListener;
import zcylas.totality.api.rpg.rest.RestType;
import zcylas.totality.networking.resource.ResourceSyncManager;

import java.util.*;

public class PlayerChargesComponent implements SyncedComponent, CopyableComponent<PlayerChargesComponent>, RestListener {

    public record ChargePool(int current, int max, RestType rechargeType, int rechargeAmount) {
        /** rechargeAmount = -1 means restore all */
        public ChargePool withCurrent(int c) { return new ChargePool(c, max, rechargeType, rechargeAmount); }
    }

    private final Map<Identifier, ChargePool> pools = new LinkedHashMap<>();
    private final ServerPlayer player;

    public PlayerChargesComponent(ServerPlayer player) {
        this.player = player;
    }

    // ── Charge API ────────────────────────────────────────────────────────────

    public void registerPool(Identifier id, int max, RestType rechargeType, int rechargeAmount) {
        pools.putIfAbsent(id, new ChargePool(max, max, rechargeType, rechargeAmount));
    }

    public boolean hasCharge(Identifier id) {
        ChargePool pool = pools.get(id);
        return pool != null && pool.current() > 0;
    }

    public boolean consume(Identifier id) {
        ChargePool pool = pools.get(id);
        if (pool == null || pool.current() <= 0) return false;
        pools.put(id, pool.withCurrent(pool.current() - 1));
        sync();
        return true;
    }

    public void restore(Identifier id, int amount) {
        ChargePool pool = pools.get(id);
        if (pool == null) return;
        int newVal = amount < 0 ? pool.max() : Math.min(pool.current() + amount, pool.max());
        pools.put(id, pool.withCurrent(newVal));
        sync();
    }

    public void restoreAll(Identifier id) { restore(id, -1); }

    public int getCurrent(Identifier id) {
        ChargePool pool = pools.get(id);
        return pool != null ? pool.current() : 0;
    }

    public int getMax(Identifier id) {
        ChargePool pool = pools.get(id);
        return pool != null ? pool.max() : 0;
    }

    public void setMax(Identifier id, int max) {
        ChargePool pool = pools.get(id);
        if (pool == null) return;
        int current = Math.min(pool.current(), max);
        pools.put(id, new ChargePool(current, max, pool.rechargeType(), pool.rechargeAmount()));
        sync();
    }

    public Map<Identifier, ChargePool> getAllPools() { return Collections.unmodifiableMap(pools); }

    // ── RestListener ──────────────────────────────────────────────────────────

    @Override
    public void onRest(ServerPlayer player, RestType type) {
        boolean changed = false;
        for (Map.Entry<Identifier, ChargePool> entry : pools.entrySet()) {
            ChargePool pool = entry.getValue();
            if (pool.rechargeType() == type || type == RestType.LONG) {
                int amount = pool.rechargeAmount() < 0 ? pool.max() : pool.rechargeAmount();
                int newVal = type == RestType.LONG ? pool.max()
                        : Math.min(pool.current() + amount, pool.max());
                entry.setValue(pool.withCurrent(newVal));
                changed = true;
            }
        }
        if (changed) sync();
    }

    @Override
    public int restPriority() { return 5; } // before abilities

    // ── Sync ──────────────────────────────────────────────────────────────────

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeInt(pools.size());
        pools.forEach((id, pool) -> {
            buf.writeUtf(id.toString());
            buf.writeInt(pool.current());
            buf.writeInt(pool.max());
        });
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            Identifier id = Identifier.parse(buf.readUtf());
            int current = buf.readInt(), max = buf.readInt();
            ChargePool existing = pools.get(id);
            if (existing != null) {
                pools.put(id, existing.withCurrent(current));
            } else {
                // Pool doesn't exist client-side yet — create it
                pools.put(id, new ChargePool(current, max, RestType.LONG, -1));
            }
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public void writeData(ValueOutput output) {
        output.putInt("PoolCount", pools.size());
        int i = 0;
        for (Map.Entry<Identifier, ChargePool> e : pools.entrySet()) {
            output.putString("Pool_" + i + "_id",      e.getKey().toString());
            output.putInt   ("Pool_" + i + "_current",  e.getValue().current());
            output.putInt   ("Pool_" + i + "_max",      e.getValue().max());
            output.putString("Pool_" + i + "_rest",     e.getValue().rechargeType().name());
            output.putInt   ("Pool_" + i + "_amount",   e.getValue().rechargeAmount());
            i++;
        }
    }

    @Override
    public void readData(ValueInput input) {
        int count = input.getIntOr("PoolCount", 0);
        for (int i = 0; i < count; i++) {
            try {
                Identifier id  = Identifier.parse(input.getStringOr("Pool_" + i + "_id", ""));
                int current    = input.getIntOr("Pool_" + i + "_current", 0);
                int max        = input.getIntOr("Pool_" + i + "_max", 0);
                RestType rest  = RestType.valueOf(input.getStringOr("Pool_" + i + "_rest", "LONG"));
                int amount     = input.getIntOr("Pool_" + i + "_amount", -1);
                pools.put(id, new ChargePool(current, max, rest, amount));
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void copyFrom(PlayerChargesComponent other, HolderLookup.Provider registries) {
        pools.clear();
        pools.putAll(other.pools);
    }

    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            ChargeComponents.PLAYER_CHARGES.sync(
                    (zcylas.totality.api.core.component.ComponentProvider) player);
            // Non-authoritative dirty notification for the parallel Phase 3A generic Resource sync
            // path. This component is a generic multi-pool owner, but only its Rage pool is
            // currently resource-registered (RageResourceAdapter) — marking totality:rage dirty on
            // every pool change is a harmless, self-correcting over-notification (an unrelated
            // pool's change requeries an unchanged Rage value, which the diff engine drops without
            // sending a packet) rather than requiring this component to know which specific pool id
            // maps to which Resource id.
            ResourceSyncManager.markDirty(player.getUUID(), PlayerResourceIds.RAGE);
        }
    }

    public static int toClassLevel(int playerLevel) {
        return playerLevel / 4;
    }

    public static void registerWithRestBus() {
        // Called once at init — RestEventBus calls each player's component via the component system
    }

    public void updatePoolMax(Identifier id, int newMax) {
        ChargePool pool = pools.get(id);
        if (pool == null) return;
        int current = Math.min(pool.current(), newMax);
        pools.put(id, new ChargePool(current, newMax, pool.rechargeType(), pool.rechargeAmount()));
        sync();
    }

    /** Like registerPool but always updates recharge type/amount on existing pools. */
    public void ensurePool(Identifier id, int max, RestType rechargeType, int rechargeAmount) {
        ChargePool existing = pools.get(id);
        if (existing == null) {
            pools.put(id, new ChargePool(max, max, rechargeType, rechargeAmount));
        } else {
            // Preserve current charges, update everything else
            int current = Math.min(existing.current(), max);
            pools.put(id, new ChargePool(current, max, rechargeType, rechargeAmount));
        }
        sync();
    }
}