package com.happysg.kaboom.block.aerialBombs.baseTypes;

import com.happysg.kaboom.block.aerialBombs.heavy.ApHeavyAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.heavy.ClusterHeavyAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.heavy.FragHeavyAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.heavy.HeavyAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.small.FluidSmallAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.small.SmallAerialBombBlock;
import com.happysg.kaboom.block.aerialBombs.tiny.TinyAerialBombBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import rbasamoyai.createbigcannons.index.CBCBlockPartials;

public class AerialBombBlockEntityRenderer<T extends AerialBombBlockEntity> implements BlockEntityRenderer<T> {

    public AerialBombBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(T be, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        if (be.getLevel() != null && VisualizationManager.supportsVisualization(be.getLevel())) {
            return;
        }

        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof AerialBombBlock)) return;

        if (state.getBlock() instanceof HeavyAerialBombBlock || state.getBlock() instanceof ApHeavyAerialBombBlock
                || state.getBlock() instanceof ClusterHeavyAerialBombBlock || state.getBlock() instanceof FragHeavyAerialBombBlock) {
            return;
        }

        Direction direction = state.getValue(AerialBombBlock.FACING);
        int count = state.hasProperty(AerialBombBlock.COUNT) ? state.getValue(AerialBombBlock.COUNT) : 1;
        boolean isTiny = state.getBlock() instanceof TinyAerialBombBlock;
        boolean isSmall = state.getBlock() instanceof SmallAerialBombBlock || state.getBlock() instanceof FluidSmallAerialBombBlock || state.getBlock() instanceof FluidAerialBombBlock;

        int maxFuzes = 1;
        if (isTiny) {
            maxFuzes = 9;
        } else if (isSmall) {
            maxFuzes = 4;
        }

        double forward = 0;
        if (isSmall) {
            forward = -1.0 / 16.0;
        } else if (isTiny) {
            forward = -0.5 / 16.0;
        }

        BakedModel fuzeModel = CBCBlockPartials.FUZE.get();
        if (fuzeModel == null) return;

        for (int i = 0; i < maxFuzes; i++) {
            if (i >= count) break;

            int globalIndex = AerialBombBlock.getGlobalIndex(isTiny, count, i);
            ItemStack fuzeStack = be.getFuze(globalIndex);
            if (fuzeStack.isEmpty()) continue;

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

            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0.5);
            poseStack.translate(offset.x, offset.y, offset.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(direction.toYRot()));
            poseStack.translate(-0.5, -0.5, -0.5);

            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                    poseStack.last(),
                    buffer.getBuffer(RenderType.cutout()),
                    state,
                    fuzeModel,
                    1.0F, 1.0F, 1.0F,
                    combinedLight,
                    combinedOverlay,
                    ModelData.EMPTY,
                    RenderType.cutout()
            );

            poseStack.popPose();
        }
    }
}
