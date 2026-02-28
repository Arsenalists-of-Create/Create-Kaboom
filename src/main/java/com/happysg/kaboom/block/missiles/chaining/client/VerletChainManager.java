package com.happysg.kaboom.block.missiles.chaining.client;

import com.happysg.kaboom.block.missiles.chaining.ChainLink;
import net.minecraft.client.multiplayer.ClientLevel;

import net.minecraft.world.phys.Vec3;

import java.util.*;

public class VerletChainManager {

    private final Map<UUID, VerletChain> chains = new HashMap<>();

    private static final float SLACK_MULTIPLIER = 1.15f;
    private static final float MIN_CHAIN_LENGTH = 4.0f;
    private static final int MIN_POINTS = 12;


    public VerletChain getOrCreateChain(ChainLink link, Vec3 anchorPos, Vec3 targetPos) {
        UUID linkId = link.getId();
        VerletChain chain = chains.get(linkId);

        if (chain != null) {
            double currentRopeLength = (chain.getPoints().size() - 1) * VerletChain.LINK_LENGTH;
            double endpointDist = anchorPos.distanceTo(targetPos);
            if (endpointDist > currentRopeLength * 0.85) {
                chain = null;
                chains.remove(linkId);
            }
        }

        if (chain == null) {
            chain = new VerletChain();
            float maxLength = link.getMaxLength();
            if (maxLength <= 0) {
                maxLength = (float) anchorPos.distanceTo(targetPos);
            }
            float chainLength = Math.max(maxLength * SLACK_MULTIPLIER, MIN_CHAIN_LENGTH);
            int pointCount = Math.max(MIN_POINTS, (int) (chainLength / VerletChain.LINK_LENGTH) + 1);
            chain.initializePoints(anchorPos, targetPos, pointCount);
            chains.put(linkId, chain);
        }
        return chain;
    }


    public VerletChain getOrCreateFixedChain(ChainLink link, Vec3 anchorPos, Vec3 targetPos) {
        UUID linkId = link.getId();
        VerletChain chain = chains.get(linkId);

        if (chain == null) {
            chain = new VerletChain();
            float maxLength = link.getMaxLength();
            if (maxLength <= 0) {
                maxLength = (float) anchorPos.distanceTo(targetPos);
            }
            float chainLength = Math.max(maxLength * SLACK_MULTIPLIER, MIN_CHAIN_LENGTH);
            int pointCount = Math.max(MIN_POINTS, (int) (chainLength / VerletChain.LINK_LENGTH) + 1);
            chain.initializePoints(anchorPos, targetPos, pointCount);
            chains.put(linkId, chain);
        }
        return chain;
    }

    public VerletChain getOrCreateDanglingChain(ChainLink link, Vec3 anchorPos) {
        UUID linkId = link.getId();
        VerletChain chain = chains.get(linkId);
        if (chain == null) {
            chain = new VerletChain();
            Vec3 endPos = anchorPos.add(0, -1, 0);
            float chainLength = Math.max(MIN_CHAIN_LENGTH, 4.0f);
            int pointCount = Math.max(MIN_POINTS, (int) (chainLength / VerletChain.LINK_LENGTH) + 1);
            chain.initializePoints(anchorPos, endPos, pointCount);
            chains.put(linkId, chain);
        }
        return chain;
    }

    public void tick(ClientLevel level) {
        for (VerletChain chain : chains.values()) {
            chain.tick(level);
        }
    }

    public void pruneStaleChains(Set<UUID> activeLinkIds) {
        chains.keySet().removeIf(id -> !activeLinkIds.contains(id));
    }
}
