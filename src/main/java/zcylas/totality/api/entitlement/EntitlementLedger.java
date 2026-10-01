package zcylas.totality.api.entitlement;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The persisted, server-authoritative part of a player's entitlement state (canonical §3.1): permanent
 * facts, persisted grants, persisted suspensions, orphan quarantine, migration markers, an audit log,
 * a monotonically increasing revision and the online-tick clock.
 *
 * <p>Provider-reconciled grants are deliberately absent: they are derived from their source systems.
 *
 * <p>The codec decodes each entry independently. An entry that fails to decode is kept verbatim in an
 * unreadable list and written back unchanged, so a malformed or future-format record is never lost.
 */
public final class EntitlementLedger {

    public static final int DATA_VERSION = 1;
    public static final int MAX_AUDIT_ENTRIES = 64;

    int dataVersion = DATA_VERSION;
    long revision;
    long onlineTicks;
    final Map<EntitlementKey, EnumMap<PermanentEntitlementFact, PermanentFactRecord>> permanent = new LinkedHashMap<>();
    final Map<UUID, EntitlementGrant> persistentGrants = new LinkedHashMap<>();
    final Map<UUID, EntitlementSuspension> suspensions = new LinkedHashMap<>();
    final Map<EntitlementKey, OrphanedEntitlementRecord> orphaned = new LinkedHashMap<>();
    final Map<UUID, EntitlementGrant> orphanedGrants = new LinkedHashMap<>();
    final Set<Identifier> appliedMigrations = new LinkedHashSet<>();
    final Set<Identifier> processedLegacyAbilityIds = new LinkedHashSet<>();
    final Deque<EntitlementAuditEntry> audit = new ArrayDeque<>();
    final List<Dynamic<?>> unreadable = new ArrayList<>();

    // ── Read access ───────────────────────────────────────────────────────────

    public int dataVersion() { return dataVersion; }
    public long revision() { return revision; }
    public long onlineTicks() { return onlineTicks; }

    public Map<PermanentEntitlementFact, PermanentFactRecord> facts(EntitlementKey key) {
        EnumMap<PermanentEntitlementFact, PermanentFactRecord> facts = permanent.get(key);
        return facts == null ? Map.of() : Collections.unmodifiableMap(facts);
    }

    public Set<EntitlementKey> keysWithFacts() { return Collections.unmodifiableSet(permanent.keySet()); }
    public Map<UUID, EntitlementGrant> persistentGrants() { return Collections.unmodifiableMap(persistentGrants); }
    public Map<UUID, EntitlementSuspension> suspensions() { return Collections.unmodifiableMap(suspensions); }
    public Map<EntitlementKey, OrphanedEntitlementRecord> orphaned() { return Collections.unmodifiableMap(orphaned); }
    public Map<UUID, EntitlementGrant> orphanedGrants() { return Collections.unmodifiableMap(orphanedGrants); }
    public Set<Identifier> appliedMigrations() { return Collections.unmodifiableSet(appliedMigrations); }
    public Set<Identifier> processedLegacyAbilityIds() { return Collections.unmodifiableSet(processedLegacyAbilityIds); }
    public List<EntitlementAuditEntry> audit() { return List.copyOf(audit); }
    public int unreadableCount() { return unreadable.size(); }

    // ── Migration bookkeeping (grants nothing) ────────────────────────────────

    /** @return false when the migration was already recorded */
    public boolean markMigrationApplied(Identifier migrationId) {
        return appliedMigrations.add(migrationId);
    }

    /** Records that a legacy ability id was classified, so it is never re-migrated. */
    public boolean markLegacyAbilityProcessed(Identifier abilityId) {
        return processedLegacyAbilityIds.add(abilityId);
    }

    /** Quarantines unresolvable legacy evidence for diagnostics. Never replaces an orphan that holds facts. */
    public void quarantineLegacy(OrphanedEntitlementRecord record) {
        OrphanedEntitlementRecord existing = orphaned.get(record.key());
        if (existing == null || existing.facts().isEmpty()) orphaned.put(record.key(), record);
    }

    /** Drops a legacy quarantine entry once its id has been resolved (never one that holds facts). */
    public void clearLegacyQuarantine(EntitlementKey key) {
        OrphanedEntitlementRecord existing = orphaned.get(key);
        if (existing != null && existing.facts().isEmpty()) orphaned.remove(key);
    }

    // ── Package-level mutation (engine and migration helpers) ─────────────────

    void addAudit(EntitlementAuditEntry entry) {
        audit.addLast(entry);
        while (audit.size() > MAX_AUDIT_ENTRIES) audit.removeFirst();
    }

    void advanceOnlineTicks(long ticks) {
        onlineTicks += ticks;
    }

    /**
     * Moves records whose key is no longer registered into quarantine and restores quarantined
     * permanent facts / grants whose key is registered again (canonical §14.12). Legacy-quarantine
     * entries carry no facts and stay until their own migration processes them.
     *
     * @return true when anything moved
     */
    boolean sortOrphans(Predicate<EntitlementKey> registered) {
        boolean moved = false;
        for (var it = permanent.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (registered.test(entry.getKey())) continue;
            orphaned.put(entry.getKey(), new OrphanedEntitlementRecord(entry.getKey(),
                    OrphanedEntitlementRecord.ORIGIN_PERMANENT_FACT, entry.getValue(), "content not registered"));
            it.remove();
            moved = true;
        }
        for (var it = orphaned.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            OrphanedEntitlementRecord orphan = entry.getValue();
            if (orphan.facts().isEmpty() || !registered.test(entry.getKey())) continue;
            EnumMap<PermanentEntitlementFact, PermanentFactRecord> facts = new EnumMap<>(PermanentEntitlementFact.class);
            facts.putAll(orphan.facts());
            permanent.put(entry.getKey(), facts);
            it.remove();
            moved = true;
        }
        for (var it = persistentGrants.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (registered.test(entry.getValue().key())) continue;
            orphanedGrants.put(entry.getKey(), entry.getValue());
            it.remove();
            moved = true;
        }
        for (var it = orphanedGrants.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (!registered.test(entry.getValue().key())) continue;
            persistentGrants.put(entry.getKey(), entry.getValue());
            it.remove();
            moved = true;
        }
        return moved;
    }

    /** Deep copy for respawn. {@code died} drops UNTIL_DEATH grants (canonical §3.13). */
    EntitlementLedger copy(boolean died, List<EntitlementGrant> removedOnDeath) {
        EntitlementLedger copy = new EntitlementLedger();
        copy.dataVersion = dataVersion;
        copy.revision = revision;
        copy.onlineTicks = onlineTicks;
        permanent.forEach((k, v) -> copy.permanent.put(k, new EnumMap<>(v)));
        persistentGrants.forEach((id, grant) -> {
            if (died && grant.lifetime() == GrantLifetime.UNTIL_DEATH) {
                removedOnDeath.add(grant);
            } else {
                copy.persistentGrants.put(id, grant);
            }
        });
        copy.suspensions.putAll(suspensions);
        copy.orphaned.putAll(orphaned);
        copy.orphanedGrants.putAll(orphanedGrants);
        copy.appliedMigrations.addAll(appliedMigrations);
        copy.processedLegacyAbilityIds.addAll(processedLegacyAbilityIds);
        copy.audit.addAll(audit);
        copy.unreadable.addAll(unreadable);
        if (!removedOnDeath.isEmpty()) copy.revision++;
        return copy;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private record FactEntry(EntitlementKey key, PermanentEntitlementFact fact, PermanentFactRecord record) {
        static final Codec<FactEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntitlementKey.CODEC.fieldOf("key").forGetter(FactEntry::key),
                Codec.STRING.xmap(PermanentEntitlementFact::valueOf, PermanentEntitlementFact::name).fieldOf("fact").forGetter(FactEntry::fact),
                PermanentFactRecord.CODEC.fieldOf("record").forGetter(FactEntry::record)
        ).apply(i, FactEntry::new));
    }

    public static final Codec<EntitlementLedger> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<EntitlementLedger, T>> decode(DynamicOps<T> ops, T input) {
            return DataResult.success(Pair.of(read(new Dynamic<>(ops, input)), input));
        }

        @Override
        public <T> DataResult<T> encode(EntitlementLedger ledger, DynamicOps<T> ops, T prefix) {
            return ledger.write(ops).build(prefix);
        }
    };

    private static <T> EntitlementLedger read(Dynamic<T> root) {
        EntitlementLedger ledger = new EntitlementLedger();
        ledger.dataVersion = root.get("data_version").asInt(DATA_VERSION);
        ledger.revision = root.get("revision").asLong(0L);
        ledger.onlineTicks = root.get("online_ticks").asLong(0L);
        readEach(root, "facts", FactEntry.CODEC, ledger, e ->
                ledger.permanent.computeIfAbsent(e.key(), k -> new EnumMap<>(PermanentEntitlementFact.class)).put(e.fact(), e.record()));
        readEach(root, "grants", EntitlementGrant.CODEC, ledger, g -> ledger.persistentGrants.put(g.grantId(), g));
        readEach(root, "suspensions", EntitlementSuspension.CODEC, ledger, s -> ledger.suspensions.put(s.suspensionId(), s));
        readEach(root, "orphans", OrphanedEntitlementRecord.CODEC, ledger, o -> ledger.orphaned.put(o.key(), o));
        readEach(root, "orphan_grants", EntitlementGrant.CODEC, ledger, g -> ledger.orphanedGrants.put(g.grantId(), g));
        readEach(root, "migrations", Identifier.CODEC, ledger, ledger.appliedMigrations::add);
        readEach(root, "legacy_processed", Identifier.CODEC, ledger, ledger.processedLegacyAbilityIds::add);
        readEach(root, "audit", EntitlementAuditEntry.CODEC, ledger, ledger::addAudit);
        root.get("unreadable").asList(Function.identity()).forEach(ledger.unreadable::add);
        return ledger;
    }

    private static <T, E> void readEach(Dynamic<T> root, String section, Codec<E> codec, EntitlementLedger ledger,
                                        Consumer<E> sink) {
        for (Dynamic<T> element : root.get(section).asList(Function.identity())) {
            Optional<E> decoded = codec.parse(element).resultOrPartial(error ->
                    Totality.LOGGER.warn("[Entitlement] Preserving unreadable {} entry: {}", section, error));
            if (decoded.isPresent()) {
                sink.accept(decoded.get());
            } else {
                ledger.unreadable.add(element.createMap(Map.of(
                        element.createString("section"), element.createString(section),
                        element.createString("data"), element)));
            }
        }
    }

    private <T> RecordBuilder<T> write(DynamicOps<T> ops) {
        RecordBuilder<T> builder = ops.mapBuilder();
        builder.add("data_version", ops.createInt(dataVersion));
        builder.add("revision", ops.createLong(revision));
        builder.add("online_ticks", ops.createLong(onlineTicks));
        builder.add("facts", writeEach(ops, FactEntry.CODEC, permanent.entrySet().stream().flatMap(e ->
                e.getValue().entrySet().stream().map(f -> new FactEntry(e.getKey(), f.getKey(), f.getValue())))));
        builder.add("grants", writeEach(ops, EntitlementGrant.CODEC, persistentGrants.values().stream()));
        builder.add("suspensions", writeEach(ops, EntitlementSuspension.CODEC, suspensions.values().stream()));
        builder.add("orphans", writeEach(ops, OrphanedEntitlementRecord.CODEC, orphaned.values().stream()));
        builder.add("orphan_grants", writeEach(ops, EntitlementGrant.CODEC, orphanedGrants.values().stream()));
        builder.add("migrations", writeEach(ops, Identifier.CODEC, appliedMigrations.stream()));
        builder.add("legacy_processed", writeEach(ops, Identifier.CODEC, processedLegacyAbilityIds.stream()));
        builder.add("audit", writeEach(ops, EntitlementAuditEntry.CODEC, audit.stream()));
        builder.add("unreadable", ops.createList(unreadable.stream().map(d -> d.convert(ops).getValue())));
        return builder;
    }

    private static <T, E> T writeEach(DynamicOps<T> ops, Codec<E> codec, Stream<E> values) {
        return ops.createList(values.map(v -> codec.encodeStart(ops, v)
                .resultOrPartial(error -> Totality.LOGGER.error("[Entitlement] Could not encode {}: {}", v, error)))
                .flatMap(Optional::stream));
    }
}
