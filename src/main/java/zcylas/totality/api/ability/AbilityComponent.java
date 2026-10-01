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
import zcylas.totality.api.entitlement.EntitlementActions;
import zcylas.totality.api.entitlement.EntitlementService;
import zcylas.totality.api.entitlement.integration.AbilityEntitlements;
import zcylas.totality.api.rpg.rest.RestListener;
import zcylas.totality.api.rpg.rest.RestType;
import zcylas.totality.screen.character.tabs.AbilitiesTab;

import java.util.*;

public class AbilityComponent implements SyncedComponent, CopyableComponent<AbilityComponent>, RestListener {

    /** Pre-Entitlement flat unlock set, read from older saves and written back unchanged for one transition
     *  release. No longer authoritative — access comes from the Entitlement API — and consumed once per id
     *  by {@code LegacyAbilityMigration}. */
    private final Set<Identifier> legacyUnlocked = new LinkedHashSet<>();
    /** Remaining cooldown ticks per ability. */
    private final Map<Identifier, Integer> cooldowns = new HashMap<>();
    private final List<Identifier> favorites = new ArrayList<>(); // ordered list for radial
    private final Set<Identifier> activeToggles = new HashSet<>();
    private final Map<Identifier, Integer> toggleTimers = new HashMap<>();  // id → remaining ticks
    private final Map<Identifier, Integer> lastCombatTick = new HashMap<>(); // id → tick of last attack/damage


    private final ServerPlayer player;

    private @Nullable Identifier equippedAbility = null;
    private @Nullable Identifier channelingAbility = null;
    /** Last spell selected in the Spell radial — the spell equivalent of equippedAbility, kept
     *  separate since a player can have both an equipped (non-spell) ability and a selected spell
     *  active at once. Persisted/synced the same way so it survives a disconnect, unlike the old
     *  client-only {@code ClientSelectedSpellManager} field it feeds. */
    private @Nullable Identifier selectedSpell = null;

    public AbilityComponent(ServerPlayer player) {
        this.player = player;
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
        invalidateSelection();
        sync();
    }

    public @Nullable Identifier getSelectedSpell() {
        return selectedSpell;
    }

    public void setSelectedSpell(@Nullable Identifier id) {
        this.selectedSpell = id;
        invalidateSelection();
        sync();
    }

    /** Selection is exposed to entitlement snapshots; tell the cache it changed. */
    private void invalidateSelection() {
        if (player != null) EntitlementService.INSTANCE.invalidate(player, AbilityEntitlements.SELECTION);
    }

    // -------------------------------------------------------------------------
    // Access (Entitlement API)
    // -------------------------------------------------------------------------

    /** @deprecated compatibility adapter: whether the Entitlement API currently allows <em>using</em> this
     *  ability. Prefer an operation-specific {@link AbilityEntitlements#check}. */
    @Deprecated
    public boolean hasAbility(Identifier id) {
        return player != null && AbilityEntitlements.canUse(player, id, EntitlementActions.USE);
    }

    /** Abilities and spells the Entitlement API currently allows this player to use, in registry order. */
    public Set<Identifier> getAccessibleAbilities() {
        return player != null ? AbilityEntitlements.accessibleAbilityIds(player) : Set.of();
    }

    /** The frozen pre-Entitlement unlock set (migration input and diagnostics only). */
    public Set<Identifier> getLegacyUnlocked() {
        return Collections.unmodifiableSet(legacyUnlocked);
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
    // Rest recovery
    // -------------------------------------------------------------------------

    /** Clears cooldowns for any unlocked ability whose rechargeOnRest() matches. */
    @Override
    public void onRest(ServerPlayer player, RestType type) {
        if (cooldowns.isEmpty()) return;
        boolean changed = false;
        var it = cooldowns.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Ability ability = AbilityRegistry.get(entry.getKey());
            RestType recharge = ability != null ? ability.rechargeOnRest() : null;
            if (recharge != null && (recharge == type || type == RestType.LONG)) {
                it.remove();
                changed = true;
            }
        }
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
        // The client receives only what the server currently authorizes — a display view, never the
        // legacy set or hidden content. Stored selections are kept even while inaccessible (a lost source
        // may return), but only accessible ones are shown.
        Set<Identifier> accessible = getAccessibleAbilities();
        buf.writeInt(accessible.size());
        for (Identifier id : accessible) {
            buf.writeIdentifier(id);
        }
        // Write cooldowns
        buf.writeInt(cooldowns.size());
        for (var entry : cooldowns.entrySet()) {
            buf.writeIdentifier(entry.getKey());
            buf.writeInt(entry.getValue());
        }
        Identifier shownEquipped = equippedAbility != null && accessible.contains(equippedAbility) ? equippedAbility : null;
        buf.writeBoolean(shownEquipped != null);
        if (shownEquipped != null) buf.writeIdentifier(shownEquipped);

        List<Identifier> shownFavorites = favorites.stream().filter(accessible::contains).toList();
        buf.writeInt(shownFavorites.size());
        for (Identifier id : shownFavorites) buf.writeIdentifier(id);

        buf.writeInt(activeToggles.size());
        for (Identifier id : activeToggles) buf.writeIdentifier(id);

        Identifier shownSpell = selectedSpell != null && accessible.contains(selectedSpell) ? selectedSpell : null;
        buf.writeBoolean(shownSpell != null);
        if (shownSpell != null) buf.writeIdentifier(shownSpell);
    }

    /** Owner only: the payload carries no entity id, so a tracking player's client would otherwise apply
     *  another player's (entitlement-derived) ability view as its own. */
    @Override
    public boolean shouldSyncWith(ServerPlayer recipient) {
        return recipient == player;
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        // Client mirror: the accessible set is consumed by ClientAbilityManager; skip it here.
        int accessibleCount = buf.readInt();
        for (int i = 0; i < accessibleCount; i++) {
            buf.readIdentifier();
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

        selectedSpell = buf.readBoolean() ? buf.readIdentifier() : null;
    }

    @Override
    public void readData(ValueInput input) {
        legacyUnlocked.clear();

        input.listOrEmpty("unlocked", Codec.STRING).stream().forEach(raw -> {
            Identifier id = Identifier.tryParse(raw);
            if (id != null) legacyUnlocked.add(id);
        });

        // Selections are kept as saved. Access is decided by the Entitlement API once the player's grant
        // sources have been reconciled on join; inaccessible selections are simply not shown or usable.
        favorites.clear();
        input.listOrEmpty("favorites", Codec.STRING).stream().forEach(raw -> {
            Identifier id = Identifier.tryParse(raw);
            if (id != null) favorites.add(id);
        });

        equippedAbility = input.getString("equippedAbility")
                .map(Identifier::tryParse)
                .orElse(null);

        selectedSpell = input.getString("selectedSpell")
                .map(Identifier::tryParse)
                .orElse(null);
    }

    @Override
    public void writeData(ValueOutput output) {
        // Legacy set, written back unchanged for one transition release
        var list = output.list("unlocked", Codec.STRING);
        for (Identifier id : legacyUnlocked) list.add(id.toString());

        // Write favorites
        var favList = output.list("favorites", Codec.STRING);
        for (Identifier id : favorites) favList.add(id.toString());

        // Write equipped
        if (equippedAbility != null) output.putString("equippedAbility", equippedAbility.toString());
        if (selectedSpell != null) output.putString("selectedSpell", selectedSpell.toString());
    }

    @Override
    public void copyFrom(AbilityComponent other,
                         net.minecraft.core.HolderLookup.Provider registries) {
        this.legacyUnlocked.clear();
        this.legacyUnlocked.addAll(other.legacyUnlocked);
        this.cooldowns.clear();
        this.cooldowns.putAll(other.cooldowns);
        this.equippedAbility = other.equippedAbility;
        this.selectedSpell = other.selectedSpell;
        this.favorites.clear();
        this.favorites.addAll(other.favorites);

    }
    public List<Identifier> getFavorites() { return List.copyOf(favorites); }

    public boolean isFavorite(Identifier id) { return favorites.contains(id); }

    public void toggleFavorite(Identifier id) {
        if (favorites.contains(id)) favorites.remove(id);
        else if (favorites.size() < AbilitiesTab.MAX_FAVORITES) favorites.add(id);
        sync();
    }

}