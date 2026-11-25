package com.forge.demo;

import com.forge.engine.Batch;
import com.forge.engine.Game;
import com.forge.engine.Input;
import com.forge.engine.Particles;
import com.forge.engine.Rng;
import com.forge.engine.SpatialHash;
import com.forge.engine.Synth;

/**
 * SWARM: FORGE stress-test game. Twin-stick survival with hundreds of enemies,
 * all struct-of-arrays + one zero-GC spatial hash.
 * Left thumb = move, right thumb = aim + auto-fire, double-tap left side = dash.
 */
public final class SwarmGame extends Game {
    static final float HX = 1300f, HY = 750f;
    static final float P_SPEED = 340f, P_RADIUS = 16f;
    static final float DASH_SPEED = 1100f, DASH_TIME = 0.16f, DASH_CD = 0.9f;
    static final float FIRE_RATE = 0.085f, B_SPEED = 1100f, B_LIFE = 0.9f;
    static final int MAXE = 380, MAXB = 256, HP_MAX = 5;
    static final float TAU = (float) (Math.PI * 2);

    static final float[] T_RADIUS = {14f, 10f, 26f};
    static final float[] T_SPEED = {130f, 230f, 70f};
    static final float[] T_HP = {1f, 0.6f, 6f};
    static final int[] T_SCORE = {10, 15, 60};
    static final int[] T_COL = {
        Batch.col(1f, 0.25f, 0.45f), Batch.col(1f, 0.8f, 0.2f), Batch.col(0.7f, 0.3f, 1f)};

    static final int C_PLAYER = Batch.col(0.3f, 0.95f, 1f);
    static final int C_RED = Batch.col(1f, 0.3f, 0.3f);
    static final int C_WHITE = Batch.col(1f, 1f, 1f);
    static final int C_GRID = Batch.col(0.12f, 0.2f, 0.4f, 0.55f);
    static final int C_WALL = Batch.col(0.2f, 0.8f, 1f, 0.9f);

    final Particles fx = new Particles();
    final SpatialHash grid = new SpatialHash(-HX - 100f, -HY - 100f, HX * 2 + 200f, HY * 2 + 200f, 64f, MAXE);
    final Rng rng = new Rng(4242);
    final int[] nb = new int[256];
    final int[] bq = new int[128];

    // enemies (SoA)
    final float[] ex = new float[MAXE], ey = new float[MAXE], evx = new float[MAXE], evy = new float[MAXE];
    final float[] ehp = new float[MAXE], eflash = new float[MAXE];
    final int[] etype = new int[MAXE];
    int en;

    // bullets (SoA)
    final float[] bx = new float[MAXB], by = new float[MAXB], bvx = new float[MAXB], bvy = new float[MAXB];
    final float[] bl = new float[MAXB];
    int bn;

    // player
    float px, py, pvx, pvy, ax = 1f, ay = 0f;
    int pHp;
    float pInv, dashT, dashCd, dashX, dashY, fireT;

    // state
    int score, best, kills, combo;
    float comboT, time, spawnAcc, deadT;
    boolean dead, newBest;

    int sShoot, sHit, sBoom, sHurt, sDash, sDie;

    @Override
    public void init() {
        sShoot = e.audio.add(Synth.make(900f, 180f, 0.11f, Synth.SQUARE, 0.05f, 0.30f, 5f));
        sHit = e.audio.add(Synth.make(300f, 120f, 0.06f, Synth.SAW, 0.4f, 0.35f, 3f));
        sBoom = e.audio.add(Synth.make(220f, 40f, 0.35f, Synth.SAW, 0.6f, 0.8f, 2f));
        sHurt = e.audio.add(Synth.make(180f, 50f, 0.35f, Synth.SQUARE, 0.5f, 0.8f, 1.5f));
        sDash = e.audio.add(Synth.make(300f, 1400f, 0.14f, Synth.SINE, 0.1f, 0.5f, 2f));
        sDie = e.audio.add(Synth.make(400f, 25f, 1.2f, Synth.SAW, 0.5f, 0.9f, 1.2f));
        e.input.listener = new Input.Listener() {
            @Override
            public void onTap(float x, float y) {
                if (dead && deadT > 0.9f) reset();
            }

            @Override
            public void onDoubleTap(float x, float y) {
                if (x < e.vw * 0.5f) dash();
            }
        };
        reset();
    }

    void reset() {
        en = 0;
        bn = 0;
        px = py = pvx = pvy = 0f;
        ax = 1f;
        ay = 0f;
        pHp = HP_MAX;
        pInv = dashT = dashCd = fireT = 0f;
        score = kills = combo = 0;
        comboT = time = spawnAcc = deadT = 0f;
        dead = false;
        newBest = false;
        best = e.save.getInt("best", 0);
        fx.clear();
        e.juice.reset();
        e.camX = 0f;
        e.camY = 0f;
    }

    // ------------------------------------------------------------ update
    @Override
    public void update(float dt) {
        if (!dead) {
            time += dt;
            updatePlayer(dt);
            spawn(dt);
            comboT -= dt;
            if (comboT <= 0f) combo = 0;
        } else {
            deadT += e.realDt;
        }
        updateEnemies(dt);
        updateBullets(dt);
        fx.update(dt);

        float lead = (e.input.aiming && !dead) ? 90f : 0f;
        float k = 1f - (float) Math.exp(-6f * e.realDt);
        e.camX += (px + ax * lead - e.camX) * k;
        e.camY += (py + ay * lead - e.camY) * k;
    }

    void updatePlayer(float dt) {
        Input in = e.input;
        float mx = in.moveX, my = in.moveY;
        float ml = (float) Math.sqrt(mx * mx + my * my);
        float al = (float) Math.sqrt(in.aimX * in.aimX + in.aimY * in.aimY);
        if (in.aiming && al > 0.15f) {
            ax = in.aimX / al;
            ay = in.aimY / al;
        } else if (!in.aiming && ml > 0.2f) {
            ax = mx / ml;
            ay = my / ml;
        }
        boolean firing = in.aiming && al > 0.25f;

        dashCd = Math.max(0f, dashCd - dt);
        pInv = Math.max(0f, pInv - dt);
        if (dashT > 0f) {
            dashT -= dt;
            pvx = dashX * DASH_SPEED;
            pvy = dashY * DASH_SPEED;
            fx.burst(px, py, 2, 60f, C_PLAYER, 0.35f, 4f, TAU, 0f);
        } else {
            float k = 1f - (float) Math.exp(-14f * dt);
            pvx += (mx * P_SPEED - pvx) * k;
            pvy += (my * P_SPEED - pvy) * k;
        }
        px += pvx * dt;
        py += pvy * dt;
        px = Math.max(-HX + 20f, Math.min(HX - 20f, px));
        py = Math.max(-HY + 20f, Math.min(HY - 20f, py));

        fireT -= dt;
        if (firing && fireT <= 0f) {
            fireT = FIRE_RATE;
            float ang = (float) Math.atan2(ay, ax) + rng.range(-0.05f, 0.05f);
            float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
            if (bn < MAXB) {
                bx[bn] = px + dx * 22f;
                by[bn] = py + dy * 22f;
                bvx[bn] = dx * B_SPEED + pvx * 0.3f;
                bvy[bn] = dy * B_SPEED + pvy * 0.3f;
                bl[bn] = B_LIFE;
                bn++;
            }
            e.audio.play(sShoot, rng.range(0.92f, 1.08f), 0.5f);
            e.juice.shake(0.03f);
            pvx -= dx * 30f;
            pvy -= dy * 30f;
            fx.burst(px + dx * 24f, py + dy * 24f, 3, 260f, Batch.col(1f, 0.9f, 0.5f), 0.18f, 2.5f, 0.6f, ang);
        }
    }

    void dash() {
        if (dead || dashCd > 0f) return;
        float mx = e.input.moveX, my = e.input.moveY;
        float l = (float) Math.sqrt(mx * mx + my * my);
        if (l > 0.2f) {
            dashX = mx / l;
            dashY = my / l;
        } else {
            dashX = ax;
            dashY = ay;
        }
        dashT = DASH_TIME;
        dashCd = DASH_CD;
        pInv = DASH_TIME + 0.1f;
        e.audio.play(sDash, 1f, 0.6f);
        e.juice.shake(0.2f);
    }

    void spawn(float dt) {
        spawnAcc += (3f + time * 0.12f) * dt;
        float tank = Math.max(0f, Math.min(0.08f, (time - 30f) * 0.002f));
        float fast = Math.max(0f, Math.min(0.3f, (time - 10f) * 0.006f));
        while (spawnAcc >= 1f) {
            spawnAcc -= 1f;
            if (en >= MAXE) break;
            float a = rng.next() * TAU;
            float d = rng.range(800f, 1000f);
            float sx = Math.max(-HX + 30f, Math.min(HX - 30f, px + (float) Math.cos(a) * d));
            float sy = Math.max(-HY + 30f, Math.min(HY - 30f, py + (float) Math.sin(a) * d));
            float r = rng.next();
            int t = r < tank ? 2 : (r < tank + fast ? 1 : 0);
            ex[en] = sx;
            ey[en] = sy;
            evx[en] = 0f;
            evy[en] = 0f;
            ehp[en] = T_HP[t];
            eflash[en] = 0f;
            etype[en] = t;
            en++;
        }
    }

    void removeEnemy(int i) {
        en--;
        if (i != en) {
            ex[i] = ex[en]; ey[i] = ey[en]; evx[i] = evx[en]; evy[i] = evy[en];
            ehp[i] = ehp[en]; eflash[i] = eflash[en]; etype[i] = etype[en];
        }
    }

    void removeBullet(int i) {
        bn--;
        if (i != bn) {
            bx[i] = bx[bn]; by[i] = by[bn]; bvx[i] = bvx[bn]; bvy[i] = bvy[bn]; bl[i] = bl[bn];
        }
    }

    void updateEnemies(float dt) {
        for (int i = en - 1; i >= 0; i--) if (ehp[i] <= 0f) removeEnemy(i);
        grid.clear();
        for (int i = 0; i < en; i++) grid.insert(i, ex[i], ey[i]);
        float k = 1f - (float) Math.exp(-8f * dt);
        for (int j = 0; j < en; j++) {
            int t = etype[j];
            float x = ex[j], y = ey[j];
            float tx = px - x, ty = py - y;
            float dist = (float) Math.sqrt(tx * tx + ty * ty);
            float invD = 1f / Math.max(dist, 0.001f);
            float wx = tx * invD * T_SPEED[t], wy = ty * invD * T_SPEED[t];
            int c = grid.query(x, y, 30f, nb);
            float sx = 0f, sy = 0f;
            for (int q = 0; q < c; q++) {
                int o = nb[q];
                if (o == j) continue;
                float dx = x - ex[o], dy = y - ey[o];
                float l2 = dx * dx + dy * dy;
                if (l2 > 0.01f && l2 < 900f) {
                    float l = (float) Math.sqrt(l2);
                    float s = (30f - l) * 9f / l;
                    sx += dx * s;
                    sy += dy * s;
                }
            }
            evx[j] += (wx + sx - evx[j]) * k;
            evy[j] += (wy + sy - evy[j]) * k;
            ex[j] = x + evx[j] * dt;
            ey[j] = y + evy[j] * dt;
            eflash[j] = Math.max(0f, eflash[j] - dt * 6f);
            if (!dead && pInv <= 0f && dist < T_RADIUS[t] + P_RADIUS) hurt(-tx * invD, -ty * invD);
        }
    }

    void updateBullets(float dt) {
        for (int i = bn - 1; i >= 0; i--) {
            bl[i] -= dt;
            bx[i] += bvx[i] * dt;
            by[i] += bvy[i] * dt;
            boolean rm = bl[i] <= 0f || Math.abs(bx[i]) > HX || Math.abs(by[i]) > HY;
            if (!rm) {
                int c = grid.query(bx[i], by[i], 34f, bq);
                for (int q = 0; q < c; q++) {
                    int o = bq[q];
                    if (ehp[o] <= 0f) continue;
                    float r = T_RADIUS[etype[o]] + 4f;
                    float dx = bx[i] - ex[o], dy = by[i] - ey[o];
                    if (dx * dx + dy * dy < r * r) {
                        float l = (float) Math.sqrt(bvx[i] * bvx[i] + bvy[i] * bvy[i]);
                        hitEnemy(o, 1f, bvx[i] / l, bvy[i] / l);
                        rm = true;
                        break;
                    }
                }
            }
            if (rm) removeBullet(i);
        }
    }

    void hitEnemy(int i, float dmg, float dx, float dy) {
        ehp[i] -= dmg;
        eflash[i] = 1f;
        int t = etype[i];
        float kb = t == 2 ? 90f : 320f;
        evx[i] += dx * kb;
        evy[i] += dy * kb;
        if (ehp[i] <= 0f) {
            kill(i);
        } else {
            e.audio.play(sHit, rng.range(0.9f, 1.2f), 0.4f);
            fx.burst(ex[i], ey[i], 3, 200f, T_COL[t], 0.25f, 2.5f, 1.4f, (float) Math.atan2(dy, dx));
        }
    }

    void kill(int i) {
        int t = etype[i];
        boolean big = t == 2;
        float x = ex[i], y = ey[i];
        combo++;
        comboT = 2f;
        score += T_SCORE[t] * (1 + combo / 10);
        kills++;
        fx.burst(x, y, big ? 26 : 14, big ? 460f : 360f, T_COL[t], big ? 0.7f : 0.5f, big ? 4.5f : 3.5f, TAU, 0f);
        fx.burst(x, y, 6, 200f, C_WHITE, 0.25f, 3f, TAU, 0f);
        fx.ring(x, y, big ? 90f : 50f, T_COL[t], 0.35f, 4f);
        e.audio.play(sBoom, big ? 0.8f : rng.range(1.2f, 1.6f), 0.55f);
        e.juice.shake(big ? 0.45f : 0.1f);
        if (big) {
            e.juice.hitstop(0.06f);
            e.juice.punch(0.05f);
            e.buzz(40);
        }
    }

    void hurt(float dx, float dy) {
        pHp--;
        pInv = 1f;
        combo = 0;
        e.juice.shake(0.7f);
        e.juice.hitstop(0.06f);
        e.buzz(60);
        e.audio.play(sHurt, 1f, 0.8f);
        fx.burst(px, py, 30, 500f, C_RED, 0.6f, 4f, TAU, 0f);
        fx.ring(px, py, 220f, C_RED, 0.4f, 6f);
        for (int j = 0; j < en; j++) {
            float ddx = ex[j] - px, ddy = ey[j] - py;
            float l = Math.max(1f, (float) Math.sqrt(ddx * ddx + ddy * ddy));
            if (l < 220f && ehp[j] > 0f) {
                evx[j] += ddx / l * 900f;
                evy[j] += ddy / l * 900f;
                hitEnemy(j, 0.6f, ddx / l, ddy / l);
            }
        }
        if (pHp <= 0) die();
    }

    void die() {
        dead = true;
        deadT = 0f;
        e.juice.slowmo(0.15f, 1.4f);
        e.juice.shake(1f);
        e.buzz(250);
        e.audio.play(sDie, 1f, 0.9f);
        fx.burst(px, py, 80, 700f, C_PLAYER, 1f, 5f, TAU, 0f);
        fx.ring(px, py, 260f, C_PLAYER, 0.8f, 6f);
        if (score > best) {
            best = score;
            newBest = true;
            e.save.putInt("best", best);
        }
    }

    // ------------------------------------------------------------ render
    @Override
    public void render(Batch b) {
        // arena
        for (float x = -HX; x <= HX + 0.1f; x += 100f) b.line(x, -HY, x, HY, 1.5f, C_GRID);
        for (float y = -HY; y <= HY + 0.1f; y += 100f) b.line(-HX, y, HX, y, 1.5f, C_GRID);
        b.line(-HX, -HY, HX, -HY, 5f, C_WALL);
        b.line(HX, -HY, HX, HY, 5f, C_WALL);
        b.line(HX, HY, -HX, HY, 5f, C_WALL);
        b.line(-HX, HY, -HX, -HY, 5f, C_WALL);

        float halfW = e.vw * 0.5f + 40f, halfH = e.vh * 0.5f + 40f;
        float cx = e.camX, cy = e.camY;

        for (int i = 0; i < bn; i++) {
            b.line(bx[i], by[i], bx[i] - bvx[i] * 0.025f, by[i] - bvy[i] * 0.025f, 4f, Batch.col(1f, 0.95f, 0.6f));
        }
        for (int i = 0; i < en; i++) {
            float x = ex[i], y = ey[i];
            if (Math.abs(x - cx) > halfW || Math.abs(y - cy) > halfH) continue;
            int t = etype[i];
            float r = T_RADIUS[t];
            int c = Batch.lerp(T_COL[t], C_WHITE, eflash[i]);
            b.circle(x, y, r, Batch.darker(c, 0.35f));
            b.circle(x, y, r * 0.62f, c);
        }
        if (!dead) {
            boolean blink = pInv > 0f && ((int) (time * 20f) & 1) == 0;
            if (!blink) {
                float a = (float) Math.atan2(ay, ax);
                b.tri(px + (float) Math.cos(a) * 22f, py + (float) Math.sin(a) * 22f,
                    px + (float) Math.cos(a + 2.5f) * 16f, py + (float) Math.sin(a + 2.5f) * 16f,
                    px + (float) Math.cos(a - 2.5f) * 16f, py + (float) Math.sin(a - 2.5f) * 16f, C_PLAYER);
                b.circle(px, py, 6f, C_WHITE);
            }
            if (dashCd > 0f) {
                float f = 1f - dashCd / DASH_CD;
                b.arc(px, py, 28f, 2f, -1.5708f, -1.5708f + TAU * f, Batch.withAlpha(C_WHITE, 0.35f));
            }
        }
        fx.draw(b);

        // ---- HUD ----
        b.screen();
        float w = e.vw, h = e.vh;
        b.text("SCORE " + score, w - 24f, 22f, 3f, C_WHITE, Batch.RIGHT);
        for (int i = 0; i < HP_MAX; i++) {
            b.rect(w - 24f - (i + 1) * 30f + 8f, 56f, 22f, 22f, i < pHp ? C_PLAYER : Batch.col(1f, 1f, 1f, 0.12f));
        }
        if (combo >= 3 && !dead) b.text("X" + combo, w * 0.5f, 22f, 5f, Batch.col(1f, 0.8f, 0.2f), Batch.CENTER);
        float hint = Math.max(0f, Math.min(1f, 1f - (time - 6f) / 2f));
        if (hint > 0f && !dead) {
            b.text("L: MOVE    R: AIM + FIRE    DOUBLE-TAP L: DASH", w * 0.5f, h - 40f, 2f,
                Batch.withAlpha(C_WHITE, 0.6f * hint), Batch.CENTER);
        }
        if (dead) {
            b.rect(0f, 0f, w, h, Batch.col(0f, 0f, 0f, 0.55f));
            b.text("WIPED OUT", w * 0.5f, h * 0.28f, 10f, Batch.col(1f, 0.3f, 0.45f), Batch.CENTER);
            b.text("SCORE " + score, w * 0.5f, h * 0.50f, 4f, C_WHITE, Batch.CENTER);
            b.text((newBest ? "NEW BEST " : "BEST ") + best, w * 0.5f, h * 0.59f, 4f,
                Batch.col(1f, 0.8f, 0.2f), Batch.CENTER);
            if (deadT > 0.9f) b.text("TAP TO RESTART", w * 0.5f, h * 0.74f, 3f, Batch.withAlpha(C_WHITE, 0.8f), Batch.CENTER);
        }
    }

    @Override
    public String debug() {
        return "ENEMIES " + en + "\nBULLETS " + bn + "\nFX " + fx.count();
    }

    @Override
    public void onPause() {
        e.save.putInt("best", best);
    }
}
