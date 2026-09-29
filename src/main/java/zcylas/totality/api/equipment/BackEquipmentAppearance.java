package zcylas.totality.api.equipment;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.Totality;

/**
 * A read-only, render-only mirror of a player's Back slot, so every client that can see the player (not only its
 * owner) can draw what is worn on the back. {@link PlayerEquipmentComponent} stays the one authority: it writes this
 * mirror whenever the Back slot changes, loads or is copied on respawn; nothing reads it for gameplay. Not persistent
 * (the component is); synced to the player and everyone tracking them by Fabric's attachment sync.
 * <p>Respawn: the client moves only {@code copyOnDeath} attachments to its new player entity, so the mirror is marked
 * copyOnDeath, and after every respawn it is written again from the component, which decides what is kept.
 */
public final class BackEquipmentAppearance {

    public static final AttachmentType<ItemStack> BACK = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "back_appearance"),
            builder -> builder.syncWith(ItemStack.OPTIONAL_STREAM_CODEC, AttachmentSyncPredicate.all()).copyOnDeath());

    private BackEquipmentAppearance() {}

    public static void register() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                mirror(newPlayer, EquipmentComponents.get(newPlayer).getItem(PlayerEquipmentComponent.IDX_BACK)));
    }

    static void mirror(ServerPlayer player, ItemStack back) {
        if (back.isEmpty()) {
            player.removeAttached(BACK);
        } else {
            player.setAttached(BACK, back.copy());
        }
    }

    /** What {@code entity} wears on its back (as last synced), or empty. */
    public static ItemStack get(Entity entity) {
        ItemStack stack = entity.getAttached(BACK);
        return stack == null ? ItemStack.EMPTY : stack;
    }
}
