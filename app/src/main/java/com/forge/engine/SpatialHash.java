package com.forge.engine;

import java.util.Arrays;

/**
 * Zero-GC uniform grid. Item id == index into your own arrays.
 * clear() -> insert(id,x,y) for every entity -> query(x,y,r,out) returns candidate ids.
 */
public final class SpatialHash {
    private final float minX, minY, inv;
    private final int cols, rows;
    private final int[] head, next;

    public SpatialHash(float minX, float minY, float w, float h, float cell, int maxItems) {
        this.minX = minX;
        this.minY = minY;
        this.inv = 1f / cell;
        this.cols = (int) Math.ceil(w / cell) + 1;
        this.rows = (int) Math.ceil(h / cell) + 1;
        this.head = new int[cols * rows];
        this.next = new int[maxItems];
        clear();
    }

    public void clear() {
        Arrays.fill(head, -1);
    }

    private int cx(float x) {
        int c = (int) ((x - minX) * inv);
        return c < 0 ? 0 : (c >= cols ? cols - 1 : c);
    }

    private int cy(float y) {
        int c = (int) ((y - minY) * inv);
        return c < 0 ? 0 : (c >= rows ? rows - 1 : c);
    }

    public void insert(int id, float x, float y) {
        int c = cy(y) * cols + cx(x);
        next[id] = head[c];
        head[c] = id;
    }

    /** Fills out[] with ids from cells overlapping the circle's bounding box. Returns count. */
    public int query(float x, float y, float r, int[] out) {
        int x0 = cx(x - r), x1 = cx(x + r), y0 = cy(y - r), y1 = cy(y + r);
        int n = 0;
        for (int gy = y0; gy <= y1; gy++) {
            for (int gx = x0; gx <= x1; gx++) {
                for (int id = head[gy * cols + gx]; id != -1; id = next[id]) {
                    if (n == out.length) return n;
                    out[n++] = id;
                }
            }
        }
        return n;
    }
}
