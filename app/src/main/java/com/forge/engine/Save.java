package com.forge.engine;

import android.content.Context;
import android.content.SharedPreferences;

/** Persistent key/value store (SharedPreferences under the hood). */
public final class Save {
    private final SharedPreferences sp;

    public Save(Context c) {
        sp = c.getSharedPreferences("forge_save", Context.MODE_PRIVATE);
    }

    public int getInt(String k, int def) { return sp.getInt(k, def); }
    public float getFloat(String k, float def) { return sp.getFloat(k, def); }
    public String getString(String k, String def) { return sp.getString(k, def); }
    public boolean getBool(String k, boolean def) { return sp.getBoolean(k, def); }

    public void putInt(String k, int v) { sp.edit().putInt(k, v).apply(); }
    public void putFloat(String k, float v) { sp.edit().putFloat(k, v).apply(); }
    public void putString(String k, String v) { sp.edit().putString(k, v).apply(); }
    public void putBool(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
}
