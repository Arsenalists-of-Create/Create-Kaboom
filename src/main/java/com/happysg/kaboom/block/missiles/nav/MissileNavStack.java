package com.happysg.kaboom.block.missiles.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;


public final class MissileNavStack {

    public record NavOut(Vec3 thrustDir, Vec3 aimDir, double throttle, boolean done, String dbg) {}


    public record NavIn(Level level, Vec3 pos, Vec3 vel, BlockPos target, double boostY, double cruiseY) {}

    private interface NavNode {
        NavOut tick(NavIn in);
        boolean isComplete(NavIn in);
    }

    private final Deque<NavNode> stack = new ArrayDeque<>();

    private double boostY;
    private double cruiseY;

    
    public void setBoostAndCruiseHeights(double boostY, double cruiseY, Level level) {
        int maxY = level.getMaxBuildHeight() - 2;
        int minY = level.getMinBuildHeight() + 2;

        this.boostY  = Mth.clamp(boostY,  minY, maxY);
        this.cruiseY = Mth.clamp(cruiseY, minY, maxY);

        
        stack.clear();
        stack.push(new CruiseAtAltitudeToTarget());
        stack.push(new BoostToAltitude());
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    
    public NavOut tick(Level level, Vec3 pos, Vec3 vel, BlockPos target) {
        if (stack.isEmpty() || target == null) {
            Vec3 dir = vel.lengthSqr() > 1e-8 ? vel.normalize() : new Vec3(0, 1, 0);
            return new NavOut(dir, dir, 0.0, true, "nav_empty");
        }

        NavIn in = new NavIn(level, pos, vel, target, boostY, cruiseY);

        NavNode top = stack.peek();
        NavOut out = top.tick(in);

        if (top.isComplete(in)) {
            stack.pop();
        }

        boolean done = stack.isEmpty();
        return new NavOut(out.thrustDir, out.aimDir, out.throttle, done, out.dbg + (done ? "|done" : ""));
    }

    
    
    

    
    private static final class BoostToAltitude implements NavNode {
        private static final double ARRIVE_EPS = 2.0; 

        @Override
        public NavOut tick(NavIn in) {
            Vec3 thrust = new Vec3(0, 1, 0);
            return new NavOut(thrust, thrust, 1.0, false, "boost_to_y");
        }

        @Override
        public boolean isComplete(NavIn in) {
            return in.pos.y >= in.boostY - ARRIVE_EPS;
        }
    }

    
    private static final class CruiseAtAltitudeToTarget implements NavNode {

        
        private static final double MAX_TURN_RAD_PER_TICK = 0.55;
        private static final double KP_Y = 0.025;
        private static final double KD_Y = 0.12;
        private static final double MAX_VY_CMD = 0.8; 

        
        private static final double TERMINAL_START_DIST = 430;

        @Override
        public NavOut tick(NavIn in) {
            Vec3 to = Vec3.atCenterOf(in.target).subtract(in.pos);

            double horizDist = Math.sqrt(to.x * to.x + to.z * to.z);
            Vec3 toHoriz = (horizDist > 1e-6) ? new Vec3(to.x / horizDist, 0, to.z / horizDist) : new Vec3(0, 0, 0);

            
            double errY = in.cruiseY - in.pos.y;
            double vyCmd = Mth.clamp(errY * KP_Y - in.vel.y * KD_Y, -MAX_VY_CMD, MAX_VY_CMD);

            Vec3 desired = toHoriz.add(0, vyCmd, 0);
            if (desired.lengthSqr() < 1e-8) {
                desired = new Vec3(0, 1, 0);
            } else {
                desired = desired.normalize();
            }

            
            Vec3 currentDir = (in.vel.lengthSqr() > 1e-8) ? in.vel.normalize() : desired;
            Vec3 thrustDir = turnTowards(currentDir, desired, MAX_TURN_RAD_PER_TICK);

            
            Vec3 aimDir = desired;

            
            double throttle = 1.0;
            String dbg = String.format(Locale.ROOT, "cruise y=%.1f tgt=%.1f err=%.1f vy=%.2f",
                    in.pos.y, in.cruiseY, (in.cruiseY - in.pos.y), in.vel.y);
            return new NavOut(thrustDir, aimDir, throttle, false, dbg);

        }

        @Override
        public boolean isComplete(NavIn in) {
            Vec3 to = Vec3.atCenterOf(in.target).subtract(in.pos);
            double horizDist = Math.sqrt(to.x * to.x + to.z * to.z);
            
            return horizDist < TERMINAL_START_DIST;
        }

        
        private static Vec3 turnTowards(Vec3 from, Vec3 to, double maxRad) {
            from = from.normalize();
            to = to.normalize();

            double dot = Mth.clamp(from.dot(to), -1.0, 1.0);
            double angle = Math.acos(dot);
            if (angle <= maxRad) return to;

            
            Vec3 perp = from.cross(to).cross(from);
            if (perp.lengthSqr() < 1e-12) {
                
                return to;
            }
            perp = perp.normalize();

            
            Vec3 stepped = from.scale(Math.cos(maxRad)).add(perp.scale(Math.sin(maxRad)));
            return stepped.normalize();
        }
    }
}