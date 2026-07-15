package com.happysg.kaboom.block.missiles.chaining;

import com.happysg.kaboom.block.missiles.MissileEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ChainSystem {
   private final List<AnchorPoint> anchors = new ArrayList<>();
   private ChainSystem.ChainingState state = ChainSystem.ChainingState.EMPTY;
   @Nullable
   private UUID winchTargetMob;
   private float winchProgress;
   @Nullable
   private UUID activeLinkerPlayer;
   @Nullable
   private UUID activeLinkerChainId;
   private int activeLinkerEntityId = -1;
   private final Map<UUID, Integer> breakAttemptTimers = new HashMap<>();
   private int validationTimer = 0;
   private static final int BREAK_ATTEMPT_MIN_TICKS = 40;
   private static final int BREAK_ATTEMPT_MAX_TICKS = 60;
   private static final float BOSS_BREAK_MULTIPLIER = 3.0F;
   private static final double WINCH_PULL_PER_CHAIN = 50.0;
   private static final double RESIST_MULTIPLIER = 2.0;
   private static final int VALIDATION_INTERVAL = 20;

   public ChainSystem.ChainingState getState() {
      return this.state;
   }

   public boolean isWinching() {
      return this.state == ChainSystem.ChainingState.WINCHING;
   }

   @Nullable
   public UUID getWinchTargetMob() {
      return this.winchTargetMob;
   }

   public List<AnchorPoint> getAnchors() {
      return this.anchors;
   }

   @Nullable
   public UUID getActiveLinkerPlayer() {
      return this.activeLinkerPlayer;
   }

   @Nullable
   public UUID getActiveLinkerChainId() {
      return this.activeLinkerChainId;
   }

   public int getActiveLinkerEntityId() {
      return this.activeLinkerEntityId;
   }

   public void setActiveLinker(@Nullable UUID playerId, @Nullable UUID chainId) {
      this.activeLinkerPlayer = playerId;
      this.activeLinkerChainId = chainId;
   }

   public void clearActiveLinker() {
      this.activeLinkerPlayer = null;
      this.activeLinkerChainId = null;
      this.activeLinkerEntityId = -1;
   }

   public void addAnchor(AnchorPoint anchor) {
      this.anchors.add(anchor);
      this.recalculateState();
   }

   @Nullable
   private Entity findEntity(ServerLevel level, UUID entityId) {
      Entity entity = level.getEntity(entityId);
      return (Entity)(entity != null ? entity : level.getServer().getPlayerList().getPlayer(entityId));
   }

   @Nullable
   public AnchorPoint findNearestAnchorWithoutChain(BlockPos clickedOffset, double maxDist) {
      AnchorPoint nearest = null;
      double nearestDist = maxDist;
      Vec3 clickVec = Vec3.atCenterOf(clickedOffset);

      for (AnchorPoint anchor : this.anchors) {
         if (!anchor.hasLink()) {
            double dist = Vec3.atCenterOf(anchor.getBlockOffset()).distanceTo(clickVec);
            if (dist < nearestDist) {
               nearestDist = dist;
               nearest = anchor;
            }
         }
      }

      return nearest;
   }

   @Nullable
   public ChainLink findDanglingChainForPlayer(UUID playerId) {
      if (!playerId.equals(this.activeLinkerPlayer)) {
         return null;
      } else if (this.activeLinkerChainId == null) {
         return null;
      } else {
         for (AnchorPoint anchor : this.anchors) {
            if (anchor.getLink() != null
               && anchor.getLink().getId().equals(this.activeLinkerChainId)
               && anchor.getLink().getState() == ChainLink.State.DANGLING) {
               return anchor.getLink();
            }
         }

         return null;
      }
   }

   public int getAttachedChainCount(UUID mobId) {
      int count = 0;

      for (AnchorPoint anchor : this.anchors) {
         ChainLink link = anchor.getLink();
         if (link != null && mobId.equals(link.getTargetMobId())) {
            count++;
         }
      }

      return count;
   }

   public void startWinch(UUID mobId) {
      this.winchTargetMob = mobId;
      this.winchProgress = 0.0F;
      this.state = ChainSystem.ChainingState.WINCHING;
   }

   public void cancelWinch() {
      this.winchTargetMob = null;
      this.winchProgress = 0.0F;
      this.recalculateState();
   }

   public double calculateWeight(ServerLevel level) {
      double totalWeight = 0.0;
      Set<UUID> securedMobs = new HashSet<>();

      for (AnchorPoint anchor : this.anchors) {
         ChainLink link = anchor.getLink();
         if (link != null && link.getState() == ChainLink.State.SECURED && link.getTargetMobId() != null) {
            securedMobs.add(link.getTargetMobId());
         }
      }

      for (UUID mobId : securedMobs) {
         if (this.findEntity(level, mobId) instanceof Mob mob) {
            AABB bb = mob.getBoundingBox();
            double volume = bb.getXsize() * bb.getYsize() * bb.getZsize();
            totalWeight += volume * 10.0 + (double)mob.getMaxHealth() * 0.5 + (double)mob.getArmorValue() * 2.0;
         }
      }

      return totalWeight;
   }

   public void tickFromBlock(BlockPos thrusterPos, ServerLevel level) {
      this.validationTimer++;
      if (this.validationTimer >= 20) {
         this.validationTimer = 0;
         this.cleanupDeadLinks(thrusterPos, level);
      }

      switch (this.state) {
         case TETHERING:
            this.tickTethering(thrusterPos, level);
            break;
         case WINCHING:
            this.tickWinching(thrusterPos, level);
      }
   }

   public void enforceConstraintsFromBlock(BlockPos thrusterPos, ServerLevel level) {
      this.enforceTetherRange(thrusterPos, level);
   }

   public void tickFromEntity(MissileEntity missile, ServerLevel level) {
      this.validationTimer++;
      if (this.validationTimer >= 20) {
         this.validationTimer = 0;
         this.cleanupDeadLinksNoPos(level);
      }

      this.enforceTetherRangeFromEntity(missile, level);
   }

   private void tickTethering(BlockPos thrusterPos, ServerLevel level) {
      Set<UUID> tetheredMobs = new HashSet<>();

      for (AnchorPoint anchor : this.anchors) {
         ChainLink link = anchor.getLink();
         if (link != null && link.getState() == ChainLink.State.TETHERED && link.getTargetMobId() != null) {
            tetheredMobs.add(link.getTargetMobId());
         }
      }

      for (UUID mobId : tetheredMobs) {
         Entity entity = this.findEntity(level, mobId);
         if (entity instanceof Mob) {
            Mob mob = (Mob)entity;
            int timer = this.breakAttemptTimers.getOrDefault(mobId, 0);
            if (timer <= 0) {
               this.attemptBreakFree(mob, thrusterPos, level);
               timer = 40 + level.getRandom().nextInt(21);
            }

            this.breakAttemptTimers.put(mobId, timer - 1);
         }
      }
   }

   private void attemptBreakFree(Mob mob, BlockPos thrusterPos, ServerLevel level) {
      UUID mobId = mob.getUUID();
      AABB bb = mob.getBoundingBox();
      double volume = bb.getXsize() * bb.getYsize() * bb.getZsize();
      double attackDamage = mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0.0;
      float breakForce = (float)(attackDamage + volume * 2.0);
      if (!mob.canChangeDimensions(level, level)) {
         breakForce *= 3.0F;
      }

      int chainCount = this.getAttachedChainCount(mobId);
      List<AnchorPoint> toRemoveLinks = new ArrayList<>();

      for (AnchorPoint anchor : this.anchors) {
         ChainLink link = anchor.getLink();
         if (link != null && mobId.equals(link.getTargetMobId()) && link.getState() == ChainLink.State.TETHERED && link.tickStrain(breakForce, chainCount)) {
            toRemoveLinks.add(anchor);
            this.spawnChainBreakEffects(level, mob.position());
         }
      }

      for (AnchorPoint anchorx : toRemoveLinks) {
         anchorx.setLink(null);
      }

      if (this.getAttachedChainCount(mobId) == 0) {
         this.breakAttemptTimers.remove(mobId);
      }

      this.recalculateState();
   }

   private void tickWinching(BlockPos thrusterPos, ServerLevel level) {
      if (this.winchTargetMob == null) {
         this.cancelWinch();
      } else if (!(this.findEntity(level, this.winchTargetMob) instanceof Mob mob)) {
         this.cancelWinch();
      } else {
         int chainCount = this.getAttachedChainCount(this.winchTargetMob);
         if (chainCount == 0) {
            this.cancelWinch();
         } else {
            double pullForce = (double)chainCount * 50.0;
            AABB bb = mob.getBoundingBox();
            double volume = bb.getXsize() * bb.getYsize() * bb.getZsize();
            double attackDamage = mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0.0;
            double resistForce = attackDamage + volume * 2.0;
            double netForce = pullForce - resistForce;
            if (netForce > 0.0) {
               float progressIncrement = (float)(netForce * 0.001);
               this.winchProgress = Math.min(1.0F, this.winchProgress + progressIncrement);
               Vec3 targetPos = Vec3.atCenterOf(thrusterPos).add(0.0, 1.0, 0.0);
               Vec3 mobPos = mob.position();
               Vec3 lerpedPos = mobPos.lerp(targetPos, (double)this.winchProgress);
               mob.setPos(lerpedPos.x, lerpedPos.y, lerpedPos.z);
               if (this.winchProgress >= 1.0F) {
                  this.secureMob(mob, level);
               }
            } else {
               float strainDamage = (float)Math.abs(netForce) * 0.1F;

               for (AnchorPoint anchor : this.anchors) {
                  ChainLink link = anchor.getLink();
                  if (link != null && this.winchTargetMob.equals(link.getTargetMobId()) && link.tickStrain(strainDamage, chainCount)) {
                     anchor.setLink(null);
                     this.spawnChainBreakEffects(level, mob.position());
                  }
               }

               if (this.getAttachedChainCount(this.winchTargetMob) == 0) {
                  this.cancelWinch();
               }
            }
         }
      }
   }

   private void secureMob(Mob mob, ServerLevel level) {
      mob.setNoAi(true);

      for (AnchorPoint anchor : this.anchors) {
         ChainLink link = anchor.getLink();
         if (link != null && mob.getUUID().equals(link.getTargetMobId())) {
            link.setState(ChainLink.State.SECURED);
         }
      }

      this.winchTargetMob = null;
      this.winchProgress = 0.0F;
      this.recalculateState();
   }

   private void enforceTetherRange(BlockPos thrusterPos, ServerLevel level) {
      for (AnchorPoint anchor : this.anchors) {
         ChainLink link = anchor.getLink();
         if (link != null && link.getState() == ChainLink.State.TETHERED && link.getTargetMobId() != null) {
            Entity entity = this.findEntity(level, link.getTargetMobId());
            if (entity instanceof Mob mob) {
               Vec3 anchorWorld = anchor.getWorldPos(thrusterPos);
               this.constrainMobToAnchor(anchorWorld, link, mob, level);
            } else if (entity instanceof Player player) {
               Vec3 anchorWorld = anchor.getWorldPos(thrusterPos);
               this.breakPlayerFreeIfTooFar(anchorWorld, anchor, link, player, level);
            }
         }
      }
   }

   private void enforceTetherRangeFromEntity(MissileEntity missile, ServerLevel level) {
      for (AnchorPoint anchor : this.anchors) {
         ChainLink link = anchor.getLink();
         if (link != null && link.getState() == ChainLink.State.TETHERED && link.getTargetMobId() != null) {
            Entity entity = this.findEntity(level, link.getTargetMobId());
            Vec3 anchorWorld = missile.toGlobalVector(anchor.toContraptionLocalVec3(), 1.0F);
            if (entity instanceof Mob mob) {
               this.constrainMobToAnchor(anchorWorld, link, mob, level);
            } else if (entity instanceof Player player) {
               this.constrainPlayerToAnchor(anchorWorld, link, player, level);
            }
         }
      }
   }

   private void constrainMobToAnchor(Vec3 anchorWorld, ChainLink link, Mob mob, ServerLevel level) {
      Vec3 mobPos = mob.position();
      double distance = anchorWorld.distanceTo(mobPos);
      double maxLength = (double)link.getMaxLength();
      if (!(maxLength <= 0.0)) {
         if (!(distance <= maxLength)) {
            Vec3 direction = mobPos.subtract(anchorWorld).normalize();
            Vec3 correctedPos = anchorWorld.add(direction.scale(maxLength));
            mob.teleportTo(correctedPos.x, correctedPos.y, correctedPos.z);
            mob.hurtMarked = true;
            mob.getNavigation().stop();
            Vec3 vel = mob.getDeltaMovement();
            Vec3 toAnchor = anchorWorld.subtract(correctedPos).normalize();
            double dot = vel.dot(toAnchor);
            if (dot < 0.0) {
               Vec3 awayComponent = toAnchor.scale(dot);
               mob.setDeltaMovement(vel.subtract(awayComponent));
               float impactForce = (float)Math.abs(dot);
               if (impactForce > 0.05F) {
                  level.playSound(
                     null,
                     mob.blockPosition(),
                     SoundEvents.CHAIN_STEP,
                     SoundSource.BLOCKS,
                     Math.min(impactForce * 3.0F, 1.0F),
                     0.8F + level.random.nextFloat() * 0.4F
                  );
               }
            }
         }
      }
   }

   private void breakPlayerFreeIfTooFar(Vec3 anchorWorld, AnchorPoint anchor, ChainLink link, Player player, ServerLevel level) {
      double maxLength = (double)link.getMaxLength();
      if (!(maxLength <= 0.0) && !(anchorWorld.distanceTo(player.position()) <= maxLength)) {
         anchor.setLink(null);
         this.spawnChainBreakEffects(level, player.position());
         this.recalculateState();
      }
   }

   private void constrainPlayerToAnchor(Vec3 anchorWorld, ChainLink link, Player player, ServerLevel level) {
      Vec3 playerPos = player.position();
      double distance = anchorWorld.distanceTo(playerPos);
      double maxLength = (double)link.getMaxLength();
      if (!(maxLength <= 0.0) && !(distance <= maxLength)) {
         Vec3 direction = playerPos.subtract(anchorWorld).normalize();
         Vec3 correctedPos = anchorWorld.add(direction.scale(maxLength));
         player.teleportTo(correctedPos.x, correctedPos.y, correctedPos.z);
         player.hurtMarked = true;
         Vec3 toAnchor = anchorWorld.subtract(correctedPos).normalize();
         Vec3 vel = player.getDeltaMovement();
         double dot = vel.dot(toAnchor);
         if (dot < 0.0) {
            Vec3 awayComponent = toAnchor.scale(dot);
            player.setDeltaMovement(vel.subtract(awayComponent).add(toAnchor.scale(0.15)));
         } else {
            player.setDeltaMovement(vel.add(toAnchor.scale(0.15)));
         }

         if (distance - maxLength > 0.25) {
            level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.8F, 0.8F + level.random.nextFloat() * 0.4F);
         }
      }
   }

   private void cleanupDeadLinks(BlockPos thrusterPos, ServerLevel level) {
      boolean changed = false;

      for (AnchorPoint anchor : this.anchors) {
         ChainLink link = anchor.getLink();
         if (link != null && link.getTargetMobId() != null) {
            Entity entity = this.findEntity(level, link.getTargetMobId());
            if (entity == null || !entity.isAlive()) {
               if (link.getState() == ChainLink.State.SECURED && entity instanceof Mob mob) {
                  mob.setNoAi(false);
               }

               anchor.setLink(null);
               this.spawnChainBreakEffects(level, Vec3.atCenterOf(thrusterPos));
               changed = true;
            }
         }
      }

      if (changed) {
         this.recalculateState();
      }
   }

   private void cleanupDeadLinksNoPos(ServerLevel level) {
      boolean changed = false;

      for (AnchorPoint anchor : this.anchors) {
         ChainLink link = anchor.getLink();
         if (link != null && link.getTargetMobId() != null) {
            Entity entity = this.findEntity(level, link.getTargetMobId());
            if (entity == null || !entity.isAlive()) {
               if (link.getState() == ChainLink.State.SECURED && entity instanceof Mob mob) {
                  mob.setNoAi(false);
                  mob.stopRiding();
               }

               anchor.setLink(null);
               changed = true;
            }
         }
      }

      if (changed) {
         this.recalculateState();
      }
   }

   @Nullable
   public UUID findNearestTetheredMob(BlockPos thrusterPos, ServerLevel level) {
      UUID nearest = null;
      double nearestDist = Double.MAX_VALUE;
      Vec3 basePos = Vec3.atCenterOf(thrusterPos);
      Set<UUID> seen = new HashSet<>();

      for (AnchorPoint anchor : this.anchors) {
         ChainLink link = anchor.getLink();
         if (link != null && link.getState() == ChainLink.State.TETHERED && link.getTargetMobId() != null && seen.add(link.getTargetMobId())) {
            Entity entity = this.findEntity(level, link.getTargetMobId());
            if (entity != null) {
               double dist = entity.position().distanceTo(basePos);
               if (dist < nearestDist) {
                  nearestDist = dist;
                  nearest = link.getTargetMobId();
               }
            }
         }
      }

      return nearest;
   }

   @Nullable
   public AnchorPoint findAnchorForLink(ChainLink link) {
      for (AnchorPoint anchor : this.anchors) {
         if (anchor.getLink() != null && anchor.getLink().getId().equals(link.getId())) {
            return anchor;
         }
      }

      return null;
   }

   public Set<UUID> getSecuredMobIds() {
      Set<UUID> ids = new HashSet<>();

      for (AnchorPoint anchor : this.anchors) {
         ChainLink link = anchor.getLink();
         if (link != null && link.getState() == ChainLink.State.SECURED && link.getTargetMobId() != null) {
            ids.add(link.getTargetMobId());
         }
      }

      return ids;
   }

   public Set<UUID> getTetheredMobIds() {
      Set<UUID> ids = new HashSet<>();

      for (AnchorPoint anchor : this.anchors) {
         ChainLink link = anchor.getLink();
         if (link != null && link.getState() == ChainLink.State.TETHERED && link.getTargetMobId() != null) {
            ids.add(link.getTargetMobId());
         }
      }

      return ids;
   }

   public Set<UUID> getAttachedPlayerIds(ServerLevel level) {
      Set<UUID> ids = new HashSet<>();

      for (AnchorPoint anchor : this.anchors) {
         ChainLink link = anchor.getLink();
         if (link != null && link.getTargetMobId() != null && (link.getState() == ChainLink.State.TETHERED || link.getState() == ChainLink.State.SECURED)) {
            Entity entity = this.findEntity(level, link.getTargetMobId());
            if (entity instanceof Player) {
               ids.add(link.getTargetMobId());
            }
         }
      }

      return ids;
   }

   public void releaseAll(ServerLevel level) {
      for (AnchorPoint anchor : this.anchors) {
         ChainLink link = anchor.getLink();
         if (link != null
            && link.getTargetMobId() != null
            && link.getState() == ChainLink.State.SECURED
            && this.findEntity(level, link.getTargetMobId()) instanceof Mob mob) {
            mob.setNoAi(false);
            mob.stopRiding();
         }

         anchor.setLink(null);
      }

      this.winchTargetMob = null;
      this.winchProgress = 0.0F;
      this.recalculateState();
   }

   public void breakUnsecuredChains(BlockPos thrusterPos, ServerLevel level) {
      for (AnchorPoint anchor : this.anchors) {
         ChainLink link = anchor.getLink();
         if (link != null && link.getState() != ChainLink.State.SECURED) {
            if (link.getTargetMobId() != null) {
               Entity entity = this.findEntity(level, link.getTargetMobId());
               if (entity != null) {
                  this.spawnChainBreakEffects(level, entity.position());
               }
            } else {
               this.spawnChainBreakEffects(level, Vec3.atCenterOf(thrusterPos));
            }

            anchor.setLink(null);
         }
      }

      this.clearActiveLinker();
      this.recalculateState();
   }

   public void breakDanglingChains(BlockPos thrusterPos, ServerLevel level) {
      for (AnchorPoint anchor : this.anchors) {
         ChainLink link = anchor.getLink();
         if (link != null && link.getState() != ChainLink.State.SECURED && link.getState() != ChainLink.State.TETHERED) {
            this.spawnChainBreakEffects(level, Vec3.atCenterOf(thrusterPos));
            anchor.setLink(null);
         }
      }

      this.clearActiveLinker();
      this.recalculateState();
   }

   public void setLaunched() {
      this.state = ChainSystem.ChainingState.LAUNCHED;
   }

   public void recalculateState() {
      if (this.state != ChainSystem.ChainingState.LAUNCHED) {
         if (this.state != ChainSystem.ChainingState.WINCHING || this.winchTargetMob == null) {
            if (this.anchors.isEmpty()) {
               this.state = ChainSystem.ChainingState.EMPTY;
            } else {
               boolean hasSecured = false;
               boolean hasTethered = false;

               for (AnchorPoint anchor : this.anchors) {
                  ChainLink link = anchor.getLink();
                  if (link != null) {
                     if (link.getState() == ChainLink.State.SECURED) {
                        hasSecured = true;
                     }

                     if (link.getState() == ChainLink.State.TETHERED) {
                        hasTethered = true;
                     }
                  }
               }

               if (hasSecured) {
                  this.state = ChainSystem.ChainingState.SECURED;
               } else if (hasTethered) {
                  this.state = ChainSystem.ChainingState.TETHERING;
               } else {
                  this.state = ChainSystem.ChainingState.ANCHORED;
               }
            }
         }
      }
   }

   private void spawnChainBreakEffects(ServerLevel level, Vec3 pos) {
      level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
      level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.5, pos.z, 10, 0.3, 0.3, 0.3, 0.05);
      ItemEntity drop = new ItemEntity(level, pos.x, pos.y, pos.z, new ItemStack(Items.CHAIN));
      level.addFreshEntity(drop);
   }

   public void populateEntityIds(ServerLevel level) {
      for (AnchorPoint anchor : this.anchors) {
         ChainLink link = anchor.getLink();
         if (link != null) {
            if (link.getTargetMobId() != null) {
               Entity entity = this.findEntity(level, link.getTargetMobId());
               link.setTargetEntityId(entity != null ? entity.getId() : -1);
            } else {
               link.setTargetEntityId(-1);
            }
         }
      }

      if (this.activeLinkerPlayer != null) {
         ServerPlayer player = level.getServer().getPlayerList().getPlayer(this.activeLinkerPlayer);
         this.activeLinkerEntityId = player != null ? player.getId() : -1;
      } else {
         this.activeLinkerEntityId = -1;
      }
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      ListTag anchorList = new ListTag();

      for (AnchorPoint anchor : this.anchors) {
         anchorList.add(anchor.save());
      }

      tag.put("Anchors", anchorList);
      tag.putString("State", this.state.name());
      if (this.winchTargetMob != null) {
         tag.putUUID("WinchTargetMob", this.winchTargetMob);
      }

      tag.putFloat("WinchProgress", this.winchProgress);
      if (this.activeLinkerPlayer != null) {
         tag.putUUID("ActiveLinkerPlayer", this.activeLinkerPlayer);
      }

      if (this.activeLinkerChainId != null) {
         tag.putUUID("ActiveLinkerChainId", this.activeLinkerChainId);
      }

      tag.putInt("ActiveLinkerEntityId", this.activeLinkerEntityId);
      return tag;
   }

   public void load(CompoundTag tag) {
      this.anchors.clear();
      this.breakAttemptTimers.clear();
      ListTag anchorList = tag.getList("Anchors", 10);

      for (int i = 0; i < anchorList.size(); i++) {
         this.anchors.add(AnchorPoint.load(anchorList.getCompound(i)));
      }

      try {
         this.state = ChainSystem.ChainingState.valueOf(tag.getString("State"));
      } catch (IllegalArgumentException var4) {
         this.state = ChainSystem.ChainingState.EMPTY;
      }

      this.winchTargetMob = tag.hasUUID("WinchTargetMob") ? tag.getUUID("WinchTargetMob") : null;
      this.winchProgress = tag.getFloat("WinchProgress");
      this.activeLinkerPlayer = tag.hasUUID("ActiveLinkerPlayer") ? tag.getUUID("ActiveLinkerPlayer") : null;
      this.activeLinkerChainId = tag.hasUUID("ActiveLinkerChainId") ? tag.getUUID("ActiveLinkerChainId") : null;
      this.activeLinkerEntityId = tag.contains("ActiveLinkerEntityId") ? tag.getInt("ActiveLinkerEntityId") : -1;
   }

   public static enum ChainingState {
      EMPTY,
      ANCHORED,
      TETHERING,
      WINCHING,
      SECURED,
      LAUNCHED;
   }
}
