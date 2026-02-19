package com.happysg.kaboom.block.missiles;

import com.happysg.kaboom.block.missiles.MissileEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;


public class MissileRenderer extends ContraptionEntityRenderer<MissileEntity> {

    public MissileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(MissileEntity entity,
                       float entityYaw,
                       float partialTicks,
                       PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight) {

        poseStack.pushPose();

        // Use synced delta movement
        Vec3 v = entity.getDeltaMovement();
        if (v.lengthSqr() < 1e-10) {
            v = new Vec3(0, 1, 0);
        }

        // Build quaternion rotating +Y -> velocity direction
        Vector3f fromUp = new Vector3f(1, 0, 0);
        Vector3f toDir = new Vector3f((float) v.x, (float) v.y, (float) v.z);
        if (toDir.lengthSquared() < 1e-12f) {
            toDir.set(0, 1, 0);
        } else {
            toDir.normalize();
        }

        Quaternionf rotation = new Quaternionf().rotationTo(fromUp, toDir);

        // Rotate about contraption center
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(rotation);
        poseStack.translate(-0.5, 0.0, -0.5);

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);

        poseStack.popPose();
    }
}