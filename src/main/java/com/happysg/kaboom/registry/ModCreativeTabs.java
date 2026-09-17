package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.rocketpod.RocketPod;
import com.happysg.kaboom.compat.Mods;
import com.happysg.kaboom.items.rocket.RocketGuidanceType;
import com.happysg.kaboom.items.rocket.RocketPayload;
import com.simibubi.create.AllCreativeModeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import static com.happysg.kaboom.CreateKaboom.REGISTRATE;

public class ModCreativeTabs {
    public static DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateKaboom.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> KABOOM_CREATIVE_TAB = addTab("kaboom", "Create: Kaboom",
            ModBlocks.HEAVY_AERIAL_BOMB::asStack);

    public static DeferredHolder<CreativeModeTab, CreativeModeTab> addTab(String id, String name, Supplier<ItemStack> icon) {
        String itemGroupId = "itemGroup." + CreateKaboom.MODID + "." + id;
        REGISTRATE.addRawLang(itemGroupId, name);
        CreativeModeTab.Builder tabBuilder = CreativeModeTab.builder()
                .icon(icon)
                .displayItems(ModCreativeTabs::displayItems)
                .title(Component.translatable(itemGroupId))
                .withTabsBefore(AllCreativeModeTabs.PALETTES_CREATIVE_TAB.getKey());
        return CREATIVE_TABS.register(id, tabBuilder::build);
    }

    private static void displayItems(CreativeModeTab.ItemDisplayParameters pParameters, CreativeModeTab.Output pOutput) {
        pOutput.accept(ModBlocks.HEAVY_AERIAL_BOMB.asStack());
        pOutput.accept(ModBlocks.AP_HEAVY_AERIAL_BOMB.asStack());
        pOutput.accept(ModBlocks.FRAG_HEAVY_AERIAL_BOMB.asStack());
        pOutput.accept(ModBlocks.CLUSTER_HEAVY_AERIAL_BOMB);
        pOutput.accept(ModBlocks.FLUID_AERIAL_BOMB.asStack());

        pOutput.accept(ModBlocks.SMALL_AERIAL_BOMB);
        pOutput.accept(ModBlocks.AP_AERIAL_BOMB);
        pOutput.accept(ModBlocks.FRAG_AERIAL_BOMB);
        pOutput.accept(ModBlocks.SMALL_FLUID_AERIAL_BOMB);
        pOutput.accept(ModBlocks.TINY_AERIAL_BOMB);

        pOutput.accept(ModBlocks.MISSILE_THRUSTER_SMALL);
        pOutput.accept(ModBlocks.MISSILE_FUEL_SMALL);
        pOutput.accept(ModBlocks.MISSILE_THRUSTER);
        pOutput.accept(ModBlocks.MISSILE_FUEL);
//        pOutput.accept(ModBlocks.MISSILE_THRUSTER_HUGE);
//        pOutput.accept(ModBlocks.MISSILE_FUEL_HUGE);

        pOutput.accept(ModBlocks.GPS_GUIDANCE_LARGE);
        pOutput.accept(ModBlocks.GPS_GUIDANCE_SMALL);
        pOutput.accept(ModBlocks.GPS_GUIDANCE_HUGE);


        pOutput.accept(ModBlocks.LARGE_CLUSTER_WARHEAD);
        pOutput.accept(ModBlocks.LARGE_HIGH_EXPLOSIVE_WARHEAD);
        pOutput.accept(ModBlocks.LARGE_ARMOR_PIERCING_WARHEAD);
        pOutput.accept(ModBlocks.LARGE_FRAGMENTATION_WARHEAD);
        pOutput.accept(ModBlocks.LARGE_FLUID_WARHEAD);

//        pOutput.accept(ModBlocks.HUGE_CLUSTER_WARHEAD);
//        pOutput.accept(ModBlocks.HUGE_HIGH_EXPLOSIVE_WARHEAD);
//        pOutput.accept(ModBlocks.HUGE_ARMOR_PIERCING_WARHEAD);
//        pOutput.accept(ModBlocks.HUGE_FRAGMENTATION_WARHEAD);

        //pOutput.accept(ModBlocks.HUGE_FLUID_WARHEAD);
        pOutput.accept(ModBlocks.RADAR_GUIDANCE_SMALL);
        pOutput.accept(ModBlocks.RADAR_GUIDANCE_LARGE);



        if (Mods.CREATE_RADAR.isLoaded()) {
            pOutput.accept(ModBlocks.ARAD_GUIDANCE_SMALL);
            pOutput.accept(ModBlocks.ARAD_GUIDANCE_LARGE);
            pOutput.accept(ModBlocks.COMMAND_GUIDANCE_SMALL);
            pOutput.accept(ModBlocks.COMMAND_GUIDANCE_LARGE);
//            pOutput.accept(ModBlocks.COMMAND_GUIDANCE_HUGE);
            //pOutput.accept(ModBlocks.TARGET_COORDINATOR);
        }

        pOutput.accept(ModBlocks.ROCKET_POD_CENTER);
        pOutput.accept(ModBlocks.ROCKET_POD_REAR);
        pOutput.accept(ModItems.ROCKET.get().createPayloadPreset(RocketPayload.HE));
        pOutput.accept(ModItems.ROCKET.get().createPayloadPreset(RocketPayload.AP));
        pOutput.accept(ModItems.ROCKET.get().createPayloadPreset(RocketPayload.SHRAPNEL));
        pOutput.accept(ModItems.ROCKET.get().createPayloadPreset(RocketPayload.SMOKE));
        pOutput.accept(ModItems.ROCKET.get().createPayloadPreset(RocketPayload.FLUID));
        pOutput.accept(ModItems.GUIDED_ROCKET.get().createPreset(RocketPayload.HE, RocketGuidanceType.COMMAND));
        pOutput.accept(ModItems.GUIDED_ROCKET.get().createPreset(RocketPayload.HE, RocketGuidanceType.RADAR));


        pOutput.accept(ModItems.BLUE_TRACER_TIP);
        pOutput.accept(ModItems.RED_TRACER_TIP);
        pOutput.accept(ModItems.GREEN_TRACER_TIP);
        pOutput.accept(ModItems.PINK_TRACER_TIP);
        pOutput.accept(ModItems.WHITE_TRACER_TIP);
        pOutput.accept(ModItems.ORANGE_TRACER_TIP);

        pOutput.accept(ModItems.ALTITUDE_FUZE);


//        solid rocket small
//        solid rocket large


    }

    public static void register(IEventBus eventBus) {
        CreateKaboom.getLogger().info("Registering CreativeTabs!");
        CREATIVE_TABS.register(eventBus);
    }
}
