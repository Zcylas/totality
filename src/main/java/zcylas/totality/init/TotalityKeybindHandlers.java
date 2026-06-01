package zcylas.totality.init;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.networking.ability.ActivateAbilityPayload;
import zcylas.totality.networking.ability.ClientAbilityManager;
import zcylas.totality.networking.ability.ToggleAbilityPayload;
import zcylas.totality.networking.ability.veinminer.VeinminerKeyPayload;
import zcylas.totality.item.magic.GrimoireItem;
import zcylas.totality.screen.ability.AbilityRadialScreen;
import zcylas.totality.screen.magic.GrimoireRadialScreen;
import zcylas.totality.screen.magic.GrimoireScreen;
import zcylas.totality.screen.menu.MainMenuScreen;

public final class TotalityKeybindHandlers {

    private static boolean lastAbilityKeyHeld = false;

    public static void register() {
        ModKeybinds.register();
        registerGrimoireKeybind();
        registerRadialKeybind();
        registerMenuKeybind();
        registerAbilityKeybind();
        registerVeinminerKeyKeybind();
        registerAbilityRadialKeybind();
    }

    private static void registerGrimoireKeybind() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ModKeybinds.OPEN_GRIMOIRE.consumeClick()) {
                if (client.player == null) return;
                ItemStack main = client.player.getMainHandItem();
                ItemStack off  = client.player.getOffhandItem();
                ItemStack grimoire = main.getItem() instanceof GrimoireItem ? main
                        : off.getItem() instanceof GrimoireItem ? off
                        : ItemStack.EMPTY;
                if (!grimoire.isEmpty()) {
                    client.setScreen(new GrimoireScreen(grimoire));
                }
            }
        });
    }

    private static void registerRadialKeybind() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ModKeybinds.OPEN_RADIAL.consumeClick()) {
                if (client.player == null) return;
                ItemStack main = client.player.getMainHandItem();
                ItemStack off  = client.player.getOffhandItem();
                ItemStack grimoire = main.getItem() instanceof GrimoireItem ? main
                        : off.getItem() instanceof GrimoireItem ? off
                        : ItemStack.EMPTY;
                if (!grimoire.isEmpty()) {
                    client.setScreen(new GrimoireRadialScreen(grimoire));
                }
            }
        });
    }

    private static void registerMenuKeybind() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ModKeybinds.OPEN_MENU.consumeClick()) {
                if (client.player == null) return;
                if (client.screen == null) {
                    client.setScreen(new MainMenuScreen());
                }
            }
        });
    }

    private static void registerAbilityKeybind() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ModKeybinds.USE_ABILITY.consumeClick()) {
                if (client.player == null || client.level == null) return;

                Identifier equippedId = ClientAbilityManager.getEquippedAbility();
                if (equippedId == null) return;
                if (ClientAbilityManager.isOnCooldown(equippedId)) return;

                Ability targeted = AbilityRegistry.get(equippedId);
                if (targeted == null) return;
                if (targeted.getType() == Ability.Type.CHANNELED) return; // handled by hold handler
                AbilityContext context = null;
                if (targeted instanceof zcylas.totality.api.ability.ClientAbilityContext provider) {
                    context = provider.getContext(client, client.player);
                }

                ClientPlayNetworking.send(new ActivateAbilityPayload(
                        targeted.getId(),
                        context != null ? context.pos() : null
                ));
            }
        });
    }

    private static void registerVeinminerKeyKeybind() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            boolean held = ModKeybinds.USE_ABILITY.isDown();
            if (held != lastAbilityKeyHeld) {
                lastAbilityKeyHeld = held;

                // Veinminer hold
                ClientPlayNetworking.send(new VeinminerKeyPayload(held));

                // Channeled ability start/stop
                Identifier equippedId = ClientAbilityManager.getEquippedAbility();
                if (equippedId != null) {
                    Ability ability = AbilityRegistry.get(equippedId);
                    if (ability != null && ability.getType() == Ability.Type.CHANNELED) {
                        ClientPlayNetworking.send(new ToggleAbilityPayload(equippedId, held));
                        ClientAbilityManager.setChanneling(held ? equippedId : null);
                    }
                }
            }
        });
    }

    private static void registerAbilityRadialKeybind() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.screen != null) return;
            com.mojang.blaze3d.platform.Window window = client.getWindow();
            boolean altHeld = com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) ||
                    com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                            org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT);
            if (altHeld && ModKeybinds.USE_ABILITY.isDown()
                    && !ClientAbilityManager.getFavorites().isEmpty()) {
                client.setScreen(new AbilityRadialScreen());
            }
        });
    }

    private TotalityKeybindHandlers() {}
}