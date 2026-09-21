package zcylas.totality.api.rpg.resources.client.parity;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.client.ClientResourceQueryResult;

/**
 * The narrowest possible read-only seam from the pure Phase 3B-2B polling algorithm
 * ({@link ClientResourceParityPoll}) to the Phase 3B-1 trusted client Resource query façade. The
 * real, production implementation (see {@code zcylas.totality.client.resource.parity.
 * ClientResourceParityCoordinator}) delegates both methods straight to {@code
 * ClientResourceService.INSTANCE}; this interface exists purely so the polling algorithm can be
 * driven by a synthetic fake in tests, without booting a Minecraft client.
 *
 * <p>Deliberately only the two typed query methods ({@code queryScalar}/{@code queryPartitioned})
 * — never the untyped {@code query}, never {@code PlayerResourceService.query(...)}, never raw
 * {@code ClientResourceSyncState}/packet access. The Phase 3B-1 façade remains the only generic
 * read boundary for parity.
 */
public interface ClientResourceParityGenericAccess {

    ClientResourceQueryResult queryScalar(Identifier resourceId);

    ClientResourceQueryResult queryPartitioned(Identifier resourceId);
}
