package com.forge.engine;

/** Game feel: screen shake, hitstop, slow-mo, zoom punch. Driven by real (unscaled) time. */
public final class Juice {
    public float trauma, offX, offY, punch;

    private float realTime, stop, slow, slowScale = 1f, ts = 1f;

    public void update(float real) {
        realTime += real;
        trauma = Math.max(0f, trauma - real * 1.5f);
        punch += (0f - punch) * (1f - (float) Math.exp(-10f * real));
        float s = trauma * trauma;
        offX = s * 30f * noise(realTime * 22f, 0f);
        offY = s * 22f * noise(realTime * 22f, 7.3f);
        if (stop > 0f) {
            stop -= real;
            ts = 0.02f;
            return;
        }
        float target = 1f;
        if (slow > 0f) {
            slow -= real;
            target = slowScale;
        }
        ts += (target - ts) * (1f - (float) Math.exp(-12f * real));
    }

    private static float noise(float t, float o) {
        return ((float) Math.sin(t * 1.7f + o) + 0.6f * (float) Math.sin(t * 2.9f + o * 1.3f)) / 1.6f;
    }

    /** Multiply game dt by this. */
    public float timeScale() {
        return ts;
    }

    public void shake(float amount) {
        trauma = Math.min(1f, trauma + amount);
    }

    public void hitstop(float sec) {
        stop = Math.max(stop, sec);
    }

    public void slowmo(float scale, float sec) {
        slow = sec;
        slowScale = scale;
    }

    public void punch(float amount) {
        punch += amount;
    }

    public void reset() {
        trauma = 0f;
        stop = 0f;
        slow = 0f;
        ts = 1f;
        punch = 0f;
    }
}
