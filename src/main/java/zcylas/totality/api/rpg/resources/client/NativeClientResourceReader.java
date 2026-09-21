package zcylas.totality.api.rpg.resources.client;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.external.HealthResourceAdapter;

import java.util.Objects;

/**
 * Pure Health/Breath reader for the client Resource façade. Reads only through the injected
 * {@link NativeResourceAccess} — never touches {@code Minecraft}/client classes directly — so this
 * class is unit-testable with a synthetic access implementation, without launching a client. Real
 * production wiring supplies a {@code Minecraft.getInstance()}-backed access implementation from the
 * client-only layer ({@code zcylas.totality.client.resource.MinecraftNativeResourceAccess}).
 *
 * <p>Reuses {@link HealthResourceAdapter#toUnits} for Health's fixed-point rounding — the exact same
 * conversion the server-side adapter uses — so no second rounding/scale convention is introduced.
 *
 * <p><b>Corrected 2026-09-17 (real-client manual test correction):</b> this reader no longer answers
 * {@code totality:food} queries at all. It originally did (Food was {@code EXTERNAL_ADAPTER}, and
 * this class read vanilla {@code FoodData} directly, 0-20), but the 2026-09-17 Food 0-100 migration
 * made Food a real {@code GENERIC_COMPONENT} resource without updating this class or its production
 * registration — real-client testing then found the HUD silently displaying vanilla's lossy
 * compatibility mirror (20/20, 10/20) instead of the true resource (100/100, 50/100). The fix is at
 * the registration site ({@code zcylas.totality.client.resource.TotalityClientResourceReaders}, now
 * wired to the generic-synchronized reader for Food); this class no longer even has a Food branch to
 * accidentally re-register, so it structurally cannot regress into answering for Food again. A Food
 * query reaching this reader now falls through to the final {@code CLIENT_SOURCE_NOT_CONFIGURED}
 * case below, exactly like any other resource id it doesn't recognize.
 *
 * <p><b>Malformed-source safety (Phase 3B-1 external review correction, 2026-07-23):</b> a native
 * owner can report a value this reader cannot represent as a valid
 * {@link ClientResourceQueryResult.Scalar} (a non-finite Health float, a fixed-point conversion
 * overflow, a non-positive Breath maximum, ...). Every such case returns
 * {@link ClientResourceUnavailableReason#MALFORMED_SOURCE_STATE} rather than throwing out of the
 * public façade or fabricating a value — see {@link #safeScalar} and {@link #breath}. Health is never
 * clamped into range on the way there (its authoritative server adapter defines no current-value
 * clamp policy); Breath alone clamps, exactly mirroring {@code BreathResourceAdapter.normalize}'s own
 * documented policy.
 */
public final class NativeClientResourceReader implements ClientResourceReader {

    private final NativeResourceAccess access;

    public NativeClientResourceReader(NativeResourceAccess access) {
        this.access = Objects.requireNonNull(access, "access");
    }

    @Override
    public ClientResourceQueryResult query(PlayerResourceDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        Identifier id = definition.id();
        if (!access.hasLocalPlayer()) {
            return ClientResourceQueryResult.unavailable(id, ClientResourceUnavailableReason.NO_LOCAL_PLAYER);
        }
        if (id.equals(PlayerResourceIds.HEALTH)) {
            return health(id, definition.unitScale());
        }
        if (id.equals(PlayerResourceIds.BREATH)) {
            return breath(id, definition.unitScale());
        }
        // Registered under this reader for a resource id it doesn't actually know how to answer —
        // should not occur given production wiring, but never fabricated as a numeric value.
        return ClientResourceQueryResult.unavailable(id, ClientResourceUnavailableReason.CLIENT_SOURCE_NOT_CONFIGURED);
    }

    /**
     * Health: converts through {@link HealthResourceAdapter#toUnits}, the exact server-adapter
     * conversion. That method itself throws {@link IllegalArgumentException} for a non-finite input
     * (or an invalid {@code unitScale}, which cannot occur here since the definition already
     * validated it) and {@link ArithmeticException} for a genuine fixed-point overflow — both are
     * narrowly caught here and reported as {@link ClientResourceUnavailableReason#MALFORMED_SOURCE_STATE}
     * rather than escaping the public façade. Never clamped: Health's authoritative adapter defines
     * no current-value clamp policy.
     */
    private ClientResourceQueryResult health(Identifier id, long unitScale) {
        long current;
        long maximum;
        try {
            current = HealthResourceAdapter.toUnits(access.health(), unitScale);
            maximum = HealthResourceAdapter.toUnits(access.maxHealth(), unitScale);
        } catch (IllegalArgumentException | ArithmeticException malformed) {
            return ClientResourceQueryResult.unavailable(id, ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE);
        }
        return safeScalar(id, current, maximum, 0L, unitScale);
    }

    /**
     * Mirrors {@code BreathResourceAdapter.normalize}'s policy exactly: a non-positive native maximum
     * cannot be represented as a valid Breath snapshot at all and must never become a fabricated
     * {@code 0/0} success — it is reported as {@link ClientResourceUnavailableReason#MALFORMED_SOURCE_STATE}.
     * Only once the maximum is confirmed positive is the current value clamped into
     * {@code [0, maximum]} (the negative drowning-timer range becomes {@code 0}; a value above
     * maximum becomes {@code maximum}) — the one case among these three native Resources whose
     * authoritative adapter explicitly defines a current-value clamp.
     */
    private ClientResourceQueryResult breath(Identifier id, long unitScale) {
        int rawCurrent = access.airSupply();
        int rawMaximum = access.maxAirSupply();
        if (rawMaximum <= 0) {
            return ClientResourceQueryResult.unavailable(id, ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE);
        }
        long current = Math.max(0, Math.min(rawCurrent, rawMaximum));
        return safeScalar(id, current, rawMaximum, 0L, unitScale);
    }

    /**
     * Constructs a {@link ClientResourceQueryResult.Scalar}, translating a validation failure from
     * its own compact constructor (negative quantities, {@code current > maximum + overflow}, or an
     * invalid {@code unitScale}) into {@link ClientResourceUnavailableReason#MALFORMED_SOURCE_STATE}
     * instead of letting the exception escape the public façade. This is the single place Health's
     * "current exceeds maximum" case is caught — no separate clamping or range-checking logic is
     * duplicated for it.
     */
    private static ClientResourceQueryResult safeScalar(Identifier id, long current, long maximum, long overflow, long unitScale) {
        try {
            return new ClientResourceQueryResult.Scalar(
                    id, current, maximum, overflow, unitScale, ClientResourceSource.NATIVE_CLIENT_VIEW, ClientResourceTrust.FRESH);
        } catch (IllegalArgumentException | ArithmeticException malformed) {
            return ClientResourceQueryResult.unavailable(id, ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE);
        }
    }
}
