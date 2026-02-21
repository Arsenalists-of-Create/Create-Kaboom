package com.happysg.kaboom.block.missiles.util;

import com.happysg.kaboom.block.missiles.MissileEntity;
import com.happysg.kaboom.networking.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.network.PacketDistributor;
import rbasamoyai.createbigcannons.CreateBigCannons;
import rbasamoyai.createbigcannons.munitions.ImpactExplosion;
import rbasamoyai.createbigcannons.network.ClientboundPlayBlockHitEffectPacket;
import rbasamoyai.createbigcannons.config.CBCCfgMunitions;

import java.util.*;

public class MissileProjectileContext {
    private final CollisionContext collisionContext;
    private final Set<Entity> hitEntities = new LinkedHashSet<>();
    private final CBCCfgMunitions.GriefState griefState;
    private final Map<BlockPos, Float> queuedExplosions = new HashMap<>();
    private final List<ClientboundPlayBlockHitEffectPacket> effects = new LinkedList<>();

    public MissileProjectileContext(MissileEntity projectile, CBCCfgMunitions.GriefState griefState) {
        this.collisionContext = CollisionContext.of(projectile);
        this.griefState = griefState;
    }

    public CollisionContext collisionContext() { return this.collisionContext; }
    public CBCCfgMunitions.GriefState griefState() { return this.griefState; }

    public boolean hasHitEntity(Entity entity) { return this.hitEntities.contains(entity); }
    public void addEntity(Entity entity) { this.hitEntities.add(entity); }
    public Set<Entity> hitEntities() { return this.hitEntities; }

    public void queueExplosion(BlockPos pos, float power) { this.queuedExplosions.put(pos, power); }
    public Map<BlockPos, Float> getQueuedExplosions() { return this.queuedExplosions; }

    public void addPlayedEffect(ClientboundPlayBlockHitEffectPacket packet) { this.effects.add(packet); }
    public List<ClientboundPlayBlockHitEffectPacket> getPlayedEffects() { return this.effects; }

    /** Apply queued CBC side effects (server-side) and clear buffers. */
    public void apply(MissileEntity missile) {
        Level level = missile.level();
        if (level.isClientSide) {
            // nothing to do; effects are produced server-side and sent to clients
            this.effects.clear();
            this.queuedExplosions.clear();
            return;
        }

        // 1) Send block-hit FX packets (spark, debris, etc.)
        if (level instanceof ServerLevel sl) {
            for (ClientboundPlayBlockHitEffectPacket pkt : effects) {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> missile),
                        pkt
                );
            }
        }

        // 2) Apply queued over-penetration / spall explosions
        for (Map.Entry<BlockPos, Float> e : queuedExplosions.entrySet()) {
            BlockPos pos = e.getKey();
            float power = e.getValue();

            Vec3 p = Vec3.atCenterOf(pos);
            ImpactExplosion explosion = new ImpactExplosion(
                    level,
                    missile,
                    missile.indirectArtilleryFire(false),
                    p.x, p.y, p.z,
                    power,
                    Level.ExplosionInteraction.NONE
            );
            CreateBigCannons.handleCustomExplosion(level, explosion);
        }

        effects.clear();
        queuedExplosions.clear();
    }
}