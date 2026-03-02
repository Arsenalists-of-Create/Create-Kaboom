package com.happysg.kaboom.block.missiles.chaining;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

public class ChainLink {

    public enum State {
        DANGLING,
        TETHERED,
        SECURED
    }

    private final UUID id;
    private final UUID anchorId;
    @Nullable
    private UUID targetMobId;
    private State state;
    private float hp;
    private float maxLength;

    private int targetEntityId = -1;

    private static final float DEFAULT_HP = 100f;

    public ChainLink(UUID id, UUID anchorId) {
        this.id = id;
        this.anchorId = anchorId;
        this.state = State.DANGLING;
        this.hp = DEFAULT_HP;
        this.maxLength = 0f;
    }

    public ChainLink(UUID anchorId) {
        this(UUID.randomUUID(), anchorId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAnchorId() {
        return anchorId;
    }

    @Nullable
    public UUID getTargetMobId() {
        return targetMobId;
    }

    public void setTargetMobId(@Nullable UUID targetMobId) {
        this.targetMobId = targetMobId;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public float getHp() {
        return hp;
    }

    public void setHp(float hp) {
        this.hp = hp;
    }

    public float getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(float maxLength) {
        this.maxLength = maxLength;
    }

    public int getTargetEntityId() {
        return targetEntityId;
    }

    public void setTargetEntityId(int targetEntityId) {
        this.targetEntityId = targetEntityId;
    }

    /**
     * Reduces HP by breakForce / totalChainsOnMob.
     * @return true if the chain broke (HP <= 0)
     */
    public boolean tickStrain(float breakForce, int totalChainsOnMob) {
        if (totalChainsOnMob <= 0) totalChainsOnMob = 1;
        hp -= breakForce / totalChainsOnMob;
        return hp <= 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("AnchorId", anchorId);
        if (targetMobId != null) {
            tag.putUUID("TargetMobId", targetMobId);
        }
        tag.putString("State", state.name());
        tag.putFloat("Hp", hp);
        tag.putFloat("MaxLength", maxLength);
        if (targetEntityId != -1) {
            tag.putInt("TargetEntityId", targetEntityId);
        }
        return tag;
    }

    public static ChainLink load(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        UUID anchorId = tag.getUUID("AnchorId");
        ChainLink link = new ChainLink(id, anchorId);
        if (tag.hasUUID("TargetMobId")) {
            link.targetMobId = tag.getUUID("TargetMobId");
        }
        link.state = State.valueOf(tag.getString("State"));
        link.hp = tag.getFloat("Hp");
        link.maxLength = tag.getFloat("MaxLength");
        link.targetEntityId = tag.contains("TargetEntityId") ? tag.getInt("TargetEntityId") : -1;
        return link;
    }
}
