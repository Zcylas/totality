package zcylas.totality.item.spell_material;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Arcane Focus — satisfies all replaceable spell material components when held
 * in the main hand or off-hand.
 *
 * Right-clicking casts the player's currently selected spell via
 * {@link zcylas.totality.client.spell.ClientSelectedSpellManager}.
 *
 * Register different focus variants with their own cast sound:
 * <pre>
 *   new ArcaneFocusItem(props, SoundEvents.AMETHYST_BLOCK_CHIME)  // Wizard Orb
 *   new ArcaneFocusItem(props, SoundEvents.NOTE_BLOCK_GUITAR.value()) // Bard Guitar
 * </pre>
 */
public class ArcaneFocusItem extends Item {

    @Nullable private final SoundEvent castSound;

    /** Focus with no custom sound — uses the spell's own cast sound. */
    public ArcaneFocusItem(Properties properties) {
        this(properties, null);
    }

    /** Focus with a custom cast sound played every time a spell is cast with it. */
    public ArcaneFocusItem(Properties properties, @Nullable SoundEvent castSound) {
        super(properties);
        this.castSound = castSound;
    }

    @Nullable
    public SoundEvent getCastSound() { return castSound; }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            String selected = zcylas.totality.client.spell.ClientSelectedSpellManager
                    .getSelectedSpell();
            if (selected != null) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new zcylas.totality.networking.item.CastFocusPayload(selected));
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}