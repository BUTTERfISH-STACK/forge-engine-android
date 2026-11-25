package com.forge.engine;

import android.view.MotionEvent;

/**
 * Multitouch: floating twin sticks (left half = move, right half = aim) + gestures.
 * Read moveX/moveY and aimX/aimY (length 0..1). Set `listener` for tap/double-tap/swipe/long-press.
 * Three fingers down at once toggles the debug overlay.
 */
public final class Input {
    public interface Listener {
        default void onTap(float x, float y) {}
        default void onDoubleTap(float x, float y) {}
        default void onSwipe(float dx, float dy, float x, float y) {}
        default void onLongPress(float x, float y) {}
    }

    public static final float STICK_R = 120f;
    private static final long TAP_MS = 220, DOUBLE_MS = 300, SWIPE_MS = 300, LONG_MS = 550;
    private static final float TAP_DIST = 24f, SWIPE_DIST = 100f;

    public float moveX, moveY, aimX, aimY;
    public boolean moving, aiming, showSticks = true;
    public Listener listener;
    public boolean threeFingerTap;

    float scale = 1f;   // device px -> logical units (set by Engine)
    float vw = 1280f, vh = 720f;

    private static final class Finger {
        boolean active, longDone;
        float sx, sy, x, y;
        long t0;
        int role; // 0 none, 1 move, 2 aim
    }

    private final Finger[] f = new Finger[16];
    private int moveId = -1, aimId = -1, down;
    private float mox, moy, aox, aoy, ltx, lty;
    private long lastTap = -10000;

    public Input() {
        for (int i = 0; i < f.length; i++) f[i] = new Finger();
    }

    /** Called on the GL thread with raw event data copied from the UI thread. */
    void handle(int act, int ai, int[] ids, float[] xs, float[] ys) {
        switch (act) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                down(ids[ai], xs[ai] * scale, ys[ai] * scale);
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < ids.length; i++) drag(ids[i], xs[i] * scale, ys[i] * scale);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                up(ids[ai], xs[ai] * scale, ys[ai] * scale);
                break;
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                break;
            default:
                break;
        }
    }

    private void down(int id, float x, float y) {
        if (id < 0 || id >= f.length) return;
        Finger p = f[id];
        p.active = true;
        p.longDone = false;
        p.sx = x; p.sy = y; p.x = x; p.y = y;
        p.t0 = System.currentTimeMillis();
        p.role = 0;
        down++;
        if (down >= 3) threeFingerTap = true;
        if (x < vw * 0.5f && moveId == -1) {
            p.role = 1; moveId = id; mox = x; moy = y; moving = true; moveX = 0; moveY = 0;
        } else if (x >= vw * 0.5f && aimId == -1) {
            p.role = 2; aimId = id; aox = x; aoy = y; aiming = true; aimX = 0; aimY = 0;
        }
    }

    private void drag(int id, float x, float y) {
        if (id < 0 || id >= f.length || !f[id].active) return;
        Finger p = f[id];
        p.x = x;
        p.y = y;
        if (p.role == 1) {
            float dx = x - mox, dy = y - moy;
            float l = (float) Math.sqrt(dx * dx + dy * dy);
            if (l > STICK_R) {
                mox = x - dx / l * STICK_R;
                moy = y - dy / l * STICK_R;
                dx = x - mox;
                dy = y - moy;
            }
            moveX = dx / STICK_R;
            moveY = dy / STICK_R;
        } else if (p.role == 2) {
            float dx = x - aox, dy = y - aoy;
            float l = (float) Math.sqrt(dx * dx + dy * dy);
            if (l > STICK_R) {
                aox = x - dx / l * STICK_R;
                aoy = y - dy / l * STICK_R;
                dx = x - aox;
                dy = y - aoy;
            }
            aimX = dx / STICK_R;
            aimY = dy / STICK_R;
        }
    }

    private void up(int id, float x, float y) {
        if (id < 0 || id >= f.length || !f[id].active) return;
        Finger p = f[id];
        p.active = false;
        down = Math.max(0, down - 1);
        if (id == moveId) { moveId = -1; moveX = 0; moveY = 0; moving = false; }
        if (id == aimId) { aimId = -1; aimX = 0; aimY = 0; aiming = false; }
        long now = System.currentTimeMillis();
        long dur = now - p.t0;
        float dx = x - p.sx, dy = y - p.sy;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (!p.longDone && listener != null) {
            if (d < TAP_DIST && dur < TAP_MS) {
                listener.onTap(x, y);
                float ddx = x - ltx, ddy = y - lty;
                if (now - lastTap < DOUBLE_MS && ddx * ddx + ddy * ddy < 90f * 90f) {
                    listener.onDoubleTap(x, y);
                    lastTap = -10000;
                } else {
                    lastTap = now;
                    ltx = x;
                    lty = y;
                }
            } else if (d > SWIPE_DIST && dur < SWIPE_MS) {
                listener.onSwipe(dx / d, dy / d, p.sx, p.sy);
            }
        }
    }

    private void releaseAll() {
        for (Finger p : f) p.active = false;
        down = 0;
        moveId = aimId = -1;
        moving = aiming = false;
        moveX = moveY = aimX = aimY = 0f;
    }

    /** Per-frame (GL thread): long-press detection. */
    void update() {
        if (listener == null) return;
        long now = System.currentTimeMillis();
        for (Finger p : f) {
            if (p.active && !p.longDone && now - p.t0 > LONG_MS) {
                float dx = p.x - p.sx, dy = p.y - p.sy;
                if (dx * dx + dy * dy < TAP_DIST * TAP_DIST) {
                    p.longDone = true;
                    listener.onLongPress(p.x, p.y);
                }
            }
        }
    }

    void draw(Batch b) {
        if (!showSticks) return;
        float mx = moving ? mox : vw * 0.16f, my = moving ? moy : vh * 0.76f;
        float ax = aiming ? aox : vw * 0.84f, ay = aiming ? aoy : vh * 0.76f;
        stick(b, mx, my, moving ? moveX : 0f, moving ? moveY : 0f, Batch.col(0.2f, 0.9f, 1f), moving);
        stick(b, ax, ay, aiming ? aimX : 0f, aiming ? aimY : 0f, Batch.col(1f, 0.3f, 0.5f), aiming);
    }

    private static void stick(Batch b, float x, float y, float vx, float vy, int c, boolean active) {
        float a = active ? 0.55f : 0.12f;
        b.ring(x, y, STICK_R, 3f, Batch.withAlpha(c, a * 0.7f));
        b.circle(x + vx * STICK_R, y + vy * STICK_R, 36f, Batch.withAlpha(c, a));
    }
}
