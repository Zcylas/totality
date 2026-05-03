package zcylas.totality;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.biome.v1.BiomeModification;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zcylas.totality.api.alchemy.AlchemyEffects;
import zcylas.totality.api.component.PlayerComponentEvents;
import zcylas.totality.init.*;
import zcylas.totality.init.magic.MagicRunes;
import zcylas.totality.item.energy.UmbraVisorItem;
import zcylas.totality.menu.energy.ElectricFurnaceMenu;
import zcylas.totality.menu.generator.GeneratorMenu;
import zcylas.totality.networking.TotalityPackets;
import zcylas.totality.networking.TotalityServerPacketHandlers;
import zcylas.totality.networking.mana.ManaServerTick;
import zcylas.totality.worldgen.ModPlacedFeatures;

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
		registerEvents();
		registerAttributes();
		registerLookups();
		registerBiomeModifications();
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
	}

	private void registerApi(){
		UEApiInit.register();
		AlchemyEffects.register();
	}

	private void registerItemHandlers(){
		UmbraVisorItem.registerDamageHandler();
	}

	private void registerAttributes() {
		FabricDefaultAttributeRegistry.register(ModEntities.SUMMON_SKELETON, Skeleton.createAttributes());
	}

	private void registerBiomeModifications(){
		BiomeModifications.addFeature(
		BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.UNDERGROUND_ORES,
				ModPlacedFeatures.GRAPHITE_ORE_PLACED_KEY
		);
		BiomeModifications.addFeature(
		BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.UNDERGROUND_ORES,
				ModPlacedFeatures.LEAD_ORE_PLACED_KEY
		);
		BiomeModifications.addFeature(
		BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.UNDERGROUND_ORES,
				ModPlacedFeatures.TIN_ORE_PLACED_KEY
		);
		BiomeModifications.addFeature(
		BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.UNDERGROUND_ORES,
				ModPlacedFeatures.SILVER_ORE_PLACED_KEY
		);
		BiomeModifications.addFeature(
		BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.UNDERGROUND_ORES,
				ModPlacedFeatures.RUBY_ORE_PLACED_KEY
		);
	}
	private void registerEvents(){
		PlayerComponentEvents.init();
	}
}