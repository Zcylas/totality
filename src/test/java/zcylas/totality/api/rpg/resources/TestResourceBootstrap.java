package zcylas.totality.api.rpg.resources;

/**
 * Test-only helper that ensures {@code ProductionResourceDefinitions.register()} has run exactly
 * once for the whole test JVM. Production code triggers this registration through
 * {@code Totality.onInitialize()}, which never runs under plain JUnit — any test that needs to
 * observe the real {@code totality:health}/{@code totality:food} production registration must call
 * this first. Guarded by {@code isFrozen()} so multiple test classes calling this is safe and
 * idempotent (a second {@code register()} call would otherwise throw on the duplicate ids).
 */
public final class TestResourceBootstrap {

    public static synchronized void ensureProductionResourcesRegistered() {
        if (!PlayerResourceRegistry.INSTANCE.isFrozen()) {
            ProductionResourceDefinitions.register();
        }
    }

    private TestResourceBootstrap() {}
}
