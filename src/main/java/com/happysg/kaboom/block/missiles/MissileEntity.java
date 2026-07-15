package com.happysg.kaboom.block.missiles;

import com.happysg.kaboom.block.missiles.assembly.MissileSize;
import com.happysg.kaboom.block.missiles.chaining.ChainSystem;
import com.happysg.kaboom.block.missiles.nav.MissileNavigation;
import com.happysg.kaboom.block.missiles.nav.MovingTargetInterceptorNavigation;
import com.happysg.kaboom.block.missiles.parts.warhead.MissileWarheadProjectile;
import com.happysg.kaboom.block.missiles.util.MissileAttachedParticleOptions;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceData;
import com.happysg.kaboom.block.missiles.util.MissileGuidanceType;
import com.happysg.kaboom.block.missiles.util.MissileProjectileContext;
import com.happysg.kaboom.block.missiles.util.PreciseMotionSyncPacket;
import com.happysg.kaboom.compat.sable.SableUtils;
import com.happysg.kaboom.config.KaboomConfig;
import com.happysg.kaboom.mixin.AbstractProjectileAccessor;
import com.happysg.kaboom.mixin.FuzeMixin;
import com.happysg.kaboom.networking.ChainSystemSyncPacket;
import com.happysg.kaboom.registry.ModParticles;
import com.happysg.kaboom.sounds.MissileEngineSound;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.foundation.collision.CollisionList;
import com.simibubi.create.foundation.collision.CollisionList.Populate;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Explosion.BlockInteraction;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import rbasamoyai.createbigcannons.CreateBigCannons;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesHandler;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesProvider;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.config.CBCCfgMunitions.GriefState;
import rbasamoyai.createbigcannons.index.CBCDamageTypes;
import rbasamoyai.createbigcannons.index.CBCEntityTypes;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.CannonDamageSource;
import rbasamoyai.createbigcannons.munitions.ImpactExplosion;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile.ImpactResult;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile.ImpactResult.KinematicOutcome;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.solid_shot.SolidShotProjectile;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionPropertiesHandler;
import rbasamoyai.createbigcannons.munitions.config.FluidDragHandler;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.EntityDamagePropertiesComponent;
import rbasamoyai.createbigcannons.network.CBCNeoForgePacket;
import rbasamoyai.createbigcannons.network.ClientboundPlayBlockHitEffectPacket;
import rbasamoyai.createbigcannons.utils.CBCUtils;

public class MissileEntity extends OrientedContraptionEntity implements MissileNavigation.FlightAccess {
   private static final EntityDataAccessor<Float> HEADING_X = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> HEADING_Y = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> HEADING_Z = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
   public static final EntityDataAccessor<Integer> FUEL_MB = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.INT);
   protected static final EntityDataAccessor<Float> GRAVITY = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Integer> FUEL_CAP_MB = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> NAV_STATE = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.INT);
   private static final double BOUNCE_RESTITUTION = 0.35;
   private static final int SUBSTEPS = 20;
   private boolean latchedInGround = false;
   @Nullable
   private Vec3 resolvedPosThisTick = null;
   private final Set<Long> forcedChunks = new HashSet<>();
   private static final int CHUNK_RADIUS = 1;
   @OnlyIn(Dist.CLIENT)
   private boolean spawnedThrusterParticle = false;
   private int fuelMb;
   private int fuelCapacityMb;
   private MissileSize missileSize = MissileSize.SMALL;
   private int fuelTankCount = 1;
   private Vec3 lastVelForSmoke = Vec3.ZERO;
   @OnlyIn(Dist.CLIENT)
   private MissileEngineSound engineSound;
   @Nullable
   private Vec3 pendingVelocity = null;
   private boolean forceCustomColliders = true;
   private final MissileNavigation navigation = new MissileNavigation();
   private final MovingTargetInterceptorNavigation interceptorNavigation = new MovingTargetInterceptorNavigation();
   @Nullable
   private MissileGuidanceData guidanceData = null;
   @Nullable
   private UUID sourceSubLevelId = null;
   private List<AABB> customColliders = List.of();
   private Direction assemblyDirection = Direction.UP;
   private Axis forwardAxis = Axis.Y;
   private int forwardSign = 1;
   private BlockPos noseLocal = BlockPos.ZERO;
   private static final double NOSE_TIP_AHEAD = 0.55;
   private static final double MIN_MAX_COLLISION_SWEEP = 64.0;
   private static final double POSITION_REMAP_TOLERANCE = 1.0;
   private boolean launched = false;
   private final ChainSystem chainSystem = new ChainSystem();
   public static final BallisticPropertiesComponent BALLISTIC_PROPERTIES = new BallisticPropertiesComponent(-0.08, 0.0, false, 2.0F, 1.0F, 1.0F, 0.7F);
   public static final EntityDamagePropertiesComponent DAMAGE_PROPERTIES = new EntityDamagePropertiesComponent(30.0F, false, true, true, 2.0F);
   protected Map<BlockPos, Float> blockMass = new HashMap<>();
   protected int inFluidTime = 0;
   protected int penetrationTime = 0;
   @Nullable
   protected BlockState lastPenetratedBlock = Blocks.AIR.defaultBlockState();
   protected AbstractBigCannonProjectile warhead = null;
   protected BlockPos warheadpos = null;
   protected BlockPos capPos = null;

   public MissileEntity(EntityType<?> type, Level level) {
      super(type, level);
   }

   public ChainSystem getChainSystem() {
      return this.chainSystem;
   }

   @Override
   public Level guidanceLevel() {
      return this.level();
   }

   @Override
   public int guidanceTickCount() {
      return this.tickCount;
   }

   @Override
   public int guidanceEntityId() {
      return this.getId();
   }

   @Override
   public UUID guidanceUuid() {
      return this.getUUID();
   }

   @Override
   public Vec3 guidanceVelocity() {
      return this.getDeltaMovement();
   }

   @Override
   public int guidanceFuelMb() {
      return this.fuelMb;
   }

   @Override
   public int guidanceFuelCapacityMb() {
      return this.fuelCapacityMb;
   }

   @Override
   public double guidanceAccelerationMultiplier() {
      return this.missileSize.accelerationMultiplier(this.fuelTankCount);
   }

   @Override
   public void guidanceSetFuelMb(int mb) {
      this.fuelMb = Math.max(0, mb);
      this.entityData.set(FUEL_MB, this.fuelMb);
      this.entityData.set(FUEL_CAP_MB, this.fuelCapacityMb);
   }

   @Override
   public void guidanceBurnFuel(int mb) {
      this.burnFuel(mb);
   }

   @Override
   public void guidanceSyncState(int stateOrdinal) {
      this.entityData.set(NAV_STATE, stateOrdinal);
   }

   @Nullable
   public String getRadarChaffTargetId() {
      return this.interceptorNavigation.getRadarChaffTargetId();
   }

   public void applyRadarChaffSuppression(String targetId, long untilTick) {
      this.interceptorNavigation.applyRadarChaffSuppression(targetId, untilTick);
   }

   public void onRadarMissileLaunched() {
      this.interceptorNavigation.onRadarMissileLaunched(this, this.position(), this.getOrientation());
   }

   public void initFromAssembly(Contraption contraption, BlockPos warheadLocalPos, SableUtils.LaunchKinematics launch) {
      Vec3 launchPos = launch.position();
      this.setPos(launchPos.x, launchPos.y, launchPos.z);
      this.setContraption(contraption);
      this.setNoGravity(false);
      this.entityData.set(GRAVITY, -0.08F);
      this.startAtInitialYaw();
      this.warheadpos = warheadLocalPos;
      if (contraption instanceof MissileContraption mc) {
         this.assemblyDirection = mc.assemblyDirection == null ? Direction.UP : mc.assemblyDirection;
         this.fuelMb = mc.fuelAmountMb;
         this.fuelCapacityMb = mc.fuelCapacityMb;
         this.missileSize = mc.missileSize;
         this.fuelTankCount = Math.max(1, mc.fuelTankCount);
      }

      this.entityData.set(FUEL_MB, this.fuelMb);
      this.entityData.set(FUEL_CAP_MB, this.fuelCapacityMb);
      this.recomputeForwardAxisAndNose();
      this.rebuildCustomColliders(0.4);
      this.enforceCustomColliders();
      Vec3 launchDirection = launch.direction();
      this.navigation.initialize(launchDirection, this.position(), this);
      this.interceptorNavigation.initialize(launchDirection, this.position(), this);
      this.syncHeading(launchDirection);
      this.sourceSubLevelId = launch.sourceSubLevelId();
      double ejectionVelocity = Math.max(0.0, (double)KaboomConfig.server().missileEjectionVelocity.getF());
      Vec3 initialVelocity = launch.carrierVelocity().add(launchDirection.scale(ejectionVelocity));
      this.setContraptionMotion(initialVelocity);
      super.setDeltaMovement(initialVelocity);

      if (contraption instanceof MissileContraption mc && mc.guidanceTag != null && !mc.guidanceTag.isEmpty()) {
         this.guidanceData = MissileGuidanceData.fromTag(mc.guidanceTag);
         if (this.guidanceData.guidanceType() == MissileGuidanceType.GPS) {
            this.navigation.configureStationaryTarget(this.guidanceData, this.position());
         } else if (this.guidanceData.guidanceType().isInterceptor()) {
            this.interceptorNavigation.configure(this.guidanceData);
         }

         this.blockMass.clear();
         this.blockMass.put(this.capPos, 10.0F);
         this.restoreWarheadProjectile();
         if (mc.chainSystemTag != null) {
            this.chainSystem.load(mc.chainSystemTag);
            this.chainSystem.setLaunched();
         }

         return;
      }
   }

   private static Vec3 directionVector(Direction direction) {
      return new Vec3((double)direction.getStepX(), (double)direction.getStepY(), (double)direction.getStepZ());
   }

   private BlockPos resolveWarheadLocal(MissileContraption missileContraption) {
      BlockPos stored = missileContraption.warheadLocalPos;
      if (stored != null && missileContraption.getBlocks().containsKey(stored)) {
         return stored;
      } else {
         BlockPos best = null;
         int bestProjection = Integer.MIN_VALUE;

         for (Entry<BlockPos, StructureBlockInfo> entry : missileContraption.getBlocks().entrySet()) {
            if (entry.getValue().state().getBlock() instanceof ProjectileBlock) {
               BlockPos pos = entry.getKey();
               int projection = pos.getX() * this.assemblyDirection.getStepX()
                  + pos.getY() * this.assemblyDirection.getStepY()
                  + pos.getZ() * this.assemblyDirection.getStepZ();
               if (projection > bestProjection) {
                  bestProjection = projection;
                  best = pos;
               }
            }
         }

         return best;
      }
   }

   private void restoreWarheadProjectile() {
      this.warhead = null;
      if (!this.level().isClientSide && this.contraption != null && this.warheadpos != null) {
         StructureBlockInfo info = (StructureBlockInfo)this.contraption.getBlocks().get(this.warheadpos);
         if (info != null && info.state().getBlock() instanceof ProjectileBlock<?> projectileBlock) {
            AbstractCannonProjectile projectile = projectileBlock.getProjectile(this.level(), List.of(info));
            if (projectile instanceof AbstractBigCannonProjectile bigProjectile) {
               this.warhead = bigProjectile;
            }
         }
      }
   }

   private void syncChainSystemToContraption() {
      if (this.contraption instanceof MissileContraption mc) {
         mc.chainSystemTag = this.chainSystem.save();
      }
   }

   public void tick() {
      if (this.contraption == null) {
         this.discard();
      } else {
         this.enforceCustomColliders();
         if (this.level().isClientSide && !this.launched && this.getFuelMbSynced() > 0) {
            this.launched = true;
            this.engineSound = new MissileEngineSound(this);
            Minecraft.getInstance().getSoundManager().play(this.engineSound);
         }

         if (this.level().isClientSide) {
            this.clientTickVisuals();
            if (!this.spawnedThrusterParticle && this.getFuelMbSynced() > 0) {
               this.spawnedThrusterParticle = true;
               float back = 1.2F;
               float up = 0.0F;
               float right = 0.0F;
               Vec3 p = this.position();
               this.level().addParticle(new MissileAttachedParticleOptions(this.getId(), back, up, right), true, p.x, p.y, p.z, 0.0, 0.0, 0.0);
            }

            super.tick();
         } else {
            this.serverTickMovement();
         }
      }
   }

   private void serverTickMovement() {
      this.fallDistance = 0.0F;
      this.hasImpulse = true;
      if (this.level() instanceof ServerLevel sl) {
         this.chainSystem.tickFromEntity(this, sl);
         if (this.tickCount % 20 == 0) {
            this.syncChainSystemToContraption();
            this.chainSystem.populateEntityIds(sl);
            PacketDistributor.sendToPlayersTrackingEntity(this, new ChainSystemSyncPacket(this.getId(), this.chainSystem.save()), new CustomPacketPayload[0]);
         }
      }

      this.tickChunkLoading();
      this.noPhysics = true;
      this.setNoGravity(false);
      if (this.latchedInGround) {
         this.freezeInPlace();
         this.tickWarhead();
         this.sendPreciseMotion(this.position(), Vec3.ZERO);
      } else {
         if (this.pendingVelocity != null) {
            Vec3 pv = clampSpeed(this.pendingVelocity, MissileNavigation.configuredMaxSpeed());
            super.setDeltaMovement(pv);
            this.setContraptionMotion(pv);
            this.pendingVelocity = null;
         }

         Vec3 pos0 = this.position();
         double maxSpeed = MissileNavigation.configuredMaxSpeed();
         Vec3 vel0 = clampSpeed(this.getDeltaMovement(), maxSpeed);
         if (!vel0.equals(this.getDeltaMovement())) {
            super.setDeltaMovement(vel0);
            this.setContraptionMotion(vel0);
         }

         boolean boosting = this.isBoosting();
         MissileNavigation.Command guidance = this.tickGuidance(pos0, vel0);
         Vec3 aCtrl = guidance.appliedDeltaV();
         Vec3 aBase = boosting && aCtrl.lengthSqr() > 1.0E-12 ? Vec3.ZERO : this.getForcesWithParam(vel0);
         Vec3 aTick = aBase.add(aCtrl);
         MissileEntity.PhysicsStep step = this.integrateSubsteps(pos0, vel0, aTick, 20);
         Vec3 posPred = step.pos();
         Vec3 velPred = step.vel();
         this.move(MoverType.SELF, posPred.subtract(pos0));
         Vec3 pos1 = this.position();
         if (!isFinite(pos1) || pos1.distanceToSqr(posPred) > 1.0) {
            this.setPos(posPred.x, posPred.y, posPred.z);
            pos1 = posPred;
         }

         Vec3 velNext = clampSpeed(velPred, maxSpeed);
         this.setContraptionMotion(velNext);
         super.setDeltaMovement(velNext);
         this.resolvedPosThisTick = null;
         this.tickCBCImpacts(pos0, pos1);
         this.tickWarhead();
         if (this.resolvedPosThisTick != null) {
            this.setPos(this.resolvedPosThisTick.x, this.resolvedPosThisTick.y, this.resolvedPosThisTick.z);
         } else {
            Vec3 headingVec = guidance.desiredDir() != null && guidance.desiredDir().lengthSqr() > 1.0E-8 ? guidance.desiredDir() : velNext;
            this.syncHeading(headingVec);
            this.sendPreciseMotion(pos1, velNext);
         }
      }
   }

   protected Vec3 getForcesWithParam(Vec3 velocity) {
      double g = this.isNoGravity()
         ? 0.0
         : (double)((Float)this.entityData.get(GRAVITY)).floatValue() * DimensionMunitionPropertiesHandler.getProperties(this.level()).gravityMultiplier();
      return new Vec3(0.0, g, 0.0);
   }

   private int getFuelMbSynced() {
      return (Integer)this.entityData.get(FUEL_MB);
   }

   private void freezeInPlace() {
      super.setDeltaMovement(Vec3.ZERO);
      this.setContraptionMotion(Vec3.ZERO);
      this.pendingVelocity = null;
      this.noPhysics = true;
   }

   private void latchInGround() {
      this.latchedInGround = true;
      this.drainFuel();
      this.freezeInPlace();
   }

   private void sendPreciseMotion(Vec3 pos, Vec3 v) {
      if (this.level() instanceof ServerLevel) {
         int lerpSteps = 3;
         PacketDistributor.sendToPlayersTrackingEntityAndSelf(
            this,
            new PreciseMotionSyncPacket(this.getId(), pos.x, pos.y, pos.z, v.x, v.y, v.z, this.getYRot(), this.getXRot(), this.onGround(), lerpSteps),
            new CustomPacketPayload[0]
         );
      }
   }

   private void syncHeading(Vec3 v) {
      Vec3 h = v.lengthSqr() > 1.0E-8 ? v.normalize() : new Vec3(0.0, 1.0, 0.0);
      this.entityData.set(HEADING_X, (float)h.x);
      this.entityData.set(HEADING_Y, (float)h.y);
      this.entityData.set(HEADING_Z, (float)h.z);
   }

   private Vec3 tipWorldAtEntityPos(Vec3 entityPos, BlockPos localBlock, Vec3 worldDirUnit, double ahead) {
      Vec3 now = this.toGlobalVector(Vec3.atCenterOf(localBlock), 0.0F);
      Vec3 delta = entityPos.subtract(this.position());
      Vec3 base = now.add(delta);
      return base.add(worldDirUnit.scale(ahead));
   }

   private BlockPos pickLeadingLocal(Vec3 worldDirUnit) {
      if (this.contraption != null && !this.contraption.getBlocks().isEmpty()) {
         double best = -Double.MAX_VALUE;
         BlockPos bestPos = BlockPos.ZERO;

         for (BlockPos lp : this.contraption.getBlocks().keySet()) {
            Vec3 wp = this.toGlobalVector(Vec3.atCenterOf(lp), 0.0F);
            double score = wp.dot(worldDirUnit);
            if (score > best) {
               best = score;
               bestPos = lp;
            }
         }

         return bestPos;
      } else {
         return BlockPos.ZERO;
      }
   }

   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
      builder.define(HEADING_X, 0.0F);
      builder.define(HEADING_Y, 1.0F);
      builder.define(HEADING_Z, 0.0F);
      builder.define(FUEL_MB, 0);
      builder.define(FUEL_CAP_MB, 0);
      builder.define(GRAVITY, -0.08F);
      builder.define(NAV_STATE, MissileNavigation.State.BOOST.ordinal());
   }

   protected void writeAdditional(CompoundTag tag, Provider registries, boolean spawnPacket) {
      super.writeAdditional(tag, registries, spawnPacket);
      tag.putInt("kaboom:FuelMb", this.fuelMb);
      tag.putInt("kaboom:FuelCapacityMb", this.fuelCapacityMb);
      tag.putString("kaboom:MissileSize", this.missileSize.name());
      tag.putInt("kaboom:FuelTankCount", this.fuelTankCount);
      tag.putBoolean("kaboom:LatchedInGround", this.latchedInGround);
      Vec3 heading = this.getOrientation();
      tag.putDouble("kaboom:HeadingX", heading.x);
      tag.putDouble("kaboom:HeadingY", heading.y);
      tag.putDouble("kaboom:HeadingZ", heading.z);
      if (this.guidanceData != null) {
         tag.put("kaboom:MissileGuidanceData", this.guidanceData.toTag());
      }
      if (this.sourceSubLevelId != null) {
         tag.putUUID("kaboom:SourceSubLevel", this.sourceSubLevelId);
      }

      this.navigation.write(tag);
      this.interceptorNavigation.write(tag);
   }

   protected void readAdditional(CompoundTag tag, boolean spawnData) {
      super.readAdditional(tag, spawnData);
      this.fuelMb = tag.contains("kaboom:FuelMb") ? tag.getInt("kaboom:FuelMb") : this.fuelMb;
      this.fuelCapacityMb = tag.contains("kaboom:FuelCapacityMb") ? tag.getInt("kaboom:FuelCapacityMb") : this.fuelCapacityMb;
      this.fuelMb = Math.max(0, this.fuelMb);
      this.fuelCapacityMb = Math.max(0, this.fuelCapacityMb);
      if (this.fuelCapacityMb > 0) {
         this.fuelMb = Math.min(this.fuelMb, this.fuelCapacityMb);
      }

      if (this.contraption instanceof MissileContraption mc) {
         this.missileSize = mc.missileSize;
         this.fuelTankCount = Math.max(1, mc.fuelTankCount);
      }
      if (tag.contains("kaboom:MissileSize")) {
         try {
            this.missileSize = MissileSize.valueOf(tag.getString("kaboom:MissileSize"));
         } catch (IllegalArgumentException ignored) {
         }
      }
      if (tag.contains("kaboom:FuelTankCount")) {
         this.fuelTankCount = Math.max(1, tag.getInt("kaboom:FuelTankCount"));
      }

      this.entityData.set(FUEL_MB, this.fuelMb);
      this.entityData.set(FUEL_CAP_MB, this.fuelCapacityMb);
      this.latchedInGround = tag.getBoolean("kaboom:LatchedInGround");
      if (tag.contains("kaboom:HeadingX")) {
         this.syncHeading(new Vec3(tag.getDouble("kaboom:HeadingX"), tag.getDouble("kaboom:HeadingY"), tag.getDouble("kaboom:HeadingZ")));
      }

      if (tag.contains("kaboom:MissileGuidanceData")) {
         this.guidanceData = MissileGuidanceData.fromTag(tag.getCompound("kaboom:MissileGuidanceData"));
      }
      this.sourceSubLevelId = tag.hasUUID("kaboom:SourceSubLevel") ? tag.getUUID("kaboom:SourceSubLevel") : null;

      if (this.contraption instanceof MissileContraption mc) {
         this.assemblyDirection = mc.assemblyDirection == null ? Direction.UP : mc.assemblyDirection;
         this.warheadpos = this.resolveWarheadLocal(mc);
         this.recomputeForwardAxisAndNose();
         this.rebuildCustomColliders(0.4);
         this.enforceCustomColliders();
         this.blockMass.clear();
         this.blockMass.put(this.capPos, 10.0F);
         this.restoreWarheadProjectile();
         if (mc.chainSystemTag != null && !mc.chainSystemTag.isEmpty()) {
            this.chainSystem.load(mc.chainSystemTag);
            this.chainSystem.setLaunched();
         }
      }

      if (this.guidanceData != null && this.guidanceData.guidanceType().isInterceptor()) {
         this.interceptorNavigation.configure(this.guidanceData);
         this.interceptorNavigation.read(tag, this, this.position());
      } else {
         this.navigation.read(tag, this, this.position());
      }

      if (!tag.contains("kaboom:HeadingX")) {
         Vec3 fallback = this.getDeltaMovement().lengthSqr() > 1.0E-8 ? this.getDeltaMovement() : directionVector(this.assemblyDirection);
         this.syncHeading(fallback);
      }
   }

   private MissileNavigation.Command tickGuidance(Vec3 pos, Vec3 vel) {
      return this.guidanceData != null && this.guidanceData.guidanceType().isInterceptor()
         ? this.interceptorNavigation.tick(this, pos, vel)
         : this.navigation.tick(this, pos, vel);
   }

   private boolean isBoosting() {
      return this.guidanceData != null && this.guidanceData.guidanceType().isInterceptor()
         ? this.interceptorNavigation.isBoosting()
         : this.navigation.isBoosting();
   }

   private void tickChunkLoading() {
      if (this.level() instanceof ServerLevel sl) {
         ChunkPos var9 = new ChunkPos(this.blockPosition());
         HashSet wanted = new HashSet();

         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               ChunkPos p = new ChunkPos(var9.x + dx, var9.z + dz);
               long key = p.toLong();
               wanted.add(key);
               if (!this.forcedChunks.contains(key)) {
                  sl.setChunkForced(p.x, p.z, true);
                  this.forcedChunks.add(key);
               }
            }
         }

         this.forcedChunks.removeIf(keyx -> {
            if (wanted.contains(keyx)) {
               return false;
            } else {
               ChunkPos px = new ChunkPos(keyx);
               sl.setChunkForced(px.x, px.z, false);
               return true;
            }
         });
      }
   }

   public void applyLocalTransforms(PoseStack stack, float partialTicks) {
      stack.translate(-0.5F, 0.0F, -0.5F);
      TransformStack tstack = (TransformStack)((PoseTransformStack)TransformStack.of(stack).nudge(this.getId())).center();
      stack.mulPose(this.getAssemblyToHeadingRotation());
      tstack.uncenter();
   }

   public Vec3 applyRotation(Vec3 localPos, float partialTicks) {
      Vector3f transformed = this.getAssemblyToHeadingRotation().transform(new Vector3f((float)localPos.x, (float)localPos.y, (float)localPos.z));
      return new Vec3((double)transformed.x, (double)transformed.y, (double)transformed.z);
   }

   public Vec3 reverseRotation(Vec3 localPos, float partialTicks) {
      Vector3f transformed = this.getAssemblyToHeadingRotation().invert().transform(new Vector3f((float)localPos.x, (float)localPos.y, (float)localPos.z));
      return new Vec3((double)transformed.x, (double)transformed.y, (double)transformed.z);
   }

   public Quaternionf getAssemblyToHeadingRotation() {
      Vector3f from = new Vector3f((float)this.assemblyDirection.getStepX(), (float)this.assemblyDirection.getStepY(), (float)this.assemblyDirection.getStepZ());
      Vector3f to = new Vector3f((Float)this.entityData.get(HEADING_X), (Float)this.entityData.get(HEADING_Y), (Float)this.entityData.get(HEADING_Z));
      if (to.lengthSquared() < 1.0E-12F) {
         to.set(from);
      } else {
         to.normalize();
      }

      return new Quaternionf().rotationTo(from, to);
   }

   public void lerpMotion(double x, double y, double z) {
      Vec3 v = new Vec3(x, y, z);
      this.setContraptionMotion(v);
      super.setDeltaMovement(v);
   }

   public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
      if (this.tickCount >= 2) {
         super.lerpTo(x, y, z, yRot, xRot, steps);
      }
   }

   private void recomputeForwardAxisAndNose() {
      this.forwardAxis = this.assemblyDirection.getAxis();
      this.forwardSign = this.assemblyDirection.getAxisDirection() == AxisDirection.POSITIVE ? 1 : -1;
      if (this.contraption != null && !this.contraption.getBlocks().isEmpty()) {
         if (this.warheadpos != null && this.contraption.getBlocks().containsKey(this.warheadpos)) {
            this.noseLocal = this.warheadpos;
            this.capPos = this.noseLocal;
         } else {
            BlockPos best = BlockPos.ZERO;
            int bestProj = Integer.MIN_VALUE;

            for (BlockPos p : this.contraption.getBlocks().keySet()) {
               int proj = switch (this.forwardAxis) {
                  case X -> p.getX() * this.forwardSign;
                  case Y -> p.getY() * this.forwardSign;
                  case Z -> p.getZ() * this.forwardSign;
                  default -> throw new MatchException(null, null);
               };
               if (proj > bestProj) {
                  bestProj = proj;
                  best = p;
               }
            }

            this.noseLocal = best;
            this.capPos = this.noseLocal;
         }
      } else {
         this.noseLocal = BlockPos.ZERO;
         this.capPos = this.noseLocal;
      }
   }

   private void enforceCustomColliders() {
      if (this.forceCustomColliders) {
         if (this.contraption != null) {
            CollisionList colliders = this.contraption.simplifiedEntityColliders;
            colliders.size = 0;
            Populate populate = new Populate(colliders);

            for (AABB box : this.customColliders) {
               populate.append(
                  (box.minX + box.maxX) * 0.5,
                  (box.minY + box.maxY) * 0.5,
                  (box.minZ + box.maxZ) * 0.5,
                  (box.maxX - box.minX) * 0.5,
                  (box.maxY - box.minY) * 0.5,
                  (box.maxZ - box.minZ) * 0.5
               );
            }
         }
      }
   }

   private void rebuildCustomColliders(double radius) {
      if (this.contraption != null && !this.contraption.getBlocks().isEmpty()) {
         int minX = Integer.MAX_VALUE;
         int minY = Integer.MAX_VALUE;
         int minZ = Integer.MAX_VALUE;
         int maxX = Integer.MIN_VALUE;
         int maxY = Integer.MIN_VALUE;
         int maxZ = Integer.MIN_VALUE;

         for (BlockPos p : this.contraption.getBlocks().keySet()) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
         }

         double x0 = (double)minX;
         double x1 = (double)maxX + 1.0;
         double y0 = (double)minY;
         double y1 = (double)maxY + 1.0;
         double z0 = (double)minZ;
         double z1 = (double)maxZ + 1.0;
         double cx = (x0 + x1) * 0.5;
         double cy = (y0 + y1) * 0.5;
         double cz = (z0 + z1) * 0.5;
         AABB body;
         AABB capA;
         AABB capB;
         switch (this.forwardAxis) {
            case X: {
               body = new AABB(x0, cy - radius, cz - radius, x1, cy + radius, cz + radius);
               double a = x0 + 0.5;
               double b = x1 - 0.5;
               capA = new AABB(a - radius, cy - radius, cz - radius, a + radius, cy + radius, cz + radius);
               capB = new AABB(b - radius, cy - radius, cz - radius, b + radius, cy + radius, cz + radius);
               break;
            }
            case Y: {
               body = new AABB(cx - radius, y0, cz - radius, cx + radius, y1, cz + radius);
               double a = y0 + 0.5;
               double b = y1 - 0.5;
               capA = new AABB(cx - radius, a - radius, cz - radius, cx + radius, a + radius, cz + radius);
               capB = new AABB(cx - radius, b - radius, cz - radius, cx + radius, b + radius, cz + radius);
               break;
            }
            case Z: {
               body = new AABB(cx - radius, cy - radius, z0, cx + radius, cy + radius, z1);
               double a = z0 + 0.5;
               double b = z1 - 0.5;
               capA = new AABB(cx - radius, cy - radius, a - radius, cx + radius, cy + radius, a + radius);
               capB = new AABB(cx - radius, cy - radius, b - radius, cx + radius, cy + radius, b + radius);
               break;
            }
            default:
               throw new IllegalStateException("Unexpected axis " + this.forwardAxis);
         }

         this.customColliders = List.of(body, capA, capB);
      } else {
         this.customColliders = List.of(new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
      }
   }

   private MissileEntity.PhysicsStep integrateSubsteps(Vec3 pos0, Vec3 vel0, Vec3 aTick, int substeps) {
      if (substeps <= 1) {
         Vec3 vel1 = vel0.add(aTick);
         Vec3 pos1 = pos0.add(vel1);
         return new MissileEntity.PhysicsStep(pos1, vel1);
      } else {
         double dt = 1.0 / (double)substeps;
         Vec3 pos = pos0;
         Vec3 vel = vel0;

         for (int i = 0; i < substeps; i++) {
            vel = vel.add(aTick.scale(dt));
            pos = pos.add(vel.scale(dt));
         }

         return new MissileEntity.PhysicsStep(pos, vel);
      }
   }

   private static Vec3 clampSpeed(Vec3 v, double max) {
      double sp = v.length();
      return sp > max && sp > 1.0E-9 ? v.scale(max / sp) : v;
   }

   private static Vec3 reflectVelocity(Vec3 velocity, Vec3 surfaceNormal, double restitution) {
      if (surfaceNormal.lengthSqr() < 1.0E-12) {
         return velocity;
      } else {
         Vec3 n = surfaceNormal.normalize();
         double vn = velocity.dot(n);
         if (vn >= 0.0) {
            return velocity;
         } else {
            double e = Mth.clamp(restitution, 0.0, 1.0);
            return velocity.subtract(n.scale((1.0 + e) * vn));
         }
      }
   }

   @OnlyIn(Dist.CLIENT)
   private void clientTickVisuals() {
      if (this.getFuelMbSynced() > 0) {
         this.spawnSmokeClient(this.position(), this.getDeltaMovement());
      }
   }

   private static Vec3 limitTurnSafe(Vec3 currentDir, Vec3 desiredDir, double maxTurnDeg) {
      if (!(currentDir.lengthSqr() < 1.0E-8) && !(desiredDir.lengthSqr() < 1.0E-8)) {
         Vec3 a = currentDir.normalize();
         Vec3 b = desiredDir.normalize();
         double dot = Mth.clamp(a.dot(b), -1.0, 1.0);
         if (dot < -0.9995) {
            Vec3 axis = a.cross(new Vec3(0.0, 1.0, 0.0));
            if (axis.lengthSqr() < 1.0E-8) {
               axis = a.cross(new Vec3(1.0, 0.0, 0.0));
            }

            axis = axis.normalize();
            double maxRad = Math.toRadians(maxTurnDeg);
            Vec3 rotated = a.scale(Math.cos(maxRad)).add(axis.cross(a).scale(Math.sin(maxRad))).add(axis.scale(axis.dot(a) * (1.0 - Math.cos(maxRad))));
            return rotated.normalize();
         } else {
            double angle = Math.acos(dot);
            if (angle < 1.0E-6) {
               return b;
            } else {
               double maxRad = Math.toRadians(maxTurnDeg);
               if (angle <= maxRad) {
                  return b;
               } else {
                  double t = maxRad / angle;
                  double sinAngle = Math.sin(angle);
                  double w1 = Math.sin((1.0 - t) * angle) / sinAngle;
                  double w2 = Math.sin(t * angle) / sinAngle;
                  return a.scale(w1).add(b.scale(w2)).normalize();
               }
            }
         }
      } else {
         return desiredDir;
      }
   }

   public void remove(RemovalReason reason) {
      if (!this.level().isClientSide && this.level() instanceof ServerLevel sl) {
         this.interceptorNavigation.clearRadarRwrEmitter(this);

         for (long key : this.forcedChunks) {
            ChunkPos p = new ChunkPos(key);
            sl.setChunkForced(p.x, p.z, false);
         }

         this.forcedChunks.clear();
         this.chainSystem.releaseAll(sl);
      }

      super.remove(reason);
   }

   @OnlyIn(Dist.CLIENT)
   private void spawnSmokeClient(Vec3 pos, Vec3 v) {
      ClientLevel cl = (ClientLevel)this.level();
      double speed = v.length();
      double accel = v.subtract(this.lastVelForSmoke).length();
      this.lastVelForSmoke = v;
      float speedFactor = (float)Mth.clamp((speed - 0.15) / 0.85, 0.0, 1.0);
      float accelFactor = (float)Mth.clamp(accel / 0.12, 0.0, 1.0);
      float intensity = 0.35F + 0.85F * speedFactor + 0.9F * accelFactor;
      Vec3 forward = v.lengthSqr() > 1.0E-6 ? v.normalize() : new Vec3(0.0, 1.0, 0.0);
      Vec3 back = forward.scale(-1.0);
      double behind = 0.65;
      int count = Mth.clamp((int)Math.ceil((double)(2.0F * intensity)), 1, 10);
      double coneRadius = 0.05 + 0.18 * (double)intensity;
      double inherit = 0.2;
      double pushBack = 0.05;

      for (int i = 0; i < count; i++) {
         double ox = (cl.random.nextDouble() - 0.5) * coneRadius;
         double oy = (cl.random.nextDouble() - 0.5) * coneRadius;
         double oz = (cl.random.nextDouble() - 0.5) * coneRadius;
         double x = pos.x + back.x * behind + ox;
         double y = pos.y + 0.15 + back.y * behind + oy;
         double z = pos.z + back.z * behind + oz;
         double tx = (cl.random.nextDouble() - 0.5) * 0.03 * (double)intensity;
         double ty = (cl.random.nextDouble() - 0.5) * 0.02 * (double)intensity;
         double tz = (cl.random.nextDouble() - 0.5) * 0.03 * (double)intensity;
         Vec3 motion = v.scale(inherit).add(back.scale(pushBack)).add(tx, ty, tz);
         cl.addParticle((ParticleOptions)ModParticles.MISSILE_SMOKE.get(), true, x, y, z, motion.x, motion.y, motion.z);
      }
   }

   public boolean shouldRenderAtSqrDistance(double distance) {
      return true;
   }

   private void burnFuel(int mb) {
      if (mb > 0) {
         this.fuelMb = Math.max(0, this.fuelMb - mb);
         this.entityData.set(FUEL_MB, this.fuelMb);
         this.entityData.set(FUEL_CAP_MB, this.fuelCapacityMb);
      }
   }

   private void drainFuel() {
      this.fuelMb = 0;
      this.entityData.set(FUEL_MB, 0);
      this.entityData.set(FUEL_CAP_MB, this.fuelCapacityMb);
   }

   protected void tickCBCImpacts(Vec3 oldPos, Vec3 newPos) {
      this.resolvedPosThisTick = null;
      MissileProjectileContext ctx = new MissileProjectileContext(this, (GriefState)CBCConfigs.server().munitions.damageRestriction.get());
      Vec3 entDisp = newPos.subtract(oldPos);
      if (!(entDisp.lengthSqr() < 1.0E-10)) {
         double maxSweep = Math.max(64.0, MissileNavigation.configuredMaxSpeed() * 4.0);
         if (isFinite(oldPos) && isFinite(newPos) && !(entDisp.lengthSqr() > maxSweep * maxSweep)) {
            Vec3 worldDirUnit = entDisp.normalize();
            BlockPos leadLocal = this.pickLeadingLocal(worldDirUnit);
            Vec3 start = this.tipWorldAtEntityPos(oldPos, leadLocal, worldDirUnit, 0.55);
            Vec3 end = this.tipWorldAtEntityPos(newPos, leadLocal, worldDirUnit, 0.55);
            Vec3 entityToNose0 = start.subtract(oldPos);
            Vec3 disp0 = end.subtract(start);
            if (!(disp0.lengthSqr() < 1.0E-10)) {
               double r = 0.35;
               Vec3 dirUnit = disp0.normalize();
               Vec3 right = dirUnit.cross(new Vec3(0.0, 1.0, 0.0));
               if (right.lengthSqr() < 1.0E-8) {
                  right = new Vec3(1.0, 0.0, 0.0);
               }

               right = right.normalize();
               Vec3 up = right.cross(dirUnit).normalize();
               Vec3[] offsets = new Vec3[]{Vec3.ZERO, right.scale(0.35), right.scale(-0.35), up.scale(0.35), up.scale(-0.35)};
               BiFunction<Vec3, Vec3, BlockHitResult> clipCapsule = (segStart, segEnd) -> {
                  BlockHitResult best = null;
                  double bestDist = Double.POSITIVE_INFINITY;

                  for (Vec3 off : offsets) {
                     BlockHitResult hit = this.level().clip(this.collisionClipContext(
                        segStart.add(off), segEnd.add(off), Block.COLLIDER, Fluid.NONE
                     ));
                     if (hit.getType() != Type.MISS) {
                        double d = segStart.distanceTo(hit.getLocation());
                        if (d < bestDist) {
                           bestDist = d;
                           best = hit;
                        }
                     }
                  }

                  if (best != null) {
                     return best;
                  } else {
                     Direction missDir = Direction.getNearest(dirUnit.x, dirUnit.y, dirUnit.z);
                     return BlockHitResult.miss(segEnd, missDir, BlockPos.containing(segEnd));
                  }
               };
               int maxIter = 20;
               boolean shouldRemove = false;
               boolean stop = false;
               Vec3 vel0 = this.getDeltaMovement();
               Vec3 accel = this.getForces(vel0);
               Vec3 traj = vel0.add(accel);
               double reach = (double)Math.max(this.getBbWidth(), this.getBbHeight()) * 0.5;
               AABB noseBox = new AABB(start, start).inflate(0.25);

               for (int i = 0; i < maxIter; i++) {
                  Vec3 disp = end.subtract(start);
                  if (disp.lengthSqr() < 1.0E-10) {
                     this.lastPenetratedBlock = Blocks.AIR.defaultBlockState();
                     break;
                  }

                  Vec3 segDirUnit = disp.normalize();
                  BlockHitResult blockHit = clipCapsule.apply(start, end);
                  Vec3 hitEnd = end;
                  if (blockHit.getType() != Type.MISS) {
                     hitEnd = blockHit.getLocation();
                  }

                  if (i == 0) {
                     BlockHitResult fluidHit = this.level().clip(this.collisionClipContext(start, hitEnd, Block.OUTLINE, Fluid.ANY));
                     if (fluidHit.getType() != Type.MISS) {
                        BlockPos fp = fluidHit.getBlockPos();
                        BlockState fs = this.level().getBlockState(fp);
                        if (fs.getBlock() instanceof LiquidBlock && this.inFluidTime <= 0) {
                           stop = this.onImpactFluid(ctx, fs, this.level().getFluidState(fp), fluidHit.getLocation(), fluidHit);
                           this.inFluidTime = 2;
                           if (stop) {
                              break;
                           }
                        }
                     }
                  }

                  if (this.onClip(ctx, start, hitEnd)) {
                     shouldRemove = true;
                     break;
                  }

                  AABB movementRegion = noseBox.expandTowards(disp).inflate(1.0);

                  for (Entity target : this.level().getEntities(this, movementRegion)) {
                     if (!ctx.hasHitEntity(target)) {
                        AABB bb = target.getBoundingBox();
                        if (bb.intersects(noseBox) || bb.inflate(reach).clip(start, hitEnd).isPresent()) {
                           ctx.addEntity(target);
                        }
                     }
                  }

                  if (blockHit.getType() == Type.MISS) {
                     break;
                  }

                  BlockPos bp = blockHit.getBlockPos().immutable();
                  BlockState hitState = this.level().getChunkAt(bp).getBlockState(bp);
                  ImpactResult result = this.calculateBlockPenetration(ctx, hitState, blockHit);
                  double totalNose = start.distanceTo(end);
                  double usedNose = start.distanceTo(hitEnd);
                  double usedFrac = totalNose <= 1.0E-9 ? 0.0 : Mth.clamp(usedNose / totalNose, 0.0, 1.0);
                  Vec3 entDir = entDisp.lengthSqr() > 1.0E-10 ? entDisp.normalize() : segDirUnit;
                  Vec3 backOffEnt = entDir.scale(0.05);
                  Vec3 snappedEntityPos = oldPos.add(entDisp.scale(usedFrac)).subtract(backOffEnt);
                  switch (result.kinematics()) {
                     case PENETRATE:
                        this.lastPenetratedBlock = hitState;
                        this.penetrationTime = 2;
                        double used = start.distanceTo(hitEnd);
                        double total = start.distanceTo(end);
                        if (total <= 1.0E-6) {
                           double var10000 = 0.0;
                        } else {
                           Math.max(0.0, (total - used) / total);
                        }

                        start = hitEnd;
                        end = hitEnd.add(segDirUnit.scale(total - used));
                        break;
                     case STOP:
                        this.resolvedPosThisTick = snappedEntityPos;
                        this.latchInGround();
                        this.lastPenetratedBlock = hitState;
                        this.penetrationTime = 2;
                        stop = true;
                        break;
                     case BOUNCE:
                        this.resolvedPosThisTick = snappedEntityPos;
                        Vec3 normal = CBCUtils.getSurfaceNormalVector(this.level(), blockHit);
                        this.pendingVelocity = reflectVelocity(traj, normal, 0.35);
                        stop = true;
                  }

                  shouldRemove |= result.shouldRemove();
                  if (stop || shouldRemove) {
                     break;
                  }

                  noseBox = new AABB(start, start).inflate(0.25);
               }

               for (Entity e : ctx.hitEntities()) {
                  shouldRemove |= this.onHitEntity(e, ctx);
               }

               if (!this.level().isClientSide) {
                  if (ctx.griefState() != GriefState.NO_DAMAGE) {
                     Vec3 oldVel = this.getDeltaMovement();

                     for (Entry<BlockPos, Float> queued : ctx.getQueuedExplosions().entrySet()) {
                        Vec3 impactPos = Vec3.atCenterOf((Vec3i)queued.getKey());
                        ImpactExplosion explosion = new ImpactExplosion(
                           this.level(),
                           this,
                           this.getDamage(),
                           impactPos.x,
                           impactPos.y,
                           impactPos.z,
                           queued.getValue(),
                           queued.getValue(),
                           BlockInteraction.DESTROY
                        );
                        CreateBigCannons.handleCustomExplosion(this.level(), explosion);
                     }

                     this.setContraptionMotion(oldVel);
                  }

                  for (ClientboundPlayBlockHitEffectPacket pkt : ctx.getPlayedEffects()) {
                     PacketDistributor.sendToPlayersTrackingEntity(this, new CBCNeoForgePacket(pkt), new CustomPacketPayload[0]);
                  }
               }

               if (!this.level().isClientSide || !stop) {
                  Vec3 o = this.getOrientation();
                  Vec3 look = o.lengthSqr() < 1.0E-8 ? new Vec3(0.0, -1.0, 0.0) : o.normalize();
                  this.setXRot(pitchFromVector(look));
                  this.setYRot(yawFromVector(look));
               }

               if (shouldRemove) {
                  this.blockMass.remove(this.capPos);
               }
            }
         }
      }
   }

   private ClipContext collisionClipContext(Vec3 start, Vec3 end, Block blockMode, Fluid fluidMode) {
      ClipContext context = new ClipContext(start, end, blockMode, fluidMode, this);
      if (this.isBoosting()) {
         SableUtils.ignoreSubLevel(context, this.sourceSubLevelId);
      }
      return context;
   }

   protected boolean onClip(MissileProjectileContext ctx, Vec3 start, Vec3 end) {
      if (this.warheadpos != null && this.warhead instanceof FuzedBigCannonProjectile fuzed) {
         fuzed.setPos(this.toGlobalVector(Vec3.atCenterOf(this.warheadpos), 0.0F));
         fuzed.setDeltaMovement(this.getDeltaMovement());
         FuzeMixin acc = (FuzeMixin)fuzed;
         ItemStack fuzeStack = acc.getFuze();
         boolean baseFuze = acc.invokeGetFuzeProperties().baseFuze();
         ProjectileContext pctx = new ProjectileContext(fuzed, (GriefState)CBCConfigs.server().munitions.damageRestriction.get());

         for (Entity e : ctx.hitEntities()) {
            pctx.addEntity(e);
         }

         if (acc.invokeCanDetonate(fz -> fz.onProjectileClip(fuzeStack, fuzed, start, end, pctx, baseFuze))) {
            this.detonate(this.warheadpos, fuzed);
            fuzed.discard();
            this.warhead = null;
            this.warheadpos = null;
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   protected boolean onHitEntity(Entity entity, MissileProjectileContext ctx) {
      if (this.level().isClientSide) {
         return false;
      } else {
         if (this.warhead != null) {
            ProjectileContext wctx = new ProjectileContext(this.warhead, (GriefState)CBCConfigs.server().munitions.damageRestriction.get());

            for (Entity e : ctx.hitEntities()) {
               wctx.addEntity(e);
            }

            ((AbstractProjectileAccessor)this.warhead)
               .invokeImpact(new EntityHitResult(entity), new ImpactResult(KinematicOutcome.PENETRATE, this.warhead.getProjectileMass() <= 0.0F), wctx);
            EntityDamagePropertiesComponent props = this.warhead.getDamageProperties();
            if (props != null) {
               entity.setDeltaMovement(this.getDeltaMovement().scale((double)props.knockback()));
               DamageSource source = this.indirectArtilleryFire(props.ignoresEntityArmor());
               if (props.ignoresInvulnerability()) {
                  entity.invulnerableTime = 0;
               }

               entity.hurt(source, props.entityDamage());
               if (!props.rendersInvulnerable()) {
                  entity.invulnerableTime = 0;
               }
            }
         }

         return this.onImpact(new EntityHitResult(entity), new ImpactResult(KinematicOutcome.PENETRATE, false), ctx);
      }
   }

   protected DamageSource getDamage() {
      boolean bypassesArmor = false;
      if (this.warhead != null) {
         bypassesArmor = this.warhead.getDamageProperties().ignoresEntityArmor();
      }

      return new CannonDamageSource(CannonDamageSource.getDamageRegistry(this.level()).getHolderOrThrow(CBCDamageTypes.CANNON_PROJECTILE), bypassesArmor);
   }

   protected void tickWarhead() {
      if (this.contraption != null && this.warhead != null && this.warheadpos != null) {
         if (this.warhead instanceof FuzedBigCannonProjectile fuzed) {
            fuzed.setPos(this.toGlobalVector(Vec3.atCenterOf(this.warheadpos), 0.0F));
            fuzed.setDeltaMovement(this.getDeltaMovement());
            FuzeMixin acc = (FuzeMixin)fuzed;
            if (acc.invokeCanDetonate(fz -> fz.onProjectileTick(acc.getFuze(), fuzed))) {
               this.detonate(this.warheadpos, fuzed);
               fuzed.discard();
               this.warhead = null;
               this.warheadpos = null;
            }
         }
      }
   }

   protected Vec3 getForces(Vec3 velocity) {
      return velocity.normalize().scale(-this.getDragForce(velocity)).add(0.0, this.getMissileGravity(), 0.0);
   }

   private static boolean isFinite(Vec3 vector) {
      return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
   }

   protected double getMissileGravity() {
      return this.isNoGravity()
         ? 0.0
         : (double)((Float)this.entityData.get(GRAVITY)).floatValue() * DimensionMunitionPropertiesHandler.getProperties(this.level()).gravityMultiplier();
   }

   protected double getDragForce(Vec3 velocity) {
      double vel = velocity.length();
      double formDrag = BALLISTIC_PROPERTIES.drag();
      double density = DimensionMunitionPropertiesHandler.getProperties(this.level()).dragMultiplier();
      FluidState fluidState = this.level().getFluidState(this.blockPosition());
      if (!fluidState.isEmpty()) {
         density += FluidDragHandler.getFluidDrag(fluidState);
      }

      double drag = formDrag * density * vel;
      return Math.min(drag, vel);
   }

   protected boolean onImpactFluid(
      MissileProjectileContext projectileContext, BlockState blockState, FluidState fluidState, Vec3 impactPos, BlockHitResult fluidHitResult
   ) {
      Vec3 pos = this.position();
      Vec3 accel = this.getForces(this.getDeltaMovement());
      Vec3 curVel = this.getDeltaMovement().add(accel);
      Vec3 normal = CBCUtils.getSurfaceNormalVector(this.level(), fluidHitResult);
      double incidence = Math.max(0.0, curVel.normalize().dot(normal.reverse()));
      double velMag = curVel.length();
      double mass = 0.0;
      if (this.blockMass.containsKey(this.capPos)) {
         mass = (double)this.blockMass.get(this.capPos).floatValue();
      }

      double projectileDeflection = 0.7;
      double incidentVel = velMag * incidence;
      double momentum = mass * incidentVel;
      double fluidDensity = FluidDragHandler.getFluidDrag(fluidState);
      boolean canBounce = (Boolean)CBCConfigs.server().munitions.projectilesCanBounce.get();
      double baseChance = (double)CBCConfigs.server().munitions.baseProjectileFluidBounceChance.getF();
      boolean criticalAngle = projectileDeflection > 0.01 && incidence <= projectileDeflection;
      boolean buoyant = fluidDensity > 0.01 && momentum < fluidDensity;
      double incidenceFactor = criticalAngle ? Math.max(0.0, 1.0 - incidence / projectileDeflection) : 0.0;
      double massFactor = buoyant ? 0.0 : Math.max(0.0, 1.0 - momentum / fluidDensity);
      double chance = Math.max(baseChance, incidenceFactor * massFactor);
      boolean bounced = canBounce && criticalAngle && buoyant && this.level().getRandom().nextDouble() < chance;
      if (bounced) {
         this.setContraptionMotion(fluidHitResult.getLocation().subtract(pos));
         this.pendingVelocity = reflectVelocity(curVel, normal, 0.35);
      }

      if (!this.level().isClientSide) {
         Vec3 effectNormal = bounced ? normal.scale(incidentVel) : curVel.reverse();
         Vec3 fluidExplosionPos = fluidHitResult.getLocation();
         projectileContext.addPlayedEffect(
            new ClientboundPlayBlockHitEffectPacket(
               blockState,
               this.getType(),
               bounced,
               true,
               fluidExplosionPos.x,
               fluidExplosionPos.y,
               fluidExplosionPos.z,
               (float)effectNormal.x,
               (float)effectNormal.y,
               (float)effectNormal.z
            )
         );
      }

      return bounced;
   }

   protected ImpactResult calculateBlockPenetration(MissileProjectileContext projectileContext, BlockState state, BlockHitResult blockHitResult) {
      BlockPos pos = blockHitResult.getBlockPos();
      Vec3 hitLoc = blockHitResult.getLocation();
      BallisticPropertiesComponent ballistics = BALLISTIC_PROPERTIES;
      double mass = 0.0;
      if (this.contraption.getBlocks().isEmpty()) {
         this.discard();
         return new ImpactResult(KinematicOutcome.STOP, true);
      } else {
         if (this.blockMass.containsKey(this.capPos)) {
            mass = (double)this.blockMass.get(this.capPos).floatValue();
         }

         if (ballistics == null) {
            this.discard();
            return new ImpactResult(KinematicOutcome.STOP, true);
         } else {
            BlockArmorPropertiesProvider blockArmor = BlockArmorPropertiesHandler.getProperties(state);
            boolean unbreakable = projectileContext.griefState() == GriefState.NO_DAMAGE || state.getDestroySpeed(this.level(), pos) == -1.0F;
            Vec3 accel = this.getForces(this.getDeltaMovement());
            Vec3 curVel = this.getDeltaMovement().add(accel);
            Vec3 normal = CBCUtils.getSurfaceNormalVector(this.level(), blockHitResult);
            double incidence = Math.max(0.0, curVel.normalize().dot(normal.reverse()));
            double velMag = curVel.length();
            double bonusMomentum = 1.0
               + Math.max(
                  0.0,
                  (velMag - (double)CBCConfigs.server().munitions.minVelocityForPenetrationBonus.getF())
                     * (double)CBCConfigs.server().munitions.penetrationBonusScale.getF()
               );
            double incidentVel = velMag * incidence;
            double momentum = mass * incidentVel * bonusMomentum;
            double toughness = blockArmor.toughness(this.level(), state, pos, true);
            double toughnessPenalty = toughness - momentum;
            double hardnessPenalty = blockArmor.hardness(this.level(), state, pos, true) - (double)ballistics.penetration();
            double projectileDeflection = (double)ballistics.deflection();
            double baseChance = (double)CBCConfigs.server().munitions.baseProjectileBounceChance.getF();
            double bounceChance = !(projectileDeflection < 0.01) && !(incidence > projectileDeflection)
               ? Math.max(baseChance, 1.0 - incidence / projectileDeflection)
               : 0.0;
            boolean surfaceImpact = this.canHitSurface();
            boolean canBounce = (Boolean)CBCConfigs.server().munitions.projectilesCanBounce.get();
            boolean blockBroken = toughnessPenalty < 0.01 && !unbreakable;
            KinematicOutcome outcome;
            if (surfaceImpact && canBounce && this.level().getRandom().nextDouble() < bounceChance) {
               outcome = KinematicOutcome.BOUNCE;
            } else if (blockBroken && !this.level().isClientSide) {
               outcome = KinematicOutcome.PENETRATE;
            } else {
               outcome = KinematicOutcome.STOP;
            }

            boolean shatter = surfaceImpact && outcome != KinematicOutcome.BOUNCE && hardnessPenalty > (double)ballistics.toughness();
            float durabilityPenalty = ((float)Math.max(0.0, hardnessPenalty) + 1.0F) * (float)toughness / (float)incidentVel;
            if (this.warheadpos == this.capPos) {
               state.onProjectileHit(this.level(), state, blockHitResult, this.warhead);
            } else {
               state.onProjectileHit(this.level(), state, blockHitResult, new SolidShotProjectile((EntityType)CBCEntityTypes.SHOT.get(), this.level()));
            }

            if (!this.level().isClientSide) {
               boolean bounced = outcome == KinematicOutcome.BOUNCE;
               Vec3 effectNormal;
               if (bounced) {
                  effectNormal = reflectVelocity(curVel, normal, 0.35);
               } else {
                  effectNormal = curVel.reverse();
               }

               for (BlockState state1 : blockArmor.containedBlockStates(this.level(), state, pos.immutable(), true)) {
                  projectileContext.addPlayedEffect(
                     new ClientboundPlayBlockHitEffectPacket(
                        state1,
                        this.getType(),
                        bounced,
                        true,
                        hitLoc.x,
                        hitLoc.y,
                        hitLoc.z,
                        (float)effectNormal.x,
                        (float)effectNormal.y,
                        (float)effectNormal.z
                     )
                  );
               }
            }

            if (blockBroken) {
               this.blockMass.put(this.capPos, incidentVel < 1.0E-4 ? 0.0F : Math.max(this.blockMass.get(this.capPos) - durabilityPenalty, 0.0F));
               this.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
               if (surfaceImpact) {
                  float f = (float)toughness / (float)momentum;
                  float overPenetrationPower = f < 0.15F ? 2.0F - 2.0F * f : 0.0F;
                  if (overPenetrationPower > 0.0F && outcome == KinematicOutcome.PENETRATE) {
                     projectileContext.queueExplosion(pos, overPenetrationPower);
                  }
               }
            } else {
               if (outcome == KinematicOutcome.STOP) {
                  this.blockMass.put(this.capPos, 0.0F);
               } else if (this.blockMass.containsKey(this.capPos)) {
                  this.blockMass.put(this.capPos, incidentVel < 1.0E-4 ? 0.0F : Math.max(this.blockMass.get(this.capPos) - durabilityPenalty / 2.0F, 0.0F));
               }

               Vec3 spallLoc = hitLoc.add(curVel.normalize().scale(2.0));
               if (!this.level().isClientSide) {
                  ImpactExplosion explosion = new ImpactExplosion(
                     this.level(), this, this.indirectArtilleryFire(false), spallLoc.x, spallLoc.y, spallLoc.z, 2.0F, 2.0F, BlockInteraction.KEEP
                  );
                  CreateBigCannons.handleCustomExplosion(this.level(), explosion);
               }

               SoundType sound = state.getSoundType(this.level(), pos, this);
               if (!this.level().isClientSide) {
                  this.level()
                     .playSound(null, spallLoc.x, spallLoc.y, spallLoc.z, sound.getBreakSound(), SoundSource.BLOCKS, sound.getVolume(), sound.getPitch());
               }
            }

            shatter |= this.onImpact(blockHitResult, new ImpactResult(outcome, shatter), projectileContext);
            return new ImpactResult(outcome, shatter);
         }
      }
   }

   protected boolean canHitSurface() {
      return this.lastPenetratedBlock.isAir() && this.penetrationTime == 0;
   }

   public DamageSource indirectArtilleryFire(boolean bypassArmor) {
      return new CannonDamageSource(CannonDamageSource.getDamageRegistry(this.level()).getHolderOrThrow(CBCDamageTypes.CANNON_PROJECTILE), bypassArmor);
   }

   protected void detonate(BlockPos pos, FuzedBigCannonProjectile fuzed) {
      if (!this.level().isClientSide && this.level() instanceof ServerLevel sl) {
         this.chainSystem.releaseAll(sl);
      }

      if (this.level().isClientSide && this.engineSound != null) {
         Minecraft.getInstance().getSoundManager().stop(this.engineSound);
      }

      BlockPos oldPos = this.blockPosition();
      Vec3 oldDelta = this.getDeltaMovement();
      fuzed.setDeltaMovement(oldDelta);
      ((FuzeMixin)fuzed).invokeDetonate(this.toGlobalVector(Vec3.atCenterOf(pos), 0.0F));
      this.setPos((double)oldPos.getX(), (double)oldPos.getY(), (double)oldPos.getZ());
      this.setContraptionMotion(oldDelta.scale(0.75));
      this.discard();
   }

   public Vec3 getOrientation() {
      return new Vec3(
         (double)((Float)this.entityData.get(HEADING_X)).floatValue(),
         (double)((Float)this.entityData.get(HEADING_Y)).floatValue(),
         (double)((Float)this.entityData.get(HEADING_Z)).floatValue()
      );
   }

   protected boolean onImpact(HitResult hitResult, ImpactResult impactResult, MissileProjectileContext projectileContext) {
      if (this.warhead instanceof FuzedBigCannonProjectile fuzed && this.warheadpos != null) {
         Vec3 warheadWorld = this.toGlobalVector(Vec3.atCenterOf(this.warheadpos), 1.0F);
         fuzed.setPos(warheadWorld);
         fuzed.setDeltaMovement(this.getDeltaMovement());
         FuzeMixin acc = (FuzeMixin)fuzed;
         boolean baseFuze = acc.invokeGetFuzeProperties().baseFuze();
         if (acc.invokeCanDetonate(fz -> fz.onProjectileImpact(acc.getFuze(), fuzed, hitResult, impactResult, baseFuze))) {
            if (fuzed instanceof MissileWarheadProjectile missileWarhead) {
               missileWarhead.markNextDetonationAsImpact();
            }
            this.detonate(this.warheadpos, fuzed);
            fuzed.discard();
            this.warhead = null;
            this.warheadpos = null;
            return true;
         }

         return false;
      }

      if (!this.level().isClientSide && impactResult.kinematics() == KinematicOutcome.STOP) {
         this.level().explode(this, this.getX(), this.getY(), this.getZ(), 4.0F, ExplosionInteraction.TNT);
         this.discard();
         return true;
      } else {
         return false;
      }
   }

   private static record PhysicsStep(Vec3 pos, Vec3 vel) {
   }
}
