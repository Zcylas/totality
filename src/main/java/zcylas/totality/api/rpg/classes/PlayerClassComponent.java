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
    private @Nullable Identifier subclassId = null;
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

    public @Nullable Identifier getSubclassId()        { return subclassId; }
    public @Nullable Identifier getCovenantId()        { return covenantId; }
    public boolean hasSubclass()                       { return subclassId != null; }
    public boolean hasCovenant()                       { return covenantId != null; }

    public void selectClass(Identifier classId, int playerLevels) {
        classLevels.clear();
        classLevels.put(classId, playerLevels);
        subclassId = null;
        covenantId = null;
        sync();
    }

    public void selectSubclass(Identifier id) {
        this.subclassId = id;
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
        buf.writeBoolean(subclassId != null);
        if (subclassId != null) buf.writeUtf(subclassId.toString());
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
        subclassId = buf.readBoolean() ? Identifier.parse(buf.readUtf()) : null;
        covenantId = buf.readBoolean() ? Identifier.parse(buf.readUtf()) : null;
        ClientClassManager.apply(classLevels, subclassId, covenantId);
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
        output.putString("SubclassId", subclassId != null ? subclassId.toString() : "none");
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
        String sub = input.getStringOr("SubclassId", "none");
        String cov = input.getStringOr("CovenantId", "none");
        subclassId = sub.equals("none") ? null : Identifier.parse(sub);
        covenantId = cov.equals("none") ? null : Identifier.parse(cov);
    }

    // ── Death copy ────────────────────────────────────────────────────────────

    @Override
    public void copyFrom(PlayerClassComponent other, HolderLookup.Provider registries) {
        this.classLevels.clear();
        this.classLevels.putAll(other.classLevels);
        this.subclassId = other.subclassId;
        this.covenantId = other.covenantId;
    }

    // In PlayerClassComponent
    public void resetClass() {
        this.classLevels.clear();
        this.subclassId = null;
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