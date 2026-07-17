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

    // ── Grimoire modifier-chord state (radial correction pass, Part B) ────────
    // Migrated off the old hold-C-for-HOLD_THRESHOLD-ticks radial trigger onto the SAME
    // Radial-Modifier-chord model Ability/Spell already use — see registerGrimoireKeybind().
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

    /**
     * Radial correction pass, Part B: previously a hold-C-for-{@code HOLD_THRESHOLD}-ticks radial
     * trigger — the exact OLD model Ability/Spell were already migrated off in the prior pass
     * (design document {@code TOTALITY_COMBAT_INPUT_AND_HUD.md} Section 2), left un-migrated for
     * Grimoire at the time. Now uses the SAME Radial-Modifier-chord model, decided once at the
     * press edge, mirroring {@link #registerAbilityKeybind()} exactly: {@link
     * ModKeybinds#OPEN_GRIMOIRE} alone (any hold duration — there is no more continuous "charging"
     * gesture to preserve for a bare press, unlike a channeled Ability) opens the ordinary Grimoire
     * crafting screen (or the Class tab if Shift is held, an unrelated pre-existing quirk left
     * unchanged) on release; {@link ModKeybinds#RADIAL_MODIFIER} + {@link
     * ModKeybinds#OPEN_GRIMOIRE} opens the Grimoire radial immediately and suppresses the normal
     * screen from opening on release. Reuses the EXISTING registered {@link
     * ModKeybinds#OPEN_GRIMOIRE} key mapping (default C) — no second Grimoire key mapping was
     * created. V was never wired to the Grimoire radial in this codebase (confirmed by audit —
     * only {@link #registerBlockKeybind()} reads {@code GLFW_KEY_V}/{@link ModKeybinds#BLOCK}) and
     * remains untouched, exclusively Blocking's key.
     */
    private static void registerGrimoireKeybind() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;
            com.mojang.blaze3d.platform.Window window = client.getWindow();

            // ModKeybinds.isPhysicallyDown, NOT .isDown() — see its javadoc: opening the Grimoire
            // radial screen itself (below) triggers Gui.setScreen's unconditional
            // KeyMapping.releaseAll(), which would otherwise corrupt THIS handler's own tracking
            // on every subsequent tick while the radial stays open.
            boolean radialModifierDown = ModKeybinds.isPhysicallyDown(ModKeybinds.RADIAL_MODIFIER);
            boolean cDown = ModKeybinds.isPhysicallyDown(ModKeybinds.OPEN_GRIMOIRE);

            if (cDown) {
                if (!grimoireWasDown) {
                    // Fresh press — decide chord-vs-normal exactly once, at the edge.
                    grimoireRadialOpened = radialModifierDown;
                    if (grimoireRadialOpened && client.gui.screen() == null) {
                        ItemStack main = client.player.getMainHandItem();
                        ItemStack off  = client.player.getOffhandItem();
                        ItemStack grimoire = main.getItem() instanceof GrimoireItem ? main
                                : off.getItem() instanceof GrimoireItem ? off
                                : ItemStack.EMPTY;
                        if (!grimoire.isEmpty()) {
                            client.gui.setScreen(new GrimoireRadialScreen(grimoire));
                        }
                    }
                }
            } else {
                if (grimoireWasDown && !grimoireRadialOpened && client.gui.screen() == null) {
                    // Release without ever being a radial chord — open the ordinary Grimoire
                    // screen, exactly as a quick tap always did before.
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
    // HOLD_THRESHOLD (the old hold-N-ticks radial trigger) was fully retired by the radial
    // correction pass, Part B — Grimoire was its last remaining user (now migrated to the
    // Modifier-chord model, see registerGrimoireKeybind()); no field remains that reads it.

    /**
     * Phase 4 correction pass, Part B: previously read literal {@code GLFW_KEY_Z}/{@code
     * GLFW_KEY_X} via raw {@code InputConstants.isKeyDown} instead of the registered {@link
     * ModKeybinds#USE_ABILITY}/{@link ModKeybinds#USE_SPELL} key mappings — rebinding either key
     * in the controls menu changed nothing here (it only changed {@code
     * registerVeinminerKeyKeybind}'s already-correct channeled-hold check below, producing a split
     * where the channeled-hold gesture followed the rebound key but ordinary activation and the
     * radial chord silently kept following Z/X).
     *
     * <p>Radial correction pass follow-up: this method now uses {@link
     * ModKeybinds#isPhysicallyDown} rather than {@code KeyMapping.isDown()} for ALL of Ability,
     * Spell, and Radial Modifier — {@code isDown()} gets unconditionally zeroed by {@code
     * Gui.setScreen}'s {@code KeyMapping.releaseAll()} the instant the radial screen opens (see
     * {@link ModKeybinds#isPhysicallyDown}'s javadoc), and this is a GLOBAL per-tick listener that
     * keeps running for as long as the key is held, including every tick the radial stays open
     * afterward — a naive {@code isDown()} read here would misread "released" on the very next
     * tick even though the key never actually moved, corrupting {@code abilityWasDown}/{@code
     * abilityRadialOpened} state out from under the still-open radial screen.
     */
    private static void registerAbilityKeybind() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;

            boolean radialModifierDown = ModKeybinds.isPhysicallyDown(ModKeybinds.RADIAL_MODIFIER);

            // ── Ability (ModKeybinds.USE_ABILITY, default Z, rebindable) ───────
            boolean abilityDown = ModKeybinds.isPhysicallyDown(ModKeybinds.USE_ABILITY);

            if (abilityDown) {
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
            abilityWasDown = abilityDown;

            // ── Spell (ModKeybinds.USE_SPELL, default X, rebindable) ───────────
            boolean spellDown = ModKeybinds.isPhysicallyDown(ModKeybinds.USE_SPELL);

            if (spellDown) {
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
            spellWasDown = spellDown;
        });
    }

    /**
     * Post-review correction: previously polled raw {@code GLFW_KEY_V} directly instead of the
     * registered {@link ModKeybinds#BLOCK} key mapping — {@code BLOCK} was registered (and
     * appeared in Controls) but rebinding it changed nothing here. Now reads {@link
     * ModKeybinds#isPhysicallyDown} (not {@code KeyMapping.isDown()} — see its javadoc: opening
     * any screen, e.g. the Grimoire radial while dual-wielding, zeroes {@code isDown()} via
     * {@code KeyMapping.releaseAll()} regardless of physical key state), so Block press/hold/
     * release all follow whichever key is currently bound, immune to that side effect, exactly
     * like Ability/Spell/Grimoire/Radial Modifier already do.
     */
    private static void registerBlockKeybind() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;

            boolean vDown = ModKeybinds.isPhysicallyDown(ModKeybinds.BLOCK);

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
            // ModKeybinds.isPhysicallyDown, not .isDown() — see its javadoc. This tracker keeps
            // running every tick regardless of what screen is open, including every tick after a
            // radial screen's own KeyMapping.releaseAll() side effect; a naive isDown() read here
            // would misreport a still-held Ability key as "just released" and could desync
            // veinminer/channeled-ability state out from under an actually-continuous hold.
            boolean held = ModKeybinds.isPhysicallyDown(ModKeybinds.USE_ABILITY);
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