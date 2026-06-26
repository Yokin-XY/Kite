package com.termux.x11;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;

@Keep
public class LorieView extends SurfaceView {
    private static final int BUTTON_LEFT = 1;
    private static final int BGRA_8888 = 5;
    private static LorieView activeView;
    private ClipboardManager clipboard;
    private int inputViewportLeft;
    private int inputViewportTop;
    private int inputViewportWidth;
    private int inputViewportHeight;
    private float inputSourceLeft;
    private float inputSourceTop;
    private float inputSourceWidth;
    private float inputSourceHeight;

    static {
        System.loadLibrary("Xlorie");
    }

    public LorieView(Context context) {
        super(context);
        init();
    }

    private void init() {
        activeView = this;
        clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                holder.setFormat(BGRA_8888);
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                LorieView.this.surfaceChanged(holder.getSurface());
                updateViewport();
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                LorieView.this.surfaceChanged(null);
            }
        });
        setBackground(new ColorDrawable(Color.TRANSPARENT));
        setFocusable(true);
        setFocusableInTouchMode(true);
        nativeInit();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateViewport();
    }

    public void triggerCallback() {
        requestFocus();
        updateViewport();
    }

    private void updateViewport() {
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        if (width <= 0 || height <= 0) return;
        X11ViewportPlan.Layout layout = X11ViewportPlan.fitLandscapeDesktop(width, height);
        setRendererZoom(100);
        updateInputViewport(
                layout.viewportLeft,
                layout.viewportTop,
                layout.viewportWidth,
                layout.viewportHeight,
                0,
                0,
                layout.desktopWidth,
                layout.desktopHeight
        );
        setViewport(
                layout.viewportLeft,
                layout.viewportTop,
                layout.viewportWidth,
                layout.viewportHeight,
                layout.desktopWidth,
                layout.desktopHeight
        );
        int framerate = getDisplay() != null ? Math.round(getDisplay().getRefreshRate()) : 60;
        sendWindowChange(layout.desktopWidth, layout.desktopHeight, Math.max(30, framerate), "kite");
    }

    private void updateInputViewport(
            int viewportLeft,
            int viewportTop,
            int viewportWidth,
            int viewportHeight,
            float sourceLeft,
            float sourceTop,
            float sourceWidth,
            float sourceHeight
    ) {
        inputViewportLeft = viewportLeft;
        inputViewportTop = viewportTop;
        inputViewportWidth = Math.max(1, viewportWidth);
        inputViewportHeight = Math.max(1, viewportHeight);
        inputSourceLeft = sourceLeft;
        inputSourceTop = sourceTop;
        inputSourceWidth = sourceWidth > 0 ? sourceWidth : inputViewportWidth;
        inputSourceHeight = sourceHeight > 0 ? sourceHeight : inputViewportHeight;
    }

    @Keep
    @SuppressWarnings("unused")
    private static void setRendererViewport(
            int viewportLeft,
            int viewportTop,
            int viewportWidth,
            int viewportHeight,
            float sourceLeft,
            float sourceTop,
            float sourceWidth,
            float sourceHeight
    ) {
        LorieView view = activeView;
        if (view == null) return;
        view.post(() -> view.updateInputViewport(
                viewportLeft,
                viewportTop,
                viewportWidth,
                viewportHeight,
                sourceLeft,
                sourceTop,
                sourceWidth,
                sourceHeight
        ));
    }

    private float mapInputX(float x) {
        float relative = (x - inputViewportLeft) / Math.max(1, inputViewportWidth);
        return inputSourceLeft + relative * inputSourceWidth;
    }

    private float mapInputY(float y) {
        float relative = (y - inputViewportTop) / Math.max(1, inputViewportHeight);
        return inputSourceTop + relative * inputSourceHeight;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        requestFocus();
        int action = event.getActionMasked();
        float x = mapInputX(event.getX());
        float y = mapInputY(event.getY());
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                sendMouseEvent(x, y, BUTTON_LEFT, true, false);
                return true;
            case MotionEvent.ACTION_MOVE:
                sendMouseEvent(x, y, 0, false, false);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                sendMouseEvent(x, y, BUTTON_LEFT, false, false);
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP) {
            return sendKeyEvent(event.getScanCode(), event.getKeyCode(), action == KeyEvent.ACTION_DOWN);
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE | EditorInfo.IME_FLAG_NO_FULLSCREEN;
        return new BaseInputConnection(this, false) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (text != null && text.length() > 0) {
                    sendTextEvent(text.toString().getBytes(StandardCharsets.UTF_8));
                }
                return true;
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                return LorieView.this.dispatchKeyEvent(event);
            }
        };
    }

    @Keep
    public void setClipboardText(String text) {
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("X11 clipboard", text == null ? "" : text));
        }
    }

    @Keep
    public void requestClipboard() {
        CharSequence text = clipboard != null && clipboard.getText() != null ? clipboard.getText() : "";
        sendClipboardEvent(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Keep
    public void resetIme() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            post(() -> ((android.view.inputmethod.InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).invalidateInput(this));
        }
    }

    public boolean sendKeyEvent(int scanCode, int keyCode, boolean keyDown) {
        return sendKeyEvent(scanCode, keyCode, keyDown, 0);
    }

    private native void nativeInit();
    private native void surfaceChanged(Surface surface);
    private native void setFiltering(int filtering);
    public static native void connect(int fd);
    public static native boolean connected();
    public static native void startLogcat(int fd);
    public static native void setClipboardSyncEnabled(boolean enabled, boolean ignored);
    public native void sendClipboardAnnounce();
    public native void sendClipboardEvent(byte[] text);
    public static native void sendWindowChange(int width, int height, int framerate, String name);
    public static native void setViewport(int x, int y, int width, int height, int expectedWidth, int expectedHeight);
    private static native void setRendererZoom(int percent);
    public native void sendMouseEvent(float x, float y, int whichButton, boolean buttonDown, boolean relative);
    public native void sendTouchEvent(int action, int id, int x, int y);
    public native void sendStylusEvent(float x, float y, int pressure, int tiltX, int tiltY, int orientation, int buttons, boolean eraser, boolean mouseMode);
    public static native void requestStylusEnabled(boolean enabled);
    public native boolean sendKeyEvent(int scanCode, int keyCode, boolean keyDown, int a);
    public native void sendTextEvent(byte[] text);
    public static native boolean requestConnection();
}

final class X11ViewportPlan {
    private static final int LANDSCAPE_DESKTOP_WIDTH = 1280;
    private static final int LANDSCAPE_DESKTOP_HEIGHT = 720;

    private X11ViewportPlan() {}

    static Layout fitLandscapeDesktop(int viewWidth, int viewHeight) {
        return fit(viewWidth, viewHeight, LANDSCAPE_DESKTOP_WIDTH, LANDSCAPE_DESKTOP_HEIGHT);
    }

    private static Layout fit(int viewWidth, int viewHeight, int desktopWidth, int desktopHeight) {
        if (viewWidth <= 0 || viewHeight <= 0) {
            return new Layout(0, 0, 1, 1, desktopWidth, desktopHeight);
        }
        float scale = Math.min(viewWidth / (float) desktopWidth, viewHeight / (float) desktopHeight);
        int viewportWidth = Math.max(1, Math.round(desktopWidth * scale));
        int viewportHeight = Math.max(1, Math.round(desktopHeight * scale));
        int viewportLeft = (viewWidth - viewportWidth) / 2;
        int viewportTop = (viewHeight - viewportHeight) / 2;
        return new Layout(viewportLeft, viewportTop, viewportWidth, viewportHeight, desktopWidth, desktopHeight);
    }

    static final class Layout {
        final int viewportLeft;
        final int viewportTop;
        final int viewportWidth;
        final int viewportHeight;
        final int desktopWidth;
        final int desktopHeight;

        Layout(int viewportLeft, int viewportTop, int viewportWidth, int viewportHeight, int desktopWidth, int desktopHeight) {
            this.viewportLeft = viewportLeft;
            this.viewportTop = viewportTop;
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
            this.desktopWidth = desktopWidth;
            this.desktopHeight = desktopHeight;
        }
    }
}
