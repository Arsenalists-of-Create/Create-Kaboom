package com.happysg.kaboom.client;


import com.happysg.kaboom.CreateKaboom;

import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.block.missiles.MissileRenderer;
import com.happysg.kaboom.block.missiles.chaining.client.ChainRenderer;

import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.ponder.KaboomPonderPlugin;

import com.happysg.kaboom.registry.ModEntities;
import com.happysg.kaboom.registry.ModBlockEntityTypes;


import com.simibubi.create.content.contraptions.render.ContraptionVisual;


import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;


import net.createmod.ponder.foundation.PonderIndex;


import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;


import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;


import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import net.neoforged.neoforge.common.NeoForge;



@EventBusSubscriber(
        value = net.neoforged.api.distmarker.Dist.CLIENT
)
public final class CreateKaboomClient {


    private CreateKaboomClient() {
    }



    private static final FuzeSelectionHandler FUZE_GUIDE_HANDLER =
            new FuzeSelectionHandler();





    public static void register(
            IEventBus modEventBus,
            ModContainer container
    ) {



        modEventBus.addListener(
                CreateKaboomClient::clientInit
        );



        modEventBus.addListener(
                CreateKaboomClient::registerAdditionalModels
        );



        NeoForge.EVENT_BUS.register(
                CreateKaboomClient.class
        );



        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                KaboomConfig::createConfigScreen
        );

    }







    private static void clientInit(
            final FMLClientSetupEvent event
    ) {



        PonderIndex.addPlugin(
                new KaboomPonderPlugin()
        );



        NeoForge.EVENT_BUS.register(
                new ChainRenderer()
        );





        event.enqueueWork(() -> {



            EntityRenderers.register(
                    ModEntities.MISSILE.get(),
                    MissileRenderer::new
            );




            /*
             * Fuze render для AerialBombBlockEntity
             *
             * Працює:
             * Heavy
             * Small
             * Tiny
             */

            VisualizerRegistry.setVisualizer(
                    ModBlockEntityTypes.AERIAL_BOMB.get(),
                    new SimpleBlockEntityVisualizer<>(
                            KaboomFuzedBlockVisual::new,
                            be -> true
                    )
            );

            VisualizerRegistry.setVisualizer(
                    ModBlockEntityTypes.FLUID_AERIAL_BOMB_BE.get(),
                    new SimpleBlockEntityVisualizer<>(
                            KaboomFuzedBlockVisual::new,
                            be -> true
                    )
            );


        });






        VisualizerRegistry.setVisualizer(

                ModEntities.MISSILE.get(),

                new SimpleEntityVisualizer<MissileEntity>(
                        ContraptionVisual::new,
                        entity -> false
                )

        );

    }







    private static void registerAdditionalModels(
            final ModelEvent.RegisterAdditional event
    ) {



        event.register(

                ModelResourceLocation.standalone(

                        ResourceLocation.fromNamespaceAndPath(

                                CreateKaboom.MODID,

                                "block/chain_anchor"

                        )

                )

        );

    }









    @SubscribeEvent
    public static void clientTick(
            ClientTickEvent.Post event
    ) {



        Minecraft mc =
                Minecraft.getInstance();



        if (mc.player == null)
            return;



        if (mc.level == null)
            return;



        FUZE_GUIDE_HANDLER.tick();


    }

}