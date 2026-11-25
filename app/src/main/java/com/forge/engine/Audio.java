package com.forge.engine;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import java.util.ArrayList;

/** Low-latency software mixer: 16 voices, pitch-shiftable, streamed to one AudioTrack. */
public final class Audio implements Runnable {
    public static final int RATE = 22050;
    private static final int VOICES = 16;

    private final ArrayList<short[]> bank = new ArrayList<>();
    private final short[][] vd = new short[VOICES][];
    private final float[] vp = new float[VOICES], vs = new float[VOICES], vv = new float[VOICES];
    private int steal;
    private AudioTrack track;
    private Thread thread;
    private volatile boolean running;

    public float master = 1f;

    /** Register PCM (e.g. from Synth.make). Returns a sound id. */
    public int add(short[] pcm) {
        bank.add(pcm);
        return bank.size() - 1;
    }

    public synchronized void play(int id, float pitch, float vol) {
        int slot = -1;
        for (int i = 0; i < VOICES; i++) {
            if (vd[i] == null) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            slot = steal;
            steal = (steal + 1) % VOICES;
        }
        vd[slot] = bank.get(id);
        vp[slot] = 0f;
        vs[slot] = pitch;
        vv[slot] = vol;
    }

    public void start() {
        if (running) return;
        int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        track = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(Math.max(min, 2048))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build();
        track.play();
        running = true;
        thread = new Thread(this, "forge-audio");
        thread.start();
    }

    public void stop() {
        running = false;
        try {
            if (thread != null) thread.join(500);
        } catch (InterruptedException ignored) {
        }
        if (track != null) {
            track.stop();
            track.release();
            track = null;
        }
        thread = null;
    }

    @Override
    public void run() {
        short[] buf = new short[256];
        AudioTrack t = track;
        while (running) {
            mix(buf);
            t.write(buf, 0, buf.length);
        }
    }

    private synchronized void mix(short[] out) {
        for (int i = 0; i < out.length; i++) {
            float s = 0f;
            for (int v = 0; v < VOICES; v++) {
                short[] d = vd[v];
                if (d == null) continue;
                int ip = (int) vp[v];
                if (ip >= d.length - 1) {
                    vd[v] = null;
                    continue;
                }
                float fr = vp[v] - ip;
                s += (d[ip] + (d[ip + 1] - d[ip]) * fr) * vv[v];
                vp[v] += vs[v];
            }
            s *= master;
            if (s > 32767f) s = 32767f;
            if (s < -32767f) s = -32767f;
            out[i] = (short) s;
        }
    }
}
