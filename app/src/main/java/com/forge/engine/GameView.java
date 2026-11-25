package com.forge.engine;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

public final class GameView extends GLSurfaceView {
    private final Engine engine;

    public GameView(Context c, Engine engine) {
        super(c);
        this.engine = engine;
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(engine);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int act = ev.getActionMasked();
        final int ai = ev.getActionIndex();
        final int n = ev.getPointerCount();
        final int[] ids = new int[n];
        final float[] xs = new float[n];
        final float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            ids[i] = ev.getPointerId(i);
            xs[i] = ev.getX(i);
            ys[i] = ev.getY(i);
        }
        queueEvent(() -> engine.input.handle(act, ai, ids, xs, ys));
        return true;
    }
}
