package com.forge.engine;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.VibrationEffect;
import android.os.Vibrator;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** The loop. Owns the renderer, input, audio, juice and save; drives your Game. */
public final class Engine implements GLSurfaceView.Renderer {
    public static final float VH = 720f;

    public final Context ctx;
    public final Batch batch = new Batch();
    public final Input input = new Input();
    public final Audio audio = new Audio();
    public final Juice juice = new Juice();
    public final Save save;
    public final Game game;

    public float camX, camY, camZoom = 1f;
    public float realDt = 0.016f, fps = 60f;
    public float vw = 1280f, vh = VH;
    public boolean debug;
    /** >0 = deterministic fixed timestep (tests, replays). 0 = real time. */
    public float fixedDt = 0f;

    private long last;
    private boolean inited;
    private final Vibrator vib;

    public Engine(Context c, Game g) {
        ctx = c;
        game = g;
        save = new Save(c);
        vib = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        g.attach(this);
    }

    public void buzz(int ms) {
        if (vib != null && vib.hasVibrator()) {
            vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        batch.init();
        last = System.nanoTime();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
        vh = VH;
        vw = VH * w / (float) h;
        input.scale = VH / h;
        input.vw = vw;
        input.vh = vh;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        realDt = fixedDt > 0f ? fixedDt : Math.min((now - last) * 1e-9f, 0.05f);
        last = now;
        if (realDt > 0f) fps += (1f / realDt - fps) * 0.05f;

        if (!inited) {
            inited = true;
            game.init();
        }
        if (input.threeFingerTap) {
            input.threeFingerTap = false;
            debug = !debug;
        }
        input.update();
        juice.update(realDt);
        game.update(realDt * juice.timeScale());

        GLES20.glClearColor(game.bg[0], game.bg[1], game.bg[2], 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        batch.begin(vw, vh);
        batch.camera(camX + juice.offX, camY + juice.offY, camZoom * (1f + juice.punch));
        game.render(batch);
        batch.screen();
        input.draw(batch);
        if (debug) {
            int c = Batch.col(0.6f, 1f, 0.7f);
            String s = "FPS " + (int) fps + "\nDRAWS " + batch.drawCalls + "\n" + game.debug();
            float y = 10f;
            for (String line : s.split("\n")) {
                batch.text(line, 12f, y, 2f, c, Batch.LEFT);
                y += 20f;
            }
        }
        batch.end();
    }

    public void onPause() {
        game.onPause();
    }
}
