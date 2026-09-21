package zcylas.totality.api.rpg.resources.client;

/**
 * Pure abstraction over "the active local player's native vanilla state," so
 * {@code NativeClientResourceReader}'s normalization/result-construction logic is unit-testable with
 * a synthetic implementation, without launching a Minecraft client. The real production
 * implementation ({@code zcylas.totality.client.resource.MinecraftNativeResourceAccess}) is a thin
 * wrapper over {@code Minecraft.getInstance()} and lives in the client-only implementation layer.
 *
 * <p>Every accessor other than {@link #hasLocalPlayer()} is only ever called by a reader after it has
 * already confirmed {@link #hasLocalPlayer()} is {@code true} for this query — implementations are
 * not required to handle a no-local-player case themselves.
 */
public interface NativeResourceAccess {

    /** Whether an active client world and local player both currently exist. */
    boolean hasLocalPlayer();

    /** The local player's current mechanical Health ({@code Player#getHealth()}). */
    float health();

    /** The local player's current mechanical maximum Health ({@code Player#getMaxHealth()}). */
    float maxHealth();

    /** The local player's current native Food level, 0-20 scale ({@code FoodData#getFoodLevel()}). */
    int foodLevel();

    /** The local player's current native air supply ({@code Entity#getAirSupply()}). */
    int airSupply();

    /** The local player's current native maximum air supply ({@code Entity#getMaxAirSupply()}). */
    int maxAirSupply();
}
