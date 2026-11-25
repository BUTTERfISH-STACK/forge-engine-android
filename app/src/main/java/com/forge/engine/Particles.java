package com.forge.engine;

/** Struct-of-arrays particle system + shockwave rings. One instance = thousands of particles, no objects. */
public final class Particles {
    private static final int MAX = 3000, RINGS = 48;

    private final float[] x = new float[MAX], y = new float[MAX], vx = new float[MAX], vy = new float[MAX];
    private final float[] life = new float[MAX], maxLife = new float[MAX], size = new float[MAX];
    private final int[] col = new int[MAX];
    private int n;

    private final float[] rx = new float[RINGS], ry = new float[RINGS], rt = new float[RINGS];
    private final float[] rdur = new float[RINGS], rmax = new float[RINGS], rw = new float[RINGS];
    private final int[] rcol = new int[RINGS];
    private int rn;

    private final Rng rng = new Rng(777);
    public float drag = 2.5f;

    public int count() {
        return n;
    }

    public void clear() {
        n = 0;
        rn = 0;
    }

    public void burst(float px, float py, int count, float speed, int color, float lifeSec,
                      float sz, float spread, float dir) {
        for (int k = 0; k < count; k++) {
            int i;
            if (n < MAX) i = n++;
            else i = rng.nextInt(MAX);
            float a = dir + rng.range(-spread * 0.5f, spread * 0.5f);
            float s = speed * rng.range(0.25f, 1f);
            x[i] = px;
            y[i] = py;
            vx[i] = (float) Math.cos(a) * s;
            vy[i] = (float) Math.sin(a) * s;
            float l = lifeSec * rng.range(0.6f, 1f);
            life[i] = l;
            maxLife[i] = l;
            size[i] = sz * rng.range(0.6f, 1.2f);
            col[i] = color;
        }
    }

    public void ring(float px, float py, float radius, int color, float dur, float width) {
        int i;
        if (rn < RINGS) i = rn++;
        else i = rng.nextInt(RINGS);
        rx[i] = px; ry[i] = py; rt[i] = 0f; rdur[i] = dur; rmax[i] = radius; rw[i] = width; rcol[i] = color;
    }

    public void update(float dt) {
        float k = (float) Math.exp(-drag * dt);
        for (int i = n - 1; i >= 0; i--) {
            life[i] -= dt;
            if (life[i] <= 0f) {
                n--;
                if (i != n) {
                    x[i] = x[n]; y[i] = y[n]; vx[i] = vx[n]; vy[i] = vy[n];
                    life[i] = life[n]; maxLife[i] = maxLife[n]; size[i] = size[n]; col[i] = col[n];
                }
            } else {
                vx[i] *= k;
                vy[i] *= k;
                x[i] += vx[i] * dt;
                y[i] += vy[i] * dt;
            }
        }
        for (int i = rn - 1; i >= 0; i--) {
            rt[i] += dt;
            if (rt[i] >= rdur[i]) {
                rn--;
                if (i != rn) {
                    rx[i] = rx[rn]; ry[i] = ry[rn]; rt[i] = rt[rn]; rdur[i] = rdur[rn];
                    rmax[i] = rmax[rn]; rw[i] = rw[rn]; rcol[i] = rcol[rn];
                }
            }
        }
    }

    /** Draws additively (neon glow). Call inside world camera. */
    public void draw(Batch b) {
        b.additive(true);
        for (int i = 0; i < n; i++) {
            float t = life[i] / maxLife[i];
            b.line(x[i], y[i], x[i] - vx[i] * 0.035f, y[i] - vy[i] * 0.035f,
                size[i] * (0.4f + 0.6f * t), Batch.withAlpha(col[i], t));
        }
        for (int i = 0; i < rn; i++) {
            float t = rt[i] / rdur[i];
            float r = rmax[i] * (1f - (1f - t) * (1f - t) * (1f - t));
            b.ring(rx[i], ry[i], r, rw[i] * (1f - t) + 1f, Batch.withAlpha(rcol[i], 1f - t));
        }
        b.additive(false);
    }
}
