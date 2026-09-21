package zcylas.totality.api.soulgem;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.mob.stats.MobRank;

import java.util.UUID;

/**
 * The soul captured inside a filled Soul Gem — the minimum persistent captured-soul representation
 * for this foundation pass. See
 * {@code TOTALITY_SOUL_GEM_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-17.md} §9.
 *
 * <p>{@code soulInstanceId} identifies this specific CAPTURED SOUL INSTANCE — it is NOT the source
 * mob's entity UUID. Two separately captured zombies must receive distinct instance ids even when
 * every other field matches, so two such gems never accidentally compare equal / stack together
 * (this record's generated {@code equals}/{@code hashCode} already give the correct semantics; see
 * {@link CapturedSoulComponent} for how that becomes real ItemStack-component equality).
 *
 * <p>{@code rank} is the soul's ACTUAL rank — never promoted to whatever ceiling the containing
 * gem's {@link SoulGemAcceptanceRule} happens to accept (e.g. a Common Soul Gem holding a Rank F
 * soul still records {@code rank == MobRank.F}, not {@code MobRank.D}).
 */
public record CapturedSoul(UUID soulInstanceId, MobRank rank, SoulCategory category, Identifier sourceEntityType) {
}
