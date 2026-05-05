package com.happysg.kaboom;

import com.happysg.kaboom.client.CreateKaboomClient;
import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.events.ChainInteractionHandler;
import com.happysg.kaboom.events.ChainTickHandler;
import com.happysg.kaboom.networking.NetworkHandler;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.happysg.kaboom.registry.ModBlocks;
import com.happysg.kaboom.registry.ModCreativeTabs;
import com.happysg.kaboom.registry.ModEntities;
import com.happysg.kaboom.registry.ModItems;
import com.happysg.kaboom.registry.ModLang;
import com.happysg.kaboom.registry.ModParticles;
import com.happysg.kaboom.registry.ModProjectiles;
import com.happysg.kaboom.registry.ModSounds;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.stream.Collectors;

@Mod(CreateKaboom.MODID)
public class CreateKaboom {
    public static final String MODID = "create_kaboom";
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID);

    private static final Logger LOGGER = LogUtils.getLogger();

    public CreateKaboom(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("Initializing Create Kaboom");
        NeoForge.EVENT_BUS.register(new ChainInteractionHandler());
        NeoForge.EVENT_BUS.register(new ChainTickHandler());
        REGISTRATE.defaultCreativeTab((ResourceKey<CreativeModeTab>) null);
        REGISTRATE.registerEventListeners(modEventBus);

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
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public static String toHumanReadable(String key) {
        String value = key.replace("_", " ");
        value = Arrays.stream(StringUtils.splitByCharacterTypeCamelCase(value))
                .map(StringUtils::capitalize)
                .collect(Collectors.joining(" "));
        return StringUtils.normalizeSpace(value);
    }

    public static void registerCapabilities(final RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntityTypes.FLUID_AERIAL_BOMB_BE.get(),
                (blockEntity, side) -> blockEntity.getFluidHandler(side)
        );
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntityTypes.FUEL_TANK_SMALL.get(),
                (blockEntity, side) -> blockEntity.getFluidHandler(side)
        );
    }
}
