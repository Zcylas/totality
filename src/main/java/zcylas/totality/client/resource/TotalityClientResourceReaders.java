package zcylas.totality.client.resource;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.client.ClientResourceReader;
import zcylas.totality.api.rpg.resources.client.ClientResourceService;
import zcylas.totality.api.rpg.resources.client.GenericSyncClientResourceReader;
import zcylas.totality.api.rpg.resources.client.NativeClientResourceReader;
import zcylas.totality.networking.resource.ClientResourceSyncBridge;

/**
 * Phase 3B-1 production wiring: registers the client Resource façade's reader strategies once, at
 * client startup, so {@link ClientResourceService#INSTANCE} is fully usable by a future HUD, menu,
 * radial, tooltip, or debug consumer without any further wiring. Originally registration-only ("no
 * production consumer queries the façade yet"), but {@code TotalityHudRenderer}'s Phase 3C migration
 * later made the HUD a real consumer for Mana/Stamina/Rage, and the 2026-09-17 Food 0-100 migration
 * made it one for Food too.
 *
 * <p><b>Corrected 2026-09-17 (real-client manual test correction):</b> {@code totality:food} is now
 * registered with the generic-synchronized reader, not the native reader. Food migrated from
 * {@code EXTERNAL_ADAPTER} to {@code GENERIC_COMPONENT} authority, but this registration was never
 * updated at the time — the native reader kept answering Food queries straight out of vanilla's
 * {@code FoodData} (0-20), so the HUD (and anything else querying {@code totality:food} through the
 * façade) silently displayed the lossy compatibility mirror instead of the true resource. Confirmed
 * by real-client testing: the HUD showed 20/20 at full Food and 10/20 after
 * {@code /totality food set 50}, both exactly the vanilla mirror, not the true 100/100 or 50/100.
 * Health and Breath remain on the native reader — they are still genuinely {@code EXTERNAL_ADAPTER}.
 */
@Environment(EnvType.CLIENT)
public final class TotalityClientResourceReaders {

    public static void register() {
        ClientResourceReader nativeReader = new NativeClientResourceReader(MinecraftNativeResourceAccess.INSTANCE);
        ClientResourceService.INSTANCE.registerReader(PlayerResourceIds.HEALTH, nativeReader);
        ClientResourceService.INSTANCE.registerReader(PlayerResourceIds.BREATH, nativeReader);

        // Food joined this generic-synchronized group in the 2026-09-17 real-client correction — see
        // this class's own Javadoc. It must never move back to nativeReader above.
        ClientResourceReader genericReader = new GenericSyncClientResourceReader(ClientResourceSyncBridge.INSTANCE);
        ClientResourceService.INSTANCE.registerReader(PlayerResourceIds.FOOD, genericReader);
        ClientResourceService.INSTANCE.registerReader(PlayerResourceIds.MANA, genericReader);
        ClientResourceService.INSTANCE.registerReader(PlayerResourceIds.STAMINA, genericReader);
        ClientResourceService.INSTANCE.registerReader(PlayerResourceIds.SPELL_SLOTS, genericReader);
        ClientResourceService.INSTANCE.registerReader(PlayerResourceIds.RAGE, genericReader);
    }

    private TotalityClientResourceReaders() {}
}
