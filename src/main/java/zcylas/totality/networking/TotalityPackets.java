package zcylas.totality.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import zcylas.totality.api.core.component.ComponentSync;
import zcylas.totality.networking.ability.ActivateAbilityPayload;
import zcylas.totality.networking.ability.EquipAbilityPayload;
import zcylas.totality.networking.ability.FavoriteAbilityPayload;
import zcylas.totality.networking.ability.ToggleAbilityPayload;
import zcylas.totality.networking.ability.veinminer.VeinminerKeyPayload;
import zcylas.totality.networking.alchemy.BrewPayload;
import zcylas.totality.networking.alchemy.BrewResultPayload;
import zcylas.totality.networking.alchemy.OpenApothecaryTablePayload;
import zcylas.totality.networking.ancestry.OpenAncestrySelectionPayload;
import zcylas.totality.networking.ancestry.SelectAncestryPayload;
import zcylas.totality.networking.classes.OpenClassSelectionPayload;
import zcylas.totality.networking.classes.SelectClassPayload;
import zcylas.totality.networking.combat.CombatTextPayload;
import zcylas.totality.networking.combat.PowerAttackPayload;
import zcylas.totality.networking.config.ItemSideModePayload;
import zcylas.totality.networking.config.ItemSideModeSyncPayload;
import zcylas.totality.networking.config.SideModePayload;
import zcylas.totality.networking.config.SideModeSyncPayload;
import zcylas.totality.networking.dice.DiceCheckRequestPayload;
import zcylas.totality.networking.dice.DiceRollClickPayload;
import zcylas.totality.networking.dice.DiceRollResultPayload;
import zcylas.totality.networking.fluid.FluidTankModePayload;
import zcylas.totality.networking.inventory.InventoryDropPayload;
import zcylas.totality.networking.inventory.InventoryEquipPayload;
import zcylas.totality.networking.inventory.InventoryUsePayload;
import zcylas.totality.networking.magic.grimoire.SwitchGrimoireSlotPayload;
import zcylas.totality.networking.magic.grimoire.UpdateGrimoirePayload;
import zcylas.totality.networking.mana.SyncManaPayload;
import zcylas.totality.networking.menu.OpenMainMenuPayload;
import zcylas.totality.networking.movement.MovementStaminaPayload;
import zcylas.totality.networking.movement.PowerSprintStatePayload;
import zcylas.totality.networking.movement.ToggleFlightPayload;
import zcylas.totality.networking.notification.SendNotificationPayload;
import zcylas.totality.networking.skills.UnlockMasteryPayload;
import zcylas.totality.networking.stamina.SyncStaminaPayload;
import zcylas.totality.networking.stats.OpenStatusScreenPayload;
import zcylas.totality.networking.stats.SpendAttributePointPayload;

public class TotalityPackets {

    public static void register() {
        serverbound(PayloadTypeRegistry.serverboundPlay());
        clientbound(PayloadTypeRegistry.clientboundPlay());
    }

    private static void serverbound(PayloadTypeRegistry<RegistryFriendlyByteBuf> registry) {
        registry.register(FluidTankModePayload.ID, FluidTankModePayload.CODEC);
        registry.register(SideModePayload.TYPE, SideModePayload.CODEC);
        registry.register(UpdateGrimoirePayload.TYPE, UpdateGrimoirePayload.CODEC);
        registry.register(SwitchGrimoireSlotPayload.TYPE, SwitchGrimoireSlotPayload.STREAM_CODEC);
        registry.register(ItemSideModePayload.TYPE, ItemSideModePayload.CODEC);
        registry.register(BrewPayload.TYPE, BrewPayload.STREAM_CODEC);
        registry.register(SpendAttributePointPayload.TYPE, SpendAttributePointPayload.CODEC);
        registry.register(UnlockMasteryPayload.TYPE, UnlockMasteryPayload.CODEC);
        registry.register(InventoryEquipPayload.TYPE, InventoryEquipPayload.CODEC);
        registry.register(InventoryUsePayload.TYPE, InventoryUsePayload.CODEC);
        registry.register(InventoryDropPayload.TYPE, InventoryDropPayload.CODEC);
        registry.register(ActivateAbilityPayload.TYPE, ActivateAbilityPayload.CODEC);
        registry.register(EquipAbilityPayload.TYPE, EquipAbilityPayload.CODEC);
        registry.register(FavoriteAbilityPayload.TYPE, FavoriteAbilityPayload.CODEC);
        registry.register(VeinminerKeyPayload.TYPE, VeinminerKeyPayload.CODEC);
        registry.register(PowerAttackPayload.TYPE, PowerAttackPayload.CODEC);
        registry.register(SelectAncestryPayload.TYPE, SelectAncestryPayload.STREAM_CODEC);
        registry.register(ToggleFlightPayload.TYPE, ToggleFlightPayload.CODEC);
        registry.register(MovementStaminaPayload.TYPE, MovementStaminaPayload.CODEC);
        registry.register(PowerSprintStatePayload.TYPE, PowerSprintStatePayload.CODEC);
        registry.register(ToggleAbilityPayload.TYPE, ToggleAbilityPayload.STREAM_CODEC);
        registry.register(DiceRollClickPayload.TYPE, DiceRollClickPayload.STREAM_CODEC);
        registry.register(SelectClassPayload.TYPE, SelectClassPayload.STREAM_CODEC);
    }

    private static void clientbound(PayloadTypeRegistry<RegistryFriendlyByteBuf> registry) {
        registry.register(SideModeSyncPayload.TYPE, SideModeSyncPayload.CODEC);
        registry.register(SyncStaminaPayload.TYPE, SyncStaminaPayload.CODEC);
        registry.register(SyncManaPayload.TYPE, SyncManaPayload.CODEC);
        registry.register(ItemSideModeSyncPayload.TYPE, ItemSideModeSyncPayload.CODEC);
        registry.register(ComponentSync.PACKET_TYPE, ComponentSync.STREAM_CODEC);
        registry.register(OpenApothecaryTablePayload.TYPE, OpenApothecaryTablePayload.STREAM_CODEC);
        registry.register(BrewResultPayload.TYPE, BrewResultPayload.STREAM_CODEC);
        registry.register(SendNotificationPayload.TYPE, SendNotificationPayload.CODEC);
        registry.register(OpenStatusScreenPayload.TYPE, OpenStatusScreenPayload.CODEC);
        registry.register(OpenMainMenuPayload.TYPE, OpenMainMenuPayload.CODEC);
        registry.register(OpenAncestrySelectionPayload.TYPE, OpenAncestrySelectionPayload.STREAM_CODEC);
        registry.register(CombatTextPayload.TYPE, CombatTextPayload.CODEC);
        registry.register(DiceCheckRequestPayload.TYPE, DiceCheckRequestPayload.STREAM_CODEC);
        registry.register(DiceRollResultPayload.TYPE,   DiceRollResultPayload.STREAM_CODEC);
        registry.register(OpenClassSelectionPayload.TYPE, OpenClassSelectionPayload.STREAM_CODEC);
    }

    private TotalityPackets() {}
}