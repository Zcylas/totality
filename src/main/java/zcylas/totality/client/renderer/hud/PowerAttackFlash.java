package zcylas.totality.client.renderer.hud;

public class PowerAttackFlash {
    private static float flashTimer = 0f;
    private static final float FLASH_DURATION = 10f; // ticks

    public static void trigger() {
        flashTimer = FLASH_DURATION;
    }

    public static void tick() {
        if (flashTimer > 0) flashTimer--;
    }

    public static float getAlpha() {
        if (flashTimer <= 0) return 0f;
        return (flashTimer / FLASH_DURATION) * 0.4f;
    }

    public static boolean isActive() {
        return flashTimer > 0;
    }

    private PowerAttackFlash() {}
}