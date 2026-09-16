package zcylas.totality.api.rpg.classes;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;
import zcylas.totality.api.rpg.stats.AbilityScore;

import java.util.*;

public class PlayerClassComponent implements SyncedComponent, CopyableComponent<PlayerClassComponent> {

    private final Map<Identifier, Integer> classLevels = new LinkedHashMap<>();
    /**
     * Per-class subclass ownership: {@code classId -> subclassId}. Migrated (2026-09-16) from a
     * single global {@code Identifier subclassId} field, which incorrectly modeled subclass choice
     * as one slot per PLAYER rather than one slot per CLASS — meaning a multiclass character who
     * picked a Barbarian subclass could never also pick a Wizard subclass ({@code hasSubclass()}
     * was already {@code true} globally). See the Class Tab Quick Level-Up implementation report's
     * "Per-class subclass migration" section for the full audit and legacy-save migration behavior
     * ({@link #readData}).
     */
    private final Map<Identifier, Identifier> subclassIds = new LinkedHashMap<>();
    private @Nullable Identifier covenantId = null;
    private final ServerPlayer player;

    public PlayerClassComponent(ServerPlayer player) {
        this.player = player;
    }

    // ── Class levels ──────────────────────────────────────────────────────────

    public void setClassLevel(Identifier classId, int levels) {
        if (levels <= 0) classLevels.remove(classId);
        else classLevels.put(classId, levels);
        sync();
    }

    public int getClassLevel(Identifier classId) {
        return classLevels.getOrDefault(classId, 0);
    }

    public int getTotalClassLevel() {
        return classLevels.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Player levels → class level. Every 5 player levels = 1 class level, max 30 at level 150. */
    public int getClassLevel(int playerLevel) {
        return Math.clamp(playerLevel / 5, 1, 30);
    }

    public static int toClassLevel(int playerLevel) {
        return Math.clamp(playerLevel / 5, 1, 30);
    }

    public boolean hasClass(Identifier classId) {
        return classLevels.containsKey(classId);
    }

    public boolean hasAnyClass() {
        return !classLevels.isEmpty();
    }

    /** Returns the class with the most levels. For single-class players this is
     *  always their only class. Safe to call before multiclassing is built. */
    public @Nullable Identifier getPrimaryClassId() {
        return classLevels.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public Map<Identifier, Integer> getAllClassLevels() {
        return Collections.unmodifiableMap(classLevels);
    }

    public Set<AbilityScore> getSaveProficiencies() {
        Identifier primary = getPrimaryClassId();
        if (primary == null) return Set.of();
        return ClassRegistry.get(primary)
                .map(data -> Set.copyOf(data.savingThrowProficiencies()))
                .orElse(Set.of());
    }

    // ── Subclass & Covenant ───────────────────────────────────────────────────

    /** The subclass chosen for {@code classId}, or {@code null} if that class has none yet. A
     *  subclass chosen for one class never affects any other class's entry. */
    public @Nullable Identifier getSubclassId(Identifier classId) { return subclassIds.get(classId); }
    public @Nullable Identifier getCovenantId()        { return covenantId; }
    public boolean hasSubclass(Identifier classId)     { return subclassIds.containsKey(classId); }
    public boolean hasCovenant()                       { return covenantId != null; }
    public Map<Identifier, Identifier> getAllSubclassIds() { return Collections.unmodifiableMap(subclassIds); }

    public void selectClass(Identifier classId, int playerLevels) {
        classLevels.clear();
        classLevels.put(classId, playerLevels);
        subclassIds.clear();
        covenantId = null;
        sync();
    }

    /** Applies {@code subclassId} to {@code classId}'s own subclass slot — never any other class's. */
    public void selectSubclass(Identifier classId, Identifier subclassId) {
        subclassIds.put(classId, subclassId);
        sync();
    }

    public void selectCovenant(Identifier id) {
        this.covenantId = id;
        sync();
    }

    public int getAvailableClassPoints(int playerLevel) {
        return Math.clamp(playerLevel / 5, 1, 30);
    }

    public int getSpentClassPoints() {
        return classLevels.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getUnspentClassPoints(int playerLevel) {
        return getAvailableClassPoints(playerLevel) - getSpentClassPoints();
    }

    public boolean addClassLevel(Identifier classId) {
        int current = classLevels.getOrDefault(classId, 0);
        classLevels.put(classId, current + 1);
        sync();
        return true;
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeInt(classLevels.size());
        classLevels.forEach((id, lvl) -> {
            buf.writeUtf(id.toString());
            buf.writeInt(lvl);
        });
        buf.writeInt(subclassIds.size());
        subclassIds.forEach((classId, subclassId) -> {
            buf.writeUtf(classId.toString());
            buf.writeUtf(subclassId.toString());
        });
        buf.writeBoolean(covenantId != null);
        if (covenantId != null) buf.writeUtf(covenantId.toString());
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        classLevels.clear();
        int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            classLevels.put(Identifier.parse(buf.readUtf()), buf.readInt());
        }
        subclassIds.clear();
        int subCount = buf.readInt();
        for (int i = 0; i < subCount; i++) {
            subclassIds.put(Identifier.parse(buf.readUtf()), Identifier.parse(buf.readUtf()));
        }
        covenantId = buf.readBoolean() ? Identifier.parse(buf.readUtf()) : null;
        ClientClassManager.apply(classLevels, subclassIds, covenantId);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public void writeData(ValueOutput output) {
        output.putInt("ClassCount", classLevels.size());
        int i = 0;
        for (Map.Entry<Identifier, Integer> entry : classLevels.entrySet()) {
            output.putString("ClassId_" + i, entry.getKey().toString());
            output.putInt("ClassLvl_" + i, entry.getValue());
            i++;
        }
        // Per-class format (2026-09-16) — see readData for the one-time migration from the old
        // single "SubclassId" key this replaces. Always written now; the old key is never written
        // again once a character has been saved once under this format.
        output.putInt("SubclassCount", subclassIds.size());
        int j = 0;
        for (Map.Entry<Identifier, Identifier> entry : subclassIds.entrySet()) {
            output.putString("SubclassClassId_" + j, entry.getKey().toString());
            output.putString("SubclassSubclassId_" + j, entry.getValue().toString());
            j++;
        }
        output.putString("CovenantId", covenantId != null ? covenantId.toString() : "none");
    }

    @Override
    public void readData(ValueInput input) {
        classLevels.clear();
        int count = input.getIntOr("ClassCount", 0);
        for (int i = 0; i < count; i++) {
            String idStr = input.getStringOr("ClassId_" + i, "none");
            int lvl      = input.getIntOr("ClassLvl_" + i, 0);
            if (!idStr.equals("none")) {
                try { classLevels.put(Identifier.parse(idStr), lvl); }
                catch (Exception ignored) {}
            }
        }

        subclassIds.clear();
        // -1 (never a real count) distinguishes "this save predates the per-class format" from "this
        // save is already per-class but currently has zero subclasses chosen" (SubclassCount == 0).
        int subCount = input.getIntOr("SubclassCount", -1);
        if (subCount >= 0) {
            for (int i = 0; i < subCount; i++) {
                String classIdStr    = input.getStringOr("SubclassClassId_" + i, "none");
                String subclassIdStr = input.getStringOr("SubclassSubclassId_" + i, "none");
                if (!classIdStr.equals("none") && !subclassIdStr.equals("none")) {
                    try { subclassIds.put(Identifier.parse(classIdStr), Identifier.parse(subclassIdStr)); }
                    catch (Exception ignored) {}
                }
            }
        } else {
            // Legacy migration: the old format stored one global subclass with no owning class
            // recorded — infer it from SubclassRegistry.get(id).parentClassId(), the same
            // authoritative class/subclass relationship every other consumer already relies on.
            // Deterministic and re-derived from scratch on every load until this character is
            // saved once under the new format above (after which SubclassCount >= 0 forever, and
            // this branch is never consulted again) — so a load that never triggers a save cannot
            // duplicate or drift, and an unknown/malformed legacy id simply migrates nothing.
            String legacySub = input.getStringOr("SubclassId", "none");
            if (!legacySub.equals("none")) {
                try {
                    Identifier legacySubclassId = Identifier.parse(legacySub);
                    SubclassRegistry.get(legacySubclassId)
                            .ifPresent(data -> subclassIds.put(data.parentClassId(), legacySubclassId));
                } catch (Exception ignored) {}
            }
        }

        String cov = input.getStringOr("CovenantId", "none");
        covenantId = cov.equals("none") ? null : Identifier.parse(cov);
    }

    // ── Death copy ────────────────────────────────────────────────────────────

    @Override
    public void copyFrom(PlayerClassComponent other, HolderLookup.Provider registries) {
        this.classLevels.clear();
        this.classLevels.putAll(other.classLevels);
        this.subclassIds.clear();
        this.subclassIds.putAll(other.subclassIds);
        this.covenantId = other.covenantId;
    }

    // In PlayerClassComponent
    public void resetClass() {
        this.classLevels.clear();
        this.subclassIds.clear();
        this.covenantId = null;
        sync();
    }

    // ── Sync helper ───────────────────────────────────────────────────────────

    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            ClassComponents.PLAYER_CLASS.sync(
                    (zcylas.totality.api.core.component.ComponentProvider) player
            );
        }
    }
}