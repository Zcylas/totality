package zcylas.totality.entity.npc;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import zcylas.totality.Totality;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.dialogue.DialogueComponents;
import zcylas.totality.api.dialogue.DialogueSessionManager;
import zcylas.totality.api.dialogue.NarrativeFlagsComponent;

/**
 * Dedicated NPC for banking services (account opening, currency exchange, merchant
 * investment). Reuses TotalityNpcEntity's goals for now — temporary humanoid-skin
 * rendering until a custom Blockbench model is built.
 *
 * Overrides mobInteract entirely instead of using the generic dialogueId field —
 * routes to the first-meeting dialogue once per player PER BANKER (each individual
 * Banker instance remembers you separately, since they're a generic profession —
 * meeting the banker in one village doesn't introduce you to the one in the next),
 * then the short repeat greeting on every visit after. Tracked via a narrative flag
 * scoped by this entity's UUID.
 *
 * The Mobile Banking phone-linking flow (see [[project-phone-system]]) is a dialogue
 * choice, not a separate item-on-entity interaction here — an earlier version routed it
 * through this method directly (right-click while holding a Phone), but that required a
 * precise hit on the entity's hitbox and competed with the Phone's own right-click screen,
 * which felt unreliable in testing. All Banker interaction now goes through dialogue.
 */
public class BankerNpcEntity extends TotalityNpcEntity {

    private static final Identifier INTRO_DIALOGUE =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "banker_intro");
    private static final Identifier GREETING_DIALOGUE =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "banker_greeting");

    public BankerNpcEntity(EntityType<? extends BankerNpcEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TotalityNpcEntity.createAttributes();
    }

    private String metFlagKey() {
        return "met_banker:" + getUUID();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp) {
            NarrativeFlagsComponent flags = DialogueComponents.FLAGS.get((ComponentProvider) sp);
            String flagKey = metFlagKey();
            boolean metBefore = flags.hasFlag(flagKey);
            if (!metBefore) flags.setFlag(flagKey, 1);
            DialogueSessionManager.startDialogue(sp, metBefore ? GREETING_DIALOGUE : INTRO_DIALOGUE, this);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
}
