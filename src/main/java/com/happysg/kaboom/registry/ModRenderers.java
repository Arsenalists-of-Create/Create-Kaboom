package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombFuzeRenderer;
import com.happysg.kaboom.block.missiles.MissileRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(modid = CreateKaboom.MODID, value = Dist.CLIENT)
public class ModRenderers {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MISSILE.get(), MissileRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.AERIAL_BOMB.get(), AerialBombFuzeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.FLUID_AERIAL_BOMB_BE.get(), AerialBombFuzeRenderer::new);
    }
}
