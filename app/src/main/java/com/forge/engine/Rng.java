package com.forge.engine;

/** Tiny allocation-free xorshift RNG. */
public final class Rng {
    private int s;

    public Rng(int seed) {
        s = seed == 0 ? 0x9E3779B9 : seed;
    }

    public int nextInt() {
        s ^= s << 13;
        s ^= s >>> 17;
        s ^= s << 5;
        return s;
    }

    /** 0..1 */
    public float next() {
        return (nextInt() >>> 8) * (1f / 16777216f);
    }

    public float range(float a, float b) {
        return a + (b - a) * next();
    }

    public int nextInt(int n) {
        return (nextInt() >>> 1) % n;
    }

    public boolean chance(float p) {
        return next() < p;
    }
}
