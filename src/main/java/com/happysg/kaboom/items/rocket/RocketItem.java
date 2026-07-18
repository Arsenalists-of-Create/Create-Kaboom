package com.happysg.kaboom.items.rocket;

import com.happysg.kaboom.registry.ModProjectiles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;


public class RocketItem extends Item {
    public RocketItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public AbstractCannonProjectile createProjectile(ServerLevel level, ItemStack stack, Vec3 launchDirection) {
        UnguidedRocketProjectile projectile = ModProjectiles.UNGUIDED_ROCKET.create(level);
        if (projectile != null) {
            projectile.initialize(stack, launchDirection);
        }
        return projectile;
    }
}
