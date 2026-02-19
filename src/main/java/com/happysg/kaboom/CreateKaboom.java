package com.happysg.kaboom;

import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.block.missiles.MissileRenderer;
import com.happysg.kaboom.networking.ModMessages;
import com.happysg.kaboom.networking.NetworkHandler;
import com.happysg.kaboom.particles.EngineGlowParticle;
import com.happysg.kaboom.particles.MissileSmokeParticle;
import com.happysg.kaboom.ponder.KaboomPonderPlugin;
import com.happysg.kaboom.registry.*;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import com.simibubi.create.foundation.data.CreateRegistrate;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.stream.Collectors;

@Mod(CreateKaboom.MODID)
public class CreateKaboom {
    public static final String MODID = "create_kaboom";
    private static final Logger LOGGER = LogUtils.getLogger();

     public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID);

    public CreateKaboom() {
        getLogger().info("Initializing Create Kaboom!");
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.register(this);
        REGISTRATE.registerEventListeners(modEventBus);
        ModItems.register();
        ModBlocks.register();
        NetworkHandler.register();
        ModBlockEntityTypes.register();
        ModProjectiles.register();
        ModParticles.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModLang.register();
        ModEntities.register(modEventBus);
        ModSounds.register(modEventBus);
        modEventBus.addListener(CreateKaboom::init);
        modEventBus.addListener(CreateKaboom::clientInit);

    }

    public static Logger getLogger() {
        return LOGGER;
    }

    public static ResourceLocation asResource(String path) {
        return new ResourceLocation(MODID, path);
    }

    public static String toHumanReadable(String key) {
        String s = key.replace("_", " ");
        s = Arrays.stream(StringUtils.splitByCharacterTypeCamelCase(s))
                .map(StringUtils::capitalize)
                .collect(Collectors.joining(" "));
        return StringUtils.normalizeSpace(s);
    }


    public static void clientInit(final FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new KaboomPonderPlugin());

        event.enqueueWork(() -> {
            EntityRenderers.register(ModEntities.MISSILE.get(), MissileRenderer::new);
        });
        VisualizerRegistry.setVisualizer(
                ModEntities.MISSILE.get(),
                new SimpleEntityVisualizer<MissileEntity>(ContraptionVisual::new, entity -> false)
        );


    }


    public static void init(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModMessages::register);
    }
}
