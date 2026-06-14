package zcylas.totality.api.magic.spell.destruction;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.magic.spell.CastType;
import zcylas.totality.api.magic.spell.Spell;
import zcylas.totality.api.magic.spell.SpellActionType;
import zcylas.totality.api.magic.spell.SpellSchool;
import zcylas.totality.entity.magic.FireballProjectileEntity;

/**
 * Fireball — 3rd level Destruction spell. Available to Wizard and Sorcerer.
 *
 * Fires a slow-moving projectile that explodes on impact in a 6-block radius.
 * Each entity in the blast makes a DEX saving throw:
 *   Fail: 8d6 Fire damage
 *   Save: 4d6 Fire damage (half)
 *
 * Consumes a 3rd-level spell slot (or higher).
 *
 * Spell save DC = 8 + proficiency + spellcasting ability modifier,
 * resolved via {@link #getSpellSaveDc} which picks INT/CHA/WIS from the caster's class.
 */
public class FireballSpell extends Spell {

    public FireballSpell() {
        super(
                Identifier.fromNamespaceAndPath("totality", "fireball"),
                "Fireball",
                "You hurl a bright streak that blossoms into an explosion of flame. " +
                        "Each creature in a 6-block radius must make a DEX saving throw. " +
                        "Fail: 8d6 Fire damage. Save: half damage.",
                Type.ACTIVE,
                3,
                SpellSchool.DESTRUCTION,
                false,
                false,
                SpellActionType.ACTION,
                java.util.EnumSet.of(
                        zcylas.totality.api.magic.spell.SpellComponent.VERBAL,
                        zcylas.totality.api.magic.spell.SpellComponent.SOMATIC,
                        zcylas.totality.api.magic.spell.SpellComponent.MATERIAL),
                zcylas.totality.api.magic.spell.SpellMaterial.replaceableItems(
                        java.util.List.of(
                                zcylas.totality.init.items.SpellComponentItems.BAT_GUANO,
                                zcylas.totality.init.items.SpellComponentItems.SULPHUR_DUST),
                        "a tiny ball of bat guano and sulphur"),
                CastType.INSTANT,
                0,
                100,  // 5s cooldown — powerful 3rd level spell
                Identifier.fromNamespaceAndPath("totality", "textures/ability/spell/fireball.png"),
                "A bright streak leaves your finger."
        );
    }

    @Override public boolean isDefault() { return true; }

    @Override
    public boolean canActivate(ServerPlayer player, @Nullable AbilityContext context) {
        return checkMaterials(player);
    }

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext context) {
        consumeMaterials(player);

        int dc = getSpellSaveDc(player);
        FireballProjectileEntity fireball = FireballProjectileEntity.create(
                player.level(), player, dc);

        player.level().playSound(null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE,
                net.minecraft.sounds.SoundSource.PLAYERS,
                1.5f, 0.8f);

        player.level().addFreshEntity(fireball);
    }
}