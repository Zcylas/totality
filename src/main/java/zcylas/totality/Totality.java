package zcylas.totality;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zcylas.totality.init.*;
import zcylas.totality.init.magic.MagicRunes;
import zcylas.totality.item.energy.UmbraVisorItem;
import zcylas.totality.menu.generator.GeneratorMenu;
import zcylas.totality.networking.TotalityPackets;
import zcylas.totality.networking.TotalityServerPacketHandlers;
import zcylas.totality.networking.mana.ManaServerTick;

public class Totality implements ModInitializer {
	public static final String MOD_ID = "totality";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		TotalityPackets.register();
		TotalityServerPacketHandlers.register();
		registerEntities();
		registerMenus();
		registerServerTickEvents();
		registerApi();
		registerItemHandlers();
		registerInits();
		registerLookups();
	}

	private void registerInits(){
		ModComponents.register();
		ModItems.register();
		ModBlocks.register();
		ModBlockEntities.register();
		ModEntities.register();
		MagicRunes.register();
		ModEffects.register();
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
	}

	private void registerServerTickEvents(){
		ManaServerTick.register();
	}

	private void registerApi(){
		UEApiInit.register();
	}

	private void registerItemHandlers(){
		UmbraVisorItem.registerDamageHandler();
	}

}