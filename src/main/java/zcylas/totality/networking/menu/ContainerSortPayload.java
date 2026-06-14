package zcylas.totality.networking.menu;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.Totality;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent client → server when a sort/transfer button is clicked in any container
 * screen implementing {@link zcylas.totality.api.menu.SortableContainerMenu}.
 *
 * Actions:
 *   SORT         — {@code sortedStacks} carries the client-computed sorted
 *                  order (merged + alphabetical/count/type). Server applies it.
 *   QUICK_STACK  — move inventory items that already exist in the container.
 *   TO_CONTAINER — move all eligible items from inventory to container.
 *   TO_INVENTORY — empty the container into player inventory.
 *
 * For non-SORT actions {@code sortedStacks} is empty.
 */
public record ContainerSortPayload(int action, boolean shiftPressed,
                                   List<ItemStack> sortedStacks)
        implements CustomPacketPayload {

    public static final int SORT          = 0;
    public static final int QUICK_STACK   = 1;
    public static final int TO_CONTAINER  = 2;
    public static final int TO_INVENTORY  = 3;

    public static final Type<ContainerSortPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "container_sort"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ContainerSortPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    ContainerSortPayload::action,
                    ByteBufCodecs.BOOL,
                    ContainerSortPayload::shiftPressed,
                    ItemStack.OPTIONAL_LIST_STREAM_CODEC,
                    ContainerSortPayload::sortedStacks,
                    ContainerSortPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** Send a sort action with client-computed sorted stacks. */
    public static void sendSort(List<ItemStack> sortedStacks, boolean shiftPressed) {
        ClientPlayNetworking.send(new ContainerSortPayload(SORT, shiftPressed, sortedStacks));
    }

    /** Send a non-sort action (no sorted stacks needed). */
    public static void send(int action, boolean shiftPressed) {
        ClientPlayNetworking.send(new ContainerSortPayload(action, shiftPressed, List.of()));
    }
}