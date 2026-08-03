package com.happysg.kaboom;

import com.happysg.kaboom.block.aerialBombs.baseTypes.FluidAerialBombBlockEntity;
import com.happysg.kaboom.block.missiles.parts.fuel.MissileFuelTankBlockEntity;
import com.happysg.kaboom.block.missiles.parts.warhead.MissileWarheadBlockEntity;
import com.happysg.kaboom.client.CreateKaboomClient;
import com.happysg.kaboom.commands.SelfChainCommand;
import com.happysg.kaboom.compat.radars.RadarCompatRegistry;
import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.events.ChainInteractionHandler;
import com.happysg.kaboom.events.ChainTickHandler;
import com.happysg.kaboom.events.ShoulderRocketInteractionHandler;
import com.happysg.kaboom.items.rocket.RocketFluidHandler;
import com.happysg.kaboom.items.rocket.RocketItem;
import com.happysg.kaboom.items.rocket.RocketPayload;
import com.happysg.kaboom.networking.NetworkHandler;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.happysg.kaboom.registry.ModBlocks;
import com.happysg.kaboom.registry.ModCreativeTabs;
import com.happysg.kaboom.registry.ModContraptionTypes;
import com.happysg.kaboom.registry.ModDataComponents;
import com.happysg.kaboom.registry.ModEntities;
import com.happysg.kaboom.registry.ModItems;
import com.happysg.kaboom.registry.ModLang;
import com.happysg.kaboom.registry.ModParticles;
import com.happysg.kaboom.registry.ModProjectiles;
import com.happysg.kaboom.registry.ModRecipeSerializers;
import com.happysg.kaboom.registry.ModSounds;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

@Mod("create_kaboom")
public class CreateKaboom {
   public static final String MODID = "create_kaboom";
   public static final CreateRegistrate REGISTRATE = CreateRegistrate.create("create_kaboom")
      .setTooltipModifierFactory(item ->
         new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE));
   private static final Logger LOGGER = LogUtils.getLogger();

   public CreateKaboom(IEventBus modEventBus, ModContainer container) {
      LOGGER.info("Initializing Create Kaboom");
      NeoForge.EVENT_BUS.register(new ChainInteractionHandler());
      NeoForge.EVENT_BUS.register(new ChainTickHandler());
      NeoForge.EVENT_BUS.register(new ShoulderRocketInteractionHandler());
      RadarCompatRegistry.register(modEventBus);
      NeoForge.EVENT_BUS.addListener(SelfChainCommand::register);
      REGISTRATE.defaultCreativeTab((ResourceKey)null);
      REGISTRATE.registerEventListeners(modEventBus);
      ModDataComponents.register(modEventBus);
      ModRecipeSerializers.register(modEventBus);
      ModItems.register();
      ModBlocks.register();
      ModBlockEntityTypes.register();
      ModProjectiles.register();
      ModParticles.register(modEventBus);
      ModCreativeTabs.register(modEventBus);
      ModLang.register();
      ModEntities.register(modEventBus);
      ModSounds.register(modEventBus);
      KaboomConfig.register(container);
      modEventBus.addListener(ModContraptionTypes::register);
      modEventBus.addListener(CreateKaboom::registerCapabilities);
      modEventBus.addListener(NetworkHandler::register);
      if (FMLEnvironment.dist == Dist.CLIENT) {
         CreateKaboomClient.register(modEventBus, container);
      }
   }

   public static Logger getLogger() {
      return LOGGER;
   }

   public static ResourceLocation asResource(String path) {
      return ResourceLocation.fromNamespaceAndPath("create_kaboom", path);
   }

   public static String toHumanReadable(String key) {
      String value = key.replace("_", " ");
      value = Arrays.stream(StringUtils.splitByCharacterTypeCamelCase(value)).<CharSequence>map(StringUtils::capitalize).collect(Collectors.joining(" "));
      return StringUtils.normalizeSpace(value);
   }

   public static void registerCapabilities(RegisterCapabilitiesEvent event) {
      event.registerBlockEntity(
         FluidHandler.BLOCK, ModBlockEntityTypes.FLUID_AERIAL_BOMB_BE.get(), FluidAerialBombBlockEntity::getFluidHandler
      );
      event.registerBlockEntity(
         FluidHandler.BLOCK, ModBlockEntityTypes.FUEL_TANK_SMALL.get(), MissileFuelTankBlockEntity::getFluidHandler
      );
      event.registerBlockEntity(
         FluidHandler.BLOCK, ModBlockEntityTypes.MISSILE_WARHEAD.get(), MissileWarheadBlockEntity::getFluidHandler
      );
      event.registerItem(
         FluidHandler.ITEM,
         (stack, ignored) -> RocketItem.getPayload(stack) == RocketPayload.FLUID
            ? new RocketFluidHandler(stack)
            : null,
         ModItems.ROCKET.get(),
         ModItems.GUIDED_ROCKET.get()
      );
   }
}
