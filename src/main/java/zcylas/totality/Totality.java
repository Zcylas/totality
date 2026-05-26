package zcylas.totality;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.ability.AbilityServerTick;
import zcylas.totality.api.ritual.RitualRecipeRegistry;
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
import zcylas.totality.networking.ability.FavoriteAbilityHandler;
import zcylas.totality.networking.ability.veinminer.VeinminerKeyHandler;
import zcylas.totality.networking.ancestry.SelectAncestryHandler;
import zcylas.totality.networking.ancestry.SelectAncestryPayload;
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
		ModBlockEntities.register();
		ModEntities.register();
		MagicRunes.register();
		ModEffects.register();
		ModSounds.register();
		ModLootTables.register();
		TotalityCommands.register();
		RitualRecipeRegistry.register();
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
	}

	private void registerServerTickEvents(){
		ManaServerTick.register();
		StaminaServerTick.register();
		AbilityServerTick.register();
		registerPassiveTicker();
	}

	private void registerApi(){
		UEApiInit.register();
		AlchemyEffects.register();
		AbilityRegistry.register();
		TotalityHarvestHandlers.register();
		TotalityMovementHandler.register();
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
		FavoriteAbilityHandler.register();
		ToggleFlightHandler.register();
		MovementStaminaHandler.register();
		PowerSprintStateHandler.register();
		SelectAncestryHandler.register();
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
	}
	private void registerSkillEvents(){
		MiningSkillEvents.register();
	}

}