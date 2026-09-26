package com.happysg.kaboom.block.seamine;

import com.happysg.kaboom.compat.CBCExplosionHelper;
import com.happysg.kaboom.compat.SableCompatibility;
import com.happysg.kaboom.registry.ModBlocks;
import com.happysg.kaboom.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.effects.particles.splashes.ProjectileSplashParticleData;
import rbasamoyai.createbigcannons.effects.particles.splashes.SplashSprayParticleData;
import rbasamoyai.createbigcannons.index.CBCSoundEvents;

import java.util.List;

public class SeaMineEntity extends Entity {

    private static final EntityDataAccessor<BlockPos> ANCHOR_POS =
            SynchedEntityData.defineId(SeaMineEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> TARGET_Y =
            SynchedEntityData.defineId(SeaMineEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SENSITIVITY =
            SynchedEntityData.defineId(SeaMineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ARMED =
            SynchedEntityData.defineId(SeaMineEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ASCENDING =
            SynchedEntityData.defineId(SeaMineEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TRIGGERED =
            SynchedEntityData.defineId(SeaMineEntity.class, EntityDataSerializers.BOOLEAN);

    private ItemStack fuze = ItemStack.EMPTY;
    private ItemStack chainStack = ItemStack.EMPTY;
    private int chainLength = 16;
    private int armingCooldown = 60;
    private int triggerTimer = 20;
    private int clientTriggerTicks = 0;

    public SeaMineEntity(EntityType<? extends SeaMineEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public SeaMineEntity(Level level, double x, double y, double z) {
        this(ModEntities.SEA_MINE.get(), level);
        this.setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ANCHOR_POS, BlockPos.ZERO);
        builder.define(TARGET_Y, 0.0F);
        builder.define(SENSITIVITY, 50);
        builder.define(ARMED, false);
        builder.define(ASCENDING, true);
        builder.define(TRIGGERED, false);
    }

    public void initData(BlockPos anchorPos, float targetY, int sensitivity, int chainLength, ItemStack fuze, ItemStack chainStack) {
        this.entityData.set(ANCHOR_POS, anchorPos);
        this.entityData.set(TARGET_Y, targetY);
        this.entityData.set(SENSITIVITY, sensitivity);
        this.entityData.set(ARMED, false);
        this.entityData.set(ASCENDING, true);
        this.entityData.set(TRIGGERED, false);
        this.chainLength = chainLength;
        this.fuze = fuze.copy();
        this.chainStack = chainStack.copy();
        this.armingCooldown = 60;
        this.triggerTimer = 20;
        this.clientTriggerTicks = 0;
    }

    public BlockPos getAnchorPos() {
        return this.entityData.get(ANCHOR_POS);
    }

    public float getTargetY() {
        return this.entityData.get(TARGET_Y);
    }

    public int getSensitivity() {
        return this.entityData.get(SENSITIVITY);
    }

    public boolean isArmed() {
        return this.entityData.get(ARMED);
    }

    public boolean isAscending() {
        return this.entityData.get(ASCENDING);
    }

    public boolean isTriggered() {
        return this.entityData.get(TRIGGERED);
    }

    public float getTriggerProgress(float partialTick) {
        if (!isTriggered()) return 0.0f;
        return Math.min(1.0f, (this.clientTriggerTicks + partialTick) / 3.0f);
    }

    public void trigger() {
        if (isTriggered() || !isAlive()) return;
        this.entityData.set(TRIGGERED, true);
        this.triggerTimer = 20;
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, 1.5F, 0.9F);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 1.5F, 1.2F);
    }

    public ItemStack getFuze() {
        return this.fuze;
    }

    public ItemStack getChainStack() {
        return this.chainStack;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            if (isTriggered()) {
                this.clientTriggerTicks++;
            }
        }

        float target = getTargetY();
        boolean ascending = isAscending();

        if (ascending) {
            if (this.getY() < target) {
                this.setDeltaMovement(0, 0.12, 0);
                this.move(MoverType.SELF, this.getDeltaMovement());

                if (!this.level().isClientSide) {
                    if (this.tickCount % 5 == 0 && this.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, this.getX(), this.getY(), this.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
                        serverLevel.sendParticles(ParticleTypes.BUBBLE, this.getX(), this.getY(), this.getZ(), 3, 0.2, 0.2, 0.2, 0.05);
                    }
                    if (this.tickCount % 6 == 0) {
                        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.BLOCKS, 0.5F, 1.2F);
                    }
                    if (this.tickCount % 12 == 0) {
                        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.5F, 1.0F);
                    }
                }
            } else {
                this.setPos(this.getX(), target, this.getZ());
                this.setDeltaMovement(Vec3.ZERO);
                this.entityData.set(ASCENDING, false);
                if (!this.level().isClientSide) {
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4F, 1.8F);
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
                }
            }
        } else {
            this.setDeltaMovement(Vec3.ZERO);
            this.setPos(this.getX(), target, this.getZ());

            if (!this.level().isClientSide) {
                if (isTriggered()) {
                    if (--this.triggerTimer <= 0) {
                        explode();
                        return;
                    }
                } else if (!isArmed()) {
                    if (--this.armingCooldown <= 0) {
                        this.entityData.set(ARMED, true);
                        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.6F, 1.5F);
                    }
                } else {
                    checkDetonation();
                }
            }
        }
    }

    private void checkDetonation() {
        AABB hornBox = this.getBoundingBox().inflate(0.35);
        List<Entity> list = this.level().getEntities(this, hornBox);
        for (Entity entity : list) {
            if (entity.isSpectator()) continue;
            String entityName = entity.getType().getDescriptionId().toLowerCase();
            boolean isMassive = entity instanceof Boat
                    || entityName.contains("sable")
                    || entityName.contains("ship")
                    || entityName.contains("contraption")
                    || entity instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity;
            if (isMassive) {
                trigger();
                return;
            }
            if (entity instanceof LivingEntity living) {
                float width = living.getBbWidth();
                float height = living.getBbHeight();
                float weight = width * width * height * 100.0f;
                if (weight >= getSensitivity()) {
                    trigger();
                    return;
                }
            }
        }

        if (ModList.get().isLoaded("sable")) {
            if (SableCompatibility.checkWheelMeshes(this.level(), this.blockPosition())) {
                trigger();
            }
        }
    }

    public void explode() {
        if (this.level().isClientSide || !this.isAlive()) return;
        this.discard();

        if (this.level() instanceof ServerLevel serverLevel) {
            CBCSoundEvents.PROJECTILE_SPLASH.playAt(this.level(), this.getX(), this.getY(), this.getZ(), 4.0f, 0.8f + this.random.nextFloat() * 0.3f, false);

            SplashSprayParticleData spray = new SplashSprayParticleData(0.92f, 0.97f, 1.0f, 2.0f, 0.0f, 45);
            serverLevel.sendParticles(spray, this.getX(), this.getY() + 0.5, this.getZ(), 40, 1.0, 1.0, 1.0, 0.35);

            ProjectileSplashParticleData splash = new ProjectileSplashParticleData(0.92f, 0.97f, 1.0f, 0.0f);
            serverLevel.sendParticles(splash, this.getX(), this.getY() + 0.5, this.getZ(), 8, 0.4, 0.4, 0.4, 0.15);

            serverLevel.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 0.5, this.getZ(), 80, 1.2, 1.2, 1.2, 0.4);
            serverLevel.sendParticles(ParticleTypes.BUBBLE, this.getX(), this.getY() + 0.5, this.getZ(), 60, 1.0, 1.0, 1.0, 0.3);

            int surfaceY = (int) this.getY();
            for (int y = surfaceY; y < this.level().getMaxBuildHeight(); y++) {
                BlockPos checkPos = new BlockPos(this.blockPosition().getX(), y, this.blockPosition().getZ());
                if (this.level().getBlockState(checkPos).getFluidState().is(FluidTags.WATER)) {
                    surfaceY = y;
                } else {
                    break;
                }
            }
            if (surfaceY >= this.getY()) {
                SplashSprayParticleData surfaceSpray = new SplashSprayParticleData(0.92f, 0.97f, 1.0f, 3.0f, 0.0f, 55);
                serverLevel.sendParticles(surfaceSpray, this.getX(), surfaceY + 1.0, this.getZ(), 50, 1.5, 0.3, 1.5, 0.45);
                serverLevel.sendParticles(ParticleTypes.SPLASH, this.getX(), surfaceY + 1.0, this.getZ(), 120, 1.5, 0.3, 1.5, 0.45);
            }
        }

        CBCExplosionHelper.createShellExplosion(
                this.level(),
                null,
                null,
                this.getX(),
                this.getY() + 0.5,
                this.getZ(),
                6.0f,
                false,
                CBCConfigs.server().munitions.damageRestriction.get().explosiveInteraction()
        );
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (!this.level().isClientSide) {
                this.spawnAtLocation(new ItemStack(ModBlocks.SEA_MINE.get()));
                if (!this.fuze.isEmpty()) {
                    this.spawnAtLocation(this.fuze);
                }
                if (!this.chainStack.isEmpty()) {
                    this.spawnAtLocation(this.chainStack);
                }
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
                this.discard();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.interact(player, hand);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) return false;
        if (!this.level().isClientSide && this.isAlive()) {
            if (isArmed()) {
                trigger();
            } else {
                this.spawnAtLocation(new ItemStack(ModBlocks.SEA_MINE.get()));
                if (!this.fuze.isEmpty()) {
                    this.spawnAtLocation(this.fuze);
                }
                if (!this.chainStack.isEmpty()) {
                    this.spawnAtLocation(this.chainStack);
                }
                this.discard();
            }
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("AnchorPos")) {
            this.entityData.set(ANCHOR_POS, BlockPos.of(tag.getLong("AnchorPos")));
        }
        if (tag.contains("TargetY")) {
            this.entityData.set(TARGET_Y, tag.getFloat("TargetY"));
        }
        if (tag.contains("Sensitivity")) {
            this.entityData.set(SENSITIVITY, tag.getInt("Sensitivity"));
        }
        if (tag.contains("Armed")) {
            this.entityData.set(ARMED, tag.getBoolean("Armed"));
        }
        if (tag.contains("Ascending")) {
            this.entityData.set(ASCENDING, tag.getBoolean("Ascending"));
        }
        if (tag.contains("Triggered")) {
            this.entityData.set(TRIGGERED, tag.getBoolean("Triggered"));
        }
        if (tag.contains("ArmingCooldown")) {
            this.armingCooldown = tag.getInt("ArmingCooldown");
        }
        if (tag.contains("TriggerTimer")) {
            this.triggerTimer = tag.getInt("TriggerTimer");
        }
        if (tag.contains("ChainLength")) {
            this.chainLength = tag.getInt("ChainLength");
        }
        if (tag.contains("Fuze")) {
            this.fuze = ItemStack.parseOptional(this.registryAccess(), tag.getCompound("Fuze"));
        }
        if (tag.contains("ChainStack")) {
            this.chainStack = ItemStack.parseOptional(this.registryAccess(), tag.getCompound("ChainStack"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("AnchorPos", getAnchorPos().asLong());
        tag.putFloat("TargetY", getTargetY());
        tag.putInt("Sensitivity", getSensitivity());
        tag.putBoolean("Armed", isArmed());
        tag.putBoolean("Ascending", isAscending());
        tag.putBoolean("Triggered", isTriggered());
        tag.putInt("ArmingCooldown", this.armingCooldown);
        tag.putInt("TriggerTimer", this.triggerTimer);
        tag.putInt("ChainLength", this.chainLength);
        if (!this.fuze.isEmpty()) {
            tag.put("Fuze", this.fuze.saveOptional(this.registryAccess()));
        }
        if (!this.chainStack.isEmpty()) {
            tag.put("ChainStack", this.chainStack.saveOptional(this.registryAccess()));
        }
    }
}
