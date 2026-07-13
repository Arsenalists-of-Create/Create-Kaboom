package com.happysg.kaboom.client;


import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlockEntity;
import com.happysg.kaboom.block.aerialBombs.heavy.ApHeavyAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.heavy.ClusterHeavyAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.heavy.FragHeavyAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.heavy.HeavyAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.small.SmallAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.small.FluidSmallAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.tiny.TinyAerialBombBlock;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.Instancer;

import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;

import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.OrientedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import rbasamoyai.createbigcannons.index.CBCBlockPartials;



public class KaboomFuzedBlockVisual
        extends AbstractBlockEntityVisual<AerialBombBlockEntity>
        implements SimpleDynamicVisual {



    private final OrientedInstance[] fuzes;

    public KaboomFuzedBlockVisual(
            VisualizationContext ctx,
            AerialBombBlockEntity blockEntity,
            float partialTick
    ) {
        super(ctx, blockEntity, partialTick);

        BlockState state = this.blockState;
        Direction direction = state.getValue(AerialBombBlock.FACING);

        int maxFuzes = 1;
        if (state.getBlock() instanceof TinyAerialBombBlock) {
            maxFuzes = 9;
        } else if (state.getBlock() instanceof SmallAerialBombBlock || state.getBlock() instanceof FluidSmallAerialBombBlock) {
            maxFuzes = 4;
        }

        this.fuzes = new OrientedInstance[maxFuzes];
        for (int i = 0; i < maxFuzes; i++) {
            this.fuzes[i] = fuzeProvider(direction).createInstance();
        }

        Vec3 pos = Vec3.atLowerCornerOf(this.blockEntity.getBlockPos());

        if (state.getBlock() instanceof HeavyAerialBombBlock || state.getBlock() instanceof ApHeavyAerialBombBlock || state.getBlock() instanceof ClusterHeavyAerialBombBlock || state.getBlock() instanceof FragHeavyAerialBombBlock) {
            for (OrientedInstance fuze : this.fuzes) {
                fuze.setVisible(false);
            }
            return;
        }

        int count = state.hasProperty(AerialBombBlock.COUNT) ? state.getValue(AerialBombBlock.COUNT) : 1;
        boolean isTiny = state.getBlock() instanceof TinyAerialBombBlock;
        boolean isSmall = state.getBlock() instanceof SmallAerialBombBlock || state.getBlock() instanceof FluidSmallAerialBombBlock;

        double forward = 0;
        if (isSmall) {
            forward = -1 / 16f;
        } else if (isTiny) {
            forward = -0.5 / 16f;
        }

        for (int i = 0; i < maxFuzes; i++) {
            if (i < count) {
                double cx = 8.0;
                double cy = 8.0;
                if (isTiny || isSmall) {
                    double[][] activeCenters = AerialBombBlock.getActiveCenters(isTiny, count);
                    cx = activeCenters[i][0];
                    cy = activeCenters[i][1];
                }

                double hOffset = (cx - 8.0) / 16.0;
                double vOffset = (cy - 8.0) / 16.0;

                Vec3 right = new Vec3(direction.getClockWise().step());
                Vec3 offset = new Vec3(direction.step())
                        .scale(forward)
                        .add(right.scale(hOffset))
                        .add(0, vOffset, 0);

                this.fuzes[i].position(pos.add(offset));

                int globalIndex = AerialBombBlock.getGlobalIndex(isTiny, count, i);
                boolean visible = !this.blockEntity.getFuze(globalIndex).isEmpty();
                this.fuzes[i].setVisible(visible);
            } else {
                this.fuzes[i].setVisible(false);
            }
        }
    }

    private Instancer<OrientedInstance> fuzeProvider(
            Direction direction
    ) {
        return instancerProvider()
                .instancer(
                        InstanceTypes.ORIENTED,
                        Models.partial(
                                CBCBlockPartials.FUZE,
                                direction
                        )
                );
    }

    @Override
    public void beginFrame(
            SimpleDynamicVisual.Context ctx
    ) {
        BlockState state = this.blockState;
        int count = state.hasProperty(AerialBombBlock.COUNT) ? state.getValue(AerialBombBlock.COUNT) : 1;
        boolean isTiny = state.getBlock() instanceof TinyAerialBombBlock;
        boolean isSmall = state.getBlock() instanceof SmallAerialBombBlock || state.getBlock() instanceof FluidSmallAerialBombBlock;

        for (int i = 0; i < fuzes.length; i++) {
            if (i < count) {
                int globalIndex = AerialBombBlock.getGlobalIndex(isTiny, count, i);
                boolean visible = !this.blockEntity.getFuze(globalIndex).isEmpty();
                fuzes[i].setVisible(visible);
            } else {
                fuzes[i].setVisible(false);
            }
        }
    }

    @Override
    public void updateLight(
            float partialTick
    ) {
        for (OrientedInstance fuze : fuzes) {
            relight(fuze);
        }
    }

    @Override
    protected void _delete() {
        for (OrientedInstance fuze : fuzes) {
            fuze.delete();
        }
    }

    @Override
    public void collectCrumblingInstances(
            java.util.function.Consumer<Instance> consumer
    ) {
        for (OrientedInstance fuze : fuzes) {
            consumer.accept(fuze);
        }
    }
}