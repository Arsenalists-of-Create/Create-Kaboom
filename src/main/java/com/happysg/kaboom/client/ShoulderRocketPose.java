package com.happysg.kaboom.client;

import com.happysg.kaboom.items.rocket.UnguidedRocketItem;
import com.happysg.kaboom.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class ShoulderRocketPose {
    private static final float TRANSITION_TICKS = 8.0F;
    // First-person position relative to the normal held-item pose.
    private static final float FIRST_PERSON_SIDE_OFFSET = -0.08F;
    private static final float FIRST_PERSON_HEIGHT_OFFSET = 0.16F;
    private static final float FIRST_PERSON_DEPTH_OFFSET = 0.18F;

    // Rocket orientation tweaks, in degrees, shared by first- and third-person poses.
    private static final float ROCKET_PITCH_TWEAK_DEGREES = 0.0F;
    private static final float ROCKET_YAW_TWEAK_DEGREES = 0.0F;
    private static final float ROCKET_ROLL_TWEAK_DEGREES = 5.0F;

    private static final float HOLDING_ARM_PITCH = (float) Math.toRadians(-90.0 + ROCKET_PITCH_TWEAK_DEGREES);
    private static final float SUPPORT_ARM_PITCH = (float) Math.toRadians(-90.0);

    public static final EnumProxy<HumanoidModel.ArmPose> ARM_POSE = new EnumProxy<>(
            HumanoidModel.ArmPose.class,
            true,
            (IArmPoseTransformer) ShoulderRocketPose::applyThirdPersonPose);

    private ShoulderRocketPose() {
    }

    public static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand,
                                                    ItemStack itemStack) {
                return isActivelyUsingRocket(entity, hand) ? ARM_POSE.getValue() : null;
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
                                                   ItemStack itemInHand, float partialTick,
                                                   float equipProcess, float swingProcess) {
                InteractionHand usedHand = player.getUsedItemHand();
                HumanoidArm usedArm = usedHand == InteractionHand.MAIN_HAND
                        ? player.getMainArm()
                        : player.getMainArm().getOpposite();
                if (!isActivelyUsingRocket(player, usedHand) || arm != usedArm) {
                    return false;
                }

                float progress = transitionProgress(player.getTicksUsingItem() + partialTick);
                int side = arm == HumanoidArm.RIGHT ? 1 : -1;
                poseStack.translate(side * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);
                poseStack.translate(
                        side * FIRST_PERSON_SIDE_OFFSET * progress,
                        FIRST_PERSON_HEIGHT_OFFSET * progress,
                        FIRST_PERSON_DEPTH_OFFSET * progress);
                poseStack.mulPose(Axis.XP.rotationDegrees(ROCKET_PITCH_TWEAK_DEGREES * progress));
                poseStack.mulPose(Axis.YP.rotationDegrees(side * ROCKET_YAW_TWEAK_DEGREES * progress));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * ROCKET_ROLL_TWEAK_DEGREES * progress));
                return true;
            }
        }, ModItems.ROCKET.get());
    }

    private static void applyThirdPersonPose(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        float progress = transitionProgress(entity.getTicksUsingItem());
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        var holdingArm = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        var supportArm = arm == HumanoidArm.RIGHT ? model.leftArm : model.rightArm;

        holdingArm.xRot = Mth.lerp(progress, holdingArm.xRot, HOLDING_ARM_PITCH + model.head.xRot);
        holdingArm.yRot = Mth.lerp(progress, holdingArm.yRot,
                model.head.yRot - side * 0.1F + side * ROCKET_YAW_TWEAK_DEGREES * Mth.DEG_TO_RAD);
        holdingArm.zRot = Mth.lerp(progress, holdingArm.zRot,
                side * ROCKET_ROLL_TWEAK_DEGREES * Mth.DEG_TO_RAD);
        supportArm.xRot = Mth.lerp(progress, supportArm.xRot, SUPPORT_ARM_PITCH + model.head.xRot);
        supportArm.yRot = Mth.lerp(progress, supportArm.yRot, model.head.yRot + side * 0.5F);
        supportArm.zRot = Mth.lerp(progress, supportArm.zRot, 0.0F);
    }

    private static boolean isActivelyUsingRocket(LivingEntity entity, InteractionHand hand) {
        return entity.isUsingItem()
                && entity.getUsedItemHand() == hand
                && entity.getUseItem().getItem() instanceof UnguidedRocketItem;
    }

    private static float transitionProgress(float useTicks) {
        float progress = Mth.clamp(useTicks / TRANSITION_TICKS, 0.0F, 1.0F);
        return progress * progress * (3.0F - 2.0F * progress);
    }
}
