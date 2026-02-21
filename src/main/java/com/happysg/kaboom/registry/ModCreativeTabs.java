package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.simibubi.create.AllCreativeModeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

import static com.happysg.kaboom.CreateKaboom.REGISTRATE;

public class ModCreativeTabs {
    public static DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateKaboom.MODID);

    public static final RegistryObject<CreativeModeTab> KABOOM_CREATIVE_TAB = addTab("kaboom", "Create: Kaboom",
            ModBlocks.HEAVY_AERIAL_BOMB::asStack);


    public static RegistryObject<CreativeModeTab> addTab(String id, String name, Supplier<ItemStack> icon) {
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
        pOutput.accept(ModBlocks.FLUID_AERIAL_BOMB);
        pOutput.accept(ModBlocks.TINY_AERIAL_BOMB);


        pOutput.accept(ModBlocks.MISSILE_THRUSTER);
        pOutput.accept(ModBlocks.MISSILE_FUEL);
        pOutput.accept(ModBlocks.MISSILE_GUIDANCE);
        pOutput.accept(ModBlocks.GPS_GUIDANCE_LARGE);
        pOutput.accept(ModBlocks.GPS_GUIDANCE_SMALL);
        pOutput.accept(ModBlocks.MISSILE_THRUSTER_SMALL);
        pOutput.accept(ModBlocks.HEATSEEKER_SMALL);
        pOutput.accept(ModBlocks.MISSILE_FUEL_SMALL);



        pOutput.accept(ModItems.ALTITUDE_FUZE);
    }


    public static void register(IEventBus eventBus) {
        CreateKaboom.getLogger().info("Registering CreativeTabs!");
        CREATIVE_TABS.register(eventBus);
    }
}
