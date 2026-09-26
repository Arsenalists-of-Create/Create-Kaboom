package com.happysg.kaboom.explosion;

enum ExplosionMaterial {
    AIR(false, false, false),
    PLANT(false, true, false),
    FLUID(false, true, false),
    LOOSE(true, true, false),
    BRITTLE(true, true, true),
    SOLID(true, true, true),
    IMMUNE(true, false, true),
    BOUNDARY(true, false, true);

    private final boolean solid;
    private final boolean destructible;
    private final boolean structural;

    ExplosionMaterial(boolean solid, boolean destructible, boolean structural) {
        this.solid = solid;
        this.destructible = destructible;
        this.structural = structural;
    }

    boolean isSolid() {
        return solid;
    }

    boolean isDestructible() {
        return destructible;
    }

    boolean isStructural() {
        return structural;
    }

    boolean isOpenSpace() {
        return this == AIR || this == PLANT || this == FLUID;
    }

    boolean isPermanentSupport() {
        return this == IMMUNE || this == BOUNDARY;
    }
}
