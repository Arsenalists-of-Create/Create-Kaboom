package com.happysg.kaboom.block.seamine;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SeaMineBlockEntityRenderer implements BlockEntityRenderer<SeaMineBlockEntity> {

    public SeaMineBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SeaMineBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
    }
}
