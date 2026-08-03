package com.happysg.kaboom.client;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.block.missiles.chaining.client.ChainRenderer;
import com.happysg.kaboom.client.model.RocketModel;
import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.items.rocket.UnguidedRocketItem;
import com.happysg.kaboom.ponder.KaboomPonderPlugin;
import com.happysg.kaboom.registry.ModBlockEntityTypes;
import com.happysg.kaboom.registry.ModBlocks;
import com.happysg.kaboom.registry.ModEntities;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import com.simibubi.create.foundation.item.ItemDescription;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class CreateKaboomClient {
    private static final FuzeSelectionHandler FUZE_GUIDE_HANDLER = new FuzeSelectionHandler();
    private static final double SHOULDER_ROCKET_MOUSE_SENSITIVITY = 0.0D;

    private CreateKaboomClient() {
    }

    public static void register(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(CreateKaboomClient::clientInit);
        modEventBus.addListener(CreateKaboomClient::registerAdditionalModels);
        modEventBus.addListener(CreateKaboomClient::registerGeometryLoaders);
        modEventBus.addListener(ShoulderRocketPose::register);
        NeoForge.EVENT_BUS.register(CreateKaboomClient.class);
        NeoForge.EVENT_BUS.addListener(RocketTooltipHandler::onItemTooltip);
        NeoForge.EVENT_BUS.addListener(CreateKaboomClient::computeFovModifier);
        container.registerExtensionPoint(IConfigScreenFactory.class, KaboomConfig::createConfigScreen);
    }

    private static void clientInit(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new KaboomPonderPlugin());
        NeoForge.EVENT_BUS.register(new ChainRenderer());

        event.enqueueWork(() -> {
            registerGuidanceTooltipKeys();
            VisualizerRegistry.setVisualizer(
                    ModBlockEntityTypes.AERIAL_BOMB.get(),
                    new SimpleBlockEntityVisualizer<>(KaboomFuzedBlockVisual::new, blockEntity -> true)
            );
            VisualizerRegistry.setVisualizer(
                    ModBlockEntityTypes.FLUID_AERIAL_BOMB_BE.get(),
                    new SimpleBlockEntityVisualizer<>(KaboomFuzedBlockVisual::new, blockEntity -> true)
            );
            VisualizerRegistry.setVisualizer(
                    ModEntities.MISSILE.get(),
                    new SimpleEntityVisualizer<MissileEntity>(ContraptionVisual::new, entity -> false)
            );
        });
    }

    private static void registerGuidanceTooltipKeys() {
        ItemDescription.useKey(ModBlocks.GPS_GUIDANCE_SMALL.get(), "block.create_kaboom.guidance.gps");
        ItemDescription.useKey(ModBlocks.GPS_GUIDANCE_LARGE.get(), "block.create_kaboom.guidance.gps");
        ItemDescription.useKey(ModBlocks.GPS_GUIDANCE_HUGE.get(), "block.create_kaboom.guidance.gps");
        ItemDescription.useKey(ModBlocks.COMMAND_GUIDANCE_SMALL.get(), "block.create_kaboom.guidance.command");
        ItemDescription.useKey(ModBlocks.COMMAND_GUIDANCE_LARGE.get(), "block.create_kaboom.guidance.command");
        ItemDescription.useKey(ModBlocks.RADAR_GUIDANCE_SMALL.get(), "block.create_kaboom.guidance.radar");
        ItemDescription.useKey(ModBlocks.RADAR_GUIDANCE_LARGE.get(), "block.create_kaboom.guidance.radar");
        ItemDescription.useKey(ModBlocks.ARAD_GUIDANCE_SMALL.get(), "block.create_kaboom.guidance.arad");
        ItemDescription.useKey(ModBlocks.ARAD_GUIDANCE_LARGE.get(), "block.create_kaboom.guidance.arad");
    }

    private static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "block/chain_anchor")
        ));
    }

    private static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(CreateKaboom.asResource("rocket"), RocketModel.Loader.INSTANCE);
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.level != null) {
            FUZE_GUIDE_HANDLER.tick();
            MissileClientEffects.tickMountedLaunches();
            RocketClientEffects.tickHandoffs();
        }
    }

    public static void computeFovModifier(ComputeFovModifierEvent event) {
        if (!event.getPlayer().isUsingItem()
                || !(event.getPlayer().getUseItem().getItem() instanceof UnguidedRocketItem)
                || !UnguidedRocketItem.hasShoulderPair(event.getPlayer(), event.getPlayer().getUsedItemHand())) {
            return;
        }

        float progress = Math.min(1.0F,
                (float) event.getPlayer().getTicksUsingItem()
                        / (UnguidedRocketItem.shoulderUseTicks(event.getPlayer()) - 1));
        float bowShrink = progress * progress * 0.15F;
        float fovEffectScale = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
        float adjustedFov = event.getNewFovModifier()
                - event.getFovModifier() * bowShrink * fovEffectScale;
        event.setNewFovModifier(Math.max(0.05F, adjustedFov));
    }

    @SubscribeEvent
    public static void reduceShoulderRocketAimSensitivity(CalculatePlayerTurnEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || !minecraft.player.isUsingItem()
                || !(minecraft.player.getUseItem().getItem() instanceof UnguidedRocketItem)
                || !UnguidedRocketItem.hasShoulderPair(
                minecraft.player, minecraft.player.getUsedItemHand())) {
            return;
        }

        event.setMouseSensitivity(SHOULDER_ROCKET_MOUSE_SENSITIVITY);
    }
}
