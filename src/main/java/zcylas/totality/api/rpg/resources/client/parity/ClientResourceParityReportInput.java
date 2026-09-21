package zcylas.totality.api.rpg.resources.client.parity;

import zcylas.totality.api.rpg.resources.client.ClientResourceTrust;
import zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason;

import java.util.Objects;

/**
 * Pure, Minecraft/Fabric-independent input vocabulary for the Phase 3B-3 on-demand parity inspection
 * report. This is the conversion boundary the task's external-review correction required: the impure
 * client command boundary ({@code ClientResourceParityInspectionCommand}) is the only code that ever
 * touches {@code net.minecraft.resources.Identifier}, {@code ClientResourceService},
 * {@code ClientResourceParityObservations}, {@code ClientResourceQueryResult}, or
 * {@code ClientResourceParityObservation} — it converts each of those live, Identifier-carrying
 * results into one of these plain-{@code String}-keyed, already-bounded records <b>before</b> handing
 * them to the pure {@link ClientResourceParityReportAssembler}.
 *
 * <p>Reuses already-pure existing value types where appropriate, per the correction's explicit
 * instruction — {@link ClientResourceTrust} and {@link ClientResourceUnavailableReason} (both zero-
 * import enums), and, on the shadow-parity side, {@link ClientResourceParityClassification} and
 * {@link ClientResourceParitySummary} (neither imports anything beyond {@code java.util}/each other)
 * — rather than re-encoding any of them into a second, redundant vocabulary.
 *
 * <p>No file in this sealed hierarchy imports {@code net.minecraft.*}, {@code net.fabricmc.*},
 * {@code FabricLoader}, any command API, {@code Minecraft}, {@code LocalPlayer}, any networking type,
 * any legacy manager, or any logger — confirmed by this package's own import-scan regression test.
 */
public sealed interface ClientResourceParityReportInput {

    /** Hard cap on a resource id's rendered length — mirrors {@link ClientResourceParityReportLine}'s
     *  own bounding philosophy at the input boundary, not just the final rendered line. */
    int MAX_RESOURCE_ID_LENGTH = 128;

    /** Hard cap on a bounded diagnostic token (an exception's simple class name) — never a full
     *  message, stack trace, or arbitrary object text. */
    int MAX_TOKEN_LENGTH = 100;

    String resourceId();

    /** Health/Food/Breath: the trusted native façade's result, already converted to plain types. No
     *  {@link ClientResourceParityClassification} field exists anywhere on this record — a native
     *  Resource structurally cannot carry a parity classification (the external-review correction's
     *  Blocker 2: the parity tracker remains the sole classification owner, and native Resources are
     *  never observed by it at all). */
    record Native(String resourceId, Status status) implements ClientResourceParityReportInput {
        public Native {
            resourceId = requireBoundedId(resourceId);
            Objects.requireNonNull(status, "status");
        }

        public sealed interface Status {
            record Available(long currentUnits, long maximumUnits, long overflowUnits, long unitScale, ClientResourceTrust trust)
                    implements Status {
                public Available {
                    Objects.requireNonNull(trust, "trust");
                    if (unitScale < 1) {
                        throw new IllegalArgumentException("unitScale must be >= 1, was " + unitScale);
                    }
                    if (currentUnits < 0 || maximumUnits < 0 || overflowUnits < 0) {
                        throw new IllegalArgumentException("currentUnits/maximumUnits/overflowUnits must be >= 0");
                    }
                }
            }

            record Unavailable(ClientResourceUnavailableReason reason) implements Status {
                public Unavailable { Objects.requireNonNull(reason, "reason"); }
            }

            /** The native façade's own reader threw a {@code RuntimeException} while being queried
             *  (first correction pass, Blocker 3) — never propagated into the report; only a bounded
             *  exception simple-class name survives. Distinct from {@link ConversionError}: this
             *  variant means the accessor call itself threw, before any {@code ClientResourceQueryResult}
             *  was ever returned. */
            record AccessError(String exceptionSimpleName) implements Status {
                public AccessError { exceptionSimpleName = requireBoundedToken(exceptionSimpleName); }
            }

            /** The accessor call succeeded and returned a {@code ClientResourceQueryResult}, but
             *  converting that result into this pure input vocabulary threw a {@code RuntimeException}
             *  (second correction pass, Blocker 2) — e.g. the accessor returned {@code null} instead of
             *  a real result. Deliberately a distinct variant from {@link AccessError}, never a second
             *  meaning overloaded onto it: the two represent different failing boundaries (the accessor
             *  itself vs. the conversion step immediately after it), and a reader must never have to
             *  guess which one actually failed. */
            record ConversionError(String exceptionSimpleName) implements Status {
                public ConversionError { exceptionSimpleName = requireBoundedToken(exceptionSimpleName); }
            }
        }
    }

    /** Mana/Stamina/Standard Spell Slots/Rage: the existing shadow-parity observation, already
     *  converted to plain types. {@code remainingLabel} is {@code true} only for Standard Spell
     *  Slots — see {@link ClientResourceParityReportAssembler}'s own Javadoc for why that Resource's
     *  numeric label must say "remaining," never "used." */
    record ShadowParity(String resourceId, Status status, boolean remainingLabel) implements ClientResourceParityReportInput {
        public ShadowParity {
            resourceId = requireBoundedId(resourceId);
            Objects.requireNonNull(status, "status");
        }

        public sealed interface Status {
            /** The tracker's own, already-decided classification and summaries — echoed verbatim,
             *  never recomputed. The assembler cannot independently classify a shadow Resource: this
             *  is the only place a {@link ClientResourceParityClassification} can enter a shadow
             *  line, and it is always supplied here, from the caller's own already-existing
             *  observation, never invented by the assembler. */
            record Observed(
                    ClientResourceParityClassification classification,
                    ClientResourceParitySummary genericSummary,
                    ClientResourceParitySummary legacySummary,
                    Long firstMismatchTick,
                    long lastObservedTick
            ) implements Status {
                public Observed {
                    Objects.requireNonNull(classification, "classification");
                    Objects.requireNonNull(genericSummary, "genericSummary");
                    Objects.requireNonNull(legacySummary, "legacySummary");
                    if (lastObservedTick < 0) {
                        throw new IllegalArgumentException("lastObservedTick must be >= 0, was " + lastObservedTick);
                    }
                    if (firstMismatchTick != null && firstMismatchTick < 0) {
                        throw new IllegalArgumentException("firstMismatchTick must be >= 0, was " + firstMismatchTick);
                    }
                }
            }

            /** No observation has ever been recorded for this Resource (before the first client tick
             *  with a local player, or between worlds) — never a fabricated classification. */
            record NoObservationYet() implements Status {}

            /** The shadow-parity observation accessor threw a {@code RuntimeException} while being
             *  looked up (first correction pass, Blocker 3) — same bounded-token treatment as
             *  {@link Native.Status.AccessError}. Distinct from {@link ConversionError}: this variant
             *  means the lookup call itself threw, before any {@code Optional<ClientResourceParityObservation>}
             *  was ever returned. */
            record AccessError(String exceptionSimpleName) implements Status {
                public AccessError { exceptionSimpleName = requireBoundedToken(exceptionSimpleName); }
            }

            /** The lookup call succeeded and returned an {@code Optional<ClientResourceParityObservation>},
             *  but converting that result into this pure input vocabulary threw a {@code RuntimeException}
             *  (second correction pass, Blocker 2) — e.g. the lookup returned {@code null} instead of a
             *  real {@code Optional}. Deliberately a distinct variant from {@link AccessError}, mirroring
             *  {@link Native.Status.ConversionError}'s own reasoning. */
            record ConversionError(String exceptionSimpleName) implements Status {
                public ConversionError { exceptionSimpleName = requireBoundedToken(exceptionSimpleName); }
            }
        }
    }

    private static String requireBoundedId(String resourceId) {
        Objects.requireNonNull(resourceId, "resourceId");
        if (resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        if (resourceId.length() > MAX_RESOURCE_ID_LENGTH) {
            return resourceId.substring(0, MAX_RESOURCE_ID_LENGTH);
        }
        return resourceId;
    }

    private static String requireBoundedToken(String token) {
        Objects.requireNonNull(token, "token");
        if (token.length() > MAX_TOKEN_LENGTH) {
            return token.substring(0, MAX_TOKEN_LENGTH);
        }
        return token;
    }
}
