package com.happysg.kaboom.client;

import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombBlockEntity;
import com.happysg.kaboom.block.aerialBombs.baseTypes.AerialBombFuzeLayout;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.OrientedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.index.CBCBlockPartials;

import java.util.function.Consumer;

public class KaboomFuzedBlockVisual extends AbstractBlockEntityVisual<AerialBombBlockEntity>
        implements SimpleDynamicVisual {
    private final OrientedInstance[] fuzes;
    private final Direction facing;
    private int positionedCount = -1;

    public KaboomFuzedBlockVisual(VisualizationContext context, AerialBombBlockEntity blockEntity,
                                  float partialTick) {
        super(context, blockEntity, partialTick);
        BlockState state = blockEntity.getBlockState();
        this.facing = state.getValue(AerialBombBlock.FACING);
        this.fuzes = new OrientedInstance[AerialBombFuzeLayout.capacity(state)];
        for (int slot = 0; slot < fuzes.length; slot++) {
            fuzes[slot] = fuzeProvider(facing).createInstance();
        }
        updatePositions(state);
        updateVisibility(state);
    }

    private Instancer<OrientedInstance> fuzeProvider(Direction direction) {
        return instancerProvider().instancer(
                InstanceTypes.ORIENTED,
                Models.partial(CBCBlockPartials.FUZE, direction)
        );
    }

    private void updatePositions(BlockState state) {
        AerialBombFuzeLayout.SlotCenter[] centers = AerialBombFuzeLayout.activeCenters(state);
        double forward = 0;
        if (state.getBlock() instanceof AerialBombBlock bomb) {
            if (bomb.getBombSize() == 2) {
                forward = -1 / 16.0;
            } else if (bomb.getBombSize() >= 4) {
                forward = -0.5 / 16.0;
            }
        }

        Vec3 renderRelativePos = Vec3.atLowerCornerOf(getVisualPosition());
        for (int slot = 0; slot < fuzes.length; slot++) {
            if (slot < centers.length) {
                Vec3 offset = AerialBombFuzeLayout.offsetFromBlockCenter(facing, centers[slot], forward);
                fuzes[slot].position(renderRelativePos.add(offset));
                fuzes[slot].setChanged();
            }
        }
        positionedCount = centers.length;
    }

    private void updateVisibility(BlockState state) {
        int activeSlots = AerialBombFuzeLayout.activeSlotCount(state);
        for (int slot = 0; slot < fuzes.length; slot++) {
            fuzes[slot].setVisible(slot < activeSlots && !blockEntity.getFuze(slot).isEmpty());
        }
    }

    @Override
    public void beginFrame(SimpleDynamicVisual.Context context) {
        BlockState state = blockEntity.getBlockState();
        int count = AerialBombFuzeLayout.activeSlotCount(state);
        if (count != positionedCount) {
            updatePositions(state);
        }
        updateVisibility(state);
    }

    @Override
    public void updateLight(float partialTick) {
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
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        for (OrientedInstance fuze : fuzes) {
            consumer.accept(fuze);
        }
    }
}
