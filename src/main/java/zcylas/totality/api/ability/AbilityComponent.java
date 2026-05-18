package zcylas.totality.api.ability;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AbilityComponent implements SyncedComponent, CopyableComponent<AbilityComponent> {

    /** Ability IDs the player has unlocked. */
    private final Set<Identifier> unlocked = new HashSet<>();
    /** Remaining cooldown ticks per ability. */
    private final Map<Identifier, Integer> cooldowns = new HashMap<>();

    private final ServerPlayer player;

    private @Nullable Identifier equippedAbility = null;

    public AbilityComponent(ServerPlayer player) {
        this.player = player;
        // Grant all default abilities immediately
        for (Ability ability : AbilityRegistry.defaults()) {
            unlocked.add(ability.getId());
        }
    }

    public @Nullable Identifier getEquippedAbility() {
        return equippedAbility;
    }

    public void setEquippedAbility(@Nullable Identifier id) {
        this.equippedAbility = id;
        sync();
    }

    // -------------------------------------------------------------------------
    // Unlock
    // -------------------------------------------------------------------------

    public boolean hasAbility(Identifier id) {
        return unlocked.contains(id);
    }

    public void unlock(Identifier id) {
        unlocked.add(id);
        sync();
    }

    public Set<Identifier> getUnlocked() {
        return Set.copyOf(unlocked);
    }

    // -------------------------------------------------------------------------
    // Cooldowns
    // -------------------------------------------------------------------------

    public boolean isOnCooldown(Identifier id) {
        return cooldowns.getOrDefault(id, 0) > 0;
    }

    public int getCooldown(Identifier id) {
        return cooldowns.getOrDefault(id, 0);
    }

    public void startCooldown(Identifier id) {
        Ability ability = AbilityRegistry.get(id);
        if (ability == null || ability.getCooldownTicks() <= 0) return;
        cooldowns.put(id, ability.getCooldownTicks());
        sync();
    }

    /** Called every server tick by PlayerAbilityManager. */
    public void tickCooldowns() {
        if (cooldowns.isEmpty()) return;
        boolean changed = false;
        for (var entry : cooldowns.entrySet()) {
            if (entry.getValue() > 0) {
                entry.setValue(entry.getValue() - 1);
                changed = true;
            }
        }
        cooldowns.entrySet().removeIf(e -> e.getValue() <= 0);
        if (changed) sync();
    }

    // -------------------------------------------------------------------------
    // Sync
    // -------------------------------------------------------------------------

    private void sync() {
        if (!player.level().isClientSide()) {
            AbilityComponents.ABILITIES.sync(
                    (zcylas.totality.api.core.component.ComponentProvider) player);
        }
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        // Write unlocked set
        buf.writeInt(unlocked.size());
        for (Identifier id : unlocked) {
            buf.writeIdentifier(id);
        }
        // Write cooldowns
        buf.writeInt(cooldowns.size());
        for (var entry : cooldowns.entrySet()) {
            buf.writeIdentifier(entry.getKey());
            buf.writeInt(entry.getValue());
        }
        buf.writeBoolean(equippedAbility != null);
        if (equippedAbility != null) buf.writeIdentifier(equippedAbility);
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        unlocked.clear();
        int unlockedCount = buf.readInt();
        for (int i = 0; i < unlockedCount; i++) {
            unlocked.add(buf.readIdentifier());
        }
        cooldowns.clear();
        int cooldownCount = buf.readInt();
        for (int i = 0; i < cooldownCount; i++) {
            Identifier id = buf.readIdentifier();
            int ticks = buf.readInt();
            cooldowns.put(id, ticks);
        }
        equippedAbility = buf.readBoolean() ? buf.readIdentifier() : null;
    }

    @Override
    public void readData(ValueInput input) {
        unlocked.clear();
        input.listOrEmpty("unlocked", Codec.STRING).stream().forEach(raw -> {
            Identifier id = Identifier.tryParse(raw);
            if (id != null) unlocked.add(id);
        });
        equippedAbility = input.getString("equippedAbility")
                .map(Identifier::tryParse)
                .orElse(null);
        // Cooldowns intentionally not persisted — reset on relog
    }

    @Override
    public void writeData(ValueOutput output) {
        var list = output.list("unlocked", Codec.STRING);
        for (Identifier id : unlocked) {
            list.add(id.toString());
        }
        if (equippedAbility != null) output.putString("equippedAbility", equippedAbility.toString());
        // Cooldowns intentionally not saved
    }

    @Override
    public void copyFrom(AbilityComponent other,
                         net.minecraft.core.HolderLookup.Provider registries) {
        this.unlocked.clear();
        this.unlocked.addAll(other.unlocked);
        this.cooldowns.clear();
        this.cooldowns.putAll(other.cooldowns);
        this.equippedAbility = other.equippedAbility;
    }
    public void forget(Identifier id) {
        unlocked.remove(id);
        cooldowns.remove(id);
        if (id.equals(equippedAbility)) equippedAbility = null;
        sync();
    }
}