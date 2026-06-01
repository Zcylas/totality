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
import zcylas.totality.screen.character.tabs.AbilitiesTab;

import java.util.*;

public class AbilityComponent implements SyncedComponent, CopyableComponent<AbilityComponent> {

    /** Ability IDs the player has unlocked. */
    private final Set<Identifier> unlocked = new HashSet<>();
    /** Remaining cooldown ticks per ability. */
    private final Map<Identifier, Integer> cooldowns = new HashMap<>();
    private final List<Identifier> favorites = new ArrayList<>(); // ordered list for radial
    private final Set<Identifier> activeToggles = new HashSet<>();
    private final Map<Identifier, Integer> toggleTimers = new HashMap<>();  // id → remaining ticks
    private final Map<Identifier, Integer> lastCombatTick = new HashMap<>(); // id → tick of last attack/damage


    private final ServerPlayer player;

    private @Nullable Identifier equippedAbility = null;
    private @Nullable Identifier channelingAbility = null;

    public AbilityComponent(ServerPlayer player) {
        this.player = player;
        // Grant all default abilities immediately
        ensureDefaultAbilitiesUnlocked();
    }

    public @Nullable Identifier getChannelingAbility() {
        return channelingAbility;
    }

    public void startChanneling(Identifier id) {
        this.channelingAbility = id;
        // No sync needed — transient state
    }

    public void stopChanneling() {
        this.channelingAbility = null;
    }

    public boolean isToggleActive(Identifier id)  { return activeToggles.contains(id); }

    public boolean isChanneling() {
        return channelingAbility != null;
    }

    public boolean isChanneling(Identifier id) {
        return id.equals(channelingAbility);
    }

    public Set<Identifier>          getActiveToggles()  { return activeToggles; }
    public Map<Identifier, Integer> getToggleTimers()   { return toggleTimers; }
    public Map<Identifier, Integer> getLastCombatTick() { return lastCombatTick; }

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

    public void activateToggle(Identifier id, int durationTicks) {
        activeToggles.add(id);
        toggleTimers.put(id, durationTicks);
        lastCombatTick.put(id, player != null ? player.tickCount : 0); // ← initialize here
        sync();
    }

    public void deactivateToggle(Identifier id) {
        activeToggles.remove(id);
        toggleTimers.remove(id);
        lastCombatTick.remove(id);
        sync();
    }

    public void refreshCombatTick(Identifier id, int currentTick) {
        if (activeToggles.contains(id)) lastCombatTick.put(id, currentTick);
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

        buf.writeInt(favorites.size());
        for (Identifier id : favorites) buf.writeIdentifier(id);

        buf.writeInt(activeToggles.size());
        for (Identifier id : activeToggles) buf.writeIdentifier(id);
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
        favorites.clear();
        int favCount = buf.readInt();
        for (int i = 0; i < favCount; i++) favorites.add(buf.readIdentifier());

        activeToggles.clear();
        int toggleCount = buf.readInt();
        for (int i = 0; i < toggleCount; i++) activeToggles.add(buf.readIdentifier());
    }

    @Override
    public void readData(ValueInput input) {
        unlocked.clear();

        input.listOrEmpty("unlocked", Codec.STRING).stream().forEach(raw -> {
            Identifier id = Identifier.tryParse(raw);
            if (id != null) unlocked.add(id);
        });

        ensureDefaultAbilitiesUnlocked();

        favorites.clear();
        input.listOrEmpty("favorites", Codec.STRING).stream().forEach(raw -> {
            Identifier id = Identifier.tryParse(raw);
            if (id != null) favorites.add(id);
        });

        favorites.removeIf(id -> !unlocked.contains(id));

        equippedAbility = input.getString("equippedAbility")
                .map(Identifier::tryParse)
                .orElse(null);

        if (equippedAbility != null && !unlocked.contains(equippedAbility)) {
            equippedAbility = null;
        }
    }

    @Override
    public void writeData(ValueOutput output) {
        // Write unlocked
        var list = output.list("unlocked", Codec.STRING);
        for (Identifier id : unlocked) list.add(id.toString());

        // Write favorites
        var favList = output.list("favorites", Codec.STRING);
        for (Identifier id : favorites) favList.add(id.toString());

        // Write equipped
        if (equippedAbility != null) output.putString("equippedAbility", equippedAbility.toString());
    }

    @Override
    public void copyFrom(AbilityComponent other,
                         net.minecraft.core.HolderLookup.Provider registries) {
        this.unlocked.clear();
        this.unlocked.addAll(other.unlocked);
        this.cooldowns.clear();
        this.cooldowns.putAll(other.cooldowns);
        this.equippedAbility = other.equippedAbility;
        this.favorites.clear();
        this.favorites.addAll(other.favorites);

    }
    public void forget(Identifier id) {
        unlocked.remove(id);
        cooldowns.remove(id);
        if (id.equals(equippedAbility)) equippedAbility = null;
        if (id.equals(channelingAbility)) channelingAbility = null; // ← add this
        sync();
    }

    public List<Identifier> getFavorites() { return List.copyOf(favorites); }

    public boolean isFavorite(Identifier id) { return favorites.contains(id); }

    public void toggleFavorite(Identifier id) {
        if (favorites.contains(id)) favorites.remove(id);
        else if (favorites.size() < AbilitiesTab.MAX_FAVORITES) favorites.add(id);
        sync();
    }

    private void ensureDefaultAbilitiesUnlocked() {
        for (Ability ability : AbilityRegistry.defaults()) {
            unlocked.add(ability.getId());
        }
    }

}