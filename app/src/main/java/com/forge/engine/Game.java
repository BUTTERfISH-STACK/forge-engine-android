package com.forge.engine;

/** Subclass this. The engine calls init() once, then update()/render() every frame on the GL thread. */
public abstract class Game {
    protected Engine e;

    /** Clear color r,g,b. */
    public float[] bg = {0.015f, 0.02f, 0.045f};

    final void attach(Engine engine) {
        e = engine;
    }

    /** Create sounds, reset state, set input listener. */
    public abstract void init();

    /** dt is already scaled by hitstop/slow-mo. Use e.realDt for unscaled time. */
    public abstract void update(float dt);

    /** World camera is already set from e.camX/e.camY/e.camZoom (+shake). Call b.screen() for HUD. */
    public abstract void render(Batch b);

    /** Extra lines for the debug overlay (newline separated). */
    public String debug() {
        return "";
    }

    /** App went to background: persist things. */
    public void onPause() {}
}
