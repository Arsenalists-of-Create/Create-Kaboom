package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombProjectile;
import com.happysg.kaboom.block.aerialBombs.cluster.ClusterBombletProjectile;
import com.happysg.kaboom.block.aerialBombs.baseTypes.FallingAerialBombRenderer;
import com.happysg.kaboom.block.aerialBombs.cluster.ClusterRenderer;
import com.tterrag.registrate.util.entry.EntityEntry;
import com.tterrag.registrate.util.nullness.NonNullConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import rbasamoyai.ritchiesprojectilelib.RPLTags;

public class ModProjectiles {

    public static final EntityEntry<AerialBombProjectile> AERIAL_BOMB_PROJECTILE = CreateKaboom.REGISTRATE
            .entity("aerial_bomb_projectile", AerialBombProjectile::new, MobCategory.MISC)
            .properties(bombProperties())
            .tag(RPLTags.PRECISE_MOTION)
            .renderer(() -> FallingAerialBombRenderer::new)
            .register();
    public static final EntityEntry<ClusterBombletProjectile> CLUSTER_BOMBLET = CreateKaboom.REGISTRATE
            .entity("cluster_bomblet", ClusterBombletProjectile::new, MobCategory.MISC)
            .properties(bombProperties())
            .tag(RPLTags.PRECISE_MOTION)
            .renderer(() -> ClusterRenderer::new)
            .register();

    private static <T extends Entity> NonNullConsumer<EntityType.Builder<T>> bombProperties() {
        return builder -> builder
                .sized(1.0F, 1.0F)
                .fireImmune()
                .updateInterval(1)
                .setShouldReceiveVelocityUpdates(false)
                .clientTrackingRange(16);
    }

    public static void register() {
        CreateKaboom.getLogger().info("Registering projectiles!");
    }
}
