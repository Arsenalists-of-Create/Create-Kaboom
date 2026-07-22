package com.happysg.kaboom.mixin;

import com.happysg.kaboom.items.tracer.ColoredTracerProjectile;
import com.happysg.kaboom.items.tracer.TracerColor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rbasamoyai.createbigcannons.CreateBigCannons;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.munitions.autocannon.AbstractAutocannonProjectile;
import rbasamoyai.createbigcannons.munitions.autocannon.AutocannonAmmoType;
import rbasamoyai.createbigcannons.munitions.autocannon.AutocannonProjectileRenderer;
import rbasamoyai.createbigcannons.utils.CBCUtils;

@Mixin(AutocannonProjectileRenderer.class)
public abstract class AutocannonProjectileRendererMixin {
    @Unique
    private static final RenderType CREATE_KABOOM_COLOR = RenderType.entityTranslucentCull(
            CreateBigCannons.resource("textures/entity/color.png"));
    @Unique
    private static final RenderType CREATE_KABOOM_COLOR_SPECULAR = RenderType.eyes(
            CreateBigCannons.resource("textures/entity/color_s.png"));

    @Shadow(remap = false)
    private static void renderBox(VertexConsumer builder, Matrix4f pose, Matrix3f normal,
                                  int red, int green, int blue, float length, float width) {
    }

    @Shadow(remap = false)
    private static void renderBoxInverted(VertexConsumer builder, Matrix4f pose, Matrix3f normal,
                                          int red, int green, int blue, float length, float width) {
    }

    @Inject(
            method = "render(Lrbasamoyai/createbigcannons/munitions/autocannon/AbstractAutocannonProjectile;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void createKaboom$renderColoredTracer(AbstractAutocannonProjectile entity,
                                                   float entityYaw,
                                                   float partialTicks,
                                                   PoseStack poseStack,
                                                   MultiBufferSource bufferSource,
                                                   int packedLight,
                                                   CallbackInfo ci) {
        if (!(entity instanceof ColoredTracerProjectile coloredProjectile)) {
            return;
        }
        TracerColor color = coloredProjectile.createKaboom$getTracerColor();
        if (color == null) {
            return;
        }

        Vec3 previousPosition = new Vec3(entity.xOld, entity.yOld, entity.zOld);
        Vec3 frameDisplacement = entity.position().subtract(previousPosition);
        double displacementSqr = frameDisplacement.lengthSqr();
        boolean fastButNotTeleported = 1.0e-4 < displacementSqr
                && displacementSqr < entity.getDeltaMovement().lengthSqr() * 4.0;
        double frameLength = fastButNotTeleported ? frameDisplacement.length() : 0.0;
        double displacement = entity.getTotalDisplacement() - frameLength * (1.0f - partialTicks);
        float trailLength = (float) Math.min(frameLength, displacement);

        Vec3 orientation = entity.getOrientation();
        if (orientation.lengthSqr() < 1.0e-4) {
            orientation = new Vec3(0.0, -1.0, 0.0);
        }

        poseStack.pushPose();
        if (orientation.horizontalDistanceSqr() > 1.0e-4 && Math.abs(orientation.y) > 0.01) {
            Vec3 horizontal = new Vec3(orientation.x, 0.0, orientation.z).normalize();
            poseStack.mulPose(CBCUtils.mat4x4fFacing(orientation.normalize().reverse(), horizontal));
            poseStack.mulPose(CBCUtils.mat4x4fFacing(horizontal, new Vec3(0.0, 0.0, -1.0)));
        } else {
            poseStack.mulPose(CBCUtils.mat4x4fFacing(
                    orientation.normalize(), new Vec3(0.0, 0.0, -1.0)));
        }

        PoseStack.Pose pose = poseStack.last();
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        float width = entity.getAutocannonRoundType() == AutocannonAmmoType.MACHINE_GUN
                ? 0.03125f
                : 0.0625f;

        renderColorLayers(bufferSource.getBuffer(CREATE_KABOOM_COLOR), poseMatrix, normalMatrix,
                color, trailLength, width);
        if (CBCConfigs.client().enableEmissiveTracers.get()) {
            renderColorLayers(bufferSource.getBuffer(CREATE_KABOOM_COLOR_SPECULAR), poseMatrix, normalMatrix,
                    color, trailLength, width);
        }

        poseStack.popPose();
        ci.cancel();
    }

    @Unique
    private static void renderColorLayers(VertexConsumer builder, Matrix4f pose, Matrix3f normal,
                                          TracerColor color, float length, float width) {
        renderBox(builder, pose, normal,
                color.insideRed(), color.insideGreen(), color.insideBlue(), length, width);
        renderBoxInverted(builder, pose, normal,
                color.outsideRed(), color.outsideGreen(), color.outsideBlue(), length, width * 1.5f);
    }

    @Inject(
            method = "shouldRender(Lrbasamoyai/createbigcannons/munitions/autocannon/AbstractAutocannonProjectile;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void createKaboom$alwaysRenderColoredTracer(AbstractAutocannonProjectile entity,
                                                         Frustum frustum,
                                                         double x,
                                                         double y,
                                                         double z,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ColoredTracerProjectile coloredProjectile
                && coloredProjectile.createKaboom$getTracerColor() != null) {
            cir.setReturnValue(true);
        }
    }
}
