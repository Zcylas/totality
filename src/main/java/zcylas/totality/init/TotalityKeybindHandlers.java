package zcylas.totality.init;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.rpg.combat.weapon.TotalityMeleeWeaponItem;
import zcylas.totality.networking.ability.ActivateAbilityPayload;
import zcylas.totality.networking.ability.ClientAbilityManager;
import zcylas.totality.networking.ability.ToggleAbilityPayload;
import zcylas.totality.networking.ability.veinminer.VeinminerKeyPayload;
import zcylas.totality.client.combat.DualWieldTracker;
import zcylas.totality.networking.combat.BlockKeyPayload;
import zcylas.totality.item.magic.GrimoireItem;
import zcylas.totality.screen.ability.AbilityRadialScreen;
import zcylas.totality.screen.magic.GrimoireRadialScreen;
import zcylas.totality.screen.magic.GrimoireScreen;
import zcylas.totality.screen.phone.PhoneScreens;
import zcylas.totality.client.renderer.hud.notification.NotificationManager;

public final class TotalityKeybindHandlers {

    private static boolean lastAbilityKeyHeld = false;

    // ── Grimoire hold state ──────────────────────────────────────────────────
    private static int     grimoireHoldTicks    = 0;
    private static boolean grimoireWasDown      = false;
    private static boolean grimoireRadialOpened = false;

    // ── Block (V) state ──────────────────────────────────────────────────────
    private static boolean blockWasDown   = false;
    private static boolean blockingActive = false;

    public static void register() {
        ModKeybinds.register();
        registerGrimoireKeybind();
        registerMenuKeybind();
        registerAbilityKeybind();
        registerBlockKeybind();
        registerVeinminerKeyKeybind();
        registerAbilityRadialKeybind();
    }

    private static void registerGrimoireKeybind() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;
            com.mojang.blaze3d.platform.Window window = client.getWindow();

            boolean cDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                    window, org.lwjgl.glfw.GLFW.GLFW_KEY_C);

            if (cDown) {
                if (!grimoireWasDown) { grimoireHoldTicks = 0; grimoireRadialOpened = false; }
                grimoireHoldTicks++;
                // Hold → open Grimoire radial
                if (grimoireHoldTicks >= HOLD_THRESHOLD && !grimoireRadialOpened
                        && client.gui.screen() == null) {
                    ItemStack main = client.player.getMainHandItem();
                    ItemStack off  = client.player.getOffhandItem();
                    ItemStack grimoire = main.getItem() instanceof GrimoireItem ? main
                            : off.getItem() instanceof GrimoireItem ? off
                            : ItemStack.EMPTY;
                    if (!grimoire.isEmpty()) {
                        client.gui.setScreen(new GrimoireRadialScreen(grimoire));
                        grimoireRadialOpened = true;
                    }
                }
            } else {
                if (grimoireWasDown && !grimoireRadialOpened && client.gui.screen() == null) {
                    // Quick tap
                    boolean shiftHeld =
                            com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                                    org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) ||
                            com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                                    org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
                    if (shiftHeld) {
                        client.gui.setScreen(new zcylas.totality.screen.character.CharacterScreen(
                                zcylas.totality.screen.character.CharacterScreen.CharacterTab.CLASS));
                    } else {
                        ItemStack main = client.player.getMainHandItem();
                        ItemStack off  = client.player.getOffhandItem();
                        ItemStack grimoire = main.getItem() instanceof GrimoireItem ? main
                                : off.getItem() instanceof GrimoireItem ? off
                                : ItemStack.EMPTY;
                        if (!grimoire.isEmpty()) {
                            client.gui.setScreen(new GrimoireScreen(grimoire));
                        }
                    }
                }
                grimoireHoldTicks    = 0;
                grimoireRadialOpened = false;
            }
            grimoireWasDown = cDown;
        });
    }

    private static void registerMenuKeybind() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ModKeybinds.OPEN_MENU.consumeClick()) {
                if (client.player == null) return;
                if (client.gui.screen() == null) {
                    if (!PhoneScreens.openForEquippedPhone(client)) {
                        NotificationManager.add("No phone equipped.", 0xFFAA8833);
                    }
                }
            }
        });
    }

    // ── Radial modifier chord state (Phase 4 correction pass, Part D) ─────────
    // Replaces the old hold-Z/hold-X-for-HOLD_THRESHOLD-ticks radial trigger (which interrupted
    // channeled abilities/spells once the threshold passed — 26.2 migration §15.6d) with a
    // modifier chord: the Radial Modifier key must already be held at the moment Z/X is FIRST
    // pressed for that press to be treated as "open the radial, don't activate." The decision is
    // made once, at the press-edge, and never re-evaluated for the rest of that hold — pressing
    // the modifier after Z/X has already started, or releasing it mid-hold, has no effect on an
    // already-running press. `abilityRadialOpened`/`spellRadialOpened` are reused (not renamed)
    // from the old implementation: `abilityRadialOpened` is also read by
    // registerVeinminerKeyKeybind() below to suppress channeled-ability start/veinminer for the
    // duration of a radial-chord press, exactly as it suppressed it once the old threshold fired —
    // now suppressed from the very first tick instead of only after ~1 second.
    private static boolean abilityWasDown = false;
    private static boolean abilityRadialOpened = false;
    private static boolean spellWasDown   = false;
    private static boolean spellRadialOpened = false;
    private static final int HOLD_THRESHOLD = 10; // still used by the unrelated Grimoire (C) hold-radial, unchanged by Part D

    private static void registerAbilityKeybind() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;
            com.mojang.blaze3d.platform.Window window = client.getWindow();

            boolean radialModifierDown = ModKeybinds.RADIAL_MODIFIER.isDown();

            // ── Ability (Z key) ───────────────────────────────────────────────
            boolean zDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                    window, org.lwjgl.glfw.GLFW.GLFW_KEY_Z);

            if (zDown) {
                if (!abilityWasDown) {
                    // Fresh press — decide chord-vs-activation exactly once, at the edge.
                    abilityRadialOpened = radialModifierDown;
                    if (abilityRadialOpened && client.gui.screen() == null
                            && !ClientAbilityManager.getAbilityFavorites().isEmpty()) {
                        client.gui.setScreen(new zcylas.totality.screen.ability.AbilityRadialScreen());
                    }
                }
            } else {
                if (abilityWasDown && !abilityRadialOpened && client.gui.screen() == null) {
                    // Quick tap / release without ever being a radial chord — fire equipped ability.
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
                abilityRadialOpened = false;
            }
            abilityWasDown = zDown;

            // ── Spell (X key) ────────────────────────────────────────────────
            boolean xDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                    window, org.lwjgl.glfw.GLFW.GLFW_KEY_X);

            if (xDown) {
                if (!spellWasDown) {
                    spellRadialOpened = radialModifierDown;
                    if (spellRadialOpened && client.gui.screen() == null
                            && !ClientAbilityManager.getSpellFavorites().isEmpty()) {
                        client.gui.setScreen(new zcylas.totality.screen.ability.SpellRadialScreen());
                    }
                }
            } else {
                if (spellWasDown && !spellRadialOpened && client.gui.screen() == null) {
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
                spellRadialOpened = false;
            }
            spellWasDown = xDown;
        });
    }

    private static void registerBlockKeybind() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;
            com.mojang.blaze3d.platform.Window window = client.getWindow();

            boolean vDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                    window, org.lwjgl.glfw.GLFW.GLFW_KEY_V);

            // Start blocking: V just went down with no screen open
            if (vDown && !blockWasDown && client.gui.screen() == null && !blockingActive) {
                InteractionHand blockHand = resolveBlockHand(client.player.getOffhandItem(),
                        client.player.getMainHandItem());
                if (blockHand != null) {
                    client.player.startUsingItem(blockHand);
                    ClientPlayNetworking.send(new BlockKeyPayload(true));
                    blockingActive = true;
                    DualWieldTracker.isDualBlocking = isDualWielding(
                            client.player.getMainHandItem(), client.player.getOffhandItem());
                }
            }

            // Re-apply if V is held but local item use was cancelled (e.g., by right-click)
            if (blockingActive && vDown && client.gui.screen() == null
                    && !client.player.isUsingItem()) {
                InteractionHand blockHand = resolveBlockHand(client.player.getOffhandItem(),
                        client.player.getMainHandItem());
                if (blockHand != null) client.player.startUsingItem(blockHand);
            }

            // Stop blocking: V released, or a screen opened while we were blocking
            if (blockingActive && (!vDown || client.gui.screen() != null)) {
                client.player.stopUsingItem();
                ClientPlayNetworking.send(new BlockKeyPayload(false));
                blockingActive = false;
                DualWieldTracker.isDualBlocking = false;
            }

            blockWasDown = vDown;
        });
    }

    private static InteractionHand resolveBlockHand(ItemStack offhand, ItemStack mainhand) {
        if (offhand.getItem() instanceof ShieldItem) return InteractionHand.OFF_HAND;
        if (mainhand.is(ItemTags.SWORDS) || mainhand.getItem() instanceof TotalityMeleeWeaponItem)
            return InteractionHand.MAIN_HAND;
        return null;
    }

    private static boolean isDualWielding(ItemStack mainhand, ItemStack offhand) {
        boolean mainIsMelee = mainhand.is(ItemTags.SWORDS) || mainhand.getItem() instanceof TotalityMeleeWeaponItem;
        boolean offIsMelee = offhand.is(ItemTags.SWORDS) || offhand.getItem() instanceof TotalityMeleeWeaponItem;
        return mainIsMelee && offIsMelee;
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