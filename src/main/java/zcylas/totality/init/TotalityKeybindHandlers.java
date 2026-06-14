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

                // SHIFT + C → jump straight to the Class tab in the character screen
                com.mojang.blaze3d.platform.Window window = client.getWindow();
                boolean shiftHeld =
                        com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                                org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) ||
                                com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                                        org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
                if (shiftHeld) {
                    client.setScreen(new zcylas.totality.screen.character.CharacterScreen(
                            zcylas.totality.screen.character.CharacterScreen.CharacterTab.CLASS));
                    return;
                }

                // C alone → open Grimoire
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

    // ── Hold-to-open radial state ────────────────────────────────────────────
    private static int  abilityHoldTicks  = 0;
    private static boolean abilityWasDown = false;
    private static boolean abilityRadialOpened = false;
    private static int  spellHoldTicks    = 0;
    private static boolean spellWasDown   = false;
    private static boolean spellRadialOpened = false;
    private static final int HOLD_THRESHOLD = 10; // ticks before radial opens

    private static void registerAbilityKeybind() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;
            com.mojang.blaze3d.platform.Window window = client.getWindow();

            // ── Ability (Z key) ───────────────────────────────────────────────
            boolean zDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                    window, org.lwjgl.glfw.GLFW.GLFW_KEY_Z);

            if (zDown) {
                if (!abilityWasDown) { abilityHoldTicks = 0; abilityRadialOpened = false; }
                abilityHoldTicks++;
                // Hold threshold reached — open ability radial
                if (abilityHoldTicks >= HOLD_THRESHOLD && !abilityRadialOpened
                        && client.screen == null
                        && !ClientAbilityManager.getAbilityFavorites().isEmpty()) {
                    client.setScreen(new zcylas.totality.screen.ability.AbilityRadialScreen());
                    abilityRadialOpened = true;
                }
            } else {
                if (abilityWasDown && !abilityRadialOpened && client.screen == null) {
                    // Quick tap — fire equipped ability
                    Identifier equippedId = ClientAbilityManager.getEquippedAbility();
                    if (equippedId != null && !ClientAbilityManager.isOnCooldown(equippedId)) {
                        Ability targeted = AbilityRegistry.get(equippedId);
                        if (targeted != null && targeted.getType() != Ability.Type.CHANNELED) {
                            AbilityContext context = null;
                            if (targeted instanceof zcylas.totality.api.ability.ClientAbilityContext p)
                                context = p.getContext(client, client.player);
                            ClientPlayNetworking.send(new ActivateAbilityPayload(
                                    targeted.getId(), context != null ? context.pos() : null));
                        }
                    }
                }
                abilityHoldTicks = 0;
                abilityRadialOpened = false;
            }
            abilityWasDown = zDown;

            // ── Spell (X key) ────────────────────────────────────────────────
            boolean xDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                    window, org.lwjgl.glfw.GLFW.GLFW_KEY_X);

            if (xDown) {
                if (!spellWasDown) { spellHoldTicks = 0; spellRadialOpened = false; }
                spellHoldTicks++;
                if (spellHoldTicks >= HOLD_THRESHOLD && !spellRadialOpened
                        && client.screen == null
                        && !ClientAbilityManager.getSpellFavorites().isEmpty()) {
                    client.setScreen(new zcylas.totality.screen.ability.SpellRadialScreen());
                    spellRadialOpened = true;
                }
            } else {
                if (spellWasDown && !spellRadialOpened && client.screen == null) {
                    // Quick tap — fire selected spell
                    String selectedSpell = zcylas.totality.client.spell.ClientSelectedSpellManager
                            .getSelectedSpell();
                    if (selectedSpell != null) {
                        Identifier spellId = Identifier.parse(selectedSpell);
                        if (!ClientAbilityManager.isOnCooldown(spellId)) {
                            ClientPlayNetworking.send(new ActivateAbilityPayload(spellId, null));
                        }
                    }
                }
                spellHoldTicks = 0;
                spellRadialOpened = false;
            }
            spellWasDown = xDown;
        });
    }

    private static void registerAbilityRadialKeybind() {
        // Merged into registerAbilityKeybind() above
    }

    private static void registerVeinminerKeyKeybind() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            boolean held = ModKeybinds.USE_ABILITY.isDown();
            if (held != lastAbilityKeyHeld) {
                lastAbilityKeyHeld = held;

                // Don't trigger veinminer/channeled logic if the radial was just open
                if (abilityRadialOpened) return;

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



    private TotalityKeybindHandlers() {}
}