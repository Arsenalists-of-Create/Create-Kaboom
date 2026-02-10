package com.happysg.kaboom.ponder;


import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.registry.ModPonderIndex;
import com.happysg.kaboom.registry.ModPonderTags;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;


public class KaboomPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() { return CreateKaboom.MODID; }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ModPonderIndex.register(helper);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        ModPonderTags.register(helper);
    }
}