package com.forge.engine;

/** Procedural sound design: sweeping oscillators + noise + decay envelope -> PCM. No asset files. */
public final class Synth {
    public static final int SINE = 0, SQUARE = 1, SAW = 2, TRI = 3, NOISE = 4;

    /**
     * @param f0    start frequency Hz (>0)
     * @param f1    end frequency Hz (>0), exponential sweep
     * @param noise 0..1 noise mixed into the oscillator
     * @param decay envelope exponent (higher = snappier)
     */
    public static short[] make(float f0, float f1, float dur, int wave, float noise, float vol, float decay) {
        int n = (int) (Audio.RATE * dur);
        short[] out = new short[n];
        Rng rng = new Rng(0x1234567 + n);
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            double f = f0 * Math.pow(f1 / f0, t);
            phase += f / Audio.RATE;
            float p = (float) (phase - Math.floor(phase));
            float s;
            switch (wave) {
                case SINE: s = (float) Math.sin(p * Math.PI * 2); break;
                case SQUARE: s = p < 0.5f ? 1f : -1f; break;
                case SAW: s = p * 2f - 1f; break;
                case TRI: s = Math.abs(p * 4f - 2f) - 1f; break;
                default: s = rng.next() * 2f - 1f; break;
            }
            s = s * (1f - noise) + (rng.next() * 2f - 1f) * noise;
            float env = (float) Math.pow(1f - t, decay) * Math.min(1f, i / 60f);
            s *= env * vol;
            if (s > 1f) s = 1f;
            if (s < -1f) s = -1f;
            out[i] = (short) (s * 32767f);
        }
        return out;
    }

    private Synth() {}
}
