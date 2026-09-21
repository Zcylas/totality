package zcylas.totality.api.rpg.resources.presentation;

import java.util.Objects;

/**
 * Minimal presentation metadata a {@link zcylas.totality.api.rpg.resources.PlayerResourceDefinition}
 * may declare. See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §19.1.
 *
 * This Phase 2A shape intentionally omits every field that has no real consumer yet (translation
 * keys, icon, display group, formatter identifier, priority, show-when-full/empty/inactive,
 * smoothing) — those belong to the phase that actually builds contextual HUD selection and
 * generic-resource menus. Only what Health/Food's {@code CORE_CONSTANT} bar presentation and the
 * shared formatter registry actually need is declared here, matching Phase 1's precedent of
 * deferring speculative fields (see the readiness audit's Stage 2 deviation notes).
 */
public record ResourcePresentationDefinition(
        ResourceDisplayConversion displayConversion,
        ResourceDisplayType displayType,
        ResourceHudRole hudRole
) {
    public ResourcePresentationDefinition {
        Objects.requireNonNull(displayConversion, "displayConversion");
        Objects.requireNonNull(displayType, "displayType");
        Objects.requireNonNull(hudRole, "hudRole");
    }
}
