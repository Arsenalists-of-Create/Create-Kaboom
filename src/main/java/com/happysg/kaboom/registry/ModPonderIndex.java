package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;


public class ModPonderIndex {

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        CreateKaboom.getLogger().info("Registering Ponder!");

    }
}
