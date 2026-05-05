package com.happysg.kaboom.block.missiles.chaining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public class AnchorPoint {

    private final UUID id;
    private final BlockPos blockOffset;
    private final Direction face;
    @Nullable
    private ChainLink link;

    public AnchorPoint(UUID id, BlockPos blockOffset, Direction face) {
        this.id = id;
        this.blockOffset = blockOffset;
        this.face = face;
    }

    public AnchorPoint(BlockPos blockOffset, Direction face) {
        this(UUID.randomUUID(), blockOffset, face);
    }

    public UUID getId() {
        return id;
    }

    public BlockPos getBlockOffset() {
        return blockOffset;
    }

    public Direction getFace() {
        return face;
    }

    public Vec3 getWorldPos(BlockPos thrusterPos) {
        BlockPos worldBlock = thrusterPos.offset(blockOffset);

        return Vec3.atCenterOf(worldBlock).add(
                face.getStepX() * 0.5,
                face.getStepY() * 0.5,
                face.getStepZ() * 0.5
        );
    }

    public Vec3 getWorldPos(Vec3 entityPos) {
        return entityPos.add(toContraptionLocalVec3());
    }

    public Vec3 toContraptionLocalVec3() {
        return Vec3.atCenterOf(blockOffset).add(
                face.getStepX() * 0.5,
                face.getStepY() * 0.5,
                face.getStepZ() * 0.5
        );
    }

    @Nullable
    public ChainLink getLink() {
        return link;
    }

    public void setLink(@Nullable ChainLink link) {
        this.link = link;
    }

    public boolean hasLink() {
        return link != null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putLong("BlockOffset", blockOffset.asLong());
        tag.putString("Face", face.name());
        if (link != null) {
            tag.put("Link", link.save());
        }
        return tag;
    }

    public static AnchorPoint load(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        BlockPos offset = BlockPos.of(tag.getLong("BlockOffset"));
        Direction face = Direction.valueOf(tag.getString("Face"));
        AnchorPoint anchor = new AnchorPoint(id, offset, face);
        if (tag.contains("Link")) {
            anchor.link = ChainLink.load(tag.getCompound("Link"));
        }
        return anchor;
    }
}
