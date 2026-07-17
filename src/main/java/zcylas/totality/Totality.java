package zcylas.totality;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.entity.npc.BankerNpcEntity;
import zcylas.totality.entity.npc.TotalityNpcEntity;
import zcylas.totality.api.ability.AbilityServerTick;
import zcylas.totality.api.combat.condition.ConditionServerTick;
import zcylas.totality.api.combat.condition.Conditions;
import zcylas.totality.api.combat.damage.DamageTypes;
import zcylas.totality.api.core.util.ServerScheduler;
import zcylas.totality.api.magic.spell.SpellRegistry;
import zcylas.totality.api.mob.stats.MobStatBlockLoader;
import zcylas.totality.api.ritual.RitualRecipeRegistry;
import zcylas.totality.api.rpg.ancestry.OriginRegistry;
import zcylas.totality.api.rpg.ancestry.SpeciesRegistry;
import zcylas.totality.api.rpg.classes.TotalityClasses;
import zcylas.totality.api.rpg.skills.core.OneHandedSkillHandler;
import zcylas.totality.api.rpg.skills.mining.MiningSkillEvents;
import zcylas.totality.api.rpg.skills.alchemy.AlchemyEffects;
import zcylas.totality.api.rpg.combat.weapon.TwoHandedRestriction;
import zcylas.totality.api.rpg.combat.weapon.WeaponStaminaHandler;
import zcylas.totality.init.*;
import zcylas.totality.init.magic.MagicRunes;
import zcylas.totality.item.energy.UmbraVisorItem;
import zcylas.totality.menu.energy.ElectricFurnaceMenu;
import zcylas.totality.menu.generator.GeneratorMenu;
import zcylas.totality.networking.TotalityPackets;
import zcylas.totality.networking.TotalityServerPacketHandlers;
import zcylas.totality.networking.ability.ActivateAbilityHandler;
import zcylas.totality.networking.ability.EquipAbilityHandler;
import zcylas.totality.networking.ability.SelectSpellHandler;
import zcylas.totality.networking.ability.FavoriteAbilityHandler;
import zcylas.totality.networking.ability.ToggleAbilityHandler;
import zcylas.totality.networking.ability.veinminer.VeinminerKeyHandler;
import zcylas.totality.networking.ancestry.SelectAncestryHandler;
import zcylas.totality.networking.ancestry.SelectAncestryPayload;
import zcylas.totality.networking.classes.SelectClassHandler;
import zcylas.totality.networking.inventory.InventoryActionHandler;
import zcylas.totality.networking.mana.ManaServerTick;
import zcylas.totality.networking.movement.MovementStaminaHandler;
import zcylas.totality.networking.movement.PowerSprintStateHandler;
import zcylas.totality.networking.movement.ToggleFlightHandler;
import zcylas.totality.networking.skills.UnlockMasteryHandler;
import zcylas.totality.networking.stamina.StaminaServerTick;
import zcylas.totality.networking.stats.SpendAttributePointHandler;

public class Totality implements ModInitializer {
	public static final String MOD_ID = "totality";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// ── Core registration ─────────────────────────────────────────────────
		registerInits();
		registerEntities();
		registerMenus();
		registerAttributes();
		registerLookups();

		// ── Networking ────────────────────────────────────────────────────────
		TotalityPackets.register();
		TotalityServerPacketHandlers.register();

		// ── APIs ──────────────────────────────────────────────────────────────
		registerApi();

		// ── RPG systems ───────────────────────────────────────────────────────
		registerCombatApi();
		registerSkillEvents();
		registerSkillHandlers();
		registerAbilityClasses();
		registerRPGHandlers();
		registerItemHandlers();

		// ── Ticks ─────────────────────────────────────────────────────────────
		registerServerTickEvents();

		// ── World ─────────────────────────────────────────────────────────────
		TotalityBiomeModifications.register();
	}

	private void registerInits(){
		ModComponents.register();
		ModItems.register();
		ModBlocks.register();
		zcylas.totality.worldgen.ModFeatures.register();
		ModBlockEntities.register();
		ModEntities.register();
		MagicRunes.register();
		ModEffects.register();
		ModSounds.register();
		ModLootTables.register();
		TotalityCommands.register();
		RitualRecipeRegistry.register();
		zcylas.totality.api.dialogue.DialogueComponents.register();
		zcylas.totality.api.quest.QuestComponents.register();
		ResourceManagerHelper.get(PackType.SERVER_DATA)
				.registerReloadListener(new MobStatBlockLoader());
		ResourceManagerHelper.get(PackType.SERVER_DATA)
				.registerReloadListener(zcylas.totality.api.dialogue.DialogueRegistry.INSTANCE);
		ResourceManagerHelper.get(PackType.SERVER_DATA)
				.registerReloadListener(zcylas.totality.entity.npc.NpcNameRegistry.INSTANCE);
		ResourceManagerHelper.get(PackType.SERVER_DATA)
				.registerReloadListener(zcylas.totality.api.quest.QuestRegistry.INSTANCE);
		zcylas.totality.api.shop.ShopRegistry.register();
		zcylas.totality.api.shop.MerchantRuntimeRegistry.register();
		zcylas.totality.api.shop.TradeSessionManager.register();
		zcylas.totality.api.economy.value.ItemValueRegistry.register();
		zcylas.totality.api.shop.MerchantSellVerification.register();
		zcylas.totality.api.shop.assortment.ProvisionerAssortmentRegistry.register();
		zcylas.totality.api.shop.ProvisionerVerification.register();
		zcylas.totality.api.shop.TradingScreenVerification.register();
		zcylas.totality.api.rpg.combat.PowerAttackVerification.register();
		zcylas.totality.networking.combat.OffhandAttackVerification.register();
		ModEvents.register();
	}

	private void registerLookups(){
		FluidStorage.SIDED.registerForBlockEntity(
				(blockEntity, direction) -> blockEntity.getFluidStorage(direction),
				ModBlockEntities.FLUID_TANK
		);
	}
	private void registerEntities(){
		var ignored2 = ModEntities.GRIMOIRE_PROJECTILE;
	}

	private void registerMenus(){
		var ignored = GeneratorMenu.TYPE;
		var _ = ElectricFurnaceMenu.TYPE;
		var _2 = zcylas.totality.menu.ComponentPouchMenu.TYPE;
		var _3 = zcylas.totality.menu.equipment.AccessoryInventoryMenu.TYPE;
	}

	private void registerServerTickEvents(){
		ManaServerTick.register();
		StaminaServerTick.register();
		AbilityServerTick.register();
		ConditionServerTick.register();
		ServerScheduler.register();
		zcylas.totality.api.rpg.rest.RestSessionManager.register();
		registerPassiveTicker();
	}

	private void registerApi(){
		UEApiInit.register();
		AlchemyEffects.register();
		AbilityRegistry.register();
		SpellRegistry.init();
		TotalityHarvestHandlers.register();
		DamageTypes.init();
		Conditions.init();
		SpeciesRegistry.init();
		OriginRegistry.init();
		TotalityClasses.register();
		zcylas.totality.api.item.TotalityItemComponents.register();
	}

	private void registerCombatApi(){
		WeaponStaminaHandler.register();
		TwoHandedRestriction.register();
	}
	private void registerRPGHandlers(){
		UnlockMasteryHandler.register();
		ActivateAbilityHandler.register();
		InventoryActionHandler.register();
		SpendAttributePointHandler.register();
		EquipAbilityHandler.register();
		SelectSpellHandler.register();
		FavoriteAbilityHandler.register();
		ToggleFlightHandler.register();
		MovementStaminaHandler.register();
		PowerSprintStateHandler.register();
		SelectAncestryHandler.register();
		ToggleAbilityHandler.register();
		SelectClassHandler.register();
		zcylas.totality.networking.classes.AddClassLevelHandler.register();
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
				zcylas.totality.networking.item.AttunementPayload.TYPE,
				zcylas.totality.networking.item.AttunementHandler::handle);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
				zcylas.totality.networking.item.UnAttunePayload.TYPE,
				zcylas.totality.networking.item.UnAttuneHandler::handle);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
				zcylas.totality.networking.item.CastFocusPayload.TYPE,
				(payload, ctx) -> ctx.server().execute(() ->
						zcylas.totality.networking.item.CastFocusHandler.handle(ctx.player(), payload)));
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
				zcylas.totality.networking.item.PhoneSetupPayload.TYPE,
				zcylas.totality.networking.item.PhoneSetupHandler::handle);
		zcylas.totality.networking.menu.ContainerSortHandler.register();
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
				zcylas.totality.networking.equipment.OpenAccessoryInventoryPayload.TYPE,
				(payload, ctx) -> ctx.server().execute(() -> {
					net.minecraft.server.level.ServerPlayer player = ctx.player();
					// Drop any item on cursor before switching menus
					net.minecraft.world.item.ItemStack carried = player.containerMenu.getCarried();
					if (!carried.isEmpty()) {
						if (!player.getInventory().add(carried)) player.drop(carried, false);
						player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
					}
					player.openMenu(new net.minecraft.world.MenuProvider() {
						@Override
						public net.minecraft.network.chat.Component getDisplayName() {
							return net.minecraft.network.chat.Component.empty();
						}
						@Override
						public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
								int syncId,
								net.minecraft.world.entity.player.Inventory inv,
								net.minecraft.world.entity.player.Player p) {
							return new zcylas.totality.menu.equipment.AccessoryInventoryMenu(syncId, inv);
						}
					});
				}));
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
				zcylas.totality.networking.equipment.OpenInventoryPayload.TYPE,
				(payload, ctx) -> ctx.server().execute(() -> ctx.player().doCloseContainer()));
	}

	private void registerPassiveTicker() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
				zcylas.totality.api.ability.AbilityComponent comp =
						zcylas.totality.api.ability.AbilityComponents.ABILITIES.get(
								(zcylas.totality.api.core.component.ComponentProvider) player);
				for (net.minecraft.resources.Identifier id : comp.getUnlocked()) {
					zcylas.totality.api.ability.Ability ability = AbilityRegistry.get(id);
					if (ability != null && ability.getType() == zcylas.totality.api.ability.Ability.Type.PASSIVE) {
						ability.onPassiveTick(player);
					}
				}
			}
		});
	}

	private void registerAbilityClasses(){
		VeinminerKeyHandler.register();
	}
	private void registerSkillHandlers(){
		OneHandedSkillHandler.register();
	}

	private void registerItemHandlers(){
		UmbraVisorItem.registerDamageHandler();
	}

	private void registerAttributes() {
		FabricDefaultAttributeRegistry.register(ModEntities.SUMMON_SKELETON, Skeleton.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.TOTALITY_NPC, TotalityNpcEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.BANKER, BankerNpcEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.PROVISIONER,
				zcylas.totality.entity.npc.ProvisionerNpcEntity.createAttributes());
	}
	private void registerSkillEvents(){
		MiningSkillEvents.register();
	}

}