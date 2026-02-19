package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.MissileEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CreateKaboom.MODID);


    public static final RegistryObject<EntityType<MissileEntity>> MISSILE =
            ENTITIES.register("missile", () ->
                    EntityType.Builder.<MissileEntity>of(MissileEntity::new, MobCategory.MISC)
                            .sized(1, 1)           // width, height — adjust to your missile
                            .clientTrackingRange(1024)
                            .updateInterval(1)
                            .setShouldReceiveVelocityUpdates(true)
                            .build(new ResourceLocation(CreateKaboom.MODID, "missile").toString())
            );

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}