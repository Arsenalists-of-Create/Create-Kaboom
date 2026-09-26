package com.happysg.kaboom.items.rocket;

import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;

import java.util.List;

public class UnguidedRocketItem extends RocketItem {
    public static final int SHOULDER_USE_TICKS = 3 * 20;
    private static final int ELYTRA_SHOULDER_USE_TICKS = 6 * 20;
    private static final int SHOULDER_COOLDOWN_TICKS = 5 * 20;
    private static final float BOW_BASE_INACCURACY = 1.0F;
    private static final float AIRBORNE_INACCURACY_MULTIPLIER = 3.0F;
    private static final int ELYTRA_MISHAP_DAMAGE = 40;
    private static final int ELYTRA_FIRE_TICK_DAMAGE = 10;
    private static final float SELF_DAMAGE_CHANCE = 0.4F;
    private static final float BACKBLAST_DAMAGE = 8.0F;
    private static final int BACKBLAST_FIRE_SECONDS = 8;
    private static final String MISHAP_FIRE_UNTIL_TAG = "create_kaboom:ShoulderRocketMishapFireUntil";
    private static final double BACKBLAST_LENGTH = 3.0;
    private static final double BACKBLAST_TANGENT = Math.tan(Math.toRadians(30.0));
    private static final int[] IGNITION_SOUND_TICKS = {10, 30, 50};
    private static final int[] ELYTRA_IGNITION_SOUND_TICKS = {20, 60, 100};

    public UnguidedRocketItem(Properties properties) {
        super(properties);
    }

    public ItemStack createPayloadPreset(RocketPayload payload) {
        return createConfiguredPreset(payload, null);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canStartShoulderUse(player, hand)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    public boolean canStartShoulderUse(Player player, InteractionHand rocketHand) {
        ItemStack rocket = player.getItemInHand(rocketHand);
        return shoulderFiredRocketsEnabled()
                && !player.isUsingItem()
                && !player.getCooldowns().isOnCooldown(this)
                && rocket.getItem() == this
                && isLaunchable(rocket)
                && player.getItemInHand(otherHand(rocketHand)).is(Items.FLINT_AND_STEEL);
    }

    public static boolean hasShoulderPair(Player player, InteractionHand rocketHand) {
        ItemStack rocket = player.getItemInHand(rocketHand);
        return shoulderFiredRocketsEnabled()
                && rocket.getItem() instanceof UnguidedRocketItem rocketItem
                && rocketItem.isLaunchable(rocket)
                && player.getItemInHand(otherHand(rocketHand)).is(Items.FLINT_AND_STEEL);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return shoulderUseTicks(entity);
    }

    public static int shoulderUseTicks(LivingEntity entity) {
        return shouldApplyElytraPenalties(entity) ? ELYTRA_SHOULDER_USE_TICKS : SHOULDER_USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (!(entity instanceof Player player)) {
            entity.stopUsingItem();
            return;
        }
        InteractionHand rocketHand = player.getUsedItemHand();
        if (!hasShoulderPair(player, rocketHand)) {
            player.stopUsingItem();
            return;
        }
        if (!level.isClientSide) {
            int elapsedTicks = shoulderUseTicks(player) - remainingUseTicks;
            int[] ignitionSoundTicks = shouldApplyElytraPenalties(player)
                    ? ELYTRA_IGNITION_SOUND_TICKS
                    : IGNITION_SOUND_TICKS;
            for (int soundTick : ignitionSoundTicks) {
                if (elapsedTicks == soundTick) {
                    level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                            SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 0.75F,
                            0.8F + level.random.nextFloat() * 0.4F);
                    break;
                }
            }
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level instanceof ServerLevel serverLevel && entity instanceof Player player) {
            InteractionHand rocketHand = player.getUsedItemHand();
            if (hasShoulderPair(player, rocketHand)) {
                launchShoulderRocket(serverLevel, player, rocketHand, stack);
            }
        }
        return stack;
    }

    private void launchShoulderRocket(ServerLevel level, Player player, InteractionHand rocketHand,
                                      ItemStack rocketStack) {
        Vec3 launchDirection = player.getLookAngle().normalize();
        float inaccuracy = BOW_BASE_INACCURACY * configuredShoulderInaccuracy();
        if (hasAirborneInaccuracyPenalty(player)) {
            inaccuracy *= AIRBORNE_INACCURACY_MULTIPLIER;
        }
        AbstractCannonProjectile projectile = createProjectile(
                level, rocketStack, launchDirection, inaccuracy);
        if (!(projectile instanceof UnguidedRocketProjectile rocket)) {
            return;
        }

        Vec3 launchPosition = getShoulderPosition(player, rocketHand, launchDirection);
        rocket.setPos(launchPosition);
        rocket.setOwner(player);
        rocket.addUntouchableEntity(player, 1);
        if (!level.addFreshEntity(rocket)) {
            rocket.discard();
            return;
        }

        consumeLaunchItems(player, rocketHand, rocketStack);
        player.getCooldowns().addCooldown(this, SHOULDER_COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        applyBackblast(level, player, rocket, launchPosition, launchDirection);
    }

    private static Vec3 getShoulderPosition(Player player, InteractionHand rocketHand, Vec3 launchDirection) {
        float yawRadians = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yawRadians), 0.0, -Mth.sin(yawRadians));
        HumanoidArm rocketArm = rocketHand == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
        double side = rocketArm == HumanoidArm.RIGHT ? 1.0 : -1.0;
        return player.getEyePosition()
                .add(launchDirection.scale(0.35))
                .add(right.scale(0.25 * side))
                .add(0.0, -0.25, 0.0);
    }

    private static void applyBackblast(ServerLevel level, Player player, UnguidedRocketProjectile rocket,
                                       Vec3 origin, Vec3 launchDirection) {
        Vec3 backward = launchDirection.scale(-1.0).normalize();
        spawnBackblastParticles(level, origin, backward);
        DamageSource source = level.damageSources().explosion(rocket, player);

        if (level.random.nextFloat() < SELF_DAMAGE_CHANCE) {
            player.hurt(source, BACKBLAST_DAMAGE);
            if (elytraPenaltiesEnabled()) {
                player.getPersistentData().putLong(MISHAP_FIRE_UNTIL_TAG,
                        level.getGameTime() + BACKBLAST_FIRE_SECONDS * 20L);
                damageEquippedElytra(player, ELYTRA_MISHAP_DAMAGE);
            }
            player.igniteForSeconds(BACKBLAST_FIRE_SECONDS);
        }

        Vec3 coneEnd = origin.add(backward.scale(BACKBLAST_LENGTH));
        double maximumRadius = BACKBLAST_LENGTH * BACKBLAST_TANGENT;
        AABB searchBounds = new AABB(origin, coneEnd).inflate(maximumRadius);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class, searchBounds,
                target -> target != player && target.isAlive() && !target.isSpectator());
        for (LivingEntity target : targets) {
            Vec3 targetCenter = target.getBoundingBox().getCenter();
            Vec3 offset = targetCenter.subtract(origin);
            double axialDistance = offset.dot(backward);
            if (axialDistance <= 0.0 || axialDistance > BACKBLAST_LENGTH) {
                continue;
            }
            double radialDistanceSquared = Math.max(0.0,
                    offset.lengthSqr() - axialDistance * axialDistance);
            double allowedRadius = axialDistance * BACKBLAST_TANGENT;
            if (radialDistanceSquared > allowedRadius * allowedRadius
                    || isBackblastOccluded(level, player, origin, targetCenter)) {
                continue;
            }
            target.hurt(source, BACKBLAST_DAMAGE);
            target.igniteForSeconds(BACKBLAST_FIRE_SECONDS);
        }
    }

    public static void applyMishapFireTickDamage(Player player) {
        if (!elytraPenaltiesEnabled()) {
            player.getPersistentData().remove(MISHAP_FIRE_UNTIL_TAG);
            return;
        }
        if (!player.getPersistentData().contains(MISHAP_FIRE_UNTIL_TAG)) {
            return;
        }
        long mishapFireUntil = player.getPersistentData().getLong(MISHAP_FIRE_UNTIL_TAG);
        if (player.level().getGameTime() > mishapFireUntil) {
            player.getPersistentData().remove(MISHAP_FIRE_UNTIL_TAG);
            return;
        }
        damageEquippedElytra(player, ELYTRA_FIRE_TICK_DAMAGE);
    }

    private static void damageEquippedElytra(Player player, int amount) {
        ItemStack chestItem = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chestItem.is(Items.ELYTRA)) {
            chestItem.hurtAndBreak(amount, player, EquipmentSlot.CHEST);
        }
    }

    private static boolean hasEquippedElytra(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
    }

    private static boolean shouldApplyElytraPenalties(LivingEntity entity) {
        return elytraPenaltiesEnabled() && hasEquippedElytra(entity);
    }

    private static boolean elytraPenaltiesEnabled() {
        return KaboomConfig.server().shoulderRocketElytraPenalties.get();
    }

    private static boolean shoulderFiredRocketsEnabled() {
        return KaboomConfig.server().shoulderFiredRocketsEnabled.get();
    }

    private static boolean isBackblastOccluded(ServerLevel level, Player player, Vec3 origin, Vec3 target) {
        return level.clip(new ClipContext(origin, target, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS;
    }

    private static void spawnBackblastParticles(ServerLevel level, Vec3 origin, Vec3 backward) {
        for (int i = 0; i < 12; ++i) {
            double speed = 0.35 + level.random.nextDouble() * 0.2;
            Vec3 spread = new Vec3(
                    level.random.nextGaussian() * 0.08,
                    level.random.nextGaussian() * 0.08,
                    level.random.nextGaussian() * 0.08);
            Vec3 motion = backward.scale(speed).add(spread);
            level.sendParticles(ModParticles.ROCKET_LAUNCH_SMOKE.get(),
                    origin.x, origin.y, origin.z, 0,
                    motion.x, motion.y, motion.z, 1.0);
        }
    }

    private static void consumeLaunchItems(Player player, InteractionHand rocketHand, ItemStack rocketStack) {
        if (!player.hasInfiniteMaterials()) {
            rocketStack.shrink(1);
        }
        InteractionHand flintHand = otherHand(rocketHand);
        player.getItemInHand(flintHand).hurtAndBreak(
                1, player, LivingEntity.getSlotForHand(flintHand));
    }

    private static float configuredShoulderInaccuracy() {
        float configured = KaboomConfig.server().shoulderRocketInaccuracyMultiplier.getF();
        return Float.isFinite(configured) ? Math.max(0.0F, configured) : 1.5F;
    }

    private static boolean hasAirborneInaccuracyPenalty(Player player) {
        if (player.isFallFlying()) {
            return elytraPenaltiesEnabled();
        }
        return !player.onGround() && player.getDeltaMovement().y < 0.0;
    }

    private static InteractionHand otherHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }
}
