package zcylas.totality.item.vehicle;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.entity.vehicle.SkateboardEntity;
import zcylas.totality.init.ModEntities;

/**
 * The default skateboard as an item: used on the top of a block it sets the board down there, heading the way the
 * player faces (as vanilla places boats). Taking it back: see {@link SkateboardEntity}.
 */
public class SkateboardItem extends Item {

    public SkateboardItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        SkateboardEntity board = ModEntities.SKATEBOARD.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (board == null) return InteractionResult.FAIL;
        Vec3 at = context.getClickLocation();
        board.setInitialPos(at.x, at.y, at.z, player != null ? player.getYRot() : 0.0F);
        if (!level.noCollision(board, board.getBoundingBox())) return InteractionResult.FAIL;
        if (level instanceof ServerLevel serverLevel) {
            EntityType.<SkateboardEntity>createDefaultStackConfig(serverLevel, stack, player).apply(board);
            serverLevel.addFreshEntity(board);
            serverLevel.gameEvent(player, GameEvent.ENTITY_PLACE, at);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}
