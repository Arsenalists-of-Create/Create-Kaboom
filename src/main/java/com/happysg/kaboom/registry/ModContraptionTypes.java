package com.happysg.kaboom.registry;

import static com.simibubi.create.AllContraptionTypes.BY_LEGACY_NAME;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.missiles.MissileContraption;
import com.happysg.kaboom.block.rocketpod.RocketPodContraption;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;
import rbasamoyai.createbigcannons.cannon_control.cannon_types.CannonContraptionTypeRegistry;
import rbasamoyai.createbigcannons.cannon_control.cannon_types.ICannonContraptionType;

public final class ModContraptionTypes {
    private static final ResourceLocation ROCKET_POD_ID = CreateKaboom.asResource("rocket_pod");
    private static final ResourceLocation MISSILE_ID = CreateKaboom.asResource("missile");

    public static final ICannonContraptionType ROCKET_POD_CANNON_TYPE =
            CannonContraptionTypeRegistry.register(ROCKET_POD_ID, () -> ROCKET_POD_ID);

    public static final ICannonContraptionType MISSILE_CANNON_TYPE =
            CannonContraptionTypeRegistry.register(MISSILE_ID, () -> MISSILE_ID);

    public static final Holder.Reference<ContraptionType> ROCKET_POD =
            register("rocket_pod", RocketPodContraption::new);

    public static final Holder.Reference<ContraptionType> MISSILE =
            register("missile", MissileContraption::new);

    private ModContraptionTypes() {
    }

    private static Holder.Reference<ContraptionType> register(
            String name, java.util.function.Supplier<? extends com.simibubi.create.content.contraptions.Contraption> factory) {
        ContraptionType type = new ContraptionType(factory);
        BY_LEGACY_NAME.put(name, type);
        return Registry.registerForHolder(
                CreateBuiltInRegistries.CONTRAPTION_TYPE, CreateKaboom.asResource(name), type);
    }

    public static void register(RegisterEvent event) {
        // Loading this class during the registry lifecycle initializes both contraption identities.
    }
}
