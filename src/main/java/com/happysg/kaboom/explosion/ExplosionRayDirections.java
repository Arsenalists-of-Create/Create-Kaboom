package com.happysg.kaboom.explosion;

final class ExplosionRayDirections {
    static final float[] X;
    static final float[] Y;
    static final float[] Z;

    static {
        final int grid = 16;
        int count = grid * grid * grid - (grid - 2) * (grid - 2) * (grid - 2);
        X = new float[count];
        Y = new float[count];
        Z = new float[count];

        int index = 0;
        for (int gx = 0; gx < grid; gx++) {
            for (int gy = 0; gy < grid; gy++) {
                for (int gz = 0; gz < grid; gz++) {
                    if (gx != 0 && gx != grid - 1 && gy != 0 && gy != grid - 1 && gz != 0 && gz != grid - 1) {
                        continue;
                    }

                    double x = gx / (double) (grid - 1) * 2.0 - 1.0;
                    double y = gy / (double) (grid - 1) * 2.0 - 1.0;
                    double z = gz / (double) (grid - 1) * 2.0 - 1.0;
                    double length = Math.sqrt(x * x + y * y + z * z);
                    if (length <= 1.0E-8) {
                        continue;
                    }

                    X[index] = (float) (x / length);
                    Y[index] = (float) (y / length);
                    Z[index] = (float) (z / length);
                    index++;
                }
            }
        }

        if (index != count) {
            throw new IllegalStateException("Explosion ray direction count mismatch");
        }
    }

    private ExplosionRayDirections() {
    }
}
