package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.MissileEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CreateKaboom.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> MISSILE =
            ENTITIES.register("missile", () ->
                    EntityType.Builder.<MissileEntity>of(MissileEntity::new, MobCategory.MISC)
                            .sized(1, 1)
                            .clientTrackingRange(1024)
                            .updateInterval(1)
                            .setShouldReceiveVelocityUpdates(true)
                            .build(ResourceLocation.fromNamespaceAndPath(CreateKaboom.MODID, "missile").toString())
            );

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
