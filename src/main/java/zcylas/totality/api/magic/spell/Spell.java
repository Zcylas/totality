package zcylas.totality.api.magic.spell;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.api.rpg.classes.ClassRegistry;
import zcylas.totality.api.rpg.classes.PlayerClassComponent;
import zcylas.totality.api.rpg.combat.SavingThrow;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.networking.notification.SendNotificationPayload;

import java.util.Objects;

/**
 * Base class for all D&D-style spells.
 *
 * Spells are Abilities with extra metadata:
 *   - spellLevel  : 0 = cantrip (free), 1+ = consumes a spell slot
 *   - school      : SpellSchool (DESTRUCTION, ILLUSION, etc.)
 *   - concentration: if true, broken by taking damage (CON save) or casting another concentration spell
 *   - ritual      : if true, can be cast without a spell slot over 10 extra minutes
 *
 * Source is always {@link Source#SPELL}.
 * sourceDetail is always the school name (shown in the Abilities/Spells tab card).
 *
 * Cantrips (level 0) scale with character level rather than spell slots.
 * Leveled spells consume from {@link zcylas.totality.api.magic.spell.SpellSlotComponent} — not yet implemented.
 */
public abstract class Spell extends Ability {

    private final int spellLevel;
    private final SpellSchool school;
    private final boolean concentration;
    private final boolean ritual;
    private final SpellActionType actionType;
    private final CastType castType;
    private final int castTimeTicks;
    private final java.util.Set<SpellComponent> components;
    private final @org.jetbrains.annotations.Nullable SpellMaterial material;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param id            unique identifier, e.g. {@code totality:eldritch_blast}
     * @param displayName   shown in the Spells tab card
     * @param description   mechanical description of what the spell does
     * @param type          ACTIVE for most spells; TOGGLE for persistent concentration spells;
     *                      CHANNELED for sustained-cast spells
     * @param spellLevel    0 = cantrip, 1–9 = leveled spell
     * @param school        SpellSchool this spell belongs to
     * @param concentration true if active concentration can be broken by damage
     * @param ritual        true if this spell can be ritual-cast (no slot, +10 min)
     * @param cooldownTicks cooldown in ticks; 0 for cantrips (limited only by mana/slots)
     * @param icon          texture path for the ability icon
     * @param flavourText   italic flavour text shown in the detail panel
     */
    /** Backward-compatible constructor — defaults to ACTION, V+S, INSTANT cast. */
    protected Spell(Identifier id, String displayName, String description,
                    Type type, int spellLevel, SpellSchool school,
                    boolean concentration, boolean ritual,
                    int cooldownTicks, Identifier icon, String flavourText) {
        this(id, displayName, description, type, spellLevel, school, concentration, ritual,
                SpellActionType.ACTION,
                java.util.EnumSet.of(SpellComponent.VERBAL, SpellComponent.SOMATIC),
                null, CastType.INSTANT, 0, cooldownTicks, icon, flavourText);
    }

    /** Full constructor. */
    protected Spell(Identifier id, String displayName, String description,
                    Type type, int spellLevel, SpellSchool school,
                    boolean concentration, boolean ritual,
                    SpellActionType actionType,
                    java.util.Set<SpellComponent> components,
                    @org.jetbrains.annotations.Nullable SpellMaterial material,
                    CastType castType,
                    int castTimeTicks,
                    int cooldownTicks, Identifier icon, String flavourText) {
        super(id, displayName, description, type, cooldownTicks, icon,
                Source.SPELL, school.getDisplayName(), flavourText);
        this.spellLevel    = spellLevel;
        this.school        = school;
        this.concentration = concentration;
        this.ritual        = ritual;
        this.actionType    = actionType;
        this.components    = java.util.Collections.unmodifiableSet(
                java.util.EnumSet.copyOf(components.isEmpty()
                        ? java.util.EnumSet.of(SpellComponent.VERBAL) : components));
        this.material      = material;
        this.castType      = castType;
        this.castTimeTicks = castTimeTicks;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int getSpellLevel()                                   { return spellLevel; }
    public SpellSchool getSchool()                               { return school; }
    public SpellActionType getActionType()                       { return actionType; }
    public CastType getCastType()                               { return castType; }
    public int getCastTimeTicks()                               { return castTimeTicks; }
    public java.util.Set<SpellComponent> getComponents()         { return components; }
    public boolean hasComponent(SpellComponent c)                { return components.contains(c); }
    @org.jetbrains.annotations.Nullable
    public SpellMaterial getMaterial() { return material; }

    /**
     * Returns true if the player can satisfy this spell's material requirements.
     * Checks inventory for each required item.
     * TODO: also accept Arcane Focus (skips replaceable check) or Component Pouch.
     *
     * Call from {@link #canActivate} before allowing the cast.
     */
    protected boolean checkMaterials(ServerPlayer player) {
        if (!hasComponent(SpellComponent.MATERIAL) || material == null) return true;
        if (material.requiredItems().isEmpty()) return true;
        if (material.isReplaceable() && hasArcaneFocus(player)) return true;

        java.util.List<String> missing = new java.util.ArrayList<>();
        for (net.minecraft.world.item.Item required : material.requiredItems()) {
            if (!hasItemInPouchOrInventory(player, required)) {
                missing.add(new ItemStack(required).getHoverName().getString());
            }
        }
        if (!missing.isEmpty()) {
            SendNotificationPayload.send(player,
                    "Missing: " + String.join(", ", missing), 0xFFFF4444);
            return false;
        }
        return true;
    }

    protected void consumeMaterials(ServerPlayer player) {
        if (!hasComponent(SpellComponent.MATERIAL) || material == null) return;
        if (material.requiredItems().isEmpty()) return;
        if (material.isReplaceable() && hasArcaneFocus(player)) return;
        for (net.minecraft.world.item.Item required : material.requiredItems()) {
            removeOneFromPouchOrInventory(player, required);
        }
    }

    /** Only the Arcane Focus bypasses replaceable material checks. The pouch is a container, not a bypass. */
    private static boolean hasArcaneFocus(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof zcylas.totality.item.spell_material.ArcaneFocusItem
                || player.getOffhandItem().getItem() instanceof zcylas.totality.item.spell_material.ArcaneFocusItem;
    }

    private static boolean hasItemInPouchOrInventory(ServerPlayer player,
                                                     net.minecraft.world.item.Item item) {
        for (ItemStack ps : player.getInventory().getNonEquipmentItems()) {
            if (ps.isEmpty() || !(ps.getItem() instanceof zcylas.totality.item.spell_material.ComponentPouchItem)) continue;
            var c = ps.get(net.minecraft.core.component.DataComponents.CONTAINER);
            if (c != null) {
                if (c.nonEmptyItemCopyStream().anyMatch(s -> s.is(item))) return true;
            }
        }
        return hasItemInInventory(player, item);
    }

    private static void removeOneFromPouchOrInventory(ServerPlayer player,
                                                      net.minecraft.world.item.Item item) {
        for (ItemStack ps : player.getInventory().getNonEquipmentItems()) {
            if (ps.isEmpty() || !(ps.getItem() instanceof zcylas.totality.item.spell_material.ComponentPouchItem)) continue;
            var c = ps.get(net.minecraft.core.component.DataComponents.CONTAINER);
            if (c == null) continue;
            // Collect all items, shrink the first match, rebuild
            var list = c.nonEmptyItemCopyStream()
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            boolean removed = false;
            for (ItemStack s : list) {
                if (!removed && s.is(item)) { s.shrink(1); removed = true; break; }
            }
            if (removed) {
                var newList = list.stream().filter(s -> !s.isEmpty()).toList();
                ps.set(net.minecraft.core.component.DataComponents.CONTAINER,
                        net.minecraft.world.item.component.ItemContainerContents.fromItems(newList));
                return;
            }
        }
        removeOneFromInventory(player, item);
    }
    private static boolean hasItemInInventory(ServerPlayer player,
                                              net.minecraft.world.item.Item item) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.is(item)) return true;
        }
        return false;
    }

    private static void removeOneFromInventory(ServerPlayer player,
                                               net.minecraft.world.item.Item item) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.is(item)) { stack.shrink(1); return; }
        }
    }

    /** Returns V, S, M string like "V, S, M (a pinch of sulfur)". */
    public String getComponentString() {
        StringBuilder sb = new StringBuilder();
        for (SpellComponent c : SpellComponent.values()) {
            if (components.contains(c)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(c.displayName());
            }
        }
        if (material != null) sb.append(" (").append(material.description()).append(")");
        return sb.toString();
    }

    /** True for cantrips — cast freely with no spell slot. */
    public boolean isCantrip()        { return spellLevel == 0; }

    /**
     * True if maintaining this spell requires concentration.
     * Future: losing concentration (damage → CON save) cancels the effect.
     */
    public boolean isConcentration()  { return concentration; }

    /**
     * True if this spell can be ritual-cast — no slot consumed, but 10 extra minutes.
     * Future: linked to the Ritual Arts skill and a dedicated ritual screen.
     */
    public boolean isRitual()         { return ritual; }

    /**
     * Human-readable spell level for the UI.
     * e.g. "Cantrip", "1st-level", "2nd-level", "3rd-level", "4th-level" ...
     */
    /**
     * Returns the spell save DC for this caster:
     * 8 + proficiency bonus + spellcasting ability modifier.
     * Uses {@link #getSpellcastingAbility} so it automatically picks the right
     * ability (INT/CHA/WIS) based on the player's primary class.
     */
    protected int getSpellSaveDc(ServerPlayer player) {
        return SavingThrow.spellSaveDc(player, getSpellcastingAbility(player));
    }

    /**
     * Returns the spellcasting ability for this player based on their primary class.
     * Wizards and Artificers use INT, Sorcerers/Warlocks/Bards/Paladins use CHA,
     * Clerics/Druids/Rangers use WIS. Defaults to INT if no class is set.
     *
     * Most spells should call this rather than hardcoding an ability score, so
     * that multi-class characters automatically use the right modifier.
     */
    protected AbilityScore getSpellcastingAbility(ServerPlayer player) {
        PlayerClassComponent classComp = ClassComponents.get(player);
        if (classComp == null) return AbilityScore.INT;

        var primaryId = classComp.getPrimaryClassId();
        if (primaryId == null) return AbilityScore.INT;

        return ClassRegistry.get(primaryId)
                .map(data -> data.spellcastingAbility())
                .filter(Objects::nonNull)
                .orElse(AbilityScore.INT);
    }

    public String getLevelDisplay() {
        return switch (spellLevel) {
            case 0  -> "Cantrip";
            case 1  -> "1st-level";
            case 2  -> "2nd-level";
            case 3  -> "3rd-level";
            default -> spellLevel + "th-level";
        };
    }
}