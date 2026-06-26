package com.termux.x11;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Keep;

@Keep
public final class MainActivity {
    public static final Handler handler = new Handler(Looper.getMainLooper());
    private static final MainActivity INSTANCE = new MainActivity();

    private MainActivity() {
    }

    public static MainActivity getInstance() {
        return INSTANCE;
    }

    public void clientConnectedStateChanged() {
    }
}
