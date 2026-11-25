package com.forge.demo;

import com.forge.engine.ForgeActivity;
import com.forge.engine.Game;

public final class MainActivity extends ForgeActivity {
    @Override
    protected Game createGame() {
        return new SwarmGame();
    }
}
