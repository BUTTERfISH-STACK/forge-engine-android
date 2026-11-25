package com.forge.engine;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Batched 2D renderer. Everything (rects, lines, circles, rings, text) becomes colored triangles
 * in one dynamic buffer, flushed in as few draw calls as possible.
 * Logical screen is VH (720) units tall; width follows the device aspect ratio.
 */
public final class Batch {
    public static final int LEFT = 0, CENTER = 1, RIGHT = 2;

    private static final int MAX_VERTS = 30000;
    private static final int STRIDE = 6;

    private static final float[] COS16 = new float[17], SIN16 = new float[17];
    private static final float[] COS32 = new float[33], SIN32 = new float[33];

    static {
        for (int i = 0; i <= 16; i++) {
            COS16[i] = (float) Math.cos(i * Math.PI * 2 / 16);
            SIN16[i] = (float) Math.sin(i * Math.PI * 2 / 16);
        }
        for (int i = 0; i <= 32; i++) {
            COS32[i] = (float) Math.cos(i * Math.PI * 2 / 32);
            SIN32[i] = (float) Math.sin(i * Math.PI * 2 / 32);
        }
    }

    private static final String VS =
        "attribute vec2 aPos;attribute vec4 aCol;uniform vec2 uScale;uniform vec2 uOff;varying vec4 vCol;"
            + "void main(){vCol=aCol;gl_Position=vec4(aPos*uScale+uOff,0.0,1.0);}";
    private static final String FS =
        "precision mediump float;varying vec4 vCol;void main(){gl_FragColor=vCol;}";

    private final float[] v = new float[MAX_VERTS * STRIDE];
    private final FloatBuffer fb =
        ByteBuffer.allocateDirect(MAX_VERTS * STRIDE * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private int n;
    private int prog, aPos, aCol, uScale, uOff;
    private float cx, cy, zoom = 1f;
    private boolean additive;

    public float vw = 1280f, vh = 720f;
    public int drawCalls;

    // ---------- colors ----------
    public static int col(float r, float g, float b, float a) {
        return ((int) (a * 255f + 0.5f) << 24) | ((int) (r * 255f + 0.5f) << 16)
            | ((int) (g * 255f + 0.5f) << 8) | (int) (b * 255f + 0.5f);
    }

    public static int col(float r, float g, float b) {
        return col(r, g, b, 1f);
    }

    public static int withAlpha(int c, float a) {
        int na = (int) (((c >>> 24) & 255) * a);
        return (na << 24) | (c & 0xFFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, aa = a >>> 24;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255, ba = b >>> 24;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
            | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static int darker(int c, float f) {
        return lerp(c, (c & 0xFF000000) | 0, f);
    }

    private static float cr(int c) { return ((c >> 16) & 255) * (1f / 255f); }
    private static float cg(int c) { return ((c >> 8) & 255) * (1f / 255f); }
    private static float cb(int c) { return (c & 255) * (1f / 255f); }
    private static float ca(int c) { return ((c >>> 24) & 255) * (1f / 255f); }

    // ---------- GL setup ----------
    public void init() {
        int vs = compile(GLES20.GL_VERTEX_SHADER, VS);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, FS);
        prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, vs);
        GLES20.glAttachShader(prog, fs);
        GLES20.glLinkProgram(prog);
        aPos = GLES20.glGetAttribLocation(prog, "aPos");
        aCol = GLES20.glGetAttribLocation(prog, "aCol");
        uScale = GLES20.glGetUniformLocation(prog, "uScale");
        uOff = GLES20.glGetUniformLocation(prog, "uOff");
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_BLEND);
        blend();
    }

    private static int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("Shader: " + GLES20.glGetShaderInfoLog(s));
        return s;
    }

    private void blend() {
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, additive ? GLES20.GL_ONE : GLES20.GL_ONE_MINUS_SRC_ALPHA);
    }

    public void begin(float w, float h) {
        vw = w;
        vh = h;
        n = 0;
        drawCalls = 0;
        additive = false;
        blend();
        screen();
    }

    public void end() {
        flush();
    }

    /** World camera: center (x,y) in world units, zoom 1 = one unit per logical pixel. */
    public void camera(float x, float y, float z) {
        flush();
        cx = x;
        cy = y;
        zoom = z;
    }

    /** Screen space (0,0 top-left, vw x vh). Use for HUD. */
    public void screen() {
        camera(vw * 0.5f, vh * 0.5f, 1f);
    }

    public void additive(boolean a) {
        if (a == additive) return;
        flush();
        additive = a;
        blend();
    }

    public void flush() {
        if (n == 0) return;
        GLES20.glUseProgram(prog);
        float sx = 2f * zoom / vw, sy = -2f * zoom / vh;
        GLES20.glUniform2f(uScale, sx, sy);
        GLES20.glUniform2f(uOff, -cx * sx, -cy * sy);
        fb.clear();
        fb.put(v, 0, n);
        fb.position(0);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, STRIDE * 4, fb);
        fb.position(2);
        GLES20.glEnableVertexAttribArray(aCol);
        GLES20.glVertexAttribPointer(aCol, 4, GLES20.GL_FLOAT, false, STRIDE * 4, fb);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, n / STRIDE);
        fb.position(0);
        n = 0;
        drawCalls++;
    }

    // ---------- primitives ----------
    private void ensure(int verts) {
        if (n / STRIDE + verts > MAX_VERTS) flush();
    }

    private void vtx(float x, float y, float r, float g, float b, float a) {
        v[n] = x;
        v[n + 1] = y;
        v[n + 2] = r;
        v[n + 3] = g;
        v[n + 4] = b;
        v[n + 5] = a;
        n += STRIDE;
    }

    public void rect(float x, float y, float w, float h, int c) {
        ensure(6);
        float r = cr(c), g = cg(c), b = cb(c), a = ca(c);
        vtx(x, y, r, g, b, a);
        vtx(x + w, y, r, g, b, a);
        vtx(x, y + h, r, g, b, a);
        vtx(x + w, y, r, g, b, a);
        vtx(x + w, y + h, r, g, b, a);
        vtx(x, y + h, r, g, b, a);
    }

    public void tri(float x1, float y1, float x2, float y2, float x3, float y3, int c) {
        ensure(3);
        float r = cr(c), g = cg(c), b = cb(c), a = ca(c);
        vtx(x1, y1, r, g, b, a);
        vtx(x2, y2, r, g, b, a);
        vtx(x3, y3, r, g, b, a);
    }

    public void line(float x1, float y1, float x2, float y2, float w, int c) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return;
        float nx = -dy / len * w * 0.5f, ny = dx / len * w * 0.5f;
        ensure(6);
        float r = cr(c), g = cg(c), b = cb(c), a = ca(c);
        vtx(x1 + nx, y1 + ny, r, g, b, a);
        vtx(x1 - nx, y1 - ny, r, g, b, a);
        vtx(x2 + nx, y2 + ny, r, g, b, a);
        vtx(x2 + nx, y2 + ny, r, g, b, a);
        vtx(x1 - nx, y1 - ny, r, g, b, a);
        vtx(x2 - nx, y2 - ny, r, g, b, a);
    }

    public void circle(float x, float y, float rad, int c) {
        float[] co = rad < 30f ? COS16 : COS32;
        float[] si = rad < 30f ? SIN16 : SIN32;
        int seg = co.length - 1;
        ensure(seg * 3);
        float r = cr(c), g = cg(c), b = cb(c), a = ca(c);
        for (int i = 0; i < seg; i++) {
            vtx(x, y, r, g, b, a);
            vtx(x + co[i] * rad, y + si[i] * rad, r, g, b, a);
            vtx(x + co[i + 1] * rad, y + si[i + 1] * rad, r, g, b, a);
        }
    }

    public void ring(float x, float y, float rad, float thick, int c) {
        arc(x, y, rad, thick, 0f, (float) (Math.PI * 2), c);
    }

    public void arc(float x, float y, float rad, float thick, float a0, float a1, int c) {
        final int seg = 32;
        ensure(seg * 6);
        float ro = rad + thick * 0.5f, ri = Math.max(0f, rad - thick * 0.5f);
        float r = cr(c), g = cg(c), b = cb(c), a = ca(c);
        float step = (a1 - a0) / seg;
        float pc = (float) Math.cos(a0), ps = (float) Math.sin(a0);
        for (int i = 1; i <= seg; i++) {
            float ang = a0 + step * i;
            float nc = (float) Math.cos(ang), ns = (float) Math.sin(ang);
            vtx(x + pc * ro, y + ps * ro, r, g, b, a);
            vtx(x + pc * ri, y + ps * ri, r, g, b, a);
            vtx(x + nc * ro, y + ns * ro, r, g, b, a);
            vtx(x + nc * ro, y + ns * ro, r, g, b, a);
            vtx(x + pc * ri, y + ps * ri, r, g, b, a);
            vtx(x + nc * ri, y + ns * ri, r, g, b, a);
            pc = nc;
            ps = ns;
        }
    }

    /** Pixel-font text. `scale` = size of one font pixel in logical units (3 is readable). */
    public void text(String s, float x, float y, float scale, int c, int align) {
        float w = Font.width(s, scale);
        if (align == CENTER) x -= w * 0.5f;
        else if (align == RIGHT) x -= w;
        for (int i = 0; i < s.length(); i++) {
            int[] rows = Font.rows(s.charAt(i));
            if (rows != null) {
                for (int r = 0; r < 7; r++) {
                    int bits = rows[r];
                    int col = 0;
                    while (col < 5) {
                        if ((bits & (1 << (4 - col))) != 0) {
                            int start = col;
                            while (col < 5 && (bits & (1 << (4 - col))) != 0) col++;
                            rect(x + start * scale, y + r * scale, (col - start) * scale, scale, c);
                        } else {
                            col++;
                        }
                    }
                }
            }
            x += 6f * scale;
        }
    }
}
