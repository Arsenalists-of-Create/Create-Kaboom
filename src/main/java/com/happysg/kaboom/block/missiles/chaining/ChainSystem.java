package com.happysg.kaboom.block.missiles.chaining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;

public class ChainSystem {

    public enum ChainingState {
        EMPTY,
        ANCHORED,
        TETHERING,
        WINCHING,
        SECURED,
        LAUNCHED
    }

    private final List<AnchorPoint> anchors = new ArrayList<>();
    private ChainingState state = ChainingState.EMPTY;
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
    private static final float BOSS_BREAK_MULTIPLIER = 3.0f;
    private static final double WINCH_PULL_PER_CHAIN = 50.0;
    private static final double RESIST_MULTIPLIER = 2.0;
    private static final int VALIDATION_INTERVAL = 20;

    public ChainingState getState() {
        return state;
    }

    public boolean isWinching() {
        return state == ChainingState.WINCHING;
    }

    @Nullable
    public UUID getWinchTargetMob() {
        return winchTargetMob;
    }

    public List<AnchorPoint> getAnchors() {
        return anchors;
    }

    @Nullable
    public UUID getActiveLinkerPlayer() {
        return activeLinkerPlayer;
    }

    @Nullable
    public UUID getActiveLinkerChainId() {
        return activeLinkerChainId;
    }

    public int getActiveLinkerEntityId() {
        return activeLinkerEntityId;
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
        anchors.add(anchor);
        recalculateState();
    }

    @Nullable
    public AnchorPoint findNearestAnchorWithoutChain(BlockPos clickedOffset, double maxDist) {
        AnchorPoint nearest = null;
        double nearestDist = maxDist;
        Vec3 clickVec = Vec3.atCenterOf(clickedOffset);
        for (AnchorPoint anchor : anchors) {
            if (anchor.hasLink()) continue;
            double dist = Vec3.atCenterOf(anchor.getBlockOffset()).distanceTo(clickVec);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = anchor;
            }
        }
        return nearest;
    }

    @Nullable
    public ChainLink findDanglingChainForPlayer(UUID playerId) {
        if (!playerId.equals(activeLinkerPlayer)) return null;
        if (activeLinkerChainId == null) return null;
        for (AnchorPoint anchor : anchors) {
            if (anchor.getLink() != null && anchor.getLink().getId().equals(activeLinkerChainId)) {
                if (anchor.getLink().getState() == ChainLink.State.DANGLING) {
                    return anchor.getLink();
                }
            }
        }
        return null;
    }

    public int getAttachedChainCount(UUID mobId) {
        int count = 0;
        for (AnchorPoint anchor : anchors) {
            ChainLink link = anchor.getLink();
            if (link != null && mobId.equals(link.getTargetMobId())) {
                count++;
            }
        }
        return count;
    }

    public void startWinch(UUID mobId) {
        this.winchTargetMob = mobId;
        this.winchProgress = 0.0f;
        this.state = ChainingState.WINCHING;
    }

    public void cancelWinch() {
        this.winchTargetMob = null;
        this.winchProgress = 0.0f;
        recalculateState();
    }

    public double calculateWeight(ServerLevel level) {
        double totalWeight = 0;
        Set<UUID> securedMobs = new HashSet<>();
        for (AnchorPoint anchor : anchors) {
            ChainLink link = anchor.getLink();
            if (link != null && link.getState() == ChainLink.State.SECURED && link.getTargetMobId() != null) {
                securedMobs.add(link.getTargetMobId());
            }
        }
        for (UUID mobId : securedMobs) {
            Entity entity = level.getEntity(mobId);
            if (entity instanceof Mob mob) {
                AABB bb = mob.getBoundingBox();
                double volume = bb.getXsize() * bb.getYsize() * bb.getZsize();
                totalWeight += volume * 10.0 + mob.getMaxHealth() * 0.5 + mob.getArmorValue() * 2.0;
            }
        }
        return totalWeight;
    }

    public void tickFromBlock(BlockPos thrusterPos, ServerLevel level) {
        validationTimer++;
        if (validationTimer >= VALIDATION_INTERVAL) {
            validationTimer = 0;
            cleanupDeadLinks(thrusterPos, level);
        }

        switch (state) {
            case TETHERING -> tickTethering(thrusterPos, level);
            case WINCHING -> tickWinching(thrusterPos, level);
            default -> {}
        }

    }

    public void enforceConstraintsFromBlock(BlockPos thrusterPos, ServerLevel level) {
        enforceTetherRange(thrusterPos, level);
    }

    public void tickFromEntity(Vec3 entityPos, ServerLevel level) {
        validationTimer++;
        if (validationTimer >= VALIDATION_INTERVAL) {
            validationTimer = 0;
            cleanupDeadLinksNoPos(level);
        }

        enforceTetherRangeFromEntity(entityPos, level);
    }

    private void tickTethering(BlockPos thrusterPos, ServerLevel level) {
        Set<UUID> tetheredMobs = new HashSet<>();
        for (AnchorPoint anchor : anchors) {
            ChainLink link = anchor.getLink();
            if (link != null && link.getState() == ChainLink.State.TETHERED && link.getTargetMobId() != null) {
                tetheredMobs.add(link.getTargetMobId());
            }
        }

        for (UUID mobId : tetheredMobs) {
            Entity entity = level.getEntity(mobId);
            if (!(entity instanceof Mob mob)) continue;

            int timer = breakAttemptTimers.getOrDefault(mobId, 0);
            if (timer <= 0) {
                attemptBreakFree(mob, thrusterPos, level);
                timer = BREAK_ATTEMPT_MIN_TICKS +
                        level.getRandom().nextInt(BREAK_ATTEMPT_MAX_TICKS - BREAK_ATTEMPT_MIN_TICKS + 1);
            }
            breakAttemptTimers.put(mobId, timer - 1);
        }
    }

    private void attemptBreakFree(Mob mob, BlockPos thrusterPos, ServerLevel level) {
        UUID mobId = mob.getUUID();
        AABB bb = mob.getBoundingBox();
        double volume = bb.getXsize() * bb.getYsize() * bb.getZsize();
        double attackDamage = mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                ? mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
        float breakForce = (float) (attackDamage + volume * 2.0);

        if (!mob.canChangeDimensions(level, level)) {
            breakForce *= BOSS_BREAK_MULTIPLIER;
        }

        int chainCount = getAttachedChainCount(mobId);
        List<AnchorPoint> toRemoveLinks = new ArrayList<>();

        for (AnchorPoint anchor : anchors) {
            ChainLink link = anchor.getLink();
            if (link == null || !mobId.equals(link.getTargetMobId())) continue;
            if (link.getState() != ChainLink.State.TETHERED) continue;

            if (link.tickStrain(breakForce, chainCount)) {
                toRemoveLinks.add(anchor);
                spawnChainBreakEffects(level, mob.position());
            }
        }

        for (AnchorPoint anchor : toRemoveLinks) {
            anchor.setLink(null);
        }

        if (getAttachedChainCount(mobId) == 0) {
            breakAttemptTimers.remove(mobId);
        }

        recalculateState();
    }

    private void tickWinching(BlockPos thrusterPos, ServerLevel level) {
        if (winchTargetMob == null) {
            cancelWinch();
            return;
        }

        Entity entity = level.getEntity(winchTargetMob);
        if (!(entity instanceof Mob mob)) {
            cancelWinch();
            return;
        }

        int chainCount = getAttachedChainCount(winchTargetMob);
        if (chainCount == 0) {
            cancelWinch();
            return;
        }

        double pullForce = chainCount * WINCH_PULL_PER_CHAIN;
        AABB bb = mob.getBoundingBox();
        double volume = bb.getXsize() * bb.getYsize() * bb.getZsize();
        double attackDamage = mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                ? mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
        double resistForce = attackDamage + volume * RESIST_MULTIPLIER;

        double netForce = pullForce - resistForce;

        if (netForce > 0) {
            float progressIncrement = (float) (netForce * 0.001);
            winchProgress = Math.min(1.0f, winchProgress + progressIncrement);

            Vec3 targetPos = Vec3.atCenterOf(thrusterPos).add(0, 1, 0);
            Vec3 mobPos = mob.position();
            Vec3 lerpedPos = mobPos.lerp(targetPos, winchProgress);
            mob.setPos(lerpedPos.x, lerpedPos.y, lerpedPos.z);

            if (winchProgress >= 1.0f) {
                secureMob(mob, level);
            }
        } else {
            float strainDamage = (float) Math.abs(netForce) * 0.1f;

            for (AnchorPoint anchor : anchors) {
                ChainLink link = anchor.getLink();
                if (link == null || !winchTargetMob.equals(link.getTargetMobId())) continue;

                if (link.tickStrain(strainDamage, chainCount)) {
                    anchor.setLink(null);
                    spawnChainBreakEffects(level, mob.position());
                }
            }

            if (getAttachedChainCount(winchTargetMob) == 0) {
                cancelWinch();
            }
        }
    }

    private void secureMob(Mob mob, ServerLevel level) {
        mob.setNoAi(true);

        for (AnchorPoint anchor : anchors) {
            ChainLink link = anchor.getLink();
            if (link != null && mob.getUUID().equals(link.getTargetMobId())) {
                link.setState(ChainLink.State.SECURED);
            }
        }

        winchTargetMob = null;
        winchProgress = 0.0f;
        recalculateState();
    }

    private void enforceTetherRange(BlockPos thrusterPos, ServerLevel level) {
        for (AnchorPoint anchor : anchors) {
            ChainLink link = anchor.getLink();
            if (link == null) continue;
            if (link.getState() != ChainLink.State.TETHERED) continue;
            if (link.getTargetMobId() == null) continue;

            Entity entity = level.getEntity(link.getTargetMobId());
            if (!(entity instanceof Mob mob)) {
                continue;
            }

            Vec3 anchorWorld = anchor.getWorldPos(thrusterPos);
            constrainMobToAnchor(anchorWorld, link, mob, level);
        }
    }

    private void enforceTetherRangeFromEntity(Vec3 entityPos, ServerLevel level) {
        for (AnchorPoint anchor : anchors) {
            ChainLink link = anchor.getLink();
            if (link == null) continue;
            if (link.getState() != ChainLink.State.TETHERED) continue;
            if (link.getTargetMobId() == null) continue;

            Entity entity = level.getEntity(link.getTargetMobId());
            if (!(entity instanceof Mob mob)) continue;

            Vec3 anchorWorld = anchor.getWorldPos(entityPos);
            constrainMobToAnchor(anchorWorld, link, mob, level);
        }
    }

    private void constrainMobToAnchor(Vec3 anchorWorld, ChainLink link, Mob mob, ServerLevel level) {
        Vec3 mobPos = mob.position();
        double distance = anchorWorld.distanceTo(mobPos);
        double maxLength = link.getMaxLength();

        if (maxLength <= 0) {
            return;
        }
        if (distance <= maxLength) return;

        Vec3 direction = mobPos.subtract(anchorWorld).normalize();
        Vec3 correctedPos = anchorWorld.add(direction.scale(maxLength));
        mob.teleportTo(correctedPos.x, correctedPos.y, correctedPos.z);
        mob.hurtMarked = true;

        mob.getNavigation().stop();

        Vec3 vel = mob.getDeltaMovement();
        Vec3 toAnchor = anchorWorld.subtract(correctedPos).normalize();
        double dot = vel.dot(toAnchor);

        if (dot < 0) {

            Vec3 awayComponent = toAnchor.scale(dot);
            mob.setDeltaMovement(vel.subtract(awayComponent));

            float impactForce = (float) Math.abs(dot);
            if (impactForce > 0.05f) {
                level.playSound(null, mob.blockPosition(),
                        SoundEvents.CHAIN_STEP, SoundSource.BLOCKS,
                        Math.min(impactForce * 3, 1.0f),
                        0.8f + level.random.nextFloat() * 0.4f);
            }
        }
    }

    private void cleanupDeadLinks(BlockPos thrusterPos, ServerLevel level) {
        boolean changed = false;
        for (AnchorPoint anchor : anchors) {
            ChainLink link = anchor.getLink();
            if (link == null || link.getTargetMobId() == null) continue;

            Entity entity = level.getEntity(link.getTargetMobId());
            if (entity == null || !entity.isAlive()) {
                if (link.getState() == ChainLink.State.SECURED && entity instanceof Mob mob) {
                    mob.setNoAi(false);
                }
                anchor.setLink(null);
                spawnChainBreakEffects(level, Vec3.atCenterOf(thrusterPos));
                changed = true;
            }
        }
        if (changed) recalculateState();
    }

    private void cleanupDeadLinksNoPos(ServerLevel level) {
        boolean changed = false;
        for (AnchorPoint anchor : anchors) {
            ChainLink link = anchor.getLink();
            if (link == null || link.getTargetMobId() == null) continue;

            Entity entity = level.getEntity(link.getTargetMobId());
            if (entity == null || !entity.isAlive()) {
                if (link.getState() == ChainLink.State.SECURED && entity instanceof Mob mob) {
                    mob.setNoAi(false);
                    mob.stopRiding();
                }
                anchor.setLink(null);
                changed = true;
            }
        }
        if (changed) recalculateState();
    }

    @Nullable
    public UUID findNearestTetheredMob(BlockPos thrusterPos, ServerLevel level) {
        UUID nearest = null;
        double nearestDist = Double.MAX_VALUE;
        Vec3 basePos = Vec3.atCenterOf(thrusterPos);

        Set<UUID> seen = new HashSet<>();
        for (AnchorPoint anchor : anchors) {
            ChainLink link = anchor.getLink();
            if (link == null || link.getState() != ChainLink.State.TETHERED || link.getTargetMobId() == null) continue;
            if (!seen.add(link.getTargetMobId())) continue;

            Entity entity = level.getEntity(link.getTargetMobId());
            if (entity == null) continue;

            double dist = entity.position().distanceTo(basePos);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = link.getTargetMobId();
            }
        }
        return nearest;
    }

    @Nullable
    public AnchorPoint findAnchorForLink(ChainLink link) {
        for (AnchorPoint anchor : anchors) {
            if (anchor.getLink() != null && anchor.getLink().getId().equals(link.getId())) {
                return anchor;
            }
        }
        return null;
    }

    public Set<UUID> getSecuredMobIds() {
        Set<UUID> ids = new HashSet<>();
        for (AnchorPoint anchor : anchors) {
            ChainLink link = anchor.getLink();
            if (link != null && link.getState() == ChainLink.State.SECURED && link.getTargetMobId() != null) {
                ids.add(link.getTargetMobId());
            }
        }
        return ids;
    }

    public Set<UUID> getTetheredMobIds() {
        Set<UUID> ids = new HashSet<>();
        for (AnchorPoint anchor : anchors) {
            ChainLink link = anchor.getLink();
            if (link != null && link.getState() == ChainLink.State.TETHERED && link.getTargetMobId() != null) {
                ids.add(link.getTargetMobId());
            }
        }
        return ids;
    }

    public void releaseAll(ServerLevel level) {
        for (AnchorPoint anchor : anchors) {
            ChainLink link = anchor.getLink();
            if (link != null && link.getTargetMobId() != null && link.getState() == ChainLink.State.SECURED) {
                Entity entity = level.getEntity(link.getTargetMobId());
                if (entity instanceof Mob mob) {
                    mob.setNoAi(false);
                    mob.stopRiding();
                }
            }
            anchor.setLink(null);
        }
        winchTargetMob = null;
        winchProgress = 0;
        recalculateState();
    }

    public void breakUnsecuredChains(BlockPos thrusterPos, ServerLevel level) {
        for (AnchorPoint anchor : anchors) {
            ChainLink link = anchor.getLink();
            if (link == null) continue;
            if (link.getState() == ChainLink.State.SECURED) continue;

            if (link.getTargetMobId() != null) {
                Entity entity = level.getEntity(link.getTargetMobId());
                if (entity != null) {
                    spawnChainBreakEffects(level, entity.position());
                }
            } else {
                spawnChainBreakEffects(level, Vec3.atCenterOf(thrusterPos));
            }
            anchor.setLink(null);
        }
        clearActiveLinker();
        recalculateState();
    }

    public void breakDanglingChains(BlockPos thrusterPos, ServerLevel level) {
        for (AnchorPoint anchor : anchors) {
            ChainLink link = anchor.getLink();
            if (link == null) continue;
            if (link.getState() == ChainLink.State.SECURED) continue;
            if (link.getState() == ChainLink.State.TETHERED) continue;

            spawnChainBreakEffects(level, Vec3.atCenterOf(thrusterPos));
            anchor.setLink(null);
        }
        clearActiveLinker();
        recalculateState();
    }

    public void setLaunched() {
        state = ChainingState.LAUNCHED;
    }

    public void recalculateState() {
        if (state == ChainingState.LAUNCHED) return;
        if (state == ChainingState.WINCHING && winchTargetMob != null) return;

        if (anchors.isEmpty()) {
            state = ChainingState.EMPTY;
            return;
        }

        boolean hasSecured = false;
        boolean hasTethered = false;

        for (AnchorPoint anchor : anchors) {
            ChainLink link = anchor.getLink();
            if (link == null) continue;
            if (link.getState() == ChainLink.State.SECURED) hasSecured = true;
            if (link.getState() == ChainLink.State.TETHERED) hasTethered = true;
        }

        if (hasSecured) {
            state = ChainingState.SECURED;
        } else if (hasTethered) {
            state = ChainingState.TETHERING;
        } else {
            state = ChainingState.ANCHORED;
        }
    }

    private void spawnChainBreakEffects(ServerLevel level, Vec3 pos) {
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.CRIT,
                pos.x, pos.y + 0.5, pos.z,
                10, 0.3, 0.3, 0.3, 0.05);
        ItemEntity drop = new ItemEntity(level, pos.x, pos.y, pos.z, new ItemStack(Items.CHAIN));
        level.addFreshEntity(drop);
    }

    public void populateEntityIds(ServerLevel level) {
        for (AnchorPoint anchor : anchors) {
            ChainLink link = anchor.getLink();
            if (link == null) continue;
            if (link.getTargetMobId() != null) {
                Entity entity = level.getEntity(link.getTargetMobId());
                link.setTargetEntityId(entity != null ? entity.getId() : -1);
            } else {
                link.setTargetEntityId(-1);
            }
        }

        if (activeLinkerPlayer != null) {
            net.minecraft.server.level.ServerPlayer player = level.getServer().getPlayerList().getPlayer(activeLinkerPlayer);
            activeLinkerEntityId = player != null ? player.getId() : -1;
        } else {
            activeLinkerEntityId = -1;
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag anchorList = new ListTag();
        for (AnchorPoint anchor : anchors) {
            anchorList.add(anchor.save());
        }
        tag.put("Anchors", anchorList);
        tag.putString("State", state.name());
        if (winchTargetMob != null) tag.putUUID("WinchTargetMob", winchTargetMob);
        tag.putFloat("WinchProgress", winchProgress);
        if (activeLinkerPlayer != null) tag.putUUID("ActiveLinkerPlayer", activeLinkerPlayer);
        if (activeLinkerChainId != null) tag.putUUID("ActiveLinkerChainId", activeLinkerChainId);
        tag.putInt("ActiveLinkerEntityId", activeLinkerEntityId);
        return tag;
    }

    public void load(CompoundTag tag) {
        anchors.clear();
        breakAttemptTimers.clear();

        ListTag anchorList = tag.getList("Anchors", Tag.TAG_COMPOUND);
        for (int i = 0; i < anchorList.size(); i++) {
            anchors.add(AnchorPoint.load(anchorList.getCompound(i)));
        }

        try {
            state = ChainingState.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException e) {
            state = ChainingState.EMPTY;
        }

        winchTargetMob = tag.hasUUID("WinchTargetMob") ? tag.getUUID("WinchTargetMob") : null;
        winchProgress = tag.getFloat("WinchProgress");
        activeLinkerPlayer = tag.hasUUID("ActiveLinkerPlayer") ? tag.getUUID("ActiveLinkerPlayer") : null;
        activeLinkerChainId = tag.hasUUID("ActiveLinkerChainId") ? tag.getUUID("ActiveLinkerChainId") : null;
        activeLinkerEntityId = tag.contains("ActiveLinkerEntityId") ? tag.getInt("ActiveLinkerEntityId") : -1;
    }
}
