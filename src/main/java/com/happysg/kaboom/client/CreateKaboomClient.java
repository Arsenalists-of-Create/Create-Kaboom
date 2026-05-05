package com.happysg.kaboom.client;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.block.missiles.MissileRenderer;
import com.happysg.kaboom.block.missiles.chaining.client.ChainRenderer;
import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.ponder.KaboomPonderPlugin;
import com.happysg.kaboom.registry.ModEntities;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

public final class CreateKaboomClient {
    private CreateKaboomClient() {
    }

    public static void register(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(CreateKaboomClient::clientInit);
        modEventBus.addListener(CreateKaboomClient::registerAdditionalModels);
        container.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory) KaboomConfig::createConfigScreen);
    }

    private static void clientInit(final FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new KaboomPonderPlugin());
        NeoForge.EVENT_BUS.register(new ChainRenderer());

        event.enqueueWork(() -> EntityRenderers.register(ModEntities.MISSILE.get(), MissileRenderer::new));
        VisualizerRegistry.setVisualizer(
                ModEntities.MISSILE.get(),
                new SimpleEntityVisualizer<MissileEntity>(ContraptionVisual::new, entity -> false)
        );
    }

    private static void registerAdditionalModels(final ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "block/chain_anchor")));
    }
}
