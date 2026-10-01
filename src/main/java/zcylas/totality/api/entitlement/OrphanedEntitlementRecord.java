package zcylas.totality.api.entitlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent data whose content is not (or no longer) registered (canonical §14.12–14.13). Never usable,
 * never synchronized, visible in admin diagnostics, and restored automatically when the same key is
 * registered again. Nothing is silently discarded during a mod update.
 *
 * @param originId where the record came from, e.g. {@code totality:permanent_fact} or
 *                 {@code totality:legacy_ability_component}
 * @param facts    the permanent facts held for the key, if any (legacy quarantine entries have none)
 */
public record OrphanedEntitlementRecord(
        EntitlementKey key,
        Identifier originId,
        Map<PermanentEntitlementFact, PermanentFactRecord> facts,
        String note
) {

    public static final Identifier ORIGIN_PERMANENT_FACT = Identifier.fromNamespaceAndPath("totality", "permanent_fact");

    private record FactEntry(PermanentEntitlementFact fact, PermanentFactRecord record) {
        static final Codec<FactEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.xmap(PermanentEntitlementFact::valueOf, PermanentEntitlementFact::name).fieldOf("fact").forGetter(FactEntry::fact),
                PermanentFactRecord.CODEC.fieldOf("record").forGetter(FactEntry::record)
        ).apply(i, FactEntry::new));
    }

    public static final Codec<OrphanedEntitlementRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            EntitlementKey.CODEC.fieldOf("key").forGetter(OrphanedEntitlementRecord::key),
            Identifier.CODEC.fieldOf("origin").forGetter(OrphanedEntitlementRecord::originId),
            FactEntry.CODEC.listOf().xmap(OrphanedEntitlementRecord::toMap, OrphanedEntitlementRecord::toList)
                    .optionalFieldOf("facts", Map.of()).forGetter(OrphanedEntitlementRecord::facts),
            Codec.STRING.optionalFieldOf("note", "").forGetter(OrphanedEntitlementRecord::note)
    ).apply(i, OrphanedEntitlementRecord::new));

    public OrphanedEntitlementRecord {
        facts = facts.isEmpty() ? Map.of() : Map.copyOf(facts);
    }

    private static Map<PermanentEntitlementFact, PermanentFactRecord> toMap(List<FactEntry> entries) {
        Map<PermanentEntitlementFact, PermanentFactRecord> map = new EnumMap<>(PermanentEntitlementFact.class);
        for (FactEntry entry : entries) map.put(entry.fact(), entry.record());
        return map;
    }

    private static List<FactEntry> toList(Map<PermanentEntitlementFact, PermanentFactRecord> map) {
        return map.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> new FactEntry(e.getKey(), e.getValue())).toList();
    }
}
