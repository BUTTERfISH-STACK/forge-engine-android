# FORGE: native Android game engine (no Godot, no Unity)

Pure Java + OpenGL ES 2.0. Zero dependencies. Ships as its own APK.

## Get the APK from your phone
1. Make a GitHub repo, upload the contents of this folder (GitHub mobile site -> Add file -> Upload).
2. Actions tab -> "build-apk" runs on push -> download artifact `forge-swarm-apk` -> install `app-debug.apk`.
(Or open in Android Studio / run `gradle assembleDebug` anywhere with the Android SDK.)

## What's in the engine (app/src/main/java/com/forge/engine)
| File | What it does |
|---|---|
| Engine / GameView / ForgeActivity | GL loop, immersive fullscreen, lifecycle, haptics, `fixedDt` for deterministic replays |
| Batch | Batched renderer: rect, line, circle, ring, arc, tri, pixel-font text, additive glow, world/screen cameras. 720-unit-tall logical screen |
| Input | Multitouch floating twin sticks + tap / double-tap / swipe / long-press. 3 fingers = debug overlay |
| Audio + Synth | 16-voice software mixer, procedural SFX (no asset files) |
| Juice | Screen shake, hitstop, slow-mo, zoom punch |
| Particles | 3000 particles + shockwave rings, struct-of-arrays, zero allocation |
| SpatialHash | Zero-GC grid for hundreds of colliding entities |
| Save, Rng, Font | Persistence, xorshift RNG, built-in 5x7 font |

## Make your own game
```java
public final class MyGame extends Game {
    public void init()           { /* sounds, state, e.input.listener */ }
    public void update(float dt) { /* dt already includes hitstop/slow-mo */ }
    public void render(Batch b)  { b.circle(0, 0, 40, Batch.col(1,0,.4f)); b.screen(); b.text("HI", 20, 20, 4, -1, Batch.LEFT); }
}
```
Return it from `MainActivity.createGame()`.

## Demo: SWARM (com/forge/demo/SwarmGame.java)
Twin-stick survival, up to 380 enemies. Left thumb move, right thumb aim+fire, double-tap left = dash.
