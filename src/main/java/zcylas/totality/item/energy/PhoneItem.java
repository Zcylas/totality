package zcylas.totality.item.energy;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import zcylas.totality.screen.phone.PhoneFrame;
import zcylas.totality.screen.phone.PhoneScreens;
import zcylas.totality.screen.phone.PhoneSource;

/**
 * Equipment-slot phone item. Will implement {@link zcylas.totality.api.industrial.energy.UEItem}
 * once battery capacity/I/O values are balanced — for now it's a plain item.
 */
public class PhoneItem extends Item {

    private final PhoneFrame frame;

    public PhoneItem(PhoneFrame frame, Properties properties) {
        super(properties.stacksTo(1));
        this.frame = frame;
    }

    public PhoneFrame getFrame() { return frame; }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            ItemStack stack = player.getItemInHand(hand);
            PhoneScreens.openFromHand(stack, PhoneSource.hand(hand), frame);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
