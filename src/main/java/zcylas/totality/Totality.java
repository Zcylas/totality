package zcylas.totality;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zcylas.totality.api.rpg.skills.core.OneHandedSkillHandler;
import zcylas.totality.api.rpg.stats.StatsServerEvents;
import zcylas.totality.api.rpg.skills.alchemy.AlchemyEffects;
import zcylas.totality.api.rpg.combat.weapon.TwoHandedRestriction;
import zcylas.totality.api.rpg.combat.weapon.WeaponStaminaHandler;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.init.*;
import zcylas.totality.init.magic.MagicRunes;
import zcylas.totality.item.energy.UmbraVisorItem;
import zcylas.totality.menu.energy.ElectricFurnaceMenu;
import zcylas.totality.menu.generator.GeneratorMenu;
import zcylas.totality.networking.TotalityPackets;
import zcylas.totality.networking.TotalityServerPacketHandlers;
import zcylas.totality.networking.inventory.InventoryActionHandler;
import zcylas.totality.networking.mana.ManaServerTick;
import zcylas.totality.networking.skills.UnlockMasteryHandler;
import zcylas.totality.networking.stamina.StaminaServerTick;
import zcylas.totality.networking.stats.SpendAttributePointHandler;
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
		registerCombatApi();
		registerEvents();
		registerRPGHandlers();
		registerSkillHandlers();
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
		ModLootTables.register();
		TotalityCommands.register();
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
	}

	private void registerApi(){
		UEApiInit.register();
		AlchemyEffects.register();
	}

	private void registerCombatApi(){
		WeaponStaminaHandler.register();
		TwoHandedRestriction.register();
	}
	private void registerRPGHandlers(){
		UnlockMasteryHandler.register();
		InventoryActionHandler.register();
		SpendAttributePointHandler.register();
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
		// Blue Mountain Flower — plains, forests, meadows
		BiomeModifications.addFeature(
				BiomeSelectors.tag(BiomeTags.IS_OVERWORLD)
						.and(ctx -> {
							String path = ctx.getBiomeKey().identifier().getPath();

							return path.contains("plains")
									|| path.contains("meadow")
									|| (path.contains("forest")
									&& !path.contains("birch")
									&& !path.contains("dark"));
						}),
				GenerationStep.Decoration.VEGETAL_DECORATION,
				ModPlacedFeatures.BLUE_MOUNTAIN_FLOWER_PLACED_KEY
		);
		// Purple Mountain Flower — taiga, snowy biomes
		BiomeModifications.addFeature(
				BiomeSelectors.tag(BiomeTags.IS_OVERWORLD)
						.and(ctx -> {
							String path = ctx.getBiomeKey().identifier().getPath();

							return path.contains("taiga")
									|| path.contains("snowy")
									|| path.contains("frozen");
						}),
				GenerationStep.Decoration.VEGETAL_DECORATION,
				ModPlacedFeatures.PURPLE_MOUNTAIN_FLOWER_PLACED_KEY
		);

	// Red Mountain Flower — forests, jungles, warm biomes
		BiomeModifications.addFeature(
				BiomeSelectors.tag(BiomeTags.IS_OVERWORLD)
						.and(ctx -> {
							String path = ctx.getBiomeKey().identifier().getPath();

							return path.contains("jungle")
									|| path.contains("birch")
									|| path.contains("dark_forest")
									|| path.contains("savanna");
						}),
				GenerationStep.Decoration.VEGETAL_DECORATION,
				ModPlacedFeatures.RED_MOUNTAIN_FLOWER_PLACED_KEY
		);

	}
	private void registerEvents(){
		PlayerComponentEvents.init();
		StatsServerEvents.register();
	}
}