package com.termux.x11;

import android.os.ParcelFileDescriptor;

import androidx.annotation.Keep;

@Keep
public final class CmdEntryPoint {
    static {
        System.loadLibrary("Xlorie");
    }

    public static native boolean start(String[] args);
    public native ParcelFileDescriptor getXConnection();
    public native ParcelFileDescriptor getLogcatOutput();
    private static native boolean connected();
    private native void listenForConnections();

    public boolean isConnected() {
        return connected();
    }

    public void sendBroadcast() {
    }

    public void spawnListeningThread() {
        Thread thread = new Thread(this::listenForConnections, "KiteX11ConnectionSignal");
        thread.setDaemon(true);
        thread.start();
    }
}
