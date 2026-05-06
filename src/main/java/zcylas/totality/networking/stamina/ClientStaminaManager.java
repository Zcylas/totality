package zcylas.totality.networking.stamina;

public class ClientStaminaManager {
    private static int stamina    = 100;
    private static int maxStamina = 100;

    public static int getStamina()    { return stamina; }
    public static int getMaxStamina() { return maxStamina; }

    public static void sync(int stamina, int maxStamina) {
        ClientStaminaManager.stamina    = stamina;
        ClientStaminaManager.maxStamina = maxStamina;
    }
}