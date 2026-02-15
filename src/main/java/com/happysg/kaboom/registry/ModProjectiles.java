package com.happysg.kaboom.registry;

import com.happysg.kaboom.CreateKaboom;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombProjectile;
import com.happysg.kaboom.block.aerialBombs.cluster.ClusterBombletProjectile;
import com.happysg.kaboom.block.aerialBombs.baseTypes.FallingAerialBombRenderer;
import com.happysg.kaboom.block.aerialBombs.cluster.ClusterRenderer;
import com.happysg.kaboom.block.missiles.AbstractMissileEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.tterrag.registrate.util.entry.EntityEntry;
import com.tterrag.registrate.util.nullness.NonNullConsumer;
import net.minecraft.world.entity.MobCategory;
import rbasamoyai.createbigcannons.multiloader.EntityTypeConfigurator;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.ritchiesprojectilelib.RPLTags;

import java.util.function.Consumer;

public class ModProjectiles {

    public static final EntityEntry<AerialBombProjectile> AERIAL_BOMB_PROJECTILE = CreateKaboom.REGISTRATE
            .entity("aerial_bomb_projectile",AerialBombProjectile::new, MobCategory.MISC)
            .properties(bombProperties())
            .tag(RPLTags.PRECISE_MOTION)
            .renderer(() -> FallingAerialBombRenderer::new)
            .register();
    public static final EntityEntry<ClusterBombletProjectile> CLUSTER_BOMBLET = CreateKaboom.REGISTRATE
            .entity("cluster_bomblet",ClusterBombletProjectile::new, MobCategory.MISC)
            .properties(bombProperties())
            .tag(RPLTags.PRECISE_MOTION)
            .renderer(() -> ClusterRenderer::new)
            .register();

    private static <T> NonNullConsumer<T> configure(Consumer<EntityTypeConfigurator> cons) {
        return (b) -> {
            cons.accept(EntityTypeConfigurator.of(b));
        };
    }

    private static <T> NonNullConsumer<T> bombProperties() {
        return configure((c) -> {
            c.size(1F, 1F).fireImmune().updateInterval(1).updateVelocity(false).trackingRange(16);
        });
    }

    public static void register() {
        CreateKaboom.getLogger().info("Registering projectiles!");
    }
}
