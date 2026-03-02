package com.happysg.kaboom.block.missiles.chaining.client;

import com.happysg.kaboom.block.missiles.chaining.ChainLink;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class VerletChain {

    public static final float LINK_LENGTH = 0.25f;
    private static final int SUB_STEPS = 4;
    private static final Vec3 GRAVITY_FULL = new Vec3(0, -0.08, 0);
    private static final float VIBRATION_AMPLITUDE = 0.02f;
    private static final double MIN_BEND_ANGLE_COS = Math.cos(Math.toRadians(120));
    private static final float ANGULAR_STIFFNESS = 0.1f;

    private final List<VerletPoint> points = new ArrayList<>();
    private ChainLink.State currentState = ChainLink.State.DANGLING;
    private int tickCount = 0;
    private Vec3 prevTargetPos = null;
    private final ChainCollisionField collisionField = new ChainCollisionField();

    public void initializePoints(Vec3 start, Vec3 end, int pointCount) {
        points.clear();
        double dist = start.distanceTo(end);
        double sagDepth = dist * 0.3;
        for (int i = 0; i < pointCount; i++) {
            float t = pointCount > 1 ? (float) i / (pointCount - 1) : 0f;
            Vec3 pos = start.lerp(end, t);
            double sag = -sagDepth * 4.0 * t * (1.0 - t);
            pos = pos.add(0, sag, 0);
            points.add(new VerletPoint(pos));
        }
        prevTargetPos = end;
    }

    public void updateEndpoints(Vec3 anchorPos, Vec3 targetPos, ChainLink.State linkState, boolean isWinching) {
        if (points.isEmpty()) return;

        this.currentState = linkState;

        // Detect large endpoint jumps → reinitialize
        Vec3 currentFirst = points.get(0).pos;
        Vec3 currentLast = points.get(points.size() - 1).pos;
        double chainLen = (points.size() - 1) * LINK_LENGTH;
        double jumpThreshold = chainLen * 0.5;

        if (currentFirst.distanceTo(anchorPos) > jumpThreshold || currentLast.distanceTo(targetPos) > jumpThreshold) {
            initializePoints(anchorPos, targetPos, points.size());
        }

        // Pin first point to anchor
        VerletPoint first = points.get(0);
        first.pinned = true;
        first.pos = anchorPos;
        first.oldPos = anchorPos;

        VerletPoint last = points.get(points.size() - 1);

        switch (linkState) {
            case TETHERED, SECURED -> {
                last.pinned = true;
                last.pos = targetPos;
                last.oldPos = targetPos;
            }
            case DANGLING -> {
                last.pinned = false;
            }
        }

        // Tension wave: when tethered mob hits max chain length, inject impulse
        if (linkState == ChainLink.State.TETHERED && prevTargetPos != null) {
            Vec3 mobVel = targetPos.subtract(prevTargetPos);
            double mobSpeed = mobVel.length();
            double dist = anchorPos.distanceTo(targetPos);
            if (mobSpeed > 0.05 && dist > chainLen * 0.9) {
                int impulseCount = Math.min(4, points.size() - 1);
                for (int i = points.size() - 2; i >= Math.max(1, points.size() - 1 - impulseCount); i--) {
                    VerletPoint p = points.get(i);
                    if (!p.pinned) {
                        p.oldPos = p.oldPos.subtract(mobVel.scale(0.3));
                    }
                }
            }
        }
        prevTargetPos = targetPos;

        // Winching: remove trailing points as chain shortens
        if (isWinching && points.size() > 2) {
            double endpointDist = anchorPos.distanceTo(targetPos);
            int desiredPoints = Math.max(2, (int) (endpointDist / LINK_LENGTH) + 1);
            while (points.size() > desiredPoints && points.size() > 2) {
                points.remove(points.size() - 2);
            }

            for (int i = 1; i < points.size() - 1; i++) {
                float phase = (float) (tickCount * 0.5 + i * 1.5);
                double vx = Math.sin(phase) * VIBRATION_AMPLITUDE;
                double vy = Math.cos(phase * 1.3) * VIBRATION_AMPLITUDE;
                double vz = Math.sin(phase * 0.7) * VIBRATION_AMPLITUDE;
                VerletPoint p = points.get(i);
                if (!p.pinned) {
                    p.pos = p.pos.add(vx, vy, vz);
                }
            }
        }
    }

    public void tick(Level level) {
        tickCount++;

        // Save render snapshots once before sub-stepping
        for (VerletPoint p : points) {
            p.saveRenderSnapshot();
        }

        // Build SDF collision field once per tick
        collisionField.build(level, points);

        int iterations = Math.max(6, Math.min(20, points.size() / 4));
        Vec3 subGravity = GRAVITY_FULL.scale(1.0 / (SUB_STEPS * SUB_STEPS));

        for (int step = 0; step < SUB_STEPS; step++) {

            // Integrate with fractional gravity
            for (VerletPoint p : points) {
                p.integrate(subGravity);
            }

            // Surface friction (uses onSurface from previous sub-step)
            for (VerletPoint p : points) {
                p.applyFriction();
            }

            // Clear onSurface once before constraint loop
            for (VerletPoint p : points) {
                p.onSurface = false;
            }

            // Constraint solving with SDF collision interleaved
            for (int iter = 0; iter < iterations; iter++) {
                // Alternate direction each iteration
                if (iter % 2 == 0) {
                    for (int i = 0; i < points.size() - 1; i++) {
                        solveConstraintPair(i);
                    }
                } else {
                    for (int i = points.size() - 2; i >= 0; i--) {
                        solveConstraintPair(i);
                    }
                }

                solveTotalLengthConstraint();

                // SDF collision after each constraint pass
                for (VerletPoint p : points) {
                    solveCollisionConstraint(p, collisionField);
                }
            }

            // Angular stiffness after constraints settle
            solveAngularConstraints();
        }
    }

    private void solveCollisionConstraint(VerletPoint p, ChainCollisionField field) {
        if (p.pinned) return;

        ChainCollisionField.CollisionQuery q = field.query(p.pos, VerletPoint.RADIUS);
        if (q.distance() >= VerletPoint.RADIUS) return;

        double penetration = VerletPoint.RADIUS - q.distance();
        p.pos = p.pos.add(q.gradient().scale(penetration + 0.001));
        p.onSurface = true;

        // Apply friction: remove normal velocity component, scale tangential by friction
        Vec3 vel = p.pos.subtract(p.oldPos);
        double normalVel = vel.dot(q.gradient());
        if (normalVel < 0) {
            Vec3 tangential = vel.subtract(q.gradient().scale(normalVel));
            p.oldPos = p.pos.subtract(tangential.scale(VerletPoint.FRICTION));
        }
    }

    private void solveTotalLengthConstraint() {
        if (points.size() < 2) return;

        double restLength = (points.size() - 1) * LINK_LENGTH;
        double actualLength = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            actualLength += points.get(i).pos.distanceTo(points.get(i + 1).pos);
        }

        if (actualLength <= restLength) return;

        double ratio = restLength / actualLength;
        for (int i = 0; i < points.size() - 1; i++) {
            VerletPoint a = points.get(i);
            VerletPoint b = points.get(i + 1);

            if (a.pinned && b.pinned) continue;

            Vec3 diff = b.pos.subtract(a.pos);
            double segLen = diff.length();
            if (segLen < 1e-6) continue;

            double targetLen = segLen * ratio;
            double error = segLen - targetLen;
            Vec3 correction = diff.normalize().scale(error);

            if (a.pinned) {
                b.pos = b.pos.subtract(correction);
            } else if (b.pinned) {
                a.pos = a.pos.add(correction);
            } else {
                Vec3 half = correction.scale(0.5);
                a.pos = a.pos.add(half);
                b.pos = b.pos.subtract(half);
            }
        }
    }

    private void solveConstraintPair(int i) {
        VerletPoint a = points.get(i);
        VerletPoint b = points.get(i + 1);

        Vec3 diff = b.pos.subtract(a.pos);
        double dist = diff.length();
        if (dist < 1e-6) return;

        double error = dist - LINK_LENGTH;
        Vec3 correction = diff.normalize().scale(error);

        if (a.pinned && b.pinned) {
            // Both pinned, no correction
        } else if (a.pinned) {
            b.pos = b.pos.subtract(correction);
        } else if (b.pinned) {
            a.pos = a.pos.add(correction);
        } else {
            Vec3 half = correction.scale(0.5);
            a.pos = a.pos.add(half);
            b.pos = b.pos.subtract(half);
        }
    }

    private void solveAngularConstraints() {
        for (int i = 1; i < points.size() - 1; i++) {
            VerletPoint a = points.get(i - 1);
            VerletPoint b = points.get(i);
            VerletPoint c = points.get(i + 1);

            if (b.pinned) continue;

            Vec3 ab = b.pos.subtract(a.pos);
            Vec3 bc = c.pos.subtract(b.pos);

            double abLen = ab.length();
            double bcLen = bc.length();
            if (abLen < 1e-6 || bcLen < 1e-6) continue;

            double cosAngle = ab.dot(bc) / (abLen * bcLen);

            if (cosAngle > MIN_BEND_ANGLE_COS) {
                Vec3 midpoint = a.pos.add(c.pos).scale(0.5);
                Vec3 correction = midpoint.subtract(b.pos).scale(ANGULAR_STIFFNESS);
                b.pos = b.pos.add(correction);
            }
        }
    }

    public List<VerletPoint> getPoints() {
        return points;
    }

    public ChainLink.State getCurrentState() {
        return currentState;
    }
}
