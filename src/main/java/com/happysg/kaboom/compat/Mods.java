package com.happysg.kaboom.compat;

import net.createmod.catnip.lang.Lang;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;

import java.util.Optional;
import java.util.function.Supplier;

public enum Mods {
    CREATE_MECHAMAYHEM,
    VALKYRIENSKIES,
    VS_CLOCKWORK,
    CREATE_RADAR,
    COMPUTERCRAFT,
    CREATEBIGCANNONS;

    private final String id;

    Mods() {
        id = Lang.asId(name());
    }

    public String id() {
        return id;
    }

    public ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(id, path);
    }

    public Block getBlock(String id) {
        return BuiltInRegistries.BLOCK.get(rl(id));
    }

    public boolean isLoaded() {
        return ModList.get().isLoaded(id);
    }

    public <T> Optional<T> runIfInstalled(Supplier<Supplier<T>> toRun) {
        if (isLoaded())
            return Optional.of(toRun.get().get());
        return Optional.empty();
    }

    public void executeIfInstalled(Supplier<Runnable> toExecute) {
        if (isLoaded()) {
            toExecute.get().run();
        }
    }
}
